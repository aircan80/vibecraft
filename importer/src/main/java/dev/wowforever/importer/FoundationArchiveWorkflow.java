package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.archive.records.SourceRecords;
import dev.wowforever.importer.clientdata.MiniJson;
import dev.wowforever.importer.foundation.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate native client acquisition and additive offline v0.4 foundation projection. */
public final class FoundationArchiveWorkflow {
  public static final String SOURCE="client-foundation",TRANSFORM="client-foundation-v2",MANIFEST="foundation/manifest.json";
  private FoundationArchiveWorkflow() {}

  /** Reads exact native exports listed by a separately recorded acquisition manifest. */
  public static String ingest(Path database,Path directory)throws Exception {
    existing(database);
    Path acquisition=directory.resolve("acquisition-manifest.json");
    byte[] acquisitionBytes=Files.readAllBytes(acquisition);
    var input=object(MiniJson.parse(new String(acquisitionBytes,StandardCharsets.UTF_8)));
    String product=string(input,"product"),build=string(input,"build"),locale=string(input,"locale");
    if(!build.matches("[0-9]+(?:\\.[0-9]+){3}"))throw new IllegalArgumentException("Explicit four-part build required");
    var artifacts=new ArrayList<SourceArchive.Artifact>();
    artifacts.add(new SourceArchive.Artifact("acquisition-manifest.json","",acquisitionBytes));
    var tables=new ArrayList<Map<String,Object>>();Set<String> names=new HashSet<>();
    for(var value:list(input,"files")) {
      var file=object(value);String name=string(file,"table"),hash=string(file,"sha256"),url=string(file,"url");
      if(!name.matches("[A-Za-z][A-Za-z0-9_]*") || !names.add(name))throw new IllegalArgumentException("Invalid or duplicate native table "+name);
      String path="tables/"+name+"."+build+".csv";
      Path csv=directory.resolve(name+"."+build+".csv");
      if(!Files.isRegularFile(csv))csv=directory.resolve(name+".csv");
      byte[] bytes=Files.readAllBytes(csv);
      if(!ClientDataset.sha256(bytes).equals(hash))throw new IllegalArgumentException("Acquired CSV SHA-256 mismatch: "+name);
      artifacts.add(new SourceArchive.Artifact(path,url,bytes));
      Object keys=file.getOrDefault("key_columns",List.of("ID"));
      tables.add(Map.of("name",name,"path",path,"sha256",hash,"keys",keys));
    }
    if(tables.isEmpty())throw new IllegalArgumentException("Acquisition manifest has no genuine CSV tables");
    tables.sort(Comparator.comparing(m->(String)m.get("name")));
    var manifest=Map.of("product",product,"build",build,"locale",locale,"tables",tables);
    artifacts.add(new SourceArchive.Artifact(MANIFEST,"",ClientDataset.json(manifest).getBytes(StandardCharsets.UTF_8)));
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      try(var c=connect(database)) {
        c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot(SOURCE,build+" native CSV exports",product,build,locale),artifacts);
        c.commit(); // original bytes remain durable even if structured indexing cannot represent a table
        SourceRecords.capture(c,snapshot,artifacts.stream().map(a->new SourceRecords.SourceInput(a.path(),a.bytes())).toList());
        c.commit();return snapshot;
      }
    }
  }

  public static void project(Path database,String snapshot)throws Exception {
    candidate(database,c->{
      ArchiveWorkflow.initialize(c);
      var metadata=SourceArchive.snapshot(c,snapshot).metadata();
      if(!SOURCE.equals(metadata.source()))throw new IllegalArgumentException("Not a native client foundation snapshot");
      var artifacts=SourceArchive.artifacts(c,snapshot);
      var manifest=object(MiniJson.parse(new String(artifacts.stream().filter(a->MANIFEST.equals(a.path())).findFirst().orElseThrow().bytes(),StandardCharsets.UTF_8)));
      String product=string(manifest,"product"),build=string(manifest,"build"),locale=string(manifest,"locale");
      if(!product.equals(metadata.product()) || !build.equals(metadata.build()) || !locale.equals(metadata.locale()))throw new IllegalArgumentException("Dataset manifest/snapshot mismatch");
      var tables=new ArrayList<ClientDataset.Table>();
      for(var value:list(manifest,"tables")) {
        var t=object(value);var keys=list(t,"keys").stream().map(v->{if(!(v instanceof String s))throw new IllegalArgumentException("Native keys must be strings");return s;}).toList();
        tables.add(new ClientDataset.Table(string(t,"name"),string(t,"path"),string(t,"sha256"),keys));
      }
      if(artifacts.size()!=tables.size()+2)throw new IllegalArgumentException("Unmanifested native dataset artifact");
      ClientDataset.project(c,snapshot,product,build,locale,tables,artifacts);
      projectItemKnowledge(c,snapshot,product,build);
      if(product.equals("wow_classic_beta"))NativeItemMetadataProjection.project(c,snapshot);
      AdditiveProjection.record(c,Map.of("client-foundation-"+product+"-"+build,snapshot),null,"client_foundation",TRANSFORM,
          "Native client tables retained with complete exported table coverage; missing tables remain unavailable; no core facts regenerated");
      put(c,"client_foundation_snapshot_id",snapshot);
    });
  }

  public static void diff(Path database,String eraSnapshot,String foreverSnapshot)throws Exception {
    candidate(database,c->{
      ForeverDiff.project(c,eraSnapshot,foreverSnapshot);
      AdditiveProjection.record(c,Map.of("foundation-diff-era-"+eraSnapshot,eraSnapshot,"foundation-diff-forever-"+foreverSnapshot,foreverSnapshot),null,
          "forever_diff","forever-diff-v1","Computed paired native client table comparison; coverage restrictions retained");
      put(c,"forever_diff_era_snapshot_id",eraSnapshot);put(c,"forever_diff_forever_snapshot_id",foreverSnapshot);
      put(c,"forever_diff_era_build",datasetBuild(c,eraSnapshot));put(c,"forever_diff_forever_build",datasetBuild(c,foreverSnapshot));
    });
  }

  static void projectItemKnowledge(Connection c,String snapshot,String product,String build)throws Exception {
    FieldKnowledge.initialize(c);
    if(product.equals("wow_classic_era")) {
      for(var row:rows(c,snapshot,"ItemSparse"))FieldKnowledge.assertEvidence(c,new FieldKnowledge.Evidence("item",row.id,"classic_stats",FieldKnowledge.State.KNOWN,
          "blizzard-client-era",build,snapshot,row.path,row.line,row.payload));
      return;
    }
    if(!product.equals("wow_classic_beta"))return;
    if(!hasTable(c,snapshot,"Item") || !hasTable(c,snapshot,"ItemSparse"))return;
    var sparse=new HashMap<String,NativeRow>();for(var row:rows(c,snapshot,"ItemSparse"))sparse.put(row.id,row);
    for(var row:rows(c,snapshot,"Item")) {
      var visible=sparse.get(row.id);
      FieldKnowledge.assertEvidence(c,new FieldKnowledge.Evidence("item",row.id,"stats",visible==null?FieldKnowledge.State.HIDDEN_BY_BLIZZARD:FieldKnowledge.State.KNOWN,
          "blizzard-client",build,snapshot,visible==null?row.path:visible.path,visible==null?row.line:visible.line,visible==null?null:visible.payload));
    }
  }
  record NativeRow(String id,String payload,String path,int line) {}
  private static List<NativeRow> rows(Connection c,String snapshot,String table)throws SQLException {
    var rows=new ArrayList<NativeRow>();try(var p=c.prepareStatement("SELECT row_id,payload,source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name=? ORDER BY row_id")) {
      p.setString(1,snapshot);p.setString(2,table);try(var r=p.executeQuery()){while(r.next())rows.add(new NativeRow(r.getString(1),r.getString(2),r.getString(3),r.getInt(4)));}
    }return rows;
  }
  private static boolean hasTable(Connection c,String snapshot,String table)throws SQLException {
    try(var p=c.prepareStatement("SELECT 1 FROM client_table_manifest WHERE snapshot_id=? AND table_name=?")){p.setString(1,snapshot);p.setString(2,table);try(var r=p.executeQuery()){return r.next();}}
  }
  private static String datasetBuild(Connection c,String snapshot)throws SQLException {
    try(var p=c.prepareStatement("SELECT build FROM client_table_dataset WHERE snapshot_id=?")){p.setString(1,snapshot);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Missing projected dataset");return r.getString(1);}}
  }
  @FunctionalInterface private interface Mutation {void run(Connection c)throws Exception;}
  private static void candidate(Path database,Mutation mutation)throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      Path candidate=Files.createTempFile(database.getParent(),".foundation-",".sqlite");
      try {
        try(var c=connect(database);var p=c.prepareStatement("VACUUM INTO ?")){Files.delete(candidate);p.setString(1,candidate.toString());p.execute();}
        try(var c=connect(candidate)) {
          c.setAutoCommit(false);mutation.run(c);c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next() || !"ok".equals(r.getString(1)))throw new SQLException("Foundation candidate integrity failed");}
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Foundation candidate foreign keys failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {Files.deleteIfExists(candidate);}
    }
  }
  private static Path existing(Path database)throws IOException {database=database.toAbsolutePath().normalize();if(!Files.isRegularFile(database))throw new IOException("Existing candidate database required: "+database);return database;}
  private static Path lockPath(Path database){return database.toAbsolutePath().resolveSibling(database.getFileName()+".lock");}
  private static Connection connect(Path database)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+database);}
  private static void put(Connection c,String key,String value)throws SQLException {try(var p=c.prepareStatement("INSERT OR REPLACE INTO meta VALUES (?,?)")){p.setString(1,key);p.setString(2,value);p.executeUpdate();}}
  @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){if(value instanceof Map<?,?> m)return (Map<String,Object>)m;throw new IllegalArgumentException("Expected manifest object");}
  private static String string(Map<String,Object> value,String key){if(value.get(key) instanceof String s)return s;throw new IllegalArgumentException("Missing manifest string "+key);}
  @SuppressWarnings("unchecked") private static List<Object> list(Map<String,Object> value,String key){if(value.get(key) instanceof List<?> list)return (List<Object>)list;throw new IllegalArgumentException("Missing manifest list "+key);}
}
