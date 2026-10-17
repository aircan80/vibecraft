package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.wowforever.app.model.Craft;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
}
