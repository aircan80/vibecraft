package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.DividerRows;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.table.TableColumnModel;

/**
 * The item tables of the NPC, object, quest and instance pages ({@link Columns#lootRated()}): columns that say
 * nothing are left out ({@link VisibleColumns}), rates get a bar (with a tooltip of the rate's sources when they are
 * given) and origins a chip.
 */
final class LootTables {
  /** Highest rate first, unknown rates last, then by name. */
  static final Comparator<LootRow> BY_RATE = Comparator
      .comparing(LootRow::dropPct, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(LootRow::displayName, String.CASE_INSENSITIVE_ORDER);

  private LootTables() {}

  /** The rate sources of any row when none are known. */
  static final Function<Object, List<RateSource>> NO_SOURCES = row -> null;

  /** A sortable table of {@code loot}. */
  static LinkTable<LootRow> plain(List<LootRow> loot, Navigator nav) {
    LinkTable<LootRow> table = LinkTable.inline(VisibleColumns.of(Columns.lootRated(), loot, null), nav);
    table.setRows(loot);
    decorate(table, NO_SOURCES);
    return table;
  }

  /**
   * {@code loot} grouped by {@code groupOf} under heading rows, the groups in {@code order}, each sorted by
   * {@code within} (null keeps the given order). The grouping fixes the order, so the table is not sortable.
   */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Function<LootRow, String> groupOf,
      List<String> order, Comparator<LootRow> within, Navigator nav) {
    return grouped(loot, groupOf, order, within, NO_SOURCES, nav);
  }

  /**
   * {@link #grouped(List, Function, List, Comparator, Navigator)} whose rate cells' tooltips list the sources
   * {@code sources} gives for a row (looked up while rendering: only sources loaded beforehand).
   */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Function<LootRow, String> groupOf,
      List<String> order, Comparator<LootRow> within, Function<? super LootRow, List<RateSource>> sources,
      Navigator nav) {
    List<Column<LootRow>> cols = VisibleColumns.of(Columns.lootRated(), loot, null);
    LinkTable<GroupedRows.Row<LootRow>> table = LinkTable.inline(GroupedRows.columns(cols), nav);
    table.setRows(GroupedRows.of(loot, groupOf, order, within));
    decorate(table, row -> {
      LootRow entry = GroupedRows.Entry.valueOf(row, LootRow.class);
      return entry == null ? null : sources.apply(entry);
    });
    DividerRows.install(table, GroupedRows::isHeading);
    return table;
  }

  /**
   * The loaded rate sources of a loot row of NPC {@code npcId}'s loot, from {@code sources} (as
   * {@link dev.wowforever.app.db.ItemRepository#sourcesOfNpc} groups them); null when the row has none
   * ({@code sourceCount} 0) or none were loaded.
   */
  static Function<LootRow, List<RateSource>> sourcesOf(long npcId, Map<RateSource.Key, List<RateSource>> sources) {
    return row -> row.sourceCount() > 0 ? sources.get(new RateSource.Key(npcId, row.item().id())) : null;
  }

  /** Rate bars (tooltips from {@code sources}) and origin chips on the Rate and Origin columns that are shown. */
  private static void decorate(LinkTable<?> table, Function<Object, List<RateSource>> sources) {
    TableColumnModel columns = table.table().getColumnModel();
    for (int i = 0; i < columns.getColumnCount(); i++) {
      switch (String.valueOf(columns.getColumn(i).getHeaderValue())) {
        case Columns.RATE -> RateBarRenderer.install(columns.getColumn(i), sources);
        case VisibleColumns.ORIGIN -> OriginChip.install(columns.getColumn(i));
        default -> { }
      }
    }
  }
}
