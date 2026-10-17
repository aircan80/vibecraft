### Task 12: Zone maps with spawn pins

**Files:** `ui/widgets/MapView.java` (`JComponent` painting a cached `BufferedImage` scaled to width with aspect; pins at `x%·w, y%·h`; hover → name tooltip; click → `nav.open`; pins within 8 px cluster into a count badge; `setPins(List<Pin(EntityRef, name, x, y, kind)>)`, `setFilter(bossesAndRaresOnly)`), `ZonePage` top area shows the map with Bosses and Rares pins by default + "All NPCs" toggle + "Objects" toggle; `NpcPage` Spawns section shows the map with that NPC's pins; `ZoneRepository.map(zoneId)` (from 2A) + spawn queries (`npcSpawnsInZone(zoneId, bossesOnly)`, `objectSpawnsInZone`).
**Tests:** `MapViewTest` (headless: pin hit-testing, clustering threshold, scaling math), `ZonePageTest.mapShownWithPins`, `NpcPageTest.spawnsOnMap`, repository spawn-query tests. Controller visual check: Hogger's pins on Elwynn, Sentinel Hill flight master on Westfall (alignment acceptance from spec ruling 13).
- [ ] Commit `feat(ui): zone maps with spawn pins`.

