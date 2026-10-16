package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.DropRow;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemPageTest {
  private final Repositories repos = FixtureDb.repositories();
  private static final ItemSummary LETTER = new ItemSummary(2874, "An Unsent Letter", 16, 16, "Quest", "Quest", 1);

  private static DropSource drop(long npc, String name, Double pct, String origin) {
    return new DropSource(new EntityRef(NPC, npc), name, 20, 21, "The Deadmines", 1581L, pct, "questie", origin);
  }

  private static ItemDetail drops(DropSource... sources) {
    return new ItemDetail(LETTER, 12, 0, null, List.of(sources), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of());
  }

  private static ItemSource src(String kind, EntityRef ref, String name) {
    return new ItemSource(kind, ref, name);
  }

  /** An item with every kind of source. */
  private static ItemDetail everything() {
    EntityRef quest = new EntityRef(QUEST, 373);
    return new ItemDetail(LETTER, 12, 0, null,
        List.of(drop(639, "Edwin VanCleef", 60.0, "both"), drop(1, "Defias Pirate", null, "questie"),
            drop(2, "Defias Squallshaper", 1.5, "questie")),
        List.of(new InstanceLoot(new EntityRef(INSTANCE, 2), "The Deadmines", 10, "Edwin VanCleef", 639L, 6, 60.0),
            new InstanceLoot(new EntityRef(INSTANCE, 2), "The Deadmines", 11, "Trash", null, 3, null)),
        List.of(src(ItemSource.OBJECT, new EntityRef(OBJECT, 75), "Chest")),
        List.of(src(ItemSource.CONTAINER, new EntityRef(ITEM, 6643), "Bag")),
        List.of(src(ItemSource.VENDOR, new EntityRef(NPC, 3), "Vendor")),
        List.of(src(ItemSource.REWARD, quest, "The Unsent Letter")),
        List.of(src(ItemSource.RELATED, quest, "The Unsent Letter"),
            src(ItemSource.RELATED, new EntityRef(QUEST, 374), "Bazil Thredd")));
  }

  private static List<Object> column(JTable t, int col) {
    List<Object> values = new ArrayList<>();
    for (int r = 0; r < t.getRowCount(); r++) {
      values.add(t.getValueAt(r, col));
    }
    return values;
  }

  private static String rendered(JTable t, int row, int col) {
    Component c = t.prepareRenderer(t.getCellRenderer(row, col), row, col);
    return ((javax.swing.JLabel) c).getText();
  }

  @Test
  void onlyNonEmptySectionsInOrderWithCounts() {
    var p = new ItemPage(repos.items().detail(14149).get(), t -> {});
    assertThat(p.sections()).extracting(s -> s.heading().getText())
        .containsExactly("Dropped by (1)", "In instance loot (1)");
    assertThat(p.section("Sold by")).isNull();
    assertThat(p.section("Dropped by").table()).isSameAs(p.droppedBy());
  }

  @Test
  void everyKindHasItsSectionInTheFixedOrder() {
    var p = new ItemPage(everything(), t -> {});
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Dropped by", "In instance loot",
        "Contained in", "Sold by", "Quest reward", "Related quests");
    assertThat(p.sections()).extracting(s -> s.heading().getText()).containsExactly("Dropped by (3)",
        "In instance loot (2)", "Contained in (2)", "Sold by (1)", "Quest reward (1)", "Related quests (2)");
    JTable contained = p.section("Contained in").table().table();
    assertThat(contained.getColumnName(1)).isEqualTo("Kind");
    assertThat(column(contained, 1)).containsExactly("Object", "Container item");
    assertThat(p.section("Sold by").table().table().getColumnCount()).isEqualTo(1);
  }

  @Test
  void summaryCountsEachKind() {
    assertThat(new ItemPage(repos.items().detail(14149).get(), t -> {}).summary())
        .isEqualTo("1 NPC · 1 loot list · no vendor · no quests");
    assertThat(new ItemPage(everything(), t -> {}).summary())
        .isEqualTo("3 NPCs · 2 loot lists · 2 containers · 1 vendor · 2 quests");
    assertThat(new ItemPage(everything(), t -> {}).summaryLabel().getForeground())
        .isEqualTo(Theme.current().textMuted());
  }

  @Test
  void vanCleefLeadsTheDropsAtSixtyPercent() {
    var p = new ItemPage(repos.items().detail(2874).get(), t -> {});
    JTable t = p.droppedBy().table();
    assertThat(t.getColumnName(3)).isEqualTo("Rate");
    assertThat(t.getValueAt(0, 0)).isEqualTo("Edwin VanCleef");
    assertThat(t.getCellRenderer(0, 3)).isNotNull();
    assertThat(rendered(t, 0, 3)).isEqualTo("60.00 %");
    assertThat(column(t, 0)).noneMatch(v -> String.valueOf(v).startsWith("Rate unknown"));
  }

  @Test
  void dropsSortByRateThenNameWithUnknownRatesAfterADivider() {
    var p = new ItemPage(drops(drop(1, "Zed", null, "questie"), drop(2, "Low", 1.5, "questie"),
        drop(639, "Edwin VanCleef", 60.0, "both"), drop(3, "Abe", null, "questie"), drop(4, "Bea", 1.5, "questie")),
        t -> {});
    LinkTable<DropRow> d = p.droppedBy();
    JTable t = d.table();
    assertThat(column(t, 0)).containsExactly("Edwin VanCleef", "Bea", "Low", "Rate unknown (2)", "Abe", "Zed");
    assertThat(p.section("Dropped by").heading().getText()).isEqualTo("Dropped by (5)");
    // the divider is muted, links nowhere, and cannot be selected
    assertThat(d.model().rowAt(3)).isInstanceOf(DropRow.Divider.class);
    assertThat(d.model().refAt(3, 0)).isEmpty();
    assertThat(t.prepareRenderer(t.getCellRenderer(3, 0), 3, 0).getForeground())
        .isEqualTo(Theme.current().textMuted());
    assertThat(rendered(t, 3, 3)).isEmpty();
    t.changeSelection(2, 0, false, false);
    t.changeSelection(3, 0, false, false);
    assertThat(t.getSelectedRow()).isEqualTo(4);
    t.changeSelection(3, 0, false, false);
    assertThat(t.getSelectedRow()).isEqualTo(2);
    assertThat(t.getRowSorter()).as("rank order is fixed").isNull();
  }

  @Test
  void originChipOnlyOnNonQuestieRows() {
    var p = new ItemPage(drops(drop(639, "Edwin VanCleef", 60.0, "both"), drop(2, "Low", 1.5, "questie"),
        drop(5, "Loot", 30.0, "atlasloot")), t -> {});
    JTable t = p.droppedBy().table();
    assertThat(t.getColumnName(4)).isEqualTo("Origin");
    assertThat(column(t, 0)).containsExactly("Edwin VanCleef", "Loot", "Low");
    List<String> chips = new ArrayList<>();
    for (int r = 0; r < 3; r++) {
      Component c = t.prepareRenderer(t.getCellRenderer(r, 4), r, 4);
      chips.add(((OriginChip) c).chipText());
    }
    assertThat(chips).containsExactly("Questie + AtlasLoot", "AtlasLoot", null);
    assertThat(t.prepareRenderer(t.getCellRenderer(0, 3), 0, 3)).isInstanceOf(RateBarRenderer.class);
  }

  @Test
  void everyEntityInTheDropsLinks() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemPage(repos.items().detail(14149).get(), opened::add);
    LinkTable<DropRow> d = p.droppedBy();
    assertThat(d.model().refAt(0, 0)).contains(new EntityRef(NPC, 11520));
    assertThat(d.model().refAt(0, 2)).contains(new EntityRef(ZONE, 2437));
    assertThat(d.table().getValueAt(0, 1)).hasToString("16");
    assertThat(d.table().prepareRenderer(d.table().getCellRenderer(0, 0), 0, 0).getForeground())
        .isEqualTo(LinkRenderer.linkColor());
    d.activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 11520)));
  }

  @Test
  void unknownNpcRendersPlaceholderUnderTheDivider() {
    var p = new ItemPage(repos.items().detail(190179).get(), t -> {});
    assertThat(column(p.droppedBy().table(), 0)).containsExactly("Rate unknown (1)", "Unknown NPC #185333");
  }

  @Test
  void lootListsReadInstanceThenListAndLinkBoth() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemPage(repos.items().detail(14149).get(), opened::add);
    var loot = p.section("In instance loot").table();
    JTable t = loot.table();
    assertThat(List.of(t.getColumnName(0), t.getColumnName(1), t.getColumnName(2)))
        .containsExactly("Loot list", "Boss", "Rate");
    assertThat(t.getValueAt(0, 0)).isEqualTo("Ragefire Chasm › Taragaman the Hungerer");
    assertThat(loot.model().refAt(0, 0)).contains(new EntityRef(INSTANCE, 1));
    assertThat(loot.model().refAt(0, 1)).contains(new EntityRef(NPC, 11520));
    assertThat(rendered(t, 0, 2)).isEqualTo("33.30 %");
    loot.activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(INSTANCE, 1)));
  }

  @Test
  void lootListsThatReadTheSameNameTheirSlot() {
    EntityRef sm = new EntityRef(INSTANCE, 9);
    ItemDetail d = new ItemDetail(LETTER, 12, 0, null, List.of(),
        List.of(new InstanceLoot(sm, "Scarlet Monastery", 70, "Armory trash", null, 24, 0.5),
            new InstanceLoot(sm, "Scarlet Monastery", 70, "Armory trash", null, 4, 0.5),
            new InstanceLoot(sm, "Scarlet Monastery", 71, "Herod", 3975L, 2, 12.0)),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var loot = new ItemPage(d, t -> {}).section("In instance loot").table();
    assertThat(column(loot.table(), 0)).containsExactly("Scarlet Monastery › Herod",
        "Scarlet Monastery › Armory trash · slot 4", "Scarlet Monastery › Armory trash · slot 24");
    assertThat(loot.model().refAt(1, 1)).isEmpty();
  }

  @Test
  void ctrlNumberJumpsToThatSection() {
    var p = new ItemPage(everything(), t -> {});
    p.setSize(700, 160);
    layOut(p);
    KeyStroke ctrl1 = KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
    assertThat(p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(
        KeyStroke.getKeyStroke(KeyEvent.VK_7, InputEvent.CTRL_DOWN_MASK))).isNull();
    press(p, KeyStroke.getKeyStroke(KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK));
    assertThat(p.sections().get(3).getY()).as("laid out").isPositive();
    assertThat(p.scrollPane().getViewport().getViewPosition().y).isPositive().isEqualTo(expectedTop(p, 3));
    assertThat(p.sections().get(3).table().table().getSelectedRow()).isZero();
    press(p, ctrl1);
    SourceSection first = p.sections().get(0);
    assertThat(p.scrollPane().getViewport().getViewPosition().y).isEqualTo(expectedTop(p, 0));
    assertThat(first.table().table().getSelectedRow()).isZero();
  }

  @Test
  void jumpingToADividerLedTableSelectsTheFirstSource() {
    var p = new ItemPage(repos.items().detail(190179).get(), t -> {});
    press(p, KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
    assertThat(p.droppedBy().table().getSelectedRow()).isEqualTo(1);
  }

  /** Lays out the whole tree top-down; headless components have no peer, so {@code validate()} does nothing. */
  private static void layOut(java.awt.Container c) {
    c.doLayout();
    for (Component child : c.getComponents()) {
      if (child instanceof java.awt.Container cc) {
        layOut(cc);
      }
    }
  }

  private static int expectedTop(ItemPage p, int section) {
    int max = p.scrollPane().getViewport().getView().getHeight() - p.scrollPane().getViewport().getHeight();
    return Math.min(p.sections().get(section).getY(), max);
  }

  private static void press(ItemPage p, KeyStroke key) {
    Object action = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(key);
    assertThat(action).as(key.toString()).isNotNull();
    p.getActionMap().get(action).actionPerformed(new ActionEvent(p, ActionEvent.ACTION_PERFORMED, ""));
  }

  @Test
  void headerKeepsNameFactsAndStartsQuestLink() {
    var p = new ItemPage(repos.items().detail(14149).get(), t -> {});
    assertThat(p.header().title()).isEqualTo("Subterranean Cape");
    assertThat(p.header().subtitle()).isEqualTo("Item #14149 · Armor · Cloth · iLvl 18 · Requires 13");
    EntityRef quest = new EntityRef(QUEST, 373);
    ItemDetail d = new ItemDetail(new ItemSummary(2874, "An Unsent Letter", 1, null, "Quest", "Quest", 1), 12, 0,
        373L, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(new ItemSource(ItemSource.RELATED, quest, "The Unsent Letter")));
    List<ViewTarget> opened = new ArrayList<>();
    var q = new ItemPage(d, opened::add);
    assertThat(q.header().subtitle()).isEqualTo("Item #2874 · Quest · iLvl 1 · Starts quest: The Unsent Letter");
    q.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(quest));
  }
}
