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
  void resistancesAreTheirOwnKind() {
    for (int id = 51; id <= 56; id++) {
      assertThat(StatNames.kind(id)).as("stat %d", id).isEqualTo(StatNames.RESIST);
    }
    assertThat(StatNames.kind(7)).isEqualTo(StatNames.STAT);
    assertThat(StatNames.kind(50)).isEqualTo(StatNames.STAT);
  }

  @Test
  void unknownIdsGetAPlaceholder() {
    assertThat(StatNames.isKnown(87)).isFalse();
    assertThat(StatNames.name(87)).isEqualTo("Stat #87");
    assertThat(StatNames.isKnown(2)).as("2 is unused by the client").isFalse();
  }
}
