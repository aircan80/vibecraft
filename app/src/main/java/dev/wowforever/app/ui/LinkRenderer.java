package dev.wowforever.app.ui;

import com.formdev.flatlaf.FlatLaf;
import dev.wowforever.app.ui.theme.QualityColors;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Renders link cells in the look-and-feel's link colour (looked up per paint, so theme changes apply); a cell
 * of a {@link ResultsTableModel} link column that links nowhere (a boss without an NPC, say) renders as plain
 * text, and a cell with an item quality in that quality's colour for the installed (dark or light) look-and-feel.
 */
public final class LinkRenderer extends DefaultTableCellRenderer {
  private static final Color FALLBACK = new Color(0x22, 0x6F, 0xC9);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    setFont(TableLinks.isHoveredLink(table, row, column)
        ? table.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON))
        : table.getFont());
    if (!isSelected) {
      if (!linksSomewhere(table, row, column)) {
        setForeground(table.getForeground());
      } else {
        Integer quality = quality(table, row, column);
        setForeground(quality != null ? QualityColors.text(quality, FlatLaf.isLafDark()) : linkColor());
      }
    }
    return this;
  }

  /** The item quality of the cell, or null when it has none (or the table is not a {@link ResultsTableModel}). */
  private static Integer quality(JTable table, int viewRow, int viewColumn) {
    if (!(table.getModel() instanceof ResultsTableModel<?> model) || viewRow < 0 || viewColumn < 0) {
      return null;
    }
    return model.qualityAt(table.convertRowIndexToModel(viewRow), table.convertColumnIndexToModel(viewColumn));
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
