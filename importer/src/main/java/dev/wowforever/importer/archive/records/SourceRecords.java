package dev.wowforever.importer.archive.records;

import dev.wowforever.importer.clientdata.CsvTable;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import dev.wowforever.lua.ast.TableEntry;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable structured evidence. Original archived bytes remain authoritative for unsupported syntax. */
public final class SourceRecords {
  public static final int EXTRACTION_VERSION = 1;
  private SourceRecords() {}

  public record SourceInput(String path, byte[] bytes) {}
  public record Record(String path, String key, String format, String payload, String sha256) {}
  public record ParseStatus(String path, String status, String detail) {}
  public record Change(String path, String key, String kind) {}
  private record Parsed(List<Record> rows, String status, String detail) {}

  public static void initialize(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS source_record (snapshot_id TEXT NOT NULL, extraction_version INTEGER NOT NULL, artifact_path TEXT NOT NULL,"
          + " record_key TEXT NOT NULL, format TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL,"
          + " PRIMARY KEY(snapshot_id,extraction_version,artifact_path,record_key))");
      s.execute("CREATE TABLE IF NOT EXISTS source_record_parse (snapshot_id TEXT NOT NULL, extraction_version INTEGER NOT NULL, artifact_path TEXT NOT NULL,"
          + " status TEXT NOT NULL, detail TEXT NOT NULL, PRIMARY KEY(snapshot_id,extraction_version,artifact_path))");
    }
  }

  /** Caller owns the transaction. Re-capture is allowed only when the complete extraction is identical. */
  public static void capture(Connection c, String snapshotId, List<SourceInput> inputs) throws SQLException {
    initialize(c);
    for (SourceInput input : inputs) {
      Parsed raw = parse(input);
      Parsed parsed = new Parsed(raw.rows().stream().sorted(java.util.Comparator.comparing(Record::key)).toList(),
          raw.status(), raw.detail());
      var existing = status(c, snapshotId, input.path());
      if (existing.isPresent()) {
        List<Record> prior = records(c, snapshotId, input.path());
        if (!index(prior).equals(index(parsed.rows())) || !existing.get().status().equals(parsed.status())
            || !existing.get().detail().equals(parsed.detail())) {
          throw new SQLException("immutable source extraction already exists: " + input.path());
        }
        continue;
      }
      try (var s = c.prepareStatement("INSERT INTO source_record(snapshot_id,artifact_path,record_key,format,payload,sha256,extraction_version) VALUES (?,?,?,?,?,?,?)")) {
        for (Record row : parsed.rows()) {
          s.setString(1, snapshotId); s.setString(2, row.path()); s.setString(3, row.key());
          s.setString(4, row.format()); s.setString(5, row.payload()); s.setString(6, row.sha256()); s.setInt(7, EXTRACTION_VERSION); s.addBatch();
        }
        s.executeBatch();
      }
      try (var s = c.prepareStatement("INSERT INTO source_record_parse(snapshot_id,artifact_path,status,detail,extraction_version) VALUES (?,?,?,?,?)")) {
        s.setString(1, snapshotId); s.setString(2, input.path()); s.setString(3, parsed.status());
        s.setString(4, parsed.detail()); s.setInt(5, EXTRACTION_VERSION); s.executeUpdate();
      }
    }
  }

  public static List<Record> records(Connection c, String snapshotId) throws SQLException {
    return records(c, snapshotId, null);
  }

  private static List<Record> records(Connection c, String snapshotId, String path) throws SQLException {
    List<Record> out = new ArrayList<>();
    try (var s = c.prepareStatement("SELECT artifact_path,record_key,format,payload,sha256 FROM source_record"
        + " WHERE snapshot_id=? AND extraction_version=" + EXTRACTION_VERSION + (path == null ? "" : " AND artifact_path=?") + " ORDER BY artifact_path,record_key")) {
      s.setString(1, snapshotId);
      if (path != null) s.setString(2, path);
      try (var r = s.executeQuery()) {
        while (r.next()) out.add(new Record(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)));
      }
    }
    return List.copyOf(out);
  }

  private static java.util.Optional<ParseStatus> status(Connection c, String snapshotId, String path)
      throws SQLException {
    try (var s = c.prepareStatement("SELECT status,detail FROM source_record_parse"
        + " WHERE snapshot_id=? AND extraction_version=? AND artifact_path=?")) {
      s.setString(1, snapshotId); s.setInt(2, EXTRACTION_VERSION); s.setString(3, path);
      try (var r = s.executeQuery()) {
        return r.next() ? java.util.Optional.of(new ParseStatus(path, r.getString(1), r.getString(2)))
            : java.util.Optional.empty();
      }
    }
  }

  public static List<ParseStatus> statuses(Connection c, String snapshotId) throws SQLException {
    List<ParseStatus> out = new ArrayList<>();
    try (var s = c.prepareStatement("SELECT artifact_path,status,detail FROM source_record_parse"
        + " WHERE snapshot_id=? AND extraction_version=" + EXTRACTION_VERSION + " ORDER BY artifact_path")) {
      s.setString(1, snapshotId);
      try (var r = s.executeQuery()) {
        while (r.next()) out.add(new ParseStatus(r.getString(1), r.getString(2), r.getString(3)));
      }
    }
    return List.copyOf(out);
  }

  /** Partial/error extractions cannot prove record removal; changed scope must be reviewed separately. */
  public static List<Change> diff(Connection c, String before, String after) throws SQLException {
    Map<String, Record> old = index(records(c, before)), next = index(records(c, after));
    Map<String, String> afterStatus = new LinkedHashMap<>();
    for (var p : statuses(c, after)) afterStatus.put(p.path(), p.status());
    List<Change> changes = new ArrayList<>();
    for (var e : old.entrySet()) {
      Record a = e.getValue(), b = next.remove(e.getKey());
      if (b == null) changes.add(new Change(a.path(), a.key(),
          "complete".equals(afterStatus.get(a.path())) ? "removed" : "unresolved"));
      else if (!a.sha256().equals(b.sha256())) changes.add(new Change(a.path(), a.key(), "changed"));
    }
    for (Record r : next.values()) changes.add(new Change(r.path(), r.key(), "added"));
    return List.copyOf(changes);
  }

  private static Map<String, Record> index(List<Record> rows) {
    Map<String, Record> out = new LinkedHashMap<>();
    for (Record r : rows) out.put(r.path() + "\u0000" + r.key(), r);
    return out;
  }

  private static Parsed parse(SourceInput input) {
    List<Record> rows = new ArrayList<>();
    String path = input.path(), lower = path.toLowerCase(Locale.ROOT);
    try {
      if (!lower.endsWith(".csv") && !lower.endsWith(".lua")) {
        return new Parsed(rows, "unsupported", "Original artifact retained; no structured extractor for this format");
      }
      String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(input.bytes())).toString();
      if (lower.endsWith(".csv")) {
        CsvTable table = CsvTable.read(path, new StringReader(text));
        add(rows, path, "@header", "csv-json", Json.encode(table.columns()));
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        int ordinal = 0;
        for (var row : table.rows()) {
          List<String> cells = new ArrayList<>();
          for (int i = 0; i < table.columns().size(); i++) cells.add(row.get(i));
          String nativeKey = table.hasColumn("ID") && !row.get("ID").isEmpty() ? "ID:" + row.get("ID") : "row:" + (++ordinal);
          int occurrence = occurrences.merge(nativeKey, 1, Integer::sum);
          add(rows, path, nativeKey + "#" + occurrence, "csv-json", Json.encode(List.of(table.columns(), cells)));
        }
        return new Parsed(rows, "complete", "CSV headers and all parsed cells; original formatting remains in artifact");
      }
      if (lower.endsWith(".lua")) {
        boolean partial = lua(rows, path, "", LuaChunk.parse(text, path).statements());
        rows.sort(java.util.Comparator.comparing(Record::key));
        return new Parsed(rows, partial ? "partial" : "complete",
            "Lua syntax records without evaluation; comments/lexical spelling remain in artifact");
      }
      return new Parsed(rows, "unsupported", "Original artifact retained; no structured extractor for this format");
    } catch (Exception e) {
      return new Parsed(List.of(), "error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static boolean lua(List<Record> rows, String path, String prefix, List<Stmt> statements) {
    boolean partial = false;
    int ordinal = 0;
    for (Stmt stmt : statements) {
      if (stmt instanceof Stmt.Local local) {
        List<Stmt> assignments = new ArrayList<>();
        for (int i = 0; i < local.names().size(); i++) {
          if (i < local.values().size()) assignments.add(new Stmt.Assign(
              new Expr.Name(local.names().get(i), local.line()), local.values().get(i), local.line()));
        }
        partial |= lua(rows, path, prefix + "local:" + (++ordinal) + "/", assignments);
        if (local.values().size() != local.names().size()) {
          add(rows, path, prefix + "local-declaration:" + ordinal, "lua-ast-json", Json.encode(local));
        }
        continue;
      }
      String base = prefix + "statement:" + (++ordinal);
      Expr value = switch (stmt) {
        case Stmt.Assign a -> a.value();
        case Stmt.Return r -> r.value();
        default -> null;
      };
      String target = stmt instanceof Stmt.Assign a ? Json.encode(a.target()) : "return";
      base = prefix + target + "#" + ordinal;
      if (value instanceof Expr.TableCtor table) {
        int positional = 0;
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (TableEntry entry : table.entries()) {
          String key = switch (entry) {
            case TableEntry.Keyed k -> Json.encode(k.key());
            case TableEntry.Named n -> "name:" + n.name();
            case TableEntry.Positional ignored -> "position:" + (++positional);
          };
          int occurrence = seen.merge(key, 1, Integer::sum);
          add(rows, path, base + "/" + key + "#" + occurrence, "lua-ast-json", Json.encode(entry));
        }
        if (table.entries().isEmpty()) add(rows, path, base, "lua-ast-json", Json.encode(table));
      } else if (value instanceof Expr.Literal l && l.value() instanceof LuaString s
          && s.value().stripLeading().startsWith("return")) {
        partial |= lua(rows, path, base + "/embedded/", LuaChunk.parse(s.value(), path + "#embedded").statements());
      } else {
        add(rows, path, base, "lua-ast-json", Json.encode(stmt));
      }
      partial |= hasSkipped(stmt);
    }
    return partial;
  }

  private static boolean hasSkipped(Object value) {
    if (value instanceof Stmt.Skipped) return true;
    if (value instanceof Iterable<?> list) {
      for (Object item : list) if (hasSkipped(item)) return true;
    } else if (value != null && value.getClass().isRecord()) {
      for (var field : value.getClass().getRecordComponents()) {
        try { if (hasSkipped(field.getAccessor().invoke(value))) return true; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
      }
    }
    return false;
  }

  private static void add(List<Record> rows, String path, String key, String format, String payload) {
    try {
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
      rows.add(new Record(path, key, format, payload, hash));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
