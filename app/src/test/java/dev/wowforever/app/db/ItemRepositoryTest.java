package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSummary;
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
}
