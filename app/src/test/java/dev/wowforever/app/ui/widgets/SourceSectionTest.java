package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SourceSectionTest {
  private static List<ItemSource> vendors(int n) {
    return java.util.stream.LongStream.rangeClosed(1, n)
        .mapToObj(i -> new ItemSource(ItemSource.VENDOR, new EntityRef(NPC, i), "Vendor " + i)).toList();
  }

  private static SourceSection section(int rows) {
    LinkTable<ItemSource> t = LinkTable.inline(Columns.itemSources(), v -> {});
    t.setRows(vendors(rows));
    return new SourceSection("Sold by", rows, t);
  }

  @Test
  void headingNamesTitleAndCountInGold() {
    SourceSection s = section(3);
    assertThat(s.title()).isEqualTo("Sold by");
    assertThat(s.heading().getText()).isEqualTo("Sold by (3)");
    assertThat(s.heading().getForeground()).isEqualTo(Theme.current().accentGold());
  }

  @Test
  void tableIsAsTallAsItsRowsWithoutAScrollPane() {
    SourceSection s = section(5);
    JTable table = s.table().table();
    assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, table)).isNull();
    assertThat(SwingUtilities.isDescendingFrom(table.getTableHeader(), s)).isTrue();
    assertThat(s.table().getPreferredSize().height)
        .isEqualTo(5 * table.getRowHeight() + table.getTableHeader().getPreferredSize().height);
  }

  @Test
  void neverStretchesTaller() {
    SourceSection s = section(2);
    assertThat(s.getMaximumSize().height).isEqualTo(s.getPreferredSize().height);
    assertThat(s.getMaximumSize().width).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void rejectsATableWithItsOwnScrollPane() {
    LinkTable<ItemSource> scrolled = new LinkTable<>(Columns.itemSources(), v -> {});
    assertThatThrownBy(() -> new SourceSection("Sold by", 0, scrolled)).isInstanceOf(IllegalArgumentException.class);
  }
}
