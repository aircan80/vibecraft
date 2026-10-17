# Task 7 report: NPC patrol waypoints

Status: DONE. Commit `9efed5d` (`feat(importer): NPC patrol waypoints`) on `feature/phase3a-quest-data`.
Full `./mvnw -B -q verify` is green. The shipped database was NOT regenerated and ShippedDataTest was NOT changed,
per the controller ruling (Task 10 does both).

## What was built

- **Schema.** `npc_waypoint(npc_id, zone_id, route, ordinal, x, y, PRIMARY KEY (npc_id, zone_id, route, ordinal))`
  exactly as the brief, plus `idx_npc_waypoint_zone` for the zone reader. SCHEMA_VERSION stays 3.
- **Importer.** `RawEntity.waypoints(id, batch)` walks `{[zoneId] = { {{x,y},...}, ... }}`. `route` is the route's
  1-based array index in the zone, `ordinal` the point's 1-based array index. A malformed zone (non-integer key or
  non-table value), route (non-table) or point (not `{number, number}`) counts `badValue.npc.waypoints` and is
  skipped; nothing throws. Nil holes are skipped silently, so an index can be skipped but never reused.
  `EntityWriter.npcs` writes the rows and counts `npc.waypoints.npcs` (NPCs with at least one point) and
  `npc.waypoints.points`. Both counters are registered with 0 up front, so they always appear in the report.
- **UiFields.** `waypoints` is now an NPC UI field: plan 3B draws the routes, so losing them must trip the strict gate.
- **App.** New `app/.../model/Route.java`: `Route(long npcId, long zoneId, int route, List<Point> points, boolean loop)`
  with a nested `Route.Point(double x, double y)`. I did not reuse Task 6's `QuestObjective.Point(zoneId, x, y)`:
  it is nested in QuestObjective and carries a zoneId that Route already holds. `Route.isLoop` means at least three
  points and the last point within `LOOP_TOLERANCE` = 2.0 map units (Euclidean, 0 to 100 coordinates) of the first.
  One or two points are a line, never a loop.
  `NpcRepository.routes(npcId)` and `ZoneRepository.routesInZone(zoneId)` share one package-private reader
  that orders rows by npc_id, zone_id, route, ordinal and groups consecutive rows into routes. Both return an empty
  list when the database has no `npc_waypoint` table. The check is the new helper `Sql.hasTable` (a sqlite_master
  lookup).

## Files

- `importer/src/main/resources/dev/wowforever/importer/schema.sql`
- `importer/src/main/java/dev/wowforever/importer/sqlite/EntityWriter.java`
- `importer/src/main/java/dev/wowforever/importer/sqlite/RawEntity.java`
- `importer/src/main/java/dev/wowforever/importer/report/UiFields.java`
- `importer/src/test/resources/fixtures/questiedb/data/Forever/foreverNpcDB.lua` (+ NPC 550)
- `importer/src/test/java/dev/wowforever/importer/sqlite/EntityWriterTest.java` (+ waypointsAreWrittenInOrder)
- `importer/src/test/java/dev/wowforever/importer/{MainTest,corrections/CorrectionApplierTest,entity/RawEntityLoaderTest,report/ImportReportTest}.java`
  (adjusted, see below)
- `app/src/main/java/dev/wowforever/app/model/Route.java` (new)
- `app/src/main/java/dev/wowforever/app/db/{NpcRepository,ZoneRepository,Sql}.java`
- `app/src/test/java/dev/wowforever/app/db/{NpcRepositoryTest,ZoneRepositoryTest}.java`

## NPC 550 Defias Messenger

The row was copied byte for byte from line 363 of the pinned `data/Forever/foreverNpcDB.lua` (checked with diff).
No Forever NPC correction file at the pin (foreverNPCFixes, legacy/classicNPCFixes, generated/foreverBaseNpc,
traces/foreverNpcTraces) has an entry for NPC 550. The only `[550]` hit is quest 550 in
classicQuestReputationFixes, so the fixture correction files needed no change.

