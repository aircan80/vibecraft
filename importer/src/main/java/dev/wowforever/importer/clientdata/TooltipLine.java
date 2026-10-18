package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * One rendered tooltip line, a row of {@code item_tooltip_line}: the UI draws these as they are (spec 4.3).
 *
 * @param ordinal the line's position, from 0
 * @param kind    one of the spec 4.3 kinds (see {@link TooltipBuilder})
 * @param text    the line; a slot line holds the slot and the subclass separated by a tab, which the UI
 *                right-aligns; Use/Equip texts may hold line breaks
 * @param colour  {@code #rrggbb}
 */
public record TooltipLine(int ordinal, String kind, String text, String colour, SpellTextEvidence evidence) {
  public TooltipLine(int ordinal, String kind, String text, String colour) { this(ordinal, kind, text, colour, null); }
  public static final String WHITE = "#ffffff";
  public static final String EQUIP_GREEN = "#1eff00";
  public static final String FLAVOUR_GOLD = "#ffd100";
  public static final String MUTED = "#9d9d9d";
  public static final String SET_GREY = "#808080";
  /** Item quality 0 (poor) to 7 (heirloom). */
  static final List<String> QUALITY = List.of("#9d9d9d", "#ffffff", "#1eff00", "#0070dd", "#a335ee", "#ff8000",
      "#e6cc80", "#00ccff");

  /** The name colour of an item quality; white when unknown. */
  public static String qualityColour(Integer quality) {
    return quality != null && quality >= 0 && quality < QUALITY.size() ? QUALITY.get(quality) : WHITE;
  }
}
