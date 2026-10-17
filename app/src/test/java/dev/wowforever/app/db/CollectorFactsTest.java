package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.widgets.OriginChip;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class CollectorFactsTest {
  @Test
  void repeatedCapturesConfirmExistingEdgesWithoutDuplicatingOrReplacingRates(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    long objectItem;
    long bossId;
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + copy); var s = c.createStatement()) {
      try (var rows = s.executeQuery("SELECT item_id FROM item_object_drop WHERE object_id = 386759 LIMIT 1")) {
        objectItem = rows.getLong(1);
      }
      try (var rows = s.executeQuery("SELECT id FROM boss WHERE npc_id = 639 LIMIT 1")) {
        bossId = rows.getLong(1);
      }
      s.executeUpdate("CREATE TABLE collector_fact(snapshot_id TEXT, category TEXT, ordinal INTEGER,"
          + " item_id INTEGER, entity_type TEXT, entity_id INTEGER,"
          + " PRIMARY KEY(snapshot_id,category,ordinal,item_id,entity_type,entity_id))");
      s.executeUpdate("INSERT INTO collector_fact VALUES ('a','npc-drop',1,2874,'npc',639),"
          + " ('b','npc-drop',1,2874,'npc',639), ('a','object-drop',1," + objectItem + ",'object',386759),"
          + " ('b','object-drop',1," + objectItem + ",'object',386759),"
          + " ('a','vendor',1,5191,'npc',639)");
      s.executeUpdate("INSERT INTO collector_fact VALUES ('a','boss-drop',1,2874,'boss'," + bossId + "),"
          + " ('b','boss-drop',1,2874,'boss'," + bossId + "),"
          + " ('a','boss-drop',2,5191,'boss'," + bossId + ")");
      s.executeUpdate("DELETE FROM boss_loot WHERE boss_id = " + bossId + " AND item_id = 5191");
      s.executeUpdate("INSERT OR IGNORE INTO item_vendor(item_id,npc_id) VALUES(5191,639)");
    }
    try (Database db = Database.open(copy)) {
      var r = Repositories.of(db);
      assertThat(r.instances().bossLoot(bossId, InstanceRepository.OBSERVED_DIFFICULTY))
          .hasSize(2).allSatisfy(l -> {
            assertThat(l.observedInGame()).isTrue();
            assertThat(l.dropPct()).isNull();
            assertThat(l.pctSource()).isNull();
          });
      assertThat(r.instances().bossLoot(bossId, "NORMAL"))
          .allSatisfy(l -> assertThat(l.observedInGame()).isFalse());
      assertThat(r.instances().detail(2).orElseThrow().difficulties())
          .contains(InstanceRepository.OBSERVED_DIFFICULTY);
      assertThat(r.items().detail(5191).orElseThrow().inInstanceLoot())
          .filteredOn(l -> l.bossId() == bossId).singleElement().satisfies(l -> {
            assertThat(l.observedInGame()).isTrue();
            assertThat(l.dropPct()).isNull();
          });
      assertThat(r.items().detail(5191).orElseThrow().soldBy())
          .filteredOn(l -> l.ref().id() == 639).singleElement()
          .satisfies(l -> assertThat(l.observedInGame()).isTrue());
      assertThat(r.npcs().detail(639).orElseThrow().sells())
          .filteredOn(l -> l.item().id() == 5191).singleElement()
          .satisfies(l -> assertThat(l.observedInGame()).isTrue());
      var original = FixtureDb.repositories().items().detail(2874).orElseThrow().droppedBy().getFirst();
      assertThat(r.items().detail(2874).orElseThrow().droppedBy()).singleElement().satisfies(drop -> {
        assertThat(drop.observedInGame()).isTrue();
        assertThat(drop.origin()).isEqualTo(original.origin());
        assertThat(drop.dropPct()).isEqualTo(original.dropPct());
        assertThat(drop.pctSource()).isEqualTo(original.pctSource());
        assertThat(OriginChip.label(drop.displayOrigin())).isEqualTo("Observed in game");
        assertThat(SourceFilter.HIDE_QUESTIE_ONLY.keeps(drop.displayOrigin())).isTrue();
        assertThat(SourceFilter.HIDE_ATLASLOOT_ONLY.keeps(drop.displayOrigin())).isTrue();
      });
      assertThat(r.npcs().detail(639).orElseThrow().drops()).filteredOn(l -> l.item().id() == 2874)
          .singleElement().satisfies(l -> assertThat(l.observedInGame()).isTrue());
      assertThat(r.npcs().detail(639).orElseThrow().drops()).filteredOn(l -> l.item().id() == 5191)
          .allSatisfy(l -> assertThat(l.observedInGame()).isFalse());
      assertThat(r.objects().detail(386759).orElseThrow().contains()).filteredOn(l -> l.item().id() == objectItem)
          .singleElement().satisfies(l -> assertThat(l.observedInGame()).isTrue());
      assertThat(r.items().detail(objectItem).orElseThrow().containedInObjects())
          .filteredOn(l -> l.ref().id() == 386759).singleElement()
          .satisfies(l -> assertThat(l.displayOrigin()).isEqualTo("Observed in game"));
    }
  }

  @Test
  void olderDatabasesNeedNoEvidenceTable() {
    var r = FixtureDb.repositories();
    assertThat(r.items().detail(2874).orElseThrow().droppedBy()).allSatisfy(l -> assertThat(l.observedInGame()).isFalse());
    assertThat(r.npcs().detail(639).orElseThrow().drops()).allSatisfy(l -> assertThat(l.observedInGame()).isFalse());
    assertThat(r.objects().detail(386759).orElseThrow().contains()).allSatisfy(l -> assertThat(l.observedInGame()).isFalse());
  }
}
