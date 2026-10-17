package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class TokensTest {
  /** WCAG relative luminance of {@code c}. */
  private static double luminance(Color c) {
    double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
    for (int i = 0; i < 3; i++) {
      rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
  }

  /** The WCAG contrast ratio of {@code a} on {@code b}. */
  static double contrast(Color a, Color b) {
    double la = luminance(a);
    double lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  @Test
  void goldTextReadsAtFourAndAHalfToOneOnTheBackgroundsInBothThemes() {
    for (Theme t : new Theme[] {Tokens.DARK, Tokens.LIGHT}) {
      assertThat(contrast(t.goldText(), t.bgBase())).as("on bg.base, dark " + t.dark()).isGreaterThanOrEqualTo(4.5);
      assertThat(contrast(t.goldText(), t.bgSurface())).as("on bg.surface").isGreaterThanOrEqualTo(4.5);
    }
  }

  @Test
  void theLightThemeDarkensTheGoldForTextOnlyAndTheDarkThemeKeepsOneGold() {
    assertThat(Tokens.LIGHT.goldText()).isEqualTo(new Color(0x7A5E14));
    assertThat(Tokens.LIGHT.accentGold()).as("chips and the rail").isEqualTo(new Color(0x9A7419));
    assertThat(contrast(Tokens.LIGHT.accentGold(), Tokens.LIGHT.bgBase())).as("why: too light for small text")
        .isLessThan(4.5);
    assertThat(Tokens.DARK.goldText()).isEqualTo(Tokens.DARK.accentGold());
  }
}
