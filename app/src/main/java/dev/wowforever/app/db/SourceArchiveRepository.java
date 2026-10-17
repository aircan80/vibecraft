package dev.wowforever.app.db;

import java.util.List;
import java.util.Map;

/** Read-only dataset archive metadata. Archive presence does not establish entity or field lineage. */
public final class SourceArchiveRepository {
  public record SourceFile(String path, String url, String sha256) {}
  public record Snapshot(String id, String source, String version, String product, String build,
      String locale, String capturedAt, List<SourceFile> files) {
    public Snapshot { files = List.copyOf(files); }
  }
  public record Projection(String ingestionId, String projectedAt, String transformVersion) {}
  public record Catalog(boolean available, List<Snapshot> snapshots, Map<String, List<String>> activeRoles,
      Projection projection) {
    public Catalog(boolean available, List<Snapshot> snapshots) { this(available, snapshots, Map.of(), null); }
    public Catalog {
      snapshots = List.copyOf(snapshots);
      var roles = new java.util.LinkedHashMap<String, List<String>>();
      activeRoles.forEach((id, values) -> roles.put(id, List.copyOf(values)));
      activeRoles = Map.copyOf(roles);
    }
  }
  private final Database db;

  public SourceArchiveRepository(Database db) { this.db = db; }

  /** Does not read archived blobs. Older app databases honestly report that no archive exists. */
  public Catalog catalog() {
    return db.read(c -> {
      boolean available = Sql.list(c, "SELECT name FROM sqlite_master WHERE type='table' "
          + "AND name IN ('source_archive_snapshot','source_archive_file')", rs -> rs.getString(1)).size() == 2;
      if (!available) return new Catalog(false, List.of());
      var snapshots = Sql.list(c, "SELECT id,source,version,product,build,locale,captured_at "
          + "FROM source_archive_snapshot ORDER BY source,captured_at DESC,id", rs ->
          new Snapshot(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
              rs.getString(6), rs.getString(7), List.of()));
      var result = new java.util.ArrayList<Snapshot>();
      for (var s : snapshots) {
        var files = Sql.list(c, "SELECT path,url,sha256 FROM source_archive_file WHERE snapshot_id=? ORDER BY path",
            rs -> new SourceFile(rs.getString(1), rs.getString(2), rs.getString(3)), s.id());
        result.add(new Snapshot(s.id(), s.source(), s.version(), s.product(), s.build(), s.locale(), s.capturedAt(), files));
      }
      var active = new java.util.LinkedHashMap<String, List<String>>();
      Projection projection = null;
      if (Sql.hasTable(c, "meta") && Sql.hasTable(c, "source_ingestion_input")
          && Sql.hasTable(c, "source_projection")) {
        String ingestion = Sql.one(c, "SELECT value FROM meta WHERE key='source_ingestion_id'",
            rs -> rs.getString(1)).orElse(null);
        if (ingestion != null) {
          projection = Sql.one(c, "SELECT ingestion_id,projected_at,transform_version FROM source_projection "
              + "WHERE ingestion_id=? ORDER BY id DESC LIMIT 1",
              rs -> new Projection(rs.getString(1), rs.getString(2), rs.getString(3)), ingestion).orElse(null);
          // An ingestion is not claimed as projected without its completed projection record.
          if (projection != null) {
            var inputs = Sql.list(c, "SELECT snapshot_id,role FROM source_ingestion_input "
                + "WHERE ingestion_id=? ORDER BY snapshot_id,role",
                rs -> new String[] {rs.getString(1), rs.getString(2)}, ingestion);
            for (var input : inputs) active.computeIfAbsent(input[0], ignored -> new java.util.ArrayList<>()).add(input[1]);
          }
        }
      }
      return new Catalog(true, result, active, projection);
    });
  }
}
