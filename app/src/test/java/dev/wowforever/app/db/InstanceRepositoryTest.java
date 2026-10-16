package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.LootRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class InstanceRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void bossLootInSlotOrder() {
    InstanceDetail rfc = repos.instances().detail(1).get();
    assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).isSorted();
  }

  @Test
  void difficultiesAreTheDistinctOnesOfTheInstanceBosses() {
    assertThat(repos.instances().difficulties(1)).containsExactly("NORMAL");
    assertThat(repos.instances().difficulties(3)).containsExactly("NORMAL");
    assertThat(repos.instances().difficulties(-1)).isEmpty();
  }

  @Test
  void bossLootOfOneDifficultyAndItsItemClass() {
    long taragaman = repos.instances().detail(1).get().bosses().get(0).id();
    assertThat(repos.instances().bossLoot(taragaman, "NORMAL"))
        .containsExactlyElementsOf(repos.instances().bossLoot(taragaman));
    assertThat(repos.instances().bossLoot(taragaman, "HEROIC")).isEmpty();
    assertThat(repos.instances().bossLoot(taragaman)).filteredOn(l -> l.item().id() == 14149).singleElement()
        .satisfies(l -> {
          assertThat(l.className()).isEqualTo("Armor");
          assertThat(l.subclassId()).isEqualTo(1);
        });
  }

  @Test
  void instanceSummaryNamesItsZone() {
    var rfc = repos.instances().detail(1).get().summary();
    assertThat(rfc.zoneId()).isEqualTo(2437L);
    assertThat(rfc.zoneName()).isEqualTo("Ragefire Chasm");
    assertThat(repos.instances().all()).filteredOn(i -> i.id() == 1).singleElement()
        .satisfies(i -> assertThat(i.zoneName()).isEqualTo("Ragefire Chasm"));
  }

  @Test
  void ragefireStartsWithTaragaman() {
    InstanceDetail rfc = repos.instances().detail(1).get();
    assertThat(rfc.summary().name()).isEqualTo("Ragefire Chasm");
    assertThat(rfc.bosses().get(0).npcId()).isEqualTo(11520L);
    assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).containsExactly(1, 2, 3);
    assertThat(repos.instances().all()).extracting(i -> i.id()).contains(1L);
  }
}
