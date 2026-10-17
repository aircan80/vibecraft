package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatNamesTest {
  @Test
  void namesTheItemModTypes() {
    assertThat(StatNames.name(0)).isEqualTo("Mana");
    assertThat(StatNames.name(3)).isEqualTo("Agility");
    assertThat(StatNames.name(7)).isEqualTo("Stamina");
    assertThat(StatNames.name(32)).isEqualTo("Crit");
    assertThat(StatNames.name(39)).isEqualTo("Ranged Attack Power");
    assertThat(StatNames.name(45)).isEqualTo("Spell Power");
    assertThat(StatNames.name(50)).isEqualTo("Bonus Armor");
    assertThat(StatNames.name(56)).isEqualTo("Arcane Resistance");
  }

  @Test
  void namesTheForeverIdsTheClassicEraNames() {
    assertThat(StatNames.name(84)).isEqualTo("Holy Spell Damage");
    assertThat(StatNames.name(89)).isEqualTo("Arcane Spell Damage");
    assertThat(StatNames.name(117)).isEqualTo("Fishing");
    assertThat(StatNames.name(124)).isEqualTo("All Resistances");
    assertThat(StatNames.name(131)).isEqualTo("Attack Power against Beasts");
    assertThat(StatNames.kind(124)).as("rendered as an Equip line, not a school resistance").isEqualTo(StatNames.STAT);
    for (int id : new int[] {84, 85, 86, 87, 88, 89, 90, 91, 96, 103, 112, 113, 117, 119, 121, 124, 127, 128, 131,
        135, 136}) {
      assertThat(StatNames.isKnown(id)).as("stat %d", id).isTrue();
    }
    for (int id : new int[] {83, 92, 98, 114, 115, 132}) {
      assertThat(StatNames.isKnown(id)).as("stat %d: no Era item names it", id).isFalse();
    }
  }

  @Test
  void resistancesAreTheirOwnKind() {
    for (int id = 51; id <= 56; id++) {
      assertThat(StatNames.kind(id)).as("stat %d", id).isEqualTo(StatNames.RESIST);
    }
    assertThat(StatNames.kind(7)).isEqualTo(StatNames.STAT);
    assertThat(StatNames.kind(50)).isEqualTo(StatNames.STAT);
  }

  @Test
  void unknownIdsGetAPlaceholder() {
    assertThat(StatNames.isKnown(92)).isFalse();
    assertThat(StatNames.name(92)).isEqualTo("Stat #92");
    assertThat(StatNames.isKnown(2)).as("2 is unused by the client").isFalse();
  }
}
