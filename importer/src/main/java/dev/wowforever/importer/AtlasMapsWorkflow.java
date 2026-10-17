package dev.wowforever.importer;
import dev.wowforever.importer.archive.SourceArchive;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
/** Reviewed seven-floor Atlas overlay; modifies only a caller-owned candidate database. */
public final class AtlasMapsWorkflow {
 private AtlasMapsWorkflow() {}
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IllegalArgumentException("Usage: AtlasMapsWorkflow CANDIDATE_DATABASE PREPARED_DIRECTORY");
  if(!Files.isRegularFile(Path.of(args[0])))throw new IllegalArgumentException("Candidate database must already exist");
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+Path.of(args[0]).toAbsolutePath())) {
   c.setAutoCommit(false);try {System.out.println(apply(c,Path.of(args[1])));c.commit();}catch(Exception e){c.rollback();throw e;}
  }
 }
 /** Replays entirely from verified archived bytes; no network or addon directory is needed. */
 public static String replay(Connection c,String snapshot)throws Exception {
  Path temp=Files.createTempDirectory("wowforever-atlas-replay-");
  try {
   SourceArchive.extract(c,snapshot,temp);
   String replayed=apply(c,temp);
   if(!replayed.equals(snapshot))throw new IllegalArgumentException("Atlas replay snapshot identity mismatch");
   return replayed;
  } finally {
   try(var paths=Files.walk(temp)) {for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
  }
 }
 /** The caller owns transactions, candidate publication and post-import validation. */
 public static String apply(Connection c,Path prepared)throws Exception {
  Properties pins=new Properties();try(var in=AtlasMapsWorkflow.class.getResourceAsStream("/atlasmaps-pins.properties")){pins.load(in);}
  Map<String,byte[]> files=new TreeMap<>();
  for(String name:pins.stringPropertyNames()) {
   byte[] bytes=Files.readAllBytes(prepared.resolve(name));String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
   if(!hash.equals(pins.getProperty(name)))throw new IllegalArgumentException("Atlas asset hash mismatch: "+name);files.put(name,bytes);
  }
  ArchiveWorkflow.initialize(c);
  var artifacts=new ArrayList<SourceArchive.Artifact>();
  for(var entry:files.entrySet()) {
   String url=entry.getKey().equals("core.zip")||entry.getKey().equals("GPL-2.0-Atlas.txt") ? "https://github.com/arithmandar/Atlas/tree/7787b5b2adfedf3e3e3c62838b81c0db6bd5d7ec" : "https://github.com/arithmandar/Atlas-Kalimdor-and-Eastern-Kingdoms/tree/19c74eae268360cabdab7269b161b4f08b7f7426";
   artifacts.add(new SourceArchive.Artifact(entry.getKey(),url,entry.getValue()));
  }
  String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("atlas-floor-maps","19c74eae268360cabdab7269b161b4f08b7f7426","wow_classic_beta",null,"enUS"),artifacts);
  try(var s=c.createStatement()) {
   s.execute("CREATE TABLE IF NOT EXISTS instance_map(instance_id INTEGER NOT NULL REFERENCES instance(id),floor TEXT NOT NULL,label TEXT NOT NULL,jpeg BLOB NOT NULL,forever INTEGER NOT NULL,ordinal INTEGER NOT NULL,snapshot_id TEXT NOT NULL REFERENCES source_archive_snapshot(id),PRIMARY KEY(instance_id,floor))");
   s.execute("CREATE TABLE IF NOT EXISTS instance_map_legend(instance_id INTEGER NOT NULL,floor TEXT NOT NULL,ordinal INTEGER NOT NULL,number TEXT NOT NULL,name TEXT NOT NULL,npc_id INTEGER REFERENCES npc(id),source_line INTEGER NOT NULL,PRIMARY KEY(instance_id,floor,ordinal),FOREIGN KEY(instance_id,floor) REFERENCES instance_map(instance_id,floor))");
   s.executeUpdate("DELETE FROM instance_map_legend");s.executeUpdate("DELETE FROM instance_map");
  }
  Map<String,Long> ids=new HashMap<>(); int ordinal=0;
  for(String line:new String(files.get("maps.tsv"),StandardCharsets.UTF_8).split("\\R")) {
   String[] row=line.split("\\t"); long map=Long.parseLong(row[0]);Long id=null;
   String sql=map==36?"SELECT id FROM instance WHERE key='TheDeadmines'":"SELECT id FROM instance WHERE client_map_id=?";
   try(var p=c.prepareStatement(sql)) {if(map!=36)p.setLong(1,map);try(var r=p.executeQuery()){if(r.next()){id=r.getLong(1);if(r.next())throw new SQLException("Ambiguous Atlas instance "+map);}}}
   if(id==null)throw new SQLException("Missing Atlas instance "+map);ids.put(row[1],id);
   try(var p=c.prepareStatement("INSERT INTO instance_map VALUES(?,?,?,?,?,?,?)")){p.setLong(1,id);p.setString(2,row[1]);p.setString(3,row[2]);p.setBytes(4,files.get(row[1]+".jpg"));p.setInt(5,Integer.parseInt(row[3]));p.setInt(6,ordinal++);p.setString(7,snapshot);p.executeUpdate();}
  }
  ordinal=0;
  for(String line:new String(files.get("legend.tsv"),StandardCharsets.UTF_8).split("\\R")) {
   String[] row=line.split("\\t",-1);Long npc=null;
   // Exact unique names only; never use Atlas's reported IDs as canonical identities.
   try(var p=c.prepareStatement("SELECT id FROM npc WHERE name=?")){p.setString(1,row[2]);try(var r=p.executeQuery()){if(r.next()){long candidate=r.getLong(1);if(!r.next())npc=candidate;}}}
   try(var p=c.prepareStatement("INSERT INTO instance_map_legend VALUES(?,?,?,?,?,?,?)")){p.setLong(1,ids.get(row[0]));p.setString(2,row[0]);p.setInt(3,ordinal++);p.setString(4,row[1]);p.setString(5,row[2]);p.setObject(6,npc);p.setInt(7,Integer.parseInt(row[3]));p.executeUpdate();}
  }
  AdditiveProjection.record(c,Map.of("atlas-floor-maps",snapshot),"atlas-floor-maps","atlas_maps","atlas-maps-v1","Seven genuine Atlas maps; independent Atlas legends; original archives and GPL retained");
  return snapshot;
 }
}
