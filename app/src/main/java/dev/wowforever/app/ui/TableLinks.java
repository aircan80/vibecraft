package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityRef;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

/**
 * What every table of links shares ({@link ResultsPanel} and the detail panels' link tables): the sorter and
 * link renderer for a model, Enter and double-click activation, and the view-to-model lookup of a row's link.
 */
public final class TableLinks {
  private static final String OPEN_ROW = "openRow";

  private TableLinks() {}

  /** Makes {@code table} single-selection and calls {@code activate} with the view row on Enter or double-click. */
  public static void bindActivation(JTable table, IntConsumer activate) {
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke("ENTER"), OPEN_ROW);
    table.getActionMap().put(OPEN_ROW, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int row = table.getSelectedRow();
        if (row >= 0) {
          activate.accept(row);
        }
      }
    });
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && row >= 0) {
          activate.accept(row);
        }
      }
    });
  }

  /** Shows {@code model} in {@code table}: a fresh (unsorted) sorter with column orders, links in link colour. */
  public static void setModel(JTable table, ResultsTableModel<?> model) {
    table.setModel(model);
    TableRowSorter<ResultsTableModel<?>> sorter = new TableRowSorter<>(model);
    LinkRenderer linkRenderer = new LinkRenderer();
    for (int c = 0; c < model.getColumnCount(); c++) {
      Columns.Column<?> column = model.column(c);
      if (column.order() != null) {
        sorter.setComparator(c, column.order());
      }
      if (column.isLink()) {
        table.getColumnModel().getColumn(c).setCellRenderer(linkRenderer);
      }
    }
    table.setRowSorter(sorter);
  }

  /** The entity of the first link column of view row {@code viewRow}; empty when the row links nowhere. */
  public static Optional<EntityRef> refAt(JTable table, ResultsTableModel<?> model, int viewRow) {
    int col = model.firstLinkColumn();
    if (col < 0) {
      return Optional.empty();
    }
    return model.refAt(table.convertRowIndexToModel(viewRow), col);
  }
}
