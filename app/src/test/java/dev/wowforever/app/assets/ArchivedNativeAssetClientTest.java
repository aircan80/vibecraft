package dev.wowforever.app.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivedNativeAssetClientTest {
    @TempDir Path cache;
    record Fixture(SelectedNativeAssetClientTest.Fixture core,BlizzardBuildDescriptor descriptor,
                   Map<String,byte[]> files,String archive,byte[] encoded) {}
    Fixture fixture(boolean second)throws Exception {
        var core=SelectedNativeAssetClientTest.fixture(2);byte[] encoded=SelectedNativeAssetClientTest.blte(core.asset());
        byte[] index=CdnArchiveIndexTest.index(core.assetEncodedKey(),encoded.length,123);
        String archive=CdnArchiveIndexTest.identity(index),base="https://us.cdn.blizzard.com/tpr/wow/";
        var files=new HashMap<>(core.transport());files.remove(base+"data/"+SelectedNativeAssetClientTest.keyPath(core.assetEncodedKey()));
        files.put(base+"data/"+SelectedNativeAssetClientTest.keyPath(archive)+".index",index);
        String extra="";
        if(second){byte[] absent=CdnArchiveIndexTest.index("0".repeat(32),1,0);String first=CdnArchiveIndexTest.identity(absent);extra=first+" ";files.put(base+"data/"+SelectedNativeAssetClientTest.keyPath(first)+".index",absent);}
        byte[] cdn=("archives = "+extra+archive+"\narchives-index-size = 1\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String cdnKey=SelectedNativeAssetClientTest.hash("MD5",cdn);
        files.put(base+"config/"+SelectedNativeAssetClientTest.keyPath(cdnKey),cdn);
        String versions="Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|BuildId!DEC:4|VersionsName!String:0\nus|"+core.descriptor().buildConfigMd5()+"|"+cdnKey+"|70451|"+SelectedNativeAssetClientTest.BUILD+"\n";
        String cdns="Name!STRING:0|Path!STRING:0|Hosts!STRING:0\nus|tpr/wow|us.cdn.blizzard.com\n";
        var builds=BlizzardBuildDiscovery.parseBuilds(SelectedNativeAssetClientTest.PRODUCT,versions,BlizzardBuildDiscovery.Channel.CURRENT);
        var snapshot=new BlizzardBuildDiscovery.Snapshot(SelectedNativeAssetClientTest.PRODUCT,Instant.EPOCH,builds,BlizzardBuildDiscovery.parseCdns(cdns),List.of(SelectedNativeAssetClientTest.document("versions",versions),SelectedNativeAssetClientTest.document("cdns",cdns)));
        return new Fixture(core,BlizzardBuildDescriptor.fromDiscovery(snapshot,builds.getFirst(),"enUS"),files,archive,encoded);
    }
    NativeAssetClient client(Fixture f,AtomicInteger network,boolean corruptRange,NativeAssetClient.ArchiveBudget budget) {
        return new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(uri,bound,cancelled)->{
            assertFalse(uri.getHost().equals("wago.tools"));network.incrementAndGet();byte[] bytes=f.files().get(uri.toString());
            if(bytes==null)throw new FileNotFoundException("loose absent");return bytes;
        },(uri,offset,size,cancelled)->{
            assertTrue(uri.getPath().endsWith(f.archive()));assertEquals(123,offset);assertEquals(f.encoded().length,size);
            network.incrementAndGet();return corruptRange?new byte[size]:f.encoded();
        },budget);
    }
    @Test void exactArchiveRangeCompletesRealParsersAndCacheReuseHasZeroNetwork()throws Exception {
        var f=fixture(false);var calls=new AtomicInteger();
        try(var client=client(f,calls,false,NativeAssetClient.ArchiveBudget.standard())) {
            assertArrayEquals(f.core().asset(),Files.readAllBytes(client.fetch(SelectedNativeAssetClientTest.FDID,()->false).path()));
        }
        assertTrue(Files.exists(cache.resolve("archive-locations/"+f.descriptor().cdnConfigMd5()+"/"+f.core().assetEncodedKey())));
        String proof=Files.readString(cache.resolve("proof/"+SelectedNativeAssetClientTest.PRODUCT+"/"+SelectedNativeAssetClientTest.BUILD+"/"+SelectedNativeAssetClientTest.FDID+".json"));
        assertTrue(proof.contains(f.archive()));assertTrue(proof.contains("OFFICIAL_ONLY"));
        calls.set(0);try(var client=client(f,calls,false,NativeAssetClient.ArchiveBudget.standard())){client.fetch(SelectedNativeAssetClientTest.FDID,()->false);}
        assertEquals(0,calls.get());
    }
    @Test void verifiedLocationHintAvoidsRescanningEarlierIndexes()throws Exception {
        var f=fixture(true);var calls=new AtomicInteger();
        try(var client=client(f,calls,false,NativeAssetClient.ArchiveBudget.standard())){client.fetch(SelectedNativeAssetClientTest.FDID,()->false);}
        Files.delete(cache.resolve("content/"+f.core().assetKey()+".bin"));
        Files.delete(cache.resolve("encoded/"+f.core().assetEncodedKey()+".blte"));
        for(var file:Files.list(cache.resolve("indexes")).toList())if(!file.getFileName().toString().startsWith(f.archive()))Files.writeString(file,"tampered earlier index");
        calls.set(0);
        try(var client=client(f,calls,false,new NativeAssetClient.ArchiveBudget(1,1024*1024))){assertEquals(f.core().assetKey(),client.fetch(SelectedNativeAssetClientTest.FDID,()->false).contentMd5());}
        assertEquals(2,calls.get()); // loose request refused, then exact range; index is cached and authenticated
    }
    @Test void corruptRangeDoesNotPublishDecodedAsset()throws Exception {
        var f=fixture(false);try(var client=client(f,new AtomicInteger(),true,NativeAssetClient.ArchiveBudget.standard())) {
            assertThrows(IOException.class,()->client.fetch(SelectedNativeAssetClientTest.FDID,()->false));
        }
        assertFalse(Files.exists(cache.resolve("content/"+f.core().assetKey()+".bin")));
    }
    @Test void retryReusesVerifiedIndexesSoNetworkBudgetProgressesBeyondFirstPage()throws Exception {
        var f=fixture(true);var calls=new AtomicInteger();var budget=new NativeAssetClient.ArchiveBudget(1,1024*1024);
        try(var client=client(f,calls,false,budget)){assertThrows(IOException.class,()->client.fetch(SelectedNativeAssetClientTest.FDID,()->false));}
        try(var client=client(f,calls,false,budget)){assertEquals(f.core().assetKey(),client.fetch(SelectedNativeAssetClientTest.FDID,()->false).contentMd5());}
    }
    @Test void tamperedLocationCannotSelectUnpinnedArchive()throws Exception {
        var f=fixture(false);Path location=cache.resolve("archive-locations/"+f.descriptor().cdnConfigMd5()+"/"+f.core().assetEncodedKey());Files.createDirectories(location.getParent());Files.writeString(location,"f".repeat(32));
        try(var client=client(f,new AtomicInteger(),false,NativeAssetClient.ArchiveBudget.standard())){assertThrows(IOException.class,()->client.fetch(SelectedNativeAssetClientTest.FDID,()->false));}
        assertFalse(Files.exists(cache.resolve("content/"+f.core().assetKey()+".bin")));
    }
}
