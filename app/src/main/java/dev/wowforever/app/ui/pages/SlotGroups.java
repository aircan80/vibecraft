package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import java.util.List;

/**
 * The slot-type group of a boss's loot on the instance page, from the item's class and subclass: Weapon is
 * Weapons; Armor is Jewellery for subclass 0 (Miscellaneous: rings, necks and trinkets, and some cloaks) and
 * Armour otherwise; Recipe is Recipes; anything else, an unknown class included, is Other.
 */
final class SlotGroups {
  static final String WEAPONS = "Weapons";
  static final String ARMOUR = "Armour";
  static final String JEWELLERY = "Jewellery";
  static final String RECIPES = "Recipes";
  static final String OTHER = "Other";
  /** The groups in page order. */
  static final List<String> ORDER = List.of(WEAPONS, ARMOUR, JEWELLERY, RECIPES, OTHER);

  /** The Armor subclass of rings, necks, trinkets and other non-armour wearables. */
  private static final int ARMOR_MISCELLANEOUS = 0;

  private SlotGroups() {}

  static String of(LootRow loot) {
    if (loot.className() == null) {
      return OTHER;
    }
    return switch (loot.className()) {
      case "Weapon" -> WEAPONS;
      case "Armor" -> Integer.valueOf(ARMOR_MISCELLANEOUS).equals(loot.subclassId()) ? JEWELLERY : ARMOUR;
      case "Recipe" -> RECIPES;
      default -> OTHER;
    };
  }
}
