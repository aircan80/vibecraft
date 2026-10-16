package dev.wowforever.importer.zones;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns QuestieDB zone enum symbols into display names matching Wowhead spelling. */
public final class ZoneNames {
  private static final Set<String> LOWERCASE = Set.of("of", "the", "and", "for", "in");

  /** Symbols whose proper spelling needs apostrophes or other special handling. */
  private static final Map<String, String> OVERRIDES = Map.ofEntries(
      Map.entry("THE_TEMPLE_OF_ATAL_HAKKAR", "The Temple of Atal'Hakkar"),
      Map.entry("ZUL_FARRAK", "Zul'Farrak"),
      Map.entry("ZUL_GURUB", "Zul'Gurub"),
      Map.entry("ZUL_AMAN", "Zul'Aman"),
      Map.entry("ZUL_DRAK", "Zul'Drak"),
      Map.entry("AHN_QIRAJ", "Ahn'Qiraj"),
      Map.entry("RUINS_OF_AHN_QIRAJ", "Ruins of Ahn'Qiraj"),
      Map.entry("TEMPLE_OF_AHN_QIRAJ", "Temple of Ahn'Qiraj"),
      Map.entry("ONYXIAS_LAIR", "Onyxia's Lair"),
      Map.entry("NIGHTS_WEB_HOLLOW", "Night's Web Hollow"),
      Map.entry("BLACKFATHOM_DEEPS", "Blackfathom Deeps"),
      Map.entry("STORMWIND_CITY", "Stormwind City"),
      Map.entry("UN_GORO_CRATER", "Un'Goro Crater"),
      Map.entry("KELP_THAR_FOREST", "Kelp'thar Forest"),
      Map.entry("MAGTHERIDONS_LAIR", "Magtheridon's Lair"),
      Map.entry("GRUULS_LAIR", "Gruul's Lair"),
      Map.entry("HADRONOXS_LAIR", "Hadronox's Lair"),
      Map.entry("CHAMPIONS_HALL", "Champions' Hall"),
      Map.entry("MAGISTERS_TERRACE", "Magisters' Terrace"),
      Map.entry("HROTHGARS_LANDING", "Hrothgar's Landing"),
      // Top-level zones of the real QuestieDB Forever data, spelled as on Wowhead.
      Map.entry("BLADES_EDGE_MOUNTAINS", "Blade's Edge Mountains"),
      Map.entry("ISLE_OF_QUEL_DANAS", "Isle of Quel'Danas"),
      Map.entry("KUN_LAI_SUMMIT", "Kun-Lai Summit"),
      Map.entry("STORM_PEAKS", "The Storm Peaks"),
      Map.entry("VASHJ_IR", "Vashj'ir"),
      Map.entry("THUNDER_KINGS_CITADEL", "Thunder King's Citadel"),
      Map.entry("VOLCANOTHS_LAIR", "Volcanoth's Lair"),
      Map.entry("THE_WIDOWS_WAIL", "The Widow's Wail"),
      Map.entry("KAJA_MINE", "Kaja'mine"),
      Map.entry("KAJA_MITE_CAVERN", "Kaja'mite Cavern"),
      Map.entry("PLAGUELANDS_THE_SCARLET_ENCLAVE", "Plaguelands: The Scarlet Enclave"),
      // Forever-only zones (not on Wowhead), spelled as the Forever quest and NPC text spells them.
      Map.entry("SHEN_DRALAS", "Shen'dralas"),
      Map.entry("SHEN_DAR_HIGHLANDS", "Shen'dar Highlands"),
      Map.entry("SHEN_DAR_VILLAGE", "Shen'dar Village"),
      Map.entry("ROG_MAR", "Rog'mar"),
      Map.entry("BOLDER_OK", "Bolder'ok"),
      Map.entry("KROL_DOK_STRONGHOLD", "Krol'dok Stronghold"),
      Map.entry("CAMP_GEV_REK", "Camp Gevrek"),
      Map.entry("RUINS_OF_BAN_AETHAL", "Ruins of Ban'aethal"),
      Map.entry("MOURNINGS_REST", "Mourning's Rest"),
      Map.entry("POACHERS_DEN", "Poacher's Den"),
      Map.entry("WHELGARS_EXCAVATION_SITE_FOREVER", "Whelgar's Excavation Site Forever"));

  private ZoneNames() {}

  public static String fromSymbol(String symbol) {
    String override = OVERRIDES.get(symbol);
    if (override != null) {
      return override;
    }
    StringBuilder out = new StringBuilder();
    for (String word : symbol.split("_")) {
      if (word.isEmpty()) {
        continue;
      }
      String lower = word.toLowerCase(Locale.ROOT);
      if (out.length() > 0) {
        out.append(' ');
      }
      if (out.length() > 0 && LOWERCASE.contains(lower)) {
        out.append(lower);
      } else {
        out.append(Character.toUpperCase(lower.charAt(0))).append(lower, 1, lower.length());
      }
    }
    return out.toString();
  }
}
