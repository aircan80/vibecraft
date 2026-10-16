package dev.wowforever.app.ui;

import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

/** The centre results: a title with the row count over a sortable table; double-click or Enter opens a row. */
public final class ResultsPanel extends JPanel {
  private static final String OPEN_ROW = "openRow";

  private final Navigator nav;
  private final JLabel title = new JLabel(" ");
  private final JTable table = new JTable();
  private final LinkRenderer linkRenderer = new LinkRenderer();
  private ResultsTableModel<?> model = new ResultsTableModel<>(List.of());

  public ResultsPanel(Navigator nav) {
    super(new BorderLayout());
    this.nav = nav;
    title.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    table.setModel(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke("ENTER"), OPEN_ROW);
    table.getActionMap().put(OPEN_ROW, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int row = table.getSelectedRow();
        if (row >= 0) {
          activateRow(row);
        }
      }
    });
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && row >= 0) {
          activateRow(row);
        }
      }
    });
    add(title, BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
  }

  /** Shows {@code model} under {@code "<title> (<rows>)"}, unsorted. */
  public void show(String title, ResultsTableModel<?> model) {
    this.model = model;
    table.setModel(model);
    table.setRowSorter(new TableRowSorter<>(model));
    for (int c = 0; c < model.getColumnCount(); c++) {
      if (model.column(c).isLink()) {
        table.getColumnModel().getColumn(c).setCellRenderer(linkRenderer);
      }
    }
    if (model.getColumnCount() > 0) {
      table.getColumnModel().getColumn(0).setPreferredWidth(260);
    }
    this.title.setText(String.format(Locale.ROOT, "%s (%,d)", title, model.getRowCount()));
  }

  /** Opens the entity of the first link column of view row {@code viewRow}. */
  public void activateRow(int viewRow) {
    int col = model.firstLinkColumn();
    if (col < 0) {
      return;
    }
    model.refAt(table.convertRowIndexToModel(viewRow), col).ifPresent(ref -> nav.open(new ViewTarget.Entity(ref)));
  }

  public ResultsTableModel<?> model() {
    return model;
  }

  public JTable table() {
    return table;
  }

  /** The title line as shown, e.g. {@code "Armor (12)"}. */
  public String title() {
    return title.getText();
  }
}
