package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.CsvTable;
import dev.wowforever.importer.clientdata.MiniJson;
import dev.wowforever.importer.clientdata.WorldMapTransform;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Durable client/Vanilla evidence capture followed by an independently replayable travel overlay. */
public final class MapLayersArchiveWorkflow {
  public static final String BUILD="1.60.1.70450", TRANSFORM="map-layers-v1";
  private static final List<String> TABLES=List.of("UiMapAssignment","TaxiNodes","TaxiPath","TaxiPathNode","AreaTrigger");
  private MapLayersArchiveWorkflow() {}
  public static void main(String[] args) throws Exception {
    if(args.length==5 && args[0].equals("capture")) System.out.println(ingest(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4])));
    else if(args.length==3 && args[0].equals("project")) project(Path.of(args[1]),args[2]);
    else throw new IllegalArgumentException("Usage: capture DATABASE CLIENT_BUILD_DIR VM_DATABASE VM_LOCK | project DATABASE SNAPSHOT");
  }

  /** CLIENT_BUILD_DIR contains csv/Table.csv and acquisition-manifest.json from the verified client acquisition. */
  public static String ingest(Path database,Path clientBuildDir,Path vmDatabase,Path vmLock) throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null) throw new IllegalStateException("Another import is using "+database);
      var artifacts=new ArrayList<SourceArchive.Artifact>();
      byte[] manifestBytes=Files.readAllBytes(clientBuildDir.resolve("acquisition-manifest.json"));
      if(!(MiniJson.parse(new String(manifestBytes,StandardCharsets.UTF_8)) instanceof Map<?,?> manifest) || !BUILD.equals(manifest.get("build")))
        throw new IllegalArgumentException("Expected acquisition manifest for client "+BUILD);
      Map<String,Map<?,?>> files=new HashMap<>();
      if(manifest.get("files") instanceof List<?> entries) for(Object entry:entries)
        if(entry instanceof Map<?,?> file) files.put(Objects.toString(file.get("table")),file);
      for(String table:TABLES) {
        Map<?,?> file=Objects.requireNonNull(files.get(table),"Missing manifest record "+table);
        byte[] bytes=Files.readAllBytes(clientBuildDir.resolve("csv/"+table+".csv"));
        String expected=Objects.toString(file.get("sha256"));
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected))
          throw new IllegalArgumentException("Client CSV hash differs from acquisition manifest: "+table);
        artifacts.add(new SourceArchive.Artifact("client/"+table+".csv",Objects.toString(file.get("url"),null),bytes));
      }
      artifacts.add(new SourceArchive.Artifact("client/acquisition-manifest.json",null,manifestBytes));
      byte[] lockBytes=Files.readAllBytes(vmLock);
      if(!(MiniJson.parse(new String(lockBytes,StandardCharsets.UTF_8)) instanceof Map<?,?> vmPin)
          || !"464179081673cfd240f7ffe7f0daf96bca0b5a70".equals(vmPin.get("commit")))
        throw new IllegalArgumentException("Expected pinned VMaNGOS4641790 source lock");
      artifacts.add(new SourceArchive.Artifact("vm/serverdb.lock",null,lockBytes));
      String hash;
      try(var in=Files.newInputStream(vmDatabase)) {
        var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
        while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
        hash=HexFormat.of().formatHex(digest.digest());
      }
      String triggerQuery="SELECT t.id,t.name,t.target_map,t.target_position_x,t.target_position_y,t.target_position_z,a.map_id,a.x,a.y,a.z,a.build,t.patch FROM areatrigger_teleport t JOIN areatrigger_template a ON a.id=t.id WHERE t.patch=(SELECT max(t2.patch) FROM areatrigger_teleport t2 WHERE t2.id=t.id) AND a.build=(SELECT max(a2.build) FROM areatrigger_template a2 WHERE a2.id=a.id) ORDER BY t.id";
      String flightQuery="SELECT c.id,c.guid,c.map,c.position_x,c.position_y,c.position_z,t.name,t.npc_flags FROM creature c JOIN creature_template t ON t.entry=c.id WHERE t.patch=(SELECT max(t2.patch) FROM creature_template t2 WHERE t2.entry=t.entry) AND (t.npc_flags & 8)<>0 ORDER BY c.id,c.guid";
      try(Connection vm=DriverManager.getConnection("jdbc:sqlite:file:"+vmDatabase.toAbsolutePath()+"?mode=ro")) {
        artifacts.add(new SourceArchive.Artifact("vm/teleports-derived.csv",null,export(vm,triggerQuery,List.of("id","name","target_map","target_x","target_y","target_z","map_id","x","y","z","build","patch"))));
        artifacts.add(new SourceArchive.Artifact("vm/flightmasters-derived.csv",null,export(vm,flightQuery,List.of("id","guid","map_id","x","y","z","name","npc_flags"))));
      }
      String provenance="format=derived-query-export-v1\nvm_database_sha256="+hash+"\nvm_release=https://github.com/vmangos/core/releases/download/db_latest/db-sqlite-4641790.zip\nteleports_query="+triggerQuery+"\nflightmasters_query="+flightQuery+"\n";
      artifacts.add(new SourceArchive.Artifact("vm/derived-provenance.properties",null,provenance.getBytes(StandardCharsets.UTF_8)));
      try(Connection c=connect(database)) {
        c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("map-layers-client-and-vanilla","client:"+BUILD+";vm:4641790","forever+vanilla",BUILD,"enUS"),artifacts);
        c.commit();return snapshot;
      }
    }
  }
  private static byte[] export(Connection c,String sql,List<String> header) throws SQLException {
    var out=new StringBuilder(String.join(",",header)).append('\n');
    try(var s=c.createStatement();var r=s.executeQuery(sql)) {
      while(r.next()) { for(int i=1;i<=header.size();i++) {
        if(i>1)out.append(',');String value=Objects.toString(r.getObject(i),"");out.append('"').append(value.replace("\"","\"\"")).append('"');
      }out.append('\n'); }
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }
  public static void project(Path database,String snapshot) throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null) throw new IllegalStateException("Another import is using "+database);
      Path candidate=Files.createTempFile(database.getParent(),".map-layers-",".sqlite");
      try {
        Files.delete(candidate);
        try(Connection c=connect(database);var p=c.prepareStatement("VACUUM INTO ?")){p.setString(1,candidate.toString());p.execute();}
        try(Connection c=connect(candidate)) {
          c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
          var metadata=SourceArchive.snapshot(c,snapshot).metadata();
          if(!metadata.source().equals("map-layers-client-and-vanilla") || !BUILD.equals(metadata.build())) throw new IllegalArgumentException("Incompatible travel source snapshot");
          Map<String,CsvTable> inputs=new HashMap<>();
          for(var artifact:SourceArchive.artifacts(c,snapshot)) if(artifact.path().endsWith(".csv"))
            inputs.put(artifact.path(),CsvTable.read(artifact.path(),new StringReader(new String(artifact.bytes(),StandardCharsets.UTF_8))));
          apply(c,inputs,snapshot);
          AdditiveProjection.record(c,Map.of("map-layers-"+snapshot,snapshot),"map-layers-","map_layers",TRANSFORM,
              "Client travel maps "+BUILD+"; Vanilla teleports/flightmaster matching; absent custom-instance triggers remain unknown");
          put(c,"map_layers_snapshot_id",snapshot);put(c,"map_layers_source_build",BUILD);
          put(c,"map_layers_source_label","Flight paths: client "+BUILD+" · Entrances/exits: VMaNGOS Vanilla 4641790 with matching client trigger positions; Forever entrances not yet recorded");
          c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")) {if(!r.next()||!r.getString(1).equals("ok"))throw new SQLException("Travel candidate integrity check failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {Files.deleteIfExists(candidate);}
    }
  }

  private record Zone(int id,int uiMap) {}
  private record Pin(int zone,int ui,double x,double y) {}
  private record Instance(int id,int worldMap,String name,boolean raid,String levels) {}
  private record Access(int zone,int instance,int trigger,String name,double x,double y,boolean raid,boolean exit,String tooltip) {}

  static void apply(Connection c,Map<String,CsvTable> inputs,String snapshot) throws Exception {
    var assignments=WorldMapTransform.load(required(inputs,"client/UiMapAssignment.csv"));
    List<Zone> zones=new ArrayList<>();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT z.id,z.ui_map_id FROM zone z WHERE z.ui_map_id IS NOT NULL AND z.id=(SELECT min(z2.id) FROM zone z2 WHERE z2.ui_map_id=z.ui_map_id)")) {
      while(r.next()) zones.add(new Zone(r.getInt(1),r.getInt(2)));
    }
    // Known zone identities constrain projection; map rectangles alone do not establish terrain AreaID.
    Set<Integer> uiMaps=new HashSet<>();for(var zone:zones)uiMaps.add(zone.uiMap);
    assignments=assignments.stream().filter(a -> uiMaps.contains(a.uiMapId())).toList();
    createTables(c);
    var nodes=required(inputs,"client/TaxiNodes.csv").indexByLong("ID");
    var paths=required(inputs,"client/TaxiPath.csv");
    var flightmasters=required(inputs,"vm/flightmasters-derived.csv");
    Map<Integer,Pin> nodePins=new HashMap<>();Set<Integer> publicNodes=new HashSet<>();
    for(var entry:nodes.entrySet()) {
      var node=entry.getValue();int flags=node.intOr("Flags",0), factionBits=flags&3;
      if(factionBits==0)continue; // nodes without player faction visibility are not public flight paths.
      int world=node.intOr("ContinentID",-1),id=entry.getKey().intValue();
      Pin pin=pin(assignments,zones,world,node.doubleOr("Pos_0",Double.NaN),node.doubleOr("Pos_1",Double.NaN),node.doubleOr("Pos_2",Double.NaN));
      if(pin==null)continue;nodePins.put(id,pin);publicNodes.add(id);
      Integer npc=null;double nearest=3;
      for(var fm:flightmasters.rows()) {
        var pos=WorldMapTransform.projectTo(assignments,pin.ui, fm.intOr("map_id",-1),fm.doubleOr("x",0),fm.doubleOr("y",0),fm.doubleOr("z",0));
        if(pos.isEmpty())continue;double distance=Math.hypot(pos.get().x()-pin.x,pos.get().y()-pin.y);
        if(distance<=nearest && npcExists(c,fm.intOr("id",-1))) {nearest=distance;npc=fm.intOr("id",-1);}
      }
      try(var p=c.prepareStatement("INSERT INTO map_flight_node VALUES (?,?,?,?,?,?,?,?)")) {
        p.setInt(1,pin.zone);p.setInt(2,id);p.setString(3,node.get("Name_lang"));p.setString(4,factionBits==1?"ALLIANCE":factionBits==2?"HORDE":"NEUTRAL");p.setDouble(5,pin.x);p.setDouble(6,pin.y);
        if(npc==null)p.setNull(7,Types.INTEGER);else p.setInt(7,npc);p.setString(8,snapshot);p.executeUpdate();
      }
    }
    for(var path:paths.rows()) {
      int from=path.intOr("FromTaxiNode",-1),to=path.intOr("ToTaxiNode",-1);
      if(!publicNodes.contains(from)||!publicNodes.contains(to))continue;
      try(var p=c.prepareStatement("INSERT OR IGNORE INTO map_flight_connection VALUES (?,?,?,?)")) {
        p.setInt(1,from);p.setInt(2,to);p.setString(3,nodes.get((long)to).get("Name_lang"));p.setString(4,snapshot);p.executeUpdate();
      }
    }
    routes(c,assignments,zones,nodes,paths,required(inputs,"client/TaxiPathNode.csv"),publicNodes,snapshot);
    entrances(c,assignments,zones,required(inputs,"vm/teleports-derived.csv"),required(inputs,"client/AreaTrigger.csv"),snapshot);
  }

  private static void entrances(Connection c,List<WorldMapTransform.Assignment> assignments,List<Zone> zones,CsvTable teleports,CsvTable clientTriggers,String snapshot) throws SQLException {
    Map<Integer,Instance> instances=new HashMap<>();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT id,coalesce(client_map_id,instance_id),name,content_type,level_min,level_max FROM instance")) {
      while(r.next()) {
        int world=r.getInt(2);if(r.wasNull())continue;
        String levels=r.getObject(5)==null?"Level not recorded":"Levels "+r.getInt(5)+(r.getObject(6)==null?"+":"–"+r.getInt(6));
        instances.putIfAbsent(world,new Instance(r.getInt(1),world,r.getString(3),Objects.toString(r.getString(4),"").startsWith("RAID"),levels));
      }
    }
    var client=clientTriggers.indexByLong("ID");var access=new ArrayList<Access>();
    for(var trigger:teleports.rows()) {
      int id=trigger.intOr("id",-1),own=trigger.intOr("map_id",-1),target=trigger.intOr("target_map",-1);
      Instance entering=instances.get(target),leaving=instances.get(own);
      if(entering!=null && leaving!=null) {
        try(var p=c.prepareStatement("INSERT OR IGNORE INTO map_instance_entry_context VALUES (?,?,?,?)")) {
          p.setInt(1,entering.id);p.setInt(2,leaving.id);p.setInt(3,id);p.setString(4,snapshot);p.executeUpdate();
        }
      } else if(entering!=null) {
        var clientRow=client.get((long)id);
        double x=trigger.doubleOr("x",Double.NaN),y=trigger.doubleOr("y",Double.NaN),z=trigger.doubleOr("z",Double.NaN);
        boolean clientPosition=clientRow!=null && clientRow.intOr("ContinentID",-2)==own;
        if(clientPosition){x=clientRow.doubleOr("Pos_0",x);y=clientRow.doubleOr("Pos_1",y);z=clientRow.doubleOr("Pos_2",z);}
        Pin pin=pin(assignments,zones,own,x,y,z);
        if(pin!=null) access.add(new Access(pin.zone,entering.id,id,trigger.get("name"),pin.x,pin.y,entering.raid,false,
            entering.name+" · "+(entering.raid?"Raid":"Dungeon")+" · "+entering.levels+" · "+(clientPosition?"Client "+BUILD+" trigger position; ":"Vanilla trigger position; ")+"Vanilla teleport destination"));
      } else if(leaving!=null) {
        Pin pin=pin(assignments,zones,target,trigger.doubleOr("target_x",Double.NaN),trigger.doubleOr("target_y",Double.NaN),trigger.doubleOr("target_z",Double.NaN));
        if(pin!=null) access.add(new Access(pin.zone,leaving.id,id,"Exit of "+leaving.name,pin.x,pin.y,leaving.raid,true,"Exit of "+leaving.name+" · Vanilla teleport landing point"));
      }
    }
    for(var a:access) {
      boolean close=access.stream().anyMatch(other -> other.instance==a.instance && other.zone==a.zone && other.exit!=a.exit && Math.hypot(other.x-a.x,other.y-a.y)<=2);
      if(a.exit && close) continue;
      try(var p=c.prepareStatement("INSERT INTO map_instance_access VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
        p.setInt(1,a.zone);p.setInt(2,a.instance);p.setInt(3,a.trigger);p.setString(4,a.name);p.setDouble(5,a.x);p.setDouble(6,a.y);p.setBoolean(7,a.raid);p.setBoolean(8,a.exit);p.setBoolean(9,!a.exit&&close);
        p.setString(10,a.tooltip+(!a.exit&&close?" · Exit lands here":""));p.setString(11,snapshot);
        p.executeUpdate();
      }
    }
  }

  private static void routes(Connection c,List<WorldMapTransform.Assignment> assignments,List<Zone> zones,Map<Long,CsvTable.Row> nodes,CsvTable paths,CsvTable points,Set<Integer> publicNodes,String snapshot) throws SQLException {
    var grouped=points.groupByLong("PathID");
    for(var path:paths.rows()) {
      int from=path.intOr("FromTaxiNode",-1),to=path.intOr("ToTaxiNode",-1),pathId=path.intOr("ID",-1);
      if(!publicNodes.contains(from)||!publicNodes.contains(to))continue;
      var line=grouped.getOrDefault((long)pathId,List.of()).stream().sorted(Comparator.comparingInt(p -> p.intOr("NodeIndex",0))).toList();
      for(var zone:zones) {
        int sequence=0,segment=-1;double[] last=null;
        for(int i=1;i<line.size();i++) {
          var previous=line.get(i-1);var current=line.get(i);int map=current.intOr("ContinentID",-1);
          if(map!=previous.intOr("ContinentID",-2))continue;
          var assignment=assignments.stream().filter(a -> a.uiMapId()==zone.uiMap && a.mapId()==map).min(Comparator.comparingInt(WorldMapTransform.Assignment::id));
          if(assignment.isEmpty())continue;
          double[] p=unbounded(assignment.get(),previous.doubleOr("Loc_0",Double.NaN),previous.doubleOr("Loc_1",Double.NaN));
          double[] q=unbounded(assignment.get(),current.doubleOr("Loc_0",Double.NaN),current.doubleOr("Loc_1",Double.NaN));
          double[] clipped=clip(p[0],p[1],q[0],q[1]);if(clipped==null)continue;
          boolean continuous=last!=null && Math.hypot(last[0]-clipped[0],last[1]-clipped[1])<.000001;
          if(!continuous){segment++;sequence=0;}
          for(int j=continuous?1:0;j<2;j++) try(var statement=c.prepareStatement("INSERT INTO map_flight_route VALUES (?,?,?,?,?,?,?,?)")) {
            statement.setInt(1,zone.id);statement.setInt(2,pathId);statement.setString(3,nodes.get((long)to).get("Name_lang"));statement.setInt(4,sequence++);statement.setDouble(5,clipped[j*2]);statement.setDouble(6,clipped[j*2+1]);statement.setString(7,snapshot);statement.setInt(8,segment);statement.executeUpdate();
          }
          last=new double[]{clipped[2],clipped[3]};
        }
      }
    }
  }
  private static double[] unbounded(WorldMapTransform.Assignment a,double x,double y) {
    return new double[]{100*(a.uiMaxX()-(y-a.worldMinY())/(a.worldMaxY()-a.worldMinY())*(a.uiMaxX()-a.uiMinX())),100*(a.uiMaxY()-(x-a.worldMinX())/(a.worldMaxX()-a.worldMinX())*(a.uiMaxY()-a.uiMinY()))};
  }
  /** Liang–Barsky clipping in percent coordinates; returns both authentic segment boundary intersections. */
  static double[] clip(double x0,double y0,double x1,double y1) {
    if(!Double.isFinite(x0+y0+x1+y1))return null;
    double dx=x1-x0,dy=y1-y0,lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={x0,100-x0,y0,100-y0};
    for(int i=0;i<4;i++) {
      if(p[i]==0){if(q[i]<0)return null;continue;}
      double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);if(lo>hi)return null;
    }
    return new double[]{x0+lo*dx,y0+lo*dy,x0+hi*dx,y0+hi*dy};
  }
  private static Pin pin(List<WorldMapTransform.Assignment> assignments,List<Zone> zones,int map,double x,double y,double z) {
    var point=WorldMapTransform.project(assignments,map,x,y,z);if(point.isEmpty())return null;
    var p=point.get();return zones.stream().filter(zone -> zone.uiMap==p.uiMapId()).findFirst().map(zone -> new Pin(zone.id,zone.uiMap,p.x(),p.y())).orElse(null);
  }
  private static boolean npcExists(Connection c,int id) throws SQLException {
    try(var p=c.prepareStatement("SELECT 1 FROM npc WHERE id=?")){p.setInt(1,id);try(var r=p.executeQuery()){return r.next();}}
  }
  private static void createTables(Connection c) throws SQLException {
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS map_flight_node(zone_id INTEGER NOT NULL,node_id INTEGER NOT NULL,name TEXT NOT NULL,faction TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,npc_id INTEGER,source_snapshot_id TEXT NOT NULL,PRIMARY KEY(zone_id,node_id))");
      s.execute("CREATE TABLE IF NOT EXISTS map_flight_connection(from_node_id INTEGER NOT NULL,to_node_id INTEGER NOT NULL,to_name TEXT NOT NULL,source_snapshot_id TEXT NOT NULL,PRIMARY KEY(from_node_id,to_node_id))");
      s.execute("CREATE TABLE IF NOT EXISTS map_flight_route(zone_id INTEGER NOT NULL,path_id INTEGER NOT NULL,destination TEXT NOT NULL,point_index INTEGER NOT NULL,x REAL NOT NULL,y REAL NOT NULL,source_snapshot_id TEXT NOT NULL,segment_index INTEGER NOT NULL,PRIMARY KEY(zone_id,path_id,segment_index,point_index))");
      s.execute("CREATE TABLE IF NOT EXISTS map_instance_access(zone_id INTEGER NOT NULL,instance_id INTEGER NOT NULL,trigger_id INTEGER NOT NULL,name TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,raid INTEGER NOT NULL,is_exit INTEGER NOT NULL,exit_here INTEGER NOT NULL,tooltip TEXT NOT NULL,source_snapshot_id TEXT NOT NULL,PRIMARY KEY(zone_id,trigger_id,is_exit))");
      s.execute("CREATE TABLE IF NOT EXISTS map_instance_entry_context(instance_id INTEGER NOT NULL,parent_instance_id INTEGER NOT NULL,trigger_id INTEGER NOT NULL,source_snapshot_id TEXT NOT NULL,PRIMARY KEY(instance_id,parent_instance_id,trigger_id))");
      s.execute("CREATE INDEX IF NOT EXISTS idx_map_flight_route_zone ON map_flight_route(zone_id,path_id,segment_index,point_index)");
      s.execute("CREATE INDEX IF NOT EXISTS idx_map_flight_connection_from ON map_flight_connection(from_node_id)");
      s.execute("CREATE INDEX IF NOT EXISTS idx_map_instance_access_instance ON map_instance_access(instance_id)");
      for(String name:List.of("map_flight_node","map_flight_connection","map_flight_route","map_instance_access","map_instance_entry_context"))s.executeUpdate("DELETE FROM "+name);
    }
  }
  private static CsvTable required(Map<String,CsvTable> tables,String name) {return Objects.requireNonNull(tables.get(name),"Missing archived travel input "+name);}
  private static void put(Connection c,String key,String value) throws SQLException {try(var p=c.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES (?,?)")){p.setString(1,key);p.setString(2,value);p.executeUpdate();}}
  private static Path existing(Path database) throws java.io.IOException {database=database.toAbsolutePath().normalize();if(!Files.isRegularFile(database))throw new java.io.IOException("Existing offline database required: "+database);return database;}
  private static Connection connect(Path database) throws SQLException {return DriverManager.getConnection("jdbc:sqlite:"+database);}
}
