package dev.wowforever.app.model;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.app.nav.ViewTarget;
import org.junit.jupiter.api.Test;

class ListFilterTest {
  @Test
  void eachListKindOpensWithEverything() {
    assertThat(ListFilter.defaultFor(ITEM)).isEqualTo(ItemFilter.ALL)
        .isEqualTo(new ItemFilter(null, null, null, null, null));
    assertThat(ListFilter.defaultFor(NPC)).isEqualTo(NpcFilter.ALL).isEqualTo(new NpcFilter(null, null, null, null));
    assertThat(ListFilter.defaultFor(QUEST)).isEqualTo(QuestFilter.ALL).isEqualTo(new QuestFilter(null, null, null));
    assertThat(ListFilter.defaultFor(ZONE)).isEqualTo(NoFilter.INSTANCE);
    assertThat(ListFilter.defaultFor(INSTANCE)).isEqualTo(NoFilter.INSTANCE);
    assertThatThrownBy(() -> ListFilter.defaultFor(OBJECT)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aListTargetIsAValueOfKindAndFilter() {
    assertThat(new ViewTarget.List(NPC)).isEqualTo(new ViewTarget.List(NPC, NpcFilter.ALL));
    assertThat(new ViewTarget.List(NPC, new NpcFilter(12L, 1, null, null)))
        .isEqualTo(new ViewTarget.List(NPC, new NpcFilter(12L, 1, null, null)))
        .isNotEqualTo(new ViewTarget.List(NPC));
  }

  @Test
  void aFilterMustFitItsKind() {
    assertThatThrownBy(() -> new ViewTarget.List(NPC, ItemFilter.ALL)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ViewTarget.List(ITEM, NoFilter.INSTANCE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ViewTarget.List(OBJECT)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSubclassNeedsItsClass() {
    assertThatThrownBy(() -> new ItemFilter(null, 1, null, null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itemClassLinksOfV01StillOpenTheListWithTheClassPreselected() {
    assertThat(ItemFilter.ofClass(4, ItemClassNode.CLASS_NODE)).isEqualTo(new ItemFilter(4, null, null, null, null));
    assertThat(ItemFilter.ofClass(4, 1)).isEqualTo(new ItemFilter(4, 1, null, null, null));
    assertThat(ItemFilter.ofClass(2, ItemClassNode.OTHER_SUBCLASSES))
        .isEqualTo(new ItemFilter(2, ItemClassNode.OTHER_SUBCLASSES, null, null, null));
    assertThat(ItemFilter.ofClass(ItemFilter.ALL_CLASSES, ItemClassNode.CLASS_NODE)).isEqualTo(ItemFilter.ALL);
    assertThat(ViewTarget.List.itemClass(4, 1)).isEqualTo(new ViewTarget.List(ITEM, ItemFilter.ofClass(4, 1)));
  }

  @Test
  void withersReplaceOnePart() {
    ItemFilter f = new ItemFilter(4, 1, 10, 20, null);
    assertThat(f.withClass(2, null)).isEqualTo(new ItemFilter(2, null, 10, 20, null));
    assertThat(f.withLevels(null, 30)).isEqualTo(new ItemFilter(4, 1, null, 30, null));
    NpcFilter n = new NpcFilter(12L, 1, 5, 9);
    assertThat(n.withZone(null)).isEqualTo(new NpcFilter(null, 1, 5, 9));
    assertThat(n.withRank(3)).isEqualTo(new NpcFilter(12L, 3, 5, 9));
    assertThat(n.withLevels(1, 2)).isEqualTo(new NpcFilter(12L, 1, 1, 2));
    QuestFilter q = new QuestFilter(40L, 5, 9);
    assertThat(q.withZone(12L)).isEqualTo(new QuestFilter(12L, 5, 9));
    assertThat(q.withLevels(null, null)).isEqualTo(new QuestFilter(40L, null, null));
  }
}
