package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ItemCategoriesTest {

  @Test
  void classNamesMapToTheirLootGroup() {
    assertThat(ItemCategories.of("Weapon")).isEqualTo("Gear");
    assertThat(ItemCategories.of("Armor")).isEqualTo("Gear");
    assertThat(ItemCategories.of("Recipe")).isEqualTo("Recipes");
    assertThat(ItemCategories.of("Quest")).isEqualTo("Quest items");
    assertThat(ItemCategories.of("Trade Goods")).isEqualTo("Trade goods");
    assertThat(ItemCategories.of("Reagent")).isEqualTo("Trade goods");
    assertThat(ItemCategories.of("Consumable")).isEqualTo("Consumables");
  }

  @Test
  void anyOtherClassIsJunkAndAnUnknownOneIsOther() {
    assertThat(ItemCategories.of("Container")).isEqualTo("Junk");
    assertThat(ItemCategories.of("Miscellaneous")).isEqualTo("Junk");
    assertThat(ItemCategories.of("Key")).isEqualTo("Junk");
    assertThat(ItemCategories.of(null)).isEqualTo("Other");
  }

  @Test
  void groupsAreShownInAFixedOrder() {
    assertThat(ItemCategories.ORDER)
        .containsExactly("Gear", "Recipes", "Quest items", "Trade goods", "Consumables", "Junk", "Other");
  }
}
