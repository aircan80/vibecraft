package dev.wowforever.app.ui.setup;

import dev.wowforever.app.db.LibraryActivation;
import dev.wowforever.importer.setup.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exact Forever bootstrap adapter. Construction does no I/O; preparation and publication run on a worker. */
public final class DefaultLibrarySetupService implements LibrarySetupService {
  private static final Logger LOG=LoggerFactory.getLogger(DefaultLibrarySetupService.class);
  private static final Map<String,SetupPlan.Source> SOURCE_IDS=Map.of("questie",SetupPlan.Source.QUESTIE,"native",SetupPlan.Source.NATIVE_DATA,"atlas",SetupPlan.Source.ATLASLOOT,"att",SetupPlan.Source.ALL_THE_THINGS,"raidbots-hotfix",SetupPlan.Source.RAIDBOTS_HOTFIX);
  private static final Set<String> REQUIRED=Set.of("questie","native","wago","raidbots-hotfix");
  record Publication(Path database,String storageSummary) {
    Publication {Objects.requireNonNull(database);Objects.requireNonNull(storageSummary);}
  }
  @FunctionalInterface interface CleanupAttempt {CompletedLibraryJobCleanup.Report run()throws Exception;}
  @FunctionalInterface interface CleanupOutcomeWriter {void save(Properties outcome)throws Exception;}
  interface Session {}
  interface Prepared {}
  interface Operations {
    void preflight(SetupPlan plan)throws Exception;
    List<String> review(SetupPlan plan)throws Exception;
    Session begin(SetupPlan plan)throws Exception;
    Prepared prepare(Session session,BooleanSupplier cancelled,Consumer<LibraryPreparationWorkflow.Progress> progress)throws Exception;
    Publication publish(Prepared prepared,SetupPlan plan,BooleanSupplier cancelled)throws Exception;
  }
  private record Context(UUID owner,LibrarySetupDraft draft,SetupPlan plan,Session session) implements PreparedPlan {}
  private final UUID owner=UUID.randomUUID();
  private final Operations operations;
  private final Map<SetupPlan,Session> sessions=new ConcurrentHashMap<>();

