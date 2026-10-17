package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatTextTest {
  private static List<String> texts(ItemStat... stats) {
    return StatText.lines(List.of(stats)).stream().map(l -> l.kind() + " " + l.text()).toList();
  }

  @Test
  void schoolSpellDamageIsAnEquipLine() {
    assertThat(texts(new ItemStat(87, 12))).containsExactly(
        "equip Equip: Increases damage done by Frost spells and effects by up to 12.");
  }

  @Test
  void foreverStatsThatReplaceClassicEquipSpellsUseTheEraSpellWording() {
    // the Era Equip spells of the same items name these ids (Strong Fishing Pole, Herbalist's Gloves, Goblin Mining
    // Helmet, Huge Thorium Battleaxe, Servomechanic Sledgehammer, Death's Sting, the slaying weapons, the Obsidian
    // belts, Gloves of Undead Cleansing, the Mark of the Champion and the Spell Penetration test items)
    assertThat(texts(new ItemStat(117, 5), new ItemStat(112, 5), new ItemStat(113, 5), new ItemStat(90, 2),
        new ItemStat(91, 1), new ItemStat(96, 3), new ItemStat(103, 4), new ItemStat(124, 5), new ItemStat(127, 15),
        new ItemStat(128, 30), new ItemStat(131, 15), new ItemStat(135, 85), new ItemStat(136, 18),
        new ItemStat(119, 10), new ItemStat(121, 10))).containsExactly(
        "equip Equip: Increased Two-handed Axes +2.",
        "equip Equip: Increased Two-handed Maces +1.",
        "equip Equip: Increased Daggers +3.",
        "equip Equip: Increased Swords +4.",
        "equip Equip: Herbalism +5.",
        "equip Equip: Mining +5.",
        "equip Equip: Increased Fishing +5.",
        "equip Equip: Decreases the fire resistance of your spell targets by 10.",
        "equip Equip: Decreases the frost resistance of your spell targets by 10.",
        "equip Equip: +5 All Resistances.",
        "equip Equip: +15 Attack Power against Demons.",
        "equip Equip: +30 Attack Power against Undead.",
        "equip Equip: +15 Attack Power against Beasts.",
        "equip Equip: Increases damage done to Demons by magical spells and effects by up to 85.",
        "equip Equip: Increases damage done to Undead by magical spells and effects by up to 18.");
  }

  @Test
  void idsNoClientTableNamesStayPlaceholders() {
    assertThat(texts(new ItemStat(7, 8), new ItemStat(92, 1))).containsExactly("stat +8 Stamina", "stat +1 Stat #92");
  }

  @Test
  void everyRenderedIdIsKnownSoOnlyPlaceholdersAreCountedUnknown() {
    for (int id = 0; id <= 140; id++) {
      String text = StatText.lines(List.of(new ItemStat(id, 1))).getFirst().text();
      assertThat(StatNames.isKnown(id)).as("stat %d renders %s", id, text).isEqualTo(!text.contains("Stat #"));
    }
  }
}
