package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.Route;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Shipped-data checks for the phase 3A quest data (schema v3): objectives, relations, the rail icons and the NPC
 * patrol routes. Skip them with {@code -DexcludedGroups=shipped-data}.
 */
@Tag("shipped-data")
class ShippedQuestDataTest extends ShippedDataSupport {
  @Test
  void schemaVersionHasTheQuestTables() {
    // v3 brought the quest tables; ShippedProvenanceDataTest pins the current version
    assertThat(Integer.parseInt(db.meta().get("schema_version"))).isGreaterThanOrEqualTo(3);
  }

  @Test
  void questTableMinimumCounts() {
    assertThat(count("quest_objective")).isGreaterThan(5_000);
    assertThat(count("quest_objective_point")).isGreaterThan(100);
    assertThat(count("quest_source_item")).isGreaterThan(1_000);
    assertThat(count("quest_relation")).isGreaterThan(7_000);
    assertThat(column("select distinct kind from quest_objective"))
        .containsExactlyInAnyOrder("creature", "object", "item", "reputation", "spell", "explore");
    assertThat(column("select distinct kind from quest_source_item")).containsExactlyInAnyOrder("provided", "required");
    assertThat(column("select distinct kind from quest_relation")).containsExactlyInAnyOrder("pre_single", "pre_group",
        "next", "child", "parent", "exclusive", "group", "breadcrumb_for", "breadcrumb");
  }

