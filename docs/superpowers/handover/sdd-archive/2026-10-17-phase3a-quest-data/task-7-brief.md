### Task 7: NPC patrol routes (owner request, 2026-10-17)

Owner's words: "for npcs that patrol/move (like the defias messenger) can we mark on the map their spawn point (we
already do) and the route they take? (and if they disappear where that happens, like a despawn point)". Questie's
`npcKeys.waypoints` (index 8) holds `{[zoneId] = {{{x,y},...}, ...}}` per NPC (e.g. Defias Messenger 550 in Westfall);
Questie records no despawn points, so the UI will mark the route's END as "route ends here" when it is not a loop
(last point farther than 2 % from the first).

**Files:**
- Modify: `schema.sql` (+ `npc_waypoint(npc_id INTEGER NOT NULL, zone_id INTEGER NOT NULL, route INTEGER NOT NULL,
  ordinal INTEGER NOT NULL, x REAL, y REAL, PRIMARY KEY (npc_id, zone_id, route, ordinal))`), `EntityWriter`
  (writes every waypoint list; counter `npc.waypoints.npcs`, `npc.waypoints.points`), the NPC fixture (+550 with its
  route), `app/.../db/NpcRepository.routes(npcId) -> List<Route(zoneId, List<Point>, boolean loop)>` and
  `ZoneRepository.routesInZone(zoneId)`.
- Test: `EntityWriterTest.waypointsAreWrittenInOrder`, `NpcRepositoryTest.routesOfTheDefiasMessenger` (ordinal order,
  loop detection false), `ShippedDataTest` (550 has ≥ 30 points in zone 40; `npc.waypoints.npcs` reported).
- [ ] Tests red → green; commit `feat(importer): NPC patrol waypoints`.

