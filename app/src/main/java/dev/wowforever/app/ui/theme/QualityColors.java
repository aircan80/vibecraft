package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Item quality colours for text: 0 poor, 1 common, 2 uncommon, 3 rare, 4 epic, 5 legendary, 6 artifact. Each
 * quality has a variant readable on the dark theme and one readable on the light theme; qualities outside 0–6
 * are shown as common.
 */
public final class QualityColors {
  private static final int COMMON = 1;
  private static final Color[] DARK = {
      new Color(0x9D9D9D), new Color(0xE4E1D8), new Color(0x3FE23A), new Color(0x4A9EFF), new Color(0xB861FF),
      new Color(0xFF9A33), new Color(0xE6CC80)};
  private static final Color[] LIGHT = {
      new Color(0x9D9D9D), new Color(0x1C1F24), new Color(0x137A0E), new Color(0x0058B0), new Color(0x7E2BC4),
      new Color(0xB35A00), new Color(0x8A6D1E)};

  private QualityColors() {}

  /** The text colour of {@code quality} on the dark ({@code dark}) or light theme. */
  public static Color text(int quality, boolean dark) {
    return (dark ? DARK : LIGHT)[clamp(quality)];
  }

  /** The theme-independent colour of {@code quality} (its dark-theme text colour). */
  public static Color canonical(int quality) {
    return DARK[clamp(quality)];
  }

  private static int clamp(int quality) {
    return quality >= 0 && quality < DARK.length ? quality : COMMON;
  }
}
