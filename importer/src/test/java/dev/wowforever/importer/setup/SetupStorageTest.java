package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupStorageTest {
  @TempDir Path temporary;
  @Test void existingAndFutureDestinationsInsideEveryGameRootAreRejectedWithoutWrites() throws Exception {
    Path game1=Files.createDirectory(temporary.resolve("WoW"));
    Path game2=Files.createDirectory(temporary.resolve("OtherWoW"));
    assertThatIOException().isThrownBy(() -> new SetupStorage(game1.resolve("future/cache"),List.of(game1,game2),Optional.empty()));
    var storage=new SetupStorage(temporary,List.of(game1,game2),Optional.empty());
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(game2.resolve("future/library.sqlite")));
    assertThat(Files.exists(game1.resolve("future"))).isFalse();assertThat(Files.exists(game2.resolve("future"))).isFalse();
  }
  @Test void symlinkAliasIntoGameFolderCannotBecomeWritableStorage() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game"));
    Path alias=temporary.resolve("alias");Files.createSymbolicLink(alias,game);
    assertThatIOException().isThrownBy(() -> new SetupStorage(alias.resolve("future"),List.of(game),Optional.empty()));
    var storage=new SetupStorage(temporary,List.of(alias),Optional.empty());
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(game.resolve("future")));
    assertThat(Files.exists(game.resolve("future"))).isFalse();
  }
  @Test void activeDatabaseAliasesAndSidecarsAreProtected() throws Exception {
    Path active=Files.writeString(temporary.resolve("active.sqlite"),"existing");
    var storage=new SetupStorage(temporary,List.of(),Optional.of(active));
    Path link=temporary.resolve("active-alias.sqlite");Files.createSymbolicLink(link,active);
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(link));
    Path hardlink=temporary.resolve("hardlink.sqlite");Files.createLink(hardlink,active);
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(hardlink));
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(temporary.resolve("active.sqlite-wal")));
    assertThat(Files.readString(active)).isEqualTo("existing");
  }
  @Test void freshStagingNeverOverwritesAnExistingJobAndRejectsEscape() throws Exception {
    var storage=new SetupStorage(temporary.resolve("app"),List.of(),Optional.empty());
    var job=storage.createJob("one");storage.verifyJob(job);
    assertThat(job.archiveDatabase().getParent()).isEqualTo(job.directory());
    assertThat(job.candidateDatabase()).doesNotExist();
    assertThatIOException().isThrownBy(() -> storage.createJob("one"));
    assertThatIOException().isThrownBy(() -> storage.createJob("../escape"));
    assertThatIOException().isThrownBy(() -> storage.verifyDestination(temporary.resolve("outside")));
  }
  @Test void stagedArchiveLockAliasCannotWriteIntoGameInstallation() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game"));
    Path source=Files.writeString(game.resolve("source"),"unchanged");
    var storage=new SetupStorage(temporary.resolve("app"),List.of(game),Optional.empty());
    var job=storage.createJob("job");
    Files.createSymbolicLink(job.archiveDatabase().resolveSibling("source-archive.sqlite.lock"),source);
    assertThatIOException().isThrownBy(() -> storage.verifyJob(job));
    assertThat(Files.readString(source)).isEqualTo("unchanged");
  }
  @Test void planEstablishesGameBoundaryBeforeCreatingJobDirectories() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game"));
    var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);
    var plan=new SetupPlan(sources,Set.of(SetupPlan.Acquisition.LOCAL_INSTALLATION),
        Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),List.of(game),SetupPlan.currentPins(sources));
    assertThatIOException().isThrownBy(() -> plan.prepareJob(game.resolve("app"),Optional.empty(),"job",List.of()));
    assertThat(Files.exists(game.resolve("app"))).isFalse();
  }
}
