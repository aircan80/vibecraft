package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.NpcSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class NpcRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void detailHasDropsWithPctAndSpawns() {
    NpcDetail d = repos.npcs().detail(639).get();
    assertThat(d.drops()).anyMatch(l -> l.item().id() == 2874 && l.dropPct() == 60.0);
    assertThat(repos.npcs().detail(448).get().spawns()).isNotEmpty();
  }

  @Test
  void lootRowsCarryTheItemQualityAndIcon() {
    NpcDetail d = repos.npcs().detail(639).get();
    assertThat(d.drops()).filteredOn(l -> l.item().id() == 5191).singleElement().satisfies(l -> {
      assertThat(l.quality()).isEqualTo(3);
      assertThat(l.iconId()).isEqualTo(135325L);
    });
    assertThat(d.drops()).filteredOn(l -> l.item().id() == 5193).singleElement().satisfies(l -> {
      assertThat(l.quality()).as("no client data").isNull();
      assertThat(l.iconId()).isNull();
    });
  }

  @Test
  void lootRowsCarryTheItemClass() {
    NpcDetail d = repos.npcs().detail(639).get();
    assertThat(d.drops()).filteredOn(l -> l.item().id() == 5191).singleElement().satisfies(l -> {
      assertThat(l.classId()).isEqualTo(2);
      assertThat(l.className()).isEqualTo("Weapon");
      assertThat(l.subclassId()).isEqualTo(7);
    });
    assertThat(d.drops()).filteredOn(l -> l.item().id() == 2874).singleElement()
        .satisfies(l -> assertThat(l.className()).isEqualTo("Quest"));
    assertThat(d.drops()).filteredOn(l -> l.item().id() == 5193).singleElement().satisfies(l -> {
      assertThat(l.classId()).isNull();
      assertThat(l.className()).isNull();
      assertThat(l.subclassId()).isNull();
    });
  }

  @Test
  void dropRowsCountTheNpcsThatDropTheirItemAndCarryTheClientClassAndSkill(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into item_npc_drop(item_id, npc_id, origin) values (5191, 448, 'questie'),"
          + " (5191, 449, 'questie')");
      st.executeUpdate("update item set allowable_class = 256 where id = 5191");
      st.executeUpdate("insert into item_tooltip_line(item_id, ordinal, kind, text) values"
          + " (5191, 90, 'req_skill', 'Requires Leatherworking (65)')");
    }
    try (Database db = Database.open(copy)) {
      NpcDetail d = Repositories.of(db).npcs().detail(639).orElseThrow();
      assertThat(d.drops()).filteredOn(l -> l.item().id() == 5191).singleElement().satisfies(l -> {
        assertThat(l.dropperCount()).as("VanCleef and the two added").isEqualTo(3);
        assertThat(l.allowableClass()).isEqualTo(256);
        assertThat(l.skillLine()).isEqualTo("Requires Leatherworking (65)");
      });
      assertThat(d.drops()).filteredOn(l -> l.item().id() == 2874).singleElement().satisfies(l -> {
        assertThat(l.dropperCount()).isEqualTo(1);
        assertThat(l.skillLine()).isNull();
      });
    }
  }

  @Test
  void byZoneListsSpawnZoneNpcs() {
    assertThat(repos.npcs().byZone(2437)).extracting(n -> n.id()).contains(11520L);
  }

  @Test
  void npcsByRank() {
    // the AtlasLoot encounters are bosses, whatever Questie's rank (elite, rare elite)
    assertThat(repos.npcs().list(new NpcFilter(null, 3, null, null))).extracting(NpcSummary::name)
        .containsExactly("Edwin VanCleef", "Jergosh the Invoker", "Lucifron", "Ragnaros", "Taragaman the Hungerer");
    assertThat(repos.npcs().list(new NpcFilter(null, 1, null, null))).extracting(NpcSummary::name)
        .containsExactly("Hogger");
    assertThat(repos.npcs().list(new NpcFilter(2437L, 3, null, null))).extracting(NpcSummary::name)
        .containsExactly("Jergosh the Invoker", "Taragaman the Hungerer");
    assertThat(repos.npcs().list(new NpcFilter(12L, 3, null, null))).isEmpty();
  }

  @Test
  void anAtlasLootEncounterIsABossWhateverQuestiesRank(@TempDir Path tmp) throws Exception {
    assertThat(repos.npcs().detail(639).orElseThrow().summary().rank()).as("Questie says rare elite").isEqualTo(3);
    assertThat(repos.npcs().list(new NpcFilter(null, 3, null, null))).extracting(NpcSummary::name)
        .contains("Edwin VanCleef");
    assertThat(repos.npcs().list(new NpcFilter(null, 2, null, null))).extracting(NpcSummary::name)
        .doesNotContain("Edwin VanCleef");
    assertThat(repos.npcs().byZone(1581)).filteredOn(n -> n.id() == 639).singleElement()
        .satisfies(n -> assertThat(n.rank()).isEqualTo(3));
    // an encounter AtlasLoot itself calls rare keeps Questie's rank, as the instance page reads it
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("update boss set special_type = 'rare' where npc_id = 639");
    }
    try (Database db = Database.open(copy)) {
      assertThat(Repositories.of(db).npcs().detail(639).orElseThrow().summary().rank()).isEqualTo(2);
    }
  }

  @Test
  void npcLevelRangeOverlapsTheNpcLevels() {
    assertThat(repos.npcs().list(new NpcFilter(null, null, 15, 25))).extracting(NpcSummary::name)
        .containsExactly("Edwin VanCleef", "Gobbler", "Jergosh the Invoker", "Taragaman the Hungerer");
    assertThat(repos.npcs().list(new NpcFilter(null, null, 60, null))).extracting(NpcSummary::name)
        .containsExactly("Lucifron", "Ragnaros");
    assertThat(repos.npcs().list(new NpcFilter(null, null, null, 11))).extracting(NpcSummary::name)
        .containsExactly("Hogger");
  }

  @Test
  void unfilteredNpcListHasEveryNpcByName() {
    var all = repos.npcs().list(NpcFilter.ALL);
    assertThat(all).hasSize(15);
    assertThat(all).extracting(NpcSummary::name).filteredOn(n -> n != null)
        .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    assertThat(repos.npcs().list(new NpcFilter(2437L, null, null, null)))
        .isEqualTo(repos.npcs().byZone(2437));
  }

  @Test
  void dropRowsCarryDisagreementAndSourceCount() {
    NpcDetail d = repos.npcs().detail(639).get();
    LootRow letter = d.drops().stream().filter(l -> l.item().id() == 2874).findFirst().orElseThrow();
    assertThat(letter.disagrees()).isTrue();
    assertThat(letter.sourceCount()).isEqualTo(4);
    assertThat(d.sells()).allSatisfy(l -> assertThat(l.disagrees()).isFalse());
  }
}
