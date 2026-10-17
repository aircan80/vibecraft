package dev.wowforever.importer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/** Shared identity for additive projections; the caller owns the transaction. */
final class AdditiveProjection {
  private AdditiveProjection() {}

  static void record(Connection c, Map<String,String> additions, String replacePrefix,
      String component, String version, String summary) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT)");
    }
    String base = meta(c,"source_overlay_base_build_id");
    if (base.isEmpty()) {
      base = meta(c,"build_id");
      if (base.isEmpty()) base = "legacy-unidentified";
      put(c,"source_overlay_base_build_id",base);
    }
    var inputs = new TreeMap<String,String>();
    try (var s = c.prepareStatement("SELECT role,snapshot_id FROM source_ingestion_input WHERE ingestion_id=?")) {
      s.setString(1,meta(c,"source_ingestion_id"));
      try (var r = s.executeQuery()) { while (r.next()) inputs.put(r.getString(1),r.getString(2)); }
    }
    if (replacePrefix != null) inputs.keySet().removeIf(k -> k.startsWith(replacePrefix));
    inputs.putAll(additions);
    put(c,"source_overlay_transform_"+component,version);
    var transforms = new TreeMap<String,String>();
    try (var s = c.createStatement(); var r = s.executeQuery(
        "SELECT key,value FROM meta WHERE key GLOB 'source_overlay_transform_*'")) {
      while (r.next()) transforms.put(r.getString(1),r.getString(2));
    }
    String ingestion = ArchiveWorkflow.hash(canonical(inputs));
    String transform = "additive-v1\n"+canonical(transforms);
    String build = ArchiveWorkflow.hash(base+"\n"+ingestion+"\n"+transform);
    try (var s = c.prepareStatement("INSERT OR IGNORE INTO source_ingestion VALUES (?,?)")) {
      s.setString(1,ingestion); s.setString(2,Instant.now().toString()); s.executeUpdate();
    }
    try (var s = c.prepareStatement("INSERT OR IGNORE INTO source_ingestion_input VALUES (?,?,?)")) {
      for (var e : inputs.entrySet()) {
        s.setString(1,ingestion); s.setString(2,e.getKey()); s.setString(3,e.getValue()); s.addBatch();
      }
      s.executeBatch();
    }
    try (var s = c.prepareStatement("INSERT INTO source_projection(ingestion_id,projected_at,transform_version,"
        + "schema_version,build_id,validation_summary) VALUES (?,?,?,?,?,?)")) {
      s.setString(1,ingestion); s.setString(2,Instant.now().toString()); s.setString(3,transform);
      s.setInt(4,dev.wowforever.importer.sqlite.SqliteWriter.SCHEMA_VERSION);
      s.setString(5,build); s.setString(6,(inputs.containsKey("legacy-app") ? "Legacy baseline retained; core facts not regenerated; " : "")+summary); s.executeUpdate();
    }
    String execution;
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT last_insert_rowid()")) {
      execution = r.getString(1);
    }
    put(c,"build_id",build); put(c,"source_ingestion_id",ingestion);
    put(c,"source_projection_id",execution);
  }

  private static String canonical(Map<String,String> values) {
    return values.entrySet().stream().map(e -> e.getKey()+"="+e.getValue())
        .collect(java.util.stream.Collectors.joining("\n"));
  }
  static String meta(Connection c, String key) throws SQLException {
    try (var s = c.prepareStatement("SELECT value FROM meta WHERE key=?")) {
      s.setString(1,key); try (var r = s.executeQuery()) { return r.next() ? r.getString(1) : ""; }
    }
  }
  private static void put(Connection c, String key, String value) throws SQLException {
    try (var s = c.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES (?,?)")) {
      s.setString(1,key); s.setString(2,value); s.executeUpdate();
    }
  }
}
