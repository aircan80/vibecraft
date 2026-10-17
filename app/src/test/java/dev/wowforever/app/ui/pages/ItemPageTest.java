package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.boundsIn;
import static dev.wowforever.app.ui.pages.Tables.layOut;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.DropRow;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.ItemCardPanel;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
    return ((JLabel) c).getText();
  }

  @Test
  void onlyNonEmptySectionsInOrderWithCounts() {
    var p = new ItemPage(repos.items().detail(14149).get(), t -> {});
    assertThat(p.sections()).extracting(s -> s.heading().text())
        .containsExactly("Dropped by (1)", "In instance loot (1)");
    assertThat(p.section("Sold by")).isNull();
    assertThat(p.section("Dropped by").table()).isSameAs(p.droppedBy());
  }

  @Test
  void everyKindHasItsSectionInTheFixedOrder() {
    var p = new ItemPage(everything(), t -> {});
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Dropped by", "In instance loot",
        "Contained in", "Sold by", "Quest reward", "Related quests");
    assertThat(p.sections()).extracting(s -> s.heading().text()).containsExactly("Dropped by (3)",
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

  /** Drops and a loot list of every origin. */
  private static ItemDetail mixedOrigins() {
    return new ItemDetail(LETTER, 12, 0, null,
        List.of(drop(639, "Edwin VanCleef", 60.0, "both"), drop(1, "Defias Pirate", null, "questie"),
            drop(2, "Defias Squallshaper", 1.5, "questie"), drop(5, "Atlas Only", 2.0, "atlasloot")),
        List.of(new InstanceLoot(new EntityRef(INSTANCE, 2), "The Deadmines", 10, "Edwin VanCleef", 639L, 6, 60.0)),
        List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static ItemPage filtered(ItemDetail d, SourceFilter filter) {
    return new ItemPage(d, null, Map.of(), filter, t -> {});
  }

  @Test
  void everySourceShowsByDefaultWithoutANote() {
    var p = filtered(mixedOrigins(), SourceFilter.ALL);
    assertThat(p.section("Dropped by").heading().text()).isEqualTo("Dropped by (4)");
    assertThat(p.section("Dropped by").note()).isNull();
    assertThat(p.section("In instance loot").note()).isNull();
    assertThat(new ItemPage(mixedOrigins(), t -> {}).droppedBy().rowCount()).isEqualTo(5); // with the divider
  }

  @Test
  void hidingAtlasLootOnlyDropsKeepsQuestieAndBothAndNeverTouchesTheLootLists() {
    var p = filtered(mixedOrigins(), SourceFilter.HIDE_ATLASLOOT_ONLY);
    SourceSection dropped = p.section("Dropped by");
    assertThat(dropped.heading().text()).isEqualTo("Dropped by (3)");
    assertThat(column(p.droppedBy().table(), 0)).contains("Edwin VanCleef", "Defias Pirate", "Defias Squallshaper")
        .doesNotContain("Atlas Only");
    assertThat(dropped.note().getText()).isEqualTo("1 row hidden by the source filter");
    assertThat(dropped.note().getForeground()).isEqualTo(Theme.current().textMuted());
    // the filter is for NPC drops: the instance loot lists stay as they are
    SourceSection loot = p.section("In instance loot");
    assertThat(loot.heading().text()).isEqualTo("In instance loot (1)");
    assertThat(loot.note()).isNull();
    assertThat(loot.table().rowCount()).isEqualTo(1);
    assertThat(p.summary()).as("the NPCs shown, every loot list")
        .isEqualTo("3 NPCs · 1 loot list · no vendor · no quests");
  }

  @Test
  void jumpKeysSkipASectionTheFilterEmptied() {
    var d = new ItemDetail(LETTER, 12, 0, null, List.of(drop(1, "Defias Pirate", 2.0, "questie")), List.of(),
        List.of(), List.of(), List.of(src(ItemSource.VENDOR, new EntityRef(NPC, 3), "Vendor")), List.of(),
        List.of());
    var p = filtered(d, SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(p.section("Dropped by").table().isVisible()).isFalse();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Dropped by", "Sold by");
    JTable sold = p.section("Sold by").table().table();
    press(p, KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
    assertThat(sold.getSelectedRow()).as("Ctrl+1 goes to the first section with rows").isZero();
    assertThat(p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .get(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK))).isNull();
  }

  @Test
  void hidingQuestieOnlyRowsKeepsAtlasLootAndBoth() {
    var p = filtered(mixedOrigins(), SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(p.section("Dropped by").heading().text()).isEqualTo("Dropped by (2)");
    assertThat(column(p.droppedBy().table(), 0)).containsExactly("Edwin VanCleef", "Atlas Only");
    assertThat(p.section("Dropped by").note().getText()).isEqualTo("2 rows hidden by the source filter");
    assertThat(p.section("In instance loot").heading().text()).isEqualTo("In instance loot (1)");
    assertThat(p.section("In instance loot").note()).isNull();
  }

  @Test
  void aFilterThatHidesNothingAddsNoNote() {
    var p = filtered(drops(drop(639, "Edwin VanCleef", 60.0, "both")), SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(p.section("Dropped by").heading().text()).isEqualTo("Dropped by (1)");
    assertThat(p.section("Dropped by").note()).isNull();
  }

  @Test
  void rateCellsWithLoadedSourcesListThemInTheirTooltip() {
    var vanCleef = new DropSource(new EntityRef(NPC, 639), "Edwin VanCleef", 20, 21, "The Deadmines", 1581L, 60.0,
        "questie", "both", true, 2);
    var pirate = new DropSource(new EntityRef(NPC, 1), "Defias Pirate", 20, 21, "The Deadmines", 1581L, 1.5,
        "questie", "questie", false, 0);
    var d = new ItemDetail(LETTER, 12, 0, null, List.of(vanCleef, pirate),
        List.of(new InstanceLoot(new EntityRef(INSTANCE, 2), "The Deadmines", 10, "Edwin VanCleef", 639L, 6, 60.0,
            true, 2)),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var sources = Map.of(new RateSource.Key(639, 2874),
        List.of(new RateSource("atlasloot", 56.07), new RateSource("questie-wowhead", 60.0)));
    var p = new ItemPage(d, null, sources, SourceFilter.ALL, t -> {});
    JTable drops = p.droppedBy().table();
    int rate = drops.getColumnModel().getColumnIndex("Rate");
    assertThat(Tables.tooltipAt(drops, 0, rate)).isEqualTo("Questie 60.00 %  ·  AtlasLoot 56.07 %");
    assertThat(Tables.tooltipAt(drops, 1, rate)).as("no sources").isNull();
    JTable loot = p.section("In instance loot").table().table();
    assertThat(Tables.tooltipAt(loot, 0, loot.getColumnModel().getColumnIndex("Rate")))
        .isEqualTo("Questie 60.00 %  ·  AtlasLoot 56.07 %");
  }

  @Test
  void vanCleefLeadsTheDropsAtSixtyPercent() {
    var p = new ItemPage(repos.items().detail(2874).get(), t -> {});
    JTable t = p.droppedBy().table();
    assertThat(t.getColumnName(3)).isEqualTo("Rate");
    assertThat(t.getValueAt(0, 0)).isEqualTo("Edwin VanCleef");
    assertThat(t.getCellRenderer(0, 3)).isNotNull();
    assertThat(t.getValueAt(0, 3)).isEqualTo(60.0);
    assertThat(rendered(t, 0, 3)).as("its four rate sources disagree").isEqualTo("60.00 % ≠");
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
    assertThat(p.section("Dropped by").heading().text()).isEqualTo("Dropped by (5)");
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
  void unknownNpcRendersPlaceholderWithoutADividerWhenNoRateIsKnown() {
    var p = new ItemPage(repos.items().detail(190179).get(), t -> {});
    assertThat(column(p.droppedBy().table(), 0)).containsExactly("Unknown NPC #185333");
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
  void allUnknownRatesHaveNoDividerAndJumpSelectsTheFirstSource() {
    var p = new ItemPage(drops(drop(1, "Zed", null, "questie"), drop(3, "Abe", null, "questie")), t -> {});
    JTable t = p.droppedBy().table();
    assertThat(column(t, 0)).containsExactly("Abe", "Zed");
    press(p, KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
    assertThat(t.getSelectedRow()).isZero();
    assertThat(p.droppedBy().model().rowAt(0)).isInstanceOf(DropRow.Source.class);
  }

  @Test
  void aShortPageStartsAtTheTopWithoutAGapUnderTheHeader() {
    EntityRef sm = new EntityRef(INSTANCE, 9);
    ItemDetail d = new ItemDetail(LETTER, 12, 0, null, List.of(),
        List.of(new InstanceLoot(sm, "Scarlet Monastery", 70, "Trash", null, 24, null),
            new InstanceLoot(sm, "Scarlet Monastery", 70, "Trash", null, 27, null)),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var p = new ItemPage(d, t -> {});
    p.setSize(1000, 800); // far taller than the page's content
    layOut(p);
    Rectangle header = boundsIn(p.header(), p);
    Rectangle summary = boundsIn(p.summaryLabel(), p);
    Rectangle first = boundsIn(p.sections().get(0), p);
    assertThat(header.y).isZero();
    assertThat(header.height).as("the header keeps its own height").isEqualTo(p.header().getPreferredSize().height);
    assertThat(summary.y).as("the summary right under the header").isEqualTo(header.y + header.height);
    assertThat(first.y).as("the first section within a header height of the header's bottom")
        .isLessThanOrEqualTo(header.y + 2 * header.height);
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
    assertThat(p.header().subtitle()).isEqualTo("Item #14149 · Cloth · iLvl 18 · Req 13");
    EntityRef quest = new EntityRef(QUEST, 373);
    ItemDetail d = new ItemDetail(new ItemSummary(2874, "An Unsent Letter", 1, null, "Quest", "Quest", 1), 12, 0,
        373L, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(new ItemSource(ItemSource.RELATED, quest, "The Unsent Letter")));
    List<ViewTarget> opened = new ArrayList<>();
    var q = new ItemPage(d, opened::add);
    // a quest item of item level 1 shows no item level (never "iLvl 1")
    assertThat(q.header().subtitle()).isEqualTo("Item #2874 · Quest item · Starts quest: The Unsent Letter");
    q.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(quest));
  }

  @Test
  void theNameIsInTheItemsQualityColour() {
    var p = new ItemPage(repos.items().detail(17204).get(), t -> {});
    assertThat(p.header().title()).isEqualTo("Eye of Sulfuras");
    assertThat(p.header().titleColor()).isEqualTo(QualityColors.text(5, FlatLaf.isLafDark()));
  }

  private BufferedImage icon(long iconId) {
    return Images.decode(repos.assets().icon(iconId).orElseThrow()).orElseThrow();
  }

  @Test
  void cardShown() {
    var p = new ItemPage(repos.items().detail(17204).get(), icon(134124), t -> {});
    ItemCardPanel card = p.card();
    assertThat(card).isNotNull();
    assertThat(card.name().getText()).isEqualTo("Eye of Sulfuras");
    assertThat(card.name().getForeground()).isEqualTo(QualityColors.canonical(5));
    assertThat(card.lines().texts()).startsWith("Binds when picked up").contains("Sell Price: 20g");
    assertThat(card.icon().getIcon().getIconWidth()).isEqualTo(56);
    assertThat(card.icon().getIcon().getIconHeight()).isEqualTo(56);
    // the card leads the page, the facts line (title hidden: the card has the name) and the summary follow
    assertThat(p.header().isTitleShown()).isFalse();
    assertThat(p.header().subtitle()).startsWith("Item #17204");
    p.scrollPane().setSize(700, 900);
    layOut(p.scrollPane());
    int cardY = boundsIn(card, p.scrollPane().getViewport().getView()).y;
    int headerY = boundsIn(p.header(), p.scrollPane().getViewport().getView()).y;
    int summaryY = boundsIn(p.summaryLabel(), p.scrollPane().getViewport().getView()).y;
    assertThat(cardY).isLessThan(headerY);
    assertThat(headerY).isLessThan(summaryY);
    assertThat(card.getWidth()).isLessThanOrEqualTo(ItemCardPanel.maxWidth());
    assertThat(card.getHeight()).isGreaterThan(56);
  }

  @Test
  void theCardsSetPiecesOpenTheirItems() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemPage(repos.items().detail(19682).get(), opened::add);
    JButton vest = p.card().lines().parts().stream().filter(JButton.class::isInstance).map(JButton.class::cast)
        .filter(b -> b.getText().equals("Bloodvine Vest")).findFirst().orElseThrow();
    vest.doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 19682)));
  }

  @Test
  void aPageBuiltAgainScrollsToWhereTheOldOneWas() {
    var before = new ItemPage(everything(), t -> {});
    before.scrollPane().setSize(400, 120);
    layOut(before.scrollPane());
    before.scrollPane().getVerticalScrollBar().setValue(90);
    Object state = before.saveState();
    var after = new ItemPage(everything(), t -> {});
    after.scrollPane().setSize(400, 120);
    layOut(after.scrollPane());
    after.restoreState(state);
    assertThat(after.scrollPane().getViewport().getViewPosition().y).isEqualTo(90);
    after.restoreState("not a page state"); // ignored
    after.restoreState(null);
    assertThat(after.scrollPane().getViewport().getViewPosition().y).isEqualTo(90);
  }

  @Test
  void cardShownWithoutAnIconShowsAnEmptyOneOfTheSameSize() {
    var p = new ItemPage(repos.items().detail(17204).get(), t -> {});
    assertThat(p.card().icon().getIcon()).isSameAs(IconCache.empty(IconCache.CARD));
  }

  @Test
  void cardAbsentWithoutClientData() {
    for (long id : new long[] {1176, 212347}) { // no client data; an Item row (icon) but no tooltip lines
      var p = new ItemPage(repos.items().detail(id).get(), icon(0), t -> {});
      assertThat(p.card()).as("item " + id).isNull();
      assertThat(p.header().isTitleShown()).isTrue();
    }
  }

  @Test
  void theJumpKeysStillReachTheSectionsUnderTheCard() {
    var p = new ItemPage(repos.items().detail(14149).get(), icon(0), t -> {});
    assertThat(p.card()).isNotNull();
    assertThat(p.sections()).hasSize(2);
    for (int key : new int[] {KeyEvent.VK_1, KeyEvent.VK_2}) {
      assertThat(p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .get(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK))).isNotNull();
    }
  }

  @Test
  void anItemWithoutClientDataHasAPlainName() {
    var p = new ItemPage(repos.items().detail(1176).get(), t -> {});
    assertThat(p.header().title()).isEqualTo("Smelling Salts");
    assertThat(p.header().titleColor()).isEqualTo(new JLabel().getForeground());
  }
}
