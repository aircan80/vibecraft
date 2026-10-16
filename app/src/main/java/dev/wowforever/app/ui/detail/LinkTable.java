package dev.wowforever.app.ui.detail;

import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.ResultsTableModel;
import dev.wowforever.app.ui.TableLinks;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

/** A sortable table of linked rows; double-click or Enter opens the row's first link. */
public final class LinkTable<T> extends JPanel {
  private final Navigator nav;
  private final ResultsTableModel<T> model;
  private final JTable table = new JTable();

  public LinkTable(List<Columns.Column<T>> cols, Navigator nav) {
    super(new BorderLayout());
    this.nav = Objects.requireNonNull(nav, "nav");
    this.model = new ResultsTableModel<>(cols);
    TableLinks.setModel(table, model);
    TableLinks.bindActivation(table, this::activateRow);
    add(new JScrollPane(table), BorderLayout.CENTER);
  }

  /** Replaces every row. */
  public void setRows(List<T> rows) {
    model.setRows(rows);
  }

  public int rowCount() {
    return model.getRowCount();
  }

  public JTable table() {
    return table;
  }

  public ResultsTableModel<T> model() {
    return model;
  }

  /** Opens the entity of the first link column of view row {@code viewRow} (what double-click and Enter do). */
  public void activateRow(int viewRow) {
    TableLinks.refAt(table, model, viewRow).ifPresent(ref -> nav.open(new ViewTarget.Entity(ref)));
  }

  /**
   * Adds a tab titled {@code "<title> (<rows>)"} holding a link table of {@code rows}; the tab is disabled when
   * there are no rows. The tab strip scrolls rather than wrapping onto a second row.
   */
  static <T> LinkTable<T> addTab(JTabbedPane tabs, String title, List<Columns.Column<T>> cols, List<T> rows,
      Navigator nav) {
    LinkTable<T> t = new LinkTable<>(cols, nav);
    t.setRows(rows);
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    tabs.addTab(String.format(Locale.ROOT, "%s (%,d)", title, rows.size()), t);
    tabs.setEnabledAt(tabs.getTabCount() - 1, !rows.isEmpty());
    return t;
  }

  /** Selects the first enabled tab, so a page never opens on an empty, disabled one. */
  static void selectFirstEnabled(JTabbedPane tabs) {
    for (int i = 0; i < tabs.getTabCount(); i++) {
      if (tabs.isEnabledAt(i)) {
        tabs.setSelectedIndex(i);
        return;
      }
    }
  }
}