  @Test
  void questObjectivesNameKnownEntities() {
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'item'"
        + " and not exists (select 1 from item i where i.id = o.entity_id)")).isZero();
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'creature'"
        + " and not exists (select 1 from npc n where n.id = o.entity_id)")).isZero();
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'object'"
        + " and not exists (select 1 from game_object g where g.id = o.entity_id)")).isZero();
    // every point belongs to an explore objective
    assertThat(scalar("select count(*) from quest_objective_point p where not exists (select 1 from quest_objective o"
        + " where o.quest_id = p.quest_id and o.ordinal = p.ordinal and o.kind = 'explore')")).isZero();
  }

  @Test
  void wantedHogger() {
    // Questie records no creature objective for Hogger: the one objective is the Huge Gnoll Claw
    assertThat(rows("select ordinal, kind, entity_id from quest_objective where quest_id = 176"))
        .singleElement().satisfies(r -> {
          assertThat(((Number) r[0]).intValue()).isZero();
          assertThat(r[1]).isEqualTo("item");
          assertThat(((Number) r[2]).longValue()).isEqualTo(1931L);
        });
    assertThat(column("select name from item where id = 1931")).containsExactly("Huge Gnoll Claw");
    assertThat(column("select kind || ' ' || entity_id || ' ' || role from quest_giver where quest_id = 176"))
        .containsExactlyInAnyOrder("object 68 start", "object 156561 start", "npc 240 end");
    assertThat(column("select name from game_object where id in (68, 156561)")).containsOnly("Wanted Poster");
    assertThat(column("select name from npc where id = 240")).containsExactly("Marshal Dughan");
  }

  @Test
  void riseThunderfuryNeedsTheVesselChain() {
    // 7787 Rise, Thunderfury! <- 7786 Thunderaan the Windseeker <- 7785 Examine the Vessel
    assertThat(longs("select related_id from quest_relation where quest_id = 7787 and kind = 'pre_single'"))
        .containsExactly(7786L);
    assertThat(longs("select related_id from quest_relation where quest_id = 7786 and kind = 'pre_single'"))
        .containsExactly(7785L);
    assertThat(prerequisiteClosure(7787)).containsExactlyInAnyOrder(7786L, 7785L);
  }

  @Test
  void drakefireAmuletChain() {
    // 6502 Drakefire Amulet: one prerequisite (6501), whose pre chain walks back 15 quests to 4182 Dragonkin Menace
    assertThat(column("select related_id || ' ' || kind from quest_relation where quest_id = 6502"))
        .containsExactly("6501 pre_single");
    List<Long> chain = prerequisiteClosure(6502);
    assertThat(chain).hasSize(15).contains(6501L, 6403L, 4322L, 4182L);
    String members = chain.stream().map(String::valueOf).reduce("6502", (a, b) -> a + ", " + b);
    assertThat(scalar("select count(*) from quest_relation where quest_id in (" + members + ")"))
        .isGreaterThanOrEqualTo(10);
    // the relation graph does not join it to the Horde chain's 6602 Blood of the Black Dragon Champion; the two
    // share their one objective, the item Blood of the Black Dragon Champion (16663)
    assertThat(column("select kind || ' ' || entity_id from quest_objective where quest_id in (6502, 6602)"))
        .containsExactly("item 16663", "item 16663");
  }

  @Test
  void negativePreQuestGroupIdsAreStoredAbsolute() {
    // 4964's corrected preQuestGroup is {4976, -4962}
    assertThat(longs("select related_id from quest_relation where quest_id = 4964 and kind = 'pre_group'"))
        .containsExactlyInAnyOrder(4976L, 4962L);
    assertThat(scalar("select count(*) from quest_relation where related_id <= 0")).isZero();
  }

  @Test
  void repeatableFollowsTheSpecialFlag() {
    assertThat(scalar("select count(*) from quest where repeatable <> (coalesce(special_flags, 0) & 1)")).isZero();
    assertThat(scalar("select count(*) from quest where repeatable = 1")).isGreaterThan(500);
    assertThat(scalar("select repeatable from quest where id = 16")).as("Give Gerard a Drink").isEqualTo(1);
    assertThat(scalar("select repeatable from quest where id = 176")).as("Wanted: Hogger").isZero();
  }

  @Test
  void questSortLabels() {
    assertThat(scalar("select count(*) from quest where sort_label is not null")).isGreaterThan(1_000);
    assertThat(scalar("select count(*) from quest where sort_label is not null and zone_or_sort >= 0")).isZero();
    assertThat(column("select distinct sort_label from quest"))
        .contains("Warlock", "Camping", "Darkmoon Faire", "Lunar Festival", "Blacksmithing", "Legendary");
  }

  @Test
  void committedImportReportShowsTheQuestCounters() throws Exception {
    String report = committedReport();
    // 4604's preQuestSingle 2955 is not a quest of the database; the row is kept
    assertThat(reportCounter(report, "quest.relation.missing")).isEqualTo(1);
    assertThat(scalar("select count(*) from quest_relation r where not exists (select 1 from quest q"
        + " where q.id = r.related_id)")).isEqualTo(1);
    assertThat(column("select quest_id || ' ' || related_id || ' ' || kind from quest_relation r"
        + " where not exists (select 1 from quest q where q.id = r.related_id)")).containsExactly("4604 2955 pre_single");
    long kinds = 0;
    for (Object kind : column("select distinct kind from quest_objective")) {
      long n = reportCounter(report, "quest.objective.kinds." + kind);
      assertThat(n).as("quest.objective.kinds.%s", kind)
          .isEqualTo(scalar("select count(*) from quest_objective where kind = '" + kind + "'"));
      kinds += n;
    }
    assertThat(kinds).isEqualTo(count("quest_objective"));
    assertThat(reportCounter(report, "quest.objective.unknownKind")).isZero();
    assertThat(reportCounter(report, "quest.sourceItems")).isEqualTo(count("quest_source_item"));
    assertThat(reportCounter(report, "clientdata.uiAssets")).isEqualTo(6);
  }

  @Test
  void railIconsAreEmbedded() throws Exception {
    List<String> names = new ArrayList<>();
    for (String icon : List.of("home", "items", "npcs", "quests", "zones", "instances")) {
      names.add("rail." + icon);
      names.add("rail." + icon + "@24");
    }
    assertThat(column("select name from ui_asset")).containsExactlyInAnyOrderElementsOf(names);
    for (String name : names) {
      byte[] png;
      synchronized (db) {
        try (var ps = db.connection().prepareStatement("select png from ui_asset where name = ?")) {
          ps.setString(1, name);
          try (ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).as(name).isTrue();
            png = rs.getBytes(1);
          }
        }
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image).as(name).isNotNull();
      if (name.endsWith("@24")) {
        assertThat(image.getWidth() + "x" + image.getHeight()).as(name).isEqualTo("24x24");
      }
    }
  }

  // --- NPC patrol routes (phase 3A Task 7) -----------------------------------------------------------------------

  @Test
  void defiasMessengerPatrolsWestfall() {
    // 550 Defias Messenger: one route of 171 points in Westfall (40); it walks out from Moonbrook and back, so it
    // ends within the loop tolerance of its start
    assertThat(scalar("select count(*) from npc_waypoint where npc_id = 550 and zone_id = 40"))
        .isGreaterThanOrEqualTo(30);
    assertThat(repos.npcs().routes(550)).singleElement().satisfies(r -> {
      assertThat(r.zoneId()).isEqualTo(40L);
      assertThat(r.points()).hasSizeGreaterThanOrEqualTo(30);
      assertThat(r.loop()).isTrue();
    });
  }

  @Test
  void stitchesWalksAnOpenRoute() {
    // 412 Stitches is the lowest NPC id with a route that is not a loop: 161 points through Duskwood (10), ending
    // about 48 map units from where it starts
    assertThat(column("select name from npc where id = 412")).containsExactly("Stitches");
    assertThat(repos.npcs().routes(412)).singleElement().satisfies(r -> {
      assertThat(r.zoneId()).isEqualTo(10L);
      assertThat(r.points()).hasSizeGreaterThanOrEqualTo(100);
      assertThat(r.loop()).isFalse();
    });
    List<Route> duskwood = repos.zones().routesInZone(10);
    assertThat(duskwood).anySatisfy(r -> assertThat(r.npcId()).isEqualTo(412L));
    assertThat(duskwood).filteredOn(Route::loop).isNotEmpty();
  }

  @Test
  void committedImportReportShowsTheWaypointCounters() throws Exception {
    String report = committedReport();
    assertThat(reportCounter(report, "npc.waypoints.npcs"))
        .isEqualTo(scalar("select count(distinct npc_id) from npc_waypoint")).isGreaterThan(400);
    assertThat(reportCounter(report, "npc.waypoints.points")).isEqualTo(count("npc_waypoint"));
    assertThat(scalar("select count(*) from npc_waypoint w"
        + " where not exists (select 1 from npc n where n.id = w.npc_id)")).isZero();
  }

  /** The quests reachable from {@code quest} over pre_single and pre_group rows, the quest itself excluded. */
  private static List<Long> prerequisiteClosure(long quest) {
    return longs("with recursive c(id) as (select " + quest + " union select r.related_id from quest_relation r"
        + " join c on r.quest_id = c.id where r.kind in ('pre_single', 'pre_group')) select id from c where id <> "
        + quest);
  }
}
