package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class QualityColorsTest {
  @Test
  void everyQualityHasColours() {
    for (int q = 0; q <= 7; q++) {
      assertNotNull(QualityColors.text(q, true));
      assertNotNull(QualityColors.text(q, false));
      assertNotNull(QualityColors.canonical(q));
    }
    assertEquals(new Color(0x4A9EFF), QualityColors.text(3, true));
    assertEquals(new Color(0x137A0E), QualityColors.text(2, false));
    assertEquals(new Color(0xA335EE), QualityColors.canonical(4));
    assertEquals(new Color(0x00CCFF), QualityColors.canonical(7));
  }

  @Test
  void bandThresholdsAreInclusiveAtLowerBound() {
    Color green = new Color(0x7FBF6A);
    Color yellow = new Color(0xC9B458);
    Color orange = new Color(0xC98A4E);
    Color red = new Color(0xB3625A);
    assertEquals(green, QualityColors.rateBand(25.0));
    assertEquals(green, QualityColors.rateBand(80));
    assertEquals(yellow, QualityColors.rateBand(24.99));
    assertEquals(yellow, QualityColors.rateBand(5.0));
    assertEquals(orange, QualityColors.rateBand(4.99));
    assertEquals(orange, QualityColors.rateBand(1.0));
    assertEquals(red, QualityColors.rateBand(0.99));
    assertEquals(red, QualityColors.rateBand(0.5));
  }

  @Test
  void darkTextColoursPerQuality() {
    assertThat(QualityColors.text(0, true)).isEqualTo(new Color(0x9D9D9D));
    assertThat(QualityColors.text(1, true)).isEqualTo(new Color(0xE4E1D8));
    assertThat(QualityColors.text(2, true)).isEqualTo(new Color(0x3FE23A));
    assertThat(QualityColors.text(3, true)).isEqualTo(new Color(0x4A9EFF));
    assertThat(QualityColors.text(4, true)).isEqualTo(new Color(0xB861FF));
    assertThat(QualityColors.text(5, true)).isEqualTo(new Color(0xFF9A33));
    assertThat(QualityColors.text(6, true)).isEqualTo(new Color(0xE6CC80));
    assertThat(QualityColors.text(7, true)).isEqualTo(new Color(0x00CCFF));
  }

  @Test
  void lightTextColoursPerQuality() {
    assertThat(QualityColors.text(0, false)).isEqualTo(new Color(0x6B6B6B));
    assertThat(QualityColors.text(1, false)).isEqualTo(new Color(0x1C1F24));
    assertThat(QualityColors.text(2, false)).isEqualTo(new Color(0x137A0E));
    assertThat(QualityColors.text(3, false)).isEqualTo(new Color(0x0058B0));
    assertThat(QualityColors.text(4, false)).isEqualTo(new Color(0x7E2BC4));
    assertThat(QualityColors.text(5, false)).isEqualTo(new Color(0xB35A00));
    assertThat(QualityColors.text(6, false)).isEqualTo(new Color(0x8A6D1E));
    assertThat(QualityColors.text(7, false)).isEqualTo(new Color(0x0090B8));
  }

  @Test
  void outOfRangeQualitiesAreCommon() {
    for (boolean dark : new boolean[] {true, false}) {
      assertThat(QualityColors.text(-1, dark)).isEqualTo(QualityColors.text(1, dark));
      assertThat(QualityColors.text(8, dark)).isEqualTo(QualityColors.text(1, dark));
      assertThat(QualityColors.text(Integer.MAX_VALUE, dark)).isEqualTo(QualityColors.text(1, dark));
    }
    assertThat(QualityColors.canonical(42)).isEqualTo(QualityColors.canonical(1));
    assertThat(QualityColors.canonical(-1)).isEqualTo(QualityColors.canonical(1));
  }

  @Test
  void canonicalIsTheInGameColour() {
    int[] inGame = {0x9D9D9D, 0xFFFFFF, 0x1EFF00, 0x0070DD, 0xA335EE, 0xFF8000, 0xE6CC80, 0x00CCFF};
    for (int q = 0; q < inGame.length; q++) {
      assertThat(QualityColors.canonical(q)).as("quality %d", q).isEqualTo(new Color(inGame[q]));
    }
  }
}
