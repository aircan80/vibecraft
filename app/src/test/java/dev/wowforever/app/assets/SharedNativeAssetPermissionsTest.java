package dev.wowforever.app.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SharedNativeAssetPermissionsTest {
    @TempDir Path cache;
    @AfterEach void reset(){AssetDownloadPermissions.resetForCatalogResolution();}
    void managed(boolean cdn,boolean wago,List<Path> games) {
        AssetDownloadPermissions.configureManaged(cache.resolve("library.sqlite"),new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,cdn,wago,games));
    }
    @Test void unresolvedCatalogIsCacheOnlyBeforeAnyTransportCall()throws Exception {
        AssetDownloadPermissions.resetForCatalogResolution();var f=SelectedNativeAssetClientTest.fixture(2);var requests=new ArrayList<URI>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,
                SelectedNativeAssetClientTest.transport(f,requests,false,f.asset())).withSharedPermissions()) {
            assertThrows(IOException.class,()->client.fetch(567490,()->false));
        }
        assertTrue(requests.isEmpty());
    }
    @Test void managedCdnConsentDoesNotGrantWagoFallback()throws Exception {
        managed(true,false,List.of());var f=SelectedNativeAssetClientTest.fixture(2);var requests=new ArrayList<URI>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,
                SelectedNativeAssetClientTest.transport(f,requests,true,f.asset())).withSharedPermissions()) {
            assertThrows(IOException.class,()->client.fetch(567490,()->false));
        }
        assertFalse(requests.stream().anyMatch(u->u.getHost().equals("wago.tools")));
    }
    @Test void wagoOnlyConsentUsesCachedRootChainAndNeverRequestsCdn()throws Exception {
        var f=SelectedNativeAssetClientTest.fixture(2);
        try(var seed=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,
                SelectedNativeAssetClientTest.transport(f,new ArrayList<>(),false,null))){seed.fetch(567490,()->false);}
        Files.delete(cache.resolve("content/"+f.assetKey()+".bin"));Files.delete(cache.resolve("encoded/"+f.assetEncodedKey()+".blte"));
        managed(false,true,List.of());var requests=new ArrayList<URI>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,
                SelectedNativeAssetClientTest.transport(f,requests,true,f.asset())).withSharedPermissions()) {
            assertEquals(f.assetKey(),client.fetch(567490,()->false).contentMd5());
        }
        assertEquals(1,requests.size());assertEquals("wago.tools",requests.getFirst().getHost());
    }
    @Test void sharedCacheOnlyStillReusesVerifiedAssetsWithNoNetwork()throws Exception {
        var f=SelectedNativeAssetClientTest.fixture(2);
        try(var seed=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,
                SelectedNativeAssetClientTest.transport(f,new ArrayList<>(),false,null))){seed.fetch(567490,()->false);}
        AssetDownloadPermissions.resetForCatalogResolution();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,
                (u,b,c)->{throw new AssertionError("cache-only hit must not request network");}).withSharedPermissions()) {
            assertEquals(f.assetKey(),client.fetch(567490,()->false).contentMd5());
        }
    }
    @Test void explicitDescriptorPolicyIsIndependentAndSelectedGameRootCannotBecomeSharedCache()throws Exception {
        AssetDownloadPermissions.resetForCatalogResolution();var f=SelectedNativeAssetClientTest.fixture(2);
        try(var explicit=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,
                SelectedNativeAssetClientTest.transport(f,new ArrayList<>(),false,null))){assertEquals(f.assetKey(),explicit.fetch(567490,()->false).contentMd5());}
        managed(true,true,List.of(cache));
        try(var shared=new NativeAssetClient(cache.resolve("unsafe"),f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,
                (u,b,c)->{throw new AssertionError("readonly game root must be rejected before network");}).withSharedPermissions()) {
            assertThrows(IOException.class,()->shared.fetch(567490,()->false));
        }
        assertFalse(Files.exists(cache.resolve("unsafe")));
    }
}
