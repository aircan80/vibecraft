package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.setup.ActivationFixture;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryActivationTest {
  @org.junit.jupiter.api.AfterEach void resetAssetContext() {dev.wowforever.app.assets.AssetDownloadPermissions.resetForCatalogResolution();}
  @TempDir Path temporary;
  @Test void activationCopiesImmutableVersionAndStartupConsumesItWithWarnings() throws Exception {
    var built=ActivationFixture.create(temporary.resolve("candidate"));
    byte[] original=Files.readAllBytes(built.candidate().database());
    Path app=temporary.resolve("application");
    var selected=new LibraryActivation(app,List.of(),Optional.empty())
        .activateForNextStartup(built.candidate(),built.plan(),()->false);
    assertThat(selected.database()).startsWith(app.resolve("libraries/versions"));
    assertThat(Files.readAllBytes(built.candidate().database())).isEqualTo(original);
    assertThat(Files.readAllBytes(selected.database())).isEqualTo(original);
    var stored=new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false).orElseThrow();
    assertThat(stored).isEqualTo(selected);
    assertThat(stored.validation().warnings()).anyMatch(warning->warning.contains("additive foundation"));
    var preferences=java.util.prefs.Preferences.userRoot().node("vibecraft-test/activation-"+UUID.randomUUID());
    try {
      Path consumed=ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,
          error->{throw new AssertionError("Managed library should bypass chooser");},DatabaseInstaller::validateExternal,false);
      assertThat(consumed).isEqualTo(selected.database());
      assertThat(preferences.get(ExternalDatabaseSetup.PATH_KEY,"")).isEmpty();
    } finally {preferences.removeNode();}
  }

  @Test void subsequentActivationNeverReplacesOpenDatabaseAndRetainsLastGood() throws Exception {
    var one=ActivationFixture.create(temporary.resolve("first"));
    var two=ActivationFixture.create(temporary.resolve("second"));
    Path app=temporary.resolve("app");
    var first=new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(one.candidate(),one.plan(),()->false);
    byte[] before=Files.readAllBytes(first.database());
    try(var open=Database.open(first.database())) {
      var next=new LibraryActivation(app,List.of(),Optional.of(first.database())).activateForNextStartup(two.candidate(),two.plan(),()->false);
      assertThat(next.database()).isNotEqualTo(first.database());
      assertThat(open.meta().get("build_id")).isEqualTo(first.validation().buildId());
      assertThat(Files.readAllBytes(first.database())).isEqualTo(before);
      Files.writeString(next.database(),"corrupt activation");
      assertThat(new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false).orElseThrow()).isEqualTo(first);
    }
  }

  @Test void changedReceiptCancellationAndAtomicFailurePreservePreviousPointer() throws Exception {
    var one=ActivationFixture.create(temporary.resolve("first"));
    var two=ActivationFixture.create(temporary.resolve("second"));Path app=temporary.resolve("app");
    var activation=new LibraryActivation(app,List.of(),Optional.empty());
    var first=activation.activateForNextStartup(one.candidate(),one.plan(),()->false);
    Path pointer=app.resolve("libraries/selection.properties");byte[] before=Files.readAllBytes(pointer);
    assertThatThrownBy(()->activation.activateForNextStartup(two.candidate(),two.plan(),()->true)).isInstanceOf(CancellationException.class);
    assertThat(Files.readAllBytes(pointer)).isEqualTo(before);
    var failing=new LibraryRegistry(app,List.of(),Optional.empty(),(from,to)->{throw new AtomicMoveNotSupportedException(from.toString(),to.toString(),"synthetic");});
    assertThatIOException().isThrownBy(()->LibraryActivation.activate(two.candidate(),two.plan(),()->false,failing));
    assertThat(Files.readAllBytes(pointer)).isEqualTo(before);
    try(var versions=Files.list(app.resolve("libraries/versions").toRealPath())) {
      var remaining=versions.toList();assertThat(remaining).hasSize(1);
      assertThat(Files.isSameFile(remaining.getFirst(),first.database().getParent())).isTrue();
    }
    Files.writeString(two.candidate().database(),"changed after receipt");
    assertThatIOException().isThrownBy(()->activation.activateForNextStartup(two.candidate(),two.plan(),()->false));
    assertThat(Files.readAllBytes(pointer)).isEqualTo(before);
  }

  @Test void cancellationAfterCreatingTheCopyAndForgedWarningsCannotPublish() throws Exception {
    var built=ActivationFixture.create(temporary.resolve("candidate"));Path app=temporary.resolve("app");
    var receipt=built.candidate().validation();
    var forged=new dev.wowforever.importer.setup.LibraryBuildJob.Candidate(built.candidate().database(),
        built.candidate().sourceArchive(),built.candidate().jobLog(),built.candidate().sha256(),
        new dev.wowforever.importer.setup.LibraryCandidateValidator.Validated(receipt.buildId(),receipt.nativeIdentity(),
            receipt.ingestionId(),receipt.entityCounts(),List.of()));
    var activation=new LibraryActivation(app,List.of(),Optional.empty());
    assertThatIOException().isThrownBy(()->activation.activateForNextStartup(forged,built.plan(),()->false));
    java.util.function.BooleanSupplier duringCopy=()-> {
      Path versions=app.resolve("libraries/versions");
      if(!Files.isDirectory(versions))return false;
      try(var paths=Files.list(versions)) {return paths.anyMatch(path->Files.exists(path.resolve(DatabaseInstaller.FILE_NAME)));}
      catch(IOException failure) {throw new java.io.UncheckedIOException(failure);}
    };
    assertThatThrownBy(()->activation.activateForNextStartup(built.candidate(),built.plan(),duringCopy)).isInstanceOf(CancellationException.class);
    assertThat(app.resolve("libraries/selection.properties")).doesNotExist();
    try(var versions=Files.list(app.resolve("libraries/versions").toRealPath())) {assertThat(versions.toList()).isEmpty();}
  }

  @Test void explicitDatabaseOverrideStillWinsOverManagedSelection() throws Exception {
    var built=ActivationFixture.create(temporary.resolve("candidate"));Path app=temporary.resolve("app");
    new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(built.candidate(),built.plan(),()->false);
    String original=System.getProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
    try {
      System.setProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY,built.candidate().database().toString());
      assertThat(ExternalDatabaseSetup.resolve(null,app)).isEqualTo(built.candidate().database());
    } finally {
      if(original==null)System.clearProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
      else System.setProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY,original);
    }
  }

  @Test void storageProtectsGameFoldersAndReadRejectsSymlinkEscape() throws Exception {
    var built=ActivationFixture.create(temporary.resolve("candidate"));
    Path game=Files.createDirectory(temporary.resolve("wow"));
    assertThatIOException().isThrownBy(()->new LibraryActivation(game,List.of(game),Optional.empty())
        .activateForNextStartup(built.candidate(),built.plan(),()->false));
    Path app=temporary.resolve("app");
    var selected=new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(built.candidate(),built.plan(),()->false);
    Path escaped=Files.copy(selected.database(),game.resolve("external.sqlite"));
    Files.delete(selected.database());Files.createSymbolicLink(selected.database(),escaped);
    assertThatIOException().isThrownBy(()->new LibraryRegistry(app,List.of(game),Optional.empty()).resolve(()->false));
    assertThatIOException().isThrownBy(()->new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false));
  }

  @Test void malformedPointerPreservesExistingExternalFallbackAndExplainsFirstRun() throws Exception {
    Path app=temporary.resolve("app");Files.createDirectories(app.resolve("libraries"));
    Files.writeString(app.resolve("libraries/selection.properties"),"format=1\nactive.path=../../WoW/game.sqlite\n");
    var preferences=java.util.prefs.Preferences.userRoot().node("vibecraft-test/activation-"+UUID.randomUUID());
    try {
      Path external=temporary.resolve("existing.sqlite");preferences.put(ExternalDatabaseSetup.PATH_KEY,external.toString());
      assertThat(ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,error->{throw new AssertionError();},path->path,false)).isEqualTo(external);
      preferences.remove(ExternalDatabaseSetup.PATH_KEY);
      assertThat(ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,error->{assertThat(error).contains("managed library");return external;},path->path,false)).isEqualTo(external);
    } finally {preferences.removeNode();}
  }

  @Test void validationAndActivationRefuseTheEdt() throws Exception {
    var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
    javax.swing.SwingUtilities.invokeAndWait(()-> {
      try {new LibraryRegistry(temporary,List.of(),Optional.empty()).resolve(()->false);}
      catch(Throwable failure) {error.set(failure);}
    });
    assertThat(error.get()).isInstanceOf(IllegalStateException.class);
  }
}
