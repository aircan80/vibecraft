package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Parchment's small text against the backgrounds it is drawn on. */
class ParchmentContrastTest {
  private static final Theme P = Tokens.PARCHMENT;

  /** Every colour small text takes in a Parchment table or list. */
  private static Map<String, Color> smallText() {
    Map<String, Color> texts = new LinkedHashMap<>();
    for (int q = 0; q <= 7; q++) texts.put("quality " + q, QualityColors.text(q, false));
    texts.put("text", P.textPrimary());
    texts.put("muted", P.textMuted());
    texts.put("link", P.link());
    texts.put("gold text", P.goldText());
    texts.put("warn", P.warn());
    return texts;
  }

  @Test
  void smallTextOnTableBackgrounds() {
    Map<String, Color> backgrounds = new LinkedHashMap<>();
    backgrounds.put("raised", new Color(0xFBF5E6));
    backgrounds.put("alternate row", new Color(0xF6EEDC));
    backgrounds.put("selection", P.selection());
    assertThat(P.bgRaised()).isEqualTo(backgrounds.get("raised"));
    assertThat(P.altRow()).isEqualTo(backgrounds.get("alternate row"));
    backgrounds.forEach((bgName, bg) -> smallText().forEach((name, fg) ->
        assertThat(Contrast.ratio(fg, bg)).as("%s on %s", name, bgName).isGreaterThanOrEqualTo(4.5)));
  }

  @Test
  void theSelectionIsAColourOfItsOwn() {
    assertThat(P.selection()).isNotEqualTo(P.bgRaised()).isNotEqualTo(P.altRow());
    assertThat(P.selection()).isEqualTo(new Color(0xFDEDB4));
  }

  @Test
  void headingsOnPage() {
    assertThat(Contrast.ratio(new Color(0x7A5E14), P.bgBase())).as("gold text on base").isGreaterThanOrEqualTo(4.5);
    assertThat(P.goldText()).isEqualTo(new Color(0x7A5E14));
    assertThat(Contrast.ratio(P.textMuted(), P.bgBase())).as("muted on base").isGreaterThanOrEqualTo(4.5);
  }

  @Test
  void platesStayNavy() {
    Color navy = new Color(0x0C1424);
    assertThat(P.plate()).isEqualTo(navy);
    assertThat(P.cardBg()).isEqualTo(navy);
    assertThat(P.plate()).isEqualTo(P.cardBg());
  }
}
