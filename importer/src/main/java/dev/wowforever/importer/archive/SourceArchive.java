package dev.wowforever.importer.archive;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Lossless immutable source snapshots. Transactions belong to the caller; this class never commits. */
public final class SourceArchive {
  private SourceArchive() {}

  public record Snapshot(String source, String version, String product, String build, String locale) {
    public Snapshot {
      if (source == null || source.isBlank()) throw new IllegalArgumentException("source must be named");
    }
  }

  /** A portable relative file path, acquisition URL (nullable), and unmodified bytes. */
  public record Artifact(String path, String url, byte[] bytes) {
    public Artifact {
      validatePath(path);
      bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
  }

  public record StoredSnapshot(String id, Snapshot metadata, String capturedAt) {}
  public enum Kind { ADDED, REMOVED, CHANGED }
  public record Change(String path, Kind kind, String oldSha256, String newSha256) {}

  public static void initialize(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS source_archive_snapshot (id TEXT PRIMARY KEY, source TEXT NOT NULL,"
          + " version TEXT, product TEXT, build TEXT, locale TEXT, captured_at TEXT NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS source_archive_blob (sha256 TEXT PRIMARY KEY, bytes BLOB NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS source_archive_file (snapshot_id TEXT NOT NULL, path TEXT NOT NULL,"
          + " url TEXT, sha256 TEXT NOT NULL, PRIMARY KEY(snapshot_id,path),"
          + " FOREIGN KEY(snapshot_id) REFERENCES source_archive_snapshot(id),"
          + " FOREIGN KEY(sha256) REFERENCES source_archive_blob(sha256))");
    }
  }

  /** Idempotent capture. Requires an explicit caller transaction; rolls back only this capture on failure. */
  public static String capture(Connection c, Snapshot metadata, List<Artifact> artifacts) throws SQLException {
    Objects.requireNonNull(metadata, "metadata");
    if (c.getAutoCommit()) throw new SQLException("source capture requires a caller-owned transaction");
    List<Artifact> sorted = checked(artifacts);
    String id = fingerprint(metadata, sorted);
    Savepoint savepoint = c.setSavepoint();
    try {
      try (var p = c.prepareStatement("INSERT OR IGNORE INTO source_archive_snapshot VALUES (?,?,?,?,?,?,?)")) {
        p.setString(1, id); p.setString(2, metadata.source()); p.setString(3, metadata.version());
        p.setString(4, metadata.product()); p.setString(5, metadata.build()); p.setString(6, metadata.locale());
        p.setString(7, Instant.now().toString()); p.executeUpdate();
      }
      try (var blob = c.prepareStatement("INSERT OR IGNORE INTO source_archive_blob VALUES (?,?)");
           var file = c.prepareStatement("INSERT OR IGNORE INTO source_archive_file VALUES (?,?,?,?)")) {
        for (Artifact a : sorted) {
          String hash = sha256(a.bytes);
          blob.setString(1, hash); blob.setBytes(2, a.bytes); blob.executeUpdate();
          file.setString(1, id); file.setString(2, a.path()); file.setString(3, a.url());
          file.setString(4, hash); file.executeUpdate();
        }
      }
      artifacts(c, id); // also reject pre-existing corrupt content-addressed rows
      c.releaseSavepoint(savepoint);
      return id;
    } catch (SQLException | RuntimeException e) {
      try { c.rollback(savepoint); c.releaseSavepoint(savepoint); }
      catch (SQLException rollback) { e.addSuppressed(rollback); }
      throw e;
    }
  }

