package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.ResultsTableModel;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * An entity page that scrolls as a whole: whatever the page puts at the top (its header), then one
 * {@link SourceSection} per kind of row it has, and any other titled block ({@link #addBlock}, a map, a row of links),
 * in the page type's fixed order once the page calls {@link #addSectionsInOrder} ({@link SectionOrder}). Ctrl+1 to
 * Ctrl+6 jump to the first six sections once the page calls {@link #bindJumpKeys()} after adding them; a jump opens a
 * section the compact window collapsed. A section of rows of one entity kind made here is headed by that
 * kind's 16 px rail icon; a section built elsewhere ({@link #addSection(SourceSection)}, such as the spawn points) is
 * added as it is.
 */
abstract class SourcesPage extends JPanel implements PageState {
  private static final int MAX_JUMP_KEYS = 6;

  final Navigator nav;
  final PageContent content = new PageContent();
  private final JScrollPane scroll = new JScrollPane(content);
  private final List<SourceSection> sections = new ArrayList<>();
  /** The sections and other titled blocks, in the order added and then in the page's order. */
  private final List<Block> blocks = new ArrayList<>();

  /** A part of the page below its header that the page's order places by {@code title}. */
  private record Block(String title, JComponent component) {}

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
    addRowSection(new SourceSection(title, count, table));
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
    addRowSection(section);
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
    addRowSection(new SourceSection(title, loot.size(), table));
    return table;
  }

  /**
   * Adds {@code section}, a section of entity rows, headed by the {@value RailIcons#SMALL} px rail icon of their kind
   * when they are all of one kind ({@link #railTarget}).
   */
  private void addRowSection(SourceSection section) {
    railTarget(section.table())
        .ifPresent(t -> section.heading().setIcon(RailIcons.shared().small(t, RailIcons.SMALL)));
    addSection(section);
  }

  /**
   * The rail section of the entities {@code table} lists: the kind every row's first link points at (rows without a
   * link, such as group headings and dividers, aside), when it has a rail section. Empty for a table without such rows,
   * one that mixes kinds (NPCs and objects that drop an item) and one of objects.
   */
  static Optional<RailTarget> railTarget(LinkTable<?> table) {
    ResultsTableModel<?> model = table.model();
    int column = model.firstLinkColumn();
    if (column < 0) {
      return Optional.empty();
    }
    EntityKind kind = null;
    for (int row = 0; row < model.getRowCount(); row++) {
      Optional<EntityRef> ref = model.refAt(row, column);
      if (ref.isEmpty()) {
        continue;
      }
      if (kind == null) {
        kind = ref.get().kind();
      } else if (kind != ref.get().kind()) {
        return Optional.empty();
      }
    }
    return kind == null ? Optional.empty() : RailTarget.of(kind);
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
    blocks.add(new Block(section.title(), section));
    content.add(section);
  }

  /** Adds {@code component} below the others, a block that {@link #addSectionsInOrder} places by {@code title}. */
  void addBlock(String title, JComponent component) {
    blocks.add(new Block(title, component));
    content.add(component);
  }

  /**
   * Puts the sections and blocks in {@code order} ({@link SectionOrder}): the titles it names in its order, then the
   * others in the order they were added. Whatever came before them on the page (the header) stays on top. Call once
   * every section is added, before {@link #bindJumpKeys}.
   */
  void addSectionsInOrder(List<String> order) {
    List<Block> sorted = SectionOrder.sort(order, blocks, Block::title);
    for (Block b : blocks) {
      content.remove(b.component());
    }
    blocks.clear();
    sections.clear();
    for (Block b : sorted) {
      blocks.add(b);
      content.add(b.component());
      if (b.component() instanceof SourceSection s) {
        sections.add(s);
      }
    }
    content.revalidate();
  }

  /** The icon of a page header's plate for {@code target}: its rail art, else its small icon. */
  static Icon plateIcon(RailTarget target) {
    return RailIcons.shared().plate(target).orElseGet(() -> RailIcons.shared().small(target, RailIcons.SMALL));
  }

  /**
   * Ctrl+n jumps to section n (in the window, wherever the focus is), counting only the sections with rows
   * ({@link #jumpTargets}). Call once, after the sections are added.
   */
  void bindJumpKeys() {
    for (int i = 0; i < Math.min(jumpTargets().size(), MAX_JUMP_KEYS); i++) {
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
   * The sections Ctrl+n jumps to, in page order: those with rows (a section the source filter emptied has only its
   * heading and note), collapsed or not.
   */
  private List<SourceSection> jumpTargets() {
    return sections.stream().filter(s -> !s.isEmptied()).toList();
  }

  /**
   * Scrolls jump target {@code index} (0-based, {@link #jumpTargets}) to the top of the page, selects its first row
   * when nothing is selected there yet, and focuses its table.
   */
  public void jumpTo(int index) {
    SourceSection section = jumpTargets().get(index);
    if (section.isCollapsed()) {
      section.setCollapsed(false);
      content.validate();
    }
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

  /** How far the page is scrolled down, in pixels. */
  private record Scrolled(int y) {}

  @Override
  public Object saveState() {
    return new Scrolled(scroll.getViewport().getViewPosition().y);
  }

  /**
   * Scrolls to where the saved page was: at once when the page is laid out, else once it is (a page just put on
   * screen is laid out on the next pass).
   */
  @Override
  public void restoreState(Object state) {
    if (state instanceof Scrolled s && s.y() > 0) {
      scrollTo(s.y());
      if (!isValid() || content.getHeight() < s.y()) {
        SwingUtilities.invokeLater(() -> scrollTo(s.y()));
      }
    }
  }

  private void scrollTo(int y) {
    scroll.getVerticalScrollBar().setValue(y);
  }
}
