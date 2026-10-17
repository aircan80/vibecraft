package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.DropRow;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.LootListRow;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Keys;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
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

  private static List<String> names(LinkTable<?> t) {
    List<String> names = new ArrayList<>();
    for (int c = 0; c < t.table().getColumnCount(); c++) {
      names.add(t.table().getColumnName(c));
    }
    return names;
  }

  @Test
  void compactKeepsNameRateAndOriginAndPutsTheRestInTheRowTooltip() {
    var t = new LinkTable<>(Columns.lootRated(), v -> {});
    t.setRows(List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 21, 5.0, "questie", 7),
        new LootRow(new EntityRef(ITEM, 2), "Axe", null, null, "both", null)));
    t.setCompact(true);
    assertThat(t.isCompact()).isTrue();
    assertThat(names(t)).containsExactly("Item", "Rate", "Origin");
    assertThat(t.rowTooltip(0)).isEqualTo("Item level 21 · Slot 7");
    assertThat(t.rowTooltip(1)).isNull(); // nothing hidden has a value
    t.setCompact(false);
    assertThat(names(t)).containsExactly("Item", "Item level", "Rate", "Origin", "Slot");
    assertThat(t.rowTooltip(0)).isNull();
  }

  @Test
  void compactTooltipsGiveTextAColonAndNumbersNone() {
    var t = new LinkTable<>(Columns.dropSourcesRanked(), v -> {});
    t.setRows(List.of(dropSource(1, "A", 56.0)));
    t.setCompact(true);
    assertThat(names(t)).containsExactly("Source", "Rate", "Origin");
    assertThat(t.rowTooltip(0)).isEqualTo("Level 30–31 · Zone: Ragefire Chasm");
    Rectangle cell = t.table().getCellRect(0, 0, true);
    MouseEvent over = new MouseEvent(t.table(), MouseEvent.MOUSE_MOVED, 0, 0, cell.x + 1, cell.y + 1, 0, false);
    assertThat(t.table().getToolTipText(over)).isEqualTo("Level 30–31 · Zone: Ragefire Chasm");
  }

  @Test
  void compactKeepsEachColumnsRenderer() {
    var t = new LinkTable<>(Columns.lootRated(), v -> {});
    TableCellRenderer rate = new DefaultTableCellRenderer();
    t.table().getColumnModel().getColumn(2).setCellRenderer(rate);
    t.setCompact(true);
    t.setCompact(false);
    assertThat(t.table().getColumnModel().getColumn(2).getCellRenderer()).isSameAs(rate);
  }

  @Test
  void compactFollowsTheEnclosingArea() {
    var t = new LinkTable<>(Columns.lootRated(), v -> {});
    JPanel page = new JPanel();
    page.add(t);
    JPanel compactArea = new JPanel();
    compactArea.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compactArea.add(page);
    assertThat(t.isCompact()).isTrue();
    assertThat(names(t)).containsExactly("Item", "Rate", "Origin");
    new JPanel().add(page);
    assertThat(t.isCompact()).isFalse();
    assertThat(names(t)).hasSize(5);
  }

  @Test
  void compactTooltipsTellHiddenColumnsAsTheirRenderersShowThem() {
    var t = new LinkTable<>(Columns.lootRated(), v -> {});
    t.setRows(List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 21, 5.0, "questie", 7)));
    t.table().getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      protected void setValue(Object value) {
        setText("#" + value);
      }
    });
    t.setCompact(true);
    assertThat(t.rowTooltip(0)).isEqualTo("Item level 21 · Slot: #7");
  }

  @Test
  void compactCellsKeepTheirOwnTooltipBelowTheRowsOne() {
    var t = new LinkTable<>(Columns.lootRated(), v -> {});
    t.setRows(List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 21, 5.0, "questie", 7)));
    t.table().getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
          boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setToolTipText("Sword of <Testing>");
        return this;
      }
    });
    t.setCompact(true);
    Rectangle cell = t.table().getCellRect(0, 0, true);
    MouseEvent over = new MouseEvent(t.table(), MouseEvent.MOUSE_MOVED, 0, 0, cell.x + 1, cell.y + 1, 0, false);
    assertThat(t.table().getToolTipText(over))
        .isEqualTo("<html>Item level 21 · Slot 7<br>Sword of &lt;Testing&gt;</html>");
    t.setCompact(false);
    assertThat(t.table().getToolTipText(over)).isEqualTo("Sword of <Testing>");
  }

  @Test
  void disagreeingRatesAreMarkedInEveryDropTable() {
    var drops = new LinkTable<>(Columns.dropSourcesRanked(), v -> {});
    drops.setRows(List.of(new DropRow.Source(new DropSource(new EntityRef(NPC, 639), "Edwin VanCleef", 19, 19,
        "The Deadmines", 1581L, 60.0, "questie-correction", "both", true, 4)), dropSource(2, "B", 5.0)));
    assertThat(drops.table().getValueAt(0, 3)).as("the rate stays a number").isEqualTo(60.0);
    assertThat(rateText(drops, 0, 3)).isEqualTo("60.00 % ≠");
    assertThat(rateText(drops, 1, 3)).isEqualTo("5.00 %");

    LootRow letter = new LootRow(new EntityRef(ITEM, 2874), "An Unsent Letter", 16, 60.0, "both", 5, true, 4);
    var loot = new LinkTable<>(Columns.lootRows(), v -> {});
    loot.setRows(List.of(letter));
    assertThat(loot.table().getValueAt(0, 2)).isEqualTo("60.00 % ≠");
    var rated = new LinkTable<>(Columns.lootRated(), v -> {});
    rated.setRows(List.of(letter, loot(2, 5.0)));
    assertThat(rateText(rated, 0, 2)).isEqualTo("60.00 % ≠");
    assertThat(rateText(rated, 1, 2)).isEqualTo("5.00 %");
    var lists = new LinkTable<>(Columns.lootLists(), v -> {});
    lists.setRows(LootListRow.of(List.of(new InstanceLoot(new EntityRef(INSTANCE, 4), "The Deadmines", 10,
        "Edwin VanCleef", 639L, 5, 60.0, true, 4))));
    assertThat(rateText(lists, 0, 2)).isEqualTo("60.00 % ≠");
  }

  /** The text a {@link RateBarRenderer} on column {@code col} shows in the cell. */
  private static String rateText(LinkTable<?> t, int row, int col) {
    JTable table = t.table();
    RateBarRenderer.install(table.getColumnModel().getColumn(col));
    return ((JLabel) table.prepareRenderer(table.getCellRenderer(row, col), row, col)).getText();
  }

  @Test
  void markedRatesStillSortByTheirNumber() {
    assertThat(Columns.NUMERIC_TEXT.compare("60.00 % ≠", "5.00 %")).isPositive();
    assertThat(Columns.NUMERIC_TEXT.compare("5.00 % ≠", "60.00 %")).isNegative();
  }

  @Test
  void itemNamesAreColouredByQuality() {
    var t = new LinkTable<>(Columns.items(), v -> {});
    t.setRows(List.of(new ItemSummary(17204, "Eye of Sulfuras", 60, null, "Quest", "Quest", 0, 5, 134124L),
        new ItemSummary(1176, "Smelling Salts", null, null, null, null, 0)));
    JTable table = t.table();
    assertThat(table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0).getForeground())
        .isEqualTo(QualityColors.text(5, FlatLaf.isLafDark()));
    assertThat(table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0).getForeground())
        .as("no quality: plain link colour").isEqualTo(LinkRenderer.linkColor());
  }

  @Test
  void qualityColourFollowsTheInstalledTheme() throws Exception {
    LookAndFeel saved = UIManager.getLookAndFeel();
    try {
      var t = new LinkTable<>(Columns.items(), v -> {});
      t.setRows(List.of(new ItemSummary(17204, "Eye of Sulfuras", 60, null, "Quest", "Quest", 0, 5, 134124L)));
      JTable table = t.table();
      UIManager.setLookAndFeel(new FlatDarkLaf());
      assertThat(table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0).getForeground())
          .isEqualTo(QualityColors.text(5, true));
      UIManager.setLookAndFeel(new FlatLightLaf());
      assertThat(table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0).getForeground())
          .isEqualTo(QualityColors.text(5, false));
    } finally {
      UIManager.setLookAndFeel(saved);
    }
  }

  /** Runs {@code body} with a shared icon cache over the fixture database, warmed with {@code ids} at row size. */
  private static void withIcons(Runnable body, Long... ids) {
    IconCache cache = new IconCache(FixtureDb.repositories().assets()::icon, 100, null);
    cache.warm(java.util.Arrays.asList(ids), IconCache.ROW);
    IconCache before = IconCache.install(cache);
    try {
      body.run();
    } finally {
      IconCache.install(before);
    }
  }

  private static JLabel rendered(JTable table, int row, int column) {
    return (JLabel) table.prepareRenderer(table.getCellRenderer(row, column), row, column);
  }

  @Test
  void itemListRowsShowTheirIconAtRowSize() {
    withIcons(() -> {
      var t = new LinkTable<>(Columns.items(), v -> {});
      t.setRows(List.of(new ItemSummary(17204, "Eye of Sulfuras", 60, null, "Quest", "Quest", 0, 5, 134124L),
          new ItemSummary(1176, "Smelling Salts", null, null, null, null, 0)));
      JTable table = t.table();
      JLabel eye = rendered(table, 0, 0);
      assertThat(eye.getIcon()).isSameAs(IconCache.shared().cached(134124L, IconCache.ROW).orElseThrow());
      assertThat(eye.getIcon().getIconWidth()).isEqualTo(18);
      assertThat(eye.getForeground()).isEqualTo(QualityColors.text(5, FlatLaf.isLafDark()));
      assertThat(rendered(table, 1, 0).getIcon()).as("no client data: the question mark")
          .isSameAs(IconCache.shared().cached(0L, IconCache.ROW).orElseThrow());
    }, 134124L, 0L);
  }

  @Test
  void lootRowsAreColouredByQualityAndShowTheirIcon() {
    withIcons(() -> {
      var t = new LinkTable<>(Columns.lootRated(), v -> {});
      t.setRows(List.of(new LootRow(new EntityRef(ITEM, 5191), "Cruel Barb", 24, 12.0, "both", null, 2, "Weapon",
          7, false, 1, 3, 135325L)));
      JLabel barb = rendered(t.table(), 0, 0);
      assertThat(barb.getForeground()).isEqualTo(QualityColors.text(3, FlatLaf.isLafDark()));
      assertThat(barb.getIcon()).isSameAs(IconCache.shared().cached(135325L, IconCache.ROW).orElseThrow());
    }, 135325L);
  }

  @Test
  void onlyItemSearchHitsShowAnIcon() {
    withIcons(() -> {
      var t = new LinkTable<>(Columns.searchHits(), v -> {});
      t.setRows(List.of(new SearchHit(new EntityRef(ITEM, 17204), "Eye of Sulfuras", "Item", 1, 5, 134124L),
          new SearchHit(new EntityRef(NPC, 448), "Hogger", "NPC")));
      assertThat(rendered(t.table(), 0, 0).getIcon()).isNotNull();
      assertThat(rendered(t.table(), 1, 0).getIcon()).isNull();
    }, 134124L);
  }

  @Test
  void anIconNotYetCachedRendersEmptyInsteadOfReadingTheDatabase() {
    IconCache cache = new IconCache(id -> {
      throw new AssertionError("read on the EDT");
    }, 10, null);
    IconCache before = IconCache.install(cache);
    try {
      var t = new LinkTable<>(Columns.items(), v -> {});
      t.setRows(List.of(new ItemSummary(17204, "Eye of Sulfuras", 60, null, "Quest", "Quest", 0, 5, 134124L)));
      assertThat(rendered(t.table(), 0, 0).getIcon()).isSameAs(IconCache.empty(IconCache.ROW));
    } finally {
      IconCache.install(before);
    }
  }

  @Test
  void otherLinkColumnsShowNoIcon() {
    var npcs = new LinkTable<>(Columns.npcs(), v -> {});
    npcs.setRows(List.of(
        new dev.wowforever.app.model.NpcSummary(448, "Hogger", null, 11, 11, 1, "Elwynn Forest", 12L)));
    assertThat(rendered(npcs.table(), 0, 0).getIcon()).isNull();
    assertThat(rendered(npcs.table(), 0, 4).getIcon()).isNull();
  }

  @Test
  void itemSearchHitsAreColouredByQuality() {
    var t = new LinkTable<>(Columns.searchHits(), v -> {});
    t.setRows(List.of(new SearchHit(new EntityRef(ITEM, 19682), "Bloodvine Vest", "Item", 1, 3),
        new SearchHit(new EntityRef(NPC, 448), "Hogger", "NPC")));
    JTable table = t.table();
    assertThat(table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0).getForeground())
        .isEqualTo(QualityColors.text(3, FlatLaf.isLafDark()));
    assertThat(table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0).getForeground())
        .isEqualTo(LinkRenderer.linkColor());
  }
}
