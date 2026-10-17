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
import dev.wowforever.app.ui.detail.CompactColumns;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.BoxLayout;
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

  /** Whether {@code c} and every parent up to {@code top} is visible. */
  private static boolean visibleIn(Component c, Component top) {
    for (Component p = c; p != null; p = p.getParent()) {
      if (!p.isVisible()) {
        return false;
      }
      if (p == top) {
        return true;
      }
    }
    return true;
  }

  @Test
  void eightRowsThenShowAll() {
    SourceSection s = section(34);
    JTable table = s.table().table();
    int header = table.getTableHeader().getPreferredSize().height;
    assertThat(SourceSection.ROW_LIMIT).isEqualTo(8);
    assertThat(s.shownRows()).isEqualTo(8);
    assertThat(s.table().getPreferredSize().height).isEqualTo(header + 8 * table.getRowHeight());
    assertThat(table.getRowCount()).as("the rows are all there, the rest below the fold").isEqualTo(34);
    assertThat(s.showAllButton().isVisible()).isTrue();
    assertThat(s.showAllButton().getText()).isEqualTo("Show all 34");
    s.showAllButton().doClick();
    assertThat(s.shownRows()).isEqualTo(34);
    assertThat(s.table().getPreferredSize().height).isEqualTo(header + 34 * table.getRowHeight());
    assertThat(s.showAllButton().isVisible()).isFalse();
    assertThat(s.heading().text()).isEqualTo("Sold by (34)");
  }

  @Test
  void eightRowsOrFewerShowNoButton() {
    SourceSection s = section(8);
    assertThat(s.shownRows()).isEqualTo(8);
    assertThat(s.showAllButton().isVisible()).isFalse();
  }

  @Test
  void selectingARowBelowTheFoldShowsAll() {
    SourceSection s = section(34);
    s.table().table().changeSelection(8, 0, false, false); // arrowing down past the eighth row
    assertThat(s.shownRows()).isEqualTo(34);
    assertThat(s.showAllButton().isVisible()).isFalse();
  }

  @Test
  void rowsGivenLaterFollowTheLimit() {
    SourceSection s = section(3);
    assertThat(s.showAllButton().isVisible()).isFalse();
    @SuppressWarnings("unchecked")
    LinkTable<ItemSource> t = (LinkTable<ItemSource>) s.table();
    t.setRows(vendors(12));
    s.setCount(12);
    assertThat(s.shownRows()).isEqualTo(8);
    assertThat(s.showAllButton().isVisible()).isTrue();
    assertThat(s.showAllButton().getText()).isEqualTo("Show all 12");
  }

  /** {@code data} vendors with a divider row (a vendor named "—") after every third. */
  private static SourceSection withDividers(int data) {
    List<ItemSource> rows = new ArrayList<>();
    for (ItemSource v : vendors(data)) {
      rows.add(v);
      if (v.ref().id() % 3 == 0) {
        rows.add(new ItemSource(ItemSource.VENDOR, new EntityRef(NPC, 1000 + v.ref().id()), "—"));
      }
    }
    LinkTable<ItemSource> t = LinkTable.inline(Columns.itemSources(), v -> {});
    t.setRows(rows);
    DividerRows.install(t, s -> "—".equals(s.name()));
    return new SourceSection("Sold by", data, t);
  }

  @Test
  void theCutCountsDataRowsAndTheButtonReadsTheHeadingsCount() {
    SourceSection s = withDividers(10); // 10 vendors, dividers after the 3rd, 6th and 9th: 13 rows
    JTable table = s.table().table();
    assertThat(table.getRowCount()).isEqualTo(13);
    assertThat(s.shownRows()).as("eight vendors and the two dividers among them").isEqualTo(10);
    int header = table.getTableHeader().getPreferredSize().height;
    assertThat(s.table().getPreferredSize().height).isEqualTo(header + 10 * table.getRowHeight());
    assertThat(s.showAllButton().getText()).as("the heading's count, not the rows'").isEqualTo("Show all 10");
    assertThat(s.heading().text()).isEqualTo("Sold by (10)");
    assertThat(s.showAllButton().isVisible()).isTrue();

    SourceSection eight = withDividers(8); // 8 vendors and 2 dividers: 10 rows, but nothing to cut
    assertThat(eight.table().table().getRowCount()).isEqualTo(10);
    assertThat(eight.shownRows()).isEqualTo(10);
    assertThat(eight.showAllButton().isVisible()).isFalse();
  }

  @Test
  void aSectionThatOptsOutShowsEveryRow() {
    SourceSection s = section(34);
    s.setRowLimited(false);
    assertThat(s.isRowLimited()).isFalse();
    assertThat(s.shownRows()).isEqualTo(34);
    assertThat(s.showAllButton().isVisible()).isFalse();
  }

  @Test
  void aSectionLeftWithoutRowsStaysSoThroughCollapsing() {
    LinkTable<ItemSource> t = LinkTable.inline(Columns.itemSources(), v -> {});
    SourceSection s = new SourceSection("Dropped by", 0, t);
    s.markEmptied();
    assertThat(s.isEmptied()).isTrue();
    assertThat(s.table().isVisible()).isFalse();
    s.setCollapsible(true);
    s.setCollapsed(true);
    s.setCollapsed(false);
    assertThat(s.table().isVisible()).as("still emptied").isFalse();
    SourceSection late = section(3); // emptied while collapsed
    late.setCollapsible(true);
    late.setCollapsed(true);
    late.markEmptied();
    late.setCollapsed(false);
    assertThat(late.table().isVisible()).isFalse();
  }

  @Test
  void rowsGivenWhileCollapsedGetTheButtonOnOpening() {
    SourceSection s = section(3);
    s.setCollapsible(true);
    s.setCollapsed(true);
    @SuppressWarnings("unchecked")
    LinkTable<ItemSource> t = (LinkTable<ItemSource>) s.table();
    t.setRows(vendors(20));
    s.setCount(20);
    s.setCollapsed(false);
    assertThat(s.shownRows()).isEqualTo(8);
    assertThat(visibleIn(s.showAllButton(), s)).isTrue();
    assertThat(s.showAllButton().getText()).isEqualTo("Show all 20");
  }

  @Test
  void theHeadingSaysWhetherItsSectionIsOpen() {
    SourceSection s = section(4);
    var context = s.heading().getAccessibleContext();
    assertThat(context.getAccessibleName()).as("a heading that does not fold").isEqualTo("Sold by (4)");
    s.setCollapsible(true);
    assertThat(context.getAccessibleName()).isEqualTo("Sold by (4), expanded");
    assertThat(context.getAccessibleRole()).isEqualTo(AccessibleRole.TOGGLE_BUTTON);
    assertThat(context.getAccessibleStateSet().contains(AccessibleState.EXPANDED)).isTrue();
    List<Object> fired = new ArrayList<>();
    context.addPropertyChangeListener(e -> fired.add(e.getPropertyName() + " " + e.getNewValue()));
    s.setCollapsed(true);
    assertThat(context.getAccessibleName()).isEqualTo("Sold by (4), collapsed");
    assertThat(context.getAccessibleStateSet().contains(AccessibleState.COLLAPSED)).isTrue();
    assertThat(context.getAccessibleStateSet().contains(AccessibleState.EXPANDED)).isFalse();
    assertThat(fired).contains("AccessibleState collapsed", "AccessibleName Sold by (4), collapsed");
    s.setCount(5);
    assertThat(context.getAccessibleName()).isEqualTo("Sold by (5), collapsed");
    s.setCollapsible(false);
    assertThat(context.getAccessibleName()).isEqualTo("Sold by (5)");
  }

  @Test
  void takenOutAndPutBackASectionKeepsWhatTheUserOpened() {
    JPanel compact = new JPanel();
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    JPanel pane = new JPanel();
    compact.add(pane);
    SourceSection loot = section(3);
    SourceSection quests = section(4);
    pane.add(loot);
    pane.add(quests);
    assertThat(quests.isCollapsed()).isTrue();
    quests.setCollapsed(false); // the user opens it
    pane.removeAll(); // a page rebuilding its pane, as the instance page does for every encounter
    pane.add(loot);
    pane.add(quests);
    assertThat(quests.isCollapsed()).isFalse();
    assertThat(quests.isCollapsible()).isTrue();
  }

  @Test
  void compactCollapsesAllButFirst() {
    JPanel page = new JPanel();
    page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS));
    SourceSection first = section(3);
    SourceSection second = section(4);
    SourceSection third = section(12);
    page.add(first);
    page.add(second);
    page.add(third);
    assertThat(List.of(first, second, third)).as("the full window shows every section open")
        .allSatisfy(s -> assertThat(s.isCollapsed()).isFalse());

    JPanel compact = new JPanel();
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compact.add(page);
    assertThat(first.isCollapsed()).isFalse();
    assertThat(visibleIn(first.table(), first)).isTrue();
    for (SourceSection s : List.of(second, third)) {
      assertThat(s.isCollapsible()).isTrue();
      assertThat(s.isCollapsed()).isTrue();
      assertThat(visibleIn(s.heading(), s)).as("the heading only").isTrue();
      assertThat(visibleIn(s.table(), s)).isFalse();
      assertThat(visibleIn(s.showAllButton(), s)).isFalse();
    }
    assertThat(second.heading().text()).as("with the count").isEqualTo("Sold by (4)");

    second.heading().dispatchEvent(new MouseEvent(second.heading(), MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false,
        MouseEvent.BUTTON1));
    assertThat(second.isCollapsed()).as("a click expands it").isFalse();
    assertThat(visibleIn(second.table(), second)).isTrue();
    second.heading().dispatchEvent(new MouseEvent(second.heading(), MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false,
        MouseEvent.BUTTON1));
    assertThat(second.isCollapsed()).as("and another collapses it again").isTrue();

    compact.remove(page);
    assertThat(List.of(first, second, third)).as("back in the full window, every section opens")
        .allSatisfy(s -> {
          assertThat(s.isCollapsed()).isFalse();
          assertThat(s.isCollapsible()).isFalse();
        });
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
