package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.NpcSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
  void byZoneListsSpawnZoneNpcs() {
    assertThat(repos.npcs().byZone(2437)).extracting(n -> n.id()).contains(11520L);
  }

  @Test
  void npcsByRank() {
    assertThat(repos.npcs().list(new NpcFilter(null, 3, null, null))).extracting(NpcSummary::name)
        .containsExactly("Lucifron", "Ragnaros");
    assertThat(repos.npcs().list(new NpcFilter(null, 1, null, null))).extracting(NpcSummary::name)
        .containsExactly("Hogger", "Jergosh the Invoker", "Taragaman the Hungerer");
    assertThat(repos.npcs().list(new NpcFilter(2437L, 1, null, null))).extracting(NpcSummary::name)
        .containsExactly("Jergosh the Invoker", "Taragaman the Hungerer");
    assertThat(repos.npcs().list(new NpcFilter(12L, 3, null, null))).isEmpty();
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
