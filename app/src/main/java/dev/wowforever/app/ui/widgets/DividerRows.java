package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Component;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Divider pseudo-rows in a {@link LinkTable}: a divider shows its first-column value in muted text, leaves the
 * other cells blank (or shows its own label there, muted too) and cannot be selected (the selection steps over it)
 * unless it is one the page acts on, such as a collapsible group's heading. Rows keep the order they were given,
 * since a divider belongs between its groups, so the table is not sortable.
 */
public final class DividerRows {
  private DividerRows() {}

  /** Treats the rows of {@code links} that match {@code isDivider} as dividers. Call after the renderers are set. */
  public static <T> void install(LinkTable<T> links, Predicate<T> isDivider) {
    install(links, isDivider, row -> false);
  }

  /**
   * As {@link #install(LinkTable, Predicate)}, the dividers that match {@code selectable} taking the selection
   * like any row (a heading that Enter opens, say).
   */
  public static <T> void install(LinkTable<T> links, Predicate<T> isDivider, Predicate<T> selectable) {
    JTable table = links.table();
    table.setRowSorter(null);
    links.setNonDataRows(isDivider); // a section's row limit counts data rows only
    IntPredicate divider = viewRow -> viewRow >= 0 && viewRow < table.getRowCount()
        && isDivider.test(links.model().rowAt(table.convertRowIndexToModel(viewRow)));
    for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
      TableColumn column = table.getColumnModel().getColumn(c);
      column.setCellRenderer(new Renderer(column.getCellRenderer(), column.getModelIndex(), divider));
    }
    IntPredicate skipped = viewRow -> divider.test(viewRow)
        && !selectable.test(links.model().rowAt(table.convertRowIndexToModel(viewRow)));
    SkipDividers selection = new SkipDividers(skipped, table);
    selection.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setSelectionModel(selection);
  }

  /**
   * Paints dividers itself and hands every other row to the column's own renderer (or the table's default). It
   * knows its column by model index, as the column may be off screen (a compact table's tooltip renders hidden
   * columns with no view index).
   */
  private static final class Renderer extends DefaultTableCellRenderer {
    private final TableCellRenderer own;
    private final int modelColumn;
    private final IntPredicate divider;

    Renderer(TableCellRenderer own, int modelColumn, IntPredicate divider) {
      this.own = own;
      this.modelColumn = modelColumn;
      this.divider = divider;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
      if (!divider.test(row)) {
        TableCellRenderer r =
            own != null ? own : table.getDefaultRenderer(table.getModel().getColumnClass(modelColumn));
        return r.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
      super.getTableCellRendererComponent(table, value == null ? "" : value, isSelected, false, row, column);
      if (!isSelected) {
        setForeground(Theme.current().textMuted());
      }
      return this;
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (own instanceof Component c) {
        SwingUtilities.updateComponentTreeUI(c);
      }
    }
  }

  /** Single selection that never lands on a divider: it moves on in the direction of travel, else back. */
  private static final class SkipDividers extends DefaultListSelectionModel {
    private final IntPredicate divider;
    private final JTable table;

    SkipDividers(IntPredicate divider, JTable table) {
      this.divider = divider;
      this.table = table;
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      int target = skip(index1);
      if (target >= 0) {
        super.setSelectionInterval(target, target);
      }
    }

    @Override
    public void addSelectionInterval(int index0, int index1) {
      setSelectionInterval(index0, index1);
    }

    private int skip(int index) {
      if (!divider.test(index)) {
        return index;
      }
      int step = index >= getLeadSelectionIndex() ? 1 : -1;
      for (int i : new int[] {index + step, index - step}) {
        if (i >= 0 && i < table.getRowCount() && !divider.test(i)) {
          return i;
        }
      }
      return -1;
    }
  }
}
