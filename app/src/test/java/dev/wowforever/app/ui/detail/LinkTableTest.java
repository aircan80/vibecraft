package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.DropRow;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.widgets.Keys;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LinkTableTest {

  private static DropRow dropSource(long npcId, String name) {
    return dropSource(npcId, name, null);
  }

  private static DropRow dropSource(long npcId, String name, Double pct) {
    return new DropRow.Source(
        new DropSource(new EntityRef(NPC, npcId), name, 30, 31, "Ragefire Chasm", 2437L, pct, "questie", "both"));
  }

  private static LootRow loot(long itemId, Double pct) {
    return new LootRow(new EntityRef(ITEM, itemId), "Item " + itemId, 20, pct, "questie", null);
  }

  @Test
  void activateRowOpensLink() {
    List<ViewTarget> opened = new ArrayList<>();
    var t = new LinkTable<>(Columns.dropSourcesRanked(), opened::add);
    t.setRows(List.of(dropSource(11520, "Taragaman the Hungerer")));
    t.activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 11520)));
  }

  @Test
  void dropSourceColumnsFormatLevelZoneAndRate() {
    var t = new LinkTable<>(Columns.dropSourcesRanked(), v -> {});
    t.setRows(List.of(dropSource(1, "A", 56.0712), dropSource(2, null, null)));
    assertThat(t.rowCount()).isEqualTo(2);
    assertThat(t.table().getColumnName(0)).isEqualTo("Source");
    assertThat(t.table().getValueAt(0, 1)).hasToString("30–31");
    assertThat(t.table().getValueAt(0, 2)).isEqualTo("Ragefire Chasm");
    assertThat(t.table().getValueAt(0, 3)).isEqualTo(56.0712);
    assertThat(t.table().getValueAt(1, 0)).isEqualTo("Unknown NPC #2");
    assertThat(t.table().getValueAt(1, 3)).isNull();
    assertThat(t.model().refAt(0, 2)).contains(new EntityRef(ZONE, 2437));
  }

  @Test
  void dropPercentSortsNumericallyWithUnknownLast() {
    var t = new LinkTable<>(Columns.lootRows(), v -> {});
    t.setRows(List.of(loot(1, 60.0), loot(2, null), loot(3, 5.0), loot(4, 12.5)));
    assertThat(t.table().getColumnName(2)).isEqualTo("Drop %");
    t.table().getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.ASCENDING)));
    List<Object> shown = new ArrayList<>();
    for (int r = 0; r < t.rowCount(); r++) {
      shown.add(t.table().getValueAt(r, 2));
    }
    assertThat(shown).containsExactly("5.00 %", "12.50 %", "60.00 %", "—");
  }

  @Test
  void activationFollowsTheSortedViewAndEnterOpens() {
    List<ViewTarget> opened = new ArrayList<>();
    var t = new LinkTable<>(Columns.dropSourcesRanked(), opened::add);
    t.setRows(List.of(dropSource(2, "Zed"), dropSource(1, "Abe")));
    t.table().getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
    t.table().setRowSelectionInterval(0, 0);
    Object key = t.table().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .get(KeyStroke.getKeyStroke("ENTER"));
    t.table().getActionMap().get(key).actionPerformed(new ActionEvent(t.table(), ActionEvent.ACTION_PERFORMED, ""));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 1)));
  }

  @Test
  void tabsScrollInsteadOfWrapping() {
    JTabbedPane tabs = new JTabbedPane();
    LinkTable.addTab(tabs, "Drops", Columns.lootRows(), List.of(), v -> {});
    assertThat(tabs.getTabLayoutPolicy()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
  }

  @Test
  void nameColumnsAreWideAndNumberColumnsNarrow() {
    var loot = new LinkTable<>(Columns.lootRows(), v -> {}).table().getColumnModel();
    assertThat(loot.getColumn(0).getPreferredWidth()).as("Item").isEqualTo(260);
    assertThat(loot.getColumn(1).getPreferredWidth()).as("Item level").isEqualTo(70);
    assertThat(loot.getColumn(2).getPreferredWidth()).as("Drop %").isEqualTo(70);
    assertThat(loot.getColumn(4).getPreferredWidth()).as("Slot").isEqualTo(70);
    var drops = new LinkTable<>(Columns.dropSourcesRanked(), v -> {}).table().getColumnModel();
    assertThat(drops.getColumn(0).getPreferredWidth()).as("Source").isEqualTo(260);
    assertThat(drops.getColumn(1).getPreferredWidth()).as("Level").isEqualTo(70);
  }

  @Test
  void linkColumnsUseTheLinkRenderer() {
    var t = new LinkTable<>(Columns.dropSourcesRanked(), v -> {});
    assertThat(t.table().getColumnModel().getColumn(0).getCellRenderer()).isInstanceOf(LinkRenderer.class);
    assertThat(t.table().getColumnModel().getColumn(2).getCellRenderer()).isInstanceOf(LinkRenderer.class);
    assertThat(t.table().getColumnModel().getColumn(1).getCellRenderer()).isNull();
  }

  @Test
  void ctrlCCopiesTheSelectedRowsNameAndCtrlShiftCItsId() {
    List<String> copied = new ArrayList<>();
    var t = new LinkTable<>(Columns.lootRows(), v -> {});
    t.setClipboard(copied::add);
    t.setRows(List.of(loot(19019, 5.0), loot(2, 1.0)));
    t.table().changeSelection(1, 0, false, false);
    assertThat(Keys.press(t.table(), "ctrl C")).isTrue();
    assertThat(Keys.press(t.table(), "shift ctrl C")).isTrue();
    assertThat(copied).containsExactly("Item 2", "item:2");
  }

  @Test
  void rowCopyKeysPassOnWithoutASelectedRow() {
    List<String> copied = new ArrayList<>();
    var t = new LinkTable<>(Columns.lootRows(), v -> {});
    t.setClipboard(copied::add);
    t.setRows(List.of(loot(1, 5.0)));
    assertThat(Keys.press(t.table(), "ctrl C")).isFalse();
    assertThat(Keys.press(t.table(), "shift ctrl C")).isFalse();
    assertThat(copied).isEmpty();
  }

  @Test
  void ctrlShiftCCopiesTheRowsEntityOfItsKind() {
    List<String> copied = new ArrayList<>();
    var t = new LinkTable<>(Columns.dropSourcesRanked(), v -> {});
    t.setClipboard(copied::add);
    t.setRows(List.of(dropSource(11520, "Taragaman the Hungerer")));
    t.table().changeSelection(0, 0, false, false);
    Keys.press(t.table(), "ctrl C");
    Keys.press(t.table(), "shift ctrl C");
    assertThat(copied).containsExactly("Taragaman the Hungerer", "npc:11520");
  }

  @Test
  void aCustomIdTextReplacesTheEntityId() {
    List<String> copied = new ArrayList<>();
    var t = new LinkTable<>(Columns.lootRows(), v -> {});
    t.setClipboard(copied::add);
    t.setIdText(row -> java.util.Optional.of("custom " + row.item().id()));
    t.setRows(List.of(loot(7, 5.0)));
    t.table().changeSelection(0, 0, false, false);
    Keys.press(t.table(), "shift ctrl C");
    assertThat(copied).containsExactly("custom 7");
  }

  @Test
  void inlineTableHasNoScrollPaneAndShowsItsHeader() {
    var t = LinkTable.inline(Columns.lootRows(), v -> {});
    assertThat(t.isInline()).isTrue();
    assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, t.table())).isNull();
    assertThat(t.table().getTableHeader().getParent()).isSameAs(t);
    assertThat(new LinkTable<>(Columns.lootRows(), v -> {}).isInline()).isFalse();
  }
}
