package dev.wowforever.importer;

import dev.wowforever.importer.fs.SourcePaths;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.sqlite.NativeClientItemProjection;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Complete missing native cards from archived support tables, without replacing any selected client card. */
public final class NativeClientItemCompletionWorkflow {
  public static final String TRANSFORM="native-client-item-completion-v1";
  private NativeClientItemCompletionWorkflow() {}

  public static void project(Path database,String supportSnapshot,String nativeSnapshot)throws Exception {
    database=database.toAbsolutePath().normalize();
    SourcePaths.rejectSymlinks(database);
    if(!Files.isRegularFile(database))throw new IOException("Existing candidate required");
    Path lockPath=database.resolveSibling(database.getFileName()+".lock");
    if(Files.isSymbolicLink(lockPath))throw new IOException("Symbolic lock refused");
    try(var channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      Path work=Files.createTempDirectory(database.getParent(),".native-client-completion-");
      try {
        Path input=work.resolve("context"),candidate=work.resolve("candidate.sqlite");
        String sourcePath,nativeSha,build;
        try(Connection c=connect(database)) {
          var support=SourceArchive.snapshot(c,supportSnapshot).metadata();
          var nativeSource=SourceArchive.snapshot(c,nativeSnapshot).metadata();
          if(!support.source().equals("blizzard-hotfix")||!nativeSource.source().equals(FoundationArchiveWorkflow.SOURCE)
              ||!nativeSource.product().equals("wow_classic_beta")||!support.build().equals(nativeSource.build()))
            throw new IllegalArgumentException("Matching native Forever dataset and archived support context required");
          build=nativeSource.build();
          try(var p=c.prepareStatement("SELECT m.source_path,m.sha256 FROM client_table_manifest m JOIN client_table_dataset d ON d.snapshot_id=m.snapshot_id WHERE m.snapshot_id=? AND m.table_name='ItemSparse' AND d.product='wow_classic_beta' AND d.build=?")) {
            p.setString(1,nativeSnapshot);p.setString(2,build);try(var r=p.executeQuery()) {
              if(!r.next())throw new IllegalArgumentException("Projected native ItemSparse dataset required");
              sourcePath=r.getString(1);nativeSha=r.getString(2);
            }
          }
          try(var p=c.prepareStatement("SELECT 1 FROM source_ingestion_input WHERE ingestion_id=? AND role=? AND snapshot_id=?")) {
            p.setString(1,AdditiveProjection.meta(c,"source_ingestion_id"));p.setString(2,"client-foundation-wow_classic_beta-"+build);p.setString(3,nativeSnapshot);
            try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Native completion requires the active native foundation dataset");}
          }
          SourceArchive.extract(c,supportSnapshot,input);
          if(!sha(Files.readAllBytes(input.resolve("csv/ItemSparse.csv"))).equals(nativeSha))
            throw new IllegalArgumentException("Native ItemSparse and supporting context hashes differ");
          try(var p=c.prepareStatement("VACUUM INTO ?")){p.setString(1,candidate.toString());p.execute();}
        }
        // Only the private working view omits hotfix overlays. Both original archives remain immutable.
        for(String table:List.of("ItemSparse","Item","ItemEffect"))Files.deleteIfExists(input.resolve("csv/"+table+".hotfix.csv"));
        Files.deleteIfExists(input.resolve("hotfix.json"));
        var source=new ClientDataSource(input);BuildLock context=source.lock();
        if(!context.build().equals(build))throw new IllegalArgumentException("Native completion context build differs");
        for(var pin:context.tableSha().entrySet()) {
          if(!sha(Files.readAllBytes(input.resolve("csv/"+pin.getKey()+".csv"))).equals(pin.getValue()))
            throw new IllegalArgumentException("Native supporting CSV hash differs: "+pin.getKey());
        }
        var sparse=source.table("ItemSparse").indexByLong("ID");
        Set<Long> targets=new TreeSet<>();Map<Long,String> names=new TreeMap<>();
        try(Connection c=connect(candidate);var s=c.createStatement();var r=s.executeQuery("SELECT id,name,has_client_data,client_source FROM item")) {
          while(r.next()) {
            long id=r.getLong(1);String name=r.getString(2),selected=r.getString(4);
            if(name!=null&&!name.isBlank())names.put(id,name);
            if(r.getInt(3)==0&&!ClientItem.HOTFIX.equals(selected)&&!ClientItem.ERA.equals(selected)&&sparse.containsKey(id))targets.add(id);
          }
        }
        ImportReport report=new ImportReport();var items=ItemSparseLoader.load(source,targets,report);
        if(items.size()!=targets.size()||items.values().stream().anyMatch(i->!ClientItem.FOREVER.equals(i.clientSource())||!i.hasSparse()))
          throw new IllegalArgumentException("Native completion could not resolve every selected native card");
        var values=ItemValues.compute(items,ScalingTables.load(source),report);
        var tooltips=ItemTooltips.build(source,items,values,names,report);
        report.count("nativeClientCompletion.items",items.size());
        try(Connection c=connect(candidate)) {
          c.setAutoCommit(false);NativeClientItemProjection.apply(c,items,values,tooltips,nativeSnapshot);
          try(var s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS native_client_completion_fact(native_snapshot TEXT,support_snapshot TEXT,item_id INTEGER,source_path TEXT,source_line INTEGER,build TEXT,PRIMARY KEY(native_snapshot,support_snapshot,item_id))");
            s.execute("CREATE TABLE IF NOT EXISTS native_client_completion_report(native_snapshot TEXT,support_snapshot TEXT,report TEXT,PRIMARY KEY(native_snapshot,support_snapshot))");
          }
          try(var p=c.prepareStatement("INSERT OR IGNORE INTO native_client_completion_fact VALUES (?,?,?,?,?,?)")) {
            for(long id:items.keySet()) {
              p.setString(1,nativeSnapshot);p.setString(2,supportSnapshot);p.setLong(3,id);p.setString(4,sourcePath);p.setInt(5,sparse.get(id).line());p.setString(6,build);p.addBatch();
            }
            p.executeBatch();
          }
          try(var p=c.prepareStatement("INSERT OR IGNORE INTO native_client_completion_report VALUES (?,?,?)")) {
            p.setString(1,nativeSnapshot);p.setString(2,supportSnapshot);p.setString(3,report.render());p.executeUpdate();
          }
          AdditiveProjection.record(c,Map.of("native-client-completion-support-"+supportSnapshot,supportSnapshot,
              "native-client-completion-source-"+nativeSnapshot,nativeSnapshot),null,"native_client_completion",TRANSFORM,
              "Missing native Forever client cards completed="+items.size()+"; existing client cards and unrelated facts retained");
          c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new SQLException("Native card integrity failed");}
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Native card foreign keys failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {try(var files=Files.walk(work)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
    }
  }
  private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
  private static Connection connect(Path path)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+path);}
}
