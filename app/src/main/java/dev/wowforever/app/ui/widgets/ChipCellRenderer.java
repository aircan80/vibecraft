package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;
import java.util.function.IntFunction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * A table cell that shows a plain outline {@link Chip} after the column's own cell, such as the instance
 * ({@code "Molten Core"}) after a boss on the item page's "Dropped by". Rows without a chip render as the column
 * renders them; a cell with one reads, and is coloured, as its own text, the chip following it.
 */
public final class ChipCellRenderer implements TableCellRenderer {
  private static final int GAP = 6;

  private final TableCellRenderer own;
  private final IntFunction<String> chipAt;
  private final Cell cell = new Cell();

  private ChipCellRenderer(TableCellRenderer own, IntFunction<String> chipAt) {
    this.own = own;
    this.chipAt = Objects.requireNonNull(chipAt, "chipAt");
  }

  /**
   * Renders {@code column} with a chip after its cells: {@code chipAt} gives the chip text of a view row, null for
   * none. Call after the column's own renderer is set (and before any renderer that wraps it, such as
   * {@link DividerRows}).
   */
  public static void install(TableColumn column, IntFunction<String> chipAt) {
    column.setCellRenderer(new ChipCellRenderer(column.getCellRenderer(), chipAt));
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    TableCellRenderer r = own != null ? own
        : table.getDefaultRenderer(table.getModel().getColumnClass(table.convertColumnIndexToModel(column)));
    Component text = r.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    String chip = row < 0 ? null : chipAt.apply(row);
    if (chip == null || !(text instanceof JComponent textCell)) {
      return text;
    }
    cell.show(textCell, chip);
    return cell;
  }

  /** The cell of a row with a chip: its own cell, then the chip, centred in the row's height. */
  public static final class Cell extends JPanel {
    private final Chip chip = new Chip("", Chip.Style.PLAIN);
    private JComponent text;

    Cell() {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(true);
    }

    void show(JComponent textCell, String chipText) {
      removeAll();
      text = textCell;
      text.setAlignmentY(CENTER_ALIGNMENT);
      chip.setText(chipText);
      chip.setFont(textCell.getFont().deriveFont(Font.BOLD, textCell.getFont().getSize2D() * 0.85f));
      add(text);
      add(Box.createRigidArea(new Dimension(UIScale.scale(GAP), 0)));
      add(chip);
      add(Box.createHorizontalGlue());
      setBackground(text.getBackground());
      setBorder(BorderFactory.createEmptyBorder());
    }

    /** The chip after the cell's text. */
    public Chip chip() {
      return chip;
    }

    /** The column's own cell. */
    public JComponent text() {
      return text;
    }

    /** The colour of the cell's text (a link, an item's quality). */
    @Override
    public Color getForeground() {
      return text == null ? super.getForeground() : text.getForeground();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (chip != null) {
        SwingUtilities.updateComponentTreeUI(chip);
      }
    }
  }
}
