package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.LinkRenderer;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import org.junit.jupiter.api.Test;

class LinkTableTest {

  private static DropSource dropSource(long npcId, String name) {
    return dropSource(npcId, name, null);
  }

  private static DropSource dropSource(long npcId, String name, Double pct) {
    return new DropSource(new EntityRef(NPC, npcId), name, 30, 31, "Ragefire Chasm", 2437L, pct, "questie", "both");
  }

  @Test
  void activateRowOpensLink() {
    List<ViewTarget> opened = new ArrayList<>();
    var t = new LinkTable<>(Columns.dropSources(), opened::add);
    t.setRows(List.of(dropSource(11520, "Taragaman the Hungerer")));
    t.activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 11520)));
  }

  @Test
  void dropSourceColumnsFormatLevelZoneAndPercent() {
    var t = new LinkTable<>(Columns.dropSources(), v -> {});
    t.setRows(List.of(dropSource(1, "A", 56.0712), dropSource(2, null, null)));
    assertThat(t.rowCount()).isEqualTo(2);
    assertThat(t.table().getColumnName(0)).isEqualTo("Source");
    assertThat(t.table().getValueAt(0, 1)).hasToString("30–31");
    assertThat(t.table().getValueAt(0, 2)).isEqualTo("Ragefire Chasm");
    assertThat(t.table().getValueAt(0, 3)).isEqualTo("56.07 %");
    assertThat(t.table().getValueAt(1, 0)).isEqualTo("Unknown NPC #2");
    assertThat(t.table().getValueAt(1, 3)).isEqualTo("—");
    assertThat(t.model().refAt(0, 2)).contains(new EntityRef(ZONE, 2437));
  }

  @Test
  void dropPercentSortsNumericallyWithUnknownLast() {
    var t = new LinkTable<>(Columns.dropSources(), v -> {});
    t.setRows(List.of(dropSource(1, "A", 60.0), dropSource(2, "B", null), dropSource(3, "C", 5.0),
        dropSource(4, "D", 12.5)));
    t.table().getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.ASCENDING)));
    List<Object> shown = new ArrayList<>();
    for (int r = 0; r < t.rowCount(); r++) {
      shown.add(t.table().getValueAt(r, 3));
    }
    assertThat(shown).containsExactly("5.00 %", "12.50 %", "60.00 %", "—");
  }

  @Test
  void activationFollowsTheSortedViewAndEnterOpens() {
    List<ViewTarget> opened = new ArrayList<>();
    var t = new LinkTable<>(Columns.dropSources(), opened::add);
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
    LinkTable.addTab(tabs, "Drops", Columns.dropSources(), List.of(), v -> {});
    assertThat(tabs.getTabLayoutPolicy()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
  }

  @Test
  void nameColumnsAreWideAndNumberColumnsNarrow() {
    var loot = new LinkTable<>(Columns.lootRows(), v -> {}).table().getColumnModel();
    assertThat(loot.getColumn(0).getPreferredWidth()).as("Item").isEqualTo(260);
    assertThat(loot.getColumn(1).getPreferredWidth()).as("Item level").isEqualTo(70);
    assertThat(loot.getColumn(2).getPreferredWidth()).as("Drop %").isEqualTo(70);
    assertThat(loot.getColumn(4).getPreferredWidth()).as("Slot").isEqualTo(70);
    var drops = new LinkTable<>(Columns.dropSources(), v -> {}).table().getColumnModel();
    assertThat(drops.getColumn(0).getPreferredWidth()).as("Source").isEqualTo(260);
    assertThat(drops.getColumn(1).getPreferredWidth()).as("Level").isEqualTo(70);
    assertThat(drops.getColumn(3).getPreferredWidth()).as("Drop %").isEqualTo(70);
  }

  @Test
  void linkColumnsUseTheLinkRenderer() {
    var t = new LinkTable<>(Columns.dropSources(), v -> {});
    assertThat(t.table().getColumnModel().getColumn(0).getCellRenderer()).isInstanceOf(LinkRenderer.class);
    assertThat(t.table().getColumnModel().getColumn(2).getCellRenderer()).isInstanceOf(LinkRenderer.class);
    assertThat(t.table().getColumnModel().getColumn(1).getCellRenderer()).isNull();
  }
}
