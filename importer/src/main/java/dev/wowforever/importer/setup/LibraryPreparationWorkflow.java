package dev.wowforever.importer.setup;

import dev.wowforever.importer.acquisition.*;
import dev.wowforever.importer.source.SourceSpec;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.CancellationException;import java.util.concurrent.atomic.AtomicBoolean;import java.util.function.*;

/** Java-only prepare/export workflow. Returns verified staging artifacts; never activates or deletes a library. */
public final class LibraryPreparationWorkflow {
 public enum Phase { PREFLIGHT,COMMUNITY,NATIVE_TABLES,HOTFIX,SCHEMA,OVERLAY,CATALOGUE,RUNTIME,VERIFY,READY }
 public record Progress(Phase phase,String detail,long bytes,long expectedBytes,int completed,int total) {}
 public record Review(SetupPlan.NativeIdentity identity,List<String> warnings){public Review{warnings=List.copyOf(warnings);}}
 public record AcquisitionReport(CommunitySourceMaterializer.Result community,WagoTableMaterializer.Result nativeTables,
   Optional<RaidbotsHotfixMaterializer.Result> hotfix,Optional<PinnedItemSparseDefinitionMaterializer.Result> schema,
   Optional<PinnedItemSparseOverlayMaterializer.Result> overlay) {}
 public record Prepared(SetupPlan plan,SetupPlan.JobContext acquisitionJob,SetupPlan.JobContext catalogueJob,
   LibraryBuildJob.Candidate fullCandidate,RuntimeLibraryExporter.VerifiedExport runtime,AcquisitionReport acquisition,Path preparationReceipt,List<String> warnings) {public Prepared{warnings=List.copyOf(warnings);}}
 public static final class Session {
  private final SetupPlan plan;private final SetupPlan.JobContext job;private final AtomicBoolean running=new AtomicBoolean();private volatile SetupPlan.JobContext lastCatalogue;
  private Session(SetupPlan plan,SetupPlan.JobContext job){this.plan=plan;this.job=job;}
  public SetupPlan plan(){return plan;}public SetupPlan.JobContext acquisitionJob(){return job;}public Optional<SetupPlan.JobContext> lastCatalogueJob(){return Optional.ofNullable(lastCatalogue);}
 }
 interface Operations {
  CommunitySourceMaterializer.Result community(SetupPlan p,SetupPlan.JobContext j,BooleanSupplier c,Consumer<CommunitySourceMaterializer.Progress> x)throws Exception;
  WagoTableMaterializer.Result nativeTables(SetupPlan p,SetupPlan.JobContext j,WagoTableMaterializer.Manifest m,BooleanSupplier c,Consumer<WagoTableMaterializer.Progress>x)throws Exception;
  RaidbotsHotfixMaterializer.Result hotfix(SetupPlan.JobContext j,BooleanSupplier c,Consumer<RaidbotsHotfixMaterializer.Progress>x)throws Exception;
  PinnedItemSparseDefinitionMaterializer.Result schema(SetupPlan.JobContext j,BooleanSupplier c,Consumer<PinnedItemSparseDefinitionMaterializer.Progress>x)throws Exception;
  PinnedItemSparseOverlayMaterializer.Result overlay(SetupPlan.JobContext j,WagoTableMaterializer.Result b,WagoTableMaterializer.Manifest m,RaidbotsHotfixMaterializer.Result r,PinnedItemSparseDefinitionMaterializer.Result d,BooleanSupplier c,Consumer<PinnedItemSparseOverlayMaterializer.Progress>x)throws Exception;
  LibraryBuildJob.Candidate catalogue(SetupPlan p,SetupPlan.JobContext j,SetupPlan.MaterializedInputs i,BooleanSupplier c,Consumer<LibraryBuildJob.Progress>x)throws Exception;
  RuntimeLibraryExporter.VerifiedExport runtime(LibraryBuildJob.Candidate candidate,SetupPlan.JobContext j,BooleanSupplier c,Consumer<Progress>x)throws Exception;
 }
 private final Operations operations;
 public LibraryPreparationWorkflow(){this(new ProductionOperations());}
 LibraryPreparationWorkflow(Operations operations){this.operations=Objects.requireNonNull(operations);}
 /** Rejection precedes staging or network. Other transports stay unsupported even when Wago is also selected. */
 public void preflight(SetupPlan plan)throws IOException {
  Objects.requireNonNull(plan);var preset=Forever70450Bootstrap.wagoManifest();
  if(!plan.sources().containsAll(Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA))||!plan.nativeIdentity().equals(Optional.of(preset.identity())))throw new IOException("Current library builder requires exact Forever beta 1.60.1.70450/enUS native data and Questie");
  if(!plan.acquisitions().equals(Set.of(SetupPlan.Acquisition.WAGO_EXPORT))||!plan.localGameRoots().isEmpty())throw new IOException("Current library builder supports explicitly selected Wago exports only; selected CDN/local extraction is not implemented");
  if(!plan.sourcePins().equals(SetupPlan.currentPins(plan.sources())))throw new IOException("Selected source pins changed; review a new source plan");
  Set<SetupPlan.Source> supported=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.ATLASLOOT,SetupPlan.Source.ALL_THE_THINGS,SetupPlan.Source.NATIVE_DATA,SetupPlan.Source.RAIDBOTS_HOTFIX);
  if(!supported.containsAll(plan.sources()))throw new IOException("Selected source is not supported by this library workflow");
  for(var source:SourceSpec.load()){
   SetupPlan.Source selected=source.key().equals("questiedb")?SetupPlan.Source.QUESTIE:source.key().equals("atlasloot")?SetupPlan.Source.ATLASLOOT:null;
   if(selected!=null&&plan.sources().contains(selected)&&(source.zip()!=null||source.baseline()!=null))throw new IOException("Selected community source requires unsupported archive/baseline acquisition: "+selected);
  }
 }
 public Review review(SetupPlan plan)throws IOException{
  preflight(plan);List<String> warnings=new ArrayList<>();
  if(!plan.sources().contains(SetupPlan.Source.RAIDBOTS_HOTFIX))warnings.add("The selected native-table-only preset measured 73% named item detail coverage, below the unchanged 85% gate; the current projection may be rejected. No additional source will be selected automatically.");
  warnings.add("The source preset is pinned to beta build 1.60.1.70450; verified details do not establish complete beta content, media or playable availability.");
  return new Review(Forever70450Bootstrap.wagoManifest().identity(),warnings);
 }
 public Session begin(SetupPlan plan,Path appStorage,Optional<Path> activeDatabase,String sessionId,List<Path> protectedGameRoots)throws IOException {
  preflight(plan);var job=plan.prepareJob(appStorage,activeDatabase,sessionId,protectedGameRoots);
  SetupPlanStore.save(job.storage(),job.paths().directory().resolve("preparation-plan.properties"),plan);return new Session(plan,job);
 }
 /** Retry this in-memory session to recheck/reuse downloads; every catalogue attempt uses a fresh database job. */
 public Prepared prepare(Session session,BooleanSupplier cancelled,Consumer<Progress> progress)throws Exception {
  Objects.requireNonNull(session);Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);check(cancelled);
  if(!session.running.compareAndSet(false,true))throw new IOException("This preparation session already has a running attempt");
  try{
   SetupPlan plan=session.plan;SetupPlan.JobContext cache=session.job;stage(progress,cancelled,Phase.PREFLIGHT,"Checking selected providers and protected staging");preflight(plan);cache.storage().verifyJob(cache.paths());
   if(!SetupPlanStore.load(cache.paths().directory().resolve("preparation-plan.properties")).equals(plan))throw new IOException("Session source plan changed; review a new session");
   var manifest=Forever70450Bootstrap.wagoManifest();
   stage(progress,cancelled,Phase.COMMUNITY,"Acquiring only selected community files");
   var community=operations.community(plan,cache,cancelled,p->emit(progress,cancelled,new Progress(Phase.COMMUNITY,p.source()+" "+p.path()+" "+p.stage(),p.bytes(),p.expectedBytes(),p.completed(),p.total())));
   stage(progress,cancelled,Phase.NATIVE_TABLES,"Acquiring exact pinned Forever native tables from Wago");
   var nativeTables=operations.nativeTables(plan,cache,manifest,cancelled,p->emit(progress,cancelled,new Progress(Phase.NATIVE_TABLES,p.table()+" "+p.stage(),p.bytes(),p.expectedBytes(),p.completed(),p.total())));
   Optional<RaidbotsHotfixMaterializer.Result> raw=Optional.empty();Optional<PinnedItemSparseDefinitionMaterializer.Result> schema=Optional.empty();Optional<PinnedItemSparseOverlayMaterializer.Result> overlay=Optional.empty();
   if(plan.sources().contains(SetupPlan.Source.RAIDBOTS_HOTFIX)){
    stage(progress,cancelled,Phase.HOTFIX,"Acquiring the explicitly selected pinned Raidbots snapshot");raw=Optional.of(operations.hotfix(cache,cancelled,p->emit(progress,cancelled,new Progress(Phase.HOTFIX,"Raidbots raw snapshot",p.receivedBytes(),p.expectedBytes(),0,-1))));
    stage(progress,cancelled,Phase.SCHEMA,"Acquiring the disclosed pinned WoWDBDefs schema dependency");schema=Optional.of(operations.schema(cache,cancelled,p->emit(progress,cancelled,new Progress(Phase.SCHEMA,"Reviewed ItemSparse schema",p.receivedBytes(),p.expectedBytes(),0,-1))));
    stage(progress,cancelled,Phase.OVERLAY,"Preparing conservative same-build native details");overlay=Optional.of(operations.overlay(cache,nativeTables,manifest,raw.orElseThrow(),schema.orElseThrow(),cancelled,p->emit(progress,cancelled,new Progress(Phase.OVERLAY,p.stage(),0,-1,p.completed(),p.total()))));
   }
   check(cancelled);var report=new AcquisitionReport(community,nativeTables,raw,schema,overlay);
   var attempt=new SetupPlan.JobContext(cache.storage(),cache.storage().createJob("catalogue-"+UUID.randomUUID()));session.lastCatalogue=attempt;
   writeAcquisitionReceipt(plan,attempt,report,cancelled);
   SetupPlan.MaterializedInputs inputs=inputs(community,nativeTables,overlay);
   stage(progress,cancelled,Phase.CATALOGUE,"Capturing selected sources and building a fresh staged catalogue");
   var candidate=operations.catalogue(plan,attempt,inputs,cancelled,p->emit(progress,cancelled,new Progress(Phase.CATALOGUE,p.stage()+": "+p.message(),p.completedBytes(),p.totalBytes(),0,-1)));
   stage(progress,cancelled,Phase.RUNTIME,"Preparing the compact runtime library; local database work is indeterminate");
   var runtime=operations.runtime(candidate,attempt,cancelled,p->emit(progress,cancelled,p));
   check(cancelled);
   var warnings=new ArrayList<>(review(plan).warnings());warnings.addAll(candidate.validation().warnings());Path receipt=writePreparationReceipt(plan,attempt,candidate,runtime,report,warnings,cancelled);
   emit(progress,cancelled,new Progress(Phase.READY,"Verified staged library is ready for separate app-controlled activation",runtime.receipt().outputBytes(),runtime.receipt().outputBytes(),1,1));
   return new Prepared(plan,cache,attempt,candidate,runtime,report,receipt,warnings);
  }finally{session.running.set(false);}
 }
 private static void writeAcquisitionReceipt(SetupPlan plan,SetupPlan.JobContext job,AcquisitionReport report,BooleanSupplier cancelled)throws IOException{
  Properties values=new Properties();values.setProperty("format","preparation-acquisition-v1");values.setProperty("nativeBuild",plan.nativeIdentity().orElseThrow().build());
  values.setProperty("community.manifest",report.community().manifestId());values.setProperty("community.downloadedBytes",Long.toString(report.community().downloadedBytes()));values.setProperty("community.completeCacheReused",Boolean.toString(report.community().reused()));values.setProperty("community.path",report.community().questie().toString());
  values.setProperty("wago.manifest",report.nativeTables().manifestId());values.setProperty("wago.downloadedBytes",Long.toString(report.nativeTables().downloadedBytes()));values.setProperty("wago.downloadedTables",Integer.toString(report.nativeTables().downloaded()));values.setProperty("wago.reusedTables",Integer.toString(report.nativeTables().reused()));
  long bytes=report.community().downloadedBytes()+report.nativeTables().downloadedBytes();
  if(report.hotfix().isPresent()){var raw=report.hotfix().orElseThrow();values.setProperty("raidbots.cacheReused",Boolean.toString(raw.reused()));values.setProperty("raidbots.proof",raw.evidence().toString());if(!raw.reused())bytes+=Files.size(raw.snapshot());}
  if(report.schema().isPresent()){var schema=report.schema().orElseThrow();values.setProperty("schema.cacheReused",Boolean.toString(schema.reused()));values.setProperty("schema.proof",schema.evidence().toString());if(!schema.reused())bytes+=Files.size(schema.definition());}
  values.setProperty("completedDownloadBytes",Long.toString(bytes));for(var e:plan.sourcePins().entrySet())values.setProperty("selectedPin."+e.getKey(),e.getValue());
  save(job,"acquisition-report.properties",values,cancelled);
 }
 private static Path writePreparationReceipt(SetupPlan plan,SetupPlan.JobContext job,LibraryBuildJob.Candidate candidate,RuntimeLibraryExporter.VerifiedExport runtime,AcquisitionReport acquisition,List<String> warnings,BooleanSupplier cancelled)throws IOException{
  Properties values=new Properties();values.setProperty("format","library-preparation-v1");values.setProperty("activationPerformed","false");values.setProperty("fullCandidate",candidate.database().toString());values.setProperty("fullCandidateSha256",candidate.sha256());values.setProperty("sourceArchive",candidate.sourceArchive().toString());values.setProperty("jobLog",candidate.jobLog().toString());values.setProperty("runtime",runtime.runtime().toString());
  var r=runtime.receipt();values.setProperty("runtimeSha256",r.outputSha256());values.setProperty("runtimeBytes",Long.toString(r.outputBytes()));values.setProperty("sourceBytes",Long.toString(r.sourceBytes()));values.setProperty("sourceSha256",r.sourceSha256());values.setProperty("buildId",r.buildId());values.setProperty("omittedRawRecords",Long.toString(r.omittedRawRecords()));values.setProperty("omittedArchiveBytes",Long.toString(r.omittedArchiveBytes()));
  for(var e:r.retainedTables().entrySet()){values.setProperty("retained."+e.getKey()+".rows",Long.toString(e.getValue().rows()));values.setProperty("retained."+e.getKey()+".sha256MultisetSum",e.getValue().sha256MultisetSum());}
  for(int i=0;i<warnings.size();i++)values.setProperty("warning."+i,warnings.get(i));values.setProperty("warningCount",Integer.toString(warnings.size()));return save(job,"preparation-receipt.properties",values,cancelled);
 }
 private static Path save(SetupPlan.JobContext job,String name,Properties values,BooleanSupplier cancelled)throws IOException{
  check(cancelled);Path requested=job.paths().directory().resolve(name),path=job.storage().verifyDestination(requested);if(!path.equals(requested))throw new IOException("Preparation receipt alias changed");try(var output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){values.store(output,"Staged library preparation evidence; not activation");}return path;
 }
 private static SetupPlan.MaterializedInputs inputs(CommunitySourceMaterializer.Result community,WagoTableMaterializer.Result nativeTables,Optional<PinnedItemSparseOverlayMaterializer.Result> overlay){
  return new SetupPlan.MaterializedInputs(community.questie(),community.atlasLoot(),community.allTheThings(),overlay.isPresent()?Optional.empty():Optional.of(nativeTables.client()),overlay);
 }
 private static final class ProductionOperations implements Operations {
  public CommunitySourceMaterializer.Result community(SetupPlan p,SetupPlan.JobContext j,BooleanSupplier c,Consumer<CommunitySourceMaterializer.Progress>x)throws Exception{try(var materializer=new CommunitySourceMaterializer()){return materializer.materialize(p,j,c,x);}}
  public WagoTableMaterializer.Result nativeTables(SetupPlan p,SetupPlan.JobContext j,WagoTableMaterializer.Manifest m,BooleanSupplier c,Consumer<WagoTableMaterializer.Progress>x)throws Exception{return new WagoTableMaterializer().materialize(p,j,m,c,x);}
  public RaidbotsHotfixMaterializer.Result hotfix(SetupPlan.JobContext j,BooleanSupplier c,Consumer<RaidbotsHotfixMaterializer.Progress>x)throws Exception{return new RaidbotsHotfixMaterializer().materialize(RaidbotsHotfixMaterializer.Permission.ALLOW_RAIDBOTS_HOTFIX,j,new RaidbotsHotfixMaterializer.Pin("wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS",PinnedItemSparseOverlayMaterializer.SNAPSHOT_SHA),c,x);}
  public PinnedItemSparseDefinitionMaterializer.Result schema(SetupPlan.JobContext j,BooleanSupplier c,Consumer<PinnedItemSparseDefinitionMaterializer.Progress>x)throws Exception{return new PinnedItemSparseDefinitionMaterializer().materialize(RaidbotsHotfixMaterializer.Permission.ALLOW_RAIDBOTS_HOTFIX,j,c,x);}
  public PinnedItemSparseOverlayMaterializer.Result overlay(SetupPlan.JobContext j,WagoTableMaterializer.Result b,WagoTableMaterializer.Manifest m,RaidbotsHotfixMaterializer.Result r,PinnedItemSparseDefinitionMaterializer.Result d,BooleanSupplier c,Consumer<PinnedItemSparseOverlayMaterializer.Progress>x)throws Exception{if(Files.size(d.definition())>1_048_576)throw new IOException("Schema dependency exceeds byte limit");return new PinnedItemSparseOverlayMaterializer().materialize(RaidbotsHotfixMaterializer.Permission.ALLOW_RAIDBOTS_HOTFIX,j,b,m,r,readDefinition(d.definition(),c),c,x);}
  public LibraryBuildJob.Candidate catalogue(SetupPlan p,SetupPlan.JobContext j,SetupPlan.MaterializedInputs i,BooleanSupplier c,Consumer<LibraryBuildJob.Progress>x)throws Exception{return new LibraryBuildJob().build(p,j,i,c,x);}
  public RuntimeLibraryExporter.VerifiedExport runtime(LibraryBuildJob.Candidate candidate,SetupPlan.JobContext j,BooleanSupplier c,Consumer<Progress>x)throws Exception{
   var exporter=new RuntimeLibraryExporter();Path output=j.paths().directory().resolve("runtime.sqlite");var receipt=exporter.export(candidate.database(),output,j.storage(),Files.size(candidate.database()),candidate.sha256(),c);
   emit(x,c,new Progress(Phase.VERIFY,"Verifying compact tables and source/export receipt",receipt.outputBytes(),receipt.outputBytes(),0,-1));return exporter.verifyExport(candidate.database(),output,j.storage(),receipt,c);
  }
 }
 private static byte[] readDefinition(Path file,BooleanSupplier cancelled)throws IOException{
  try(var input=Files.newInputStream(file);var output=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n,total=0;while((n=input.read(bytes))!=-1){check(cancelled);if((total+=n)>1_048_576)throw new IOException("Schema dependency exceeds byte limit");output.write(bytes,0,n);}return output.toByteArray();}
 }
 private static void stage(Consumer<Progress> progress,BooleanSupplier cancelled,Phase phase,String detail){emit(progress,cancelled,new Progress(phase,detail,0,-1,0,-1));}
 private static void emit(Consumer<Progress> progress,BooleanSupplier cancelled,Progress value){check(cancelled);progress.accept(value);check(cancelled);}
 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Library preparation cancelled");}
}
