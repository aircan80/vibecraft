package dev.wowforever.app.assets;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AssetDownloadPermissionsTest {
  @TempDir Path temporary;
  @BeforeEach @AfterEach void reset() {AssetDownloadPermissions.resetForCatalogResolution();}
  @Test void coldContextIsCacheOnlyAndMemoryOnlyActiveGuardWorksOnEdt() throws Exception {
    assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.CACHE_ONLY);
    assertThat(AssetDownloadPermissions.activeDatabase()).isEmpty();
    Path database=temporary.resolve("not-created.sqlite");
    var policy=new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,false,true,List.of());
    javax.swing.SwingUtilities.invokeAndWait(()-> {
      AssetDownloadPermissions.configureManaged(database,policy);
      assertThat(AssetDownloadPermissions.activeDatabase()).contains(database);
      assertThat(AssetDownloadPermissions.current()).isEqualTo(policy);
    });
    assertThat(database).doesNotExist();
    AssetDownloadPermissions.resetForCatalogResolution();
    assertThat(AssetDownloadPermissions.activeDatabase()).isEmpty();
  }
  @Test void providerFlagsAreIndependentAndRootsAreImmutable() {
    var cdn=new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,true,false,List.of(temporary));
    var wago=new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,false,true,List.of());
    assertThat(cdn.allowBlizzardCdn()).isTrue();assertThat(cdn.allowWagoExport()).isFalse();
    assertThat(wago.allowBlizzardCdn()).isFalse();assertThat(wago.allowWagoExport()).isTrue();
    assertThatThrownBy(()->cdn.readOnlyGameRoots().add(temporary)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(()->new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.CACHE_ONLY,true,false,List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->AssetDownloadPermissions.configureManaged(temporary,AssetDownloadPermissions.LEGACY))
        .isInstanceOf(IllegalArgumentException.class);
  }
  @Test void cacheWritesStayWithinOwnedStorageAndOutsideProtectedAliases() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game")),cache=Files.createDirectory(temporary.resolve("cache"));
    AssetDownloadPermissions.configureManaged(temporary.resolve("catalog.sqlite"),
        new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,false,true,List.of(game)));
    assertThat(AssetDownloadPermissions.checkCacheWrite(cache,cache.resolve("content/new.bin"))).isEqualTo(cache.resolve("content/new.bin"));
    assertThatIOException().isThrownBy(()->AssetDownloadPermissions.checkCacheWrite(cache,temporary.resolve("outside.bin")));
    assertThatIOException().isThrownBy(()->AssetDownloadPermissions.checkCacheWrite(game,game.resolve("cache/new.bin")));
    Files.createSymbolicLink(cache.resolve("alias"),game);
    assertThatIOException().isThrownBy(()->AssetDownloadPermissions.checkCacheWrite(cache,cache.resolve("alias/new.bin")));
    assertThat(game.resolve("cache")).doesNotExist();
  }
  @Test void legacyKeepsProvidersButRecognizableGameFoldersRemainReadOnly() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game"));Files.writeString(game.resolve(".build.info"),"fixture");
    AssetDownloadPermissions.configureLegacy(temporary.resolve("old.sqlite"));
    assertThat(AssetDownloadPermissions.current()).isEqualTo(AssetDownloadPermissions.LEGACY);
    assertThatIOException().isThrownBy(()->AssetDownloadPermissions.checkCacheWrite(game,game.resolve("new.bin")));
  }
}
