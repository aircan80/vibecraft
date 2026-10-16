package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassRaceNamesTest {
  private static ClassRaceNames names;

  @BeforeAll
  static void load() {
    names = ClassRaceNames.load(ClientItems.fixture());
  }

  @Test
  void classLineOnlyForAStrictSubsetOfThePlayableClasses() {
    assertThat(names.classes(256)).contains("Classes: Warlock");
    assertThat(names.classes(8 | 16)).contains("Classes: Rogue, Priest");
    // 31296 = Shaman plus retail-only bits (Monk, Demon Hunter, Evoker ...): only Shaman is playable
    assertThat(names.classes(31296)).contains("Classes: Shaman");
    assertThat(names.classes(-1)).isEmpty();
    assertThat(names.classes(0)).isEmpty();
    assertThat(names.classes(null)).isEmpty();
    assertThat(names.classes(32767)).as("all bits").isEmpty();
    assertThat(names.classes(1535)).as("all nine classic classes").isEmpty();
    assertThat(names.classes(32)).as("only a non-playable class (Death Knight)").isEmpty();
  }

  @Test
  void raceLineFromBothMaskHalvesTestedByBit() {
    assertThat(names.races(32L, 0L)).contains("Races: Tauren");
    // Forever's Alliance-wide mask: the low byte is Human, Dwarf, Night Elf, Gnome; bit 32 (the high half's bit 0)
    // is High Order Skyborne
    assertThat(names.races(-1321907123L, 1427461461L))
        .contains("Races: Human, Dwarf, Night Elf, Gnome, High Order Skyborne");
    assertThat(names.races(1309324210L, -1440044374L))
        .contains("Races: Orc, Undead, Tauren, Troll, Windshaper Skyborne");
    // an Era mask: 1101 = Human, Dwarf, Night Elf, Gnome + Draenei (not playable here); no Skyborne bit
    assertThat(names.races(1101L, 0L)).contains("Races: Human, Dwarf, Night Elf, Gnome");
    assertThat(names.races(-1L, -1L)).isEmpty();
    assertThat(names.races(0L, 0L)).isEmpty();
    assertThat(names.races(null, null)).isEqualTo(Optional.empty());
    assertThat(names.races(-1L, null)).as("no high half: classic races all allowed").isEmpty();
  }
}
