package dev.wowforever.importer.setup;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.sqlite.SQLiteConfig;

/** Non-destructive runtime-v1 export. Input must be a closed, validated staging database. */
public final class RuntimeLibraryExporter {
  public record Digest(long rows,String sha256MultisetSum) {}
  public record Receipt(long sourceBytes,String sourceSha256,long outputBytes,String outputSha256,
      String buildId,long omittedRawRecords,long omittedArchiveBytes,Map<String,Digest> retainedTables) {
    public Receipt { retainedTables=Map.copyOf(retainedTables); }
  }
  private static final Set<String> OMIT=Set.of("meta","source_record","source_archive_blob","sqlite_stat1","sqlite_stat4");
  public Receipt export(Path source,Path output,SetupStorage storage,long expectedBytes,String expectedSha256,
      BooleanSupplier cancelled)throws IOException,SQLException {
    Objects.requireNonNull(cancelled);check(cancelled);
    source=storage.verifyDestination(source.toRealPath());output=storage.verifyDestination(output);
    if(!Files.isRegularFile(source)||expectedBytes<1||!expectedSha256.matches("[a-f0-9]{64}"))throw new IOException("Invalid staged source identity");
    if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)||source.equals(output))throw new IOException("Runtime output already exists");
    if(!Files.isDirectory(output.getParent()))throw new IOException("Runtime output parent must exist");
    rejectSidecars(source);
    Path temporary=storage.verifyDestination(Files.createTempFile(storage.verifyDestination(output.getParent()),".runtime-",".sqlite"));
    try {
      try(var in=Files.newInputStream(source);var out=Files.newOutputStream(temporary,StandardOpenOption.TRUNCATE_EXISTING)) {
        var hash=sha();long bytes=0;byte[] block=new byte[1024*1024];int count;
        while((count=in.read(block))!=-1){check(cancelled);bytes+=count;if(bytes>expectedBytes)throw new IOException("Staged source size changed");hash.update(block,0,count);out.write(block,0,count);}
        if(bytes!=expectedBytes||!hex(hash.digest()).equals(expectedSha256))throw new IOException("Staged source identity mismatch");
      }
      rejectSidecars(source);
      if(Files.size(source)!=expectedBytes||!hashFile(source,cancelled).equals(expectedSha256))throw new IOException("Staged source changed during copy");
      Map<String,Digest> retained=new TreeMap<>();Map<String,String> metadata;
      long records,archives;String build;Digest archiveIdentities;
      try(Connection c=readOnly(temporary)) {
        cancellationHandler(c,cancelled);
        validate(c,"quick_check",cancelled);Set<String> tables=tables(c);
        if(!tables.containsAll(Set.of("meta","source_record","source_archive_blob","source_archive_file")))throw new IOException("Unsupported staged database schema");
        metadata=metadata(c);if(metadata.containsKey("release_dataset_profile"))throw new IOException("Source is already a runtime library");
        build=metadata.get("build_id");if(build==null||!build.matches("[a-f0-9]{64}")||!"4".equals(metadata.get("schema_version")))throw new IOException("Staged build identity or schema is invalid");
        archiveIdentities=digestRows(c,"SELECT sha256,length(bytes) FROM source_archive_blob",cancelled);
        records=scalar(c,"SELECT count(*) FROM source_record");archives=scalar(c,"SELECT coalesce(sum(length(bytes)),0) FROM source_archive_blob");
        for(String table:tables)if(!OMIT.contains(table))retained.put(table,digest(c,table,cancelled));
      }
      try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+temporary)) {
        cancellationHandler(c,cancelled);
        execute(c,"PRAGMA foreign_keys=OFF");c.setAutoCommit(false);
        execute(c,"DELETE FROM source_record");execute(c,"DROP INDEX IF EXISTS field_knowledge_assertion_identity");
        execute(c,"CREATE TABLE release_blob_temp(sha256 TEXT PRIMARY KEY,bytes BLOB CHECK(bytes IS NULL),original_size INTEGER NOT NULL,storage_state TEXT NOT NULL CHECK(storage_state='omitted-release'))");
        execute(c,"INSERT INTO release_blob_temp SELECT sha256,NULL,length(bytes),'omitted-release' FROM source_archive_blob");
        execute(c,"DROP TABLE source_archive_blob");execute(c,"ALTER TABLE release_blob_temp RENAME TO source_archive_blob");
        try(var p=c.prepareStatement("INSERT INTO meta(key,value) VALUES(?,?)")) {
          for(var entry:Map.of("release_dataset_profile","runtime-v1","release_dataset_created_at",Instant.now().toString(),"release_raw_records_omitted",Long.toString(records),"release_archive_bytes_omitted",Long.toString(archives),"release_rebuild_supported","false").entrySet()) {
            check(cancelled);p.setString(1,entry.getKey());p.setString(2,entry.getValue());p.executeUpdate();
          }
        }
        check(cancelled);c.commit();c.setAutoCommit(true);execute(c,"VACUUM");check(cancelled);
        validate(c,"integrity_check",cancelled);
        if(scalar(c,"SELECT count(*) FROM source_record")!=0||scalar(c,"SELECT count(*) FROM source_archive_blob WHERE bytes IS NOT NULL")!=0)throw new IOException("Raw source bytes remain");
        if(!archiveIdentities.equals(digestRows(c,"SELECT sha256,original_size FROM source_archive_blob",cancelled)))throw new IOException("Archive identities changed");
        var actual=metadata(c);for(var entry:metadata.entrySet())if(!Objects.equals(actual.get(entry.getKey()),entry.getValue()))throw new IOException("Original metadata changed");
        Set<String> actualTables=tables(c);actualTables.removeAll(OMIT);if(!actualTables.equals(retained.keySet()))throw new IOException("Retained table set changed");
        for(var entry:retained.entrySet())if(!entry.getValue().equals(digest(c,entry.getKey(),cancelled)))throw new IOException("Retained table changed: "+entry.getKey());
      }
      long size=Files.size(temporary);String hash=hashFile(temporary,cancelled);check(cancelled);
      // Both paths are on the same filesystem. A hard link publishes atomically and never replaces.
      storage.verifyDestination(output);Files.createLink(output,temporary);
      return new Receipt(expectedBytes,expectedSha256,size,hash,build,records,archives,retained);
    } catch(SQLException failure) {check(cancelled);throw failure;
    } finally {Files.deleteIfExists(temporary);for(String suffix:List.of("-wal","-shm","-journal"))Files.deleteIfExists(temporary.resolveSibling(temporary.getFileName()+suffix));}
  }
  private static void cancellationHandler(Connection c,BooleanSupplier cancelled)throws SQLException {org.sqlite.ProgressHandler.setHandler(c,1000,new org.sqlite.ProgressHandler(){@Override protected int progress(){return cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()?1:0;}});}
  private static void rejectSidecars(Path path)throws IOException {for(String suffix:List.of("-wal","-shm","-journal"))if(Files.exists(path.resolveSibling(path.getFileName()+suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Staged database must be closed without SQLite sidecars");}
  private static Connection readOnly(Path path)throws SQLException {SQLiteConfig config=new SQLiteConfig();config.setReadOnly(true);return config.createConnection("jdbc:sqlite:"+path);}
  private static void validate(Connection c,String pragma,BooleanSupplier cancelled)throws SQLException,IOException {
    check(cancelled);try(var s=c.createStatement();var r=s.executeQuery("PRAGMA "+pragma)){if(!r.next()||!"ok".equals(r.getString(1))||r.next())throw new IOException("Database integrity check failed");}
    try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IOException("Database foreign keys failed");}check(cancelled);
  }
  private static Set<String> tables(Connection c)throws SQLException {var result=new TreeSet<String>();try(var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")){while(r.next())result.add(r.getString(1));}return result;}
  private static Map<String,String> metadata(Connection c)throws SQLException {var result=new TreeMap<String,String>();try(var s=c.createStatement();var r=s.executeQuery("SELECT key,value FROM meta")){while(r.next())result.put(r.getString(1),r.getString(2));}return result;}
  private static long scalar(Connection c,String sql)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
  private static void execute(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
  private static Digest digest(Connection c,String table,BooleanSupplier cancelled)throws SQLException,IOException {
    return digestRows(c,"SELECT * FROM \""+table.replace("\"","\"\"")+"\"",cancelled);
  }
  private static Digest digestRows(Connection c,String sql,BooleanSupplier cancelled)throws SQLException,IOException {
    BigInteger sum=BigInteger.ZERO,mod=BigInteger.ONE.shiftLeft(256);long rows=0;
    try(var s=c.createStatement();var r=s.executeQuery(sql)) {
      int columns=r.getMetaData().getColumnCount();
      while(r.next()){check(cancelled);var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)) {
        out.writeInt(columns);for(int i=1;i<=columns;i++) {
          Object value=r.getObject(i);if(value==null){out.writeByte(0);continue;}
          if(value instanceof byte[] blob){out.writeByte(1);out.writeInt(blob.length);out.write(blob);}
          else if(value instanceof Number number){if(value instanceof Double||value instanceof Float){out.writeByte(2);out.writeLong(Double.doubleToLongBits(number.doubleValue()));}else{out.writeByte(3);out.writeLong(number.longValue());}}
          else {byte[] text=value.toString().getBytes(StandardCharsets.UTF_8);out.writeByte(4);out.writeInt(text.length);out.write(text);}
        }
      }sum=sum.add(new BigInteger(1,sha().digest(bytes.toByteArray()))).mod(mod);rows++;}
    }
    return new Digest(rows,String.format("%064x",sum));
  }
  private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
  public static String hashFile(Path path,BooleanSupplier cancelled)throws IOException {var hash=sha();try(var in=Files.newInputStream(path)){byte[] bytes=new byte[1024*1024];int count;while((count=in.read(bytes))!=-1){check(cancelled);hash.update(bytes,0,count);}}return hex(hash.digest());}
  private static String hex(byte[] value){return HexFormat.of().formatHex(value);}
  private static void check(BooleanSupplier cancelled){LibraryCandidateValidator.check(cancelled);}
}
