package dev.wowforever.app.db;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class SearchRankingTest {
  private final Repositories repos = FixtureDb.repositories();

  @TempDir
  Path tmp;

  /** A copy of the fixture database with {@code statements} applied, searched for {@code text}. */
  private List<SearchHit> searchCopy(String text, int limit, String... statements) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      for (String sql : statements) {
        s.executeUpdate(sql);
      }
    }
    try (Database db = Database.open(copy)) {
      return new SearchRepository(db).search(text, Optional.empty(), limit);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String index(String kind, long id, String name) {
    return "insert into search_index(kind, id, name) values ('" + kind + "', " + id + ", '" + name + "')";
  }

  @Test
  void exactNameMatchRanksFirst() throws Exception {
    // an item outranks a plain NPC by kind, but the NPC's name is the exact search text
    List<SearchHit> hits = searchCopy("hogger", 10,
        index("item", 90001, "Hogger Tusk"),
        "insert into item(id, name, item_level, class_id, subclass_id) values (90001, 'Hogger Tusk', 12, 12, 0)");
    assertThat(hits).extracting(SearchHit::ref)
        .containsExactly(new EntityRef(NPC, 448), new EntityRef(ITEM, 90001));
  }

  @Test
  void exactMatchIgnoresCaseAndSpacing() {
    List<SearchHit> hits = repos.search().search("  RAGEFIRE   chasm ", Optional.empty(), 10);
    assertThat(hits).first().satisfies(h -> assertThat(h.ref()).isEqualTo(new EntityRef(INSTANCE, 1)));
  }

  @Test
  void kindPriorityOrder() throws Exception {
    List<SearchHit> hits = searchCopy("gloomvale", 20,
        index("object", 91001, "Gloomvale Signpost"),
        "insert into game_object(id, name, zone_id) values (91001, 'Gloomvale Signpost', 40)",
        index("npc", 91002, "Gloomvale Peasant"),
        "insert into npc(id, name, min_level, max_level, rank, zone_id) values (91002, 'Gloomvale Peasant', 5, 6, 0, 40)",
        index("quest", 91003, "Gloomvale Trouble"),
        "insert into quest(id, name, quest_level, zone_or_sort) values (91003, 'Gloomvale Trouble', 10, 40)",
        index("item", 91004, "Gloomvale Cloak"),
        "insert into item(id, name, item_level, required_level, class_id, subclass_id) values (91004, 'Gloomvale Cloak', 20, 15, 4, 1)",
        index("npc", 91005, "Gloomvale Tyrant"),
        "insert into npc(id, name, min_level, max_level, rank, zone_id) values (91005, 'Gloomvale Tyrant', 30, 30, 4, 40)",
        index("npc", 91008, "Gloomvale Overseer"),
        "insert into npc(id, name, min_level, max_level, rank, zone_id) values (91008, 'Gloomvale Overseer', 30, 30, 1, 40)",
        "insert into boss(id, instance_id, name, npc_id, is_extra_list, sort_order) values (91009, 91007, 'Overseer', 91008, 0, 1)",
        index("zone", 91006, "Gloomvale Hollow"),
        "insert into zone(id, name, parent_id, kind) values (91006, 'Gloomvale Hollow', 40, 'zone')",
        index("instance", 91007, "Gloomvale Crypt"),
        "insert into zone(id, name, parent_id, kind) values (91100, 'Crypt Depths', 40, 'dungeon')",
        "insert into instance(id, key, name, content_type, level_min, level_max, zone_id) "
            + "values (91007, 'gloomvale_crypt', 'Gloomvale Crypt', 'DUNGEON', 20, 25, 91100)");
    assertThat(hits).extracting(SearchHit::ref).containsExactly(
        new EntityRef(INSTANCE, 91007),
        new EntityRef(ZONE, 91006),
        new EntityRef(NPC, 91008), // boss (in the boss table) and rare (rank 4) share a priority: by name
        new EntityRef(NPC, 91005),
        new EntityRef(ITEM, 91004),
        new EntityRef(QUEST, 91003),
        new EntityRef(NPC, 91002),
        new EntityRef(OBJECT, 91001));
    assertThat(hits).extracting(SearchHit::subtitle).containsExactly(
        "Dungeon · Level 20–25 · Crypt Depths",
        "Zone · Westfall",
        "NPC · Elite · Level 30 · Westfall",
        "NPC · Rare · Level 30 · Westfall",
        "Cloth · iLvl 20 · Req 15",
        "Quest · Level 10 · Westfall",
        "NPC · Level 5–6 · Westfall",
        "Object · Westfall");
  }

  @Test
  void raidZoneCollapsesIntoItsInstance() {
    List<SearchHit> hits = repos.search().search("molten core", Optional.empty(), 10);
    assertThat(hits).singleElement().satisfies(h -> {
      assertThat(h.ref()).isEqualTo(new EntityRef(INSTANCE, 3));
      assertThat(h.name()).isEqualTo("Molten Core");
      assertThat(h.subtitle()).isEqualTo("Raid (40) · Burning Steppes");
      assertThat(h.count()).isEqualTo(1);
    });
    // zone 1581 is The Deadmines' zone and merges into it; zone 10029 has no instance and stays
    assertThat(repos.search().search("deadmines", Optional.empty(), 10)).extracting(SearchHit::ref)
        .containsExactly(new EntityRef(INSTANCE, 2), new EntityRef(ZONE, 10029));
  }

  @Test
  void zoneOfOneInstanceBecomesThatInstanceEvenWhenOnlyTheZoneMatches() throws Exception {
    List<SearchHit> hits = searchCopy("emberforge", 10,
        index("zone", 92000, "Emberforge"),
        "insert into zone(id, name, parent_id, kind) values (92000, 'Emberforge', 46, 'raid')",
        "insert into instance(id, key, name, content_type, level_min, level_max, zone_id) "
            + "values (92001, 'cinder_vault', 'Cinder Vault', 'RAID20', 60, 60, 92000)");
    assertThat(hits).singleElement().satisfies(h -> {
      assertThat(h.ref()).isEqualTo(new EntityRef(INSTANCE, 92001));
      assertThat(h.name()).isEqualTo("Cinder Vault");
      assertThat(h.subtitle()).isEqualTo("Raid (20) · Level 60 · Emberforge");
    });
  }

  @Test
  void zoneWithTwoInstancesStaysAZone() throws Exception {
    List<SearchHit> hits = searchCopy("ashpeak mountain", 10,
        index("zone", 93000, "Ashpeak Mountain"),
        "insert into zone(id, name, parent_id, kind) values (93000, 'Ashpeak Mountain', 46, 'zone')",
        index("instance", 93001, "Ashpeak Depths"),
        "insert into instance(id, key, name, content_type, zone_id) values (93001, 'ad', 'Ashpeak Depths', 'DUNGEON', 93000)",
        index("instance", 93002, "Ashpeak Spire"),
        "insert into instance(id, key, name, content_type, zone_id) values (93002, 'as', 'Ashpeak Spire', 'DUNGEON', 93000)");
    assertThat(hits).singleElement().satisfies(h -> {
      assertThat(h.ref()).isEqualTo(new EntityRef(ZONE, 93000));
      assertThat(h.subtitle()).isEqualTo("Zone · Burning Steppes");
    });
  }

  @Test
  void zoneFilterKeepsZoneHits() {
    assertThat(repos.search().search("molten core", Optional.of(ZONE), 10)).extracting(SearchHit::ref)
        .containsExactly(new EntityRef(ZONE, 2717));
  }

  @Test
  void sameNameSameKindCollapse() throws Exception {
    List<SearchHit> hits = searchCopy("westfall", 10,
        index("object", 94001, "Westfall"), index("object", 94002, "Westfall"),
        index("object", 94003, "Westfall"), index("object", 94004, "Westfall"),
        "insert into game_object(id, name, zone_id) values (94001, 'Westfall', 40), (94002, 'Westfall', 40), "
            + "(94003, 'Westfall', 12), (94004, 'Westfall', 12)");
    assertThat(hits).first().satisfies(h -> assertThat(h.ref()).isEqualTo(new EntityRef(ZONE, 40)));
    assertThat(hits).filteredOn(h -> h.ref().kind() == OBJECT).singleElement().satisfies(h -> {
      assertThat(h.ref()).isEqualTo(new EntityRef(OBJECT, 94001));
      assertThat(h.count()).isEqualTo(4);
      assertThat(h.subtitle()).isEqualTo("Object · Westfall");
    });
    assertThat(hits.get(0).count()).isEqualTo(1);
  }

  /** The four Scarlet Monastery wings: separate instances in the fixture's Scarlet Monastery zone, sharing a name. */
  private static String[] scarletMonasteryWings() {
    return new String[] {
        "update zone set parent_id = 85, kind = 'dungeon' where id = 796",
        "insert into instance(id, key, name, content_type, level_min, level_max, zone_id) values "
            + "(95001, 'ScarletMonasteryGraveyard', 'Scarlet Monastery', 'DUNGEON', 26, 36, 796), "
            + "(95002, 'ScarletMonasteryLibrary', 'Scarlet Monastery', 'DUNGEON', 29, 39, 796), "
            + "(95003, 'ScarletMonasteryArmory', 'Scarlet Monastery', 'DUNGEON', 32, 42, 796), "
            + "(95004, 'ScarletMonasteryCathedral', 'Scarlet Monastery', 'DUNGEON', 35, 45, 796)",
        index("instance", 95001, "Scarlet Monastery"), index("instance", 95002, "Scarlet Monastery"),
        index("instance", 95003, "Scarlet Monastery"), index("instance", 95004, "Scarlet Monastery")};
  }

  @Test
  void sameNamedInstancesStaySeparateHitsNamedByWing() throws Exception {
    List<SearchHit> hits = searchCopy("scarlet monastery", 10, scarletMonasteryWings());
    // the zone has four instances, so it stays a zone hit after them
    assertThat(hits).extracting(SearchHit::ref).startsWith(
        new EntityRef(INSTANCE, 95001), new EntityRef(INSTANCE, 95002), new EntityRef(INSTANCE, 95003),
        new EntityRef(INSTANCE, 95004), new EntityRef(ZONE, 796));
    assertThat(hits).extracting(SearchHit::displayName).startsWith(
        "Scarlet Monastery › Graveyard", "Scarlet Monastery › Library", "Scarlet Monastery › Armory",
        "Scarlet Monastery › Cathedral", "Scarlet Monastery");
    assertThat(hits).allSatisfy(h -> assertThat(h.count()).isEqualTo(1));
    assertThat(hits.get(1).subtitle()).isEqualTo("Dungeon · Level 29–39 · Tirisfal Glades");
  }

  @Test
  void sameNamedZonesStaySeparateHits() throws Exception {
    List<SearchHit> hits = searchCopy("gloomhold", 10,
        index("zone", 96001, "Gloomhold"), index("zone", 96002, "Gloomhold"),
        "insert into zone(id, name, parent_id, kind) values (96001, 'Gloomhold', 40, 'subzone'), "
            + "(96002, 'Gloomhold', 12, 'subzone')");
    assertThat(hits).extracting(SearchHit::ref)
        .containsExactly(new EntityRef(ZONE, 96001), new EntityRef(ZONE, 96002));
    assertThat(hits).extracting(SearchHit::subtitle).containsExactly("Sub-zone · Westfall", "Sub-zone · Elwynn Forest");
  }

  @Test
  void limitAppliesAfterCollapsing() throws Exception {
    List<SearchHit> hits = searchCopy("westfall", 2,
        index("object", 94001, "Westfall"), index("object", 94002, "Westfall"),
        index("object", 94003, "Westfall"));
    assertThat(hits).extracting(SearchHit::ref)
        .containsExactly(new EntityRef(ZONE, 40), new EntityRef(OBJECT, 94001));
    assertThat(hits.get(1).count()).isEqualTo(3);
  }
}
