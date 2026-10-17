package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JPanel;
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
    assertThat(s.heading().text()).isEqualTo("Sold by (3)");
    Theme saved = Theme.current();
    try {
      Theme.setCurrent(Tokens.PARCHMENT);
      assertThat(s.heading().getForeground()).as("the darker text gold on Parchment")
          .isEqualTo(Tokens.PARCHMENT.goldText());
      Theme.setCurrent(Tokens.DARK);
      assertThat(s.heading().getForeground()).isEqualTo(Tokens.DARK.goldText());
    } finally {
      Theme.setCurrent(saved);
    }
    assertThat(s.table().table().getAccessibleContext().getAccessibleName()).isEqualTo("Sold by");
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
  void aLeadAboveTheTableGivesUpHeightBeforeTheTableDoes() {
    SourceSection s = section(1);
    JPanel lead = new JPanel();
    lead.setPreferredSize(new Dimension(300, 200));
    s.addAboveTable(lead);
    Container body = s.table().getParent();
    int tableHeight = s.table().getPreferredSize().height;
    assertThat(body.getPreferredSize().height).isEqualTo(200 + SourceSection.LEAD_GAP + tableHeight);

    body.setSize(300, 150); // measured for a smaller lead than it now wants
    body.doLayout();
    assertThat(s.table().getBounds()).isEqualTo(new Rectangle(0, 150 - tableHeight, 300, tableHeight));
    assertThat(lead.getBounds()).isEqualTo(new Rectangle(0, 0, 300, 150 - SourceSection.LEAD_GAP - tableHeight));

    body.setSize(300, 400); // more room than it wants: the lead takes it, the table stays as tall
    body.doLayout();
    assertThat(s.table().getBounds()).isEqualTo(new Rectangle(0, 400 - tableHeight, 300, tableHeight));
    assertThat(lead.getHeight()).isEqualTo(400 - SourceSection.LEAD_GAP - tableHeight);

    body.setSize(300, tableHeight - 4); // no room at all: the table starts at the top
    body.doLayout();
    assertThat(lead.getHeight()).isZero();
    assertThat(s.table().getBounds()).isEqualTo(new Rectangle(0, 0, 300, tableHeight));
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
