package dev.wowforever.app.ui.setup;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.setup.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Authored backend operations verify the app bridge, not a production network import. */
class DefaultLibrarySetupServiceTest {
  private static final Set<String> REQUIRED=Set.of("questie","native","wago","raidbots-hotfix");
  private static final LibrarySetupService.Control CONTROL=new LibrarySetupService.Control(()->false,event->{});
  private static LibrarySetupDraft draft(Set<String> sources){return new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(sources,Optional.empty(),false)));}
  private static class Ops implements DefaultLibrarySetupService.Operations {
    final AtomicInteger begins=new AtomicInteger(),prepares=new AtomicInteger(),publishes=new AtomicInteger();
    final List<String> order=new ArrayList<>();SetupPlan plan;
    private record Session()implements DefaultLibrarySetupService.Session{}
    private record Prepared()implements DefaultLibrarySetupService.Prepared{}
    public void preflight(SetupPlan plan){}
    public List<String> review(SetupPlan plan){this.plan=plan;return List.of("Authored review warning.");}
    public DefaultLibrarySetupService.Session begin(SetupPlan plan){begins.incrementAndGet();return new Session();}
    public DefaultLibrarySetupService.Prepared prepare(DefaultLibrarySetupService.Session session,BooleanSupplier cancelled,Consumer<LibraryPreparationWorkflow.Progress> progress)throws Exception{prepares.incrementAndGet();order.add("prepare");progress.accept(new LibraryPreparationWorkflow.Progress(LibraryPreparationWorkflow.Phase.NATIVE_TABLES,"raw-source-path",12,20,2,51));return new Prepared();}
    public DefaultLibrarySetupService.Publication publish(DefaultLibrarySetupService.Prepared prepared,SetupPlan plan,BooleanSupplier cancelled){publishes.incrementAndGet();order.add("publish");return new DefaultLibrarySetupService.Publication(Path.of("managed/version/runtime.sqlite"),"Authored storage summary.");}
  }
  @Test void missingWagoAndHotfixAreExplicitBlockersWithoutStagingOrOptIn()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);var draft=draft(Set.of("questie","native"));var review=service.review(draft,CONTROL);
    assertThat(review.ready()).isFalse();assertThat(review.blockers()).anyMatch(text->text.contains("Wago")).anyMatch(text->text.contains("Raidbots"));assertThat(draft.games().get("forever").sources()).containsExactlyInAnyOrder("questie","native");assertThat(ops.begins).hasValue(0);assertThat(ops.prepares).hasValue(0);
  }
  @Test void exactSelectedSourcesPinsAndSessionAreRetainedAcrossReviews()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);var ids=new HashSet<>(REQUIRED);ids.add("atlas");ids.add("att");var review=service.review(draft(ids),CONTROL);assertThat(review.ready()).isTrue();
    assertThat(ops.plan.sources()).containsExactlyInAnyOrder(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA,SetupPlan.Source.RAIDBOTS_HOTFIX,SetupPlan.Source.ATLASLOOT,SetupPlan.Source.ALL_THE_THINGS);
    assertThat(ops.plan.acquisitions()).containsExactly(SetupPlan.Acquisition.WAGO_EXPORT);assertThat(ops.plan.nativeIdentity()).contains(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS"));assertThat(ops.plan.sourcePins()).isEqualTo(SetupPlan.currentPins(ops.plan.sources()));
    service.review(draft(ids),CONTROL);assertThat(ops.begins).hasValue(1);assertThat(review.summary()).contains("GitHub/WoWDBDefs","Wago","Authored review warning");
  }
  @Test void plannedGamesCannotBeSilentlyIgnored()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);
    var draft=new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(REQUIRED,Optional.of(Path.of("wow")),true),"classic",new LibrarySetupDraft.Profile(Set.of("native"),Optional.empty(),false)));
    var review=service.review(draft,CONTROL);assertThat(review.ready()).isFalse();assertThat(review.blockers()).anyMatch(text->text.contains("classic"));assertThat(ops.begins).hasValue(0);
  }
  @Test void optionalLocalAndCdnPermissionsArePreservedWhileCatalogueUsesWago()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);Path folder=Path.of("missing-wow-folder");
    var draft=new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(REQUIRED,Optional.of(folder),true)));
    var review=service.review(draft,CONTROL);assertThat(review.ready()).isTrue();
    assertThat(ops.plan.acquisitions()).containsExactlyInAnyOrder(SetupPlan.Acquisition.WAGO_EXPORT,SetupPlan.Acquisition.LOCAL_INSTALLATION,SetupPlan.Acquisition.BLIZZARD_CDN);
    assertThat(ops.plan.localGameRoots()).containsExactly(folder.toAbsolutePath().normalize());
    assertThat(review.summary()).contains("permissions are preserved","catalogue extraction from those sources is not used","protected from app writes");
    service.build(review,LibrarySetupService.Mode.MANAGEMENT,CONTROL);assertThat(ops.publishes).hasValue(1);
  }
  @Test void prepareThenPublishReturnsOnlyTheRegisteredVersionWithRealReportedCounts()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);var review=service.review(draft(REQUIRED),CONTROL);var events=new ArrayList<LibrarySetupService.Progress>();
    var result=service.build(review,LibrarySetupService.Mode.MANAGEMENT,new LibrarySetupService.Control(()->false,events::add));
    assertThat(ops.order).containsExactly("prepare","publish");assertThat(result.database()).isEqualTo(Path.of("managed/version/runtime.sqlite"));assertThat(result.nextStartupRegistered()).isTrue();assertThat(result.summary()).contains("current session is unchanged","Authored storage summary.").doesNotContain("Full source");
    assertThat(events).anyMatch(event->event.phase()==LibrarySetupService.Phase.DOWNLOAD&&event.completed()==12&&event.total()==20&&event.unit().equals("bytes"));assertThat(events).allMatch(event->!event.detail().contains("raw-source-path"));
  }
  @Test void preparationFailureAndCancellationNeverPublishAnActiveSelection()throws Exception{
    var ops=new Ops(){@Override public DefaultLibrarySetupService.Prepared prepare(DefaultLibrarySetupService.Session session,BooleanSupplier stop,Consumer<LibraryPreparationWorkflow.Progress> events)throws Exception{throw new IOException("Required item detail coverage failed");}};
    var service=new DefaultLibrarySetupService(ops);var review=service.review(draft(REQUIRED),CONTROL);
    assertThatThrownBy(()->service.build(review,LibrarySetupService.Mode.FIRST_RUN,CONTROL)).isInstanceOf(LibrarySetupService.JobFailure.class).hasMessageContaining("Some item details are still missing");assertThat(ops.publishes).hasValue(0);
    assertThatThrownBy(()->service.build(review,LibrarySetupService.Mode.FIRST_RUN,new LibrarySetupService.Control(()->true,event->{}))).isInstanceOf(CancellationException.class);assertThat(ops.publishes).hasValue(0);
  }
  @Test void confirmedAtomicPublicationResultWinsALateCancellationRequest()throws Exception{
    var stop=new java.util.concurrent.atomic.AtomicBoolean();
    var ops=new Ops(){@Override public DefaultLibrarySetupService.Publication publish(DefaultLibrarySetupService.Prepared prepared,SetupPlan plan,BooleanSupplier cancelled){
      assertThat(cancelled.getAsBoolean()).isFalse();var registered=super.publish(prepared,plan,cancelled);
      // Models cancellation arriving after the atomic selection pointer has committed.
      stop.set(true);return registered;
    }};
    var service=new DefaultLibrarySetupService(ops);var review=service.review(draft(REQUIRED),CONTROL);
    var result=service.build(review,LibrarySetupService.Mode.MANAGEMENT,new LibrarySetupService.Control(stop::get,event->{}));
    assertThat(stop).isTrue();assertThat(result.nextStartupRegistered()).isTrue();assertThat(result.database()).isEqualTo(Path.of("managed/version/runtime.sqlite"));assertThat(ops.publishes).hasValue(1);
  }
  @Test void completedCleanupSummaryAndOutcomePreserveRegisteredPath() {
    var outcome=new java.util.concurrent.atomic.AtomicReference<Properties>();Path registered=Path.of("managed/runtime.sqlite");
    var result=DefaultLibrarySetupService.finishPublication(registered,"a".repeat(64),()->new CompletedLibraryJobCleanup.Report(1_284_538_368L,List.of(Path.of("source-archive.sqlite"),Path.of("candidate.sqlite")),List.of(),true),outcome::set);
    assertThat(result.database()).isEqualTo(registered);assertThat(result.storageSummary()).contains("1.28 GB","reusable downloads","support receipts").doesNotContain("Full source");
    assertThat(outcome.get()).containsEntry("status","complete").containsEntry("deletedBytes","1284538368").containsEntry("runtimeSha256","a".repeat(64));
  }
  @Test void partialCleanupWarningReportsActualRemovedBytesWithoutHidingRegistration() {
    var outcome=new java.util.concurrent.atomic.AtomicReference<Properties>();Path registered=Path.of("managed/runtime.sqlite");
    var result=DefaultLibrarySetupService.finishPublication(registered,"a".repeat(64),()->new CompletedLibraryJobCleanup.Report(642_269_184L,List.of(Path.of("source-archive.sqlite")),List.of("Inspect remaining candidate before retry."),false),outcome::set);
    assertThat(result.database()).isEqualTo(registered);assertThat(result.storageSummary()).contains("Storage warning","incomplete","642.3 MB","Inspect remaining candidate");assertThat(outcome.get()).containsEntry("status","partial").containsEntry("deletedFiles","source-archive.sqlite");
  }
  @Test void refusedCleanupAndFailedOutcomePersistenceRemainNonfatal() {
    var outcome=new java.util.concurrent.atomic.AtomicReference<Properties>();Path registered=Path.of("managed/runtime.sqlite");
    var refused=DefaultLibrarySetupService.finishPublication(registered,"a".repeat(64),()->{throw new IOException("Protected job or live sidecars");},outcome::set);
    assertThat(refused.database()).isEqualTo(registered);assertThat(refused.storageSummary()).contains("Storage warning","may remain");assertThat(outcome.get()).containsEntry("status","failed");
    var unsaved=DefaultLibrarySetupService.finishPublication(registered,"a".repeat(64),()->new CompletedLibraryJobCleanup.Report(42,List.of(),List.of(),true),p->{throw new IOException("Outcome directory unavailable");});
    assertThat(unsaved.database()).isEqualTo(registered);assertThat(unsaved.storageSummary()).contains("Removed 42 bytes","cleanup report could not be saved");
  }
  @Test void cancellationAlreadyRequestedAfterRegistrationReturnsRegisteredResultWithStorageWarning()throws Exception {
    var stop=new java.util.concurrent.atomic.AtomicBoolean();var outcome=new java.util.concurrent.atomic.AtomicReference<Properties>();
    var ops=new Ops(){@Override public DefaultLibrarySetupService.Publication publish(DefaultLibrarySetupService.Prepared prepared,SetupPlan plan,BooleanSupplier cancelled){
      var registered=super.publish(prepared,plan,cancelled);stop.set(true);
      return DefaultLibrarySetupService.finishPublication(registered.database(),"a".repeat(64),()->{assertThat(cancelled.getAsBoolean()).isTrue();throw new CancellationException("Cleanup cancelled after durable selection");},outcome::set);
    }};
    var service=new DefaultLibrarySetupService(ops);var review=service.review(draft(REQUIRED),CONTROL);
    var result=service.build(review,LibrarySetupService.Mode.MANAGEMENT,new LibrarySetupService.Control(stop::get,event->{}));
    assertThat(result.nextStartupRegistered()).isTrue();assertThat(result.database()).isEqualTo(Path.of("managed/version/runtime.sqlite"));assertThat(result.summary()).contains("registered for next startup","cleanup was cancelled");assertThat(outcome.get()).containsEntry("status","cancelled");
  }
  @Test void cleanupDoesNotSwallowVirtualMachineErrors() {
    assertThatThrownBy(()->DefaultLibrarySetupService.finishPublication(Path.of("managed/runtime.sqlite"),"a".repeat(64),()->{throw new OutOfMemoryError("Authored error; no allocation failure");},p->{})).isInstanceOf(OutOfMemoryError.class);
  }
  @Test void cleanupOutcomeIsPersistedOnlyWithinOwnedOriginalJob(@org.junit.jupiter.api.io.TempDir Path root)throws Exception {
    var storage=new SetupStorage(root,List.of(root.resolve("game")),Optional.of(root.resolve("active.sqlite")));var job=new SetupPlan.JobContext(storage,storage.createJob("completed"));
    var outcome=new Properties();outcome.setProperty("status","cancelled");outcome.setProperty("registeredPath",root.resolve("libraries/runtime.sqlite").toString());
    DefaultLibrarySetupService.saveCleanupOutcome(job,outcome);
    try(var files=java.nio.file.Files.list(job.paths().directory())){
      var reports=files.filter(path->path.getFileName().toString().startsWith("cleanup-outcome-")).toList();assertThat(reports).hasSize(1);
      var actual=new Properties();try(var in=java.nio.file.Files.newInputStream(reports.getFirst())){actual.load(in);}assertThat(actual).containsEntry("status","cancelled");
    }
    Path protectedJob=root.resolve("game/jobs/blocked");java.nio.file.Files.createDirectories(protectedJob);
    var paths=new SetupStorage.JobPaths(protectedJob,protectedJob.resolve("source-archive.sqlite"),protectedJob.resolve("candidate.sqlite"),protectedJob.resolve("cache"),protectedJob.resolve("build-id.txt"),protectedJob.resolve("import-report.txt"));
    assertThatThrownBy(()->DefaultLibrarySetupService.saveCleanupOutcome(new SetupPlan.JobContext(storage,paths),outcome)).isInstanceOf(IOException.class);
  }
  @Test void productionCapabilityPreflightDoesNotCreateStorageOrAdvertiseUnsupportedSources()throws Exception{
    Path root=java.nio.file.Files.createTempDirectory("vibecraft-adapter-capabilities-");
    try{
      Path storage=root.resolve("not-created");var service=new DefaultLibrarySetupService(storage,Optional.empty());
      var sources=service.capabilities().sourcesByGame();assertThat(sources.keySet()).containsExactly("forever");assertThat(sources.get("forever")).containsAll(REQUIRED).doesNotContain("vmangos","collector");assertThat(java.nio.file.Files.exists(storage)).isFalse();
    }finally{java.nio.file.Files.delete(root);}
  }
  @Test void forgedConsentAndEdtCallsAreRejected()throws Exception{
    var ops=new Ops();var service=new DefaultLibrarySetupService(ops);var review=service.review(draft(REQUIRED),CONTROL);
    var changed=new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(REQUIRED,Optional.empty(),true)));
    var forged=new LibrarySetupService.Review(changed,"",List.of(),review.plan());assertThatThrownBy(()->service.build(forged,LibrarySetupService.Mode.MANAGEMENT,CONTROL)).isInstanceOf(LibrarySetupService.JobFailure.class);assertThat(ops.prepares).hasValue(0);
    SwingUtilities.invokeAndWait(()->assertThatThrownBy(service::capabilities).isInstanceOf(IllegalStateException.class));
  }
}
