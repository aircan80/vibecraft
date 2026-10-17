package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.wowforever.app.model.Craft;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.TestPages;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Shipped-data checks for where an item comes from beyond the drop lists: the client's crafting recipes (phase 3A
 * Task 8) and the VMaNGOS community drop rates in the rate provenance (Task 9). Skip them with
 * {@code -DexcludedGroups=shipped-data}.
 */
@Tag("shipped-data")
class ShippedProvenanceDataTest extends ShippedDataSupport {

  // --- client build ------------------------------------------------------------------------------------------------

  @Test
  void clientBuildIsCurrent() {
    // 3B Task 17: the installed client is 1.60.1.70450; schema v4 adds the client dungeons' instance columns
    assertThat(db.meta().get("client_product")).isEqualTo("wow_classic_beta");
    assertThat(db.meta().get("client_build")).isEqualTo("1.60.1.70450");
    assertThat(Long.parseLong(db.meta().get("client_item_count"))).isGreaterThanOrEqualTo(20_492);
    assertThat(db.meta().get("schema_version")).isEqualTo("4");
  }

  // --- crafting ----------------------------------------------------------------------------------------------------

  @Test
  void heavyLeatherIsMadeFromMediumLeather() {
    // 20649 Heavy Leather: Leatherworking, 5 Medium Leather (2319) make 1 Heavy Leather (4234); trainers teach it
    assertThat(repos.items().createdBy(4234)).singleElement().satisfies(c -> {
      assertThat(c.spellId()).isEqualTo(20649L);
      assertThat(c.spellName()).isEqualTo("Heavy Leather");
      assertThat(c.skillName()).isEqualTo("Leatherworking");
      assertThat(c.quantityText()).isEqualTo("1");
      assertThat(c.recipeItemIds()).isEmpty();
      assertThat(c.reagents()).singleElement().satisfies(r -> {
        assertThat(r.itemId()).isEqualTo(2319L);
        assertThat(r.count()).isEqualTo(5);
        assertThat(r.name()).isEqualTo("Medium Leather");
      });
    });
  }

  @Test
  void heavyLeatherIsAReagentOfOverAHundredCrafts() {
    List<Craft> crafts = repos.items().reagentFor(4234);
    assertThat(crafts).hasSize((int) scalar("select count(*) from craft_reagent where item_id = 4234"))
        .hasSizeGreaterThanOrEqualTo(100)
        .allSatisfy(c -> assertThat(c.reagents()).anySatisfy(r -> assertThat(r.itemId()).isEqualTo(4234L)));
    assertThat(repos.items().reagentFor(2319)).extracting(Craft::spellId).contains(20649L);
  }

  @Test
  void recipeItemsTeachTheirCrafts() {
    assertThat(count("craft_recipe_item")).isGreaterThan(1_500);
    assertThat(repos.items().taughtBy(2408)).as("Pattern: Fine Leather Gloves").singleElement().satisfies(c -> {
      assertThat(c.spellId()).isEqualTo(2164L);
      assertThat(c.itemId()).isEqualTo(2312L);
      assertThat(c.itemName()).isEqualTo("Fine Leather Gloves");
      assertThat(c.recipeItemIds()).containsExactly(2408L);
    });
    // 1225758 Prowler Steak is taught by two items; either one finds it
    for (long recipe : List.of(238645L, 250394L)) {
      assertThat(repos.items().taughtBy(recipe)).singleElement().satisfies(c -> {
        assertThat(c.spellName()).isEqualTo("Prowler Steak");
        assertThat(c.recipeItemIds()).containsExactly(238645L, 250394L);
      });
    }
  }

  @Test
  void committedImportReportShowsTheCraftCounters() throws Exception {
    String report = committedReport();
    assertThat(reportCounter(report, "clientdata.crafts")).isEqualTo(count("craft")).isGreaterThan(3_000);
    assertThat(reportCounter(report, "clientdata.crafts.reagents")).isEqualTo(count("craft_reagent"));
    assertThat(reportCounter(report, "clientdata.crafts.recipeItems")).isEqualTo(count("craft_recipe_item"));
    assertThat(reportCounter(report, "clientdata.crafts.rowsWithoutSpell")).isZero();
    assertThat(report).as("crafting was not skipped").doesNotContainPattern("(?m)^clientdata\\.crafts\\.skipped =");
  }

