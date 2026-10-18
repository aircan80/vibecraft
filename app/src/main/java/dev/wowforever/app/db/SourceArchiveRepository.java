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
  public record LegacySource(String source, String version, String linkType, String url) {}
  public record Projection(String ingestionId, String projectedAt, String transformVersion) {}
  public record ProjectionInput(String role, String snapshotId, Snapshot snapshot) {}
  public record ProjectionRun(String id, String ingestionId, String projectedAt, String transformVersion,
      String schemaVersion, String buildId, String validationSummary, boolean active, List<ProjectionInput> inputs) {
    public ProjectionRun { inputs = List.copyOf(inputs); }
  }
  public record Catalog(boolean available, List<Snapshot> snapshots, Map<String, List<String>> activeRoles,
      Projection projection, List<LegacySource> legacySources, List<ProjectionRun> history, String historyNote) {
    public Catalog(boolean available, List<Snapshot> snapshots, Map<String, List<String>> activeRoles,
        Projection projection, List<LegacySource> legacySources) {
      this(available, snapshots, activeRoles, projection, legacySources, List.of(), "No completed build history recorded.");
    }
    public Catalog(boolean available, List<Snapshot> snapshots, Map<String, List<String>> activeRoles,
        Projection projection) { this(available, snapshots, activeRoles, projection, List.of()); }
    public Catalog(boolean available, List<Snapshot> snapshots) { this(available, snapshots, Map.of(), null); }
    public Catalog {
      snapshots = List.copyOf(snapshots);
      legacySources = List.copyOf(legacySources);
      history = List.copyOf(history);
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
      if (!available) {
        var meta = new java.util.LinkedHashMap<String, String>();
        if (Sql.hasTable(c, "meta")) for (var row : Sql.list(c, "SELECT key,value FROM meta",
            rs -> new String[] {rs.getString(1), rs.getString(2)})) meta.put(row[0], row[1]);
        return new Catalog(false, List.of(), Map.of(), null, legacy(meta));
      }
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
      var history = new java.util.ArrayList<ProjectionRun>();
      Projection projection = null;
      String note = "No completed build history recorded.";
      if (Sql.hasTable(c, "source_ingestion_input") && Sql.hasTable(c, "source_projection")) {
        var meta = new java.util.LinkedHashMap<String, String>();
        if (Sql.hasTable(c, "meta")) for (var row : Sql.list(c, "SELECT key,value FROM meta",
            rs -> new String[] {rs.getString(1), rs.getString(2)})) meta.put(row[0], row[1]);
        String ingestion = meta.get("source_ingestion_id");
        String activeId = meta.get("source_projection_id");
        var columns = Sql.list(c, "PRAGMA table_info(source_projection)", rs -> rs.getString("name"));
        var runs = Sql.list(c, "SELECT id,ingestion_id,projected_at,transform_version,"
            + (columns.contains("schema_version") ? "schema_version" : "NULL") + ","
            + (columns.contains("build_id") ? "build_id" : "NULL") + ","
            + (columns.contains("validation_summary") ? "validation_summary" : "NULL")
            + " FROM source_projection ORDER BY id DESC",
            rs -> new ProjectionRun(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), false, List.of()));
        var byId = new java.util.HashMap<String, Snapshot>();
        for (var snapshot : result) byId.put(snapshot.id(), snapshot);
        var inputsByIngestion = new java.util.HashMap<String, List<ProjectionInput>>();
        for (var run : runs) {
          var inputs = inputsByIngestion.get(run.ingestionId());
          if (inputs == null) {
            inputs = Sql.list(c, "SELECT role,snapshot_id FROM source_ingestion_input WHERE ingestion_id=? ORDER BY role",
                rs -> new ProjectionInput(rs.getString(1), rs.getString(2), byId.get(rs.getString(2))), run.ingestionId());
            inputsByIngestion.put(run.ingestionId(), inputs);
          }
          boolean selected = run.id().equals(activeId) && (ingestion == null || run.ingestionId().equals(ingestion));
          history.add(new ProjectionRun(run.id(), run.ingestionId(), run.projectedAt(), run.transformVersion(),
              run.schemaVersion(), run.buildId(), run.validationSummary(), selected, inputs));
          if (selected) {
            projection = new Projection(run.ingestionId(), run.projectedAt(), run.transformVersion());
            for (var input : inputs) active.computeIfAbsent(input.snapshotId(), ignored -> new java.util.ArrayList<>()).add(input.role());
          }
        }
        if (projection != null) note = "Active build identified by the database's recorded execution ID.";
        else if (activeId != null) note = "Active execution metadata does not match a completed build; no build is marked active.";
        else if (ingestion != null) {
          note = "Active ingestion is recorded, but its execution ID was not recorded; no build is marked active.";
          if (inputsByIngestion.containsKey(ingestion)) for (var input : inputsByIngestion.get(ingestion))
            active.computeIfAbsent(input.snapshotId(), ignored -> new java.util.ArrayList<>()).add(input.role());
        } else if (!history.isEmpty()) note = "Completed builds are archived; the active execution was not recorded.";
      }
      var baselineReferences = new java.util.ArrayList<LegacySource>();
      if (result.stream().anyMatch(s -> s.source().equals("legacy-app-database")
          && active.getOrDefault(s.id(), List.of()).contains("legacy-app")) && Sql.hasTable(c, "meta")) {
        var metadata = new java.util.LinkedHashMap<String, String>();
        for (var row : Sql.list(c, "SELECT key,value FROM meta", rs -> new String[]{rs.getString(1),rs.getString(2)}))
          metadata.put(row[0],row[1]);
        baselineReferences.addAll(legacy(metadata));
      }
      if (Sql.hasTable(c, "meta") && Sql.one(c,
          "SELECT value FROM meta WHERE key='release_dataset_profile'", rs -> rs.getString(1)).isPresent()) {
        note += " Compact release dataset: source identities, versions, file hashes and field conflicts are retained; "
            + "raw source records and original archive bytes are omitted. Rebuilding requires the development database.";
      }
      return new Catalog(true, result, active, projection, baselineReferences, history, note);
    });
  }
  private static List<LegacySource> legacy(Map<String, String> meta) {
    var sources = new java.util.ArrayList<LegacySource>();
    commit(sources, "QuestieDB", meta.get("questiedb_commit"), "Questie/QuestieDB");
    commit(sources, "AtlasLootClassic", meta.get("atlasloot_commit"), "Hoizame/AtlasLootClassic");
    commit(sources, "AtlasLoot baseline", meta.get("atlasloot_baseline_commit"), "Hoizame/AtlasLootClassic");
    client(sources, "Blizzard client (via wago.tools)", meta.get("client_product"), meta.get("client_build"));
    client(sources, "Blizzard client fallback (via wago.tools)", meta.get("client_fallback_product"), meta.get("client_fallback_build"));
    String server = meta.get("serverdb_commit");
    if (present(server) || present(meta.get("serverdb_asset"))) sources.add(new LegacySource("VMaNGOS",
        java.util.stream.Stream.of(server, meta.get("serverdb_asset")).filter(SourceArchiveRepository::present)
            .collect(java.util.stream.Collectors.joining(" · ")),
        "Repository revision; asset recorded separately", pinned("vmangos/core", server)));
    return List.copyOf(sources);
  }
  private static void commit(List<LegacySource> sources, String source, String version, String repo) {
    if (present(version)) sources.add(new LegacySource(source, version,
        pinned(repo, version).isEmpty() ? "Recorded pin; original inputs not archived" : "Pinned repository revision", pinned(repo, version)));
  }
  private static void client(List<LegacySource> sources, String source, String product, String build) {
    if (present(product) || present(build)) sources.add(new LegacySource(source,
        java.util.stream.Stream.of(product, build).filter(SourceArchiveRepository::present)
            .collect(java.util.stream.Collectors.joining(" · ")),
        "Delivery/reference site; not archived source evidence", "https://wago.tools/db2"));
  }
  private static String pinned(String repo, String commit) {
    return commit != null && commit.matches("[0-9a-fA-F]{40}") ? "https://github.com/" + repo + "/tree/" + commit : "";
  }
  private static boolean present(String value) { return value != null && !value.isBlank(); }

}