  public DefaultLibrarySetupService(Path appStorage,Optional<Path> activeDatabase){this(appStorage,List.of(),activeDatabase);}
  public DefaultLibrarySetupService(Path appStorage,List<Path> protectedGameRoots,Optional<Path> activeDatabase){
    this(new ProductionOperations(appStorage,protectedGameRoots,activeDatabase));
  }
  DefaultLibrarySetupService(Operations operations){this.operations=Objects.requireNonNull(operations);}
  @Override public Capabilities capabilities()throws Exception{
    worker();var supported=new HashSet<>(REQUIRED);
    for(String optional:List.of("atlas","att")){
      var ids=new HashSet<>(REQUIRED);ids.add(optional);
      try{operations.preflight(plan(ids));supported.add(optional);}catch(Exception unavailable){LOG.debug("Optional setup provider {} is unavailable",optional,unavailable);}
    }
    return new Capabilities(Map.of("forever",supported));
  }
  @Override public Review review(LibrarySetupDraft draft,Control control)throws Exception{
    worker();control.boundary();var blockers=new ArrayList<>(capabilities().blockers(draft));
    if(draft.games().size()!=1||!draft.games().containsKey("forever"))blockers.add("Only the Forever beta catalogue can be built right now. Other selected versions are not silently omitted.");
    var profile=draft.games().get("forever");
    if(profile!=null){
      if(!profile.sources().contains("questie"))blockers.add("QuestieDB is required by the current catalogue builder.");
      if(!profile.sources().contains("native"))blockers.add("Select Blizzard native data for the current Forever catalogue.");
      if(!profile.sources().contains("wago"))blockers.add("Current catalogue tables require explicitly selected Wago exports. Local CASC extraction is not connected to this builder.");
      if(!profile.sources().contains("raidbots-hotfix"))blockers.add("Some named items still lack native details with Wago alone. The current 70450 coverage gate needs the separately selected Raidbots hotfix details source. Nothing will be selected automatically.");
    }
    control.boundary();
    if(!blockers.isEmpty())return new Review(draft,"Review the selected sources before building.",List.copyOf(new LinkedHashSet<>(blockers)),null);
    SetupPlan plan=plan(profile);
    try{
      var warnings=operations.review(plan);control.boundary();
      Session session=sessions.get(plan);
      if(session==null){session=operations.begin(plan);control.boundary();if(sessions.size()>=8)sessions.clear();sessions.put(plan,session);}
      String summary="Forever beta 1.60.1.70450 / enUS. Catalogue tables come from the explicitly selected Wago exports; local/CDN permissions are preserved but catalogue extraction from those sources is not used by this build. A remembered folder may be unavailable while Wago supplies the catalogue; the folder remains protected from app writes. Raidbots is a separately selected pinned third-party hotfix mirror, with its disclosed GitHub/WoWDBDefs schema dependency. Downloads begin only when Build is selected.";
      if(!warnings.isEmpty())summary+=" "+String.join(" ",warnings);
      return new Review(draft,summary,List.of(),new Context(owner,draft,plan,session));
    }catch(CancellationException stopped){throw stopped;}catch(Exception failure){LOG.error("Library source review failed",failure);throw new JobFailure("The selected source plan or app storage could not be prepared. Review source compatibility and the data location.",failure);}
  }
  @Override public Result build(Review review,Mode mode,Control control)throws Exception{
    worker();control.boundary();
    if(!review.ready()||!(review.plan() instanceof Context context)||!context.owner().equals(owner)||!context.draft().equals(review.draft())||review.draft().games().size()!=1||!review.draft().games().containsKey("forever")||!plan(review.draft().games().get("forever")).equals(context.plan()))throw new JobFailure("Review the current source choices again before building.");
    try{
      var prepared=operations.prepare(context.session(),control.cancellationRequested(),progress->report(progress,control));control.boundary();
      control.progress().accept(new Progress(Phase.OPEN,"Registering the verified compact library for the next startup."));control.boundary();
      Publication registered=operations.publish(prepared,context.plan(),control.cancellationRequested());
      // Registration is already durable; a late cancellation or cleanup warning cannot undo it.
      return new Result(registered.database(),"The checked compact Forever library is registered for next startup. Your current session is unchanged. "+registered.storageSummary(),true);
    }catch(CancellationException stopped){throw stopped;}catch(Exception failure){
      LOG.error("Library preparation/publication failed",failure);
      boolean coverage=false;for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT).contains("coverage"))coverage=true;
      throw new JobFailure(coverage?"Some item details are still missing. The required data checks did not pass; review sources or retry verified downloads. No other edition or provider was selected.":"The library could not be prepared or registered. Your current session is unchanged. Review sources, storage and the retained job log before retrying.",failure);
    }
  }
  /** Runs only after activation returned its registered path. Cleanup never changes that result. */
  static Publication finishPublication(Path registered,String runtimeSha,CleanupAttempt cleanup,CleanupOutcomeWriter writer) {
    var outcome=new Properties();outcome.setProperty("format","library-cleanup-outcome-v1");
    outcome.setProperty("registeredPath",registered.toString());outcome.setProperty("runtimeSha256",runtimeSha);
    outcome.setProperty("recordedAt",java.time.Instant.now().toString());String summary;
    try {
      var report=cleanup.run();outcome.setProperty("status",report.complete()?"complete":"partial");
      outcome.setProperty("deletedBytes",Long.toString(report.deletedBytes()));outcome.setProperty("deletedFiles",report.deleted().stream().map(path->path.getFileName().toString()).collect(java.util.stream.Collectors.joining(",")));
      outcome.setProperty("warningCount",Integer.toString(report.warnings().size()));
      for(int i=0;i<report.warnings().size();i++)outcome.setProperty("warning."+i,report.warnings().get(i));
      summary=report.complete()?"Removed "+storageSize(report.deletedBytes())+" of temporary build databases.":"Storage warning: temporary build cleanup was incomplete; removed "+storageSize(report.deletedBytes())+". Some staging files remain for review.";
      if(!report.warnings().isEmpty())summary+=" "+String.join(" ",report.warnings());
    }catch(CancellationException stopped){
      outcome.setProperty("status","cancelled");summary="Storage warning: temporary build cleanup was cancelled. Staging files remain for review.";
      LOG.info("Library registration succeeded; staging cleanup was cancelled");
    }catch(Exception failure){
      outcome.setProperty("status","failed");summary="Storage warning: temporary build cleanup could not be completed. Some staging files may remain for review.";
      LOG.warn("Library registration succeeded; staging cleanup was refused or failed",failure);
    }
    summary+=" Runtime copies, reusable downloads, logs and support receipts are retained.";
    outcome.setProperty("summary",summary);
    try {writer.save(outcome);}catch(Exception failure){
      LOG.warn("Library registration succeeded; cleanup outcome could not be saved",failure);
      summary+=" Storage warning: the cleanup report could not be saved.";
    }
    return new Publication(registered,summary);
  }
  private static String storageSize(long bytes) {
    if(bytes>=1_000_000_000L)return String.format(Locale.ROOT,"%.2f GB",bytes/1_000_000_000d);
    if(bytes>=1_000_000L)return String.format(Locale.ROOT,"%.1f MB",bytes/1_000_000d);
    if(bytes>=1000)return String.format(Locale.ROOT,"%.1f KB",bytes/1000d);
    return bytes+" bytes";
  }
  static void saveCleanupOutcome(SetupPlan.JobContext job,Properties outcome)throws IOException {
    job.storage().verifyJob(job.paths());
    Path requested=job.paths().directory().resolve("cleanup-outcome-"+UUID.randomUUID()+".properties"),file=job.storage().verifyDestination(requested);
    if(!file.equals(requested))throw new IOException("Cleanup outcome storage alias changed");
    var bytes=new ByteArrayOutputStream();outcome.store(bytes,"Registration succeeded; post-activation staging cleanup outcome");
    if(bytes.size()>65536)throw new IOException("Cleanup outcome exceeds its bound");
    try(var channel=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
      var buffer=ByteBuffer.wrap(bytes.toByteArray());while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
    }
  }
  private static SetupPlan plan(Set<String> ids){return plan(new LibrarySetupDraft.Profile(ids,Optional.empty(),false));}
  private static SetupPlan plan(LibrarySetupDraft.Profile profile){
    Set<String> ids=profile.sources();
    var sources=EnumSet.noneOf(SetupPlan.Source.class);
    for(String id:ids){if(id.equals("wago"))continue;var source=SOURCE_IDS.get(id);if(source==null)throw new IllegalArgumentException("Unsupported selected source: "+id);sources.add(source);}
    var acquisitions=EnumSet.noneOf(SetupPlan.Acquisition.class);
    if(ids.contains("wago"))acquisitions.add(SetupPlan.Acquisition.WAGO_EXPORT);
    if(profile.readOnlyGameFolder().isPresent())acquisitions.add(SetupPlan.Acquisition.LOCAL_INSTALLATION);
    if(profile.allowBlizzardCdn())acquisitions.add(SetupPlan.Acquisition.BLIZZARD_CDN);
    return new SetupPlan(sources,acquisitions,Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),profile.readOnlyGameFolder().stream().toList(),SetupPlan.currentPins(sources));
  }
  private static void report(LibraryPreparationWorkflow.Progress event,Control control){
    LOG.debug("Library preparation {}: {}",event.phase(),event.detail());
    Phase phase=switch(event.phase()){
      case COMMUNITY,NATIVE_TABLES,HOTFIX,SCHEMA->Phase.DOWNLOAD;
      case PREFLIGHT,OVERLAY->Phase.PREPARE;
      case CATALOGUE->Phase.BUILD;
      case RUNTIME->Phase.TRIM;
      case VERIFY,READY->Phase.CHECK;
    };
    String detail=switch(event.phase()){
      case COMMUNITY->"Downloading only the selected reference sources.";
      case NATIVE_TABLES->"Downloading exact Forever build tables from Wago.";
      case HOTFIX->"Downloading the separately selected pinned Raidbots hotfix snapshot.";
      case SCHEMA->"Downloading the disclosed pinned schema from GitHub/WoWDBDefs.";
      case OVERLAY->"Preparing supported same-build item details.";
      case CATALOGUE->"Building a fresh catalogue from the reviewed sources.";
      case RUNTIME->"Trimming raw source records from the runtime copy.";
      case VERIFY,READY->"Checking the compact library and its source receipts.";
      case PREFLIGHT->"Checking the reviewed source plan and protected storage.";
    };
    long completed=-1,total=-1;String unit="";
    if(event.bytes()>0||event.expectedBytes()>0){completed=event.bytes();total=event.expectedBytes();unit="bytes";}
    else if(event.total()>=0){completed=event.completed();total=event.total();unit=event.phase()==LibraryPreparationWorkflow.Phase.NATIVE_TABLES?"tables":event.phase()==LibraryPreparationWorkflow.Phase.COMMUNITY?"files":"records";}
    control.progress().accept(new Progress(phase,detail,completed,total,unit));
  }
  private static void worker(){if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Library setup work must run off the EDT");}
  private static final class ProductionOperations implements Operations {
    private record WorkflowSession(LibraryPreparationWorkflow.Session value)implements Session{}
    private record WorkflowPrepared(LibraryPreparationWorkflow.Prepared value)implements Prepared{}
    private final LibraryPreparationWorkflow workflow=new LibraryPreparationWorkflow();
    private final Path storage;private final List<Path> protectedRoots;private final Optional<Path> active;
    ProductionOperations(Path storage,List<Path> roots,Optional<Path> active){this.storage=Objects.requireNonNull(storage);protectedRoots=List.copyOf(roots);this.active=Objects.requireNonNull(active);}
    public void preflight(SetupPlan plan)throws Exception{workflow.preflight(plan);}
    public List<String> review(SetupPlan plan)throws Exception{return workflow.review(plan).warnings();}
    public Session begin(SetupPlan plan)throws Exception{return new WorkflowSession(workflow.begin(plan,storage,active,"setup-"+UUID.randomUUID(),protectedRoots));}
    public Prepared prepare(Session session,BooleanSupplier cancelled,Consumer<LibraryPreparationWorkflow.Progress> progress)throws Exception{return new WorkflowPrepared(workflow.prepare(((WorkflowSession)session).value(),cancelled,progress));}
    public Publication publish(Prepared prepared,SetupPlan plan,BooleanSupplier cancelled)throws Exception {
      var value=((WorkflowPrepared)prepared).value();
      Path registered=new LibraryActivation(storage,protectedRoots,active).activateExportedForNextStartup(value.fullCandidate(),value.runtime().runtime(),value.runtime().receipt(),plan,cancelled).database();
      return finishPublication(registered,value.runtime().receipt().outputSha256(),()->{
        Path archive=value.catalogueJob().paths().archiveDatabase();
        var identity=new CompletedLibraryJobCleanup.ArchiveIdentity(Files.size(archive),RuntimeLibraryExporter.hashFile(archive,cancelled));
        return new CompletedLibraryJobCleanup().cleanupCompleted(value.catalogueJob(),value.runtime().receipt(),identity,cancelled);
      },outcome->saveCleanupOutcome(value.catalogueJob(),outcome));
    }
  }
}
