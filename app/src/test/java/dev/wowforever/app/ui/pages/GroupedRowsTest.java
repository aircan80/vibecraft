package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GroupedRowsTest {
  private static final LootRow LETTER =
      new LootRow(new EntityRef(ITEM, 2874), "An Unsent Letter", 16, 60.0, "both", 5, true, 4);
  private static final LootRow SWORD = new LootRow(new EntityRef(ITEM, 1), "Sword", 20, 5.0, "questie", null);

  private static List<GroupedRows.Row<LootRow>> rows() {
    return GroupedRows.of(List.of(LETTER, SWORD), l -> "All", List.of("All"), null); // heading, letter, sword
  }

  @Test
  void groupedRateColumnsFlagTheEntriesWhoseSourcesDisagree() {
    List<Column<GroupedRows.Row<LootRow>>> cols = GroupedRows.columns(Columns.lootRated());
    Column<GroupedRows.Row<LootRow>> rate = cols.get(2);
    assertThat(rate.title()).isEqualTo(Columns.RATE);
    List<GroupedRows.Row<LootRow>> rows = rows();
    assertThat(rate.disagreesFor(rows.get(0))).as("heading").isFalse();
    assertThat(rate.disagreesFor(rows.get(1))).isTrue();
    assertThat(rate.disagreesFor(rows.get(2))).isFalse();
    assertThat(cols.get(0).disagreesFor(rows.get(1))).as("not a rate column").isFalse();
  }

  @Test
  void groupedColumnsKeepTheQualityOfTheirEntries() {
    Column<LootRow> name = new Column<>("Item", String.class, LootRow::displayName,
        l -> Optional.of(l.item()), null, l -> l.item().id() == 2874 ? 1 : 4);
    Column<GroupedRows.Row<LootRow>> grouped = GroupedRows.columns(List.of(name)).get(0);
    List<GroupedRows.Row<LootRow>> rows = rows();
    assertThat(grouped.qualityFor(rows.get(0))).as("heading").isNull();
    assertThat(grouped.qualityFor(rows.get(1))).isEqualTo(1);
    assertThat(grouped.qualityFor(rows.get(2))).isEqualTo(4);
    assertThat(GroupedRows.columns(Columns.lootRated()).get(0).qualityFor(rows.get(1))).isNull();
  }
}
