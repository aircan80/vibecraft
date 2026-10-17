package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneMap;
import dev.wowforever.app.model.ZoneSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
    List<QuestPin> pins = zones.questGiversInZone(40);
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
    // Sealed Orders is started by an item, which has no spawn point
    assertThat(pins).flatExtracting(QuestPin::quests).extracting(QuestRef::questId).doesNotContain(900105L);
    // an NPC without a name in the plain fixture, in a zone without a map
    assertThat(repos.zones().questGiversInZone(141)).singleElement().satisfies(p -> {
      assertThat(p.displayName()).isEqualTo("Unknown NPC #2155");
      assertThat(p.quests()).extracting(QuestRef::label, QuestRef::starts)
          .containsExactly(tuple("Bounty: Gnarlpine Furbolg (8)", true), tuple("Bounty: Gnarlpine Furbolg (8)", false));
    });
    assertThat(zones.questGiversInZone(-5)).isEmpty();
  }

  @Test
  void spawnsOfAnNpcOrObject() {
    ZoneRepository zones = SpawnFixtureDb.repositories().zones();
    assertThat(zones.spawnsOf(new EntityRef(EntityKind.NPC, 900004))).extracting(Spawn::zoneId, Spawn::x)
        .containsExactly(tuple(40L, 56.6), tuple(40L, -1.0), tuple(40L, 100.5));
    assertThat(zones.spawnsOf(new EntityRef(EntityKind.OBJECT, 175740))).extracting(Spawn::zoneName)
        .contains("Westfall", "Loch Modan");
    assertThat(zones.spawnsOf(new EntityRef(EntityKind.ITEM, 16305))).isEmpty();
  }
}
