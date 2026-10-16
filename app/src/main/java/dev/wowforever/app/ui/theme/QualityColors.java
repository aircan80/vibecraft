package dev.wowforever.app.ui.theme;

import java.awt.Color;

/** Item quality (0 poor to 7 heirloom) text colours and drop-rate band colours. */
public final class QualityColors {
  private static final Color[] DARK = {
    c(0x9D9D9D), c(0xE4E1D8), c(0x3FE23A), c(0x4A9EFF),
    c(0xB861FF), c(0xFF9A33), c(0xE6CC80), c(0x00CCFF)
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

  /** Readable text colour for the quality on the dark or light theme. */
  public static Color text(int quality, boolean dark) {
    return (dark ? DARK : LIGHT)[clamp(quality)];
  }

  /** The in-game colour. */
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

  private static int clamp(int q) {
    return Math.max(0, Math.min(7, q));
  }

  private static Color c(int rgb) {
    return new Color(rgb);
  }
}
