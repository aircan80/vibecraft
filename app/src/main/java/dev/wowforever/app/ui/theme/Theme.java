package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Colour tokens of one mode, for custom painting. {@link #current()} is the active palette. {@code warn} is the
 * amber of a warning sign, such as the marker of a drop rate whose sources disagree.
 */
public record Theme(
    boolean dark,
    Color bgBase,
    Color bgSurface,
    Color bgRaised,
    Color textPrimary,
    Color textMuted,
    Color accentGold,
    Color link,
    Color cardBg,
    Color cardBorder,
    Color warn) {

  private static volatile Theme current = Tokens.DARK;

  public static Theme current() {
    return current;
  }

  /** Sets the active palette; called by the theme manager. */
  public static void setCurrent(Theme theme) {
    current = theme;
  }
}
