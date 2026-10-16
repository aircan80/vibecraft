package dev.wowforever.app.ui.pages;

import java.util.List;

/**
 * The loot group of an item on the NPC page, from its class name ({@code item_class.name}): Weapon and Armor are
 * Gear, Recipe is Recipes, Quest is Quest items, Trade Goods and Reagent are Trade goods, Consumable is
 * Consumables, any other class is Junk, and an unknown class is Other.
 */
final class ItemCategories {
  static final String GEAR = "Gear";
  static final String RECIPES = "Recipes";
  static final String QUEST_ITEMS = "Quest items";
  static final String TRADE_GOODS = "Trade goods";
  static final String CONSUMABLES = "Consumables";
  static final String JUNK = "Junk";
  static final String OTHER = "Other";
  /** The groups in page order. */
  static final List<String> ORDER = List.of(GEAR, RECIPES, QUEST_ITEMS, TRADE_GOODS, CONSUMABLES, JUNK, OTHER);

  private ItemCategories() {}

  /** The group of an item of class {@code className}; {@link #OTHER} for null. */
  static String of(String className) {
    if (className == null) {
      return OTHER;
    }
    return switch (className) {
      case "Weapon", "Armor" -> GEAR;
      case "Recipe" -> RECIPES;
      case "Quest" -> QUEST_ITEMS;
      case "Trade Goods", "Reagent" -> TRADE_GOODS;
      case "Consumable" -> CONSUMABLES;
      default -> JUNK;
    };
  }
}
