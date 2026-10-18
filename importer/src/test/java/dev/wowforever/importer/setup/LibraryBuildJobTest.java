package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryBuildJobTest {
  @TempDir Path temporary;
  private SetupPlan.JobContext job(SetupPlan plan)throws Exception {
    Path active=Files.writeString(temporary.resolve("active.sqlite"),"last working library");
    return plan.prepareJob(temporary.resolve("app"),Optional.of(active),"job",List.of());
  }
  private void unchanged(SetupPlan.JobContext job)throws Exception {
    assertThat(Files.readString(temporary.resolve("active.sqlite"))).isEqualTo("last working library");
    assertThat(job.paths().directory().resolve("candidate.sqlite.part")).doesNotExist();
  }
  @Test void syntheticProjectionBoundaryProducesValidatedReceiptWithoutActivatingAnything()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);
    List<LibraryBuildJob.Stage> stages=new ArrayList<>();
    var engine=new LibraryBuildJob(LibraryJobFixtures::syntheticProjection,new LibraryCandidateValidator());
    var result=engine.build(plan,job,inputs,()->false,p->stages.add(p.stage()));
    assertThat(result.database()).isEqualTo(job.paths().candidateDatabase()).exists();
    assertThat(result.sha256()).matches("[a-f0-9]{64}");assertThat(result.validation().buildId()).isEqualTo(LibraryJobFixtures.BUILD_ID);
    assertThat(result.validation().entityCounts().get("item")).isEqualTo(1L);
    assertThat(stages).containsSubsequence(LibraryBuildJob.Stage.CAPTURING,LibraryBuildJob.Stage.PROJECTING,LibraryBuildJob.Stage.VALIDATING,LibraryBuildJob.Stage.READY);
    unchanged(job);
  }
  @Test void actualProductionProjectionRefusesIncompleteNativeTablesInsteadOfFabricatingSuccess()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);
    assertThatThrownBy(()->new LibraryBuildJob().build(plan,job,inputs,()->false,p->{})).isInstanceOf(IllegalStateException.class).hasMessageContaining("client table Item");
    assertThat(job.paths().candidateDatabase()).doesNotExist();assertThat(job.paths().archiveDatabase()).exists();unchanged(job);
  }
  @Test void cancellationBeforeCaptureAndBetweenCaptureAndProjectionNeverPublishesCandidate()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);
    var engine=new LibraryBuildJob(LibraryJobFixtures::syntheticProjection,new LibraryCandidateValidator());
    assertThatThrownBy(()->engine.build(plan,job,inputs,()->true,p->{})).isInstanceOf(CancellationException.class);
    assertThat(job.paths().archiveDatabase()).doesNotExist();
    AtomicBoolean cancelled=new AtomicBoolean();
    assertThatThrownBy(()->engine.build(plan,job,inputs,cancelled::get,p->{if(p.stage()==LibraryBuildJob.Stage.PROJECTING)cancelled.set(true);})).isInstanceOf(CancellationException.class);
    assertThat(job.paths().archiveDatabase()).exists();assertThat(job.paths().candidateDatabase()).doesNotExist();unchanged(job);
  }
  @Test void cancellationRequestedDuringExistingProjectionIsHonoredAtItsBoundary()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);AtomicBoolean cancelled=new AtomicBoolean();
    var engine=new LibraryBuildJob((archive,ingestion,report)->{int code=LibraryJobFixtures.syntheticProjection(archive,ingestion,report);cancelled.set(true);return code;},new LibraryCandidateValidator());
    assertThatThrownBy(()->engine.build(plan,job,inputs,cancelled::get,p->{})).isInstanceOf(CancellationException.class);
    assertThat(job.paths().candidateDatabase()).doesNotExist();unchanged(job);
  }
  @Test void cancellationDuringCopyDeletesOnlyThisRunsPartialCandidate()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);AtomicBoolean cancelled=new AtomicBoolean();
    var engine=new LibraryBuildJob(LibraryJobFixtures::syntheticProjection,new LibraryCandidateValidator());
    assertThatThrownBy(()->engine.build(plan,job,inputs,cancelled::get,p->{if(p.stage()==LibraryBuildJob.Stage.COPYING&&p.completedBytes()>0)cancelled.set(true);})).isInstanceOf(CancellationException.class);
    assertThat(job.paths().candidateDatabase()).doesNotExist();unchanged(job);
  }
  @Test void badCandidateIdentityAndExistingCandidateBothPreserveLastWorkingLibrary()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=job(plan);var inputs=LibraryJobFixtures.inputs(temporary);
    var engine=new LibraryBuildJob((archive,ingestion,report)->{LibraryJobFixtures.syntheticProjection(archive,ingestion,report);
      LibraryJobFixtures.execute(archive,"UPDATE meta SET value='wow_classic_era' WHERE key='client_product'");return 0;},new LibraryCandidateValidator());
    assertThatIOException().isThrownBy(()->engine.build(plan,job,inputs,()->false,p->{}));
    assertThat(job.paths().candidateDatabase()).doesNotExist();unchanged(job);
    Files.writeString(job.paths().candidateDatabase(),"do not overwrite");
    assertThatIOException().isThrownBy(()->new LibraryBuildJob().build(plan,job,inputs,()->false,p->{}));
    assertThat(Files.readString(job.paths().candidateDatabase())).isEqualTo("do not overwrite");unchanged(job);
  }
}
