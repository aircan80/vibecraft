package dev.wowforever.app.assets;

import java.io.*;
import java.net.URI;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Complete synthetic CASC chains exercise real parsers/hash checks; no game assets are committed. */
class SelectedNativeAssetClientTest {
    @TempDir Path cache;
    static final long FDID=567490;
    static final String PRODUCT="wow_classic_beta",BUILD="1.60.1.70451";
    static String hash(String algo,byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algo).digest(bytes));}
    static byte[] blte(byte[] bytes)throws IOException{var out=new ByteArrayOutputStream();out.write(new byte[]{'B','L','T','E',0,0,0,0,'N'});out.write(bytes);return out.toByteArray();}
    static String keyPath(String key){return key.substring(0,2)+"/"+key.substring(2,4)+"/"+key;}
    static BlizzardBuildDiscovery.Document document(String endpoint,String text)throws Exception{
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);return new BlizzardBuildDiscovery.Document(URI.create("https://us.version.battle.net/"+PRODUCT+"/"+endpoint),Instant.EPOCH,BlizzardBuildDiscovery.Status.PUBLISHED,hash("SHA-256",bytes),bytes.length,text);
    }
    record Fixture(BlizzardBuildDescriptor descriptor,Map<String,byte[]> transport,byte[] asset,String assetKey,String assetEncodedKey,String rootKey) {}
    static Fixture fixture(int locale)throws Exception{return fixture(locale,BUILD,PRODUCT);}
    static Fixture fixture(int locale,String declaredBuild,String declaredProduct)throws Exception{
        byte[] asset="synthetic source-qualified asset".getBytes(StandardCharsets.UTF_8);String assetKey=hash("MD5",asset),assetEncoded=hash("MD5",blte(asset));
        ByteBuffer root=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);root.putInt(0x4d465354).putInt(24).putInt(1).putInt(1).putInt(1).putInt(0).putInt(1).putInt(0).putInt(locale).putInt((int)FDID).put(HexFormat.of().parseHex(assetKey)).putLong(0);
        byte[] rootBytes=root.array();String rootKey=hash("MD5",rootBytes),rootEncoded=hash("MD5",blte(rootBytes));
        ByteBuffer en=ByteBuffer.allocate(1078).order(ByteOrder.BIG_ENDIAN);en.putShort((short)0x454e).put((byte)1).put((byte)16).put((byte)16).putShort((short)1);en.putInt(9,1);en.putInt(18,0);en.position(54);
        for(var entry:List.of(Map.entry(rootKey,Map.entry(rootBytes.length,rootEncoded)),Map.entry(assetKey,Map.entry(asset.length,assetEncoded)))){
            en.put((byte)1).put((byte)0).putInt(entry.getValue().getKey()).put(HexFormat.of().parseHex(entry.getKey())).put(HexFormat.of().parseHex(entry.getValue().getValue()));
        }
        byte[] encoding=en.array();String encodingKey=hash("MD5",encoding),encodedEncoding=hash("MD5",blte(encoding));
        byte[] config=("build-name = WOW-"+declaredBuild.substring(declaredBuild.lastIndexOf('.')+1)+"patch"+declaredBuild.substring(0,declaredBuild.lastIndexOf('.'))+"_Fixture\nbuild-uid = "+declaredProduct+"\nroot = "+rootKey+"\nencoding = "+encodingKey+" "+encodedEncoding+"\n").getBytes(StandardCharsets.UTF_8);
        byte[] cdnConfig=("archives = "+"a".repeat(32)+"\n").getBytes(StandardCharsets.UTF_8);String configKey=hash("MD5",config),cdnKey=hash("MD5",cdnConfig);
        String versions="Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|BuildId!DEC:4|VersionsName!String:0\nus|"+configKey+"|"+cdnKey+"|70451|"+BUILD+"\n";
        String cdns="Name!STRING:0|Path!STRING:0|Hosts!STRING:0\nus|tpr/wow|us.cdn.blizzard.com\n";
        var builds=BlizzardBuildDiscovery.parseBuilds(PRODUCT,versions,BlizzardBuildDiscovery.Channel.CURRENT);
        var snapshot=new BlizzardBuildDiscovery.Snapshot(PRODUCT,Instant.EPOCH,builds,BlizzardBuildDiscovery.parseCdns(cdns),List.of(document("versions",versions),document("cdns",cdns)));
        var descriptor=BlizzardBuildDescriptor.fromDiscovery(snapshot,builds.getFirst(),locale==0x10?"frFR":"enUS");
        Map<String,byte[]> transport=new HashMap<>();String base="https://us.cdn.blizzard.com/tpr/wow/";
        transport.put(base+"config/"+keyPath(configKey),config);transport.put(base+"config/"+keyPath(cdnKey),cdnConfig);
        transport.put(base+"data/"+keyPath(encodedEncoding),blte(encoding));transport.put(base+"data/"+keyPath(rootEncoded),blte(rootBytes));transport.put(base+"data/"+keyPath(assetEncoded),blte(asset));
        return new Fixture(descriptor,transport,asset,assetKey,assetEncoded,rootKey);
    }
    static NativeAssetClient.Transport transport(Fixture f,List<URI> requests,boolean officialAssetUnavailable,byte[] fallback){return (uri,bound,cancelled)->{
        requests.add(uri);if(uri.getHost().equals("wago.tools")){if(fallback==null)throw new IOException("Third-party transport not expected");return fallback;}
        if(officialAssetUnavailable&&uri.toString().endsWith(f.assetEncodedKey()))throw new IOException("Archive-only test content");
        byte[] bytes=f.transport().get(uri.toString());if(bytes==null)throw new IOException("Unexpected URL: "+uri);return bytes;
    };}
    @Test void selectedBuildAuthenticatesWholeChainAndReusesVerifiedCacheWithoutNetwork()throws Exception{
        var f=fixture(2);List<URI> requests=new ArrayList<>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,requests,false,null))){
            var asset=client.fetch(FDID,()->false);assertArrayEquals(f.asset(),Files.readAllBytes(asset.path()));assertEquals(BUILD,asset.build());assertEquals(f.rootKey(),asset.rootContentMd5());assertEquals(PRODUCT,asset.product());assertEquals(5,requests.size());
            String proof=Files.readString(cache.resolve("proof/"+PRODUCT+"/"+BUILD+"/"+FDID+".json"));assertTrue(proof.contains("OFFICIAL_ONLY"));assertTrue(proof.contains(f.descriptor().buildConfigMd5()));
        }
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(u,b,c)->{throw new AssertionError("Verified cache must avoid network");})){assertEquals(f.assetKey(),client.fetch(FDID,()->false).contentMd5());}
    }
    @Test void officialOnlyNeverUsesWagoEvenWhenNativeLooseAssetMissing()throws Exception{
        var f=fixture(2);List<URI> requests=new ArrayList<>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,requests,true,f.asset()))){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertFalse(requests.stream().anyMatch(u->u.getHost().equals("wago.tools")));assertFalse(Files.exists(cache.resolve("content/"+f.assetKey()+".bin")));
    }
    @Test void explicitlyAllowedWagoMustMatchSelectedRootAndBuild()throws Exception{
        var f=fixture(2);List<URI> requests=new ArrayList<>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,transport(f,requests,true,f.asset()))){assertEquals(f.assetKey(),client.fetch(FDID,()->false).contentMd5());}
        URI fallback=requests.getLast();assertEquals("wago.tools",fallback.getHost());assertTrue(fallback.getQuery().contains("version="+BUILD));assertTrue(fallback.getQuery().contains("branch="+PRODUCT));
    }
    @Test void rejectsWrongFallbackBytesWithoutPublishingAsset()throws Exception{
        var f=fixture(2);try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO,transport(f,new ArrayList<>(),true,new byte[]{1,2,3}))){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertFalse(Files.exists(cache.resolve("content/"+f.assetKey()+".bin")));
    }
    @Test void corruptedSelectedConfigurationAndCacheNeverBecomeTrusted()throws Exception{
        var f=fixture(2);f.transport().replaceAll((u,b)->u.contains("/config/")?new byte[]{1}:b);
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,new ArrayList<>(),false,null))){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertFalse(Files.exists(cache.resolve("content/"+f.assetKey()+".bin")));
    }
    @Test void rejectsCdnConfigurationHashMismatchBeforeReadingContent()throws Exception{
        var f=fixture(2);String key=f.descriptor().cdnConfigMd5();f.transport().put("https://us.cdn.blizzard.com/tpr/wow/config/"+keyPath(key),new byte[]{9});List<URI> requests=new ArrayList<>();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,requests,false,null))){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertEquals(2,requests.size());assertTrue(requests.stream().allMatch(u->u.getPath().contains("/config/")));
    }
    @Test void selectedBuildAndProductMustAgreeWithHashVerifiedConfiguration()throws Exception{
        for(var f:List.of(fixture(2,"1.60.1.70450",PRODUCT),fixture(2,BUILD,"wow_classic_era"))){
            try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,new ArrayList<>(),false,null))){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        }
    }
    @Test void tamperedDecodedCacheFailsRevalidationWithoutOtherTransport()throws Exception{
        var f=fixture(2);try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,new ArrayList<>(),false,null))){client.fetch(FDID,()->false);}
        Files.write(cache.resolve("content/"+f.assetKey()+".bin"),new byte[]{1,2,3});
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(u,b,c)->{throw new AssertionError("Cached corruption must not select another transport");})){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
    }
    @Test void selectedLocaleControlsRootContentWithoutEraOrEnglishFallback()throws Exception{
        var f=fixture(0x10);try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,transport(f,new ArrayList<>(),false,null))){assertEquals(f.assetKey(),client.fetch(FDID,()->false).contentMd5());}
    }
    @Test void cancellationStopsAfterTransferBeforeContentPublication()throws Exception{
        var f=fixture(2);AtomicBoolean cancelled=new AtomicBoolean();
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(u,b,c)->{cancelled.set(true);return f.transport().get(u.toString());})){assertThrows(CancellationException.class,()->client.fetch(FDID,cancelled::get));}
        assertFalse(Files.exists(cache.resolve("config/"+f.descriptor().buildConfigMd5())));
    }
    @Test void gameFolderAndSymlinkedGameCacheAreNeverWritten()throws Exception{
        var f=fixture(2);Path game=cache.resolve("game");Files.createDirectories(game);Files.writeString(game.resolve(".build.info"),"source metadata");
        try(var client=new NativeAssetClient(game.resolve("cache"),f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(u,b,c)->{throw new AssertionError("No transport expected");})){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertFalse(Files.exists(game.resolve("cache")));
        Path link=cache.resolve("redirect");try{Files.createSymbolicLink(link,game);}catch(UnsupportedOperationException|FileSystemException unavailable){return;}
        try(var client=new NativeAssetClient(link.resolve("cache"),f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(u,b,c)->{throw new AssertionError("No transport expected");})){assertThrows(IOException.class,()->client.fetch(FDID,()->false));}
        assertEquals("source metadata",Files.readString(game.resolve(".build.info")));
    }
}
