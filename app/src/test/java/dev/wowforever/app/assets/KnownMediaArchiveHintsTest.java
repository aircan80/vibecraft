package dev.wowforever.app.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class KnownMediaArchiveHintsTest {
    @TempDir Path cache;
    @AfterEach void reset(){AssetDownloadPermissions.resetForCatalogResolution();}
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static KnownMediaArchiveHints synthetic(ArchivedNativeAssetClientTest.Fixture f,String archive)throws Exception {
        byte[] config=f.files().get("https://us.cdn.blizzard.com/tpr/wow/config/"+SelectedNativeAssetClientTest.keyPath(f.descriptor().buildConfigMd5()));
        String encoding=new String(config,StandardCharsets.UTF_8).lines().filter(l->l.startsWith("encoding = ")).findFirst().orElseThrow().split(" ")[2];
        byte[] bytes=("# Synthetic reviewed routing metadata only.\n# product="+f.descriptor().product()+" build="+f.descriptor().build()
                +" config="+f.descriptor().buildConfigMd5()+" cdn="+f.descriptor().cdnConfigMd5()+" root="+f.core().rootKey()+" encoding="+encoding
                +"\nfdid\tcontent_md5\tencoding_key\tarchive\n567490\t"+f.core().assetKey()+"\t"+f.core().assetEncodedKey()+"\t"+archive+"\n").getBytes(StandardCharsets.UTF_8);
        return KnownMediaArchiveHints.parse(bytes,sha(bytes));
    }
    NativeAssetClient client(ArchivedNativeAssetClientTest.Fixture f,KnownMediaArchiveHints hints,List<URI> indexes,AtomicInteger ranges) {
        return new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,(uri,bound,cancelled)->{
            if(uri.getPath().endsWith(".index"))indexes.add(uri);byte[] bytes=f.files().get(uri.toString());if(bytes==null)throw new FileNotFoundException("loose absent");return bytes;
        },(uri,offset,size,cancelled)->{ranges.incrementAndGet();assertTrue(uri.getPath().endsWith(f.archive()));assertEquals(123,offset);assertEquals(f.encoded().length,size);return f.encoded();},new NativeAssetClient.ArchiveBudget(1,1024*1024),hints);
    }
    @Test void reviewedMetadataCoversAll31CurrentIdsUsingOnlyThreeArchives()throws Exception {
        var hints=KnownMediaArchiveHints.reviewed();assertEquals(31,hints.entries().size());
        var archives=new HashSet<String>();var profile=BlizzardBuildDescriptor.legacyForever();
        for(var entry:hints.entries())archives.add(hints.archive(profile,"8a0ca94be2f6ace93991c04ca6e19b57","c441a5427cb86b23bbfb6ed9c6cf0114",entry.fileDataId(),entry.contentMd5(),entry.encodedKey()).orElseThrow());
        assertEquals(Set.of("87b7255c070103bb6f355d6b27d5dd63","3405a8dbe3154b5801900aad8ebf7af9","5b5e32951bded7e54260f5ae4c1cf2af"),archives);
        assertEquals(31,hints.entries().stream().map(KnownMediaArchiveHints.Entry::fileDataId).distinct().count());
    }
    @Test void alteredBytesOrExtraOffsetCannotBecomeReviewedMetadata()throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream(KnownMediaArchiveHints.RESOURCE)){bytes=input.readAllBytes();}
        bytes[bytes.length-2]^=1;assertThrows(IOException.class,()->KnownMediaArchiveHints.parse(bytes,KnownMediaArchiveHints.SHA256));
        String modified=new String(bytes,StandardCharsets.UTF_8).replace("fdid\tcontent_md5\tencoding_key\tarchive","fdid\tcontent_md5\tencoding_key\tarchive\toffset");
        byte[] extra=modified.getBytes(StandardCharsets.UTF_8);assertThrows(IOException.class,()->KnownMediaArchiveHints.parse(extra,sha(extra)));
    }
    @Test void fullBuildRootEncodingContentAndEkeyIdentityMustAgree()throws Exception {
        var hints=KnownMediaArchiveHints.reviewed();var entry=hints.entries().iterator().next();var supported=BlizzardBuildDescriptor.legacyForever();
        assertTrue(hints.archive(SelectedNativeAssetClientTest.fixture(2).descriptor(),"8a0ca94be2f6ace93991c04ca6e19b57","c441a5427cb86b23bbfb6ed9c6cf0114",entry.fileDataId(),entry.contentMd5(),entry.encodedKey()).isEmpty());
        assertTrue(hints.archive(supported,"0".repeat(32),"c441a5427cb86b23bbfb6ed9c6cf0114",entry.fileDataId(),entry.contentMd5(),entry.encodedKey()).isEmpty());
        assertTrue(hints.archive(supported,"8a0ca94be2f6ace93991c04ca6e19b57","0".repeat(32),entry.fileDataId(),entry.contentMd5(),entry.encodedKey()).isEmpty());
        assertTrue(hints.archive(supported,"8a0ca94be2f6ace93991c04ca6e19b57","c441a5427cb86b23bbfb6ed9c6cf0114",entry.fileDataId(),"0".repeat(32),entry.encodedKey()).isEmpty());
        assertTrue(hints.archive(supported,"8a0ca94be2f6ace93991c04ca6e19b57","c441a5427cb86b23bbfb6ed9c6cf0114",entry.fileDataId(),entry.contentMd5(),"0".repeat(32)).isEmpty());
    }
    @Test void eachPinnedBuildIdentityFieldIsRequiredIndividually()throws Exception {
        String original;try(var input=getClass().getResourceAsStream(KnownMediaArchiveHints.RESOURCE)){original=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        var entry=KnownMediaArchiveHints.reviewed().entries().iterator().next();
        for(var replacement:Map.of("product=wow_classic_beta","product=wow_classic_era","build=1.60.1.70450","build=1.60.1.70451",
                "config=84bea4233331e390fda8724c6f91ffa5","config="+"0".repeat(32),"cdn=f065eade5276a5ac05606e60a4c5a1f7","cdn="+"0".repeat(32),
                "root=8a0ca94be2f6ace93991c04ca6e19b57","root="+"0".repeat(32),"encoding=c441a5427cb86b23bbfb6ed9c6cf0114","encoding="+"0".repeat(32)).entrySet()) {
            byte[] altered=original.replace(replacement.getKey(),replacement.getValue()).getBytes(StandardCharsets.UTF_8);
            var hints=KnownMediaArchiveHints.parse(altered,sha(altered));
            assertTrue(hints.archive(BlizzardBuildDescriptor.legacyForever(),"8a0ca94be2f6ace93991c04ca6e19b57","c441a5427cb86b23bbfb6ed9c6cf0114",
                    entry.fileDataId(),entry.contentMd5(),entry.encodedKey()).isEmpty(),replacement.getKey());
        }
    }
    @Test void validHintFetchesOnlyMatchingIndexThenDerivesAuthenticatedRange()throws Exception {
        var f=new ArchivedNativeAssetClientTest().fixture(true);var indexes=new ArrayList<URI>();var ranges=new AtomicInteger();
        try(var client=client(f,synthetic(f,f.archive()),indexes,ranges)){assertArrayEquals(f.core().asset(),Files.readAllBytes(client.fetch(567490,()->false).path()));}
        assertEquals(1,indexes.size());assertTrue(indexes.getFirst().getPath().endsWith(f.archive()+".index"));assertEquals(1,ranges.get());
    }
    @Test void staleHintCannotSupplyWrongRangeAndBoundedRetryStillProgresses()throws Exception {
        var f=new ArchivedNativeAssetClientTest().fixture(true);String first=NativeAssetClient.parseArchives(f.files().get("https://us.cdn.blizzard.com/tpr/wow/config/"+SelectedNativeAssetClientTest.keyPath(f.descriptor().cdnConfigMd5()))).getFirst();
        var indexes=new ArrayList<URI>();var ranges=new AtomicInteger();var stale=synthetic(f,first);
        try(var client=client(f,stale,indexes,ranges)){assertThrows(IOException.class,()->client.fetch(567490,()->false));}
        assertEquals(0,ranges.get());
        try(var client=client(f,stale,indexes,ranges)){assertEquals(f.core().assetKey(),client.fetch(567490,()->false).contentMd5());}
        assertEquals(1,ranges.get());assertEquals(2,indexes.size());
    }
    @Test void sharedNoCdnConsentMeansZeroDownloadsEvenWithValidReviewedHint()throws Exception {
        var f=new ArchivedNativeAssetClientTest().fixture(true);var hints=synthetic(f,f.archive());var indexes=new ArrayList<URI>();var ranges=new AtomicInteger();
        try(var client=client(f,hints,indexes,ranges)){client.fetch(567490,()->false);}
        Files.delete(cache.resolve("content/"+f.core().assetKey()+".bin"));Files.delete(cache.resolve("encoded/"+f.core().assetEncodedKey()+".blte"));Files.delete(cache.resolve("indexes/"+f.archive()+".index"));
        AssetDownloadPermissions.configureManaged(cache.resolve("library.sqlite"),new AssetDownloadPermissions.Consent(AssetDownloadPermissions.Mode.MANAGED,false,false,List.of()));
        try(var client=new NativeAssetClient(cache,f.descriptor(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY,
                (u,b,c)->{throw new AssertionError("Unselected CDN must never be contacted");},
                (u,o,n,c)->{throw new AssertionError("Unselected CDN range must never be contacted");},NativeAssetClient.ArchiveBudget.standard(),hints).withSharedPermissions()) {
            assertThrows(IOException.class,()->client.fetch(567490,()->false));
        }
    }
}
