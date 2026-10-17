package dev.wowforever.app.ui.pages;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Reads what a page's tables show, and where its parts sit, for assertions. */
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

  /** The tooltip a pointer resting in the middle of view cell ({@code row}, {@code col}) shows. */
  static String tooltipAt(JTable t, int row, int col) {
    java.awt.Rectangle cell = t.getCellRect(row, col, false);
    return t.getToolTipText(new java.awt.event.MouseEvent(t, java.awt.event.MouseEvent.MOUSE_MOVED, 0L, 0,
        cell.x + cell.width / 2, cell.y + cell.height / 2, 0, false));
  }

  /** The column titles, left to right. */
  static List<String> columnNames(JTable t) {
    List<String> names = new ArrayList<>();
    for (int c = 0; c < t.getColumnCount(); c++) {
      names.add(t.getColumnName(c));
    }
    return names;
  }

  /** Lays out the whole tree top-down; headless components have no peer, so {@code validate()} does nothing. */
  static void layOut(Container c) {
    c.doLayout();
    for (Component child : c.getComponents()) {
      if (child instanceof Container cc) {
        layOut(cc);
      }
    }
  }

  /** The bounds of {@code c} in the coordinates of its ancestor {@code root}. */
  static java.awt.Rectangle boundsIn(Component c, Component root) {
    return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
  }
}
