package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
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
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSplitPane;
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
    assertThat(rfc.lootSection().heading().getText()).isEqualTo("Loot (3)");
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
    assertThat(p.lootSection().heading().getText()).isEqualTo("Loot (1)");
    p.selectBoss(0);
    assertThat(p.extraLists().isSelectionEmpty()).isTrue();
    assertThat(p.bossHeader().title()).isEqualTo("Rhahk'Zor");
  }

  @Test
  void difficultyControlIsAbsentWithOneDifficulty() {
    var p = deadmines(new ArrayList<>());
    assertThat(repos.instances().detail(2).get().difficulties()).hasSize(1);
    assertThat(p.difficultyControl()).isNull();
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
  void headerReadsTypeLevelsAndLinksTheZoneTwice() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = deadmines(opened);
    assertThat(p.header().title()).isEqualTo("The Deadmines");
    assertThat(p.header().subtitle()).isEqualTo("Dungeon · Level 10–17 · The Deadmines · NPCs in The Deadmines");
    p.header().links().forEach(b -> b.doClick());
    EntityRef zone = new EntityRef(ZONE, 1581);
    assertThat(opened).containsExactly(new ViewTarget.Entity(zone), new ViewTarget.Entity(zone));
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
}
