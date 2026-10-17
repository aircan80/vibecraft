package dev.wowforever.importer.questie;

import java.util.Map;

/**
 * Names of the QuestSort categories a quest's {@code zoneOrSort} points at when it is negative ({@code -id}).
 *
 * <p>The ids are Questie's {@code sortKeys} enum ({@code src/corrections/enum/quests.lua} at the pinned QuestieDB
 * commit, which mirrors QuestSort.dbc); the labels are the in-game category names. Ids outside the table read as
 * {@code "Sort #<id>"}.
 */
public final class QuestSort {
  private static final Map<Long, String> LABELS = Map.ofEntries(
      // general quest categories
      Map.entry(1000L, "Special (Temporary)"),
      Map.entry(367L, "Reputation"),
      Map.entry(344L, "Legendary"),
      Map.entry(284L, "Special"),
      Map.entry(221L, "Treasure Map"),
      Map.entry(23L, "Undercity"),
      Map.entry(1L, "Epic"),
      // classes
      Map.entry(395L, "Monk"),
      Map.entry(372L, "Death Knight"),
      Map.entry(263L, "Druid"),
      Map.entry(262L, "Priest"),
      Map.entry(261L, "Hunter"),
      Map.entry(162L, "Rogue"),
      Map.entry(161L, "Mage"),
      Map.entry(141L, "Paladin"),
      Map.entry(82L, "Shaman"),
      Map.entry(81L, "Warrior"),
      Map.entry(61L, "Warlock"),
      // professions and skills
      Map.entry(398L, "Riding"),
      Map.entry(377L, "Archaeology"),
      Map.entry(373L, "Jewelcrafting"),
      Map.entry(371L, "Inscription"),
      Map.entry(324L, "First Aid"),
      Map.entry(304L, "Cooking"),
      Map.entry(264L, "Tailoring"),
      Map.entry(201L, "Engineering"),
      Map.entry(182L, "Leatherworking"),
      Map.entry(181L, "Alchemy"),
      Map.entry(121L, "Blacksmithing"),
      Map.entry(101L, "Fishing"),
      Map.entry(24L, "Herbalism"),
      // holidays and recurring events
      Map.entry(404L, "Feast of Winter Veil"),
      Map.entry(402L, "Harvest Festival"),
      Map.entry(378L, "Children's Week"),
      Map.entry(376L, "Love is in the Air"),
      Map.entry(375L, "Pilgrim's Bounty"),
      Map.entry(374L, "Noblegarden"),
      Map.entry(370L, "Brewfest"),
      Map.entry(369L, "Midsummer"),
      Map.entry(366L, "Lunar Festival"),
      Map.entry(364L, "Darkmoon Faire"),
      Map.entry(41L, "Day of the Dead"),
      Map.entry(22L, "Seasonal"),
      Map.entry(21L, "Hallow's End"),
      // battlegrounds and world events
      Map.entry(368L, "Invasion"),
      Map.entry(365L, "Ahn'Qiraj War"),
      Map.entry(241L, "Argent Tournament"),
      Map.entry(25L, "Battlegrounds"),
      // later expansions (Questie lists them; no Forever quest is expected to use them)
      Map.entry(381L, "Elemental Bonds"),
      Map.entry(380L, "The Zandalari"),
      Map.entry(379L, "Firelands Invasion"),
      Map.entry(400L, "Proving Grounds"),
      Map.entry(399L, "Brawler's Guild"),
      Map.entry(397L, "Pandaren Campaign"),
      Map.entry(396L, "Landfall"),
      Map.entry(394L, "Pet Battle"),
      Map.entry(392L, "Scenario"),
      Map.entry(391L, "Pandaren Brewmasters"),
      // seasonal realms
      Map.entry(662L, "Titan Reforged"),
      Map.entry(644L, "Blackrock Eruption"),
      Map.entry(641L, "Nightmare Incursions"),
      // WoW: Forever quest categories
      Map.entry(676L, "Night Elf"),
      Map.entry(666L, "Camping"),
      Map.entry(660L, "The High Order"));

  private QuestSort() {}

  /** The label of QuestSort {@code id} (a positive id: {@code -zoneOrSort}); {@code "Sort #<id>"} when unknown. */
  public static String label(long id) {
    String label = LABELS.get(id);
    return label != null ? label : "Sort #" + id;
  }
}
