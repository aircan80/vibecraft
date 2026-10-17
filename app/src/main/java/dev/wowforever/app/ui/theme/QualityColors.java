package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Item quality colours (0 poor, 1 common, 2 uncommon, 3 rare, 4 epic, 5 legendary, 6 artifact, 7 heirloom) and
 * drop-rate band colours, as the UI review's quality table gives them: a text variant readable on the dark theme
 * and one readable on the light theme for tables and titles, and the canonical in-game colour for icon borders and
 * the tooltip card. Qualities outside 0–7 are shown as common.
 */
public final class QualityColors {
  private static final int COMMON = 1;
  private static final Color[] DARK = {
    c(0x9D9D9D), c(0xE4E1D8), c(0x3FE23A), c(0x4A9EF0),
    c(0xB864F5), c(0xFF9A33), c(0xE6CC80), c(0x00CCFF)
  };
  // Heirloom (7) light variant is a darker cyan chosen for contrast on light backgrounds.
  private static final Color[] LIGHT = {
    c(0x6B6B6B), c(0x1C1F24), c(0x137A0E), c(0x0058B0),
    c(0x7E2BC4), c(0xB35A00), c(0x8A6D1E), c(0x0090B8)
  };
  private static final Color[] CANONICAL = {
    c(0x9D9D9D), c(0xFFFFFF), c(0x1EFF00), c(0x0070DD),
    c(0xA335EE), c(0xFF8000), c(0xE6CC80), c(0x00CCFF)
  };
  private static final Color GREEN = c(0x7FBF6A);
  private static final Color YELLOW = c(0xC9B458);
  private static final Color ORANGE = c(0xC98A4E);
  private static final Color RED = c(0xB3625A);

  private QualityColors() {}

  /** The text colour of {@code quality} on the dark ({@code dark}) or light theme. */
  public static Color text(int quality, boolean dark) {
    return (dark ? DARK : LIGHT)[clamp(quality)];
  }

  /** The in-game colour of {@code quality}, for icon borders and the tooltip card (either theme). */
  public static Color canonical(int quality) {
    return CANONICAL[clamp(quality)];
  }

  /** Drop-rate colour; bands include their lower bound (25, 5, 1). */
  public static Color rateBand(double pct) {
    if (pct >= 25.0) return GREEN;
    if (pct >= 5.0) return YELLOW;
    if (pct >= 1.0) return ORANGE;
    return RED;
  }

  private static int clamp(int quality) {
    return quality >= 0 && quality < DARK.length ? quality : COMMON;
  }

  private static Color c(int rgb) {
    return new Color(rgb);
  }
}
