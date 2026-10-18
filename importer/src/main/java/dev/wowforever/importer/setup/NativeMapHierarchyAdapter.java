package dev.wowforever.importer.setup;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.acquisition.*;
import dev.wowforever.importer.clientdata.CsvTable;
import dev.wowforever.importer.foundation.ClientDataset;
import dev.wowforever.lua.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.sql.*;import java.time.Instant;import java.util.*;import java.util.concurrent.CancellationException;import java.util.function.*;import org.sqlite.SQLiteConfig;

/** Adds separately proven native map app facts to a protected copy; core ingestion identity remains unchanged. */
public final class NativeMapHierarchyAdapter {
 public static final String SOURCE="native-map-hierarchy",TRANSFORM="native-map-hierarchy-v1";
 public static final String OVERRIDE="support/Forever/Zones/areaIdToUiMapId.lua";
 public static final String OVERRIDE_SHA="c6f26a18a2d0d9bdf5750039eabe111f545700a751928e8f79aedb12fbb8ff09";
 public record Progress(String stage,long bytes,long expectedBytes){}
 public record Result(Path database,String sha256,String coreBuildId,String coreIngestionId,String mapSnapshot,
   int maps,int placed,int unplaced,int linkedZones,Path receipt,LibraryCandidateValidator.Validated coreValidation){}
 record Inputs(String clientSnapshot,String questieSnapshot,SourceArchive.Artifact maps,SourceArchive.Artifact assignments,SourceArchive.Artifact overrides){}
 public Result apply(SetupPlan plan,SetupPlan.JobContext destination,LibraryBuildJob.Candidate core,
   BooleanSupplier cancelled,Consumer<Progress> progress)throws Exception {
  check(cancelled);if(!plan.nativeIdentity().equals(Optional.of(Forever70450Bootstrap.wagoManifest().identity()))||!plan.allows(SetupPlan.Acquisition.WAGO_EXPORT)||!plan.sourcePins().equals(SetupPlan.currentPins(plan.sources())))throw new IOException("Native hierarchy requires the exact selected Forever70450/Wago context and pins");
  var storage=destination.storage();storage.verifyJob(destination.paths());Path original=storage.verifyDestination(core.database().toRealPath());
  Path output=checked(storage,destination.paths().candidateDatabase()),partial=checked(storage,output.resolveSibling(output.getFileName()+".part"));
  if(original.equals(output)||Files.exists(output,LinkOption.NOFOLLOW_LINKS)||Files.exists(partial,LinkOption.NOFOLLOW_LINKS))throw new IOException("Native hierarchy needs a fresh protected candidate path");
  rejectSidecars(original);if(!core.sha256().equals(RuntimeLibraryExporter.hashFile(original,cancelled)))throw new IOException("Core candidate content identity changed");
  progress.accept(new Progress("validating original core candidate",0,-1));var validated=new LibraryCandidateValidator().validate(original,plan,core.validation().ingestionId(),cancelled);
  Inputs inputs;Set<Long> zoneIds;try(var c=readOnly(original)){inputs=inputs(c,plan,validated.ingestionId());zoneIds=zoneIds(c);if(table(c,"ui_map")||!meta(c,"world_map_snapshot_id").isEmpty())throw new IOException("Core candidate already contains a map projection; use a fresh core attempt");}
  var model=NativeMapHierarchyProjection.build(csv(inputs.maps()),csv(inputs.assignments()),overrides(inputs.overrides().bytes()),zoneIds,cancelled);
  Path lock=checked(storage,destination.paths().directory().resolve("native-map.lock"));boolean owned=false;
  try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()){
   if(held==null)throw new IOException("Another native map projection is using this job");
   if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)||Files.exists(partial,LinkOption.NOFOLLOW_LINKS))throw new IOException("Native map candidate path became occupied");
   Files.createFile(checked(storage,partial));owned=true;copy(original,partial,core.sha256(),cancelled,progress);
   progress.accept(new Progress("capturing exact native map inputs and projecting hierarchy",0,-1));String snapshot;
   try(var c=DriverManager.getConnection("jdbc:sqlite:"+partial)){
    c.setAutoCommit(false);Map<String,Object> manifest=new TreeMap<>();manifest.put("transform",TRANSFORM);manifest.put("product","wow_classic_beta");manifest.put("build",PinnedItemSparseDefinition.BUILD);manifest.put("locale","enUS");manifest.put("coreIngestion",validated.ingestionId());manifest.put("coreBuildId",validated.buildId());manifest.put("clientSnapshot",inputs.clientSnapshot());manifest.put("questieSnapshot",inputs.questieSnapshot());manifest.put("questiePin",plan.sourcePins().get(SetupPlan.Source.QUESTIE));manifest.put("providerEvidence","selected Wago pinned CSV exports; not CASC authentication");manifest.put("tables",Map.of("UiMap",sha(inputs.maps().bytes()),"UiMapAssignment",sha(inputs.assignments().bytes())));manifest.put("questieOverrideSha256",OVERRIDE_SHA);
    snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot(SOURCE,TRANSFORM,"wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS"),List.of(inputs.maps(),inputs.assignments(),inputs.overrides(),new SourceArchive.Artifact("native-map/manifest.json","",ClientDataset.json(manifest).getBytes(StandardCharsets.UTF_8))));
    project(c,model,snapshot,validated);check(cancelled);c.commit();
   }
   progress.accept(new Progress("verifying native hierarchy and unchanged core identity",0,-1));
   var after=new LibraryCandidateValidator().validate(partial,plan,validated.ingestionId(),cancelled);
   if(!after.buildId().equals(validated.buildId()))throw new IOException("Native map projection changed core build identity");
   try(var c=readOnly(partial)){verify(c,model,snapshot,validated);}
   if(!core.sha256().equals(RuntimeLibraryExporter.hashFile(original,cancelled)))throw new IOException("Original core candidate changed during map projection");
   String hash=RuntimeLibraryExporter.hashFile(partial,cancelled);check(cancelled);storage.verifyJob(destination.paths());
   if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Native map output became occupied");Files.move(checked(storage,partial),checked(storage,output),StandardCopyOption.ATOMIC_MOVE);owned=false;
   Properties receipt=new Properties();receipt.setProperty("transform",TRANSFORM);receipt.setProperty("sourceSha256",core.sha256());receipt.setProperty("candidateSha256",hash);receipt.setProperty("coreBuildId",validated.buildId());receipt.setProperty("coreIngestionId",validated.ingestionId());receipt.setProperty("mapSnapshot",snapshot);receipt.setProperty("product","wow_classic_beta");receipt.setProperty("build",PinnedItemSparseDefinition.BUILD);receipt.setProperty("locale","enUS");receipt.setProperty("maps",Integer.toString(model.nodes().size()));receipt.setProperty("placed",Integer.toString(model.bounds().size()));receipt.setProperty("unplaced",Integer.toString(model.nodes().size()-1-model.bounds().size()));receipt.setProperty("linkedZones",Integer.toString(model.zoneLinks().size()));receipt.setProperty("ambiguousBounds",Integer.toString(model.ambiguousBounds().size()));receipt.setProperty("ambiguousAreas",Integer.toString(model.ambiguousAreas().size()));receipt.setProperty("conflictingOverrides",Integer.toString(model.conflictingOverrides().size()));receipt.setProperty("artworkAcquired","false");
   Path proof=checked(storage,destination.paths().directory().resolve("native-map-receipt.properties"));try(var out=Files.newOutputStream(proof,StandardOpenOption.CREATE_NEW)){receipt.store(out,"Separate native map projection; core ingestion metadata is not replaced");}
   return new Result(output,hash,validated.buildId(),validated.ingestionId(),snapshot,model.nodes().size(),model.bounds().size(),model.nodes().size()-1-model.bounds().size(),model.zoneLinks().size(),proof,after);
  }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("Another native map projection is using this job",busy);}
  finally{if(owned)Files.deleteIfExists(checked(storage,partial));}
 }
 private static Inputs inputs(Connection c,SetupPlan plan,String ingestion)throws Exception{
  Map<String,String> roles=new HashMap<>();try(var s=c.prepareStatement("SELECT role,snapshot_id FROM source_ingestion_input WHERE ingestion_id=?")){s.setString(1,ingestion);try(var r=s.executeQuery()){while(r.next())roles.put(r.getString(1),r.getString(2));}}
  String client=roles.get("client"),questie=roles.get("questiedb");if(client==null||questie==null)throw new IOException("Selected archived native/Questie inputs are missing");
  var nativeContext=SourceArchive.snapshot(c,client).metadata();var questieContext=SourceArchive.snapshot(c,questie).metadata();verifyContext(nativeContext,questieContext,plan.sourcePins().get(SetupPlan.Source.QUESTIE));
  var maps=artifact(c,client,"csv/UiMap.csv");var assignments=artifact(c,client,"csv/UiMapAssignment.csv");var overrides=artifact(c,questie,OVERRIDE);var pins=Forever70450Bootstrap.wagoManifest().tableSha256();
  if(!pins.get("UiMap").equals(sha(maps.bytes()))||!pins.get("UiMapAssignment").equals(sha(assignments.bytes()))||!OVERRIDE_SHA.equals(sha(overrides.bytes())))throw new IOException("Archived native map/Questie override content differs from reviewed pins");
  return new Inputs(client,questie,maps,assignments,overrides);
 }
 static void verifyContext(SourceArchive.Snapshot nativeContext,SourceArchive.Snapshot questieContext,String pin)throws IOException{
  if(!"blizzard-client".equals(nativeContext.source())||!"wow_classic_beta".equals(nativeContext.product())||!PinnedItemSparseDefinition.BUILD.equals(nativeContext.build())||!"enUS".equals(nativeContext.locale()))throw new IOException("Native map input is not the selected Forever70450/enUS context");
  if(pin==null||!"questiedb".equals(questieContext.source())||!"forever".equals(questieContext.product())||questieContext.version()==null||!(questieContext.version().equals(pin)||questieContext.version().startsWith(pin+" ")))throw new IOException("Questie map override does not match the selected source pin");
 }
 private static SourceArchive.Artifact artifact(Connection c,String snapshot,String path)throws Exception{
  try(var p=c.prepareStatement("SELECT f.url,b.bytes,length(b.bytes) FROM source_archive_file f JOIN source_archive_blob b ON b.sha256=f.sha256 WHERE f.snapshot_id=? AND f.path=?")){p.setString(1,snapshot);p.setString(2,path);try(var r=p.executeQuery()){if(!r.next())throw new IOException("Raw native map input missing: "+path);long length=r.getLong(3);if(r.wasNull()||length>1_048_576)throw new IOException("Raw native map input missing or oversized: "+path);try(var in=r.getBinaryStream(2)){byte[] bytes=in.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Native map input exceeds byte limit");return new SourceArchive.Artifact(path,r.getString(1),bytes);}}}
 }
 private static Map<Long,Long> overrides(byte[] bytes)throws Exception{
  String file="archived Questie map overrides";var chunk=LuaChunk.parse(new String(bytes,StandardCharsets.UTF_8),file);var text=chunk.longStringAssignedTo("ZoneDB.private.areaIdToUiMapIdOverride").orElseThrow(()->new IOException("Missing explicit Questie map override table"));var inner=LuaChunk.parse(text,file+":table");var problems=new ProblemCollector();var value=new ChunkEvaluator(file,CallHandler.NONE,problems).run(inner.statements(),new Scope()).orElse(null);if(!(value instanceof LuaTable table)||problems.size()>0)throw new IOException("Unsupported Questie map override expression");Map<Long,Long> out=new HashMap<>();for(var entry:table.entries()){var area=LuaValues.asLong(entry.getKey());var map=LuaValues.asLong(entry.getValue());if(area.isEmpty()||map.isEmpty())throw new IOException("Noninteger Questie map override");out.put(area.getAsLong(),map.getAsLong());}return Map.copyOf(out);
 }
 static void project(Connection c,NativeMapHierarchyProjection.Model model,String snapshot,LibraryCandidateValidator.Validated core)throws Exception{
  try(var s=c.createStatement()){
   s.execute("CREATE TABLE ui_map(id INTEGER PRIMARY KEY,parent_id INTEGER NOT NULL,name TEXT NOT NULL)");s.execute("CREATE TABLE ui_map_child(parent_ui_map_id INTEGER NOT NULL,child_ui_map_id INTEGER NOT NULL,x0 REAL,y0 REAL,x1 REAL,y1 REAL,PRIMARY KEY(parent_ui_map_id,child_ui_map_id),CHECK((x0 IS NULL AND y0 IS NULL AND x1 IS NULL AND y1 IS NULL) OR (x0 IS NOT NULL AND y0 IS NOT NULL AND x1 IS NOT NULL AND y1 IS NOT NULL AND x0<x1 AND y0<y1)))");s.execute("CREATE TABLE world_map_projection(snapshot_id TEXT PRIMARY KEY,transform_version TEXT NOT NULL,projected_at TEXT NOT NULL)");s.execute("CREATE TABLE native_map_zone_link(snapshot_id TEXT NOT NULL REFERENCES source_archive_snapshot(id),area_id INTEGER NOT NULL REFERENCES zone(id),ui_map_id INTEGER NOT NULL REFERENCES ui_map(id),assignment_ids TEXT NOT NULL,PRIMARY KEY(snapshot_id,area_id))");
  }
  for(var node:model.nodes())try(var p=c.prepareStatement("INSERT INTO ui_map VALUES (?,?,?)")){p.setLong(1,node.id());p.setLong(2,node.parent());p.setString(3,node.name());p.executeUpdate();}
  for(var node:model.nodes())if(node.id()!=947)try(var p=c.prepareStatement("INSERT INTO ui_map_child VALUES (?,?,?,?,?,?)")){p.setLong(1,node.parent());p.setLong(2,node.id());var b=model.bounds().get(node.id());if(b==null)for(int i=3;i<=6;i++)p.setNull(i,Types.REAL);else{p.setDouble(3,b.x0());p.setDouble(4,b.y0());p.setDouble(5,b.x1());p.setDouble(6,b.y1());}p.executeUpdate();}
  for(var link:model.zoneLinks()){
   try(var p=c.prepareStatement("SELECT ui_map_id FROM zone WHERE id=?")){p.setLong(1,link.area());try(var r=p.executeQuery()){if(!r.next()||r.getObject(1)!=null&&r.getLong(1)!=link.map())throw new IOException("Existing zone map context disagrees with native assignment");}}
   try(var p=c.prepareStatement("UPDATE zone SET ui_map_id=? WHERE id=?")){p.setLong(1,link.map());p.setLong(2,link.area());p.executeUpdate();}try(var p=c.prepareStatement("INSERT INTO native_map_zone_link VALUES (?,?,?,?)")){p.setString(1,snapshot);p.setLong(2,link.area());p.setLong(3,link.map());p.setString(4,ClientDataset.json(link.assignments()));p.executeUpdate();}
  }
  try(var p=c.prepareStatement("INSERT INTO world_map_projection VALUES (?,?,?)")){p.setString(1,snapshot);p.setString(2,TRANSFORM);p.setString(3,Instant.now().toString());p.executeUpdate();}
  for(var e:Map.of("world_map_snapshot_id",snapshot,"world_map_source_build",PinnedItemSparseDefinition.BUILD,"world_map_source_product","wow_classic_beta","world_map_source_locale","enUS","world_map_transform_version",TRANSFORM,"world_map_core_ingestion_id",core.ingestionId(),"world_map_core_build_id",core.buildId(),"world_map_unplaced_count",Integer.toString(model.nodes().size()-1-model.bounds().size()),"world_map_coverage","native hierarchy and explicit area links; artwork not acquired").entrySet())try(var p=c.prepareStatement("INSERT INTO meta(key,value) VALUES (?,?)")){p.setString(1,e.getKey());p.setString(2,e.getValue());p.executeUpdate();}
 }
 static void verify(Connection c,NativeMapHierarchyProjection.Model model,String snapshot,LibraryCandidateValidator.Validated core)throws Exception{
  if(!meta(c,"source_ingestion_id").equals(core.ingestionId())||!meta(c,"build_id").equals(core.buildId())||!meta(c,"world_map_snapshot_id").equals(snapshot)||!meta(c,"world_map_source_build").equals(PinnedItemSparseDefinition.BUILD))throw new IOException("Native map projection identity mismatch");
  try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new IOException("Native map candidate integrity failure");}try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IOException("Native map candidate foreign-key failure");}
  for(var pair:Map.of("ui_map_child",model.nodes().size()-1,"native_map_zone_link",model.zoneLinks().size(),"world_map_projection",1).entrySet())try(var st=c.createStatement();var r=st.executeQuery("SELECT count(*) FROM "+pair.getKey())){if(!r.next()||r.getInt(1)!=pair.getValue())throw new IOException("Unexpected native map projection row count");}
  for(var node:model.nodes())try(var p=c.prepareStatement("SELECT parent_id,name FROM ui_map WHERE id=?")){p.setLong(1,node.id());try(var r=p.executeQuery()){if(!r.next()||r.getLong(1)!=node.parent()||!r.getString(2).equals(node.name()))throw new IOException("Native map row mismatch");}}
  try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM ui_map")){if(!r.next()||r.getInt(1)!=model.nodes().size())throw new IOException("Unexpected native map rows");}
  for(var node:model.nodes())if(node.id()!=947)try(var p=c.prepareStatement("SELECT parent_ui_map_id,x0,y0,x1,y1 FROM ui_map_child WHERE child_ui_map_id=?")){p.setLong(1,node.id());try(var r=p.executeQuery()){if(!r.next()||r.getLong(1)!=node.parent())throw new IOException("Native map parent link mismatch");var b=model.bounds().get(node.id());double[] expected=b==null?null:new double[]{b.x0(),b.y0(),b.x1(),b.y1()};for(int i=0;i<4;i++)if(expected==null?r.getObject(i+2)!=null:r.getObject(i+2)==null||Double.compare(r.getDouble(i+2),expected[i])!=0)throw new IOException("Native map bound mismatch");}}
  for(var link:model.zoneLinks())try(var p=c.prepareStatement("SELECT z.ui_map_id,n.ui_map_id,n.assignment_ids FROM zone z JOIN native_map_zone_link n ON n.area_id=z.id WHERE z.id=? AND n.snapshot_id=?")){p.setLong(1,link.area());p.setString(2,snapshot);try(var r=p.executeQuery()){if(!r.next()||r.getLong(1)!=link.map()||r.getLong(2)!=link.map()||!r.getString(3).equals(ClientDataset.json(link.assignments())))throw new IOException("Native zone map link mismatch");}}
 }
 private static Set<Long> zoneIds(Connection c)throws SQLException{Set<Long> ids=new HashSet<>();try(var s=c.createStatement();var r=s.executeQuery("SELECT id FROM zone")){while(r.next())ids.add(r.getLong(1));}return Set.copyOf(ids);}
 private static CsvTable csv(SourceArchive.Artifact a)throws IOException{return CsvTable.read(a.path(),new StringReader(new String(a.bytes(),StandardCharsets.UTF_8)));}
 private static Connection readOnly(Path path)throws SQLException{SQLiteConfig config=new SQLiteConfig();config.setReadOnly(true);return DriverManager.getConnection("jdbc:sqlite:"+path,config.toProperties());}
 private static String meta(Connection c,String key)throws SQLException{try(var p=c.prepareStatement("SELECT value FROM meta WHERE key=?")){p.setString(1,key);try(var r=p.executeQuery()){return r.next()?r.getString(1):"";}}}
 private static boolean table(Connection c,String name)throws SQLException{try(var p=c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")){p.setString(1,name);try(var r=p.executeQuery()){return r.next();}}}
 private static void copy(Path from,Path to,String expected,BooleanSupplier cancelled,Consumer<Progress> progress)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=Files.size(from),copied=0;try(var in=Files.newInputStream(from);var out=Files.newOutputStream(to,StandardOpenOption.TRUNCATE_EXISTING)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){check(cancelled);copied+=n;if(copied>total)throw new IOException("Core candidate grew during copy");digest.update(bytes,0,n);out.write(bytes,0,n);if(copied%(8L<<20)==0)progress.accept(new Progress("copying core candidate",copied,total));}}if(copied!=total||!expected.equals(HexFormat.of().formatHex(digest.digest())))throw new IOException("Core candidate changed during copy");}
 private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 private static void rejectSidecars(Path file)throws IOException{for(String suffix:List.of("-wal","-shm","-journal"))if(Files.exists(file.resolveSibling(file.getFileName()+suffix)))throw new IOException("Core candidate must be closed before native map projection");}
 private static Path checked(SetupStorage storage,Path path)throws IOException{Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Native map destination alias changed");return actual;}
 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Native map adapter cancelled");}
}
