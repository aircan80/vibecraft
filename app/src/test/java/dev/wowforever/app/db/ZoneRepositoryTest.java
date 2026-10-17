package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LevelBand;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestPins;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneMap;
import dev.wowforever.app.model.ZoneSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class ZoneRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void zoneSummaryNamesItsParentAndInstancesTheirZone() {
    ZoneSummary deadmines = repos.zones().detail(1581).get().summary();
    assertThat(deadmines.parentId()).isEqualTo(40L);
    assertThat(deadmines.parentName()).isEqualTo("Westfall");
    assertThat(repos.zones().all()).filteredOn(z -> z.id() == 1581).singleElement()
        .satisfies(z -> assertThat(z.parentName()).isEqualTo("Westfall"));
    assertThat(repos.zones().detail(40).get().instances()).filteredOn(i -> i.id() != 0)
        .anySatisfy(i -> assertThat(i.zoneName()).isEqualTo("The Deadmines"));
  }

  @Test
  void zoneDetailListsNpcsAndInstances() {
    ZoneDetail z = repos.zones().detail(40).get();
    assertThat(z.instances()).extracting(i -> i.name()).contains("The Deadmines");
    assertThat(z.subzones()).extracting(ZoneSummary::id).contains(1581L);
  }

  @Test
  void theLevelBandWeighsHostileNpcsByTheirSpawnsAndLeavesOutBossesFriendsAndInstanceNpcs(@TempDir Path tmp)
      throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      StringBuilder npcs = new StringBuilder("insert into npc(id, name, min_level, max_level, rank, zone_id,"
          + " friendly_to) values (97001, 'Gnoll', 11, 12, 0, 40, null), (97002, 'Harvest Golem', 14, 16, 0, 40, null),"
          + " (97003, 'Defias Pillager', 18, 19, 0, 40, null), (97004, 'Guard', 55, 55, 1, 40, 'A'),"
          + " (97005, 'Chicken', 1, 1, 0, 40, 'AH'), (97006, 'Overlord', 60, 60, 3, 40, null),"
          + " (97007, 'Miner', 25, 25, 0, 40, null)");
      st.executeUpdate(npcs.toString());
      StringBuilder spawns = new StringBuilder("insert into npc_spawn(npc_id, zone_id, x, y) values ");
      for (int i = 0; i < 20; i++) {
        spawns.append(i == 0 ? "" : ", ").append("(97001, 40, 50, 50), (97002, 40, 50, 50), (97003, 40, 50, 50),")
            .append(" (97004, 40, 50, 50), (97005, 40, 50, 50)");
      }
      spawns.append(", (97006, 40, 50, 50), (97007, 40, -1, -1)");
      st.executeUpdate(spawns.toString());
    }
    try (Database db = Database.open(copy)) {
      ZoneDetail westfall = Repositories.of(db).zones().detail(40).orElseThrow();
      // the guards (friendly, 55), the chickens (friendly, 1) and the boss (60) are left out; so is the miner,
      // met only inside an instance (-1, -1): the gnolls to the Defias, 11.5 to 18.5, read 10–20
      assertThat(westfall.levelBand()).contains(new LevelBand(10, 20));
      assertThat(westfall.npcs()).extracting(n -> n.id()).contains(97001L, 97004L).doesNotContain(97007L);
      assertThat(westfall.npcs()).filteredOn(n -> n.id() == 97004L).singleElement()
          .satisfies(n -> assertThat(n.friendlyTo()).isEqualTo("A"));
      ZoneDetail deadmines = Repositories.of(db).zones().detail(1581).orElseThrow();
      assertThat(deadmines.npcs()).as("a dungeon keeps the NPCs met inside").extracting(n -> n.id()).contains(639L);
      assertThat(deadmines.levelBand()).as("none met on a map").isEmpty();
    }
  }

  @Test
  void allListsTopLevelFirstAndOnlyZonesWithContent() {
    List<ZoneSummary> all = repos.zones().all();
    assertThat(all).extracting(ZoneSummary::id).contains(40L, 12L, 2437L);
    int firstChild = 0;
    while (firstChild < all.size() && all.get(firstChild).parentId() == null) {
      firstChild++;
    }
    assertThat(all.subList(firstChild, all.size())).allMatch(z -> z.parentId() != null);
    assertThat(all.subList(0, firstChild)).extracting(ZoneSummary::name).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    for (ZoneSummary z : all) {
      ZoneDetail d = repos.zones().detail(z.id()).get();
      assertThat(d.npcs().size() + d.objects().size() + d.quests().size() + d.instances().size() + d.subzones().size())
          .as("zone %s has content", z.id()).isPositive();
    }
  }

  @Test
  void mapJoinsTheZonesUiMap() {
    ZoneMap map = repos.zones().map(40).orElseThrow();
    assertThat(map.uiMapId()).isEqualTo(1436);
    assertThat(map.width()).isEqualTo(1002);
    assertThat(map.height()).isEqualTo(668);
    assertThat(map.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8); // JPEG start-of-image marker
    assertThat(repos.zones().detail(40).get().map())
        .hasValueSatisfying(m -> assertThat(m.uiMapId()).isEqualTo(1436));
  }

  @Test
  void zoneWithoutAMapHasNone() {
    assertThat(repos.zones().map(2437)).isEmpty();
    assertThat(repos.zones().detail(2437).get().map()).isEmpty();
    assertThat(repos.zones().map(-1)).isEmpty();
  }

  @Test
  void npcSpawnsInZoneJoinNamesAndRanksAndSkipPointsWithoutCoordinates() {
    List<SpawnPin> elwynn = repos.zones().npcSpawnsInZone(12, false);
    assertThat(elwynn).filteredOn(p -> p.ref().id() == 448).hasSize(5)
        .allSatisfy(p -> {
          assertThat(p.ref().kind()).isEqualTo(EntityKind.NPC);
          assertThat(p.name()).isEqualTo("Hogger");
          assertThat(p.rank()).isEqualTo(1);
        })
        .first().satisfies(p -> {
          assertThat(p.x()).isEqualTo(25.02);
          assertThat(p.y()).isEqualTo(92.9);
        });
    // Hogger is an elite, not a boss or a rare
    assertThat(repos.zones().npcSpawnsInZone(12, true)).isEmpty();
    // instance spawn points (-1, -1) are not on any map
    assertThat(repos.zones().npcSpawnsInZone(1581, false)).isEmpty();
    assertThat(repos.zones().npcSpawnsInZone(-5, false)).isEmpty();
  }

  @Test
  void bossesAndRaresOnlyKeepsRanksBossRareEliteAndRare() {
    ZoneRepository zones = SpawnFixtureDb.repositories().zones();
    assertThat(zones.npcSpawnsInZone(40, true)).extracting(SpawnPin::name, SpawnPin::rank)
        .containsExactlyInAnyOrder(tuple("Foe Reaper 4000", 2), tuple("Vultros", 4), tuple("Test Boss", 3));
    // Thor's points at -1, -1 and 100.5, 50 are off the map
    assertThat(zones.npcSpawnsInZone(40, false)).extracting(SpawnPin::name)
        .containsExactlyInAnyOrder("Foe Reaper 4000", "Vultros", "Test Boss", "Thor");
  }

  @Test
  void objectSpawnsInZoneJoinNames() {
    List<SpawnPin> elwynn = repos.zones().objectSpawnsInZone(12);
    assertThat(elwynn).allSatisfy(p -> {
      assertThat(p.ref().kind()).isEqualTo(EntityKind.OBJECT);
      assertThat(p.rank()).isNull();
    });
    assertThat(elwynn).filteredOn(p -> p.ref().equals(new EntityRef(EntityKind.OBJECT, 2843))).hasSize(7)
        .allSatisfy(p -> assertThat(p.name()).isEqualTo("Battered Chest"));
    assertThat(elwynn).extracting(SpawnPin::name).contains("Storage Chest", "Library Book");
    assertThat(repos.zones().objectSpawnsInZone(-5)).isEmpty();
  }

  @Test
  void questGiversInZone() {
    ZoneRepository zones = SpawnFixtureDb.repositories().zones();
    EntityRef guardian = new EntityRef(EntityKind.OBJECT, 175740);
    EntityRef thor = new EntityRef(EntityKind.NPC, 900004);
    QuestPins westfall = zones.questGiversInZone(40);
    assertThat(westfall.capped()).isFalse();
    List<QuestPin> pins = westfall.pins();
    // by giver name: the object's two points (its second point at 52.4, 53.1 is the same pin) and Thor's one point
    // on the map (his points at -1, -1 and 100.5, 50 are not)
    assertThat(pins).extracting(QuestPin::giver, QuestPin::giverName, QuestPin::x, QuestPin::y).containsExactly(
        tuple(guardian, "The Last Guardian", 52.4, 53.1), tuple(guardian, "The Last Guardian", 52.5, 53.1),
        tuple(thor, "Thor", 56.6, 52.6));
    assertThat(pins.get(0).quests()).containsExactly(new QuestRef(900101, "The People's Militia", 18, false));
    // both roles, by quest level
    assertThat(pins.get(2).quests()).containsExactly(new QuestRef(900102, "Patrolling Westfall", 14, true),
        new QuestRef(900102, "Patrolling Westfall", 14, false), new QuestRef(900101, "The People's Militia", 18, true));
    assertThat(pins.get(2).starts()).isTrue();
    assertThat(pins.get(0).starts()).isFalse();
    // Sealed Orders is started by items, which have no spawn point, one of them with Thor's id (an NPC spawning here)
    assertThat(pins).flatExtracting(QuestPin::quests).extracting(QuestRef::questId).doesNotContain(900105L);
    // an NPC without a name in the plain fixture, in a zone without a map
    assertThat(repos.zones().questGiversInZone(141).pins()).singleElement().satisfies(p -> {
      assertThat(p.displayName()).isEqualTo("Unknown NPC #2155");
      assertThat(p.quests()).extracting(QuestRef::label, QuestRef::starts)
          .containsExactly(tuple("Bounty: Gnarlpine Furbolg (8)", true), tuple("Bounty: Gnarlpine Furbolg (8)", false));
    });
    assertThat(zones.questGiversInZone(-5).pins()).isEmpty();
  }

  @Test
  void questGiversCutAtTheCapSaySo() {
    ZoneRepository zones = SpawnFixtureDb.repositories().zones();
    // Westfall's giver rows: the Last Guardian's three (two at one point) and Thor's three on the map
    QuestPins all = zones.questGiversInZone(40, 6);
    assertThat(all.capped()).as("exactly at the cap is not cut").isFalse();
    assertThat(all.pins()).hasSize(3);
    QuestPins cut = zones.questGiversInZone(40, 2);
    assertThat(cut.capped()).isTrue();
    assertThat(cut.pins()).extracting(QuestPin::giverName).containsOnly("The Last Guardian");
  }

  @Test
  void spawnsOfAnNpcOrObject() {
    Repositories spawnRepos = SpawnFixtureDb.repositories();
    assertThat(spawnRepos.npcs().spawnsOf(900004)).extracting(Spawn::zoneId, Spawn::x)
        .containsExactly(tuple(40L, 56.6), tuple(40L, -1.0), tuple(40L, 100.5));
    assertThat(spawnRepos.objects().spawnsOf(175740)).extracting(Spawn::zoneName).contains("Westfall", "Loch Modan");
    assertThat(spawnRepos.spawnsOf(new EntityRef(EntityKind.NPC, 900004)))
        .isEqualTo(spawnRepos.npcs().spawnsOf(900004));
    assertThat(spawnRepos.spawnsOf(new EntityRef(EntityKind.ITEM, 900004))).as("items do not spawn")
        .isEmpty();
  }
}
