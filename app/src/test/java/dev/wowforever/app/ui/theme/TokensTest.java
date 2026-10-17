package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class TokensTest {
  static double contrast(Color a, Color b) {
    return Contrast.ratio(a, b);
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
    assertThat(Tokens.LIGHT.accentGold()).as("the rail and the map pins").isEqualTo(new Color(0x9A7419));
    assertThat(contrast(Tokens.LIGHT.accentGold(), Tokens.LIGHT.bgBase())).as("why: too light for small text")
        .isLessThan(4.5);
    assertThat(Tokens.DARK.goldText()).isEqualTo(Tokens.DARK.accentGold());
  }
}
