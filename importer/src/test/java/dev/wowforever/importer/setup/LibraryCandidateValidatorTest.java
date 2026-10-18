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
