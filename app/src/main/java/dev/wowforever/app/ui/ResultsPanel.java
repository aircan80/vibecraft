package dev.wowforever.app.ui;

import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** The centre results: a title with the row count over a sortable table; double-click or Enter opens a row. */
public final class ResultsPanel extends JPanel {
  private final Navigator nav;
  private final JLabel title = new JLabel(" ");
  private final JTable table = new JTable();
  private ResultsTableModel<?> model = new ResultsTableModel<>(List.of());

  public ResultsPanel(Navigator nav) {
    super(new BorderLayout());
    this.nav = nav;
    title.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    table.setModel(model);
    TableLinks.bindActivation(table, this::activateRow);
    add(title, BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
  }

  /** Shows {@code model} under {@code "<title> (<rows>)"}, unsorted. */
  public void show(String title, ResultsTableModel<?> model) {
    show(title, model, false);
  }

  /**
   * Shows {@code model}, unsorted, under {@code "<title> (<rows>)"}, or {@code "<title> (first <rows>)"} when
   * {@code capped} says the rows are only the first page of a longer list.
   */
  public void show(String title, ResultsTableModel<?> model, boolean capped) {
    this.model = model;
    TableLinks.setModel(table, model);
    this.title.setText(String.format(Locale.ROOT, capped ? "%s (first %,d)" : "%s (%,d)", title,
        model.getRowCount()));
  }

  /** Opens the entity of the first link column of view row {@code viewRow}. */
  public void activateRow(int viewRow) {
    TableLinks.refAt(table, model, viewRow).ifPresent(ref -> nav.open(new ViewTarget.Entity(ref)));
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
