package dev.wowforever.importer;

import dev.wowforever.importer.fs.SourcePaths;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.lua.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit offline, additive projection. Archived bytes remain authoritative. */
public final class CollectorProjection {
  private CollectorProjection() {}
  public static void project(Path database, String ingestion) throws Exception {
    database=database.toAbsolutePath().normalize();
    if (!Files.isRegularFile(database)) throw new IllegalArgumentException("Source database does not exist");
    SourcePaths.rejectSymlinks(database);
    Path lockPath=database.resolveSibling(database.getFileName()+".lock");
    if(Files.isSymbolicLink(lockPath)) throw new IllegalArgumentException("Symbolic lock refused");
    try(var channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null) throw new IllegalStateException("Another import is using "+database);
      try(var c=DriverManager.getConnection("jdbc:sqlite:"+database)) {
        c.setAutoCommit(false);
        try {
          List<String> snapshots=new ArrayList<>();
          try(var s=c.prepareStatement("SELECT role,snapshot_id FROM source_ingestion_input WHERE ingestion_id=?")) {
            s.setString(1,ingestion);
            try(var r=s.executeQuery()){while(r.next()) {
              if(!r.getString(1).equals("collector") && !r.getString(1).matches("collector-[0-9a-f]{64}")) throw new IllegalArgumentException("Expected collector-only ingestion");
              if(!r.getString(1).equals("collector") && !r.getString(1).equals("collector-"+r.getString(2)))
                throw new IllegalArgumentException("Collector role/snapshot mismatch");
              snapshots.add(r.getString(2));
            }}
          }
          if(snapshots.isEmpty()) throw new IllegalArgumentException("Unknown collector ingestion");
          initialize(c);
          for(String snapshot:snapshots) {
            var metadata=SourceArchive.snapshot(c,snapshot).metadata();
            if(!metadata.source().equals("wowforever-collector") || !metadata.product().equals("forever") || !metadata.build().matches("1\\.60\\.[0-9]+\\.[0-9]+"))
              throw new IllegalArgumentException("Expected Forever collector snapshot");
            var files=SourceArchive.artifacts(c,snapshot);
            if(files.size()!=1) throw new IllegalArgumentException("Expected one collector artifact");
            LuaTable root=CollectorArchiveWorkflow.read(new String(files.getFirst().bytes(),StandardCharsets.UTF_8));
            if(id(root,"schema")!=1) throw new IllegalArgumentException("Unsupported collector schema");
            LuaTable session=table(root.get("session"));
            String current=str(session,"build")+"."+str(session,"buildNumber");
            if(!current.equals(metadata.build()) || id(session,"interface")!=16001)
              throw new IllegalArgumentException("Collector session does not match archived build");
            boolean mixedBuild=table(session.get("builds")).entries().stream()
                .anyMatch(e -> !LuaValues.asString(e.getValue()).orElse("").equals(current));
            Set<String> seen=new HashSet<>();
            for(String category:List.of("loot","vendors","trainers","quests","encounters","apiFailures")) {
              for(var entry:table(root.get(category)).entries()) {
                if(!(entry.getKey() instanceof LuaNumber key) || !key.isInteger() || key.asLong()<1) continue;
                long ordinal=key.asLong(); LuaTable row=table(entry.getValue());
                String status="raw";
                if(mixedBuild) status="mixed-build-unresolved";
                else if(category.equals("loot")) {
                  LuaTable source=table(row.get("source"));String kind=str(row,"lootKind"),type=str(source,"type");
                  String spawn=str(source,"spawnKey");long entity=id(source,"id");
                  if(kind.equals("corpse") && !(row.get("sourceHidden") instanceof LuaBoolean hidden && hidden.value()) && !spawn.isBlank() && entity>0 && (type.equals("Creature") || type.equals("GameObject"))) {
                    status=projectItems(c,snapshot,category,ordinal,row,entity,type,seen,type+":"+entity+":"+spawn+":"+kind)?"projected":"unresolved";
                  } else status=kind.equals("unknown")?"unknown-loot-kind":"unresolved";
                } else if(category.equals("vendors")) {
                  LuaTable npc=table(row.get("npc"));
                  if(str(npc,"type").equals("Creature") && id(npc,"id")>0)
                    status=projectItems(c,snapshot,category,ordinal,row,id(npc,"id"),"vendor",new HashSet<>(),"vendor")?"projected":"unresolved";
                }
                try(var s=c.prepareStatement("INSERT OR IGNORE INTO collector_observation VALUES (?,?,?,?,?)")) {
                  s.setString(1,snapshot);s.setString(2,category);s.setLong(3,ordinal);s.setString(4,json(row));s.setString(5,status);s.executeUpdate();
                }
              }
            }
          }
          var additions = new TreeMap<String,String>();
          for (String snapshot : snapshots) additions.put("collector-"+snapshot,snapshot);
          AdditiveProjection.record(c,additions,null,"collector","collector-project-v1",
              "Presence only; unknown entities and encounter hints retained raw; synthetic tests do not establish real observation acceptance");
          c.commit();
        } catch(Exception e) {c.rollback();throw e;}
      }
    }
  }
  static void initialize(Connection c) throws SQLException {
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS collector_observation(snapshot_id TEXT NOT NULL,category TEXT NOT NULL,ordinal INTEGER NOT NULL,payload_json TEXT NOT NULL,status TEXT NOT NULL,PRIMARY KEY(snapshot_id,category,ordinal))");
      s.execute("CREATE TABLE IF NOT EXISTS collector_fact(snapshot_id TEXT NOT NULL,category TEXT NOT NULL,ordinal INTEGER NOT NULL,item_id INTEGER NOT NULL,entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,PRIMARY KEY(snapshot_id,category,ordinal,item_id,entity_type,entity_id))");
      s.execute("CREATE INDEX IF NOT EXISTS idx_collector_fact_relation ON collector_fact(category,entity_type,entity_id,item_id)");
    }
  }
  private static boolean projectItems(Connection c,String snapshot,String category,long ordinal,LuaTable row,long entity,String type,Set<String> seen,String sourceKey) throws SQLException {
    String entityTable=type.equals("GameObject")?"game_object":"npc";
    if(!exists(c,entityTable,entity)) return false;
    boolean projected=false;
    for(var entry:table(row.get("items")).entries()) {
      LuaTable item=table(entry.getValue()); long itemId=id(item,"itemID");
      if(itemId<1 || !exists(c,"item",itemId) || item.get("currencyID") instanceof LuaNumber) continue;
      String link=str(item,"link");
      var match=java.util.regex.Pattern.compile("\\|Hitem:([0-9]+):").matcher(link);
      if(!match.find() || !match.group(1).equals(Long.toString(itemId))) continue;
      boolean fresh=seen.add(sourceKey+":"+itemId);
      if(fresh) {
      String relation=type.equals("vendor")?"item_vendor":type.equals("Creature")?"item_npc_drop":"item_object_drop";
      String column=type.equals("GameObject")?"object_id":"npc_id";
      String columns="item_id,"+column+(type.equals("Creature")?",origin":"");
      try(var s=c.prepareStatement("INSERT OR IGNORE INTO "+relation+"("+columns+") VALUES (?,?"+(type.equals("Creature")?",'collector'":"")+")")) {
        s.setLong(1,itemId);s.setLong(2,entity);s.executeUpdate();
      }
      String factCategory=type.equals("vendor")?"vendor":type.equals("Creature")?"npc-drop":"object-drop";
      try(var s=c.prepareStatement("INSERT OR IGNORE INTO collector_fact VALUES (?,?,?,?,?,?)")) {
        s.setString(1,snapshot);s.setString(2,factCategory);s.setLong(3,ordinal);s.setLong(4,itemId);s.setString(5,type.equals("GameObject")?"object":"npc");s.setLong(6,entity);s.executeUpdate();
      }
      }
      if(type.equals("Creature") && id(table(row.get("instance")),"mapID")>0) {
        try(var q=c.prepareStatement("SELECT b.id FROM boss b JOIN instance i ON i.id=b.instance_id WHERE b.npc_id=? AND i.client_map_id=?")) {
          q.setLong(1,entity);q.setLong(2,id(table(row.get("instance")),"mapID"));
          try(var results=q.executeQuery()){while(results.next()) {
            if(!seen.add(sourceKey+":"+itemId+":boss:"+results.getLong(1)))continue;
            try(var fact=c.prepareStatement("INSERT OR IGNORE INTO collector_fact VALUES (?,'boss-drop',?,?,'boss',?)")) {
              fact.setString(1,snapshot);fact.setLong(2,ordinal);fact.setLong(3,itemId);fact.setLong(4,results.getLong(1));fact.executeUpdate();
            }
          }}
        }
      }
      projected=true;
    }
    return projected;
  }
  private static boolean exists(Connection c,String table,long id) throws SQLException {
    try(var s=c.prepareStatement("SELECT 1 FROM "+table+" WHERE id=?")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next();}}
  }
  private static LuaTable table(LuaValue v){return v instanceof LuaTable t?t:new LuaTable();}
  private static String str(LuaTable t,String key){return LuaValues.asString(t.get(key)).orElse("");}
  private static long id(LuaTable t,String key){return LuaValues.asLong(t.get(key)).orElse(-1);}
  private static String quote(String text){
    StringBuilder out=new StringBuilder("\"");
    for(char ch:text.toCharArray()) {
      if(ch=='"' || ch=='\\')out.append('\\').append(ch);
      else if(ch<32)out.append(String.format("\\u%04x",(int)ch));
      else out.append(ch);
    }
    return out.append('"').toString();
  }

  private static String json(LuaValue value) {
    if(value instanceof LuaTable t) return "{"+String.join(",",t.entries().stream().map(e->quote(e.getKey() instanceof LuaString s?s.value():e.getKey().toString())+":"+json(e.getValue())).toList())+"}";
    if(value instanceof LuaString s)return quote(s.value());
    if(value instanceof LuaNumber n && !Double.isFinite(n.value()))return quote("nonfinite:"+n);
    if(value instanceof LuaNil)return "null";
    return value.toString();
  }
}
