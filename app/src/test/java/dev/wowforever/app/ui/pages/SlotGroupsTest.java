package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import org.junit.jupiter.api.Test;

class SlotGroupsTest {

  private static LootRow item(Integer classId, String className, Integer subclassId) {
    return new LootRow(new EntityRef(ITEM, 1), "X", 60, null, "atlasloot", 1, classId, className, subclassId);
  }

  @Test
  void weaponsArmourJewelleryRecipesAndOther() {
    assertThat(SlotGroups.of(item(2, "Weapon", 7))).isEqualTo("Weapons");
    assertThat(SlotGroups.of(item(4, "Armor", 1))).isEqualTo("Armour");
    assertThat(SlotGroups.of(item(4, "Armor", 4))).isEqualTo("Armour");
    assertThat(SlotGroups.of(item(4, "Armor", 0))).as("rings, necks, trinkets, cloaks").isEqualTo("Jewellery");
    assertThat(SlotGroups.of(item(9, "Recipe", 2))).isEqualTo("Recipes");
    assertThat(SlotGroups.of(item(12, "Quest", 0))).isEqualTo("Other");
    assertThat(SlotGroups.of(item(null, null, null))).isEqualTo("Other");
    assertThat(SlotGroups.of(item(4, "Armor", null))).isEqualTo("Armour");
  }

  @Test
  void groupsAreShownInAFixedOrder() {
    assertThat(SlotGroups.ORDER).containsExactly("Weapons", "Armour", "Jewellery", "Recipes", "Other");
  }
}
