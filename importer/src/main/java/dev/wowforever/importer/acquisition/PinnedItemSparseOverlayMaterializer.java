package dev.wowforever.importer.acquisition;

import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.setup.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.time.Instant;import java.util.*;import java.util.concurrent.CancellationException;import java.util.function.*;

/** A conservative, content-pinned projection; never activates a library or grants source-plan permission. */
public final class PinnedItemSparseOverlayMaterializer {
 public static final String POLICY="forever-70450-raidbots-f6fae4-sparse-v1";
 public static final String SNAPSHOT_SHA="f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b";
 public static final String BASE_MANIFEST_SHA="cfb3e7b90156dd5b226e0da2329721484747302abbc2f310079350d7d29f884f";
 public interface Input {Set<String> paths()throws IOException;byte[] read(String relative,long maximumBytes)throws IOException;}
 public record Validation(String overlaySha256,Set<Long> excludedIds,Set<Long> overlayIds,int rows){public Validation{excludedIds=Set.copyOf(excludedIds);overlayIds=Set.copyOf(overlayIds);}}
 public record Result(Path directory,Validation validation){public String overlaySha256(){return validation.overlaySha256();}public Set<Long> excludedIds(){return validation.excludedIds();}public Set<Long> overlayIds(){return validation.overlayIds();}public int rows(){return validation.rows();}}
 public record Progress(String stage,int completed,int total) {}
 record Projection(SortedMap<Long,Map<String,Object>> rows,SortedSet<Long> excluded) {}
 public Result materialize(RaidbotsHotfixMaterializer.Permission permission,SetupPlan.JobContext job,
   WagoTableMaterializer.Result base,WagoTableMaterializer.Manifest manifest,RaidbotsHotfixMaterializer.Result raw,
   byte[] definition,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
  consent(permission);check(cancelled);var storage=job.storage();storage.verifyJob(job.paths());
  validateManifest(manifest);verifyBase(base.directory(),manifest,cancelled,false);
  var pin=pin();var ledger=new ItemSparseHotfixDecoder().decode(raw,pin,definition,cancelled);
  var projection=resolve(ledger,baseSparse(base.directory()),cancelled);
  Path root=checked(storage,job.paths().cache().resolve("pinned-hotfix-client"));Files.createDirectories(root);
  Path stage=checked(storage,root.resolve("pending-"+UUID.randomUUID()));Files.createDirectory(stage);boolean complete=false;
  try {
   Path csv=checked(storage,stage.resolve("csv")),evidence=checked(storage,stage.resolve("evidence"));Files.createDirectory(csv);Files.createDirectory(evidence);
   int copied=0;for(var table:manifest.tableSha256().entrySet()){
    progress.accept(new Progress("copying native tables",copied++,manifest.tableSha256().size()));
    copyVerified(base.directory().resolve("csv/"+table.getKey()+".csv"),checked(storage,csv.resolve(table.getKey()+".csv")),table.getValue(),cancelled);
   }
   copyVerified(base.directory().resolve("build.lock"),checked(storage,evidence.resolve("base-build.lock")),hash(base.directory().resolve("build.lock"),cancelled),cancelled);
   copyVerified(base.directory().resolve("acquisition.properties"),checked(storage,evidence.resolve("wago-acquisition.properties")),hash(base.directory().resolve("acquisition.properties"),cancelled),cancelled);
   copyVerified(raw.snapshot(),checked(storage,evidence.resolve("DBCache.bin")),SNAPSHOT_SHA,cancelled);
   copyVerified(raw.evidence(),checked(storage,evidence.resolve("raidbots-acquisition.properties")),hash(raw.evidence(),cancelled),cancelled);
   if(!PinnedItemSparseDefinition.SHA256.equals(PinnedItemSparseDefinition.hash(definition)))throw new IOException("Definition changed");
   Files.write(checked(storage,evidence.resolve("ItemSparse.dbd")),definition,StandardOpenOption.CREATE_NEW);
   Path overlay=checked(storage,csv.resolve("ItemSparse.hotfix.csv"));writeCsv(overlay,ledger.columns(),projection.rows(),cancelled);String overlaySha=hash(overlay,cancelled);
   Map<String,Object> tableInfo=Map.of("rows",projection.rows().size(),"valid",projection.rows().size(),"invalidated",projection.excluded().size(),"undone",0,"removed",0,"sha256",overlaySha);
   Map<String,Object> info=new TreeMap<>();info.put("url",RaidbotsHotfixMaterializer.SOURCE.toString());info.put("sha256",SNAPSHOT_SHA);info.put("header_build",70450);info.put("build",PinnedItemSparseDefinition.BUILD);info.put("fetched_at",Instant.now().toString());info.put("tables",Map.of("ItemSparse",tableInfo));info.put("skipped",List.of());info.put("policy",POLICY);
   writeJson(checked(storage,stage.resolve("hotfix.json")),info);
   Map<String,Object> lock=readObject(base.directory().resolve("build.lock"));Map<String,String> hashes=new TreeMap<>(manifest.tableSha256());hashes.put("ItemSparse.hotfix",overlaySha);lock.put("tables",hashes);lock.put("hotfix",Map.of("sha256",SNAPSHOT_SHA,"header_build",70450));writeJson(checked(storage,stage.resolve("build.lock")),lock);
   writeJson(checked(storage,evidence.resolve("event-receipt.json")),receipt(ledger,projection));
   Properties policy=new Properties();policy.setProperty("policy",POLICY);policy.setProperty("snapshotSha256",SNAPSHOT_SHA);policy.setProperty("definitionSha256",PinnedItemSparseDefinition.SHA256);policy.setProperty("baseManifestSha256",BASE_MANIFEST_SHA);policy.setProperty("opaqueRegion","70");policy.setProperty("excludedIds",join(projection.excluded()));policy.setProperty("overlaySha256",overlaySha);
   try(var output=Files.newOutputStream(checked(storage,stage.resolve("overlay-policy.properties")),StandardOpenOption.CREATE_NEW)){policy.store(output,"Conservative exact-pin native detail projection; not CASC proof");}
   progress.accept(new Progress("validating complete input",1,1));Result verified=validate(stage,cancelled);check(cancelled);storage.verifyJob(job.paths());
   Path published=checked(storage,root.resolve("input-"+UUID.randomUUID()));Files.move(checked(storage,stage),published,StandardCopyOption.ATOMIC_MOVE);complete=true;
   return new Result(published,verified.validation());
  }finally{if(!complete)removeOwnedStage(storage,stage);}
 }
 /** Recompute the exact policy from preserved raw evidence and definition, not from a mutable receipt. */
 public Result validate(Path directory,BooleanSupplier cancelled)throws IOException {
  return new Result(directory,validate(new Input(){
   public Set<String> paths()throws IOException{Set<String> result=new HashSet<>();try(var files=Files.walk(directory)){for(Path path:files.toList()){if(Files.isSymbolicLink(path))throw new IOException("Input source alias refused");if(Files.isRegularFile(path))result.add(directory.relativize(path).toString().replace('\\','/'));}}return Set.copyOf(result);}
   public byte[] read(String relative,long maximum)throws IOException{return readLimited(directory.resolve(relative),maximum);}
  },cancelled));
 }
 /** Read-only archived file view; adapters must bound SQL BLOB streams before allocating arrays. */
 public Validation validate(Input input,BooleanSupplier cancelled)throws IOException {
  check(cancelled);Map<String,Object> lock=object(read(input,"build.lock",1_048_576));Map<String,String> hashes=tableHashes(lock);String overlaySha=hashes.remove("ItemSparse.hotfix");
  if(overlaySha==null)throw new IOException("Missing selected ItemSparse overlay hash");
  var manifest=new WagoTableMaterializer.Manifest(new SetupPlan.NativeIdentity("wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS"),hashes);validateManifest(manifest);
  if(!"wow_classic_beta".equals(lock.get("product"))||!PinnedItemSparseDefinition.BUILD.equals(lock.get("build"))||!"enUS".equals(lock.get("locale"))||lock.containsKey("fallback_product")||lock.containsKey("fallback_build"))throw new IOException("Output native identity/fallback mismatch");
  Set<String> expectedCsv=new HashSet<>();for(var entry:hashes.entrySet()){check(cancelled);String file="csv/"+entry.getKey()+".csv";expectedCsv.add(file);if(!entry.getValue().equals(PinnedItemSparseDefinition.hash(read(input,file,128L<<20))))throw new IOException("Output base table hash mismatch: "+entry.getKey());}
  expectedCsv.add("csv/ItemSparse.hotfix.csv");Set<String> actual=new HashSet<>();for(String path:input.paths())if(path.startsWith("csv/"))actual.add(path);if(!actual.equals(expectedCsv))throw new IOException("Unselected or missing native CSV files");
  byte[] definition=read(input,"evidence/ItemSparse.dbd",1_048_576);var ledger=new ItemSparseHotfixDecoder().decodeBytes(read(input,"evidence/DBCache.bin",64L<<20),properties(read(input,"evidence/raidbots-acquisition.properties",16384)),pin(),definition,cancelled);
  Set<Long> baseIds;try{baseIds=CsvTable.read("archived ItemSparse",new StringReader(new String(read(input,"csv/ItemSparse.csv",128L<<20),StandardCharsets.UTF_8))).indexByLong("ID").keySet();}catch(RuntimeException invalid){throw new IOException("Unreadable base ItemSparse IDs",invalid);}
  var projection=resolve(ledger,baseIds,cancelled);byte[] expected=csvBytes(ledger.columns(),projection.rows(),cancelled);
  if(!overlaySha.equals(PinnedItemSparseDefinition.hash(expected))||!Arrays.equals(expected,read(input,"csv/ItemSparse.hotfix.csv",32L<<20)))throw new IOException("Output overlay content does not match exact snapshot policy");
  if(!Arrays.equals(json(receipt(ledger,projection)).getBytes(StandardCharsets.UTF_8),read(input,"evidence/event-receipt.json",8L<<20)))throw new IOException("Overlay event/exclusion receipt mismatch");
  Properties policy=properties(read(input,"overlay-policy.properties",65536));Map<String,String> required=Map.of("policy",POLICY,"snapshotSha256",SNAPSHOT_SHA,"definitionSha256",PinnedItemSparseDefinition.SHA256,"baseManifestSha256",BASE_MANIFEST_SHA,"opaqueRegion","70","excludedIds",join(projection.excluded()),"overlaySha256",overlaySha);
  for(var e:required.entrySet())if(!e.getValue().equals(policy.getProperty(e.getKey())))throw new IOException("Overlay policy receipt mismatch");
  Map<String,Object> hotfix=object(read(input,"hotfix.json",1_048_576));
  if(!POLICY.equals(hotfix.get("policy"))||!SNAPSHOT_SHA.equals(hotfix.get("sha256"))||!PinnedItemSparseDefinition.BUILD.equals(hotfix.get("build"))||!Long.valueOf(70450).equals(hotfix.get("header_build"))||!RaidbotsHotfixMaterializer.SOURCE.toString().equals(hotfix.get("url")))throw new IOException("Hotfix metadata identity mismatch");
  if(!(hotfix.get("tables") instanceof Map<?,?> tables)||tables.size()!=1||!(tables.get("ItemSparse") instanceof Map<?,?> table)||!overlaySha.equals(table.get("sha256"))||!Long.valueOf(projection.rows().size()).equals(table.get("rows"))||!Long.valueOf(projection.excluded().size()).equals(table.get("invalidated"))||!Long.valueOf(0).equals(table.get("removed"))||!Long.valueOf(0).equals(table.get("undone"))||!Long.valueOf(projection.rows().size()).equals(table.get("valid"))||!(hotfix.get("skipped") instanceof List<?> skipped)||!skipped.isEmpty())throw new IOException("Hotfix metadata policy/count mismatch");
  if(!(lock.get("hotfix") instanceof Map<?,?> hotfixPin)||!SNAPSHOT_SHA.equals(hotfixPin.get("sha256"))||!Long.valueOf(70450).equals(hotfixPin.get("header_build")))throw new IOException("Hotfix build lock mismatch");
  validateWagoProof(properties(read(input,"evidence/wago-acquisition.properties",65536)),manifest);Map<String,Object> original=object(read(input,"evidence/base-build.lock",1_048_576));if(!tableHashes(original).equals(hashes)||!"wow_classic_beta".equals(original.get("product"))||!PinnedItemSparseDefinition.BUILD.equals(original.get("build"))||!"enUS".equals(original.get("locale"))||original.containsKey("hotfix")||original.containsKey("fallback_product")||original.containsKey("fallback_build"))throw new IOException("Preserved base lock mismatch");
  return new Validation(overlaySha,projection.excluded(),projection.rows().keySet(),projection.rows().size());
 }
 private static byte[] read(Input input,String relative,long maximum)throws IOException{byte[] bytes=input.read(relative,maximum);if(bytes.length>maximum)throw new IOException("Archive adapter returned oversized content");return bytes;}
 private static Map<String,Object> object(byte[] bytes)throws IOException{try{Object value=MiniJson.parse(PinnedItemSparseDefinition.utf8(bytes));if(!(value instanceof Map<?,?> map))throw new IOException("Expected JSON object");Map<String,Object> result=new TreeMap<>();map.forEach((k,v)->result.put((String)k,v));return result;}catch(RuntimeException invalid){throw new IOException("Invalid JSON metadata",invalid);}}
 private static Properties properties(byte[] bytes)throws IOException{Properties result=new Properties();try(var input=new ByteArrayInputStream(bytes)){result.load(input);}catch(IllegalArgumentException invalid){throw new IOException("Invalid properties metadata",invalid);}return result;}
 static Projection resolve(DecodedItemSparseSnapshot ledger,Set<Long> baseIds,BooleanSupplier cancelled)throws IOException {
  SortedMap<Long,Map<String,Object>> rows=new TreeMap<>();SortedSet<Long> invalid=new TreeSet<>();
  for(var event:ledger.events()) {check(cancelled);var source=event.source();if(source.region()!=70)throw new IOException("Unsupported opaque hotfix region");
   switch(source.status()){
    case REMOVED,NOT_PUBLIC->throw new IOException("Removed/non-public ItemSparse events are unsupported by this policy");
    case INVALIDATED->invalid.add(source.recordId());
    case VALID->{var previous=rows.putIfAbsent(source.recordId(),event.values());if(previous!=null&&!previous.equals(event.values()))throw new IOException("Ambiguous differing duplicate valid ItemSparse rows");}
   }
  }
  if(invalid.stream().anyMatch(id->baseIds.contains(id)||rows.containsKey(id)))throw new IOException("Invalidated IDs overlap native facts; retraction semantics are unsupported");
  return new Projection(Collections.unmodifiableSortedMap(rows),Collections.unmodifiableSortedSet(invalid));
 }
 private static void validateManifest(WagoTableMaterializer.Manifest manifest)throws IOException {
  if(!manifest.identity().equals(new SetupPlan.NativeIdentity("wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS"))||manifest.tableSha256().size()!=51||!BASE_MANIFEST_SHA.equals(manifestHash(manifest.tableSha256())))throw new IOException("Native base CSV manifest does not match reviewed exact pin");
 }
 private static String manifestHash(Map<String,String> tables){StringBuilder value=new StringBuilder();new TreeMap<>(tables).forEach((k,v)->value.append(k).append('=').append(v).append('\n'));return PinnedItemSparseDefinition.hash(value.toString().getBytes(StandardCharsets.UTF_8));}
 private static void verifyBase(Path directory,WagoTableMaterializer.Manifest manifest,BooleanSupplier cancelled,boolean output)throws IOException {
  Map<String,Object> lock=readObject(directory.resolve("build.lock"));if(!manifest.identity().product().equals(lock.get("product"))||!manifest.identity().build().equals(lock.get("build"))||!manifest.identity().locale().equals(lock.get("locale"))||lock.containsKey("fallback_product")||lock.containsKey("fallback_build")||!output&&lock.containsKey("hotfix"))throw new IOException("Unexpected base identity/fallback/hotfix metadata");
  Map<String,String> named=tableHashes(lock);if(output)named.remove("ItemSparse.hotfix");if(!named.equals(manifest.tableSha256()))throw new IOException("Native base lock differs from selected CSV manifest");
  Set<String> expected=new HashSet<>();for(var entry:manifest.tableSha256().entrySet()){check(cancelled);expected.add(entry.getKey()+".csv");Path path=directory.resolve("csv/"+entry.getKey()+".csv");if(!entry.getValue().equals(hash(path,cancelled)))throw new IOException("Native base CSV SHA mismatch: "+entry.getKey());}
  if(output)expected.add("ItemSparse.hotfix.csv");try(var files=Files.list(directory.resolve("csv"))){for(Path file:files.toList())if(!expected.remove(file.getFileName().toString()))throw new IOException("Unselected native CSV or file: "+file.getFileName());}if(!expected.isEmpty())throw new IOException("Native CSV missing");
  if(!output){if(Files.exists(directory.resolve("hotfix.json")))throw new IOException("Unselected prior hotfix metadata");validateWagoProof(properties(directory.resolve("acquisition.properties")),manifest);}
 }
 private static void validateWagoProof(Properties proof,WagoTableMaterializer.Manifest manifest)throws IOException {
  Map<String,String> expected=Map.of("provider","WAGO_EXPORT","source","https://wago.tools","product",manifest.identity().product(),"build",manifest.identity().build(),"requestedLocale",manifest.identity().locale(),"cascRootVerified","false");for(var entry:expected.entrySet())if(!entry.getValue().equals(proof.getProperty(entry.getKey())))throw new IOException("Base Wago source proof mismatch");for(String table:manifest.tableSha256().keySet())if(!("https://wago.tools/db2/"+table+"/csv?build="+manifest.identity().build()).equals(proof.getProperty("url."+table)))throw new IOException("Base table source URL mismatch");
 }
 private static Set<Long> baseSparse(Path directory)throws IOException{try{return CsvTable.read(directory.resolve("csv/ItemSparse.csv")).indexByLong("ID").keySet();}catch(RuntimeException invalid){throw new IOException("Unreadable native ItemSparse IDs",invalid);}}
 private static RaidbotsHotfixMaterializer.Pin pin(){return new RaidbotsHotfixMaterializer.Pin("wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS",SNAPSHOT_SHA);}
 private static void consent(RaidbotsHotfixMaterializer.Permission permission)throws IOException{if(permission!=RaidbotsHotfixMaterializer.Permission.ALLOW_RAIDBOTS_HOTFIX)throw new IOException("Independent Raidbots hotfix permission required");}
 private static Object receipt(DecodedItemSparseSnapshot ledger,Projection projection){List<Object> events=new ArrayList<>();for(var event:ledger.events()){var e=event.source();events.add(Map.of("sequence",event.snapshotSequence(),"offset",e.offset(),"payloadOffset",e.payloadOffset(),"payloadBytes",e.payloadBytes(),"region",e.region(),"push",e.push(),"unique",e.unique(),"recordId",e.recordId(),"status",e.status().toString()));}return Map.of("policy",POLICY,"snapshotSha256",SNAPSHOT_SHA,"definitionSha256",PinnedItemSparseDefinition.SHA256,"excludedIds",projection.excluded(),"events",events,"regionMeaning","opaque provider value; no geography/locale assertion");}
 private static void writeCsv(Path path,List<String> columns,SortedMap<Long,Map<String,Object>> rows,BooleanSupplier cancelled)throws IOException{Files.write(path,csvBytes(columns,rows,cancelled),StandardOpenOption.CREATE_NEW);}
 private static byte[] csvBytes(List<String> columns,SortedMap<Long,Map<String,Object>> rows,BooleanSupplier cancelled)throws IOException{var text=new StringBuilder(String.join(",",columns)).append('\n');for(var row:rows.values()){check(cancelled);for(int i=0;i<columns.size();i++){if(i>0)text.append(',');Object value=row.get(columns.get(i));if(value==null)throw new IOException("Decoded overlay field missing");if(value instanceof String string){if(!string.isEmpty())text.append('"').append(string.replace("\"","\"\"")).append('"');}else if(value instanceof Float number)text.append(Double.toString(number.doubleValue()));else text.append(value); }text.append('\n');if(text.length()>(32<<20))throw new IOException("Overlay CSV exceeds byte limit");}return text.toString().getBytes(StandardCharsets.UTF_8);}
 private static Map<String,Object> readObject(Path path)throws IOException{try{Object parsed=MiniJson.parse(new String(readLimited(path,1_048_576),StandardCharsets.UTF_8));if(!(parsed instanceof Map<?,?> map))throw new IOException("Metadata must be JSON object");Map<String,Object> result=new TreeMap<>();map.forEach((k,v)->result.put((String)k,v));return result;}catch(RuntimeException invalid){throw new IOException("Invalid JSON metadata: "+path.getFileName(),invalid);}}
 private static Map<String,String> tableHashes(Map<String,Object> lock)throws IOException{if(!(lock.get("tables") instanceof Map<?,?> tables))throw new IOException("Missing native table hashes");Map<String,String> result=new TreeMap<>();for(var e:tables.entrySet()){if(!(e.getKey() instanceof String key)||!(e.getValue() instanceof String value))throw new IOException("Invalid table hash");result.put(key,value);}return result;}
 private static Properties properties(Path path)throws IOException{Properties values=new Properties();try(var input=new ByteArrayInputStream(readLimited(path,65536))){values.load(input);}catch(IllegalArgumentException bad){throw new IOException("Invalid proof metadata",bad);}return values;}
 private static byte[] readLimited(Path path,long maximum)throws IOException{if(Files.isSymbolicLink(path))throw new IOException("Source file alias refused");try(var input=Files.newInputStream(path);var out=new ByteArrayOutputStream()){byte[] b=new byte[65536];int n;long count=0;while((n=input.read(b))!=-1){count+=n;if(count>maximum)throw new IOException("Source exceeds byte limit");out.write(b,0,n);}return out.toByteArray();}}
 private static void copyVerified(Path source,Path target,String expected,BooleanSupplier cancelled)throws IOException{var digest=digest();try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] bytes=new byte[65536];int n;long total=0;while((n=input.read(bytes))!=-1){check(cancelled);if((total+=n)>(128L<<20))throw new IOException("Source copy exceeds byte limit");digest.update(bytes,0,n);output.write(bytes,0,n);}}if(!expected.equals(HexFormat.of().formatHex(digest.digest())))throw new IOException("Source changed while copying");}
 private static String hash(Path path,BooleanSupplier cancelled)throws IOException{if(Files.isSymbolicLink(path))throw new IOException("Source file alias refused");var digest=digest();try(var input=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;long size=0;while((n=input.read(bytes))!=-1){check(cancelled);if((size+=n)>(128L<<20))throw new IOException("Source hash exceeds byte limit");digest.update(bytes,0,n);}}return HexFormat.of().formatHex(digest.digest());}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static void writeJson(Path path,Object value)throws IOException{Files.writeString(path,json(value),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
 private static String json(Object value){if(value instanceof String text){var out=new StringBuilder("\"");for(char c:text.toCharArray())switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.append('"').toString();}if(value instanceof Map<?,?> map){var entries=new TreeMap<String,Object>();map.forEach((k,v)->entries.put(k.toString(),v));var out=new StringJoiner(",","{","}");entries.forEach((k,v)->out.add(json(k)+":"+json(v)));return out.toString();}if(value instanceof Iterable<?> list){var out=new StringJoiner(",","[","]");list.forEach(v->out.add(json(v)));return out.toString();}return String.valueOf(value);}
 private static String join(Set<Long> ids){return new TreeSet<>(ids).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));}
 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Pinned overlay cancelled");}
 private static Path checked(SetupStorage storage,Path path)throws IOException{Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Overlay storage alias changed");return actual;}
 private static void removeOwnedStage(SetupStorage storage,Path stage)throws IOException{if(!Files.exists(stage,LinkOption.NOFOLLOW_LINKS))return;try(var paths=Files.walk(checked(storage,stage))){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(checked(storage,path));}}
}
