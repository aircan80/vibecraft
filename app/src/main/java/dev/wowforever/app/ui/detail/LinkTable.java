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

/**
 * A sortable table of linked rows; a click on a link cell opens it, Enter or a double-click elsewhere opens the
 * row's first link.
 */
public final class LinkTable<T> extends JPanel {
  private final Navigator nav;
  private final ResultsTableModel<T> model;
  private final JTable table = new JTable();

  private final boolean inline;

  /** A link table in its own scroll pane. */
  public LinkTable(List<Columns.Column<T>> cols, Navigator nav) {
    this(cols, nav, false);
  }

  private LinkTable(List<Columns.Column<T>> cols, Navigator nav, boolean inline) {
    super(new BorderLayout());
    this.nav = Objects.requireNonNull(nav, "nav");
    this.model = new ResultsTableModel<>(cols);
    this.inline = inline;
    TableLinks.setModel(table, model);
    TableLinks.bindActivation(table, this::activateRow, ref -> nav.open(new ViewTarget.Entity(ref)));
    if (inline) {
      add(table.getTableHeader(), BorderLayout.NORTH);
      add(table, BorderLayout.CENTER);
    } else {
      add(new JScrollPane(table), BorderLayout.CENTER);
    }
  }

  /**
   * A link table without its own scroll pane: the column header over the table, as tall as all its rows, for a
   * page that scrolls as a whole.
   */
  public static <T> LinkTable<T> inline(List<Columns.Column<T>> cols, Navigator nav) {
    return new LinkTable<>(cols, nav, true);
  }

  /** Whether this table has no scroll pane of its own ({@link #inline}). */
  public boolean isInline() {
    return inline;
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
