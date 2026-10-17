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

  @Test
  void slateValues() {
    assertThat(Tokens.DARK.bgBase()).isEqualTo(new Color(0x12161D));
    assertThat(Tokens.DARK.plate()).isEqualTo(new Color(0x0C1424));
    assertThat(Tokens.DARK.altRow()).isEqualTo(new Color(0x1E232B));
    assertThat(Tokens.DARK.hairline().getAlpha()).as("35 % of 255").isEqualTo(89);
    assertThat(Tokens.DARK.hairline().getRGB() & 0xFFFFFF).as("gold").isEqualTo(0xD6B25E);
    assertThat(Tokens.DARK.barEnd()).isEqualTo(Tokens.DARK.accentGold());
  }

  @Test
  void slateSmallTextContrast() {
    Theme t = Tokens.DARK;
    java.util.Map<String, Color> backgrounds = new java.util.LinkedHashMap<>();
    backgrounds.put("bg.base", t.bgBase());
    backgrounds.put("bg.surface", t.bgSurface());
    backgrounds.put("altRow", t.altRow());
    backgrounds.put("selection", t.selection());
    java.util.Map<String, Color> texts = new java.util.LinkedHashMap<>();
    for (int q = 0; q <= 7; q++) texts.put("quality " + q, QualityColors.text(q, true));
    texts.put("text.primary", t.textPrimary());
    texts.put("text.muted", t.textMuted());
    texts.put("link", t.link());
    texts.put("gold", t.goldText());
    backgrounds.forEach((bgName, bg) -> texts.forEach((name, fg) ->
        assertThat(contrast(fg, bg)).as("%s on %s", name, bgName).isGreaterThanOrEqualTo(4.5)));
  }
}
