package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;

class KindChipsTest {

  private static List<String> texts(KindChips chips) {
    return chips.chips().stream().map(AbstractButton::getText).toList();
  }

  @Test
  void labelsInOrderWithoutCounts() {
    KindChips chips = new KindChips();
    assertThat(texts(chips))
        .containsExactly("All", "Items", "NPCs", "Objects", "Quests", "Zones", "Dungeons & Raids");
    assertThat(chips.selected()).isEmpty();
    assertThat(chips.chips().get(0).isSelected()).isTrue();
    assertThat(chips.chips()).noneMatch(AbstractButton::isFocusable);
  }

  @Test
  void countsRenderAndAllIsTheirSum() {
    KindChips chips = new KindChips();
    chips.setCounts(Map.of(ITEM, 1_209, QUEST, 8, OBJECT, 5, NPC, 1, ZONE, 1));
    assertThat(texts(chips)).containsExactly("All 1,224", "Items 1,209", "NPCs 1", "Objects 5", "Quests 8",
        "Zones 1", "Dungeons & Raids 0");
    assertThat(chips.chips().get(1).getAccessibleContext().getAccessibleName()).isEqualTo("Items, 1,209 results");
    chips.clearCounts();
    assertThat(texts(chips))
        .containsExactly("All", "Items", "NPCs", "Objects", "Quests", "Zones", "Dungeons & Raids");
    assertThat(chips.chips().get(1).getAccessibleContext().getAccessibleName()).isEqualTo("Items");
  }

  @Test
  void clickingAChipSelectsItsKindOnce() {
    KindChips chips = new KindChips();
    List<Optional<EntityKind>> fired = new ArrayList<>();
    chips.onSelect(fired::add);
    chips.chips().get(2).doClick();
    chips.chips().get(2).doClick(); // the selected chip stays selected and does not fire again
    chips.chips().get(6).doClick();
    chips.chips().get(0).doClick();
    assertThat(fired).containsExactly(Optional.of(NPC), Optional.of(INSTANCE), Optional.empty());
    assertThat(chips.selected()).isEmpty();
    assertThat(chips.chips().get(2).isSelected()).isFalse();
  }

  @Test
  void selectSetsTheChipWithoutFiring() {
    KindChips chips = new KindChips();
    List<Optional<EntityKind>> fired = new ArrayList<>();
    chips.onSelect(fired::add);
    chips.select(Optional.of(QUEST));
    assertThat(chips.selected()).contains(QUEST);
    assertThat(chips.chips().get(4).isSelected()).isTrue();
    assertThat(fired).isEmpty();
  }

  @Test
  void theChipsWrapToTheWidthTheyAreGiven() {
    KindChips chips = new KindChips();
    chips.setCounts(Map.of(ITEM, 1_209, QUEST, 8));
    int oneRow = chips.getPreferredSize().height;
    int wide = chips.getPreferredSize().width;
    assertThat(chips.heightAt(wide + 50)).as("room for all").isEqualTo(oneRow);
    chips.setWrapWidth(wide / 2);
    assertThat(chips.getPreferredSize().width).isEqualTo(wide / 2);
    assertThat(chips.getPreferredSize().height).as("two or more rows").isGreaterThan(oneRow);
    // laid out at that width, every chip is inside the panel
    chips.setSize(chips.getPreferredSize());
    chips.doLayout();
    assertThat(chips.chips()).allSatisfy(c -> assertThat(c.getY() + c.getHeight()).isLessThanOrEqualTo(
        chips.getHeight()));
    chips.setWrapWidth(0);
    assertThat(chips.getPreferredSize().height).isEqualTo(oneRow);
  }

  @Test
  void theCountsShownCanBeRead() {
    KindChips chips = new KindChips();
    assertThat(chips.count(Optional.of(ITEM))).isEmpty();
    chips.setCounts(Map.of(ITEM, 9, NPC, 2));
    assertThat(chips.count(Optional.of(ITEM))).hasValue(9);
    assertThat(chips.count(Optional.of(ZONE))).hasValue(0);
    assertThat(chips.count(Optional.empty())).hasValue(11);
    chips.clearCounts();
    assertThat(chips.count(Optional.empty())).isEmpty();
  }

  @Test
  void pluralLabels() {
    assertThat(KindChips.label(ITEM)).isEqualTo("Items");
    assertThat(KindChips.label(INSTANCE)).isEqualTo("Dungeons & Raids");
    assertThat(KindChips.label(EntityKind.BOSS)).isEqualTo("Bosses");
  }
}
