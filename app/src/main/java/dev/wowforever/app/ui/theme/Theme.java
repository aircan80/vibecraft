package dev.wowforever.app.ui.theme;

import java.awt.Color;

/**
 * Colour tokens of one mode, for custom painting. {@link #current()} is the active palette. {@code accentGold} is the
 * gold of shapes that carry their own contrast (the rail highlight, map pins); {@code goldText} is the gold of text
 * under 18 pt and what frames it (section headings, "Other loot lists", the Elite and Boss chips), at least 4.5:1 on
 * {@code bgBase} and {@code bgSurface}. {@code warn} is the amber of a warning sign, such as the marker of a drop
 * rate whose sources disagree.
 *
 * <p>The chrome tokens: {@code plate} is the navy of the tooltip card and the rail plates (the same in both themes);
 * {@code hairline} is the 1 px border of raised panels and the status bar, gold at 35 % alpha; {@code barStart} and
 * {@code barEnd} are the two ends of the drop-rate bar's gradient; {@code altRow} is a table's alternate row and
 * {@code selection} a table's selected row (focused or not), painted with the table's selection foreground. Lists and
 * trees keep FlatLaf's accent selection, so list renderers draw a selected row in the list's selection foreground,
 * never in a quality colour. In Slate ({@link Tokens#DARK}) small text of every quality colour is at least 4.5:1 on
 * {@code altRow} and {@code selection}; the light palette makes no such promise until Parchment replaces it.
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
    Color warn,
    Color plate,
    Color hairline,
    Color barStart,
    Color barEnd,
    Color altRow,
    Color selection) {

  private static volatile Theme current = Tokens.DARK;

  public static Theme current() {
    return current;
  }

  /** Sets the active palette; called by the theme manager. */
  public static void setCurrent(Theme theme) {
    current = theme;
  }
}
