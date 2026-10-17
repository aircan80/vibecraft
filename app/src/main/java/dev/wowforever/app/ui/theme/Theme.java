package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Colour tokens of one mode, for custom painting. {@link #current()} is the active palette. {@code accentGold} is the
 * gold of shapes that carry their own contrast (the rail highlight, map pins); {@code goldText} is the gold of text
 * under 18 pt and what frames it (section headings, "Other loot lists", the Elite and Boss chips), at least 4.5:1 on
 * {@code bgBase} and {@code bgSurface}. {@code warn} is the amber of a warning sign, such as the marker of a drop
 * rate whose sources disagree.
 */
public record Theme(
    boolean dark,
    Color bgBase,
    Color bgSurface,
    Color bgRaised,
    Color textPrimary,
    Color textMuted,
    Color accentGold,
    Color goldText,
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
