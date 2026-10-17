package dev.wowforever.app.ui.pages;

import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.ResultsTableModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * The columns a page table keeps in the compact window: the name (the first link column, else the first column),
 * the {@value Columns#RATE} column and the {@value VisibleColumns#ORIGIN} chip column. The values of the other
 * columns go into the row's tooltip ({@code "Level 21 · Zone: Westfall · Slot 7"}). A table is compact while an
 * enclosing component carries {@link #KEY} (the compact window's content does); it is not a global setting.
 */
public final class CompactColumns {
  /** The client property, set to {@link Boolean#TRUE}, of a component whose tables are compact. */
  public static final String KEY = "wowforever.compactTables";
  private static final String SEPARATOR = " · ";

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
    return col == Math.max(0, model.firstLinkColumn()) || title.equals(Columns.RATE)
        || title.equals(VisibleColumns.ORIGIN);
  }

  /**
   * The tooltip of model row {@code row} of a compact table: each column it does not keep that has a value, as
   * {@code "Level 21"} for a value that starts with a digit and {@code "Zone: Westfall"} for any other; null when
   * none has a value.
   */
  public static String tooltip(ResultsTableModel<?> model, int row) {
    List<String> parts = new ArrayList<>();
    for (int col = 0; col < model.getColumnCount(); col++) {
      Object value = model.getValueAt(row, col);
      String text = value == null ? "" : value.toString().strip();
      if (kept(model, col) || text.isEmpty() || text.equals(Columns.NONE)) {
        continue;
      }
      String title = model.getColumnName(col);
      parts.add(Character.isDigit(text.charAt(0)) ? title + " " + text : title + ": " + text);
    }
    return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
  }
}
