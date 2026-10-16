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
  void lowercasesConnectorsExceptFirstWord() {
    assertThat(ZoneNames.fromSymbol("OF_THE_AND")).isEqualTo("Of the and");
  }
}
