package dev.wowforever.app.assets;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalWowDiscoveryTest {
    @TempDir Path temp;
    static final String VERSION="1.60.1.70450",PRODUCT="wow_classic_beta";
    static String md5(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));}
    String fixture(Path root,String product,String version)throws Exception{
        Files.createDirectories(root);
        String config="build-name = WOW-"+version.substring(version.lastIndexOf('.')+1)+"patch"+version.substring(0,version.lastIndexOf('.'))+"_Fixture\nbuild-uid = "+product+"\nroot = "+"1".repeat(32)+"\nencoding = "+"2".repeat(32)+" "+"3".repeat(32)+"\n";
        String key=md5(config.getBytes());Path file=root.resolve("Data/config/"+key.substring(0,2)+"/"+key.substring(2,4)+"/"+key);Files.createDirectories(file.getParent());Files.writeString(file,config);
        Files.writeString(root.resolve(".build.info"),"\ufeffActive!DEC:1|Product!STRING:0|Version!STRING:0|Build Key!HEX:16|Tags!STRING:0\n1|"+product+"|"+version+"|"+key+"|Windows x86_64 enUS speech deDE\n");return key;
    }
    @Test void validatesTypedMetadataConfigHashesLocaleAndDeduplicatesParentSelections()throws Exception{
        Path root=temp.resolve("World of Warcraft");String key=fixture(root,PRODUCT,VERSION);Path child=root.resolve("_classic_beta_");Files.createDirectories(child);
        var detector=new LocalWowDiscovery(Map.of(PRODUCT+":"+VERSION,key));
        var result=detector.scan(List.of(root,child,root.resolve(".build.info")),false,()->false,p->{});
        assertEquals(1,result.installations().size());var found=result.installations().getFirst();
        assertEquals(LocalWowDiscovery.Status.SUPPORTED_METADATA,found.status());assertEquals(root.toRealPath(),found.directory());assertEquals(Set.of("enUS","deDE"),found.locales());
        assertEquals(key,found.buildConfigMd5());assertEquals("1".repeat(32),found.rootContentMd5());assertTrue(detector.stillMatches(found,()->false));
        assertThrows(UnsupportedOperationException.class,()->result.installations().clear());
    }
    @Test void neverTreatsEraAsForeverDespiteFolderName()throws Exception{
        Path root=temp.resolve("Forever Beta");fixture(root,"wow_classic_era","1.15.9.99999");
        assertEquals(LocalWowDiscovery.Status.OTHER_EDITION,new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst().status());
    }
    @Test void unknownForeverBuildAndUnpinnedConfigCannotBecomeSupported()throws Exception{
        Path root=temp.resolve("beta");fixture(root,PRODUCT,VERSION);
        assertEquals(LocalWowDiscovery.Status.UNSUPPORTED_BUILD,new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst().status());
    }
    @Test void missingAndCorruptedConfigAreDistinctFromSupported()throws Exception{
        Path root=temp.resolve("beta");String key=fixture(root,PRODUCT,VERSION);Path file=root.resolve("Data/config/"+key.substring(0,2)+"/"+key.substring(2,4)+"/"+key);
        Files.writeString(file,"corrupt");var detector=new LocalWowDiscovery();assertEquals(LocalWowDiscovery.Status.INVALID,detector.inspect(root,()->false,p->{}).installations().getFirst().status());
        Files.delete(file);assertEquals(LocalWowDiscovery.Status.INCOMPLETE,detector.inspect(root,()->false,p->{}).installations().getFirst().status());
    }
    @Test void rejectsOversizedAndMalformedMetadata()throws Exception{
        Path root=temp.resolve("beta");fixture(root,PRODUCT,VERSION);Files.write(root.resolve(".build.info"),new byte[1024*1024+1]);
        assertEquals(LocalWowDiscovery.Status.INVALID,new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst().status());
        Files.writeString(root.resolve(".build.info"),"Active|Active|Version\n1|1|1.60.1.70450");
        assertEquals(LocalWowDiscovery.Status.INVALID,new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst().status());
    }
    @Test void rejectsMetadataThatDisagreesWithHashedConfiguration()throws Exception{
        Path root=temp.resolve("beta");fixture(root,PRODUCT,VERSION);
        Files.writeString(root.resolve(".build.info"),Files.readString(root.resolve(".build.info")).replace(VERSION,"1.60.1.70451"));
        var result=new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst();
        assertEquals(LocalWowDiscovery.Status.INVALID,result.status());assertTrue(result.explanation().contains("version disagrees"));
    }
    @Test void rejectsEscapingMetadataSymlink()throws Exception{
        Path root=temp.resolve("beta");fixture(root,PRODUCT,VERSION);Path outside=Files.createTempFile("wow-metadata-outside-",".txt");
        try{Files.delete(root.resolve(".build.info"));Files.createSymbolicLink(root.resolve(".build.info"),outside);
            assertEquals(LocalWowDiscovery.Status.INVALID,new LocalWowDiscovery().inspect(root,()->false,p->{}).installations().getFirst().status());
        }finally{Files.deleteIfExists(outside);}
    }
    @Test void detectsIdentityChangeBeforeLaterExtraction()throws Exception{
        Path root=temp.resolve("beta");String key=fixture(root,PRODUCT,VERSION);var detector=new LocalWowDiscovery(Map.of(PRODUCT+":"+VERSION,key));var found=detector.inspect(root,()->false,p->{}).installations().getFirst();
        Files.writeString(root.resolve(".build.info"),Files.readString(root.resolve(".build.info"))+"#patch changed\n");assertFalse(detector.stillMatches(found,()->false));
    }
    @Test void boundedNoRecursionAndCancellationAvoidUnwantedInspection()throws Exception{
        Path root=temp.resolve("nested/World of Warcraft");fixture(root,PRODUCT,VERSION);
        assertTrue(new LocalWowDiscovery().inspect(temp,()->false,p->{}).installations().isEmpty());
        List<Path> paths=new ArrayList<>();for(int i=0;i<80;i++)paths.add(temp.resolve("absent-"+i));
        var result=new LocalWowDiscovery().scan(paths,false,()->false,p->{});assertEquals(64,result.inspectedCandidates());assertTrue(result.limitReached());
        AtomicInteger progress=new AtomicInteger();assertThrows(CancellationException.class,()->new LocalWowDiscovery().inspect(root,()->true,p->progress.incrementAndGet()));assertEquals(0,progress.get());
    }
    @Test void refusesEdtAndDoesNotModifyInstallation()throws Exception{
        Path root=temp.resolve("beta");fixture(root,PRODUCT,VERSION);byte[] before=Files.readAllBytes(root.resolve(".build.info"));List<Path> filesBefore;try(var paths=Files.walk(root)){filesBefore=paths.sorted().toList();}var detector=new LocalWowDiscovery();
        SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->detector.inspect(root,()->false,p->{})));
        detector.inspect(root,()->false,p->{});assertArrayEquals(before,Files.readAllBytes(root.resolve(".build.info")));
        try(var paths=Files.walk(root)){assertEquals(filesBefore,paths.sorted().toList());}
    }
}
