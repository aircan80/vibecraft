package dev.wowforever.app.assets;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class CdnArchiveIndexTest {
    static byte[] md5(byte[] bytes)throws Exception{return MessageDigest.getInstance("MD5").digest(bytes);}
    static String identity(byte[] bytes)throws Exception{return HexFormat.of().formatHex(md5(Arrays.copyOfRange(bytes,bytes.length-28,bytes.length)));}
    static byte[] index(String key,int size,long offset)throws Exception {
        byte[] b=new byte[4096+24+28];ByteBuffer buf=ByteBuffer.wrap(b);
        buf.put(HexFormat.of().parseHex(key)).putInt(size).putInt((int)offset);
        buf.position(4096);buf.put(HexFormat.of().parseHex(key)).put(md5(Arrays.copyOf(b,4096)),0,8);
        buf.put(md5(Arrays.copyOfRange(b,4096,4120)),0,8);
        buf.put(new byte[]{1,0,0,4,4,4,16,8});buf.order(ByteOrder.LITTLE_ENDIAN).putInt(1);
        byte[] hash=md5(Arrays.copyOfRange(b,b.length-20,b.length));System.arraycopy(hash,0,b,b.length-8,8);return b;
    }
    @Test void exactFooterTocPageChainLocatesUnsignedOffsetAndRejectsAbsentKey()throws Exception {
        byte[] bytes=index("1".repeat(32),50,0xf0000000L);String id=identity(bytes);
        assertEquals(new CdnArchiveIndex.Entry(0xf0000000L,50),CdnArchiveIndex.find(bytes,id,"1".repeat(32),()->false));
        assertNull(CdnArchiveIndex.find(bytes,id,"2".repeat(32),()->false));
    }
    @Test void rejectsCorruptPageTocFooterAndWrongPinnedIdentity()throws Exception {
        byte[] bytes=index("1".repeat(32),50,12);String id=identity(bytes);
        for(int at:new int[]{0,4096,bytes.length-1}) {
            byte[] bad=bytes.clone();bad[at]^=1;
            assertThrows(IOException.class,()->CdnArchiveIndex.find(bad,id,"1".repeat(32),()->false));
        }
        assertThrows(IOException.class,()->CdnArchiveIndex.find(bytes,"0".repeat(32),"1".repeat(32),()->false));
    }
    @Test void framingFormatAndCancellationAreExplicit()throws Exception {
        byte[] bytes=index("1".repeat(32),50,12);String id=identity(bytes);
        assertThrows(CancellationException.class,()->CdnArchiveIndex.find(bytes,id,"1".repeat(32),()->true));
        byte[] unsupported=bytes.clone();unsupported[unsupported.length-28+12]=6;
        assertThrows(IOException.class,()->CdnArchiveIndex.find(unsupported,identity(unsupported),"1".repeat(32),()->false));
        assertThrows(IOException.class,()->CdnArchiveIndex.find(Arrays.copyOf(bytes,bytes.length-1),id,"1".repeat(32),()->false));
    }
    @Test void rangeRequiresExact206FramingAndDoesNotAcceptWholeBody()throws Exception {
        NativeAssetClient.validateResponse(206,"bytes 20-29/100",10,20,10);
        assertThrows(IOException.class,()->NativeAssetClient.validateResponse(200,"",10,20,10));
        for(String bad:List.of("bytes 21-30/100","bytes 20-28/100","bytes 20-29/*","bytes 20-29/25","bytes 20-29/999999999999999999999"))
            assertThrows(IOException.class,()->NativeAssetClient.validateResponse(206,bad,10,20,10));
        assertThrows(IOException.class,()->NativeAssetClient.validateResponse(206,"bytes 20-29/100",9,20,10));
    }
}
