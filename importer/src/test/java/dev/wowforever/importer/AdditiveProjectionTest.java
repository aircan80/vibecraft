package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.wowforever.importer.sqlite.*;
import dev.wowforever.importer.archive.SourceArchive;

class AdditiveProjectionTest {
  @TempDir Path tmp;
  private String scalar(Path db,String query) throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(query)) {
      assertThat(r.next()).isTrue();return r.getString(1);
    }
  }
  @Test void offlineOverlaysComposeInEitherOrderAndReplayWithoutBuildChurn() throws Exception {
    Path base=tmp.resolve("base.sqlite");
    SqliteWriter.write(base,new ImportModel(Map.of(),new dev.wowforever.importer.drops.DropRates(),
        new LinkedHashMap<>(),List.of(),new LinkedHashMap<>()));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+base);var s=c.createStatement()) {
      c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
      String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("test-core","1","forever","1.60.1.70450","enUS"),
          List.of(new SourceArchive.Artifact("core.txt","",new byte[]{1})));
      s.execute("INSERT INTO source_ingestion VALUES ('core','before')");
      try(var p=c.prepareStatement("INSERT INTO source_ingestion_input VALUES ('core','core',?)")) {p.setString(1,snapshot);p.executeUpdate();}
      s.execute("INSERT OR REPLACE INTO meta VALUES ('build_id','original'),('source_ingestion_id','core')");
      s.execute("INSERT INTO item(id) VALUES (1)");s.execute("INSERT INTO npc(id) VALUES (2)");
      s.execute("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES(1,2,35,'vmangos','questie')");
      c.commit();
    }
    Path a=tmp.resolve("a.sqlite"),b=tmp.resolve("b.sqlite");Files.copy(base,a);Files.copy(base,b);
    Path maps=tmp.resolve("UiMap.1.60.1.70374.csv"),assignments=tmp.resolve("UiMapAssignment.1.60.1.70374.csv");
    Files.writeString(maps,"Name_lang,ID,ParentUiMapID,Type\nAzeroth,947,0,1\nEastern Kingdoms,1415,947,2\nWestfall,1436,1415,3\n");
    Files.writeString(assignments,"ID,UiMapID,OrderIndex,MapID,UiMin_0,UiMin_1,UiMax_0,UiMax_1,Region_0,Region_1,Region_3,Region_4\n1,947,0,0,.55,.1,.9,.87,0,0,100,200\n2,1415,0,0,0,0,1,1,0,0,100,200\n3,1436,0,0,0,0,1,1,20,40,40,100\n");
    Path collector=tmp.resolve("synthetic.lua");
    Files.writeString(collector,"WowForeverCollectorDB={schema=1,session={addonVersion='1.1',build='1.60.1',buildNumber='70450',interface=16001,locale='enUS'},loot={{lootKind='corpse',source={type='Creature',id=2,spawnKey='same'},items={{itemID=1,link='|Hitem:1:|h[x]|h'}}}}}");
    String mapA=WorldMapArchiveWorkflow.ingest(a,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    String captureA=CollectorArchiveWorkflow.ingest(a,collector);
    WorldMapArchiveWorkflow.project(a,mapA);CollectorProjection.project(a,captureA);
    String expected=scalar(a,"SELECT value FROM meta WHERE key='build_id'");
    WorldMapArchiveWorkflow.project(a,mapA);CollectorProjection.project(a,captureA);
    assertThat(scalar(a,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(expected);
    String captureB=CollectorArchiveWorkflow.ingest(b,collector);
    String mapB=WorldMapArchiveWorkflow.ingest(b,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    CollectorProjection.project(b,captureB);WorldMapArchiveWorkflow.project(b,mapB);
    assertThat(scalar(b,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(expected);
    assertThat(scalar(a,"SELECT value FROM meta WHERE key='source_ingestion_id'"))
        .isEqualTo(scalar(b,"SELECT value FROM meta WHERE key='source_ingestion_id'"));
    assertThat(scalar(a,"SELECT count(*) FROM source_ingestion_input WHERE ingestion_id=(SELECT value FROM meta WHERE key='source_ingestion_id')")).isEqualTo("3");
    assertThat(scalar(a,"SELECT value FROM meta WHERE key='source_projection_id'"))
        .isEqualTo(scalar(a,"SELECT max(id) FROM source_projection"));
    assertThat(scalar(a,"SELECT drop_pct||':'||origin FROM item_npc_drop")).isEqualTo("35.0:questie");
    Files.writeString(maps,Files.readString(maps).replace("Westfall","Westfall revised"));
    String next=WorldMapArchiveWorkflow.ingest(a,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    WorldMapArchiveWorkflow.project(a,next);
    assertThat(scalar(a,"SELECT count(*) FROM source_ingestion_input WHERE role LIKE 'world-map-%' AND ingestion_id=(SELECT value FROM meta WHERE key='source_ingestion_id')")).isEqualTo("1");
    assertThat(scalar(a,"SELECT count(*) FROM collector_fact")).isEqualTo("1");
    assertThat(scalar(a,"SELECT count(*) FROM world_map_projection")).isEqualTo("2");
  }
}