  // --- VMaNGOS community drop rates --------------------------------------------------------------------------------

  @Test
  void hoggersClawKeepsTheObservedRate() {
    // VMaNGOS rates the Huge Gnoll Claw (1931) 100 % while on the quest; Questie's observed 84 % keeps the headline
    assertThat(rows("select source, pct, quest_only from drop_rate_source where npc_id = 448 and item_id = 1931"
        + " order by source")).satisfiesExactly(
            r -> {
              assertThat(r[0]).isEqualTo("questie-wowhead");
              assertThat(((Number) r[1]).doubleValue()).isCloseTo(83.9965, within(1e-4));
              assertThat(((Number) r[2]).intValue()).isZero();
            },
            r -> {
              assertThat(r[0]).isEqualTo("vmangos");
              assertThat(((Number) r[1]).doubleValue()).isCloseTo(100.0, within(1e-9));
              assertThat(((Number) r[2]).intValue()).isEqualTo(1);
            });
    assertThat(rows("select drop_pct, pct_source, disagrees, source_count from item_npc_drop"
        + " where npc_id = 448 and item_id = 1931")).singleElement().satisfies(r -> {
          assertThat(((Number) r[0]).doubleValue()).isCloseTo(83.9965, within(1e-4));
          assertThat(r[1]).isEqualTo("questie-wowhead");
          assertThat(((Number) r[2]).intValue()).isEqualTo(1);
          assertThat(((Number) r[3]).intValue()).isEqualTo(2);
        });
  }

  @Test
  void vmangosHeadlinesMostDrops() {
    Map<String, Long> headlines = new TreeMap<>();
    for (Object[] r : rows("select coalesce(pct_source, '-'), count(*) from item_npc_drop group by 1")) {
      headlines.put((String) r[0], ((Number) r[1]).longValue());
    }
    // 137,373 vmangos, 17,548 without a rate and 9,334 questie-wowhead of 164,447 rows at the pinned inputs
    assertThat(headlines.get("vmangos")).isBetween(120_000L, 150_000L);
    assertThat(headlines.get("-")).isBetween(10_000L, 25_000L);
    assertThat(headlines.get("questie-wowhead")).isBetween(5_000L, 15_000L);
    // a quest-only VMaNGOS rate headlines only when no other source rates the pair
    assertThat(scalar("select count(*) from item_npc_drop d join drop_rate_source s on s.npc_id = d.npc_id"
        + " and s.item_id = d.item_id and s.source = 'vmangos' and s.quest_only = 1 where d.pct_source = 'vmangos'"
        + " and d.source_count > 1")).isZero();
    // ... and such headlines exist: 208 at the pinned inputs
    assertThat(scalar("select count(*) from item_npc_drop d join drop_rate_source s on s.npc_id = d.npc_id"
        + " and s.item_id = d.item_id and s.source = 'vmangos' and s.quest_only = 1 where d.pct_source = 'vmangos'"))
        .as("quest-only headlines").isBetween(100L, 400L);
    assertThat(scalar("select count(*) from drop_rate_source where quest_only = 1")).isGreaterThan(1_000);
    assertThat(db.meta().get("serverdb_asset")).isEqualTo("db-sqlite-4641790.zip");
    assertThat(db.meta().get("serverdb_commit")).startsWith("4641790");
  }

  @Test
  void committedImportReportShowsTheServerDbCounters() throws Exception {
    String report = committedReport();
    assertThat(reportCounter(report, "serverdb.skipped")).isZero();
    assertThat(reportCounter(report, "serverdb.loot.rows")).isGreaterThan(200_000);
    assertThat(reportCounter(report, "serverdb.unmatched.npcs")).isLessThanOrEqualTo(10);
    assertThat(reportCounter(report, "provenance.sources.vmangos"))
        .isEqualTo(scalar("select count(*) from drop_rate_source where source = 'vmangos'"));
  }

