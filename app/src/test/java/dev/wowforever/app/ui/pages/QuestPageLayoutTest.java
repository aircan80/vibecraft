package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.pages.quest.QuestTestData;
import java.awt.Component;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestPageLayoutTest {
  private static QuestPage page(long id, boolean compact) {
    var loaded = QuestTestData.load(id);
    var page = new QuestPage(loaded.data(), loaded.maps(), loaded.zoneNames(), t -> {}, text -> {});
    JPanel host = new JPanel();
    host.putClientProperty(CompactColumns.KEY, compact);
    host.add(page);
    page.setSize(compact ? 420 : 1216, compact ? 720 : 680);
    settle(page);
    return page;
  }

  private static void settle(QuestPage page) {
    for (int i = 0; i < 6; i++) Tables.layOut(page);
  }

  /** Ignore ordinary offscreen scroll content, but require each nested parent to allocate the whole field. */
  private static void assertInternallyUnclipped(JComponent component) {
    Rectangle actual = new Rectangle(0, 0, component.getWidth(), component.getHeight());
    assertThat(actual.width).isPositive();
    assertThat(actual.height).isPositive();
    Component child = component;
    for (var parent = component.getParent(); parent != null && !(parent instanceof JViewport); parent = parent.getParent()) {
      actual.translate(child.getX(), child.getY());
      assertThat(new Rectangle(0, 0, parent.getWidth(), parent.getHeight()).contains(actual))
          .as("%s fits inside %s: %s within %s", component.getClass().getSimpleName(),
              parent.getClass().getSimpleName(), actual, parent.getBounds()).isTrue();
      child = parent;
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {176, 166, 6502, 7787, 7791})
  void actualGiverRowsAndObjectiveLocationFitTheFullScanWithCopyButtonHeadroom(long id) {
    var page = page(id, false);
    for (String title : new String[]{"Given by", "Turned in to"}) {
      var section = page.section(title);
      if (section == null) continue;
      var table = section.table().table();
      assertInternallyUnclipped(table);
      for (int row = 0; row < table.getRowCount(); row++) {
        Rectangle cell = SwingUtilities.convertRectangle(table, table.getCellRect(row, 0, true), page);
        assertThat(cell.y + cell.height).as("quest%s %s row%s visible with27px header headroom", id, title, row)
            .isLessThanOrEqualTo(640);
      }
    }
    if (!page.objectiveRows().rows().isEmpty()) {
      var row = page.objectiveRows().rows().getFirst();
      JComponent where = row.whereLink().isVisible() ? row.whereLink() : row.whereLine();
      assertInternallyUnclipped(where);
      assertThat(Tables.boundsIn(where, page).y + where.getHeight()).isLessThanOrEqualTo(640);
    } else {
      assertInternallyUnclipped(page.objectives());
      assertThat(page.objectives().getHeight()).isLessThan(100);
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {176, 166, 6502, 7787, 7791})
  void compactCoreFieldsAreNotClippedAndNaturalHeightsStayStableAfterResize(long id) {
    var page = page(id, true);
    if (!page.objectiveRows().rows().isEmpty()) {
      var row = page.objectiveRows().rows().getFirst();
      assertInternallyUnclipped(row.whereLink().isVisible() ? row.whereLink() : row.whereLine());
    } else {
      assertInternallyUnclipped(page.objectives());
      assertThat(page.objectives().getHeight()).isLessThan(100);
      assertThat(Tables.boundsIn(page.section("Given by"), page).y).isLessThan(750);
    }
    for (int width : new int[]{1216, 420, 1216, 420}) {
      page.setSize(width, 720);
      settle(page);
      int height = page.body().getPreferredSize().height;
      settle(page);
      assertThat(page.body().getPreferredSize().height).isEqualTo(height);
    }
  }
}
