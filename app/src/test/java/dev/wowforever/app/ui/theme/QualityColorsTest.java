package dev.wowforever.app.ui.theme;

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
}
