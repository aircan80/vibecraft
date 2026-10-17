package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;

/**
 * An entity page that scrolls as a whole: whatever the page puts at the top (its header), then one
 * {@link SourceSection} per kind of row it has. Ctrl+1 to Ctrl+6 jump to the first six sections once the page
 * calls {@link #bindJumpKeys()} after adding them.
 */
abstract class SourcesPage extends JPanel {
  private static final int MAX_JUMP_KEYS = 6;

  final Navigator nav;
  final PageContent content = new PageContent();
  private final JScrollPane scroll = new JScrollPane(content);
  private final List<SourceSection> sections = new ArrayList<>();

  SourcesPage(Navigator nav) {
    super(new BorderLayout());
    this.nav = nav;
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    add(scroll, BorderLayout.CENTER);
  }

  /** Adds a section of {@code rows} and returns its table; adds nothing and returns null when there are none. */
  <T> LinkTable<T> addSection(String title, int count, List<Columns.Column<T>> cols, List<T> rows) {
    if (rows.isEmpty()) {
      return null;
    }
    LinkTable<T> table = LinkTable.inline(cols, nav);
    table.setRows(rows);
    addSection(new SourceSection(title, count, table));
    return table;
  }

  /**
   * Adds a section of the rows a source filter kept ({@code filtered}), shown as {@code rows}, and returns its table:
   * the heading counts the rows kept, a note says how many the filter hid. Adds nothing and returns null when there
   * were no rows to filter.
   */
  <T> LinkTable<T> addFilteredSection(String title, SourceFilters.Filtered<?> filtered, List<Columns.Column<T>> cols,
      List<T> rows) {
    if (filtered.rows().isEmpty() && filtered.hidden() == 0) {
      return null;
    }
    LinkTable<T> table = LinkTable.inline(cols, nav);
    table.setRows(rows);
    addFilteredSection(title, filtered, table);
    return table;
  }

  /** Adds a section of {@code table}, which shows the rows {@code filtered} kept, with its hidden rows' note. */
  void addFilteredSection(String title, SourceFilters.Filtered<?> filtered, LinkTable<?> table) {
    SourceSection section = new SourceSection(title, filtered.rows().size(), table);
    SourceFilters.annotate(section, filtered);
    addSection(section);
  }

  /**
   * Adds a section of items ({@link LootTables#plain}) and returns its table; adds nothing and returns null when
   * there are none.
   */
  LinkTable<LootRow> addLootSection(String title, List<LootRow> loot) {
    if (loot.isEmpty()) {
      return null;
    }
    LinkTable<LootRow> table = LootTables.plain(loot, nav);
    addSection(new SourceSection(title, loot.size(), table));
    return table;
  }

  /**
   * A page header titled {@code title} over {@code facts} and the entity's zone: a link when the zone id is known,
   * its name as text when only the name is, nothing otherwise.
   */
  HeaderPanel zoneHeader(String title, String facts, Long zoneId, String zoneName) {
    HeaderPanel header = new HeaderPanel(title, Labels.join(facts, zoneId == null ? zoneName : null));
    header.setAlignmentX(LEFT_ALIGNMENT);
    if (zoneId != null) {
      EntityRef zone = new EntityRef(EntityKind.ZONE, zoneId);
      header.addLink(zone.displayName(zoneName), zone, nav);
    }
    return header;
  }

  /** Adds {@code section} below the others. */
  void addSection(SourceSection section) {
    sections.add(section);
    content.add(section);
  }

  /** Ctrl+n jumps to section n (in the window, wherever the focus is). Call once, after the sections are added. */
  void bindJumpKeys() {
    for (int i = 0; i < Math.min(sections.size(), MAX_JUMP_KEYS); i++) {
      int index = i;
      String key = "jumpToSection" + (i + 1);
      getInputMap(WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), key);
      getActionMap().put(key, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          jumpTo(index);
        }
      });
    }
  }

  /**
   * Scrolls section {@code index} (0-based) to the top of the page, selects its first row when nothing is
   * selected there yet, and focuses its table.
   */
  public void jumpTo(int index) {
    SourceSection section = sections.get(index);
    content.scrollRectToVisible(new Rectangle(0, section.getY(), 1, scroll.getViewport().getHeight()));
    JTable table = section.table().table();
    if (table.getSelectedRow() < 0 && table.getRowCount() > 0) {
      table.changeSelection(0, 0, false, false);
    }
    table.requestFocusInWindow();
  }

  /** The sections shown, in page order. */
  public List<SourceSection> sections() {
    return List.copyOf(sections);
  }

  /** The section titled {@code title} (no count); null when the page has none. */
  public SourceSection section(String title) {
    return sections.stream().filter(s -> s.title().equals(title)).findFirst().orElse(null);
  }

  public JScrollPane scrollPane() {
    return scroll;
  }
}
