package dev.wowforever.app.assets;

import dev.wowforever.app.music.CityMusicCatalog;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedDataFilesTest {
    @TempDir Path root;
    String previous;
    @BeforeEach void remember() { previous=System.getProperty(ReviewedDataFiles.DIRECTORY_PROPERTY); }
    @AfterEach void restore() { if(previous==null)System.clearProperty(ReviewedDataFiles.DIRECTORY_PROPERTY);else System.setProperty(ReviewedDataFiles.DIRECTORY_PROPERTY,previous); }
    @Test void explicitDirectoryReadsOnlyHashVerifiedBoundedData() throws Exception {
        byte[] expected="reviewed metadata".getBytes();
        Files.createDirectories(root.resolve("music"));Files.write(root.resolve("music/catalog.json"),expected);
        System.setProperty(ReviewedDataFiles.DIRECTORY_PROPERTY,root.toString());
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
        try(var in=ReviewedDataFiles.open("music/catalog.json",sha,100)) { assertArrayEquals(expected,in.readAllBytes()); }
        assertThrows(IOException.class,()->ReviewedDataFiles.open("music/catalog.json",sha,2));
        Files.writeString(root.resolve("music/catalog.json"),"tampered");
        assertThrows(IOException.class,()->ReviewedDataFiles.open("music/catalog.json",sha,100));
    }
    @Test void explicitMissingDataNeverFallsBackToBundledMusic() {
        System.setProperty(ReviewedDataFiles.DIRECTORY_PROPERTY,root.toString());
        assertEquals("",CityMusicCatalog.bundled().build());
        assertTrue(CityMusicCatalog.bundled().city(1519).isEmpty());
    }
    @Test void bundledAndExternalPinnedCatalogHaveSameSixCities() throws Exception {
        System.clearProperty(ReviewedDataFiles.DIRECTORY_PROPERTY);
        var bundled=CityMusicCatalog.bundled();assertEquals(NativeAssetClient.BUILD,bundled.build());
        Files.createDirectories(root.resolve("city-music"));
        try(var in=getClass().getResourceAsStream("/data/city-music/catalog.json")) { assertNotNull(in);Files.copy(in,root.resolve("city-music/catalog.json")); }
        System.setProperty(ReviewedDataFiles.DIRECTORY_PROPERTY,root.toString());
        var external=CityMusicCatalog.bundled();assertEquals(bundled.build(),external.build());
        for(long id:new long[]{1519,1537,1657,1637,1638,1497})assertEquals(bundled.city(id),external.city(id));
        Files.writeString(root.resolve("city-music/catalog.json"),"{}");
        assertEquals("",CityMusicCatalog.bundled().build());
    }
    @Test void rejectsTraversalAndSymlinkOutsideExplicitRoot() throws Exception {
        System.setProperty(ReviewedDataFiles.DIRECTORY_PROPERTY,root.toString());
        String sha="0".repeat(64);
        assertThrows(IOException.class,()->ReviewedDataFiles.open("../catalog.json",sha,100));
        assertThrows(IOException.class,()->ReviewedDataFiles.open("/catalog.json",sha,100));
        Path outside=Files.createTempFile("reviewed-outside-",".json");
        try { Files.createSymbolicLink(root.resolve("catalog.json"),outside);assertThrows(IOException.class,()->ReviewedDataFiles.open("catalog.json",sha,100)); }
        finally { Files.deleteIfExists(outside); }
    }
}
