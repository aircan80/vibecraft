package dev.wowforever.importer.sqlite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Static cmangos item class and subclass names; every entry is written whether or not an item uses it. */
public final class ItemClasses {
  private static final Map<Integer, String> CLASSES = names(
      0, "Consumable", 1, "Container", 2, "Weapon", 3, "Gem", 4, "Armor", 5, "Reagent", 6, "Projectile",
      7, "Trade Goods", 8, "Generic", 9, "Recipe", 10, "Money", 11, "Quiver", 12, "Quest", 13, "Key",
      14, "Permanent", 15, "Miscellaneous");

  private static final Map<Integer, Map<Integer, String>> SUBCLASSES = subclassTable();

  private ItemClasses() {}

  /** Class id to name, in id order. */
  public static Map<Integer, String> classes() {
    return CLASSES;
  }

  /** Class id to (subclass id to name), in id order; classes without named subclasses are absent. */
  public static Map<Integer, Map<Integer, String>> subclasses() {
    return SUBCLASSES;
  }

  private static Map<Integer, Map<Integer, String>> subclassTable() {
    Map<Integer, Map<Integer, String>> m = new LinkedHashMap<>();
    m.put(0, names(0, "Consumable", 1, "Potion", 2, "Elixir", 3, "Flask", 4, "Scroll", 5, "Food & Drink",
        6, "Item Enhancement", 7, "Bandage", 8, "Other"));
    m.put(1, names(0, "Bag", 1, "Soul Bag", 2, "Herb Bag", 3, "Enchanting Bag", 4, "Engineering Bag"));
    m.put(2, names(0, "One-Handed Axe", 1, "Two-Handed Axe", 2, "Bow", 3, "Gun", 4, "One-Handed Mace",
        5, "Two-Handed Mace", 6, "Polearm", 7, "One-Handed Sword", 8, "Two-Handed Sword", 10, "Staff",
        13, "Fist Weapon", 14, "Miscellaneous", 15, "Dagger", 16, "Thrown", 17, "Spear", 18, "Crossbow",
        19, "Wand", 20, "Fishing Pole"));
    m.put(3, names(0, "Gem"));
    m.put(4, names(0, "Miscellaneous", 1, "Cloth", 2, "Leather", 3, "Mail", 4, "Plate", 6, "Shield",
        7, "Libram", 8, "Idol", 9, "Totem"));
    m.put(5, names(0, "Reagent"));
    m.put(6, names(2, "Arrow", 3, "Bullet"));
    m.put(7, names(0, "Trade Goods", 1, "Parts", 2, "Explosives", 3, "Devices"));
    m.put(9, names(0, "Book", 1, "Leatherworking", 2, "Tailoring", 3, "Engineering", 4, "Blacksmithing",
        5, "Cooking", 6, "Alchemy", 7, "First Aid", 8, "Enchanting", 9, "Fishing"));
    m.put(10, names(0, "Money"));
    m.put(11, names(2, "Quiver", 3, "Ammo Pouch"));
    m.put(12, names(0, "Quest"));
    m.put(13, names(0, "Key", 1, "Lockpick"));
    m.put(15, names(0, "Junk", 1, "Reagent", 2, "Pet", 3, "Holiday", 4, "Other", 5, "Mount"));
    return Collections.unmodifiableMap(m);
  }

  /** Alternating id, name pairs to an ordered, unmodifiable map. */
  private static Map<Integer, String> names(Object... idNamePairs) {
    Map<Integer, String> m = new LinkedHashMap<>();
    for (int i = 0; i < idNamePairs.length; i += 2) {
      m.put((Integer) idNamePairs[i], (String) idNamePairs[i + 1]);
    }
    return Collections.unmodifiableMap(m);
  }
}
