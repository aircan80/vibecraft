package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.setup.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeLibraryActivationTest {
  @org.junit.jupiter.api.AfterEach void resetAssetContext() {dev.wowforever.app.assets.AssetDownloadPermissions.resetForCatalogResolution();}
  @TempDir Path temporary;
  record Exported(ActivationFixture.Built full,Path runtime,RuntimeLibraryExporter.Receipt receipt,SetupStorage storage) {}

  private Exported export(Path app) throws Exception {
    var full=ActivationFixture.create(app.resolve("staged-job"));
    Path runtime=app.resolve("runtime.sqlite");var storage=new SetupStorage(app,List.of(),Optional.empty());
    var receipt=new RuntimeLibraryExporter().export(full.candidate().database(),runtime,storage,
        Files.size(full.candidate().database()),full.candidate().sha256(),()->false);
    return new Exported(full,runtime,receipt,storage);
  }

  @Test void actualCompactExportActivatesAndStartupKeepsSourceIdentityAndWarnings() throws Exception {
    Path app=temporary.resolve("app");var exported=export(app);var full=exported.full();
    byte[] source=Files.readAllBytes(full.candidate().database());
    var selected=new LibraryActivation(app,List.of(),Optional.empty()).activateExportedForNextStartup(
        full.candidate(),exported.runtime(),exported.receipt(),full.plan(),()->false);
    assertThat(selected.sha256()).isEqualTo(exported.receipt().outputSha256()).isNotEqualTo(full.candidate().sha256());
    assertThat(selected.validation()).isEqualTo(full.candidate().validation());
    assertThat(selected.validation().warnings()).anyMatch(value->value.contains("additive foundation"));
    assertThat(Files.readAllBytes(full.candidate().database())).isEqualTo(source);
    assertThat(Files.readAllBytes(selected.database())).isEqualTo(Files.readAllBytes(exported.runtime()));
    try(var db=Database.open(selected.database())) {assertThat(db.meta()).containsEntry("release_dataset_profile","runtime-v1");}
    assertThat(new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false)).contains(selected);
    var preferences=java.util.prefs.Preferences.userRoot().node("vibecraft-test/runtime-activation-"+UUID.randomUUID());
    try {
      assertThat(ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,
          error->{throw new AssertionError("Verified runtime should bypass chooser");},DatabaseInstaller::validateExternal,false))
          .isEqualTo(selected.database());
    } finally {preferences.removeNode();}
  }

  @Test void forgedReceiptHashesSizesBuildOmissionsAndRetainedDigestsCannotPublish() throws Exception {
    Path app=temporary.resolve("app");var exported=export(app);var r=exported.receipt();
    var changed=new TreeMap<>(r.retainedTables());
    var item=changed.get("item");changed.put("item",new RuntimeLibraryExporter.Digest(item.rows()+1,item.sha256MultisetSum()));
    List<RuntimeLibraryExporter.Receipt> forged=List.of(
        receipt(r.sourceBytes()+1,r.sourceSha256(),r.outputBytes(),r.outputSha256(),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),"0".repeat(64),r.outputBytes(),r.outputSha256(),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes()+1,r.outputSha256(),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes(),"0".repeat(64),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes(),r.outputSha256(),"b".repeat(64),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes(),r.outputSha256(),r.buildId(),r.omittedRawRecords()+1,r.omittedArchiveBytes(),r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes(),r.outputSha256(),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes()+1,r.retainedTables()),
        receipt(r.sourceBytes(),r.sourceSha256(),r.outputBytes(),r.outputSha256(),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),changed));
    var activation=new LibraryActivation(app,List.of(),Optional.empty());
    for(var bad:forged) {
      assertThatIOException().isThrownBy(()->activation.activateExportedForNextStartup(
          exported.full().candidate(),exported.runtime(),bad,exported.full().plan(),()->false));
      assertThat(app.resolve("libraries/selection.properties")).doesNotExist();
    }
  }

  @Test void alteredRuntimeWithFreshCallerSuppliedHashStillFailsIndependentRowVerification() throws Exception {
    Path app=temporary.resolve("app");var exported=export(app);var r=exported.receipt();
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+exported.runtime());var s=c.createStatement()) {
      s.executeUpdate("UPDATE item SET name='Forged runtime item' WHERE id=1");
    }
    var forged=receipt(r.sourceBytes(),r.sourceSha256(),Files.size(exported.runtime()),
        RuntimeLibraryExporter.hashFile(exported.runtime(),()->false),r.buildId(),r.omittedRawRecords(),r.omittedArchiveBytes(),r.retainedTables());
    assertThatIOException().isThrownBy(()->new LibraryActivation(app,List.of(),Optional.empty()).activateExportedForNextStartup(
        exported.full().candidate(),exported.runtime(),forged,exported.full().plan(),()->false));
    assertThat(app.resolve("libraries/selection.properties")).doesNotExist();
  }

  @Test void changedFullSourceCannotBeReplacedByItsOldExportReceipt() throws Exception {
    Path app=temporary.resolve("app");var exported=export(app);var full=exported.full();
    var activation=new LibraryActivation(app,List.of(),Optional.empty());
    var previous=activation.activateForNextStartup(full.candidate(),full.plan(),()->false);
    byte[] pointer=Files.readAllBytes(app.resolve("libraries/selection.properties"));
    Files.writeString(full.candidate().database(),"source changed after export");
    assertThatIOException().isThrownBy(()->activation.activateExportedForNextStartup(
        full.candidate(),exported.runtime(),exported.receipt(),full.plan(),()->false));
    assertThat(Files.readAllBytes(app.resolve("libraries/selection.properties"))).isEqualTo(pointer);
    assertThat(new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false)).contains(previous);
  }

  @Test void lateCopyCancellationAndAtomicPublicationFailureKeepPreviousDatabase() throws Exception {
    Path app=temporary.resolve("app");var exported=export(app);var full=exported.full();
    var previous=new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(full.candidate(),full.plan(),()->false);
    byte[] pointer=Files.readAllBytes(app.resolve("libraries/selection.properties"));
    var failing=new LibraryRegistry(app,List.of(),Optional.of(previous.database()),
        (from,to)->{throw new AtomicMoveNotSupportedException(from.toString(),to.toString(),"synthetic failure");});
    assertThatIOException().isThrownBy(()->LibraryActivation.activateExported(full.candidate(),exported.runtime(),exported.receipt(),
        full.plan(),()->false,failing,exported.storage()));
    java.util.function.BooleanSupplier duringCopy=()-> {
      try(var paths=Files.list(app.resolve("libraries/versions").toRealPath())) {
        for(Path path:paths.toList())
          if(!Files.isSameFile(path,previous.database().getParent())&&Files.exists(path.resolve(DatabaseInstaller.FILE_NAME)))return true;
        return false;
      } catch(IOException failure) {throw new java.io.UncheckedIOException(failure);}
    };
    assertThatThrownBy(()->new LibraryActivation(app,List.of(),Optional.of(previous.database())).activateExportedForNextStartup(
        full.candidate(),exported.runtime(),exported.receipt(),full.plan(),duringCopy)).isInstanceOf(CancellationException.class);
    assertThat(Files.readAllBytes(app.resolve("libraries/selection.properties"))).isEqualTo(pointer);
    try(var paths=Files.list(app.resolve("libraries/versions").toRealPath())) {
      var remaining=paths.toList();assertThat(remaining).hasSize(1);
      assertThat(Files.isSameFile(remaining.getFirst(),previous.database().getParent())).isTrue();
    }
  }

  @Test void exportedInputsMustStayInsideActualApplicationStorageAndOffTheEdt() throws Exception {
    var exported=export(temporary.resolve("different-storage"));Path app=temporary.resolve("app");
    assertThatIOException().isThrownBy(()->new LibraryActivation(app,List.of(),Optional.empty()).activateExportedForNextStartup(
        exported.full().candidate(),exported.runtime(),exported.receipt(),exported.full().plan(),()->false));
    var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
    javax.swing.SwingUtilities.invokeAndWait(()-> {
      try {new LibraryActivation(app,List.of(),Optional.empty()).activateExportedForNextStartup(
          exported.full().candidate(),exported.runtime(),exported.receipt(),exported.full().plan(),()->false);}
      catch(Throwable failure) {error.set(failure);}
    });
    assertThat(error.get()).isInstanceOf(IllegalStateException.class);
  }

  private static RuntimeLibraryExporter.Receipt receipt(long sourceBytes,String sourceSha,
      long outputBytes,String outputSha,String build,long raw,long archive,Map<String,RuntimeLibraryExporter.Digest> retained) {
    return new RuntimeLibraryExporter.Receipt(sourceBytes,sourceSha,outputBytes,outputSha,build,raw,archive,retained);
  }
}
