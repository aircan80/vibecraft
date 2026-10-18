package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryCandidateValidatorTest {
  @TempDir Path temporary;
  @Test void syntheticCandidateValidationIsReadOnlyAndCountsUsableCoreEntities()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    String ingestion=LibraryJobFixtures.captureSynthetic(plan,job,LibraryJobFixtures.inputs(temporary));
    byte[] before=Files.readAllBytes(job.paths().archiveDatabase());
    var result=new LibraryCandidateValidator().validate(job.paths().archiveDatabase(),plan,ingestion,()->false);
    assertThat(result.entityCounts()).containsEntry("item",1L).containsEntry("quest",1L);
    assertThat(Files.readAllBytes(job.paths().archiveDatabase())).isEqualTo(before);
  }
  @Test void wrongLocaleUnselectedSourceAndEmptyRequiredCoverageAreRejected()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    String ingestion=LibraryJobFixtures.captureSynthetic(plan,job,LibraryJobFixtures.inputs(temporary));Path file=job.paths().archiveDatabase();
    LibraryJobFixtures.execute(file,"UPDATE source_archive_snapshot SET locale='deDE' WHERE source='blizzard-client'");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
    LibraryJobFixtures.execute(file,"UPDATE source_archive_snapshot SET locale='enUS' WHERE source='blizzard-client'");
    LibraryJobFixtures.execute(file,"INSERT INTO meta VALUES ('client_locale','deDE')");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
    LibraryJobFixtures.execute(file,"DELETE FROM meta WHERE key='client_locale'");
    LibraryJobFixtures.execute(file,"INSERT INTO instance(id,key,name,source) VALUES (1,'synthetic','Unexpected','atlasloot')");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
    LibraryJobFixtures.execute(file,"DELETE FROM instance");LibraryJobFixtures.execute(file,"DELETE FROM item");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
  }
  @Test void hotfixSelectionNeverAcceptsMissingProofOrMetadataOnlyClaims()throws Exception {
    var base=LibraryJobFixtures.plan();var job=base.prepareJob(temporary.resolve("app"),Optional.empty(),"hotfix-proof",List.of());
    String ingestion=LibraryJobFixtures.captureSynthetic(base,job,LibraryJobFixtures.inputs(temporary));Path file=job.paths().archiveDatabase();
    LibraryJobFixtures.execute(file,"INSERT INTO meta VALUES ('client_hotfix_sha256','"+dev.wowforever.importer.acquisition.PinnedItemSparseOverlayMaterializer.SNAPSHOT_SHA+"')");
    LibraryJobFixtures.execute(file,"INSERT INTO meta VALUES ('client_hotfix_header_build','70450')");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,base,ingestion,()->false)).withMessageContaining("Unselected hotfix");
    var selected=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA,SetupPlan.Source.RAIDBOTS_HOTFIX);
    var hotfix=new SetupPlan(selected,base.acquisitions(),base.nativeIdentity(),List.of(),SetupPlan.currentPins(selected));
    byte[] before=Files.readAllBytes(file);
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,hotfix,ingestion,()->false));
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
    LibraryJobFixtures.execute(file,"INSERT INTO meta VALUES ('release_dataset_profile','runtime-v1')");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,hotfix,ingestion,()->false)).withMessageContaining("Compacted runtime");
    LibraryJobFixtures.execute(file,"DELETE FROM meta WHERE key='release_dataset_profile'");
    LibraryJobFixtures.execute(file,"UPDATE item SET client_source='era'");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,hotfix,ingestion,()->false)).withMessageContaining("Era");
  }
  @Test void declaredForeignKeyFailureAndFailedProjectionCoverageCannotBeAccepted()throws Exception {
    var plan=LibraryJobFixtures.plan();var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    String ingestion=LibraryJobFixtures.captureSynthetic(plan,job,LibraryJobFixtures.inputs(temporary));Path file=job.paths().archiveDatabase();
    LibraryJobFixtures.execute(file,"UPDATE source_projection SET validation_summary='UI skips=0; unsupported fields=0; client coverage=50%; client data skipped=0'");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
    LibraryJobFixtures.execute(file,"UPDATE source_projection SET validation_summary='UI skips=0; unsupported fields=0; client coverage=100%; client data skipped=0'");
    LibraryJobFixtures.execute(file,"CREATE TABLE broken(parent_id REFERENCES npc(id))");LibraryJobFixtures.execute(file,"INSERT INTO broken VALUES (999)");
    assertThatIOException().isThrownBy(()->new LibraryCandidateValidator().validate(file,plan,ingestion,()->false));
  }
}
