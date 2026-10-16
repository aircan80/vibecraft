package dev.wowforever.importer.zones;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZoneNamesTest {
  @Test
  void titleCasesSymbols() {
    assertThat(ZoneNames.fromSymbol("DUN_MOROGH")).isEqualTo("Dun Morogh");
    assertThat(ZoneNames.fromSymbol("THE_HINTERLANDS")).isEqualTo("The Hinterlands");
    assertThat(ZoneNames.fromSymbol("SWAMP_OF_SORROWS")).isEqualTo("Swamp of Sorrows");
    assertThat(ZoneNames.fromSymbol("ZUL_FARRAK")).isEqualTo("Zul'Farrak");
  }

  @Test
  void appliesApostropheOverrides() {
    assertThat(ZoneNames.fromSymbol("ONYXIAS_LAIR")).isEqualTo("Onyxia's Lair");
    assertThat(ZoneNames.fromSymbol("NIGHTS_WEB_HOLLOW")).isEqualTo("Night's Web Hollow");
    assertThat(ZoneNames.fromSymbol("THE_TEMPLE_OF_ATAL_HAKKAR")).isEqualTo("The Temple of Atal'Hakkar");
    assertThat(ZoneNames.fromSymbol("RUINS_OF_AHN_QIRAJ")).isEqualTo("Ruins of Ahn'Qiraj");
  }

  @Test
  void spellsTopLevelZonesFoundInTheRealDataAsWowheadDoes() {
    assertThat(ZoneNames.fromSymbol("BLADES_EDGE_MOUNTAINS")).isEqualTo("Blade's Edge Mountains");
    assertThat(ZoneNames.fromSymbol("ISLE_OF_QUEL_DANAS")).isEqualTo("Isle of Quel'Danas");
    assertThat(ZoneNames.fromSymbol("KUN_LAI_SUMMIT")).isEqualTo("Kun-Lai Summit");
    assertThat(ZoneNames.fromSymbol("STORM_PEAKS")).isEqualTo("The Storm Peaks");
    assertThat(ZoneNames.fromSymbol("VASHJ_IR")).isEqualTo("Vashj'ir");
    assertThat(ZoneNames.fromSymbol("THUNDER_KINGS_CITADEL")).isEqualTo("Thunder King's Citadel");
    assertThat(ZoneNames.fromSymbol("VOLCANOTHS_LAIR")).isEqualTo("Volcanoth's Lair");
    assertThat(ZoneNames.fromSymbol("THE_WIDOWS_WAIL")).isEqualTo("The Widow's Wail");
    assertThat(ZoneNames.fromSymbol("KAJA_MINE")).isEqualTo("Kaja'mine");
    assertThat(ZoneNames.fromSymbol("KAJA_MITE_CAVERN")).isEqualTo("Kaja'mite Cavern");
    assertThat(ZoneNames.fromSymbol("PLAGUELANDS_THE_SCARLET_ENCLAVE")).isEqualTo("Plaguelands: The Scarlet Enclave");
  }

  @Test
  void spellsForeverZonesAsTheForeverDataDoes() {
    assertThat(ZoneNames.fromSymbol("SHEN_DRALAS")).isEqualTo("Shen'dralas");
    assertThat(ZoneNames.fromSymbol("SHEN_DAR_HIGHLANDS")).isEqualTo("Shen'dar Highlands");
    assertThat(ZoneNames.fromSymbol("SHEN_DAR_VILLAGE")).isEqualTo("Shen'dar Village");
    assertThat(ZoneNames.fromSymbol("ROG_MAR")).isEqualTo("Rog'mar");
    assertThat(ZoneNames.fromSymbol("BOLDER_OK")).isEqualTo("Bolder'ok");
    assertThat(ZoneNames.fromSymbol("KROL_DOK_STRONGHOLD")).isEqualTo("Krol'dok Stronghold");
    assertThat(ZoneNames.fromSymbol("CAMP_GEV_REK")).isEqualTo("Camp Gevrek");
    assertThat(ZoneNames.fromSymbol("RUINS_OF_BAN_AETHAL")).isEqualTo("Ruins of Ban'aethal");
    assertThat(ZoneNames.fromSymbol("MOURNINGS_REST")).isEqualTo("Mourning's Rest");
    assertThat(ZoneNames.fromSymbol("POACHERS_DEN")).isEqualTo("Poacher's Den");
    assertThat(ZoneNames.fromSymbol("WHELGARS_EXCAVATION_SITE_FOREVER")).isEqualTo("Whelgar's Excavation Site Forever");
  }

  @Test
  void lowercasesConnectorsExceptFirstWord() {
    assertThat(ZoneNames.fromSymbol("OF_THE_AND")).isEqualTo("Of the and");
    assertThat(ZoneNames.fromSymbol("BATTLE_FOR_GILNEAS")).isEqualTo("Battle for Gilneas");
    assertThat(ZoneNames.fromSymbol("BLOOD_IN_THE_SNOW_SCENARIO")).isEqualTo("Blood in the Snow Scenario");
    assertThat(ZoneNames.fromSymbol("FOR_IN")).isEqualTo("For in");
  }
}
