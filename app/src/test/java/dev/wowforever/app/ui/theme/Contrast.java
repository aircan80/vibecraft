package dev.wowforever.app.ui.theme;

import java.awt.Color;

/** The WCAG 2 contrast ratio, for tests of text colours. */
public final class Contrast {
  private Contrast() {}

  /** The WCAG relative luminance of {@code c}. */
  static double luminance(Color c) {
    double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
    for (int i = 0; i < 3; i++) {
      rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
  }

  /** The WCAG contrast ratio of {@code a} on {@code b} (1 to 21). */
  public static double ratio(Color a, Color b) {
    double la = luminance(a);
    double lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }
}
