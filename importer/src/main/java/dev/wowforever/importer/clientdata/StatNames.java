package dev.wowforever.importer.clientdata;

import java.util.Map;

/**
 * Item stat ids ({@code ItemModType}, the wowdev wiki's ITEM_MOD table) to tooltip names. Resistances (51 to 56) are
 * the {@value #RESIST} kind, every other stat the {@value #STAT} kind. Forever uses ids beyond this table (83 to 136
 * in build 1.60.1.70425); those are named {@code Stat #n} and counted by {@link ItemValues}.
 */
public final class StatNames {
  public static final String STAT = "stat";
  public static final String RESIST = "resist";

  private static final Map<Integer, String> NAMES = Map.ofEntries(
      Map.entry(0, "Mana"),
      Map.entry(1, "Health"),
      Map.entry(3, "Agility"),
      Map.entry(4, "Strength"),
      Map.entry(5, "Intellect"),
      Map.entry(6, "Spirit"),
      Map.entry(7, "Stamina"),
      Map.entry(12, "Defense Rating"),
      Map.entry(13, "Dodge"),
      Map.entry(14, "Parry"),
      Map.entry(15, "Block"),
      Map.entry(31, "Hit"),
      Map.entry(32, "Crit"),
      Map.entry(35, "Resilience"),
      Map.entry(36, "Haste"),
      Map.entry(37, "Expertise"),
      Map.entry(38, "Attack Power"),
      Map.entry(39, "Ranged Attack Power"),
      Map.entry(41, "Spell Healing"),
      Map.entry(42, "Spell Damage"),
      Map.entry(43, "Mana Regen"),
      Map.entry(44, "Armor Penetration"),
      Map.entry(45, "Spell Power"),
      Map.entry(46, "Health Regen"),
      Map.entry(47, "Spell Penetration"),
      Map.entry(48, "Block Value"),
      Map.entry(49, "Mastery"),
      Map.entry(50, "Bonus Armor"),
      Map.entry(51, "Fire Resistance"),
      Map.entry(52, "Frost Resistance"),
      Map.entry(53, "Holy Resistance"),
      Map.entry(54, "Shadow Resistance"),
      Map.entry(55, "Nature Resistance"),
      Map.entry(56, "Arcane Resistance"));

  private StatNames() {}

  public static boolean isKnown(int statId) {
    return NAMES.containsKey(statId);
  }

  /** The tooltip name, or {@code Stat #n} for an id this table does not know. */
  public static String name(int statId) {
    String n = NAMES.get(statId);
    return n != null ? n : "Stat #" + statId;
  }

  /** {@link #RESIST} for 51 to 56, else {@link #STAT}. */
  public static String kind(int statId) {
    return statId >= 51 && statId <= 56 ? RESIST : STAT;
  }
}
