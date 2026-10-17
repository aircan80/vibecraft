package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityRef;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

/**
 * What every table of links shares (the list tables and the detail panels' link tables): the sorter and
 * link renderer for a model, Enter, click and double-click activation, hover feedback, and the view-to-model
 * lookup of a row's link.
 */
public final class TableLinks {
  private static final String OPEN_ROW = "openRow";
  private static final String HOVER = "wowforever.hoveredCell";
  /** Preferred width of a table's name column (its first link column). */
  public static final int NAME_WIDTH = 260;
  /** Preferred width of a number column (counts, levels, slots, percentages). */
  public static final int NUMBER_WIDTH = 70;

  private TableLinks() {}

  /** A table cell by view row and view column. */
  public record Cell(int row, int column) {}

  /**
   * Like {@link #bindActivation(JTable, IntConsumer, Consumer)} where a click on a link cell calls
   * {@code activate} with its row.
   */
  public static void bindActivation(JTable table, IntConsumer activate) {
    bindActivation(table, activate, null);
  }

  /**
   * Makes {@code table} single-selection and wires its activation. Enter and a double-click on a cell that
   * is not a link call {@code activate} with the view row. A single left click on a link cell (a cell of a
   * {@link ResultsTableModel} link column that links somewhere) opens that cell's entity through
   * {@code openRef}, or calls {@code activate} when {@code openRef} is null; the second click of a
   * double-click on a link cell is ignored, so nothing opens twice. A single click elsewhere only selects.
   * Hovering a link cell shows a hand cursor and {@link LinkRenderer} underlines it ({@link #hoveredCell}).
   */
  public static void bindActivation(JTable table, IntConsumer activate, Consumer<EntityRef> openRef) {
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
        if (!SwingUtilities.isLeftMouseButton(e)) {
          return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) {
          return;
        }
        Optional<EntityRef> link = linkAt(table, row, table.columnAtPoint(e.getPoint()));
        if (link.isPresent()) {
          if (e.getClickCount() == 1) {
            if (openRef != null) {
              openRef.accept(link.get());
            } else {
              activate.accept(row);
            }
          }
        } else if (e.getClickCount() == 2) {
          activate.accept(row);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setHovered(table, null);
      }
    });
    table.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int col = table.columnAtPoint(e.getPoint());
        setHovered(table, row < 0 || col < 0 ? null : new Cell(row, col));
      }
    });
  }

  /** The cell the mouse is over (view coordinates); empty when it is outside the table's cells. */
  public static Optional<Cell> hoveredCell(JTable table) {
    return Optional.ofNullable((Cell) table.getClientProperty(HOVER));
  }

  /** Whether view cell ({@code row}, {@code column}) is the hovered one and links somewhere. */
  static boolean isHoveredLink(JTable table, int row, int column) {
    return hoveredCell(table).filter(c -> c.row() == row && c.column() == column).isPresent()
        && linkAt(table, row, column).isPresent();
  }

  private static void setHovered(JTable table, Cell cell) {
    Cell old = (Cell) table.getClientProperty(HOVER);
    if (Objects.equals(old, cell)) {
      return;
    }
    table.putClientProperty(HOVER, cell);
    table.setCursor(cell != null && linkAt(table, cell.row(), cell.column()).isPresent()
        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    repaintCell(table, old);
    repaintCell(table, cell);
  }

  private static void repaintCell(JTable table, Cell cell) {
    if (cell != null && cell.row() < table.getRowCount() && cell.column() < table.getColumnCount()) {
      table.repaint(table.getCellRect(cell.row(), cell.column(), false));
    }
  }

  /** The entity the cell at view ({@code row}, {@code column}) links to; empty for plain or empty cells. */
  static Optional<EntityRef> linkAt(JTable table, int row, int column) {
    if (!(table.getModel() instanceof ResultsTableModel<?> model) || row < 0 || column < 0
        || row >= table.getRowCount() || column >= table.getColumnCount()) {
      return Optional.empty();
    }
    return model.refAt(table.convertRowIndexToModel(row), table.convertColumnIndexToModel(column));
  }

  /**
   * Shows {@code model} in {@code table}: a fresh (unsorted) sorter with column orders, links in link colour,
   * a wide name column ({@link #NAME_WIDTH}) and narrow number columns ({@link #NUMBER_WIDTH}).
   */
  public static void setModel(JTable table, ResultsTableModel<?> model) {
    table.setModel(model);
    TableRowSorter<ResultsTableModel<?>> sorter = new TableRowSorter<>(model);
    LinkRenderer linkRenderer = new LinkRenderer();
    int nameColumn = model.firstLinkColumn();
    for (int c = 0; c < model.getColumnCount(); c++) {
      Columns.Column<?> column = model.column(c);
      if (c == nameColumn) {
        table.getColumnModel().getColumn(c).setPreferredWidth(NAME_WIDTH);
      } else if (isNumber(column)) {
        table.getColumnModel().getColumn(c).setPreferredWidth(NUMBER_WIDTH);
      }
      if (column.order() != null) {
        sorter.setComparator(c, column.order());
      }
      if (column.isLink()) {
        table.getColumnModel().getColumn(c).setCellRenderer(linkRenderer);
      }
    }
    table.setRowSorter(sorter);
  }

  private static boolean isNumber(Columns.Column<?> column) {
    return Number.class.isAssignableFrom(column.type()) || column.type() == Columns.Levels.class
        || column.order() == Columns.NUMERIC_TEXT;
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
