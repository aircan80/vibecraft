package dev.wowforever.importer.questie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestSortTest {
  @Test
  void negativeZoneOrSortBecomesASortLabel() {
    // QuestSort ids (-zoneOrSort): a class, a profession, events, Epic and Forever's own 676; then an unknown id
    assertThat(QuestSort.label(81)).isEqualTo("Warrior");
    assertThat(QuestSort.label(201)).isEqualTo("Engineering");
    assertThat(QuestSort.label(364)).isEqualTo("Darkmoon Faire");
    assertThat(QuestSort.label(22)).isEqualTo("Seasonal");
    assertThat(QuestSort.label(25)).isEqualTo("Battlegrounds");
    assertThat(QuestSort.label(1)).isEqualTo("Epic");
    assertThat(QuestSort.label(676)).isEqualTo("Night Elf");
    assertThat(QuestSort.label(12345)).isEqualTo("Sort #12345");
  }
}
