package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.Route.Point;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class NpcRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void detailHasDropsWithPctAndSpawns() {
    NpcDetail d = repos.npcs().detail(639).get();
    assertThat(d.drops()).anyMatch(l -> l.item().id() == 2874 && l.dropPct() == 60.0);
    assertThat(repos.npcs().detail(448).get().spawns()).isNotEmpty();
  }

  @Test
  void byZoneListsSpawnZoneNpcs() {
    assertThat(repos.npcs().byZone(2437)).extracting(n -> n.id()).contains(11520L);
  }

  @Test
  void dropRowsCarryDisagreementAndSourceCount() {
    NpcDetail d = repos.npcs().detail(639).get();
    LootRow letter = d.drops().stream().filter(l -> l.item().id() == 2874).findFirst().orElseThrow();
    assertThat(letter.disagrees()).isTrue();
    assertThat(letter.sourceCount()).isEqualTo(4);
    assertThat(d.sells()).allSatisfy(l -> assertThat(l.disagrees()).isFalse());
  }

  @Test
  void routesOfTheDefiasMessenger() {
    List<Route> routes = repos.npcs().routes(550);
    assertThat(routes).singleElement().satisfies(r -> {
      assertThat(r.npcId()).isEqualTo(550);
      assertThat(r.zoneId()).isEqualTo(40);
      assertThat(r.route()).isEqualTo(1);
      assertThat(r.points()).hasSize(171);
      assertThat(r.points().subList(0, 2)).containsExactly(new Point(45.1, 69.33), new Point(45.59, 68.72));
      assertThat(r.points().getLast()).isEqualTo(new Point(44.48, 69.61));
      // Questie's route ends 0.68 map units from its start, inside the 2 % tolerance: an out-and-back loop
      assertThat(r.loop()).isTrue();
    });
    assertThat(repos.npcs().routes(448)).isEmpty();
  }

  @Test
  void routesAreInOrdinalOrderAndLoopOnlyWhenTheyEndWithin2PctOfTheirStart(@TempDir Path dir) throws Exception {
    try (SchemaDb schema = SchemaDb.create(dir)) {
      // inserted out of order; route 1 ends 1.9 from its start (a loop), route 2 ends 2.1 away (not a loop)
      waypoint(schema, 1, 12, 1, 3, 10, 11.9);
      waypoint(schema, 1, 12, 1, 1, 10, 10);
      waypoint(schema, 1, 12, 1, 2, 20, 10);
      waypoint(schema, 1, 12, 2, 2, 20, 10);
      waypoint(schema, 1, 12, 2, 1, 10, 10);
      waypoint(schema, 1, 12, 2, 3, 12.1, 10);
      // two points are a line, never a loop, even when they coincide
      waypoint(schema, 1, 40, 1, 1, 5, 5);
      waypoint(schema, 1, 40, 1, 2, 5, 5);
      waypoint(schema, 2, 12, 1, 1, 1, 1);
      try (Database db = schema.open()) {
        assertThat(new NpcRepository(db).routes(1)).containsExactly(
            new Route(1, 12, 1, List.of(new Point(10, 10), new Point(20, 10), new Point(10, 11.9)), true),
            new Route(1, 12, 2, List.of(new Point(10, 10), new Point(20, 10), new Point(12.1, 10)), false),
            new Route(1, 40, 1, List.of(new Point(5, 5), new Point(5, 5)), false));
      }
    }
  }

  @Test
  void routesAreEmptyOnADatabaseWithoutTheWaypointTable(@TempDir Path dir) throws Exception {
    try (SchemaDb schema = SchemaDb.create(dir)) {
      schema.sql("DROP TABLE npc_waypoint");
      try (Database db = schema.open()) {
        assertThat(new NpcRepository(db).routes(550)).isEmpty();
        assertThat(new ZoneRepository(db).routesInZone(40)).isEmpty();
      }
    }
  }

  /** Inserts one npc_waypoint row. */
  static void waypoint(SchemaDb schema, long npc, long zone, int route, int ordinal, double x, double y)
      throws Exception {
    schema.sql("INSERT INTO npc_waypoint (npc_id, zone_id, route, ordinal, x, y) VALUES (?, ?, ?, ?, ?, ?)",
        npc, zone, route, ordinal, x, y);
  }
}
