package dev.wowforever.app.ui.setup;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Authored adapter fixtures exercise orchestration, not production acquisition or projection. */
class LibrarySetupCoordinatorTest {
  static LibrarySetupDraft draft(){return new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(Set.of("questie","native"),Optional.empty(),false)));}
  static class AuthoredService implements LibrarySetupService {
    final AtomicInteger reviewed=new AtomicInteger(),built=new AtomicInteger();
    @Override public Capabilities capabilities(){assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return new Capabilities(Map.of("forever",Set.of("questie","native","wago","raidbots-hotfix")));}
    @Override public Review review(LibrarySetupDraft draft,Control control)throws Exception{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();reviewed.incrementAndGet();return new Review(draft,"Authored adapter is ready.",List.of(),new PreparedPlan(){});}
    @Override public Result build(Review review,Mode mode,Control control)throws Exception{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();built.incrementAndGet();control.progress().accept(new Progress(Phase.DOWNLOAD,"Authored byte report",12,20,"bytes"));control.boundary();control.progress().accept(new Progress(Phase.BUILD,"Authored projection stage"));return new Result(Path.of("authored-library.sqlite"),"Authored result; no production pipeline ran.",mode==Mode.MANAGEMENT);}
  }
  @Test void draftCopiesEverySelectedChoiceWithoutAddingProviders(){
    var selected=new HashSet<>(Set.of("native","wago"));var map=new HashMap<String,LibrarySetupDraft.Profile>();
    map.put("forever",new LibrarySetupDraft.Profile(selected,Optional.of(Path.of("read-only-wow")),false));var draft=new LibrarySetupDraft("forever",map);selected.add("raidbots-hotfix");map.clear();
    assertThat(draft.games().get("forever").sources()).containsExactlyInAnyOrder("native","wago");assertThat(draft.games().get("forever").allowBlizzardCdn()).isFalse();assertThat(draft.games().get("forever").readOnlyGameFolder()).contains(Path.of("read-only-wow"));
  }
  @Test void capabilitiesAndBuildRunInBackgroundWithOnlyProviderReportedPhaseCounts()throws Exception{
    var service=new AuthoredService();var ref=new AtomicReference<LibrarySetupCoordinator>();var events=new ArrayList<LibrarySetupCoordinator.Snapshot>();
    SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,value->{assertThat(SwingUtilities.isEventDispatchThread()).isTrue();events.add(value);}));ref.get().review(draft());});
    await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);
    SwingUtilities.invokeAndWait(()->ref.get().build(draft()));await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.READY);
    SwingUtilities.invokeAndWait(()->{assertThat(events).anyMatch(event->event.progress()!=null&&event.progress().completed()==12&&event.progress().total()==20);assertThat(ref.get().snapshot().result().database()).isEqualTo(Path.of("authored-library.sqlite"));ref.get().close();});
    assertThat(service.reviewed).hasValue(1);assertThat(service.built).hasValue(1);
  }
  @Test void cancellationWaitsForBoundaryAndNeverReturnsReady()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var service=new AuthoredService(){@Override public Result build(Review review,Mode mode,Control control)throws Exception{entered.countDown();release.await(4,TimeUnit.SECONDS);control.boundary();return super.build(review,mode,control);}};
    var ref=coordinator(service);await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);SwingUtilities.invokeAndWait(()->ref.get().build(draft()));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
    SwingUtilities.invokeAndWait(()->{ref.get().cancel();assertThat(ref.get().snapshot().state()).isEqualTo(LibrarySetupCoordinator.State.CANCELLING);assertThat(ref.get().snapshot().result()).isNull();});
    release.countDown();await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.CANCELLED);SwingUtilities.invokeAndWait(()->ref.get().close());
  }
  @Test void confirmedRegistrationRemainsReadyWhenCancellationArrivesAfterPublication()throws Exception{
    var committed=new CountDownLatch(1);var release=new CountDownLatch(1);
    var service=new AuthoredService(){@Override public Result build(Review review,Mode mode,Control control)throws Exception{committed.countDown();release.await(4,TimeUnit.SECONDS);return new Result(Path.of("registered.sqlite"),"Registration committed",true);}};
    var ref=coordinator(service);await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);
    SwingUtilities.invokeAndWait(()->ref.get().build(draft()));assertThat(committed.await(3,TimeUnit.SECONDS)).isTrue();
    SwingUtilities.invokeAndWait(()->ref.get().cancel());release.countDown();
    await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.READY);
    SwingUtilities.invokeAndWait(()->{assertThat(ref.get().snapshot().result().nextStartupRegistered()).isTrue();ref.get().close();});
  }
  @Test void closeAndInvalidationSuppressLateReviewCallbacks()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);var callbacks=new AtomicInteger();
    var service=new AuthoredService(){@Override public Review review(LibrarySetupDraft draft,Control control)throws Exception{entered.countDown();try{release.await(4,TimeUnit.SECONDS);return super.review(draft,control);}finally{finished.countDown();}}};
    var ref=new AtomicReference<LibrarySetupCoordinator>();SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,event->callbacks.incrementAndGet()));ref.get().review(draft());});
    assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();int[] atClose={0};SwingUtilities.invokeAndWait(()->{ref.get().close();atClose[0]=callbacks.get();});release.countDown();assertThat(finished.await(3,TimeUnit.SECONDS)).isTrue();SwingUtilities.invokeAndWait(()->assertThat(callbacks).hasValue(atClose[0]));
  }
  @Test void selectedUnsupportedGameOrProviderIsBlockedRatherThanOmitted()throws Exception{
    var service=new AuthoredService();var ref=new AtomicReference<LibrarySetupCoordinator>();var draft=new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(Set.of("questie","vmangos"),Optional.empty(),false),"classic",new LibrarySetupDraft.Profile(Set.of("native"),Optional.empty(),false)));
    SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,event->{}));ref.get().review(draft);});await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.BLOCKED);
    SwingUtilities.invokeAndWait(()->{assertThat(ref.get().snapshot().review().blockers()).anyMatch(text->text.contains("vmangos")).anyMatch(text->text.contains("classic"));ref.get().close();});assertThat(service.reviewed).hasValue(0);assertThat(service.built).hasValue(0);
  }
  @Test void friendlyCoverageFailureDoesNotReturnAnActivatableCandidate()throws Exception{
    var ref=coordinator(new AuthoredService(){@Override public Result build(Review review,Mode mode,Control control)throws Exception{throw new JobFailure("Some item details are still missing.");}});await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);SwingUtilities.invokeAndWait(()->ref.get().build(draft()));await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.FAILED);SwingUtilities.invokeAndWait(()->{assertThat(ref.get().snapshot().message()).contains("Some item details are still missing");assertThat(ref.get().snapshot().result()).isNull();ref.get().close();});
  }
  @Test void replacementReviewRejectsStaleDraftAndNeverPublishesOldApproval()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new AtomicBoolean(true);
    var service=new AuthoredService(){@Override public Review review(LibrarySetupDraft draft,Control control)throws Exception{if(first.getAndSet(false)){entered.countDown();release.await(4,TimeUnit.SECONDS);}return super.review(draft,control);}};
    var ref=new AtomicReference<LibrarySetupCoordinator>();var approvals=new ArrayList<LibrarySetupDraft>();
    SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,event->{if(event.state()==LibrarySetupCoordinator.State.REVIEWED)approvals.add(event.draft());}));ref.get().review(draft());});assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
    var replacement=new LibrarySetupDraft("forever",Map.of("forever",new LibrarySetupDraft.Profile(Set.of("questie","native","wago"),Optional.empty(),false)));
    SwingUtilities.invokeAndWait(()->ref.get().review(replacement));release.countDown();await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);
    SwingUtilities.invokeAndWait(()->{assertThat(approvals).containsExactly(replacement);org.assertj.core.api.Assertions.assertThatThrownBy(()->ref.get().build(draft())).isInstanceOf(IllegalStateException.class);ref.get().close();});
  }
  @Test void closedBuildCannotPublishReadyEvenIfProviderReturnsCandidateLate()throws Exception{
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);var ready=new AtomicInteger();
    var service=new AuthoredService(){@Override public Result build(Review review,Mode mode,Control control)throws Exception{entered.countDown();try{release.await(4,TimeUnit.SECONDS);return new Result(Path.of("late-authored.sqlite"),"Late authored result",false);}finally{finished.countDown();}}};
    var ref=new AtomicReference<LibrarySetupCoordinator>();SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,event->{if(event.state()==LibrarySetupCoordinator.State.READY)ready.incrementAndGet();}));ref.get().review(draft());});await(()->ref.get().snapshot().state()==LibrarySetupCoordinator.State.REVIEWED);SwingUtilities.invokeAndWait(()->ref.get().build(draft()));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();SwingUtilities.invokeAndWait(()->ref.get().close());release.countDown();assertThat(finished.await(3,TimeUnit.SECONDS)).isTrue();SwingUtilities.invokeAndWait(()->assertThat(ready).hasValue(0));
  }
  private static AtomicReference<LibrarySetupCoordinator> coordinator(LibrarySetupService service)throws Exception{var ref=new AtomicReference<LibrarySetupCoordinator>();SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupCoordinator(service,LibrarySetupService.Mode.FIRST_RUN,event->{}));ref.get().review(draft());});return ref;}
  static void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(System.nanoTime()<end){var ready=new AtomicBoolean();SwingUtilities.invokeAndWait(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(15);}throw new AssertionError("Authored setup state did not settle");}
}
