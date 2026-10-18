package dev.wowforever.app.assets;

import java.io.IOException;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;

/** Strict v1 CDN archive index (full 16-byte EKeys, four-byte size/offset, eight-byte checksums).
 * The archive name authenticates the complete 28-byte footer, which authenticates the TOC and pages.
 */
final class CdnArchiveIndex {
    record Entry(long offset, int size) {}
    private CdnArchiveIndex() {}
    static Entry find(byte[] bytes,String archive,String wanted,BooleanSupplier cancelled)throws IOException {
        if(bytes.length<28||!archive.matches("[0-9a-f]{32}")||!wanted.matches("[0-9a-f]{32}"))throw new IOException("Invalid archive index identity");
        int footer=bytes.length-28;
        if(!HexFormat.of().formatHex(md5(bytes,footer,28)).equals(archive))throw new IOException("Archive index footer identity mismatch");
        if(bytes[footer+8]!=1||bytes[footer+9]!=0||bytes[footer+10]!=0||bytes[footer+11]!=4
                ||bytes[footer+12]!=4||bytes[footer+13]!=4||bytes[footer+14]!=16||bytes[footer+15]!=8)
            throw new IOException("Unsupported archive index format");
        byte[] footerHash=Arrays.copyOfRange(bytes,footer+8,bytes.length);Arrays.fill(footerHash,12,20,(byte)0);
        prefix(md5(footerHash,0,20),bytes,footer+20,"footer");
        long entries=Integer.toUnsignedLong(ByteBuffer.wrap(bytes,footer+16,4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        int perPage=4096/24;long pages=(entries+perPage-1)/perPage,toc=pages*4096;
        if(entries==0||toc+pages*24+28!=bytes.length)throw new IOException("Archive index framing mismatch");
        prefix(md5(bytes,(int)toc,(int)(pages*24)),bytes,footer,"TOC");
        byte[] key=HexFormat.of().parseHex(wanted),previous=null;
        Entry found=null;
        for(int page=0;page<pages;page++) {
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Archive lookup cancelled");
            int begin=page*4096;
            prefix(md5(bytes,begin,4096),bytes,(int)toc+(int)pages*16+page*8,"page");
            int count=(int)Math.min(perPage,entries-(long)page*perPage);
            for(int row=0;row<count;row++) {
                int at=begin+row*24;byte[] current=Arrays.copyOfRange(bytes,at,at+16);
                if(previous!=null&&Arrays.compareUnsigned(previous,current)>=0)throw new IOException("Unsorted/duplicate archive index EKey");
                previous=current;
                long size=Integer.toUnsignedLong(ByteBuffer.wrap(bytes,at+16,4).getInt());
                long offset=Integer.toUnsignedLong(ByteBuffer.wrap(bytes,at+20,4).getInt());
                if(size==0||size>Integer.MAX_VALUE||offset+size>0x1_0000_0000L)throw new IOException("Invalid archive entry bounds");
                if(Arrays.equals(key,current))found=new Entry(offset,(int)size);
            }
            if(!Arrays.equals(previous,Arrays.copyOfRange(bytes,(int)toc+page*16,(int)toc+(page+1)*16)))
                throw new IOException("Archive TOC key mismatch");
            for(int pad=begin+count*24;pad<begin+4096;pad++)if(bytes[pad]!=0)throw new IOException("Nonzero archive page padding");
        }
        return found;
    }
    private static byte[] md5(byte[] bytes,int offset,int length)throws IOException {
        try{var digest=MessageDigest.getInstance("MD5");digest.update(bytes,offset,length);return digest.digest();}
        catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    private static void prefix(byte[] hash,byte[] bytes,int at,String part)throws IOException {
        if(!MessageDigest.isEqual(Arrays.copyOf(hash,8),Arrays.copyOfRange(bytes,at,at+8)))throw new IOException("Archive index "+part+" checksum mismatch");
    }
}
