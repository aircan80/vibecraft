package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.TooltipLine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void detailListsAllSources() {
    ItemDetail d = repos.items().detail(14149).get();
    assertThat(d.droppedBy()).extracting(s -> s.ref().id()).contains(11520L);
    assertThat(d.droppedBy().get(0).zoneName()).isNotNull();
  }

  @Test
  void unknownNpcInDropsKeepsRow() {
    ItemDetail d = repos.items().detail(190179).get();
    assertThat(d.droppedBy()).singleElement().satisfies(s -> {
      assertThat(s.name()).isNull();
      assertThat(s.displayName()).isEqualTo("Unknown NPC #185333");
    });
  }

  @Test
  void classTreeHasWeaponsAndArmor() {
    assertThat(repos.items().classTree()).extracting(ItemClassNode::name).contains("Weapon", "Armor");
  }

  @Test
  void classTreeChildrenHaveItems() {
    List<ItemClassNode> tree = repos.items().classTree();
    assertThat(tree).allMatch(n -> n.subclassId() == -1);
    for (ItemClassNode cls : tree) {
      for (ItemClassNode sub : cls.children()) {
        assertThat(repos.items().list(cls.classId(), sub.subclassId(), 1, 0)).isNotEmpty();
      }
    }
  }

  @Test
  void sourcesListEveryRateInQuestieThenAtlasLootOrder() {
    assertThat(repos.items().sources(639, 2874)).containsExactly(
        new RateSource("questie-wowhead", 56.07), new RateSource("questie-correction", 60.0),
        new RateSource("atlasloot", 56.07), new RateSource("atlasloot-override", 57.0));
    assertThat(repos.items().sources(11520, 14149)).containsExactly(new RateSource("atlasloot", 33.3));
    assertThat(repos.items().sources(126, 730)).as("a drop without rates").isEmpty();
  }

  @Test
  void sourcesOfManyPairsComeBackByPairLeavingOutPairsWithoutRates() {
    var vanCleef = new RateSource.Key(639, 2874);
    var bag = new RateSource.Key(11520, 14149);
    var none = new RateSource.Key(126, 730);
    assertThat(repos.items().sources(List.of(vanCleef, bag, none)))
        .containsOnlyKeys(vanCleef, bag)
        .hasEntrySatisfying(vanCleef, l -> assertThat(l).hasSize(4).first()
            .isEqualTo(new RateSource("questie-wowhead", 56.07)));
    assertThat(repos.items().sources(List.of())).isEmpty();
  }

  @Test
  void missingItemIsEmpty() {
    assertThat(repos.items().detail(-1)).isEmpty();
  }

  @Test
  void listWithoutALimitReturnsEveryRow() {
    List<ItemSummary> all = repos.items().list(null, null, null, 0);
    assertThat(all).hasSize(repos.items().list(null, null, 1_000_000, 0).size()).hasSizeGreaterThan(2);
    assertThat(repos.items().list(null, null, 2, 0)).hasSize(2);
    assertThat(repos.items().list(null, null, null, 1)).hasSize(all.size() - 1);
  }

  @Test
  void detailListsTheInstanceLootTheItemIsIn() {
    ItemDetail d = repos.items().detail(14149).get();
    assertThat(d.inInstanceLoot()).singleElement().satisfies(l -> {
      assertThat(l.instance()).isEqualTo(new EntityRef(EntityKind.INSTANCE, 1));
      assertThat(l.instanceName()).isEqualTo("Ragefire Chasm");
      assertThat(l.bossName()).isEqualTo("Taragaman the Hungerer");
      assertThat(l.bossNpcId()).isEqualTo(11520L);
      assertThat(l.slot()).isNotNull();
    });
    assertThat(repos.items().detail(190179).get().inInstanceLoot()).isEmpty();
  }

  @Test
  void listFiltersByClassAndOrdersByName() {
    List<ItemSummary> all = repos.items().list(null, null, 500, 0);
    assertThat(all).isNotEmpty();
    List<ItemSummary> armor = repos.items().list(4, null, 500, 0);
    assertThat(armor).isNotEmpty().allMatch(i -> "Armor".equals(i.className()));
    assertThat(armor).extracting(ItemSummary::name).filteredOn(n -> n != null).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    ItemSummary cape = repos.items().detail(14149).get().summary();
    assertThat(cape.sourceCount()).isPositive();
  }

  @Test
  void listWithLevelRange() {
    assertThat(repos.items().list(new ItemFilter(null, null, 10, 20, null))).extracting(ItemSummary::name)
        .containsExactly("An Unsent Letter", "Crystalline Cuffs", "Cursed Felblade", "Murloc Eye", "Subterranean Cape");
    assertThat(repos.items().list(new ItemFilter(4, null, 10, 20, null))).extracting(ItemSummary::name)
        .containsExactly("Crystalline Cuffs", "Subterranean Cape");
    assertThat(repos.items().list(new ItemFilter(null, null, 66, null, null))).extracting(ItemSummary::name)
        .containsExactly("Arcanist Boots", "Thunderfury, Blessed Blade of the Windseeker");
    // the client-data fixture gives item levels to the low quest and consumable items as well
    assertThat(repos.items().list(new ItemFilter(null, null, null, 8, null))).extracting(ItemSummary::name)
        .containsExactly("Avelina's Heart", "Gold Pickup Schedule", "Illari's Key", "Refreshing Spring Water",
            "Smelling Salts", "Tough Jerky", "Worn Shortsword");
    assertThat(repos.items().list(new ItemFilter(4, null, 30, 20, null))).isEmpty();
  }

  @Test
  void listByFilterMatchesTheClassListAndKeepsUnknownLevelsWithoutABound() {
    assertThat(repos.items().list(ItemFilter.ALL)).isEqualTo(repos.items().list(null, null, null, 0));
    assertThat(repos.items().list(new ItemFilter(4, 1, null, null, null)))
        .isEqualTo(repos.items().list(4, 1, null, 0)).isNotEmpty();
    assertThat(repos.items().list(new ItemFilter(2, ItemClassNode.OTHER_SUBCLASSES, null, null, null)))
        .isEqualTo(repos.items().list(2, ItemClassNode.OTHER_SUBCLASSES, null, 0));
    assertThat(repos.items().list(ItemFilter.ALL)).extracting(ItemSummary::itemLevel).containsNull();
  }

  @Test
  void qualityIsNotFilterableYet() {
    assertThatThrownBy(() -> repos.items().list(new ItemFilter(null, null, null, null, 4)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cardCarriesQualityIconAndTooltipLinesInOrdinalOrder() {
    ItemDetail d = repos.items().detail(17204).get();
    assertThat(d.summary().quality()).isEqualTo(5);
    assertThat(d.summary().iconId()).isEqualTo(134124L);
    ItemCard card = d.card();
    assertThat(card.quality()).isEqualTo(5);
    assertThat(card.iconId()).isEqualTo(134124L);
    assertThat(card.inventoryType()).isEqualTo(0);
    assertThat(card.bonding()).isEqualTo(1);
    assertThat(card.sellPrice()).isEqualTo(200000L);
    assertThat(card.lines()).extracting(TooltipLine::kind)
        .containsExactly("name", "binding", "use", "item_level", "sell_price");
    assertThat(card.lines().get(0)).isEqualTo(new TooltipLine("name", "Eye of Sulfuras", "#ff8000"));
    assertThat(card.lines().get(2).text()).startsWith("Use: The Eye of Sulfuras can be combined");
    assertThat(card.lines().get(2).colour()).isEqualTo("#1eff00");
  }

  @Test
  void setItemCardListsTheSetLines() {
    ItemCard card = repos.items().detail(19682).get().card();
    assertThat(card.quality()).isEqualTo(3);
    assertThat(card.inventoryType()).isEqualTo(5);
    assertThat(card.lines()).hasSize(16);
    assertThat(card.lines()).extracting(TooltipLine::kind).contains("set_name", "set_item", "set_bonus");
    assertThat(card.lines()).filteredOn(l -> l.kind().equals("set_name")).singleElement()
        .satisfies(l -> assertThat(l.text()).isEqualTo("Bloodvine Garb (0/3)"));
  }

  @Test
  void itemWithoutClientDataHasNullQuality() {
    ItemDetail d = repos.items().detail(1176).get();
    assertThat(d.summary().name()).isEqualTo("Smelling Salts");
    assertThat(d.summary().quality()).isNull();
    assertThat(d.summary().iconId()).isNull();
    assertThat(d.card()).isEqualTo(ItemCard.none());
    assertThat(d.card().quality()).isNull();
    assertThat(d.card().lines()).isEmpty();
  }

  @Test
  void iconOnlyItemCarriesItsIconOnTheSummaryButHasNoCard() {
    // Illari's Key: a Forever Item row (icon 134236) but no ItemSparse row in either build
    ItemDetail d = repos.items().detail(212347).get();
    assertThat(d.summary().name()).isEqualTo("Illari's Key");
    assertThat(d.summary().quality()).isNull();
    assertThat(d.summary().iconId()).isEqualTo(134236L);
    assertThat(d.card()).isEqualTo(ItemCard.none());
    assertThat(repos.assets().icon(134236L)).isPresent();
  }

  @Test
  void listCarriesQualityAndIcon() {
    List<ItemSummary> all = repos.items().list(null, null, null, 0);
    assertThat(all).filteredOn(i -> i.id() == 19682).singleElement().satisfies(i -> {
      assertThat(i.quality()).isEqualTo(3);
      assertThat(i.iconId()).isEqualTo(132648L);
    });
    assertThat(all).filteredOn(i -> i.id() == 1176).singleElement()
        .satisfies(i -> assertThat(i.quality()).isNull());
  }

  @Test
  void dropSourcesCarryDisagreementAndSourceCount() {
    DropSource drop = repos.items().detail(2874).get().droppedBy().stream()
        .filter(s -> s.ref().id() == 639).findFirst().orElseThrow();
    assertThat(drop.disagrees()).isTrue();
    assertThat(drop.sourceCount()).isEqualTo(4);
    assertThat(repos.items().detail(2874).get().inInstanceLoot()).singleElement().satisfies(l -> {
      assertThat(l.disagrees()).isTrue();
      assertThat(l.sourceCount()).isEqualTo(4);
    });
    assertThat(repos.items().detail(14149).get().inInstanceLoot()).singleElement().satisfies(l -> {
      assertThat(l.disagrees()).isFalse();
      assertThat(l.sourceCount()).isEqualTo(1);
    });
  }
}