  public static List<StoredSnapshot> snapshots(Connection c) throws SQLException {
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM source_archive_snapshot ORDER BY captured_at,id")) {
      List<StoredSnapshot> result = new ArrayList<>();
      while (r.next()) result.add(readSnapshot(r));
      return List.copyOf(result);
    }
  }

  public static StoredSnapshot snapshot(Connection c, String id) throws SQLException {
    try (var p = c.prepareStatement("SELECT * FROM source_archive_snapshot WHERE id=?")) {
      p.setString(1, id);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new SQLException("Unknown source snapshot: " + id);
        return readSnapshot(r);
      }
    }
  }

  /** Returns exact source bytes after verifying their stored content hashes and snapshot fingerprint. */
  public static List<Artifact> artifacts(Connection c, String id) throws SQLException {
    StoredSnapshot stored = snapshot(c, id);
    List<Artifact> result = new ArrayList<>();
    try (var p = c.prepareStatement("SELECT f.path,f.url,f.sha256,b.bytes FROM source_archive_file f"
        + " LEFT JOIN source_archive_blob b ON b.sha256=f.sha256 WHERE f.snapshot_id=? ORDER BY f.path")) {
      p.setString(1, id);
      try (var r = p.executeQuery()) {
        while (r.next()) {
          byte[] bytes = r.getBytes(4);
          if (bytes == null || !sha256(bytes).equals(r.getString(3)))
            throw new SQLException("Corrupt source artifact: " + id + "/" + r.getString(1));
          try { result.add(new Artifact(r.getString(1), r.getString(2), bytes)); }
          catch (IllegalArgumentException e) { throw new SQLException("Unsafe archived artifact path", e); }
        }
      }
    }
    List<Artifact> sorted;
    try { sorted = checked(result); }
    catch (IllegalArgumentException e) { throw new SQLException("Corrupt source snapshot paths: " + id, e); }
    if (!fingerprint(stored.metadata(), sorted).equals(id)) throw new SQLException("Corrupt source snapshot: " + id);
    return List.copyOf(sorted);
  }

  /** A path is changed when either its exact bytes or acquisition URL changes. */
  public static List<Change> diff(Connection c, String oldId, String newId) throws SQLException {
    Map<String, Artifact> oldFiles = byPath(artifacts(c, oldId));
    Map<String, Artifact> newFiles = byPath(artifacts(c, newId));
    TreeSet<String> paths = new TreeSet<>(oldFiles.keySet()); paths.addAll(newFiles.keySet());
    List<Change> changes = new ArrayList<>();
    for (String path : paths) {
      Artifact before = oldFiles.get(path), after = newFiles.get(path);
      String oldHash = before == null ? null : sha256(before.bytes);
      String newHash = after == null ? null : sha256(after.bytes);
      if (before == null) changes.add(new Change(path, Kind.ADDED, null, newHash));
      else if (after == null) changes.add(new Change(path, Kind.REMOVED, oldHash, null));
      else if (!oldHash.equals(newHash) || !Objects.equals(before.url(), after.url()))
        changes.add(new Change(path, Kind.CHANGED, oldHash, newHash));
    }
    return List.copyOf(changes);
  }

  /** Extracts verified bytes into an empty directory. Existing files and symbolic links are never followed. */
  public static void extract(Connection c, String id, Path destination) throws SQLException, IOException {
    List<Artifact> files = artifacts(c, id); // verify the entire snapshot before creating files
    Path root = destination.toAbsolutePath().normalize();
    rejectSymlinks(root);
    if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a directory: " + root);
      try (var entries = Files.list(root)) {
        if (entries.findAny().isPresent()) throw new IOException("Extraction destination must be empty: " + root);
      }
    }
    Files.createDirectories(root);
    for (Artifact a : files) {
      Path target = root.resolve(a.path()).normalize();
      if (!target.startsWith(root)) throw new IOException("Unsafe artifact path: " + a.path());
      rejectSymlinks(target);
      Files.createDirectories(target.getParent());
      Files.write(target, a.bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
  }

  private static void rejectSymlinks(Path path) throws IOException {
    for (Path p = path; p != null; p = p.getParent())
      if (Files.isSymbolicLink(p)) throw new IOException("Symbolic link in extraction destination: " + p);
  }

  private static StoredSnapshot readSnapshot(ResultSet r) throws SQLException {
    return new StoredSnapshot(r.getString("id"), new Snapshot(r.getString("source"), r.getString("version"),
        r.getString("product"), r.getString("build"), r.getString("locale")), r.getString("captured_at"));
  }

  private static List<Artifact> checked(List<Artifact> artifacts) {
    List<Artifact> sorted = new ArrayList<>(Objects.requireNonNull(artifacts, "artifacts"));
    sorted.sort(Comparator.comparing(Artifact::path));
    String previous = null;
    for (Artifact a : sorted) {
      if (a.path().equals(previous)) throw new IllegalArgumentException("Duplicate artifact path: " + previous);
      previous = a.path();
    }
    TreeSet<String> paths = new TreeSet<>();
    for (Artifact a : sorted) paths.add(a.path());
    for (String path : paths) {
      int slash = path.indexOf('/');
      while (slash >= 0) {
        if (paths.contains(path.substring(0, slash))) throw new IllegalArgumentException("File/directory collision: " + path);
        slash = path.indexOf('/', slash + 1);
      }
    }
    return sorted;
  }

  private static Map<String, Artifact> byPath(List<Artifact> files) {
    Map<String, Artifact> result = new TreeMap<>();
    for (Artifact a : files) result.put(a.path(), a);
    return result;
  }

  private static void validatePath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":"))
      throw new IllegalArgumentException("Artifact path must be portable and relative: " + path);
    for (String part : path.split("/", -1))
      if (part.isEmpty() || part.equals(".") || part.equals("..") || part.chars().anyMatch(ch -> ch < 32 || ch == 127))
        throw new IllegalArgumentException("Unsafe artifact path: " + path);
  }

  private static String fingerprint(Snapshot metadata, List<Artifact> artifacts) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bytes)) {
        field(out, "wowforever-source-archive-v1");
        field(out, metadata.source()); field(out, metadata.version()); field(out, metadata.product());
        field(out, metadata.build()); field(out, metadata.locale());
        out.writeInt(artifacts.size());
        for (Artifact a : artifacts) {
          field(out, a.path()); field(out, a.url()); field(out, sha256(a.bytes));
        }
      }
      return sha256(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  private static void field(DataOutputStream out, String value) throws IOException {
    if (value == null) { out.writeInt(-1); return; }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
  }

  private static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
}
