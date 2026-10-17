package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import java.util.List;
import java.util.Set;

/**
 * The loot group of an item on the NPC page, from its class name ({@code item_class.name}): Weapon and Armor are
 * Gear, Recipe is Recipes, Quest is Quest items, Trade Goods and Reagent are Trade goods, Consumable is
 * Consumables, any other class is Junk, and an unknown class is Other. On the NPC page the generic-pool drops that
 * many NPCs share, at an unknown or low rate, go to World drops instead ({@link #ofDrop}).
 */
final class ItemCategories {
  static final String GEAR = "Gear";
  static final String RECIPES = "Recipes";
  static final String QUEST_ITEMS = "Quest items";
  static final String TRADE_GOODS = "Trade goods";
  static final String CONSUMABLES = "Consumables";
  static final String JUNK = "Junk";
  static final String OTHER = "Other";
  /**
   * The generic pool many NPCs drop (recipes, trade goods, food), last and collapsed. Its low rates are mostly VMaNGOS
   * estimates of a shared reference table, so a low known rate does not take a drop out of the pool; a rate of
   * {@value #WORLD_DROP_MAX_PCT} % or more does.
   */
  static final String WORLD_DROPS = "World drops";
  /** The groups in page order. */
  static final List<String> ORDER = List.of(GEAR, RECIPES, QUEST_ITEMS, TRADE_GOODS, CONSUMABLES, JUNK, OTHER);
  /** The NPC page's groups in page order: {@link #ORDER}, then {@link #WORLD_DROPS}. */
  static final List<String> DROP_ORDER = List.of(GEAR, RECIPES, QUEST_ITEMS, TRADE_GOODS, CONSUMABLES, JUNK, OTHER,
      WORLD_DROPS);
  /** How many NPCs must drop an item for it to count as a world drop. */
  static final int WORLD_DROP_NPCS = 20;
  /**
   * The rate, in percent, at or above which a shared pool drop stays in its class group. Low rates are mostly VMaNGOS
   * estimates of a shared reference table; a high rate comes from the creature's own loot table or from Wowhead
   * observations, and marks a drop worth farming (Murloc Eye, the meats).
   */
  static final double WORLD_DROP_MAX_PCT = 10.0;

  private ItemCategories() {}

  /**
   * The item classes of the generic pool many NPCs drop (recipes, trade goods and reagents, food and drink, junk).
   * Quest items, gear and bags are never world drops, however many NPCs drop them: they are what an NPC is looked up
   * for.
   */
  static final Set<String> WORLD_POOL_CLASSES =
      Set.of("Recipe", "Trade Goods", "Reagent", "Consumable", "Miscellaneous");

  /**
   * The NPC page group of a drop: {@link #WORLD_DROPS} for an item of a {@linkplain #WORLD_POOL_CLASSES generic-pool
   * class} that at least {@value #WORLD_DROP_NPCS} NPCs drop, when its rate is unknown or below
   * {@value #WORLD_DROP_MAX_PCT} %, else the group of its class ({@link #of(String)}). A high-rate shared drop, such
   * as a murloc's Murloc Eye, stays in its class group: it is what the NPC is farmed for.
   */
  static String ofDrop(LootRow drop) {
    boolean world = drop.dropperCount() >= WORLD_DROP_NPCS && WORLD_POOL_CLASSES.contains(drop.className())
        && (drop.dropPct() == null || drop.dropPct() < WORLD_DROP_MAX_PCT);
    return world ? WORLD_DROPS : of(drop.className());
  }

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
