package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityRef;
import java.util.List;
import java.util.Optional;
import javax.swing.table.AbstractTableModel;

/** A read-only table over a list of rows, with columns from {@link Columns}. */
public final class ResultsTableModel<T> extends AbstractTableModel {
  private final List<Columns.Column<T>> columns;
  private List<T> rows = List.of();

  public ResultsTableModel(List<Columns.Column<T>> columns) {
    this.columns = List.copyOf(columns);
  }

  /** Replaces every row. */
  public void setRows(List<T> rows) {
    this.rows = List.copyOf(rows);
    fireTableDataChanged();
  }

  /** The row at model index {@code row}. */
  public T rowAt(int row) {
    return rows.get(row);
  }

  /** The column at model index {@code col}. */
  public Columns.Column<T> column(int col) {
    return columns.get(col);
  }

  /** The entity the cell links to; empty for plain columns. */
  public Optional<EntityRef> refAt(int row, int col) {
    return columns.get(col).refFor(rows.get(row));
  }

  /** The item quality the cell is coloured by; null for the usual colour. */
  public Integer qualityAt(int row, int col) {
    return columns.get(col).qualityFor(rows.get(row));
  }

  /** Whether the cell's drop rate has sources that disagree; false outside rate columns. */
  public boolean disagreesAt(int row, int col) {
    return columns.get(col).disagreesFor(rows.get(row));
  }

  /** The model index of the first link column, or -1 when there is none. */
  public int firstLinkColumn() {
    for (int c = 0; c < columns.size(); c++) {
      if (columns.get(c).isLink()) {
        return c;
      }
    }
    return -1;
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return columns.size();
  }

  @Override
  public String getColumnName(int col) {
    return columns.get(col).title();
  }

  @Override
  public Class<?> getColumnClass(int col) {
    return columns.get(col).type();
  }

  @Override
  public Object getValueAt(int row, int col) {
    return columns.get(col).value().apply(rows.get(row));
  }
}