  // --- Vanilla rate in the rate cells (3B Task 7) ------------------------------------------------------------------

  /** The view row of {@code t} whose first cell reads {@code name}; -1 when none does. */
  private static int rowOf(JTable t, String name) {
    for (int r = 0; r < t.getRowCount(); r++) {
      if (name.equals(String.valueOf(t.getValueAt(r, 0)))) {
        return r;
      }
    }
    return -1;
  }

  /** The text and tooltip of the rate cell of view row {@code row} of {@code t}, as rendered. */
  private static String[] rateCell(JTable t, int row) {
    int col = t.getColumnModel().getColumnIndex(Columns.RATE);
    String tip = rateTooltip(t, row);
    var r = (RateBarRenderer) t.prepareRenderer(t.getCellRenderer(row, col), row, col);
    return new String[] {r.getText(), tip};
  }

  /** The tooltip a pointer resting on the rate cell of view row {@code row} of {@code t} shows. */
  private static String rateTooltip(JTable t, int row) {
    Rectangle cell = t.getCellRect(row, t.getColumnModel().getColumnIndex(Columns.RATE), false);
    return t.getToolTipText(new MouseEvent(t, MouseEvent.MOUSE_MOVED, 0L, 0, cell.x + cell.width / 2,
        cell.y + cell.height / 2, 0, false));
  }

  @Test
  void hoggerClawHasAVanillaRate() {
    assertThat(scalar("select count(*) from drop_rate_source where npc_id = 448 and item_id = 1931"
        + " and source = 'vmangos'")).isEqualTo(1L);
    // Hogger's page: Questie's 84 % headlines (no V); the tooltip lists VMaNGOS's quest-only 100 %; no boss line
    var hogger = (NpcPage) TestPages.build(new EntityRef(EntityKind.NPC, 448), repos, t -> {});
    JTable drops = hogger.drops().table();
    String[] claw = rateCell(drops, rowOf(drops, "Huge Gnoll Claw"));
    assertThat(claw[0]).isEqualTo("84.00 % ≠");
    assertThat(claw[1]).isEqualTo("Questie 84.00 %  ·  Vanilla rate (VMaNGOS) 100.00 % on quest");
  }

  @Test
  void aQuestOnlyVanillaHeadlineIsMarkedAndReadsOnQuest() {
    // Morgan the Collector (473) drops The Collector's Ring (2239) only on the quest; VMaNGOS alone rates it
    assertThat(rows("select drop_pct, pct_source, source_count from item_npc_drop where npc_id = 473"
        + " and item_id = 2239")).singleElement().satisfies(r -> {
          assertThat(((Number) r[0]).doubleValue()).isCloseTo(100.0, within(1e-9));
          assertThat(r[1]).isEqualTo("vmangos");
          assertThat(((Number) r[2]).intValue()).isEqualTo(1);
        });
    var morgan = (NpcPage) TestPages.build(new EntityRef(EntityKind.NPC, 473), repos, t -> {});
    JTable drops = morgan.drops().table();
    String[] ring = rateCell(drops, rowOf(drops, "The Collector's Ring"));
    assertThat(ring[0]).isEqualTo("100.00 % V");
    assertThat(ring[1]).isEqualTo("Vanilla rate (VMaNGOS) 100.00 % on quest");
  }

