package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.assets.AssetDownloadPermissions;
import dev.wowforever.importer.setup.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LibraryAssetConsentTest {
  @TempDir Path temporary;
  @BeforeEach @AfterEach void reset() {AssetDownloadPermissions.resetForCatalogResolution();}
  @Test void activationRecordsPlanConsentWithoutChangingCurrentProcessThenStartupAdoptsIt() throws Exception {
    Path app=temporary.resolve("app"),game=Files.createDirectory(temporary.resolve("game"));
    var full=ActivationFixture.create(app.resolve("job"));Path open=temporary.resolve("already-open.sqlite");
    AssetDownloadPermissions.configureLegacy(open);
    var selected=new LibraryActivation(app,List.of(game),Optional.empty()).activateForNextStartup(full.candidate(),full.plan(),()->false);
    assertThat(AssetDownloadPermissions.activeDatabase()).contains(open);
    assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.LEGACY);
    assertThat(selected.assetPermissions().allowBlizzardCdn()).isFalse();
    assertThat(selected.assetPermissions().allowWagoExport()).isTrue();
    assertThat(selected.assetPermissions().readOnlyGameRoots()).containsExactly(game);
    var preferences=preferences();
    try {
      assertThat(ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,error->{throw new AssertionError();},DatabaseInstaller::validateExternal,false))
          .isEqualTo(selected.database());
      assertThat(AssetDownloadPermissions.current()).isEqualTo(selected.assetPermissions());
      assertThat(AssetDownloadPermissions.activeDatabase()).contains(selected.database());
    } finally {preferences.removeNode();}
  }
  @Test void compactActivationPersistsExtraExplicitCdnPermissionAndKeepsWarnings() throws Exception {
    Path app=temporary.resolve("app");var full=ActivationFixture.create(app.resolve("job"));
    var plan=withCdn(full.plan());var storage=new SetupStorage(app,List.of(),Optional.empty());Path runtime=app.resolve("runtime.sqlite");
    var receipt=new RuntimeLibraryExporter().export(full.candidate().database(),runtime,storage,Files.size(full.candidate().database()),full.candidate().sha256(),()->false);
    var selected=new LibraryActivation(app,List.of(),Optional.empty()).activateExportedForNextStartup(full.candidate(),runtime,receipt,plan,()->false);
    assertThat(selected.assetPermissions().allowBlizzardCdn()).isTrue();assertThat(selected.assetPermissions().allowWagoExport()).isTrue();
    assertThat(selected.validation().warnings()).isEqualTo(full.candidate().validation().warnings());
    assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
    assertThat(new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false)).contains(selected);
  }
  @Test void olderPointersAndCompatibilityConstructorNeverInferNetworkPermission() throws Exception {
    Path app=temporary.resolve("app");var full=ActivationFixture.create(app.resolve("job"));
    var selected=new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(full.candidate(),full.plan(),()->false);
    var legacyConstructor=new LibraryRegistry.Selection(selected.database(),selected.sha256(),selected.validation());
    assertThat(legacyConstructor.assetPermissions()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
    Path pointer=app.resolve("libraries/selection.properties");var p=read(pointer);p.setProperty("format","1");
    // Even injected asset-looking fields in a v1 receipt cannot grant consent.
    p.setProperty("active.assets.blizzard-cdn","true");write(pointer,p);
    var restored=new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false).orElseThrow();
    assertThat(restored.assetPermissions()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
  }
  @Test void mismatchedPermissionHashFallsBackWithPreviousPermissions() throws Exception {
    Path app=temporary.resolve("app");var full=ActivationFixture.create(app.resolve("job"));var activation=new LibraryActivation(app,List.of(),Optional.empty());
    var first=activation.activateForNextStartup(full.candidate(),full.plan(),()->false);
    activation.activateForNextStartup(full.candidate(),withCdn(full.plan()),()->false);
    Path pointer=app.resolve("libraries/selection.properties");var p=read(pointer);
    p.setProperty("active.assets.database-sha256","0".repeat(64));write(pointer,p);
    var selected=new LibraryRegistry(app,List.of(),Optional.empty()).resolve(()->false).orElseThrow();
    assertThat(selected).isEqualTo(first);assertThat(selected.assetPermissions().allowBlizzardCdn()).isFalse();
  }
  @Test void firstRunChooserAdoptsNewManagedConsentBeforeReturningWithoutLaunderingSavedPath() throws Exception {
    Path app=temporary.resolve("app");var full=ActivationFixture.create(app.resolve("job"));var preferences=preferences();
    String prior=temporary.resolve("missing-legacy.sqlite").toString();preferences.put(ExternalDatabaseSetup.PATH_KEY,prior);
    try {
      Path selected=ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,error->{
        assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
        assertThat(AssetDownloadPermissions.activeDatabase()).isEmpty();
        var result=new LibraryActivation(app,List.of(),Optional.empty()).activateForNextStartup(full.candidate(),full.plan(),()->false);
        assertThat(AssetDownloadPermissions.activeDatabase()).isEmpty();return result.database();
      },DatabaseInstaller::validateExternal,false);
      assertThat(AssetDownloadPermissions.activeDatabase()).contains(selected);
      assertThat(AssetDownloadPermissions.current().allowBlizzardCdn()).isFalse();
      assertThat(AssetDownloadPermissions.current().allowWagoExport()).isTrue();
      assertThat(preferences.get(ExternalDatabaseSetup.PATH_KEY,"")).isEqualTo(prior);
    } finally {preferences.removeNode();}
  }
  @Test void unregisteredManagedPathStaysCacheOnlyButExplicitExternalOverridePreservesLegacy() throws Exception {
    Path app=temporary.resolve("app");var full=ActivationFixture.create(app.resolve("job"));
    Path orphan=app.resolve("libraries/unregistered.sqlite");Files.createDirectories(orphan.getParent());Files.copy(full.candidate().database(),orphan);
    var preferences=preferences();String override=System.getProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
    try {
      ExternalDatabaseSetup.resolveWithManagedLibrary(app,preferences,error->orphan,DatabaseInstaller::validateExternal,false);
      assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
      System.setProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY,full.candidate().database().toString());
      assertThat(ExternalDatabaseSetup.resolve(null,app)).isEqualTo(full.candidate().database());
      assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.LEGACY);
      assertThat(AssetDownloadPermissions.activeDatabase()).contains(full.candidate().database());
    } finally {
      if(override==null)System.clearProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);else System.setProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY,override);
      preferences.removeNode();
    }
  }
  @Test void cancelledColdStartupNeverRetainsPreviousCatalogNetworkPermission() throws Exception {
    AssetDownloadPermissions.configureLegacy(temporary.resolve("old.sqlite"));var preferences=preferences();
    try {
      assertThatThrownBy(()->ExternalDatabaseSetup.resolveWithManagedLibrary(temporary.resolve("app"),preferences,error->null,DatabaseInstaller::validateExternal,false))
          .isInstanceOf(CancellationException.class);
      assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
      assertThat(AssetDownloadPermissions.activeDatabase()).isEmpty();
    } finally {preferences.removeNode();}
  }
  private static SetupPlan withCdn(SetupPlan plan) {
    return new SetupPlan(plan.sources(),Set.of(SetupPlan.Acquisition.WAGO_EXPORT,SetupPlan.Acquisition.BLIZZARD_CDN),plan.nativeIdentity(),plan.localGameRoots(),plan.sourcePins());
  }
  private static Preferences preferences() {return Preferences.userRoot().node("vibecraft-test/asset-consent-"+UUID.randomUUID());}
  private static Properties read(Path path) throws Exception {var p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}return p;}
  private static void write(Path path,Properties p) throws Exception {try(var out=Files.newOutputStream(path)){p.store(out,"synthetic permission mutation");}}
}
