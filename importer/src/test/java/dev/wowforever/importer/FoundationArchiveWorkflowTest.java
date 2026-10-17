package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.foundation.ClientDataset;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FoundationArchiveWorkflowTest {
  @TempDir Path temporary;
  Path database()throws Exception {
    Path db=temporary.resolve("candidate.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      ArchiveWorkflow.initialize(c);s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("INSERT INTO meta VALUES ('build_id','baseline'),('source_ingestion_id','prior')");
      s.execute("INSERT INTO source_ingestion VALUES ('prior','before')");
      s.execute("INSERT INTO source_ingestion_input VALUES ('prior','world-map-existing','map-snapshot')");
      s.execute("CREATE TABLE item(id INTEGER PRIMARY KEY,name TEXT,stats TEXT,class_id INTEGER,subclass_id INTEGER,inventory_type INTEGER,icon_id INTEGER,item_set_id INTEGER,has_client_data INTEGER NOT NULL DEFAULT 0)");s.execute("INSERT INTO item(id,name,stats) VALUES (16806,'Felheart Belt','untouched-era-stat')");
    }return db;
  }
  Path acquisition(String build,String product,Map<String,String> files)throws Exception {
    Path dir=temporary.resolve(build);Files.createDirectories(dir);
    var manifest=new ArrayList<Map<String,Object>>();
    for(var e:new TreeMap<>(files).entrySet()) {
      byte[] bytes=e.getValue().getBytes(StandardCharsets.UTF_8);Files.write(dir.resolve(e.getKey()+"."+build+".csv"),bytes);
      manifest.add(Map.of("table",e.getKey(),"sha256",ClientDataset.sha256(bytes),"url","https://example.test/native/"+e.getKey()+"?build="+build));
    }
    Files.writeString(dir.resolve("acquisition-manifest.json"),ClientDataset.json(Map.of("build",build,"product",product,"locale","enUS","files",manifest)));
    return dir;
  }
  static long count(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){return r.getLong(1);}}
  @Test void archivesOriginalsThenReplaysOfflinePreservingCoreAndKnowledgeWithoutBlankOrZeroInference()throws Exception {
    Path db=database();Path dir=acquisition("1.60.1.70450","wow_classic_beta",Map.of(
        "Item","ID,InventoryType,UnknownFutureField\n286978,5,keep-me\n16806,6,\n100,0,zero\n",
        "ItemSparse","ID,StatValue,Display_lang\n100,0,\n"));
    String snapshot=FoundationArchiveWorkflow.ingest(db,dir);
    assertThat(FoundationArchiveWorkflow.ingest(db,dir)).isEqualTo(snapshot);
    try(var stream=Files.walk(dir)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    FoundationArchiveWorkflow.project(db,snapshot);
    String build;
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(4);
      assertThat(count(c,"SELECT count(*) FROM item WHERE id=16806 AND stats='untouched-era-stat'")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM item WHERE id=286978 AND name IS NULL AND inventory_type=5 AND has_client_data=0")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM client_table_row WHERE json_extract(payload,'$.UnknownFutureField')='keep-me'")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM client_table_row WHERE table_name='ItemSparse' AND json_extract(payload,'$.StatValue')='0' AND json_extract(payload,'$.Display_lang')=''")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM field_knowledge WHERE entity='item' AND id IN('286978','16806') AND field='stats' AND state='HIDDEN_BY_BLIZZARD'")).isEqualTo(2);
      assertThat(count(c,"SELECT count(*) FROM field_knowledge WHERE id='100' AND field='stats' AND state='KNOWN'")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM source_ingestion_input WHERE role='world-map-existing' AND snapshot_id='map-snapshot'")).isEqualTo(2);
      build=AdditiveProjection.meta(c,"build_id");assertThat(build).isNotEqualTo("baseline");
    }
    FoundationArchiveWorkflow.project(db,snapshot);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(count(c,"SELECT count(*) FROM field_knowledge_assertion")).isEqualTo(3);
      assertThat(count(c,"SELECT count(*) FROM client_table_row")).isEqualTo(4);
      assertThat(AdditiveProjection.meta(c,"build_id")).isEqualTo(build);
    }
  }
  @Test void missingSparseTableDoesNotEstablishHiddenOrNoneAndEraStatsStaySeparate()throws Exception {
    Path db=database();String forever=FoundationArchiveWorkflow.ingest(db,acquisition("1.60.1.70450","wow_classic_beta",Map.of("Item","ID,InventoryType\n16806,6\n")));
    FoundationArchiveWorkflow.project(db,forever);
    String era=FoundationArchiveWorkflow.ingest(db,acquisition("1.15.9.70003","wow_classic_era",Map.of("ItemSparse","ID,StatValue\n16806,0\n")));
    FoundationArchiveWorkflow.project(db,era);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(count(c,"SELECT count(*) FROM field_knowledge WHERE field='stats'")).isZero();
      assertThat(count(c,"SELECT count(*) FROM field_knowledge WHERE id='16806' AND field='classic_stats' AND state='KNOWN'")).isEqualTo(1);
    }
  }
  @Test void unsupportedProjectionPreservesRawAcquisitionAndAtomicOriginal()throws Exception {
    Path db=database();String snapshot=FoundationArchiveWorkflow.ingest(db,acquisition("1.60.1.70450","wow_classic_beta",Map.of("Item","ID,Name\n1,a\n1,b\n")));
    byte[] before=Files.readAllBytes(db);
    assertThatThrownBy(()->FoundationArchiveWorkflow.project(db,snapshot)).hasMessageContaining("Duplicate native row key");
    assertThat(Files.readAllBytes(db)).isEqualTo(before);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)){assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(3);}
  }
  @Test void rejectsChangedAcquisitionHashBeforeCapturingAnything()throws Exception {
    Path db=database(),dir=acquisition("1.60.1.70450","wow_classic_beta",Map.of("Item","ID\n1\n"));
    Files.writeString(dir.resolve("Item.1.60.1.70450.csv"),"ID\n2\n");
    assertThatThrownBy(()->FoundationArchiveWorkflow.ingest(db,dir)).hasMessageContaining("SHA-256 mismatch");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)){assertThat(count(c,"SELECT count(*) FROM source_archive_snapshot")).isZero();}
  }
}