| Fact | Value |
|---|---|
| Zone | 40 (Westfall) |
| Routes | 1 |
| Points | 171 (ordinals 1 to 171) |
| First point | 45.1, 69.33 (also its spawn) |
| Last point | 44.48, 69.61 |
| Distance last to first | 0.68 map units |

## Real-data check (scratch import, not shipped)

I ran `importer.jar --fail-on-unsupported` with client data into the scratchpad. It exited 0, with no skips and
no `badValue`/`unresolved`/`missingField` counters, so making waypoints a UI field does not break the Task 10 run.

| Counter | Value |
|---|---|
| npc.waypoints.npcs | 479 |
| npc.waypoints.points | 23015 |
| zones with routes | 47 |
| routes | 564 |
| routes that are loops by the 2 % rule | 453 |
| most routes for one NPC in one zone | 8 |
| routes with ordinal gaps | 0 |

For Task 10: 550 has 171 points in zone 40, so the planned "at least 30 points in zone 40" assertion holds.

## TDD evidence

- `EntityWriterTest.waypointsAreWrittenInOrder` was red first (`no such table: npc_waypoint`), then green. It covers
  NPC 550 verbatim (171 points in order, first, second and last coordinates) and a synthetic Hogger value with two
  routes in zone 12 and one in zone 40. It also covers Gobbler with one good point and four malformed parts
  (a string point, a point without y, a non-table route, a non-table zone value). The asserted counters are
  npcs 3, points 176 and badValue 4.
- The app tests were red first (compile: no `Route`), then green.
  - `NpcRepositoryTest.routesOfTheDefiasMessenger` checks 171 points in ordinal order, the first, second and last
    points, and that it is a loop. It also checks that Hogger has no routes in the fixture.
  - `NpcRepositoryTest.routesAreInOrdinalOrderAndLoopOnlyWhenTheyEndWithin2PctOfTheirStart` uses a schema-only
    database with rows inserted out of order. It covers the loop boundaries at 1.9 (a loop) and 2.1 (not a loop),
    and a two-point route that is never a loop.
  - `NpcRepositoryTest.routesAreEmptyOnADatabaseWithoutTheWaypointTable` drops `npc_waypoint` from a schema-only
    database. Both `routes` and `routesInZone` then return an empty list.
  - `ZoneRepositoryTest.routesInZoneHoldTheDefiasMessengersPatrol` uses the fixture: zone 40 holds 550's route and
    zone 12 has none.
  - `ZoneRepositoryTest.routesInZoneAreEveryNpcsRoutesByNpcRouteAndOrdinal` uses a schema-only database. It checks
    the ordering across NPCs and that another zone's route is left out.

## Verify per module (`./mvnw -B -q verify`, exit 0)

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 365 | 0 | 0 | 0 |
| app | 250 | 0 | 0 | 0 |

ShippedDataTest ran 30 tests and all passed against the committed database, which has no `npc_waypoint` table.

## Concerns

1. **Brief says 550's loop is false, the rule says true.** The brief expects "loop detection false" for 550.
   The rule I was given is "last point within 2 % of the first". 550's route ends 0.68 units from its start: it
   walks out from Moonbrook and comes back. By that rule it is a loop. I implemented the rule as given and assert
   `loop == true` for 550. The non-loop case is tested on synthetic data instead. If the owner wants 550 to show
   "route ends here", the rule must change, for example by comparing the last point with the farthest point or by
   using a tighter tolerance. On real data 453 of 564 routes count as loops, so the end marker will be fairly rare.
2. **Four existing importer tests changed** because they used `waypoints` as the example of a non-UI field.
   ImportReportTest and MainTest now use `minLevelHealth` for that, and ImportReportTest also asserts that waypoints
   is a UI field. CorrectionApplierTest's unsupported `waypoints_add` skip now counts as one UI skip, which is the
   intended stricter behaviour. RawEntityLoaderTest's NPC id list gains 550.
3. **Route numbering follows the Lua array index.** A nil hole in Questie's route list would skip a route number.
   None occur at the pin (0 gaps).
4. **Object waypoints.** objectMeta also has a `waypoints` field. It is out of scope and not imported.
