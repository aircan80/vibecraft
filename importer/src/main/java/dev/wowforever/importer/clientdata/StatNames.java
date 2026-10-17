package dev.wowforever.importer.clientdata;

import java.util.Map;

/**
 * Item stat ids ({@code ItemModType}, the wowdev wiki's ITEM_MOD table) to tooltip names. Resistances (51 to 56) are
 * the {@value #RESIST} kind, every other stat the {@value #STAT} kind.
 *
 * <p>Forever (build 1.60.1.70425) also uses ids 83 to 136, which turn Classic Equip spells into stats. Those named
 * here are the ones whose items carry the matching Equip spell in the Classic Era build (1.15.9.70003): the school
 * spell damage ids 84 to 89; weapon skills 90 (Two-handed Axes, Huge Thorium Battleaxe), 91 (Two-handed Maces,
 * Servomechanic Sledgehammer), 96 (Daggers, Death's Sting) and 103 (Swords); professions 112 (Herbalism), 113 (Mining)
 * and 117 (Fishing, the fishing poles); 119 and 121 (fire and frost spell penetration); 124 (All Resistances, the
 * Obsidian belts); 127, 128 and 131 (attack power against Demons, Undead and Beasts, the slaying weapons); 135 and
 * 136 (spell damage against Demons and Undead, Mark of the Champion and Gloves of Undead Cleansing). The others (83,
 * 92, 98, 114, 115, 132: no Era item has a matching spell) are named {@code Stat #n} and counted by
 * {@link ItemValues}.
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
      Map.entry(56, "Arcane Resistance"),
      Map.entry(84, "Holy Spell Damage"),
      Map.entry(85, "Fire Spell Damage"),
      Map.entry(86, "Nature Spell Damage"),
      Map.entry(87, "Frost Spell Damage"),
      Map.entry(88, "Shadow Spell Damage"),
      Map.entry(89, "Arcane Spell Damage"),
      Map.entry(90, "Two-handed Axes"),
      Map.entry(91, "Two-handed Maces"),
      Map.entry(96, "Daggers"),
      Map.entry(103, "Swords"),
      Map.entry(112, "Herbalism"),
      Map.entry(113, "Mining"),
      Map.entry(117, "Fishing"),
      Map.entry(119, "Fire Spell Penetration"),
      Map.entry(121, "Frost Spell Penetration"),
      Map.entry(124, "All Resistances"),
      Map.entry(127, "Attack Power against Demons"),
      Map.entry(128, "Attack Power against Undead"),
      Map.entry(131, "Attack Power against Beasts"),
      Map.entry(135, "Spell Damage against Demons"),
      Map.entry(136, "Spell Damage against Undead"));

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
