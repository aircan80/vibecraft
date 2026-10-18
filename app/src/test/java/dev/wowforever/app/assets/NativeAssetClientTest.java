package dev.wowforever.app.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.DeflaterOutputStream;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class NativeAssetClientTest {
    @TempDir Path cache;
    @Test void rejectsOtherBuildsAndProducts() {
        assertThrows(IllegalArgumentException.class, () -> new NativeAssetClient(cache,"wow_classic_era",NativeAssetClient.BUILD));
        assertThrows(IllegalArgumentException.class, () -> new NativeAssetClient(cache,NativeAssetClient.PRODUCT,"1.60.1.70451"));
    }
    @Test void rejectsInvalidPerAssetBoundsBeforeIo() throws Exception {
        try(var client = new NativeAssetClient(cache,NativeAssetClient.PRODUCT,NativeAssetClient.BUILD)) {
            assertThrows(IllegalArgumentException.class,()->client.fetch(567490,()->false,0));
            assertThrows(IllegalArgumentException.class,()->client.fetch(567490,()->false,Integer.MAX_VALUE));
            assertFalse(Files.exists(cache.resolve("content")));
        }
    }
    @Test void cancelledRequestNeverStartsTransport() throws Exception {
        try(var client = new NativeAssetClient(cache,NativeAssetClient.PRODUCT,NativeAssetClient.BUILD)) {
            assertThrows(CancellationException.class,()->client.fetch(567490,()->true));
            assertFalse(Files.exists(cache.resolve("content")));
        }
    }
    @Test void refusesEdtBeforeIo() throws Exception {
        try(var client = new NativeAssetClient(cache,NativeAssetClient.PRODUCT,NativeAssetClient.BUILD)) {
            SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->client.fetch(567490,()->false)));
        }
    }
    @Test void rejectsTamperedPinnedConfig() throws Exception {
        Files.createDirectories(cache.resolve("config"));
        Files.writeString(cache.resolve("config/"+NativeAssetClient.CONFIG),"forged");
        try(var client = new NativeAssetClient(cache,NativeAssetClient.PRODUCT,NativeAssetClient.BUILD)) {
            assertTrue(assertThrows(IOException.class,()->client.fetch(567490,()->false)).getMessage().contains("MD5 mismatch"));
        }
    }
    @Test void nativeAndCompressedBlteRespectBoundsAndFraming() throws Exception {
        byte[] body = "genuine test bytes".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write("BLTE\0\0\0\0N".getBytes()); out.write(body);
        assertArrayEquals(body,NativeAssetClient.decodeBlte(out.toByteArray(),body.length));
        assertThrows(IOException.class,()->NativeAssetClient.decodeBlte(out.toByteArray(),2));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(); compressed.write("BLTE\0\0\0\0Z".getBytes());
        try(var deflate = new DeflaterOutputStream(compressed)) { deflate.write(body); }
        assertArrayEquals(body,NativeAssetClient.decodeBlte(compressed.toByteArray(),body.length));
        assertThrows(IOException.class,()->NativeAssetClient.decodeBlte(compressed.toByteArray(),2));
        byte[] trailing = java.util.Arrays.copyOf(compressed.toByteArray(),compressed.size()+1);
        assertThrows(IOException.class,()->NativeAssetClient.decodeBlte(trailing,100));
        assertThrows(IOException.class,()->NativeAssetClient.decodeBlte(new byte[2],100));
    }
    @Test void malformedAndWrongLocaleRootFailClosed() throws Exception {
        ByteBuffer root = ByteBuffer.allocate(24+12+4+16+8).order(ByteOrder.LITTLE_ENDIAN);
        root.putInt(0x4d465354).putInt(24).putInt(1).putInt(1).putInt(1).putInt(0);
        root.putInt(1).putInt(0).putInt(4).putInt(567490); root.put(new byte[16]); root.putLong(0);
        assertNull(NativeAssetClient.rootEntry(root.array(),567490,()->false));
        root.putInt(32,2);
        assertEquals("00000000000000000000000000000000",NativeAssetClient.rootEntry(root.array(),567490,()->false));
        root.putInt(28,0x80);
        assertNull(NativeAssetClient.rootEntry(root.array(),567490,()->false));
        assertThrows(IOException.class,()->NativeAssetClient.rootEntry(new byte[8],567490,()->false));
        assertThrows(IOException.class,()->NativeAssetClient.encodingEntry(new byte[8],"00000000000000000000000000000000",()->false));
    }
}
