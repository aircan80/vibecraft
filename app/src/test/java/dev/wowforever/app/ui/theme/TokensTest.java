package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class TokensTest {
  static double contrast(Color a, Color b) {
    return Contrast.ratio(a, b);
  }

  @Test
  void goldTextReadsAtFourAndAHalfToOneOnTheBackgroundsItIsDrawnOn() {
    for (Theme t : new Theme[] {Tokens.DARK, Tokens.PARCHMENT}) {
      assertThat(contrast(t.goldText(), t.bgBase())).as("on bg.base, dark " + t.dark()).isGreaterThanOrEqualTo(4.5);
      assertThat(contrast(t.goldText(), t.bgRaised())).as("on bg.raised").isGreaterThanOrEqualTo(4.5);
    }
    assertThat(contrast(Tokens.DARK.goldText(), Tokens.DARK.bgSurface())).as("Slate bg.surface")
        .isGreaterThanOrEqualTo(4.5);
    // Parchment's surface carries no small gold text (the plan measures 4.49:1 there): tables and lists sit on raised
    assertThat(contrast(Tokens.PARCHMENT.goldText(), Tokens.PARCHMENT.bgSurface())).isBetween(4.4, 4.5);
  }

  @Test
  void parchmentDarkensTheGoldForTextOnlyAndSlateKeepsOneGold() {
    assertThat(Tokens.PARCHMENT.goldText()).isEqualTo(new Color(0x7A5E14));
    assertThat(Tokens.PARCHMENT.accentGold()).as("borders, bars, large headings").isEqualTo(new Color(0x8A6D1E));
    assertThat(contrast(Tokens.PARCHMENT.accentGold(), Tokens.PARCHMENT.bgBase())).as("why: too light for small text")
        .isLessThan(4.5);
    assertThat(Tokens.DARK.goldText()).isEqualTo(Tokens.DARK.accentGold());
  }

  @Test
  void parchmentValues() {
    Theme t = Tokens.PARCHMENT;
    assertThat(t.dark()).isFalse();
    assertThat(t.bgBase()).as("base").isEqualTo(new Color(0xF3E9D2));
    assertThat(t.bgSurface()).as("surface").isEqualTo(new Color(0xEADCBB));
    assertThat(t.bgRaised()).as("raised").isEqualTo(new Color(0xFBF5E6));
    assertThat(t.textPrimary()).as("text").isEqualTo(new Color(0x2B2416));
    assertThat(t.textMuted()).as("muted").isEqualTo(new Color(0x6B5E45));
    assertThat(t.link()).as("link").isEqualTo(new Color(0x1F4E8C));
    assertThat(t.accentGold()).as("gold").isEqualTo(new Color(0x8A6D1E));
    assertThat(t.goldText()).as("gold text < 18 pt").isEqualTo(new Color(0x7A5E14));
    assertThat(t.warn()).as("warn").isEqualTo(new Color(0x8A5A00));
    assertThat(t.altRow()).as("table alternate row").isEqualTo(new Color(0xF6EEDC));
    assertThat(t.plate()).as("plate").isEqualTo(new Color(0x0C1424));
    assertThat(t.cardBg()).as("card").isEqualTo(new Color(0x0C1424));
    assertThat(t.hairline().getAlpha()).as("35 % of 255").isEqualTo(89);
    assertThat(t.hairline().getRGB() & 0xFFFFFF).as("gold").isEqualTo(0x8A6D1E);
    assertThat(t.barEnd()).isEqualTo(t.accentGold());
    int[][] qualities = {{0, 0x6B6B6B}, {1, 0x2B2416}, {2, 0x1A7A00}, {3, 0x0057B0}, {4, 0x7B22B8}, {5, 0xA85300},
        {6, 0x7A5E14}, {7, 0x00687F}};
    for (int[] q : qualities) {
      assertThat(QualityColors.text(q[0], false)).as("quality %d", q[0]).isEqualTo(new Color(q[1]));
    }
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
