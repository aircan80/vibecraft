package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSummary;
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
