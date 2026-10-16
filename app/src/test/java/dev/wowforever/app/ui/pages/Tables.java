package dev.wowforever.app.ui.pages;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;

/** Reads what a page's tables show, for assertions. */
final class Tables {
  private Tables() {}

  /** The values of view column {@code col}, top to bottom. */
  static List<Object> column(JTable t, int col) {
    List<Object> values = new ArrayList<>();
    for (int r = 0; r < t.getRowCount(); r++) {
      values.add(t.getValueAt(r, col));
    }
    return values;
  }

  /** The column titles, left to right. */
  static List<String> columnNames(JTable t) {
    List<String> names = new ArrayList<>();
    for (int c = 0; c < t.getColumnCount(); c++) {
      names.add(t.getColumnName(c));
    }
    return names;
  }
}
