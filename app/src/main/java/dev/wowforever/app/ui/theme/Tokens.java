package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Both colour palettes. The dark one is Slate: its alternate row ({@code #1E232B}) and selection ({@code #29230F})
 * are dark enough that epic text ({@code #B864F5}) stays at least 4.5:1 on them; {@code bg.raised} would give 4.25:1.
 * Slate uses one gold for both shapes and text.
 *
 * <p>The light one is Parchment. Its {@code accent.gold} ({@code #8A6D1E}, for borders, bars and large headings)
 * is about 4.06:1 on base, too light for small text, so its {@code gold.text} is darker ({@code #7A5E14}, 5.06:1).
 * Tables, lists and text fields sit on raised ({@code #FBF5E6}), not surface: on surface ({@code #EADCBB}) poor,
 * uncommon, legendary and gold text fall under 4.5:1, so surface is only for chrome without small quality text. The
 * selection ({@code #FDEDB4}, a pale gold) is picked by hue rather than lightness: poor ({@code #6B6B6B}) needs a
 * background at least as light as the alternate row, and on it every quality, text, muted, link, gold text and warn
 * colour reads at least 4.56:1. The bar runs from gold text to gold, and the plates stay navy.
 */
public final class Tokens {
  public static final Theme DARK =
      new Theme(
          true,
          c(0x12161D), c(0x1B2027), c(0x242A33),
          c(0xE4E1D8), c(0x8E97A5),
          c(0xD6B25E), c(0xD6B25E), c(0x9CC3E6),
          c(0x0C1424), c(0x46587A),
          c(0xE0A83A),
          c(0x0C1424), alpha(0xD6B25E), c(0x8A6D1E), c(0xD6B25E), c(0x1E232B), c(0x29230F));
  public static final Theme PARCHMENT =
      new Theme(
          false,
          c(0xF3E9D2), c(0xEADCBB), c(0xFBF5E6),
          c(0x2B2416), c(0x6B5E45),
          c(0x8A6D1E), c(0x7A5E14), c(0x1F4E8C),
          c(0x0C1424), c(0x46587A),
          c(0x8A5A00),
          c(0x0C1424), alpha(0x8A6D1E), c(0x7A5E14), c(0x8A6D1E), c(0xF6EEDC), c(0xFDEDB4));

  private Tokens() {}

  private static Color c(int rgb) {
    return new Color(rgb);
  }

  /** {@code rgb} at 35 % alpha (89 of 255), the hairline's opacity. */
  private static Color alpha(int rgb) {
    return new Color((89 << 24) | rgb, true);
  }
}
