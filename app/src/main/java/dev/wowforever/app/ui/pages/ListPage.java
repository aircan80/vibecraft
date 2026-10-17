package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NoFilter;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.ChipCellRenderer;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A list page opened from the category rail, one scrolling page: the title, a {@link FilterBar} for items, NPCs
 * and quests, then a sortable table of the rows (zones by name), or, for instances, one table per
 * {@linkplain LevelBands level band}, each table headed by the list's 16 px rail icon. A click on a name opens it.
 * Filter changes go to {@code onFilter}; the
 * controller loads the rows and hands them to {@link #showRows}.
 */
public final class ListPage extends SourcesPage {
  /** The heading of the single results table. */
  static final String RESULTS = "Results";
  public static final String FOREVER_FOOTNOTE = "Announced by Blizzard, not yet in this client build: Drowned City, Krol'dok Stronghold, Alcaz Island Prison, Blackmaw Hold, The Shaper's Terrace; raids Barrow Deeps and Hyjal Summit (open 9 Dec 2026).";
  public static final String FOREVER_SOURCE = "https://www.wowhead.com/forever/blue-tracker/news/eu/24303313";
  private WrappingText foreverFootnote;
  private JButton foreverSource;

  private final HeaderPanel header;
  private final FilterBar filterBar;
  private final LinkTable<?> results;
  private final SourceSection section;
  private final JLabel emptyNote;
  private final String emptyText;
  private ViewTarget.List target;

  /**
   * A page showing {@code rows} ({@link ListRows#load} of {@code target}), its filter bar set from {@code options};
   * level changes reach {@code onFilter} once the spinners rest for {@link FilterBar#LEVEL_DELAY_MILLIS}.
   */
  public ListPage(ViewTarget.List target, ListOptions options, List<?> rows, Navigator nav,
      Consumer<ListFilter> onFilter) {
    this(target, options, rows, nav, onFilter, MasterList.timerDelay(FilterBar.LEVEL_DELAY_MILLIS));
  }

  /** As above, with {@code levelDelay} debouncing the level spinners (a seam for tests). */
  public ListPage(ViewTarget.List target, ListOptions options, List<?> rows, Navigator nav,
      Consumer<ListFilter> onFilter, MasterList.Delay levelDelay) {
    this(target, options, rows, nav, onFilter, levelDelay, ListPage::browse);
  }

  /** External announcement navigation is user-initiated; the port keeps tests offline. */
  public ListPage(ViewTarget.List target, ListOptions options, List<?> rows, Navigator nav,
      Consumer<ListFilter> onFilter, MasterList.Delay levelDelay, Consumer<URI> sourceOpener) {
    super(nav);
    this.target = Objects.requireNonNull(target, "target");
    emptyText = target.filter() instanceof NoFilter ? "No entries." : "Nothing matches these filters.";
    emptyNote = PageContent.mutedLabel(emptyText);
    header = new HeaderPanel(title(target.kind()), "");
    header.setAlignmentX(LEFT_ALIGNMENT);
    content.add(header);
    filterBar = target.filter() instanceof NoFilter ? null : new FilterBar(target.filter(), options, onFilter, levelDelay);
    if (filterBar != null) {
      content.add(filterBar);
    }
    if (target.kind() == EntityKind.INSTANCE) {
      results = null;
      section = null;
      for (LevelBands.Band band : LevelBands.of(cast(rows))) {
        LinkTable<InstanceSummary> table = addSection(band.title(), band.instances().size(), Columns.instances(), band.instances());
        var name = table.table().getColumnModel().getColumn(table.table().getColumnModel().getColumnIndex("Name"));
        ChipCellRenderer.install(name, row -> InstanceSummary.CLIENT.equals(table.model()
            .rowAt(table.table().convertRowIndexToModel(row)).source()) ? "New in Forever" : null);
      }
    } else {
      results = table(target.kind(), nav);
      setRows(results, rows);
      section = new SourceSection(RESULTS, rows.size(), results);
      results.table().getAccessibleContext().setAccessibleName(title(target.kind()));
      addSection(section);
    }
    emptyNote.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    emptyNote.setVisible(rows.isEmpty());
    content.add(emptyNote);
    if (target.kind() == EntityKind.INSTANCE) {
      JPanel footnote = new JPanel(new BorderLayout(0, 4));
      footnote.setOpaque(false);
      footnote.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
      foreverFootnote = new WrappingText("2026-10-17 research snapshot · Client build 1.60.1.70450. "
          + FOREVER_FOOTNOTE, "Announced dungeons research snapshot");
      foreverSource = new JButton("Blizzard announcement ↗");
      foreverSource.addActionListener(e -> {
        try {
          sourceOpener.accept(URI.create(FOREVER_SOURCE));
        } catch (RuntimeException failure) {
          foreverSource.setToolTipText("Could not open the browser: " + failure.getMessage());
        }
      });
      footnote.add(foreverFootnote, BorderLayout.CENTER);
      footnote.add(foreverSource, BorderLayout.SOUTH);
      content.add(footnote);
    }
    Icon icon = RailIcons.shared().small(RailTarget.of(target.kind()).orElseThrow(), RailIcons.SMALL);
    for (SourceSection s : sections()) { // the rail icon of the list, rows or not
      s.heading().setIcon(icon);
      s.setRowLimited(false); // a rail list (or a level band) is the page itself: never cut at eight rows
    }
    bindJumpKeys();
  }

  private static void browse(URI uri) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      throw new IllegalStateException("Browser opening is unavailable");
    }
    try {
      Desktop.getDesktop().browse(uri);
    } catch (IOException failure) {
      throw new IllegalStateException(failure.getMessage(), failure);
    }
  }

  public WrappingText foreverFootnote() { return foreverFootnote; }
  public JButton foreverSource() { return foreverSource; }

  /** The page title of a list of {@code kind}, as the rail names it. */
  public static String title(EntityKind kind) {
    return switch (kind) {
      case ITEM -> "Items";
      case NPC -> "NPCs";
      case QUEST -> "Quests";
      case ZONE -> "Zones";
      case INSTANCE -> "Dungeons & Raids";
      default -> throw new IllegalArgumentException("There is no list of " + kind.label() + " entities");
    };
  }

  /**
   * Shows the rows of {@code next} (same kind, another filter) in the results table, keeping its sort order. Only
   * pages with a filter bar reload.
   */
  public void showRows(ViewTarget.List next, List<?> rows) {
    if (results == null || next.kind() != target.kind()) {
      throw new IllegalStateException("A " + title(target.kind()) + " page cannot show " + next);
    }
    target = next;
    setRows(results, rows);
    section.setCount(rows.size());
    emptyNote.setText(emptyText);
    emptyNote.setVisible(rows.isEmpty());
    content.revalidate();
    content.repaint();
  }

  /**
   * Shows that the rows of {@code next} could not be loaded ({@code message}): the table empties and the note under
   * it tells the failure, so the rows of the old filter do not stand under the new one.
   */
  public void showFailure(ViewTarget.List next, String message) {
    if (results == null || next.kind() != target.kind()) {
      throw new IllegalStateException("A " + title(target.kind()) + " page cannot show " + next);
    }
    target = next;
    setRows(results, List.of());
    section.setCount(0);
    emptyNote.setText("Could not load these rows: " + message);
    emptyNote.setVisible(true);
    content.revalidate();
    content.repaint();
  }

  /** What the page shows: its kind and the filter of the rows on screen. */
  public ViewTarget.List target() {
    return target;
  }

  public HeaderPanel header() {
    return header;
  }

  /** The filter bar; null for zones and instances. */
  public FilterBar filterBar() {
    return filterBar;
  }

  /** The results table; null for instances, which have one table per level band ({@link #sections()}). */
  public LinkTable<?> results() {
    return results;
  }

  /** The section holding {@link #results()}; null for instances. */
  public SourceSection section() {
    return section;
  }

  /** The note shown when no row matches. */
  public JLabel emptyNote() {
    return emptyNote;
  }

  private static LinkTable<?> table(EntityKind kind, Navigator nav) {
    return switch (kind) {
      case ITEM -> LinkTable.inline(Columns.items(), nav);
      case NPC -> LinkTable.inline(Columns.npcs(), nav);
      case QUEST -> LinkTable.inline(Columns.quests(), nav);
      case ZONE -> LinkTable.inline(Columns.zones(), nav);
      default -> throw new IllegalArgumentException("No list table of " + kind.label() + " entities");
    };
  }

  /** {@code rows} come from {@link ListRows#load} for the table's kind, so they are of its row type. */
  @SuppressWarnings("unchecked")
  private static <T> void setRows(LinkTable<T> table, List<?> rows) {
    table.setRows((List<T>) rows);
  }

  @SuppressWarnings("unchecked")
  private static List<InstanceSummary> cast(List<?> rows) {
    return (List<InstanceSummary>) rows;
  }
}
