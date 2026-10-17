package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.CsvTable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Additive, offline world-map projection; never replaces the active core ingestion. */
public final class WorldMapArchiveWorkflow {
  public static final String ATT_COMMIT = "a8c2fce5e8e52af3925352f5e61cbf7520ae3148";
  public static final String BUILD = "1.60.1.70374";
  public static final String TRANSFORM = "world-map-v1";
  private WorldMapArchiveWorkflow() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 5 && args[0].equals("capture"))
      System.out.println(ingest(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),args[4]));
    else if (args.length == 3 && args[0].equals("project")) {
      project(Path.of(args[1]),args[2]); System.out.println(args[2]);
    } else throw new IllegalArgumentException("Usage: WorldMapArchiveWorkflow capture DATABASE UIMAP_CSV ASSIGNMENT_CSV ATT_COMMIT | project DATABASE SNAPSHOT_ID");
  }

  public static String apply(Path database, Path maps, Path assignments, String version) throws Exception {
    String snapshot=ingest(database,maps,assignments,version);
    project(database,snapshot);
    return snapshot;
  }

  public static String ingest(Path database, Path maps, Path assignments, String version) throws Exception {
    if (version == null || !version.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("ATT commit must be a full lowercase SHA-1");
    requireFilename(maps,"UiMap"); requireFilename(assignments,"UiMapAssignment");
    database=existingDatabase(database);
    if (Files.isSameFile(database,maps) || Files.isSameFile(database,assignments)) throw new IOException("Source CSV and database must differ");
    try(var channel=FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null) throw new IOException("Another import is using "+database);
      List<SourceArchive.Artifact> artifacts=List.of(artifact(maps,"UiMap",version),artifact(assignments,"UiMapAssignment",version));
      try(Connection c=connect(database)) {
        c.setAutoCommit(false); ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("allthethings-world-map",version+" (local input; content identified by hash)","forever",BUILD,"enUS"),artifacts);
        c.commit();
        dev.wowforever.importer.archive.records.SourceRecords.capture(c,snapshot,artifacts.stream()
            .map(a -> new dev.wowforever.importer.archive.records.SourceRecords.SourceInput(a.path(),a.bytes())).toList());
        c.commit(); return snapshot;
      }
    }
  }

  private static Path existingDatabase(Path database) throws IOException {
    database=database.toAbsolutePath().normalize();
    if(!Files.isRegularFile(database)) throw new IOException("Existing candidate database required: "+database);
    return database;
  }

  public static void project(Path database, String snapshot) throws Exception {
    database=existingDatabase(database);
    try (var channel = FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another import is using " + database);
      Path candidate = Files.createTempFile(database.getParent(), ".world-map-", ".sqlite");
      try {
        // SQLite backup also includes committed WAL pages, unlike a filesystem copy.
        try (Connection original = connect(database); var p = original.prepareStatement("VACUUM INTO ?")) {
          Files.delete(candidate); p.setString(1, candidate.toString()); p.execute();
        }
        try (Connection c = connect(candidate)) {
          c.setAutoCommit(false);
          ArchiveWorkflow.initialize(c);
          var metadata=SourceArchive.snapshot(c,snapshot).metadata();
          if(!metadata.source().equals("allthethings-world-map") || !BUILD.equals(metadata.build()))
            throw new IllegalArgumentException("Snapshot is not a compatible world-map capture: "+snapshot);
          var archived=SourceArchive.artifacts(c,snapshot);
          if(archived.size()!=2) throw new IllegalArgumentException("World map snapshot must contain exactly two CSV artifacts");
          List<SourceArchive.Artifact> artifacts=List.of(
              archived.stream().filter(v -> v.path().endsWith("/UiMap."+BUILD+".csv")).findFirst().orElseThrow(),
              archived.stream().filter(v -> v.path().endsWith("/UiMapAssignment."+BUILD+".csv")).findFirst().orElseThrow());
          project(c, CsvTable.read(artifacts.get(0).path(), new StringReader(new String(artifacts.get(0).bytes(), StandardCharsets.UTF_8))),
              CsvTable.read(artifacts.get(1).path(), new StringReader(new String(artifacts.get(1).bytes(), StandardCharsets.UTF_8))));
          try (var p = c.prepareStatement("INSERT OR IGNORE INTO world_map_projection VALUES (?,?,?)")) {
            p.setString(1, snapshot); p.setString(2, TRANSFORM); p.setString(3, Instant.now().toString()); p.executeUpdate();
          }
          try (var p = c.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES ('world_map_snapshot_id',?)")) {
            p.setString(1, snapshot); p.executeUpdate();
          }
          try (var p = c.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES ('world_map_source_build',?)")) {
            p.setString(1, BUILD); p.executeUpdate();
          }
          try (var s = c.createStatement()) {
            s.executeUpdate("INSERT OR REPLACE INTO meta(key,value) SELECT 'world_map_unplaced_count',CAST(count(*) AS TEXT) FROM ui_map_child WHERE x0 IS NULL");
          }
          composeProjection(c, snapshot);
          c.commit();
          try (var s = c.createStatement(); var r = s.executeQuery("PRAGMA quick_check")) {
            if (!r.next() || !"ok".equals(r.getString(1))) throw new SQLException("World map candidate integrity check failed");
          }
        }
        Files.move(candidate, database, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally { Files.deleteIfExists(candidate); }
    }
  }

  private static void composeProjection(Connection c,String snapshot) throws SQLException {
    AdditiveProjection.record(c,Map.of("world-map-"+snapshot,snapshot),"world-map-",
        "world_map",TRANSFORM,"Additive world map projection; unplaced="+AdditiveProjection.meta(c,"world_map_unplaced_count"));
  }

  private static void requireFilename(Path path, String table) {
    if (!path.getFileName().toString().equals(table + "." + BUILD + ".csv"))
      throw new IllegalArgumentException("Expected " + table + "." + BUILD + ".csv: " + path);
  }

  private static SourceArchive.Artifact artifact(Path path, String table, String version) throws IOException {
    String name = table + "." + BUILD + ".csv";
    String relative = ".contrib/.db/forever/.config/.wago/" + name;
    return new SourceArchive.Artifact(relative, "", Files.readAllBytes(path));
  }

  static void project(Connection c, CsvTable maps, CsvTable assignments) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("CREATE TABLE IF NOT EXISTS ui_map(id INTEGER PRIMARY KEY,parent_id INTEGER NOT NULL,name TEXT NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS ui_map_child(parent_ui_map_id INTEGER NOT NULL,child_ui_map_id INTEGER NOT NULL,x0 REAL,y0 REAL,x1 REAL,y1 REAL,PRIMARY KEY(parent_ui_map_id,child_ui_map_id),CHECK((x0 IS NULL AND y0 IS NULL AND x1 IS NULL AND y1 IS NULL) OR (x0 IS NOT NULL AND y0 IS NOT NULL AND x1 IS NOT NULL AND y1 IS NOT NULL AND x0<x1 AND y0<y1)))");
      s.execute("CREATE TABLE IF NOT EXISTS world_map_projection(snapshot_id TEXT PRIMARY KEY,transform_version TEXT NOT NULL,projected_at TEXT NOT NULL)");
      s.executeUpdate("DELETE FROM ui_map_child"); s.executeUpdate("DELETE FROM ui_map");
    }
    var grouped = assignments.groupByLong("UiMapID");
    List<CsvTable.Row> selected = new ArrayList<>();
    for (var map : maps.rows()) if (map.intOr("ID", -1) == 947 || Set.of(947,1414,1415).contains(map.intOr("ParentUiMapID", -1))) selected.add(map);
    selected.sort(Comparator.comparingInt(r -> r.intOr("ID", -1)));
    if (selected.stream().noneMatch(r -> r.intOr("ID", -1) == 947)) throw new IllegalArgumentException("Azeroth 947 missing from UiMap");
    for (var map : selected) {
      int id = map.intOr("ID", -1), parent = map.intOr("ParentUiMapID", -1);
      try (var p = c.prepareStatement("INSERT INTO ui_map VALUES (?,?,?)")) {
        p.setInt(1,id); p.setInt(2,parent); p.setString(3,map.get("Name_lang")); p.executeUpdate();
      }
      if (id == 947) continue;
      double[] bounds = parent == 947 && map.intOr("Type", -1) == 2
          ? continentPlacement(grouped.getOrDefault(947L,List.of()), grouped.getOrDefault((long)id,List.of()))
          : placement(grouped.getOrDefault((long)parent,List.of()), grouped.getOrDefault((long)id,List.of()));
      try (var p = c.prepareStatement("INSERT INTO ui_map_child VALUES (?,?,?,?,?,?)")) {
        p.setInt(1,parent); p.setInt(2,id);
        for(int i=0;i<4;i++) if(bounds == null) p.setNull(i+3,Types.REAL); else p.setDouble(i+3,bounds[i]);
        p.executeUpdate();
      }
    }
  }

  static double[] continentPlacement(List<CsvTable.Row> parents, List<CsvTable.Row> children) {
    for (var parent : parents.stream().sorted(Comparator.comparingInt(r -> r.intOr("ID",0))).toList())
      if (children.stream().anyMatch(child -> child.intOr("MapID",-1)==parent.intOr("MapID",-2))) {
        double[] bounds = {parent.doubleOr("UiMin_0",Double.NaN),parent.doubleOr("UiMin_1",Double.NaN),parent.doubleOr("UiMax_0",Double.NaN),parent.doubleOr("UiMax_1",Double.NaN)};
        if (Arrays.stream(bounds).allMatch(Double::isFinite) && bounds[0]>=0 && bounds[1]>=0
            && bounds[2]<=1 && bounds[3]<=1 && bounds[0]<bounds[2] && bounds[1]<bounds[3]) return bounds;
      }
    return null;
  }

  static double[] placement(List<CsvTable.Row> parents, List<CsvTable.Row> children) {
    Comparator<CsvTable.Row> order = Comparator.comparingInt((CsvTable.Row r) -> r.intOr("OrderIndex",0)).thenComparingInt(r -> r.intOr("ID",0));
    for (var child : children.stream().sorted(order).toList()) for (var parent : parents.stream().sorted(order).toList()) {
      if (child.intOr("MapID",-1) != parent.intOr("MapID",-2)) continue;
      double dx=parent.doubleOr("Region_3",0)-parent.doubleOr("Region_0",0);
      double dy=parent.doubleOr("Region_4",0)-parent.doubleOr("Region_1",0);
      if(dx<=0 || dy<=0) continue;
      double x0=projectAxis(parent,child.doubleOr("Region_4",Double.NaN),1,0,dy);
      double x1=projectAxis(parent,child.doubleOr("Region_1",Double.NaN),1,0,dy);
      double y0=projectAxis(parent,child.doubleOr("Region_3",Double.NaN),0,1,dx);
      double y1=projectAxis(parent,child.doubleOr("Region_0",Double.NaN),0,1,dx);
      if (Double.isFinite(x0+y0+x1+y1) && x0<x1 && y0<y1 && x1>0 && y1>0 && x0<1 && y0<1)
        return new double[]{Math.max(0,x0),Math.max(0,y0),Math.min(1,x1),Math.min(1,y1)};
    }
    return null;
  }
  private static double projectAxis(CsvTable.Row parent,double world,int region,int ui,double span) {
    double max=parent.doubleOr("UiMax_"+ui,Double.NaN), min=parent.doubleOr("UiMin_"+ui,Double.NaN);
    return max-(world-parent.doubleOr("Region_"+region,Double.NaN))/span*(max-min);
  }
  private static Connection connect(Path database) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:"+database); }
}
