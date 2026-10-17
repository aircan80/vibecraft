package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.Keys;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RankChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
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
    assertThat(p.rankChip().getText()).isEqualTo("Rare Elite");
    assertThat(p.rankChip().style()).isEqualTo(RankChip.Style.SILVER_OUTLINE);
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
}
