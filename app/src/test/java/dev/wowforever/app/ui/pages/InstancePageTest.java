package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.boundsIn;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static dev.wowforever.app.ui.pages.Tables.layOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class InstancePageTest {
  private final Repositories repos = FixtureDb.repositories();

  private InstancePage deadmines(List<ViewTarget> opened) {
    return new InstancePage(repos.instances().detail(2).get(), repos, opened::add);
  }

  /** What row {@code index} of {@code list} reads, through its renderer. */
  private static String rendered(JList<BossSummary> list, int index) {
    Component c = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(index),
        index, false, false);
    return ((JLabel) c).getText();
  }

  private static List<String> rendered(JList<BossSummary> list) {
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < list.getModel().getSize(); i++) {
      rows.add(rendered(list, i));
    }
    return rows;
  }

  private static InstanceDetail forever(boolean recorded) {
    return new InstanceDetail(new InstanceSummary(900, "City of Dalaran", "DUNGEON", 28, null,
        2959L, "City of Dalaran", "client:2959", "client", 28, "client_named", recorded ? "reported" : "none"),
        List.of(new BossSummary(901, "Arcane Anomaly", null, null, null, false),
            new BossSummary(902, "Mana Wraith", null, null, null, false)), recorded ? List.of("NORMAL") : List.of());
  }

  @Test
  void foreverDungeonReadsItsStateWithoutQueryingLoot() {
    var page = new InstancePage(forever(false), List.of(), (boss, mode) -> {
      throw new AssertionError("No recorded loot must not query on the EDT");
    }, "fixture-build", t -> {});
    assertThat(page.header().chips()).extracting(c -> ((dev.wowforever.app.ui.widgets.Chip) c).getText()).containsExactly("New in Forever", "Level 28+");
    assertThat(page.header().chips().get(1).getToolTipText()).isEqualTo("Tuning level from the game client");
    assertThat(page.coverage().getText()).isEqualTo(InstancePage.COVERAGE_LINE.formatted(2, 0, "fixture-build"));
    assertThat(page.encounterCaption().getText()).isEqualTo("Encounter order in the game client");
    assertThat(page.bossesNote().getText()).isEqualTo(InstancePage.BOSSES_NOTE);
    var cell = page.encounters().getCellRenderer().getListCellRendererComponent(page.encounters(),
        page.encounters().getModel().getElementAt(0), 0, false, false);
    assertThat(cell.getAccessibleContext().getAccessibleName()).contains("1", "Arcane Anomaly", "Loot not recorded yet");
    assertThat(page.noLootNote().getText()).isEqualTo(InstancePage.NO_LOOT_NOTE);
    assertThat(page.lootTable()).isNull();
    assertThat(page.extraListsHeading().isVisible()).isFalse();
    page.selectBoss(1);
    assertThat(page.lootTable()).isNull();
  }

  @Test
  void atlasLootInstanceHasNoForeverCoverageOrChips() {
    var page = deadmines(new ArrayList<>());
    assertThat(page.header().chips()).isEmpty();
    assertThat(page.coverage()).isNull();
    assertThat(page.encounterCaption()).isNull();
    assertThat(page.lootTable()).isNotNull();
  }

  @Test
  void futureRecordedClientLootStaysVisibleAndUnverified() {
    LootRow item = new LootRow(new EntityRef(ITEM, 1), "Recorded blade", 20, null, null, null, 2, "Weapon", 7);
    var page = new InstancePage(forever(true), List.of(), (boss, mode) -> List.of(item), "future-build", t -> {});
    assertThat(page.coverage().getText()).contains("recorded", "unverified").doesNotContain("confirmed");
    assertThat(page.lootTable().rowCount()).isPositive();
    assertThat(page.lootSection().note().getText()).contains("unverified");
  }

  @Test
  void encountersAreNumberedWithLevelAndKind() {
    var p = deadmines(new ArrayList<>());
    assertThat(rendered(p.encounters())).containsExactly(
        "1. Rhahk'Zor  Lv 19  Boss",
        "2. Miner Johnson  Lv 19  Rare",
        "3. Sneed  Lv 20  Boss",
        "4. Sneed's Shredder  Lv 20  Boss",
        "5. Gilnid  Lv 20  Boss",
        "6. Mr. Smite  Lv 20  Boss",
        "7. Captain Greenskin  Lv 20  Boss",
        "8. Edwin VanCleef  Lv 21  Boss",
        "9. Cookie  Lv 20  Elite");
  }

  @Test
  void aSkullLevelTakesTheNpcLevelAndAnUnknownLevelIsLeftOut() {
    var mc = new InstancePage(repos.instances().detail(3).get(), repos, t -> {});
    assertThat(rendered(mc.encounters(), 0)).isEqualTo("1. Lucifron  Lv 63  Boss");
    assertThat(mc.bossHeader().subtitle()).startsWith("Level 63");
    var unknown = new InstancePage(new InstanceDetail(new InstanceSummary(9, "X", "DUNGEON", null, null, null, null),
        List.of(new BossSummary(1, "Nameless", null, null, null, false),
            new BossSummary(2, "Skull", null, 999, null, false))), repos, t -> {});
    assertThat(rendered(unknown.encounters())).containsExactly("1. Nameless  Boss", "2. Skull  Boss");
    unknown.selectBoss(1);
    assertThat(unknown.bossHeader().subtitle()).isEmpty();
  }

  @Test
  void extraListsSitUnderOtherLootLists() {
    var p = deadmines(new ArrayList<>());
    assertThat(p.extraListsHeading().getText()).isEqualTo("Other loot lists");
    assertThat(p.extraListsHeading().isVisible()).isTrue();
    assertThat(rendered(p.extraLists())).containsExactly("Defias Gunpowder", "Trash Mobs");
    var rfc = new InstancePage(repos.instances().detail(1).get(), repos, t -> {});
    assertThat(rfc.extraListsHeading().isVisible()).isFalse();
    assertThat(rfc.extraLists().isVisible()).isFalse();
  }

  @Test
  void theFirstEncounterIsSelectedAndItsLootGroupedBySlotType() {
    var rfc = new InstancePage(repos.instances().detail(1).get(), repos, t -> {});
    assertThat(rfc.encounters().getSelectedIndex()).isZero();
    // in AtlasLoot slot order within each group: the cape is slot 1, the cuffs slot 2
    assertThat(column(rfc.lootTable().table(), 0)).containsExactly("Weapons (1)", "Cursed Felblade", "Armour (2)",
        "Subterranean Cape", "Crystalline Cuffs");
    assertThat(rfc.lootTable().model().refAt(1, 0)).contains(new EntityRef(ITEM, 14145));
    assertThat(rfc.lootTable().model().refAt(3, 0)).contains(new EntityRef(ITEM, 14149));
    assertThat(rfc.lootTable().model().refAt(4, 0)).contains(new EntityRef(ITEM, 14148));
    assertThat(rfc.lootSection().heading().text()).isEqualTo("Loot (3)");
  }

  @Test
  void selectingAnEncounterShowsItsLootAndLinksItsNpc() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = deadmines(opened);
    p.selectBoss(7);
    assertThat(column(p.lootTable().table(), 0)).containsExactly("Weapons (1)", "Cruel Barb", "Other (4)",
        "Unknown item #5193", "Unknown item #5202", "Unknown item #10399", "An Unsent Letter");
    // AtlasLoot is the origin of every row: the column is hidden
    assertThat(columnNames(p.lootTable().table())).doesNotContain("Origin");
    assertThat(p.bossHeader().title()).isEqualTo("Edwin VanCleef");
    p.bossHeader().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 639)));
  }

  @Test
  void selectingAnExtraListClearsTheEncounterAndBack() {
    var p = deadmines(new ArrayList<>());
    p.selectBoss(10);
    assertThat(p.encounters().isSelectionEmpty()).isTrue();
    assertThat(p.extraLists().getSelectedIndex()).isEqualTo(1);
    assertThat(p.bossHeader().title()).isEqualTo("Trash Mobs");
    assertThat(p.lootSection().heading().text()).isEqualTo("Loot (1)");
    p.selectBoss(0);
    assertThat(p.extraLists().isSelectionEmpty()).isTrue();
    assertThat(p.bossHeader().title()).isEqualTo("Rhahk'Zor");
  }

  /** Selects Edwin VanCleef, whose loot has rates. */
  private static void selectVanCleef(InstancePage p) {
    int vanCleef = -1;
    for (int i = 0; i < p.encounters().getModel().getSize(); i++) {
      if (java.util.Objects.equals(p.encounters().getModel().getElementAt(i).npcId(), 639L)) {
        vanCleef = i;
      }
    }
    p.selectBoss(vanCleef);
  }

  @Test
  void bossLootRatesListTheirSourcesLoadedWithTheLoot() {
    var p = deadmines(new ArrayList<>());
    selectVanCleef(p);
    JTable t = p.lootTable().table();
    int rate = t.getColumnModel().getColumnIndex("Rate");
    int letter = -1;
    for (int r = 0; r < t.getRowCount(); r++) {
      if ("An Unsent Letter".equals(String.valueOf(t.getValueAt(r, 0)))) {
        letter = r;
      }
    }
    assertThat(letter).isNotNegative();
    assertThat(Tables.tooltipAt(t, letter, rate)).isEqualTo(
        "Questie 56.07 %  ·  Questie correction 60.00 %  ·  AtlasLoot 56.07 %  ·  AtlasLoot override 57.00 %");
  }

  @Test
  void aBossSelectionAsksForItsSourcesOnceForItsRatedItems() {
    BossLoot real = BossLoot.of(repos);
    List<String> calls = new ArrayList<>();
    BossLoot counting = new BossLoot() {
      @Override
      public List<LootRow> lookup(long bossId, String difficulty) {
        return real.lookup(bossId, difficulty);
      }

      @Override
      public java.util.Map<dev.wowforever.app.model.RateSource.Key, List<dev.wowforever.app.model.RateSource>>
          sources(long npcId, List<Long> itemIds) {
        calls.add(npcId + " " + itemIds.stream().sorted().toList());
        return real.sources(npcId, itemIds);
      }
    };
    var p = new InstancePage(repos.instances().detail(2).get(), counting, t -> {});
    calls.clear();
    selectVanCleef(p);
    assertThat(calls).containsExactly("639 [2874, 5191, 5193, 5202, 10399]");
  }

  @Test
  void aFakeBossLootWithoutSourcesShowsNoRateTooltips() {
    var d = repos.instances().detail(2).get();
    var p = new InstancePage(d, (bossId, difficulty) -> repos.instances().bossLoot(bossId, difficulty), t -> {});
    selectVanCleef(p);
    JTable t = p.lootTable().table();
    int rate = t.getColumnModel().getColumnIndex("Rate");
    for (int r = 0; r < t.getRowCount(); r++) {
      assertThat(Tables.tooltipAt(t, r, rate)).isNull();
    }
  }

  @Test
  void difficultyControlIsAbsentWithOneDifficulty() {
    var p = deadmines(new ArrayList<>());
    assertThat(repos.instances().detail(2).get().difficulties()).hasSize(1);
    assertThat(p.difficultyControl()).isNull();
  }

  @Test
  void lootKeepsAtlasLootsOrderWithoutItsColumnAndNamesClassRestrictionsMuted() {
    BossSummary boss = new BossSummary(7, "Lord Alexei Barov", 10504L, 60, null, false);
    var d = new InstanceDetail(new InstanceSummary(9, "Scholomance", "DUNGEON", 58, 60, null, null), List.of(boss),
        List.of("NORMAL"));
    BossLoot loot = (bossId, difficulty) -> List.of(
        new LootRow(new EntityRef(ITEM, 1), "Barovian Family Sword", 62, null, "atlasloot", 1, 2, "Weapon", 8, false,
            0, 3, null, null, null, 0),
        new LootRow(new EntityRef(ITEM, 2), "Necropile Robe", 61, null, "atlasloot", 2, 4, "Armor", 1, false, 0, 3,
            null, null, 128 | 256 | 16, 0),
        new LootRow(new EntityRef(ITEM, 3), "Dreadmist Wraps", 57, null, "atlasloot", 3, 4, "Armor", 1, false, 0, 3,
            null, null, 256, 0));
    var p = new InstancePage(d, loot, t -> {});
    JTable t = p.lootTable().table();
    assertThat(columnNames(t)).containsExactly("Item", "Classes", "Item level").doesNotContain("#", "Slot");
    assertThat(column(t, 0)).containsExactly("Weapons (1)", "Barovian Family Sword", "Armour (2)",
        "Necropile Robe", "Dreadmist Wraps");
    assertThat(column(t, 1)).containsExactly(null, null, null, "Priest, Mage, Warlock", "Warlock");
    assertThat(t.prepareRenderer(t.getCellRenderer(4, 1), 4, 1).getForeground())
        .isEqualTo(Theme.current().textMuted());
  }

  @Test
  void aPageBuiltAgainSelectsTheSameEncounterInTheSameDifficulty() {
    BossSummary first = new BossSummary(7, "Onyxia", 10184L, 63, null, false);
    BossSummary trash = new BossSummary(8, "Trash", null, null, null, true);
    var d = new InstanceDetail(new InstanceSummary(9, "Onyxia's Lair", "RAID40", 60, 60, null, null),
        List.of(first, trash), List.of("NORMAL", "HEROIC"));
    BossLoot loot = (bossId, difficulty) -> List.of(new LootRow(new EntityRef(ITEM, bossId), bossId + " " + difficulty,
        60, null, "atlasloot", 1, 2, "Weapon", 7));
    var before = new InstancePage(d, loot, t -> {});
    before.selectBoss(1);
    ((JToggleButton) before.difficultyControl().getComponent(1)).doClick();
    var after = new InstancePage(d, loot, t -> {});
    after.restoreState(before.saveState());
    assertThat(after.extraLists().getSelectedIndex()).isZero();
    assertThat(column(after.lootTable().table(), 0)).contains("8 HEROIC");
    assertThat(((JToggleButton) after.difficultyControl().getComponent(1)).isSelected()).isTrue();
  }

  @Test
  void twoDifficultiesShowASwitchThatReloadsTheLoot() {
    BossSummary boss = new BossSummary(7, "Onyxia", 10184L, 63, null, false);
    var d = new InstanceDetail(new InstanceSummary(9, "Onyxia's Lair", "RAID40", 60, 60, null, null), List.of(boss),
        List.of("NORMAL", "HEROIC"));
    List<String> lookups = new ArrayList<>();
    BossLoot loot = (bossId, difficulty) -> {
      lookups.add(bossId + " " + difficulty);
      return List.of(new LootRow(new EntityRef(ITEM, difficulty.equals("NORMAL") ? 1 : 2), difficulty + " item", 60,
          null, "atlasloot", 1, 2, "Weapon", 7));
    };
    var p = new InstancePage(d, loot, t -> {});
    assertThat(p.difficultyControl()).isNotNull();
    List<JToggleButton> toggles = new ArrayList<>();
    for (Component c : p.difficultyControl().getComponents()) {
      toggles.add((JToggleButton) c);
    }
    assertThat(toggles).extracting(JToggleButton::getText).containsExactly("Normal", "Heroic");
    assertThat(toggles.get(0).isSelected()).isTrue();
    assertThat(lookups).containsExactly("7 NORMAL");
    assertThat(column(p.lootTable().table(), 0)).containsExactly("Weapons (1)", "NORMAL item");
    toggles.get(1).doClick();
    assertThat(lookups).containsExactly("7 NORMAL", "7 HEROIC");
    assertThat(column(p.lootTable().table(), 0)).containsExactly("Weapons (1)", "HEROIC item");
    assertThat(toggles.get(1).isSelected()).isTrue();
    assertThat(p.difficultyControl().getParent()).as("the switch stays on the page").isNotNull();
  }

  @Test
  void headerLinksTheZoneAndTheNpcListOfTheZone() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = deadmines(opened);
    assertThat(p.header().title()).isEqualTo("The Deadmines");
    assertThat(p.header().subtitle()).isEqualTo("Dungeon · Level 10–17 · The Deadmines · NPCs in The Deadmines");
    p.header().links().forEach(b -> b.doClick());
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 1581)),
        new ViewTarget.List(NPC, NpcFilter.ALL.withZone(1581L)));
  }

  @Test
  void questsSection() {
    // the fixture's Deadmines is instance 2 (the shipped database's is 3; ShippedDataTest reads that one); the
    // quests are those filed under its zone, 1581, which DetailPanels.load reads with QuestRepository.byZone
    QuestSummary defias = new QuestSummary(166, "The Defias Brotherhood", 22, 14, "The Deadmines");
    QuestSummary bandanas = new QuestSummary(214, "Red Silk Bandanas", 17, 14, "The Deadmines");
    List<ViewTarget> opened = new ArrayList<>();
    var p = new InstancePage(repos.instances().detail(2).get(), List.of(bandanas, defias), repos, opened::add);
    assertThat(p.questsSection().heading().text()).isEqualTo("Quests (2)");
    JTable quests = p.questsSection().table().table();
    assertThat(columnNames(quests)).contains("Role");
    assertThat(column(quests, 1)).containsExactly("Red Silk Bandanas", "The Defias Brotherhood");
    assertThat(columnNames(quests)).as("every quest is in the instance's zone").doesNotContain("Zone");
    p.questsSection().table().activateRow(1);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(QUEST, 166)));
    // after the loot, and still there once another encounter is picked
    List<Component> pane = List.of(p.lootSection().getParent().getComponents());
    assertThat(pane.indexOf(p.lootSection())).isLessThan(pane.indexOf(p.questsSection()));
    p.selectBoss(1);
    assertThat(List.of(p.lootSection().getParent().getComponents())).endsWith(p.questsSection());
    List<String> right = java.util.Arrays.stream(p.lootSection().getParent().getComponents())
        .filter(SourceSection.class::isInstance).map(c -> ((SourceSection) c).title()).toList();
    assertThat(right).isEqualTo(SectionOrder.INSTANCE.stream().filter(right::contains).toList())
        .containsExactly("Loot", "Quests");
    assertThat(p.questsSection().isRowLimited()).as("the quests keep the eight-row cut").isTrue();
    assertThat(p.lootSection().isRowLimited()).as("an encounter's loot is the page: never cut").isFalse();
    assertThat(deadmines(new ArrayList<>()).questsSection()).as("no quests, no section").isNull();
  }

  @Test
  void anInstanceWithoutZoneOrBossesStillRenders() {
    var p = new InstancePage(new InstanceDetail(new InstanceSummary(9, "X", "RAID40", 60, 63, null, null),
        List.of()), repos, t -> {});
    assertThat(p.header().subtitle()).isEqualTo("Raid (40) · Level 60–63");
    assertThat(p.header().links()).isEmpty();
    assertThat(p.encounters().getModel().getSize()).isZero();
    assertThat(p.lootTable()).isNull();
  }

  @Test
  void aNarrowPageStacksTheEncountersAboveTheLootAndAWideOneSetsThemSideBySide() {
    var p = new InstancePage(repos.instances().detail(3).get(), repos, t -> {});
    p.setSize(420, 720); // the compact window
    layOut(p);
    assertThat(p.isStacked()).isTrue();
    Rectangle list = boundsIn(p.encounters(), p);
    Rectangle loot = boundsIn(p.lootSection(), p);
    assertThat(list.width).as("the encounters are not clipped").isGreaterThanOrEqualTo(
        p.encounters().getPreferredSize().width);
    assertThat(loot.y).as("the loot is below the encounters").isGreaterThan(list.y);
    assertThat(loot.width).as("one column, page wide").isGreaterThan(p.getWidth() / 2);
    JSplitPane split = p.split();
    double share = (double) split.getDividerLocation() / (split.getHeight() - split.getDividerSize());
    assertThat(share).as("the encounters start with 40 % of the stacked height").isCloseTo(0.4, within(0.02));
    Rectangle encounterPane = boundsIn(split.getTopComponent(), p);
    assertThat(encounterPane.height).as("laid out at that share")
        .isCloseTo(split.getDividerLocation(), within(2));

    p.setSize(1000, 720); // resized: the columns come back
    layOut(p);
    assertThat(p.isStacked()).isFalse();
    list = boundsIn(p.encounters(), p);
    loot = boundsIn(p.lootSection(), p);
    assertThat(loot.x).as("the loot is right of the encounters").isGreaterThan(list.x + list.width - 1);

    p.setSize(420, 720);
    layOut(p);
    assertThat(p.isStacked()).isTrue();
  }
  @Test void equipmentFilterUsesLoadedRowsAndStaysVisibleAfterRenderingAndHistory() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var lookups = new java.util.concurrent.atomic.AtomicInteger();
      BossLoot source=(boss,difficulty)-> {
        lookups.incrementAndGet();
        return List.of(new LootRow(new EntityRef(ITEM,1),"Waist",null,null,null,99).withInventoryType(6).withResistanceValues(java.util.Map.of(51,5)),
            new LootRow(new EntityRef(ITEM,2),"Legs",null,null,null,6).withInventoryType(7));
      };
      var page=new InstancePage(repos.instances().detail(2).orElseThrow(),source,t->{});
      var combo=findLootSlot(page);
      int loaded=lookups.get();
      combo.setSelectedIndex(6);
      assertThat(lookups).hasValue(loaded);
      assertThat(page.lootTable().model().getRowCount()).isEqualTo(2); // Other heading + true Waist
      assertThat(findLootSlot(page)).isSameAs(combo);
      Object saved=page.saveState();
      for(var component:combo.getParent().getComponents()) {
        if(component instanceof javax.swing.JComboBox<?> box && "loot-resistance-filter".equals(box.getName()))
          box.setSelectedIndex(1);
        if(component instanceof javax.swing.JSpinner spinner) spinner.setValue(6);
      }
      assertThat(page.lootTable().model().getRowCount()).isZero();
      assertThat(lookups).hasValue(loaded);
      combo.setSelectedIndex(0);
      int beforeRestore=lookups.get();
      page.restoreState(saved);
      assertThat(lookups).hasValue(beforeRestore+1);
      assertThat(findLootSlot(page).getSelectedItem().toString()).isEqualTo("Waist");
      assertThat(page.lootTable().model().getRowCount()).isEqualTo(2);
    });
  }

  private static javax.swing.JComboBox<?> findLootSlot(java.awt.Container root) {
    for (Component child:root.getComponents()) {
      if (child instanceof javax.swing.JComboBox<?> combo && "loot-slot-filter".equals(combo.getName())) return combo;
      if (child instanceof java.awt.Container container) {
        var found=findLootSlot(container); if(found!=null)return found;
      }
    }
    return null;
  }

}
