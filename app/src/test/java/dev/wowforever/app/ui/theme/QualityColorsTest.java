package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class QualityColorsTest {

  @Test
  void darkTextColoursPerQuality() {
    assertThat(QualityColors.text(0, true)).isEqualTo(new Color(0x9D9D9D));
    assertThat(QualityColors.text(1, true)).isEqualTo(new Color(0xE4E1D8));
    assertThat(QualityColors.text(2, true)).isEqualTo(new Color(0x3FE23A));
    assertThat(QualityColors.text(3, true)).isEqualTo(new Color(0x4A9EFF));
    assertThat(QualityColors.text(4, true)).isEqualTo(new Color(0xB861FF));
    assertThat(QualityColors.text(5, true)).isEqualTo(new Color(0xFF9A33));
    assertThat(QualityColors.text(6, true)).isEqualTo(new Color(0xE6CC80));
  }

  @Test
  void lightTextColoursPerQuality() {
    assertThat(QualityColors.text(0, false)).isEqualTo(new Color(0x9D9D9D));
    assertThat(QualityColors.text(1, false)).isEqualTo(new Color(0x1C1F24));
    assertThat(QualityColors.text(2, false)).isEqualTo(new Color(0x137A0E));
    assertThat(QualityColors.text(3, false)).isEqualTo(new Color(0x0058B0));
    assertThat(QualityColors.text(4, false)).isEqualTo(new Color(0x7E2BC4));
    assertThat(QualityColors.text(5, false)).isEqualTo(new Color(0xB35A00));
    assertThat(QualityColors.text(6, false)).isEqualTo(new Color(0x8A6D1E));
  }

  @Test
  void outOfRangeQualitiesAreCommon() {
    for (boolean dark : new boolean[] {true, false}) {
      assertThat(QualityColors.text(-1, dark)).isEqualTo(QualityColors.text(1, dark));
      assertThat(QualityColors.text(7, dark)).isEqualTo(QualityColors.text(1, dark));
      assertThat(QualityColors.text(Integer.MAX_VALUE, dark)).isEqualTo(QualityColors.text(1, dark));
    }
    assertThat(QualityColors.canonical(42)).isEqualTo(QualityColors.canonical(1));
  }

  @Test
  void canonicalIsTheDarkThemeTextColour() {
    for (int q = 0; q <= 6; q++) {
      assertThat(QualityColors.canonical(q)).as("quality %d", q).isEqualTo(QualityColors.text(q, true));
    }
  }
}
