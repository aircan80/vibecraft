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
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/**
 * A list page opened from the category rail, one scrolling page: the title, a {@link FilterBar} for items, NPCs
 * and quests, then a sortable table of the rows (zones by name), or, for instances, one table per
 * {@linkplain LevelBands level band}. A click on a name opens it. Filter changes go to {@code onFilter}; the
 * controller loads the rows and hands them to {@link #showRows}.
 */
public final class ListPage extends SourcesPage {
  /** The heading of the single results table. */
  static final String RESULTS = "Results";

  private final HeaderPanel header;
  private final FilterBar filterBar;
  private final LinkTable<?> results;
  private final SourceSection section;
  private final JLabel emptyNote;
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
    super(nav);
    this.target = Objects.requireNonNull(target, "target");
    emptyNote = PageContent.mutedLabel(target.filter() instanceof NoFilter ? "No entries."
        : "Nothing matches these filters.");
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
        addSection(band.title(), band.instances().size(), Columns.instances(), band.instances());
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
    bindJumpKeys();
  }

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
    emptyNote.setVisible(rows.isEmpty());
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
