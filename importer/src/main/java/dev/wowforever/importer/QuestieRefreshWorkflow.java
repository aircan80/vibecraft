package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.source.SourceSpec;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Differential correction refresh: replace a list only when the canonical list still equals the old source. */
public final class QuestieRefreshWorkflow {
  public static final String TRANSFORM="questie-corrections-three-way-v1";
  private static final Map<String,String> GROUPS=Map.of("npc_spawn","npc_id","npc_waypoint","npc_id",
      "object_spawn","object_id","quest_objective","quest_id","quest_objective_point","quest_id");
  private static final Set<String> ADDED=Set.of("quest_giver","quest_source_item","quest_relation","item_npc_drop");
  private QuestieRefreshWorkflow() {}

  public static String ingest(Path database,Path oldRoot,String from,Path newRoot,String to,Path atlasRoot)throws Exception {
    database=existing(database);if(!from.matches("[0-9a-f]{40}")||!to.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("Exact Questie commit pins required");
    var specs=SourceSpec.load();var questie=specs.stream().filter(s->s.key().equals("questiedb")).findFirst().orElseThrow();
    var atlas=specs.stream().filter(s->s.key().equals("atlasloot")).findFirst().orElseThrow();
    if(atlas.baseline()!=null)throw new IllegalArgumentException("Questie staging requires an explicit AtlasLoot baseline migration");
    List<SourceArchive.Artifact> artifacts=new ArrayList<>();
    for(var entry:Map.of("old",oldRoot,"new",newRoot).entrySet())for(String path:questie.paths()) {
      Path file=entry.getValue().resolve(path);rejectSymlinks(file);
      String pin=entry.getKey().equals("old")?from:to;
      artifacts.add(new SourceArchive.Artifact(entry.getKey()+"/"+path,"https://raw.githubusercontent.com/"+questie.owner()+"/"+questie.repo()+"/"+pin+"/"+path,Files.readAllBytes(file)));
    }
    for(String path:atlas.paths()) {
      Path file=atlasRoot.resolve(path);rejectSymlinks(file);
      artifacts.add(new SourceArchive.Artifact("atlas/"+path,"https://raw.githubusercontent.com/"+atlas.owner()+"/"+atlas.repo()+"/"+atlas.commit()+"/"+path,Files.readAllBytes(file)));
    }
    byte[] config;try(var stream=SourceSpec.class.getResourceAsStream("/dev/wowforever/importer/sources.properties")){config=Objects.requireNonNull(stream).readAllBytes();}
    artifacts.add(new SourceArchive.Artifact("sources.properties","",config));
    artifacts.add(new SourceArchive.Artifact("refresh.properties","",("from="+from+"\nto="+to+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      try(Connection c=connect(database)) {
        c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("questiedb-refresh",from+".."+to,"forever","","enUS"),artifacts);
        c.commit();return snapshot;
      }
    }
  }

  public static void project(Path database,String snapshot)throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      Path work=Files.createTempDirectory(database.getParent(),".questie-refresh-");
      try {
        Path input=work.resolve("input"),candidate=work.resolve("candidate.sqlite");
        String version;
        try(Connection c=connect(database)) {
          var metadata=SourceArchive.snapshot(c,snapshot).metadata();if(!metadata.source().equals("questiedb-refresh"))throw new IllegalArgumentException("Expected Questie refresh archive");version=metadata.version();
          SourceArchive.extract(c,snapshot,input);
          try(var p=c.prepareStatement("VACUUM INTO ?")){p.setString(1,candidate.toString());p.execute();}
        }
        Properties refresh=new Properties(),config=new Properties();
        try(var stream=Files.newInputStream(input.resolve("refresh.properties"))){refresh.load(stream);}
        try(var stream=Files.newInputStream(input.resolve("sources.properties"))){config.load(stream);}
        String from=refresh.getProperty("from",""),to=refresh.getProperty("to","");
        if(!from.matches("[0-9a-f]{40}")||!to.matches("[0-9a-f]{40}")||!version.equals(from+".."+to))throw new IllegalArgumentException("Questie refresh pin metadata mismatch");
        for(String file:List.of("foreverItemDB.lua","foreverNpcDB.lua","foreverObjectDB.lua","foreverQuestDB.lua")) {
          if(Files.mismatch(input.resolve("old/data/Forever/"+file),input.resolve("new/data/Forever/"+file))!=-1)
            throw new IllegalArgumentException("Questie base entity changes require explicit migration");
        }
        Path old=work.resolve("old.sqlite"),newer=work.resolve("new.sqlite");
        var before=stage(input.resolve("old"),input.resolve("atlas"),old,from,config);
        var after=stage(input.resolve("new"),input.resolve("atlas"),newer,to,config);
        if(after.skipsOnUiFields()>before.skipsOnUiFields()||Main.unsupportedCounters(after)>Main.unsupportedCounters(before))
          throw new IllegalArgumentException("New Questie corrections introduce unsupported UI data; existing canonical facts retained");
        try(Connection c=connect(candidate);Connection a=connect(old);Connection b=connect(newer)) {
          c.setAutoCommit(false);apply(c,a,b,snapshot);
          AdditiveProjection.record(c,Map.of("questie-refresh-"+snapshot,snapshot),null,"questie_refresh",TRANSFORM,
              "Three-way Questie corrections; exact prior groups replaced; conflicting canonical facts retained");
          try(var p=c.prepareStatement("INSERT OR REPLACE INTO meta VALUES ('questiedb_refresh_commit',?)")){p.setString(1,to);p.executeUpdate();}
          c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new SQLException("Questie refresh integrity failed");}
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Questie refresh foreign keys failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {try(var files=Files.walk(work)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
  }

  private static dev.wowforever.importer.report.ImportReport stage(Path questie,Path atlas,Path db,String commit,Properties config)throws Exception {
    List<SourceSpec> specs=SourceSpec.load(config).stream().map(s->s.key().equals("questiedb")?new SourceSpec(s.key(),s.owner(),s.repo(),commit,s.paths()):s).toList();
    var options=new ImportOptions(db,db.getParent().resolve("empty-cache"),Optional.of(questie),Optional.of(atlas),false,
        db.resolveSibling(db.getFileName()+".build-id"),ClientDataOptions.skipped(),0,Optional.empty());
    try(var out=new PrintStream(OutputStream.nullOutputStream())){return ImportPipeline.runResolved(options,out,specs,null);}
  }

  static void apply(Connection target,Connection old,Connection newer,String snapshot)throws SQLException {
    Set<String> supported=new HashSet<>(GROUPS.keySet());supported.addAll(ADDED);supported.addAll(List.of("npc","quest"));
    try(var scan=old.createStatement();var tables=scan.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
      while(tables.next()) {
        String table=tables.getString(1);
        if(table.equals("meta")||table.startsWith("search")||table.startsWith("sqlite_")||supported.contains(table))continue;
        var columns=columns(old,table);
        if(!multiset(rows(old,table,columns,null,null)).equals(multiset(rows(newer,table,columns,null,null))))
          throw new IllegalArgumentException("Unsupported Questie source delta: "+table);
      }
    }
    try(var s=target.createStatement()) {s.execute("CREATE TABLE IF NOT EXISTS questie_refresh_change(snapshot_id TEXT,table_name TEXT,owner_id TEXT,field TEXT,old_json TEXT,new_json TEXT,current_json TEXT,status TEXT,PRIMARY KEY(snapshot_id,table_name,owner_id,field))");}
    for(String table:List.of("npc","quest")) {
      var columns=columns(old,table);var oldRows=rows(old,table,columns,null,null);var newRows=rows(newer,table,columns,null,null);
      Map<Object,List<Object>> oldById=index(oldRows,columns.indexOf("id")),newById=index(newRows,columns.indexOf("id"));
      if(!oldById.keySet().equals(newById.keySet()))throw new IllegalArgumentException("Questie entity additions/removals require migration");
      for(var e:oldById.entrySet()) {
        List<Object> next=newById.get(e.getKey());if(e.getValue().equals(next))continue;
        var present=rows(target,table,columns,"id",e.getKey());
        if(present.size()!=1)throw new SQLException("Questie target entity missing/ambiguous: "+table+" "+e.getKey());
        List<Object> current=present.getFirst();
        for(int i=0;i<columns.size();i++) {
          Object previous=e.getValue().get(i),value=next.get(i);if(Objects.equals(previous,value))continue;
          Object actual=current.get(i);String status=Objects.equals(actual,value)?"already-current":Objects.equals(actual,previous)?"applied":"conflict-retained";
          if(status.equals("applied"))try(var p=target.prepareStatement("UPDATE "+quote(table)+" SET "+quote(columns.get(i))+"=? WHERE id=?")){p.setObject(1,value);p.setObject(2,e.getKey());p.executeUpdate();}
          audit(target,snapshot,table,String.valueOf(e.getKey()),columns.get(i),previous,value,actual,status);
        }
      }
    }
    for(var group:GROUPS.entrySet()) {
      String table=group.getKey(),owner=group.getValue();var columns=columns(old,table);int key=columns.indexOf(owner);
      Map<Object,List<List<Object>>> previous=group(rows(old,table,columns,null,null),key),next=group(rows(newer,table,columns,null,null),key);
      Set<Object> owners=new HashSet<>(previous.keySet());owners.addAll(next.keySet());
      for(Object id:owners) {
        var a=previous.getOrDefault(id,List.of());var b=next.getOrDefault(id,List.of());if(multiset(a).equals(multiset(b)))continue;
        var current=rows(target,table,columns,owner,id);
        String status=multiset(current).equals(multiset(b))?"already-current":multiset(current).equals(multiset(a))?"applied":"conflict-retained";
        if(status.equals("applied")) {
          try(var p=target.prepareStatement("DELETE FROM "+quote(table)+" WHERE "+quote(owner)+"=?")){p.setObject(1,id);p.executeUpdate();}
          for(var row:b)insert(target,table,columns,row);
        }
        audit(target,snapshot,table,String.valueOf(id),"group",a,b,current,status);
      }
    }
    for(String table:ADDED) {
      var columns=columns(old,table);var previous=multiset(rows(old,table,columns,null,null));var next=multiset(rows(newer,table,columns,null,null));
      for(var row:previous.keySet())if(previous.get(row)>next.getOrDefault(row,0))throw new IllegalArgumentException("Unexpected Questie relation removal: "+table);
      var existing=multiset(rows(target,table,columns,null,null));
      for(var row:next.keySet())if(!previous.containsKey(row)) {
        String status="already-current";
        if(!existing.containsKey(row)) {
          // A drop pair may already carry other rate provenance; never replace that row.
          if(table.equals("item_npc_drop")&&!rows(target,table,columns,"item_id",row.get(columns.indexOf("item_id"))).stream()
              .filter(r->Objects.equals(r.get(columns.indexOf("npc_id")),row.get(columns.indexOf("npc_id")))).toList().isEmpty())status="conflict-retained";
          else {insert(target,table,columns,row);status="applied";}
        }
        audit(target,snapshot,table,ArchiveWorkflow.hash(json(row)),"addition",null,row,null,status);
      }
    }
  }

  private static Map<Object,List<Object>> index(List<List<Object>> rows,int key){Map<Object,List<Object>> out=new HashMap<>();for(var row:rows)out.put(row.get(key),row);return out;}
  private static Map<Object,List<List<Object>>> group(List<List<Object>> rows,int key){Map<Object,List<List<Object>>> out=new HashMap<>();for(var row:rows)out.computeIfAbsent(row.get(key),k->new ArrayList<>()).add(row);return out;}
  private static Map<List<Object>,Integer> multiset(List<List<Object>> rows){Map<List<Object>,Integer> out=new HashMap<>();for(var row:rows)out.merge(row,1,Integer::sum);return out;}
  private static List<String> columns(Connection c,String table)throws SQLException {List<String> out=new ArrayList<>();try(var s=c.createStatement();var r=s.executeQuery("PRAGMA table_info("+quote(table)+")")){while(r.next())out.add(r.getString("name"));}if(out.isEmpty())throw new SQLException("Missing Questie projection table "+table);return out;}
  private static List<List<Object>> rows(Connection c,String table,List<String> columns,String key,Object id)throws SQLException {
    String sql="SELECT "+columns.stream().map(QuestieRefreshWorkflow::quote).collect(java.util.stream.Collectors.joining(","))+" FROM "+quote(table)+(key==null?"":" WHERE "+quote(key)+" IS ?");
    List<List<Object>> out=new ArrayList<>();try(var p=c.prepareStatement(sql)){if(key!=null)p.setObject(1,id);try(var r=p.executeQuery()){while(r.next()){List<Object> row=new ArrayList<>();for(int i=1;i<=columns.size();i++)row.add(r.getObject(i));out.add(row);}}}return out;
  }
  private static void insert(Connection c,String table,List<String> columns,List<Object> row)throws SQLException {
    String sql="INSERT OR IGNORE INTO "+quote(table)+"("+columns.stream().map(QuestieRefreshWorkflow::quote).collect(java.util.stream.Collectors.joining(","))+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
    try(var p=c.prepareStatement(sql)){for(int i=0;i<row.size();i++)p.setObject(i+1,row.get(i));p.executeUpdate();}
  }
  private static void audit(Connection c,String snapshot,String table,String id,String field,Object old,Object next,Object current,String status)throws SQLException {
    try(var p=c.prepareStatement("INSERT OR IGNORE INTO questie_refresh_change VALUES (?,?,?,?,?,?,?,?)")){Object[] args={snapshot,table,id,field,json(old),json(next),json(current),status};for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}
  }
  private static String json(Object value) {
    if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
    if(value instanceof List<?> list)return list.stream().map(QuestieRefreshWorkflow::json).collect(java.util.stream.Collectors.joining(",","[","]"));
    String text=value.toString();StringBuilder out=new StringBuilder("\"");for(char c:text.toCharArray()){if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}return out.append('"').toString();
  }
  private static String quote(String name){return "\""+name.replace("\"","\"\"")+"\"";}
  private static Path existing(Path path)throws IOException {path=path.toAbsolutePath().normalize();rejectSymlinks(path);if(!Files.isRegularFile(path))throw new IOException("Existing candidate database required");return path;}
  private static Path lockPath(Path database)throws IOException {Path p=database.resolveSibling(database.getFileName()+".lock");if(Files.isSymbolicLink(p))throw new IOException("Symbolic database lock refused");return p;}
  private static void rejectSymlinks(Path path)throws IOException {for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symbolic source/database refused");}
  private static Connection connect(Path path)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+path);}
}
