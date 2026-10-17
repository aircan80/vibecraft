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
  void detailCarriesTheDifficulties() {
    assertThat(repos.instances().detail(1).get().difficulties()).containsExactly("NORMAL");
    assertThat(repos.instances().detail(2).get().difficulties()).containsExactly("NORMAL");
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
  void clientInstanceCarriesItsState() {
    // the fixture's AtlasLoot instances are 1 to 3; the client's Forever dungeons follow in map id order
    var thanes = repos.instances().all().stream().filter(i -> "client:3065".equals(i.key())).findFirst().orElseThrow();
    assertThat(thanes.id()).isEqualTo(7);
    assertThat(thanes.name()).isEqualTo("The Hall of Thanes");
    assertThat(thanes.source()).isEqualTo("client");
    assertThat(thanes.tunedMinLevel()).isEqualTo(13);
    assertThat(thanes.levelMin()).isEqualTo(13);
    assertThat(thanes.levelMax()).isNull();
    assertThat(thanes.bossesState()).isEqualTo("client_named");
    assertThat(thanes.lootState()).isEqualTo("none");
    assertThat(thanes.contentType()).isEqualTo("DUNGEON");
    assertThat(thanes.zoneId()).isEqualTo(16919L);
    assertThat(thanes.zoneName()).isEqualTo("The Hall of Thanes");

    InstanceDetail detail = repos.instances().detail(thanes.id()).orElseThrow();
    assertThat(detail.summary()).isEqualTo(thanes);
    assertThat(detail.bosses()).extracting(b -> b.name()).containsExactly("Faldrim Anvilmar", "Infurnus", "Plunder",
        "Durgen Dirgehammer");
    assertThat(detail.bosses()).allSatisfy(b -> {
      assertThat(b.npcId()).isNull();
      assertThat(b.level()).isNull();
      assertThat(b.extraList()).isFalse();
    });
    assertThat(detail.difficulties()).as("no boss loot").isEmpty();

    var rfc = repos.instances().detail(1).orElseThrow().summary();
    assertThat(rfc.source()).isEqualTo("atlasloot");
    assertThat(rfc.bossesState()).isEqualTo("atlasloot");
    assertThat(rfc.lootState()).isEqualTo("atlasloot");
    assertThat(rfc.tunedMinLevel()).isNull();
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
  void aSkullBossLevelIsFilledFromItsNpc() {
    var mc = repos.instances().detail(3).get().bosses();
    assertThat(mc.get(0).name()).isEqualTo("Lucifron");
    assertThat(mc.get(0).level()).isEqualTo(63); // AtlasLoot's 999, the NPC row's 63
    assertThat(mc).filteredOn(b -> b.name().equals("Magmadar")).singleElement()
        .satisfies(b -> assertThat(b.level()).isEqualTo(999)); // no NPC row: AtlasLoot's value stays
    assertThat(repos.instances().detail(1).get().bosses().get(0).level()).isEqualTo(16); // AtlasLoot's own level
  }

  @Test
  void ragefireStartsWithTaragaman() {
    InstanceDetail rfc = repos.instances().detail(1).get();
    assertThat(rfc.summary().name()).isEqualTo("Ragefire Chasm");
    assertThat(rfc.bosses().get(0).npcId()).isEqualTo(11520L);
    assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).containsExactly(1, 2, 3);
    assertThat(repos.instances().all()).extracting(i -> i.id()).contains(1L);
  }

  @Test
  void bossLootCarriesDisagreementAndSourceCount() {
    assertThat(repos.instances().bossLoot(10)).filteredOn(l -> l.item().id() == 2874).singleElement()
        .satisfies(l -> {
          assertThat(l.disagrees()).isTrue();
          assertThat(l.sourceCount()).isEqualTo(4);
        });
    assertThat(repos.instances().bossLoot(10)).filteredOn(l -> l.item().id() == 5191).singleElement()
        .satisfies(l -> {
          assertThat(l.disagrees()).isFalse();
          assertThat(l.sourceCount()).isEqualTo(1);
          assertThat(l.quality()).as("rated rows keep the client data").isEqualTo(3);
          assertThat(l.iconId()).isEqualTo(135325L);
        });
  }
}
