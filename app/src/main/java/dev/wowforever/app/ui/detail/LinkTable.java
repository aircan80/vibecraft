package dev.wowforever.app.ui.detail;

import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.KeyMap;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.ResultsTableModel;
import dev.wowforever.app.ui.TableLinks;
import dev.wowforever.app.ui.pages.CompactColumns;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * A sortable table of linked rows; a click on a link cell opens it, Enter or a double-click elsewhere opens the
 * row's first link. With a row selected, Ctrl+C copies its name (the first link column, else the first column) and
 * Ctrl+Shift+C its id text: {@code item:ID}, {@code npc:ID} and so on by default ({@link #setIdText}).
 *
 * <p>Inside the compact window ({@link CompactColumns#within}) the table is compact: it shows only the columns
 * {@link CompactColumns#kept} keeps, and the row's tooltip tells the others. It follows the window it is moved
 * into.
 */
public final class LinkTable<T> extends JPanel {
  private final Navigator nav;
  private final ResultsTableModel<T> model;
  private final JTable table = new JTable() {
    @Override
    public String getToolTipText(MouseEvent e) {
      String row = rowTooltip(rowAtPoint(e.getPoint()));
      return row != null ? row : super.getToolTipText(e);
    }
  };
  /** Every column in model order, so that compact mode can take some away and put them back. */
  private final List<TableColumn> columns = new ArrayList<>();

  private final boolean inline;
  private ClipboardPort clipboard = ClipboardPort.system();
  private Function<T, Optional<String>> idText = this::entityIdText;
  private boolean compact;

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
    for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
      columns.add(table.getColumnModel().getColumn(c));
    }
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        setCompact(CompactColumns.within(this));
      }
    });
    TableLinks.bindActivation(table, this::activateRow, ref -> nav.open(new ViewTarget.Entity(ref)));
    KeyMap.installRowCopy(table, this::copyName, this::copyId);
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

  /**
   * Shows only the columns {@link CompactColumns#kept} keeps, the others in the row tooltip; or every column again,
   * in model order. Each column keeps its renderer and width. Called as the table moves in or out of the compact
   * window.
   */
  public void setCompact(boolean compact) {
    if (compact == this.compact) {
      return;
    }
    this.compact = compact;
    TableColumnModel shown = table.getColumnModel();
    while (shown.getColumnCount() > 0) {
      shown.removeColumn(shown.getColumn(0));
    }
    for (TableColumn c : columns) {
      if (!compact || CompactColumns.kept(model, c.getModelIndex())) {
        shown.addColumn(c);
      }
    }
  }

  public boolean isCompact() {
    return compact;
  }

  /** The tooltip of view row {@code viewRow} in compact mode ({@link CompactColumns#tooltip}); null otherwise. */
  public String rowTooltip(int viewRow) {
    if (!compact || viewRow < 0 || viewRow >= table.getRowCount()) {
      return null;
    }
    return CompactColumns.tooltip(model, table.convertRowIndexToModel(viewRow));
  }

  /** Where Ctrl+C and Ctrl+Shift+C copy to; the system clipboard unless set. */
  public void setClipboard(ClipboardPort clipboard) {
    this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
  }

  /** What Ctrl+Shift+C copies for a row (empty copies nothing); the entity's {@link Labels#idText} unless set. */
  public void setIdText(Function<T, Optional<String>> idText) {
    this.idText = Objects.requireNonNull(idText, "idText");
  }

  private Optional<String> entityIdText(T row) {
    int col = model.firstLinkColumn();
    return col < 0 ? Optional.empty() : model.column(col).refFor(row).flatMap(Labels::idText);
  }

  private void copyName() {
    int view = table.getSelectedRow();
    if (view < 0 || model.getColumnCount() == 0) {
      return;
    }
    int col = Math.max(0, model.firstLinkColumn());
    Object name = model.getValueAt(table.convertRowIndexToModel(view), col);
    if (name != null && !name.toString().isBlank()) {
      clipboard.copy(name.toString());
    }
  }

  private void copyId() {
    int view = table.getSelectedRow();
    if (view >= 0) {
      idText.apply(model.rowAt(table.convertRowIndexToModel(view))).ifPresent(clipboard::copy);
    }
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
