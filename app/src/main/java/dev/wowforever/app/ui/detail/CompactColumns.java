package dev.wowforever.app.ui.detail;

import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.ResultsTableModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * The columns a {@link LinkTable} keeps in the compact window: the name (the first link column, else the first
 * column), the {@value Columns#RATE} column and the {@value #ORIGIN} chip column. The other columns are told in the
 * row's tooltip ({@code "Level 21 · Zone: Westfall · #7"}). A table is compact while an enclosing component
 * carries {@link #KEY} (the compact window's content does); it is not a global setting.
 */
public final class CompactColumns {
  /** The client property, set to {@link Boolean#TRUE}, of a component whose tables are compact. */
  public static final String KEY = "wowforever.compactTables";
  /** The title of the data origin column, shown as a chip. */
  public static final String ORIGIN = "Origin";
  private static final String SEPARATOR = " · ";

  /**
   * A column the compact table hides: its title, the text its renderer shows for the row and whether the row's rate
   * there has sources that disagree.
   */
  public record Cell(String title, String text, boolean disagrees) {
    /** A cell without a disagreeing rate. */
    public Cell(String title, String text) {
      this(title, text, false);
    }
  }

  private CompactColumns() {}

  /** Whether {@code c} lies inside a component that carries {@link #KEY}. */
  public static boolean within(Component c) {
    for (Component p = c; p != null; p = p.getParent()) {
      if (p instanceof JComponent j && Boolean.TRUE.equals(j.getClientProperty(KEY))) {
        return true;
      }
    }
    return false;
  }

  /** Whether a compact table of {@code model} keeps model column {@code col}. */
  public static boolean kept(ResultsTableModel<?> model, int col) {
    String title = model.getColumnName(col);
    return col == Math.max(0, model.firstLinkColumn()) || title.equals(Columns.RATE) || title.equals(ORIGIN);
  }

  /**
   * The row tooltip of a compact table over its {@code hidden} cells: each one that shows text (markup stripped),
   * as {@code "Level 21"} ({@code "#7"} for the loot order) when the text starts with a digit and
   * {@code "Zone: Westfall"} otherwise, a disagreeing rate followed by {@link Columns#DISAGREES} (which the
   * renderer of a hidden column cannot paint), joined by {@code " · "}; null when none shows any.
   */
  public static String tooltip(List<Cell> hidden) {
    List<String> parts = new ArrayList<>();
    for (Cell cell : hidden) {
      String text = plain(cell.text());
      if (text.isEmpty() || text.equals(Columns.NONE)) {
        continue;
      }
      if (cell.disagrees() && !text.endsWith(Columns.DISAGREES.strip())) {
        text += Columns.DISAGREES;
      }
      parts.add(!Character.isDigit(text.charAt(0)) ? cell.title() + ": " + text
          : cell.title().equals(Columns.LOOT_ORDER) ? cell.title() + text : cell.title() + " " + text);
    }
    return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
  }

  /**
   * The tooltip of a compact table's cell: the {@code row} tooltip, then on a line of its own the cell's own
   * tooltip (say, a cut-off name in full) when it has one; either alone when the other is null.
   */
  public static String join(String row, String cell) {
    if (row == null || cell == null || cell.isBlank()) {
      return row != null ? row : cell;
    }
    return "<html>" + escape(row) + "<br>" + (isHtml(cell) ? body(cell) : escape(cell)) + "</html>";
  }

  private static String plain(String text) {
    if (text == null) {
      return "";
    }
    String s = isHtml(text) ? body(text).replaceAll("<[^>]*>", "") : text;
    return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").strip();
  }

  private static boolean isHtml(String text) {
    return text.strip().toLowerCase(Locale.ROOT).startsWith("<html>");
  }

  /** {@code html} without its {@code <html>} wrapper. */
  private static String body(String html) {
    return html.strip().replaceFirst("(?i)^<html>", "").replaceFirst("(?i)</html>$", "");
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
