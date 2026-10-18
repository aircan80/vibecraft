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
    if(maximumBytes<44||maximumEntries<1)throw new IllegalArgumentException("Invalid snapshot bounds");
    try(var input=FileChannel.open(file,StandardOpenOption.READ)) {
      long size=input.size();if(size<44||size>maximumBytes)throw new IOException("Hotfix snapshot size outside bounds");
      ByteBuffer header=read(input,0,44);magic(header);int version=header.getInt();
      if(version!=9)throw new IOException("Unsupported XFTH version: "+version);
      long build=Integer.toUnsignedLong(header.getInt());
      if(build!=expectedBuild)throw new IOException("Hotfix header build does not match selected build");
      byte[] opaque=new byte[32];header.get(opaque);List<Entry> entries=new ArrayList<>();long at=44;
      while(at<size) {
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Hotfix audit cancelled");
        if(entries.size()>=maximumEntries)throw new IOException("Too many hotfix entries");
        if(size-at<32)throw new IOException("Truncated hotfix entry header at "+at);
        ByteBuffer row=read(input,at,32);magic(row);int region=row.getInt(),push=row.getInt();
        long unique=Integer.toUnsignedLong(row.getInt()),table=Integer.toUnsignedLong(row.getInt()),id=Integer.toUnsignedLong(row.getInt());
        long bytes=Integer.toUnsignedLong(row.getInt());int status=Byte.toUnsignedInt(row.get());
        if(status<1||status>4)throw new IOException("Unsupported hotfix status: "+status);
        if(bytes>Integer.MAX_VALUE||bytes>size-at-32)throw new IOException("Truncated hotfix payload at "+at);
        entries.add(new Entry(at,region,push,unique,table,id,at+32,(int)bytes,Status.values()[status-1]));at+=32+bytes;
      }
      if(input.size()!=size)throw new IOException("Hotfix snapshot changed during audit");
      return new Snapshot(version,build,HexFormat.of().formatHex(opaque),entries);
    }
  }
  private static ByteBuffer read(FileChannel input,long at,int bytes)throws IOException {
    ByteBuffer buffer=ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
    while(buffer.hasRemaining()){int n=input.read(buffer,at+buffer.position());if(n<0)throw new EOFException("Truncated hotfix snapshot");}
    return buffer.flip();
  }
  private static void magic(ByteBuffer bytes)throws IOException {
    if(bytes.get()!='X'||bytes.get()!='F'||bytes.get()!='T'||bytes.get()!='H')throw new IOException("Invalid XFTH magic");
  }
}
