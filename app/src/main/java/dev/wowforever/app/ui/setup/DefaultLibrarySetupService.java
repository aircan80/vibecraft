package dev.wowforever.app.ui.setup;

import dev.wowforever.app.db.LibraryActivation;
import dev.wowforever.importer.setup.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exact Forever bootstrap adapter. Construction does no I/O; no default GUI binding is provided here. */
public final class DefaultLibrarySetupService implements LibrarySetupService {
  private static final Logger LOG=LoggerFactory.getLogger(DefaultLibrarySetupService.class);
  private static final Map<String,SetupPlan.Source> SOURCE_IDS=Map.of("questie",SetupPlan.Source.QUESTIE,"native",SetupPlan.Source.NATIVE_DATA,"atlas",SetupPlan.Source.ATLASLOOT,"att",SetupPlan.Source.ALL_THE_THINGS,"raidbots-hotfix",SetupPlan.Source.RAIDBOTS_HOTFIX);
  private static final Set<String> REQUIRED=Set.of("questie","native","wago","raidbots-hotfix");
  interface Session {}
  interface Prepared {}
  interface Operations {
    void preflight(SetupPlan plan)throws Exception;
    List<String> review(SetupPlan plan)throws Exception;
    Session begin(SetupPlan plan)throws Exception;
    Prepared prepare(Session session,BooleanSupplier cancelled,Consumer<LibraryPreparationWorkflow.Progress> progress)throws Exception;
    Path publish(Prepared prepared,SetupPlan plan,BooleanSupplier cancelled)throws Exception;
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
      Path registered=operations.publish(prepared,context.plan(),control.cancellationRequested());
      // Publication may have committed its atomic selection pointer. Do not delete staging or claim a rollback.
      return new Result(registered,"The checked compact Forever library is registered for next startup. Your current session is unchanged. Full source, logs and acquisition evidence remain in app-owned storage.",true);
    }catch(CancellationException stopped){throw stopped;}catch(Exception failure){
      LOG.error("Library preparation/publication failed",failure);
      boolean coverage=false;for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT).contains("coverage"))coverage=true;
      throw new JobFailure(coverage?"Some item details are still missing. The required data checks did not pass; review sources or retry verified downloads. No other edition or provider was selected.":"The library could not be prepared or registered. Your current session is unchanged. Review sources, storage and the retained job log before retrying.",failure);
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
    public Path publish(Prepared prepared,SetupPlan plan,BooleanSupplier cancelled)throws Exception{var value=((WorkflowPrepared)prepared).value();return new LibraryActivation(storage,protectedRoots,active).activateExportedForNextStartup(value.fullCandidate(),value.runtime().runtime(),value.runtime().receipt(),plan,cancelled).database();}
  }
}