  @Test
  void worldBossVanillaRatesSayNothingOfForever() {
    // AtlasLoot's World Bosses list (26) is the only instance without a zone; every other one is a dungeon or raid
    assertThat(rows("select id, key from instance where zone_id is null")).singleElement()
        .satisfies(r -> assertThat(r[1]).isEqualTo("WorldBosses"));
    assertThat(scalar("select count(*) from instance i join zone z on z.id = i.zone_id"
        + " where z.kind not in ('dungeon', 'raid')")).isZero();
    // Azuregos, Lord Kazzak and the four Dragons of Nightmare roam open-world zones: no dungeon or raid bosses
    List<Long> worldBosses = List.of(6109L, 12397L, 14887L, 14888L, 14889L, 14890L);
    assertThat(scalar("select count(distinct b.npc_id) from boss b where b.instance_id = 26 and b.npc_id in"
        + " (6109, 12397, 14887, 14888, 14889, 14890)")).isEqualTo(6L);
    assertThat(repos.npcs().bosses(worldBosses)).isEmpty();
    assertThat(repos.npcs().bosses(List.of(639L, 6109L))).containsExactly(639L);
    // Azuregos's page: Vanilla rates, and none says Forever reworked the loot
    var azuregos = (NpcPage) TestPages.build(new EntityRef(EntityKind.NPC, 6109), repos, t -> {});
    assertVanillaWithoutBossNote(azuregos.drops().table());
    // the World Bosses page: Azuregos's encounter likewise
    var page = (InstancePage) TestPages.build(new EntityRef(EntityKind.INSTANCE, 26), repos, t -> {});
    for (int i = 0; i < page.encounters().getModel().getSize(); i++) {
      if (Long.valueOf(6109L).equals(page.encounters().getModel().getElementAt(i).npcId())) {
        page.selectBoss(i);
      }
    }
    assertVanillaWithoutBossNote(page.lootTable().table());
  }

  /** Some rate of {@code t} is a Vanilla rate, and no rate tooltip carries the boss note. */
  private static void assertVanillaWithoutBossNote(JTable t) {
    int vanilla = 0;
    for (int r = 0; r < t.getRowCount(); r++) {
      String tip = rateTooltip(t, r); // group headings render otherwise: the tooltip alone
      if (tip == null) {
        continue; // a heading, or a rate without sources
      }
      if (tip.contains("Vanilla rate (VMaNGOS)")) {
        vanilla++;
      }
      assertThat(tip).as("row %d", r).doesNotContain(RateSource.BOSS_NOTE);
    }
    assertThat(vanilla).isPositive();
  }

  @Test
  void dungeonBossVanillaRatesSayForeverLootIsReworked() {
    // Edwin VanCleef (639) is a Deadmines boss: VMaNGOS headlines An Unsent Letter (2874) at 100 %
    var vanCleef = (NpcPage) TestPages.build(new EntityRef(EntityKind.NPC, 639), repos, t -> {});
    JTable drops = vanCleef.drops().table();
    String[] letter = rateCell(drops, rowOf(drops, "An Unsent Letter"));
    assertThat(letter[0]).isEqualTo("100.00 % V ≠");
    assertThat(letter[1]).contains("Vanilla rate (VMaNGOS) 100.00 %").endsWith("\n" + RateSource.BOSS_NOTE);
    // the letter's item page: VanCleef's row says so too
    var item = (ItemPage) TestPages.build(new EntityRef(EntityKind.ITEM, 2874), repos, t -> {});
    JTable by = item.droppedBy().table();
    assertThat(rateCell(by, rowOf(by, "Edwin VanCleef"))[1]).endsWith("\n" + RateSource.BOSS_NOTE);
    // a world NPC's Vanilla rate has no such line: Morgan the Collector's ring
    var morgan = (NpcPage) TestPages.build(new EntityRef(EntityKind.NPC, 473), repos, t -> {});
    JTable ring = morgan.drops().table();
    assertThat(rateCell(ring, rowOf(ring, "The Collector's Ring"))[1]).doesNotContain(RateSource.BOSS_NOTE);
    // the instance page: VanCleef's encounter in The Deadmines
    var deadmines = (InstancePage) TestPages.build(new EntityRef(EntityKind.INSTANCE, 3), repos, t -> {});
    for (int i = 0; i < deadmines.encounters().getModel().getSize(); i++) {
      if (Long.valueOf(639L).equals(deadmines.encounters().getModel().getElementAt(i).npcId())) {
        deadmines.selectBoss(i);
      }
    }
    JTable loot = deadmines.lootTable().table();
    String[] bossLetter = rateCell(loot, rowOf(loot, "An Unsent Letter"));
    assertThat(bossLetter[0]).startsWith("100.00 % V");
    assertThat(bossLetter[1]).endsWith("\n" + RateSource.BOSS_NOTE);
  }
}
