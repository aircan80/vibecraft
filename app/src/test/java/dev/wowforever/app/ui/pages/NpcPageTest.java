package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.Keys;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RankChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class NpcPageTest {
  private final Repositories repos = FixtureDb.repositories();
  private static final NpcSummary HOGGER = new NpcSummary(448, "Hogger", null, 11, 11, 1, "Elwynn Forest", 12L);

  /** Edwin VanCleef from the fixture, with one Westfall spawn point in place of his instance one. */
  private NpcDetail vanCleef() {
    NpcDetail d = repos.npcs().detail(639).get();
    return new NpcDetail(d.summary(), d.npcFlags(), d.friendlyTo(), d.drops(), d.sells(), d.starts(), d.ends(),
        List.of(new Spawn(40, "Westfall", 42.3, 67.4)));
  }

  private static NpcDetail npc(NpcSummary s, List<LootRow> drops, List<LootRow> sells, List<Spawn> spawns) {
    return new NpcDetail(s, 0, null, drops, sells, List.of(), List.of(), spawns);
  }

  private static LootRow loot(long id, String name, Double pct, String origin, String className) {
    return new LootRow(new EntityRef(ITEM, id), name, 10, pct, origin, null, null, className, null);
  }

  private static List<LootRow> mixedDrops() {
    return List.of(loot(1, "Both Sword", 5.0, "both", "Weapon"), loot(2, "Questie Helm", 3.0, "questie", "Armor"),
        loot(3, "Atlas Dust", 2.0, "atlasloot", "Reagent"), loot(4, "Atlas Axe", 1.0, "atlasloot", "Weapon"));
  }

  /** The group headings of the drop table, with their counts. */
  private static List<String> headings(NpcPage p) {
    List<String> headings = new java.util.ArrayList<>();
    for (int r = 0; r < p.drops().rowCount(); r++) {
      if (p.drops().model().rowAt(r) instanceof GroupedRows.Heading<LootRow> h) {
        headings.add(h.text());
      }
    }
    return headings;
  }

  private static NpcPage filtered(SourceFilter filter) {
    return new NpcPage(npc(HOGGER, mixedDrops(), List.of(), List.of()), null, null, Map.of(), filter, t -> {},
        text -> {});
  }

  /** The item names of a grouped table, without its heading rows. */
  private static List<String> itemNames(NpcPage p) {
    List<String> names = new java.util.ArrayList<>();
    for (int r = 0; r < p.drops().rowCount(); r++) {
      if (p.drops().model().rowAt(r) instanceof GroupedRows.Entry<LootRow> e) {
        names.add(e.value().displayName());
      }
    }
    return names;
  }

  @Test
  void theSourceFilterHidesDropsFromTheHiddenSourceAlone() {
    var all = filtered(SourceFilter.ALL);
    assertThat(all.section("Drops").heading().getText()).isEqualTo("Drops (4)");
    assertThat(all.section("Drops").note()).isNull();
    assertThat(headings(all)).containsExactly("Gear (3)", "Trade goods (1)");

    var noAtlas = filtered(SourceFilter.HIDE_ATLASLOOT_ONLY);
    assertThat(noAtlas.section("Drops").heading().getText()).isEqualTo("Drops (2)");
    assertThat(itemNames(noAtlas)).containsExactlyInAnyOrder("Both Sword", "Questie Helm");
    assertThat(headings(noAtlas)).as("the group counts follow, an emptied group goes").containsExactly("Gear (2)");
    assertThat(noAtlas.section("Drops").note().getText()).isEqualTo("2 rows hidden by the source filter");

    var noQuestie = filtered(SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(noQuestie.section("Drops").heading().getText()).isEqualTo("Drops (3)");
    assertThat(itemNames(noQuestie)).containsExactlyInAnyOrder("Both Sword", "Atlas Dust", "Atlas Axe");
    assertThat(headings(noQuestie)).containsExactly("Gear (2)", "Trade goods (1)");
    assertThat(noQuestie.section("Drops").note().getText()).isEqualTo("1 row hidden by the source filter");
  }

  @Test
  void dropsAllHiddenLeaveTheSectionWithItsNote() {
    var d = npc(HOGGER, List.of(loot(3, "Atlas Ring", 2.0, "atlasloot", "Armor")), List.of(), List.of());
    var p = new NpcPage(d, null, null, Map.of(), SourceFilter.HIDE_ATLASLOOT_ONLY, t -> {}, text -> {});
    assertThat(p.section("Drops").heading().getText()).isEqualTo("Drops (0)");
    assertThat(p.section("Drops").note().getText()).isEqualTo("1 row hidden by the source filter");
    assertThat(p.drops().isVisible()).isFalse();
  }

  @Test
  void aDropRateWithLoadedSourcesListsThemInItsTooltip() {
    var sword = new LootRow(new EntityRef(ITEM, 1), "Sword", 10, 5.0, "both", null, null, "Weapon", null, true, 2);
    var d = npc(HOGGER, List.of(sword, loot(2, "Helm", 3.0, "questie", "Armor")), List.of(), List.of());
    var sources = Map.of(new RateSource.Key(448, 1),
        List.of(new RateSource("questie-wowhead", 5.0), new RateSource("atlasloot", null)));
    var p = new NpcPage(d, null, null, sources, SourceFilter.ALL, t -> {}, text -> {});
    JTable t = p.drops().table();
    int rate = t.getColumnModel().getColumnIndex("Rate");
    List<String> tips = new java.util.ArrayList<>();
    for (int r = 0; r < t.getRowCount(); r++) {
      tips.add(Tables.tooltipAt(t, r, rate));
    }
    assertThat(tips).containsOnlyOnce("Questie 5.00 %  ·  AtlasLoot no rate").filteredOn(java.util.Objects::nonNull)
        .hasSize(1);
  }

  @Test
  void vanCleefsLootIsGroupedByItemClassAndRankedByRate() {
    var p = new NpcPage(vanCleef(), t -> {}, text -> {});
    JTable t = p.drops().table();
    assertThat(column(t, 0)).containsExactly("Gear (1)", "Cruel Barb", "Quest items (1)", "An Unsent Letter",
        "Other (3)", "Unknown item #5202", "Unknown item #5193", "Unknown item #10399");
    assertThat(p.section("Drops").heading().getText()).isEqualTo("Drops (5)");
    // group headings are dividers: muted, linking nowhere, not selectable, and the order is fixed
    assertThat(p.drops().model().refAt(0, 0)).isEmpty();
    assertThat(p.drops().model().refAt(1, 0)).contains(new EntityRef(ITEM, 5191));
    assertThat(t.prepareRenderer(t.getCellRenderer(2, 0), 2, 0).getForeground())
        .isEqualTo(Theme.current().textMuted());
    t.changeSelection(2, 0, false, false);
    assertThat(t.getSelectedRow()).isEqualTo(3);
    assertThat(t.getRowSorter()).isNull();
  }

  @Test
  void unknownRatesComeLastWithinTheirGroup() {
    var p = new NpcPage(npc(HOGGER, List.of(loot(1, "Low", 5.0, "questie", "Armor"),
        loot(2, "Unknown", null, "questie", "Weapon"), loot(3, "High", 50.0, "questie", "Weapon"),
        loot(4, "Abe", null, "questie", "Weapon"), loot(5, "Recipe", 1.0, "questie", "Recipe")), List.of(), List.of()),
        t -> {}, text -> {});
    assertThat(column(p.drops().table(), 0)).containsExactly("Gear (4)", "High", "Low", "Abe", "Unknown",
        "Recipes (1)", "Recipe");
  }

  private static LootRow drop(long id, String name, Double pct, String className, int droppers, String skill) {
    return new LootRow(new EntityRef(ITEM, id), name, 15, pct, "questie", null, null, className, null, false, 0, null,
        null, skill, null, droppers);
  }

  /** Hogger's kind of drops: his own items, then the generic pool hundreds of NPCs drop. */
  private static NpcPage hoggerWithWorldDrops() {
    return new NpcPage(npc(HOGGER, List.of(drop(1, "Huge Gnoll Claw", 84.0, "Quest", 1, null),
        drop(2, "Tough Jerky", null, "Consumable", 161, null),
        drop(3, "Pattern: Dark Leather Tunic", null, "Recipe", 562, "Requires Leatherworking (75)"),
        drop(4, "Gold Pickup Schedule", null, "Quest", 4, null),
        drop(5, "Rare Find", null, "Consumable", ItemCategories.WORLD_DROP_NPCS - 1, null)), List.of(), List.of()),
        t -> {}, text -> {});
  }

  @Test
  void worldDropsComeLastInACollapsedGroupThatAClickOpensAndEnterCloses() {
    var p = hoggerWithWorldDrops();
    JTable t = p.drops().table();
    assertThat(column(t, 0)).containsExactly("Quest items (2)", "Huge Gnoll Claw", "Gold Pickup Schedule",
        "Consumables (1)", "Rare Find", "World drops (2) ▸");
    assertThat(p.section("Drops").heading().getText()).as("the heading counts every drop").isEqualTo("Drops (5)");
    Rectangle heading = t.getCellRect(5, 1, true);
    t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_CLICKED, 0L, 0, heading.x + 2, heading.y + 2, 1, false,
        MouseEvent.BUTTON1));
    assertThat(column(t, 0)).endsWith("World drops (2) ▾", "Pattern: Dark Leather Tunic", "Tough Jerky");
    assertThat(t.getSelectedRow()).as("the heading can be selected").isEqualTo(5);
    Object enter = t.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ENTER"));
    t.getActionMap().get(enter).actionPerformed(new ActionEvent(t, ActionEvent.ACTION_PERFORMED, "enter"));
    assertThat(column(t, 0)).last().isEqualTo("World drops (2) ▸");
    assertThat(t.prepareRenderer(t.getCellRenderer(0, 0), 0, 0).getForeground()).as("other headings stay muted")
        .isEqualTo(Theme.current().textMuted());
    t.changeSelection(0, 0, false, false);
    assertThat(t.getSelectedRow()).as("other headings are skipped").isEqualTo(1);
  }

  @Test
  void onlyTheGenericPoolFoldsIntoWorldDropsNeverQuestItemsGearOrBags() {
    int many = ItemCategories.WORLD_DROP_NPCS + 5;
    var p = new NpcPage(npc(HOGGER, List.of(drop(1, "Deviate Hide", null, "Quest", many, null),
        drop(2, "Rugged Leather Pants", null, "Armor", many, null), drop(3, "Linen Bag", null, "Container", many, null),
        drop(4, "Cheap Blade", null, "Weapon", many, null), drop(5, "Linen Cloth", null, "Trade Goods", many, null),
        drop(6, "Broken Fang", null, "Miscellaneous", many, null), drop(7, "Ice Cold Milk", null, "Consumable", many,
            null), drop(8, "Pattern: Thing", null, "Recipe", many, null),
        drop(9, "Simple Flour", null, "Reagent", many, null)), List.of(), List.of()), t -> {}, text -> {});
    List<Object> rows = column(p.drops().table(), 0);
    assertThat(rows).contains("Deviate Hide", "Rugged Leather Pants", "Linen Bag", "Cheap Blade")
        .doesNotContain("Linen Cloth", "Broken Fang", "Ice Cold Milk", "Pattern: Thing", "Simple Flour");
    assertThat(rows).last().isEqualTo("World drops (5)" + GroupedRows.COLLAPSED);
  }

  @Test
  void aRatedPoolDropManyNpcsShareIsStillAWorldDrop() {
    // VMaNGOS rates most of the generic pool from its shared reference tables: a rate does not make it Hogger's own
    int many = ItemCategories.WORLD_DROP_NPCS + 5;
    var p = new NpcPage(npc(HOGGER, List.of(drop(1, "Huge Gnoll Claw", 84.0, "Quest", 1, null),
        drop(2, "Tough Jerky", 6.1, "Consumable", 161, null), drop(3, "Tigerseye", 2.96, "Trade Goods", many, null),
        drop(4, "Malachite", 0.64, "Trade Goods", ItemCategories.WORLD_DROP_NPCS - 1, null)), List.of(), List.of()),
        t -> {}, text -> {});
    assertThat(column(p.drops().table(), 0)).containsExactly("Quest items (1)", "Huge Gnoll Claw",
        "Trade goods (1)", "Malachite", "World drops (2)" + GroupedRows.COLLAPSED);
  }

  @Test
  void recipesShowTheirRequiredSkillUnderASkillLabel() {
    var p = new NpcPage(npc(HOGGER, List.of(drop(1, "Sword", 5.0, "Weapon", 1, null),
        drop(2, "Pattern: Fine Leather Boots", 2.0, "Recipe", 3, "Requires Leatherworking (65)"),
        drop(3, "Plans: Odd", 1.0, "Recipe", 3, null)), List.of(), List.of()), t -> {}, text -> {});
    JTable t = p.drops().table();
    assertThat(columnNames(t)).containsExactly("Item", "Item level", "Rate");
    assertThat(column(t, 0)).containsExactly("Gear (1)", "Sword", "Recipes (2)", "Pattern: Fine Leather Boots",
        "Plans: Odd");
    assertThat(column(t, 1)).containsExactly(null, 15, "Skill", "Leatherworking 65", 15);
  }

  @Test
  void columnsEmptyOrUniformForEveryRowAreHidden() {
    var p = new NpcPage(vanCleef(), t -> {}, text -> {});
    JTable drops = p.drops().table();
    // no row has a slot; origins differ (both, atlasloot), so Origin stays
    assertThat(columnNames(drops)).containsExactly("Item", "Item level", "Rate", "Origin");
    assertThat(drops.prepareRenderer(drops.getCellRenderer(1, 2), 1, 2)).isInstanceOf(RateBarRenderer.class);
    assertThat(drops.prepareRenderer(drops.getCellRenderer(1, 3), 1, 3)).isInstanceOf(OriginChip.class);
    // every origin questie: the column says nothing
    var uniform = new NpcPage(npc(HOGGER, List.of(loot(1, "A", 5.0, "questie", "Armor"),
        loot(2, "B", 1.0, "questie", "Armor")), List.of(), List.of()), t -> {}, text -> {});
    assertThat(columnNames(uniform.drops().table())).containsExactly("Item", "Item level", "Rate");
  }

  @Test
  void spawnsReadAsCoordinatesAndCopyAWayCommand() {
    List<String> copied = new ArrayList<>();
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcPage(vanCleef(), opened::add, copied::add);
    SpawnSection spawns = p.spawns();
    JTable t = spawns.table().table();
    // the spawn is in Westfall, not in The Deadmines (the NPC's zone): the Zone column stays
    assertThat(columnNames(t)).containsExactly("Zone", "Coordinates");
    assertThat(column(t, 1)).containsExactly("42.3, 67.4");
    assertThat(spawns.copyButton().getText()).isEqualTo("Copy /way");
    assertThat(spawns.copyButton().isEnabled()).isTrue();
    spawns.copyButton().doClick();
    assertThat(copied).containsExactly("/way Westfall 42.3 67.4");
    spawns.table().activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
  }

  @Test
  void spawnsInThePageZoneHideTheZoneAndCopyTheSelectedPoint() {
    List<String> copied = new ArrayList<>();
    var p = new NpcPage(npc(HOGGER, List.of(), List.of(),
        List.of(new Spawn(12, "Elwynn Forest", 10.04, 20.0), new Spawn(12, "Elwynn Forest", 33.35, 44.44))),
        t -> {}, copied::add);
    JTable t = p.spawns().table().table();
    assertThat(columnNames(t)).containsExactly("Coordinates");
    assertThat(column(t, 0)).containsExactly("10.0, 20.0", "33.4, 44.4");
    p.spawns().copyButton().doClick();
    t.changeSelection(1, 0, false, false);
    p.spawns().copyButton().doClick();
    assertThat(copied).containsExactly("/way Elwynn Forest 10.0 20.0", "/way Elwynn Forest 33.4 44.4");
  }

  @Test
  void ctrlShiftCOnASpawnRowCopiesItsWayCommand() {
    List<String> copied = new ArrayList<>();
    var p = new NpcPage(vanCleef(), t -> {}, copied::add);
    JTable t = p.spawns().table().table();
    t.changeSelection(0, 0, false, false);
    assertThat(Keys.press(t, "shift ctrl C")).isTrue();
    assertThat(Keys.press(t, "ctrl C")).isTrue();
    assertThat(copied).containsExactly("/way Westfall 42.3 67.4", "Westfall");
    var inside = new NpcPage(npc(HOGGER, List.of(), List.of(), List.of(new Spawn(40, "Westfall", -1, -1))),
        t2 -> {}, copied::add);
    JTable noCoordinates = inside.spawns().table().table();
    noCoordinates.changeSelection(0, 0, false, false);
    Keys.press(noCoordinates, "shift ctrl C");
    assertThat(copied).hasSize(2); // a point without coordinates has no /way to copy
  }

  @Test
  void aSpawnWithoutCoordinatesNamesItsZoneAndCannotBeCopied() {
    // the fixture spawn is inside the instance (-1, -1), in the NPC's own zone: the zone alone is left
    var p = new NpcPage(repos.npcs().detail(639).get(), t -> {}, text -> {});
    JTable t = p.spawns().table().table();
    assertThat(columnNames(t)).containsExactly("Zone");
    assertThat(column(t, 0)).containsExactly("The Deadmines");
    assertThat(p.spawns().copyButton().isEnabled()).isFalse();
    var elsewhere = new NpcPage(npc(HOGGER, List.of(), List.of(), List.of(new Spawn(40, "Westfall", -1, -1))),
        t2 -> {}, text -> {});
    assertThat(columnNames(elsewhere.spawns().table().table())).containsExactly("Zone");
    assertThat(elsewhere.spawns().copyButton().isEnabled()).isFalse();
  }

  @Test
  void rankChipNamesTheRank() {
    var p = new NpcPage(vanCleef(), t -> {}, text -> {});
    // Questie calls him rare elite; he is an AtlasLoot encounter, so a boss
    assertThat(p.rankChip().getText()).isEqualTo("Boss");
    assertThat(p.rankChip().style()).isEqualTo(RankChip.Style.GOLD_FILL);
    assertThat(p.rankChip().isVisible()).isTrue();
  }

  @Test
  void headerHasTitleLevelsAndAZoneLink() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcPage(vanCleef(), opened::add, text -> {});
    assertThat(p.header().title()).isEqualTo("Edwin VanCleef <Defias Kingpin>");
    assertThat(p.header().subtitle()).isEqualTo("NPC #639 · Level 21 · The Deadmines");
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 1581)));
  }

  @Test
  void sellsStartsAndEndsHaveSectionsAndEmptyOnesAreLeftOut() {
    QuestSummary quest = new QuestSummary(2, "Sharptalon's Claw", 18, 15, "Elwynn Forest");
    var d = new NpcDetail(HOGGER, 0, null, List.of(), List.of(loot(9, "Bread", null, null, "Consumable")),
        List.of(quest), List.of(quest), List.of());
    var p = new NpcPage(d, t -> {}, text -> {});
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Sells", "Starts quests", "Ends quests");
    // sold items have no rate, origin or slot
    assertThat(columnNames(p.section("Sells").table().table())).containsExactly("Item", "Item level");
    // quests filed under the NPC's own zone do not repeat it
    assertThat(columnNames(p.section("Starts quests").table().table()))
        .containsExactly("Name", "Level", "Required level");
    assertThat(p.drops()).isNull();
    assertThat(p.spawns()).isNull();
  }

  @Test
  void anNpcWithNothingKnownStillRenders() {
    var p = new NpcPage(npc(new NpcSummary(5, null, null, null, null, null, null, null), List.of(), List.of(),
        List.of()), t -> {}, text -> {});
    assertThat(p.header().title()).isEqualTo("Unknown NPC #5");
    assertThat(p.header().subtitle()).isEqualTo("NPC #5");
    assertThat(p.header().links()).isEmpty();
    assertThat(p.rankChip().isVisible()).isFalse();
    assertThat(p.sections()).isEmpty();
  }

  @Test
  void dropRowsLinkToTheirItem() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcPage(vanCleef(), opened::add, text -> {});
    p.drops().activateRow(1);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 5191)));
  }

  @Test
  void spawnsOnMap() {
    NpcDetail hogger = repos.npcs().detail(448).get();
    BufferedImage elwynn = Images.decode(repos.zones().map(12).orElseThrow().jpeg()).orElseThrow();
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcPage(hogger, 12L, elwynn, opened::add, text -> {});
    MapView map = p.spawnMap();
    assertThat(map.image()).isSameAs(elwynn);
    assertThat(map.pins()).hasSize(5).allSatisfy(pin -> {
      assertThat(pin.kind()).isEqualTo(PinKind.SELF);
      assertThat(pin.ref()).isEqualTo(new EntityRef(NPC, 448));
      assertThat(pin.name()).isEqualTo("Hogger");
    });
    assertThat(map.pins().get(0)).extracting(Pin::x, Pin::y).containsExactly(25.02, 92.9);
    // inside the Spawns section, above the spawn table
    SpawnSection spawns = p.spawns();
    assertThat(map.getParent()).isSameAs(spawns.table().getParent());
    assertThat(map.getParent().getComponents()).containsExactly(map, spawns.table());
    assertThat(javax.swing.SwingUtilities.isDescendingFrom(map, p.section("Spawns"))).isTrue();
    assertThat(column(spawns.table().table(), 0)).hasSize(5);
    // the page's own pins open nothing
    map.setSize(1002, 668);
    var at = map.projection().toComponent(25.02, 92.9);
    map.dispatchEvent(new java.awt.event.MouseEvent(map, java.awt.event.MouseEvent.MOUSE_CLICKED, 0L, 0,
        (int) at.x, (int) at.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    assertThat(opened).isEmpty();
  }

  @Test
  void onlyTheSpawnsInTheMappedZoneArePinnedAndWithoutAMapTheTableStandsAlone() {
    BufferedImage elwynn = Images.decode(repos.zones().map(12).orElseThrow().jpeg()).orElseThrow();
    var d = npc(HOGGER, List.of(), List.of(), List.of(new Spawn(12, "Elwynn Forest", 10, 20),
        new Spawn(40, "Westfall", 30, 40), new Spawn(12, "Elwynn Forest", -1, -1),
        new Spawn(12, "Elwynn Forest", 100.5, 60), new Spawn(12, "Elwynn Forest", 50, 60)));
    var p = new NpcPage(d, 12L, elwynn, t -> {}, text -> {});
    // other zones and points off the map (-1, -1 and 100.5, 60) are not pinned
    assertThat(p.spawnMap().pins()).extracting(Pin::x).containsExactly(10.0, 50.0);
    var plain = new NpcPage(d, t -> {}, text -> {});
    assertThat(plain.spawnMap()).isNull();
    assertThat(plain.spawns().table().getParent()).isSameAs(plain.section("Spawns"));
    var noSpawns = new NpcPage(npc(HOGGER, List.of(), List.of(), List.of()), 12L, elwynn, t -> {}, text -> {});
    assertThat(noSpawns.spawnMap()).isNull();
  }
}
