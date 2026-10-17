package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectorProjectionTest {
  @TempDir Path tmp;
  private String row(String kind,int item) {
    return "{lootKind='"+kind+"',source={type='Creature',id=2,spawnKey='same'},items={{itemID="+item+",link='|Hitem:"+item+":|h[x]|h'}}}";
  }
  private String capture(Path db,String loot,String extra) throws Exception {
    Path file=tmp.resolve("synthetic.lua");
    Files.writeString(file,"WowForeverCollectorDB={schema=1,session={addonVersion='1.1',build='1.60.1',buildNumber='70450',interface=16001,locale='enUS'"+extra+"},loot={"+loot+"},quests={{unknownNumber=1e999,questID=99,future='retained\b\f\u0001'}}}");
    return CollectorArchiveWorkflow.ingest(db,file);
  }
  private void seed(Path db) throws Exception {
    dev.wowforever.importer.sqlite.SqliteWriter.write(db,
        new dev.wowforever.importer.sqlite.ImportModel(java.util.Map.of(),new dev.wowforever.importer.drops.DropRates(),new java.util.LinkedHashMap<>(),java.util.List.of(),new java.util.LinkedHashMap<>()));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      s.execute("INSERT INTO item(id) VALUES(1),(3)");
      s.execute("INSERT INTO npc(id) VALUES(2)");
      s.execute("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES(1,2,35,'vmangos','questie')");
    }
  }

  private String scalar(Path db,String sql)throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}
  }
  @Test void syntheticPresencePreservesRatesAndDeduplicatesPerKind() throws Exception {
    Path db=tmp.resolve("test.sqlite");seed(db);
    String ingestion=capture(db,row("pickpocket",3)+","+row("corpse",1)+","+row("corpse",3)+","+row("unknown",3),"");
    CollectorProjection.project(db,ingestion);
    String build=scalar(db,"SELECT value FROM meta WHERE key='build_id'");
    CollectorProjection.project(db,ingestion);
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(build);
    assertThat(scalar(db,"SELECT drop_pct||':'||origin FROM item_npc_drop WHERE item_id=1")).isEqualTo("35.0:questie");
    assertThat(scalar(db,"SELECT count(*) FROM item_npc_drop")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM collector_fact")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM collector_observation")).isEqualTo("5");
    assertThatThrownBy(()->ArchiveWorkflow.project(db,ingestion,System.out)).hasMessageContaining("collector overlay requires explicit replay");
    assertThat(scalar(db,"SELECT status FROM collector_observation WHERE ordinal=4 AND category='loot'")).isEqualTo("unknown-loot-kind");
    String payload=scalar(db,"SELECT payload_json FROM collector_observation WHERE category='quests'");
    assertThat(dev.wowforever.importer.clientdata.MiniJson.parse(payload).toString()).contains("retained");
  }
  @Test void newPresenceHasNoRateAndMixedBuildStaysRaw() throws Exception {
    Path db=tmp.resolve("test.sqlite");seed(db);
    String ingestion=capture(db,row("corpse",3),"");CollectorProjection.project(db,ingestion);
    assertThat(scalar(db,"SELECT origin FROM item_npc_drop WHERE item_id=3")).isEqualTo("collector");
    assertThat(scalar(db,"SELECT drop_pct FROM item_npc_drop WHERE item_id=3")).isNull();
    ingestion=capture(db,row("corpse",1),",builds={'1.60.0.70000','1.60.1.70450'}");CollectorProjection.project(db,ingestion);
    assertThat(scalar(db,"SELECT count(*) FROM collector_fact")).isEqualTo("1");
    assertThat(scalar(db,"SELECT count(*) FROM source_ingestion_input WHERE ingestion_id=(SELECT value FROM meta WHERE key='source_ingestion_id')")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM source_archive_snapshot")).isEqualTo("2");
  }
  @Test void projectsKnownObjectAndVendorButRetainsUnsafeRowsRaw() throws Exception {
    Path db=tmp.resolve("test.sqlite");seed(db);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var st=c.createStatement()) {
      st.execute("INSERT INTO game_object(id) VALUES(2)");
    }
    String loot=row("corpse",3).replace("Creature","GameObject")+","+
        row("corpse",1).replace("spawnKey='same'","spawnKey='other'").replace("itemID=1","itemID=3")+","+
        row("gather",3)+","+row("fishing",3)+","+row("corpse",3).replace("id=2","id=999");
    capture(db,loot,"");
    Path file=tmp.resolve("synthetic.lua");
    Files.writeString(file,Files.readString(file).replace("quests={{", "vendors={{npc={type='Creature',id=2},items={{itemID=3,link='|Hitem:3:|h[x]|h',price=500,extendedCost=true}}}},quests={{"));
    String ingestion=CollectorArchiveWorkflow.ingest(db,file);CollectorProjection.project(db,ingestion);
    assertThat(scalar(db,"SELECT count(*) FROM item_object_drop")).isEqualTo("1");
    assertThat(scalar(db,"SELECT count(*) FROM item_vendor")).isEqualTo("1");
    assertThat(scalar(db,"SELECT count(*) FROM item_npc_drop")).isEqualTo("1");
    assertThat(scalar(db,"SELECT count(*) FROM collector_fact")).isEqualTo("2");
    assertThat(scalar(db,"SELECT payload_json FROM collector_observation WHERE category='vendors'")).contains("extendedCost","500");
  }
  @Test void bossPresenceRequiresDirectCreatureAndMatchingClientMap() throws Exception {
    Path db=tmp.resolve("test.sqlite");seed(db);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var st=c.createStatement()) {
      st.execute("INSERT INTO instance(id,key,client_map_id) VALUES(10,'client:42',42)");
      st.execute("INSERT INTO boss(id,instance_id,npc_id,is_extra_list,sort_order) VALUES(20,10,2,0,1)");
      st.execute("INSERT INTO npc(id) VALUES(9)");
    }
    String good=row("corpse",3).replace("{lootKind=","{instance={mapID=42},lootKind=");
    String wrongMap=good.replace("mapID=42","mapID=99").replace("spawnKey='same'","spawnKey='wrongmap'");
    String trash=good.replace("id=2","id=9").replace("spawnKey='same'","spawnKey='trash'").replace("{instance=","{encounterID=777,instance=");
    String noContext=row("corpse",1).replace("spawnKey='same'","spawnKey='missing'");
    CollectorProjection.project(db,capture(db,wrongMap+","+good+","+trash+","+noContext,""));
    assertThat(scalar(db,"SELECT count(*) FROM collector_fact WHERE category='boss-drop'")).isEqualTo("1");
    assertThat(scalar(db,"SELECT entity_id FROM collector_fact WHERE category='boss-drop'")).isEqualTo("20");
    assertThat(scalar(db,"SELECT count(*) FROM boss_loot")).isEqualTo("0");
  }
  @Test void malformedTargetRollsBackAllProjectionChanges() throws Exception {
    Path db=tmp.resolve("test.sqlite");
    String ingestion=capture(db,row("corpse",1),"");
    assertThatThrownBy(()->CollectorProjection.project(db,ingestion)).isInstanceOf(SQLException.class);
    assertThat(scalar(db,"SELECT count(*) FROM sqlite_master WHERE name='collector_fact'")).isEqualTo("0");
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("0");
  }
}
