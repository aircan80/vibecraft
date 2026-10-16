package dev.wowforever.app.ui.theme;

import java.awt.Color;

/** Both colour palettes. */
public final class Tokens {
  public static final Theme DARK =
      new Theme(
          true,
          c(0x14171C), c(0x1B2027), c(0x242A33),
          c(0xE4E1D8), c(0x8E97A5),
          c(0xD6B25E), c(0x9CC3E6),
          c(0x0C1424), c(0x46587A));
  public static final Theme LIGHT =
      new Theme(
          false,
          c(0xF4F5F7), c(0xFFFFFF), c(0xE9ECF0),
          c(0x1C1F24), c(0x5C6470),
          c(0x9A7419), c(0x1F5FA8),
          c(0x0C1424), c(0x46587A));

  private Tokens() {}

  private static Color c(int rgb) {
    return new Color(rgb);
  }
}
