package dev.wowforever.app.ui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Renders link cells in the look-and-feel's link colour (looked up per paint, so theme changes apply); a cell
 * of a {@link ResultsTableModel} link column that links nowhere (a boss without an NPC, say) renders as plain
 * text.
 */
public final class LinkRenderer extends DefaultTableCellRenderer {
  private static final Color FALLBACK = new Color(0x22, 0x6F, 0xC9);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (!isSelected) {
      setForeground(linksSomewhere(table, row, column) ? linkColor() : table.getForeground());
    }
    return this;
  }

  private static boolean linksSomewhere(JTable table, int viewRow, int viewColumn) {
    if (!(table.getModel() instanceof ResultsTableModel<?> model) || viewRow < 0 || viewColumn < 0) {
      return true;
    }
    return model.refAt(table.convertRowIndexToModel(viewRow), table.convertColumnIndexToModel(viewColumn))
        .isPresent();
  }

  /** FlatLaf's {@code Component.linkColor}, or a plain blue under other look-and-feels. */
  public static Color linkColor() {
    Color c = UIManager.getColor("Component.linkColor");
    return c != null ? c : FALLBACK;
  }
}
