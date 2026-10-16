package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.DividerRows;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.TableColumnModel;

/**
 * The item tables of the NPC, object, quest and instance pages ({@link Columns#lootRated()}): columns that say
 * nothing are left out ({@link VisibleColumns}), rates get a bar and origins a chip.
 */
final class LootTables {
  /** Highest rate first, unknown rates last, then by name. */
  static final Comparator<LootRow> BY_RATE = Comparator
      .comparing(LootRow::dropPct, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(LootRow::displayName, String.CASE_INSENSITIVE_ORDER);

  private LootTables() {}

  /** A sortable table of {@code loot}. */
  static LinkTable<LootRow> plain(List<LootRow> loot, Navigator nav) {
    LinkTable<LootRow> table = LinkTable.inline(VisibleColumns.of(Columns.lootRated(), loot, null), nav);
    table.setRows(loot);
    decorate(table);
    return table;
  }

  /**
   * {@code loot} grouped by {@code groupOf} under heading rows, the groups in {@code order}, each sorted by
   * {@code within} (null keeps the given order). The grouping fixes the order, so the table is not sortable.
   */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Function<LootRow, String> groupOf,
      List<String> order, Comparator<LootRow> within, Navigator nav) {
    List<Column<LootRow>> cols = VisibleColumns.of(Columns.lootRated(), loot, null);
    LinkTable<GroupedRows.Row<LootRow>> table = LinkTable.inline(GroupedRows.columns(cols), nav);
    table.setRows(GroupedRows.of(loot, groupOf, order, within));
    decorate(table);
    DividerRows.install(table, GroupedRows::isHeading);
    return table;
  }

  /** Rate bars and origin chips on the Rate and Origin columns that are shown. */
  private static void decorate(LinkTable<?> table) {
    TableColumnModel columns = table.table().getColumnModel();
    for (int i = 0; i < columns.getColumnCount(); i++) {
      switch (String.valueOf(columns.getColumn(i).getHeaderValue())) {
        case Columns.RATE -> RateBarRenderer.install(columns.getColumn(i));
        case VisibleColumns.ORIGIN -> OriginChip.install(columns.getColumn(i));
        default -> { }
      }
    }
  }
}
