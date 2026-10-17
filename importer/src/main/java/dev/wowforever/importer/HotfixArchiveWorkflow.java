package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.foundation.FieldKnowledge;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.sqlite.HotfixItemProjection;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Archive a reproducible hotfix input separately, then update only explicitly revealed existing items. */
public final class HotfixArchiveWorkflow {
  public static final String TRANSFORM="blizzard-hotfix-items-v1";
  private HotfixArchiveWorkflow() {}

  public static String ingest(Path database,Path input)throws Exception {
    database=existing(database);input=input.toAbsolutePath().normalize();rejectSymlinks(input);
    var source=new ClientDataSource(input);BuildLock build=source.lock();verifyRaw(input,build);
    List<SourceArchive.Artifact> artifacts=new ArrayList<>();
    try(var files=Files.walk(input)) {
      for(Path file:files.filter(Files::isRegularFile).sorted().toList()) {
        String path=input.relativize(file).toString().replace('\\','/');
        String url=path.equals("raw/DBCache.bin")?"https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin":"";
        artifacts.add(new SourceArchive.Artifact(path,url,Files.readAllBytes(file)));
      }
    }
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      try(Connection c=connect(database)) {
        c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("blizzard-hotfix",build.hotfixSha256(),"forever",build.build(),"enUS"),artifacts);
        c.commit();return snapshot;
      }
    }
  }

  public static void project(Path database,String snapshot)throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      Path work=Files.createTempDirectory(database.getParent(),".hotfix-projection-");
      try {
        Path candidate=work.resolve("candidate.sqlite"),input=work.resolve("input");
        try(Connection c=connect(database)) {
          var metadata=SourceArchive.snapshot(c,snapshot).metadata();
          if(!metadata.source().equals("blizzard-hotfix"))throw new IllegalArgumentException("Expected Blizzard hotfix snapshot");
          SourceArchive.extract(c,snapshot,input);
          BuildLock archivedBuild=new ClientDataSource(input).lock();
          if(!metadata.version().equals(archivedBuild.hotfixSha256()) || !metadata.build().equals(archivedBuild.build()))
            throw new IllegalArgumentException("Hotfix source metadata differs from archived input");
          try(var p=c.prepareStatement("VACUUM INTO ?")){p.setString(1,candidate.toString());p.execute();}
        }
        var source=new ClientDataSource(input);BuildLock build=source.lock();verifyRaw(input,build);
        ImportReport report=new ImportReport();
        Map<Long,String> names=new TreeMap<>();
        Set<Long> itemIds=new TreeSet<>();
        try(Connection c=connect(candidate);var s=c.createStatement();var r=s.executeQuery("SELECT id,name FROM item")) {
          while(r.next()) {
            itemIds.add(r.getLong(1));
            if(r.getString(2)!=null && !r.getString(2).isBlank())names.put(r.getLong(1),r.getString(2));
          }
        }
        var loaded=ItemSparseLoader.load(source,itemIds,report);
        Map<Long,ClientItem> items=new TreeMap<>();loaded.forEach((id,item)->{if(ClientItem.HOTFIX.equals(item.clientSource()))items.put(id,item);});
        var values=ItemValues.compute(items,ScalingTables.load(source),report);
        var tooltips=ItemTooltips.build(source,items,values,names,report);
        try(Connection c=connect(candidate)) {
          c.setAutoCommit(false);
          try(var schema=c.createStatement()){schema.execute("CREATE TABLE IF NOT EXISTS hotfix_projection_report(snapshot_id TEXT PRIMARY KEY,report TEXT NOT NULL)");}
          try(var stored=c.prepareStatement("INSERT OR REPLACE INTO hotfix_projection_report VALUES (?,?)")) {
            stored.setString(1,snapshot);stored.setString(2,report.render());stored.executeUpdate();
          }
          HotfixItemProjection.apply(c,items,values,tooltips,snapshot);
          try(var s=c.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS hotfix_item_fact(snapshot_id TEXT,item_id INTEGER,source_path TEXT,PRIMARY KEY(snapshot_id,item_id))");}
          try(var p=c.prepareStatement("INSERT OR IGNORE INTO hotfix_item_fact VALUES (?,?,'csv/ItemSparse.hotfix.csv')")) {
            for(var item:items.values()) {
              p.setString(1,snapshot);p.setLong(2,item.id());p.addBatch();
              FieldKnowledge.assertEvidence(c,new FieldKnowledge.Evidence("item",Long.toString(item.id()),"stats",FieldKnowledge.State.KNOWN,
                  "blizzard-hotfix",build.build(),snapshot,"csv/ItemSparse.hotfix.csv",null,"Public ItemSparse hotfix row"));
            }
            p.executeBatch();
          }
          put(c,"client_hotfix_sha256",build.hotfixSha256());put(c,"client_hotfix_header_build",String.valueOf(build.hotfixHeaderBuild()));
          var info=source.hotfix().orElseThrow();
          put(c,"client_hotfix_last_modified",info.lastModified());put(c,"client_hotfix_fetched_at",info.fetchedAt());
          put(c,"hotfix_snapshot_id",snapshot);
          FieldKnowledge.rebuildKnownSelections(c);
          AdditiveProjection.record(c,Map.of("hotfix-"+snapshot,snapshot),null,"hotfix",TRANSFORM,
              "Targeted Blizzard hotfix item projection; items="+items.size()+"; beta cache, not live observation");
          c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new SQLException("Hotfix candidate integrity failed");}
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Hotfix candidate foreign keys failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {remove(work);}
    }
  }

  private static void verifyRaw(Path input,BuildLock build)throws Exception {
    if(!"wow_classic_beta".equals(build.product())||!build.build().matches("1\\.60\\.[0-9]+\\.[0-9]+")||build.hotfixSha256()==null||build.hotfixHeaderBuild()==null)
      throw new IllegalArgumentException("Expected pinned Forever hotfix context");
    byte[] bytes=Files.readAllBytes(input.resolve("raw/DBCache.bin"));
    if(!sha(bytes).equals(build.hotfixSha256()))throw new IllegalArgumentException("Raw hotfix cache hash mismatch");
    if(bytes.length<44||bytes[0]!='X'||bytes[1]!='F'||bytes[2]!='T'||bytes[3]!='H'
        ||ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4)!=9
        ||ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(8)!=build.hotfixHeaderBuild())
      throw new IllegalArgumentException("Raw hotfix cache header mismatch");
    var root=(Map<?,?>)MiniJson.parse(Files.readString(input.resolve("build.lock")));
    var entry=(Map<?,?>)root.get("hotfix");var definitions=(Map<?,?>)entry.get("dbd");
    for(String table:List.of("Item","ItemSparse","ItemEffect")) {
      byte[] definition=Files.readAllBytes(input.resolve("definitions/"+table+".dbd"));
      if(!sha(definition).equals(definitions.get(table)))throw new IllegalArgumentException("Hotfix definition hash mismatch: "+table);
    }
    sourceChecks(input,build);
  }
  private static String sha(byte[] bytes)throws Exception {
    return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  private static void sourceChecks(Path input,BuildLock build)throws Exception {
    // Every supporting native CSV retains the exact acquisition pin; no missing row is treated as evidence.
    for(var e:build.tableSha().entrySet()) {
      Path csv=input.resolve("csv/"+e.getKey()+".csv");
      if(!Files.isRegularFile(csv)||!sha(Files.readAllBytes(csv)).equals(e.getValue()))
        throw new IllegalArgumentException("Hotfix supporting CSV missing or changed: "+e.getKey());
    }
  }
  private static void put(Connection c,String key,String value)throws SQLException {try(var p=c.prepareStatement("INSERT OR REPLACE INTO meta VALUES (?,?)")){p.setString(1,key);p.setString(2,value);p.executeUpdate();}}
  private static Path existing(Path p)throws IOException {p=p.toAbsolutePath().normalize();rejectSymlinks(p);if(!Files.isRegularFile(p))throw new IOException("Existing candidate database required");return p;}
  private static Path lockPath(Path database)throws IOException {Path p=database.resolveSibling(database.getFileName()+".lock");if(Files.isSymbolicLink(p))throw new IOException("Symbolic lock refused");return p;}
  private static void rejectSymlinks(Path p)throws IOException {for(Path part=p;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Symbolic source/database refused");if(Files.isDirectory(p))try(var files=Files.walk(p)){if(files.anyMatch(Files::isSymbolicLink))throw new IOException("Symbolic source input refused");}}
  private static Connection connect(Path p)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+p);}
  private static void remove(Path p)throws IOException {try(var files=Files.walk(p)){for(Path f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
}
