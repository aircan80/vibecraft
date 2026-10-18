package dev.wowforever.importer.acquisition;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Structural XFTH v9 reader. Does not decode table records or apply a hotfix overlay. */
public final class HotfixSnapshotReader {
  public enum Status { VALID, REMOVED, INVALIDATED, NOT_PUBLIC }
  public record Entry(long offset,int region,int push,long unique,long tableHash,long recordId,
      long payloadOffset,int payloadBytes,Status status) {}
  public record Snapshot(int version,long build,String opaqueHeaderHash,List<Entry> entries) {
    public Snapshot {entries=List.copyOf(entries);}
  }
  public Snapshot read(Path file,long expectedBuild,long maximumBytes,int maximumEntries,
      BooleanSupplier cancelled)throws IOException {
    if(maximumBytes<44||maximumBytes>(64L<<20)||maximumEntries<1)throw new IllegalArgumentException("Invalid snapshot bounds");
    if(Files.size(file)>maximumBytes)throw new IOException("Hotfix snapshot size outside bounds");
    try(var input=Files.newInputStream(file);var output=new ByteArrayOutputStream()){
      byte[] chunk=new byte[65536];long count=0;int n;while((n=input.read(chunk))!=-1){check(cancelled);count+=n;if(count>maximumBytes)throw new IOException("Hotfix snapshot size outside bounds");output.write(chunk,0,n);}
      return read(output.toByteArray(),expectedBuild,maximumBytes,maximumEntries,cancelled);
    }
  }
  /** Same structural validation for a bounded read-only archive BLOB; no temporary extraction. */
  public Snapshot read(byte[] data,long expectedBuild,long maximumBytes,int maximumEntries,BooleanSupplier cancelled)throws IOException{
    if(maximumBytes<44||maximumBytes>(64L<<20)||maximumEntries<1)throw new IllegalArgumentException("Invalid snapshot bounds");
    check(cancelled);if(data.length<44||data.length>maximumBytes)throw new IOException("Hotfix snapshot size outside bounds");
    ByteBuffer header=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);magic(header);int version=header.getInt();
    if(version!=9)throw new IOException("Unsupported XFTH version: "+version);
    long build=Integer.toUnsignedLong(header.getInt());if(build!=expectedBuild)throw new IOException("Hotfix header build does not match selected build");
    byte[] opaque=new byte[32];header.get(opaque);List<Entry> entries=new ArrayList<>();int at=44;
    while(at<data.length){check(cancelled);if(entries.size()>=maximumEntries)throw new IOException("Too many hotfix entries");
      if(data.length-at<32)throw new IOException("Truncated hotfix entry header at "+at);
      ByteBuffer row=ByteBuffer.wrap(data,at,32).slice().order(ByteOrder.LITTLE_ENDIAN);magic(row);int region=row.getInt(),push=row.getInt();
      long unique=Integer.toUnsignedLong(row.getInt()),table=Integer.toUnsignedLong(row.getInt()),id=Integer.toUnsignedLong(row.getInt());
      long bytes=Integer.toUnsignedLong(row.getInt());int status=Byte.toUnsignedInt(row.get());
      if(status<1||status>4)throw new IOException("Unsupported hotfix status: "+status);
      if(bytes>Integer.MAX_VALUE||bytes>data.length-at-32)throw new IOException("Truncated hotfix payload at "+at);
      entries.add(new Entry(at,region,push,unique,table,id,at+32,(int)bytes,Status.values()[status-1]));at+=32+(int)bytes;
    }
    return new Snapshot(version,build,HexFormat.of().formatHex(opaque),entries);
  }
  private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Hotfix audit cancelled");}
  private static void magic(ByteBuffer bytes)throws IOException {
    if(bytes.get()!='X'||bytes.get()!='F'||bytes.get()!='T'||bytes.get()!='H')throw new IOException("Invalid XFTH magic");
  }
}
