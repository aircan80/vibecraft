# Task 12 report: zone maps with spawn pins

Status: DONE_WITH_CONCERNS (the concerns are small and listed at the end)
Commit: `ccb9a33 feat(ui): zone maps with spawn pins` on `feature/phase2b-ui`

## What I implemented

- **`model/SpawnPin(EntityRef ref, String name, Integer rank, double x, double y)`**. This is one spawn point to pin. `rank` is null for objects.
- **`ZoneRepository.npcSpawnsInZone(zoneId, bossesAndRaresOnly)`** and **`objectSpawnsInZone(zoneId)`**. Both use parameterised SQL. They join the npc or game_object name, and the NPC rank. They keep only points with 0 <= x, y <= 100, which drops the (-1, -1) instance placeholders. Results come in import order, capped at `PIN_CAP` (20,000). The boss filter is `(? = 0 OR n.rank IN (2, 3, 4))`.
- **`ui/widgets/MapView`** (a JComponent) replaces `MapImagePanel`. Both `MapImagePanel` and its test were removed, and the test cases moved into `MapViewTest`. It has these nested types:
  - `PinKind`: SELF, BOSS, RARE, OBJECT, NPC, in that priority order.
  - `Pin(ref, name, x, y, kind)`, with `Pin.of(SpawnPin, self)`. Rank 3 maps to BOSS, ranks 2 and 4 to RARE, an object to OBJECT, and anything else to NPC.
  - `Projection(imageW, imageH, compW)`, with `drawnWidth/Height` and `toComponent(x%, y%)`. The image is drawn from the top-left, scaled to the width, and never upscaled.
  - `Cluster(pins, position)`, with `count`, `kind` and `entities`.
- **How `MapView` behaves**:
  - **Clustering**: greedy, on a grid. Pins are taken in priority order. Each pin joins the first marker within `CLUSTER_PX` = 8 logical px (`UIScale`, inclusive). The marker sits at its highest-priority member and is drawn in that member's colour, with a count badge when it holds more than one pin.
  - **Hit testing**: `HIT_PX` = 8, nearest marker wins. It uses the same projection as painting. Clusters are cached per width, pin list and filter.
  - **Tooltip**: `"Hogger"`, or `"Hogger (5)"` for five pins of one entity. A marker of several entities gets an HTML list `"<n> pins<br>…"` with names escaped, up to 25 names.
  - **Click**: a marker of one entity calls `nav.open`. A marker of several entities shows `menuFor(cluster)`, a popup with one item per entity. The menu only appears when the map is on screen.
  - **Colours**: BOSS is gold, RARE silver, NPC muted, OBJECT the link accent. SELF is gold and drawn larger. Every pin has a 1 px dark outline, and a hovered marker gets a white ring and a hand cursor.
  - **Filter**: `setFilter(bossesAndRaresOnly)` hides NPC-kind pins only.
- **`ZonePage`**: the map area replaces the reserved placeholder, between the link rows and the sections.
  - **Toggles**: "Bosses & rares" (the default) and "All NPCs" are `JToggleButton`s in a `ButtonGroup`. "Objects" is an independent `JToggleButton` that adds object pins.
  - **Default pins**: the bosses and rares from `LoadedZone`.
  - **Loading**: "All NPCs" and "Objects" load through `Async.run(worker, …)`, guarded by a generation counter. Each load fetches everything that is still missing at that moment, and a superseded load is dropped.
  - **Switching back**: going back to "Bosses & rares" only sets the map filter. It does not reload.
  - **Seam**: a new `ZonePage.PinSource` interface (`npcs`, `objects`, and `PinSource.of(ZoneRepository)`) is used together with an `Executor`. `DetailPanels.render` passes `Async.SWING_WORKER`.
  - **No map**: no map area and no toggles. The two-argument constructor builds this case.
  - **Compact**: a hierarchy listener hides the toggle row and shows a "Pins ▾" button. Its popup holds a `JRadioButtonMenuItem` ×2 and a `JCheckBoxMenuItem` that share the toggles' button models, so either one drives the same state. The map itself scales to the compact width.
- **`NpcPage` / `ObjectPage`**: new constructors take `(detail, Long mapZoneId, BufferedImage map, nav, clipboard)`. The old constructors delegate with no map.
  - **Spawns section**: `SpawnSection` places a `MapView` above the spawn table through the new `SourceSection.addAboveTable` (BorderLayout NORTH, 6 px gap).
  - **Pins**: the entity's spawn points in `mapZoneId` that have coordinates, as SELF pins.
  - **No map**: if there is no map or no point in that zone, the table stands alone.
  - **Accessor**: `spawnMap()` is public.
- **`DetailPanels`**:
  - `LoadedZone(detail, map, pins)`: the boss and rare pins are queried only when the zone has a map.
  - New `LoadedNpc(detail, mapZoneId, map)` and `LoadedObject(...)`. `mapZoneId` is the zone of the first spawn point with coordinates. The map bytes are read and decoded inside `load`, off the EDT.
  - `render` and `name` switch on the new records.

## Tests and results

- Full `./mvnw -B -q verify` exits 0: 1014 tests, 0 failures, 0 errors, 0 skipped across all modules.
- New or changed tests:
  - `ZoneRepositoryTest`: `npcSpawnsInZoneJoinNamesAndRanksAndSkipPointsWithoutCoordinates`, `bossesAndRaresOnlyKeepsRanksBossRareEliteAndRare`, `objectSpawnsInZoneJoinNames`.
  - `MapViewTest`, 12 tests:
    - Projection maths, no upscaling, `heightFor(420)`.
    - Painting, and pin kind from rank.
    - Hit testing at 8 px versus 9 px and 8.5 px diagonally, also at half scale.
    - Cluster counts and that the result depends on scale, filtering, tooltips, hover, click-to-open, SELF opening nothing, and the cluster menu.
  - `ZonePageTest`:
    - `mapShownWithPins`: Westfall map, default boss and rare pins, toggles through a fake loader, objects added and removed, click on a pin opens the NPC.
    - `togglesLoadOnTheWorkerAndDropStaleResults`: a queued executor, so nothing runs on the calling thread and the stale result is dropped.
    - `theCompactWindowCollapsesTheTogglesIntoAPinsMenu`.
    - The renamed `headerLinksTheParentZoneAndAZoneWithoutAMapHasNoMapArea`.
  - `NpcPageTest.spawnsOnMap`: Hogger 448 on Elwynn, 5 SELF pins, above the table, clicks open nothing. Also `onlyTheSpawnsInTheMappedZoneArePinnedAndWithoutAMapTheTableStandsAlone`.
  - `ObjectPageTest.spawnsOnMap`: Library Book 386759.
  - `DetailPanelsTest`: `aZoneLoadsItsBossAndRarePinsWithItsMap`, `anNpcLoadsTheMapOfItsFirstSpawnZone` (Hogger has a map, VanCleef has none), `anObjectLoadsTheMapOfItsFirstSpawnZone`, plus pin assertions on the existing zone tests.

## TDD evidence

- **Repository**: I wrote the tests first and stubbed the methods to return empty lists. The run went red with 3 failures in `ZoneRepositoryTest`. I then implemented the queries and the run went green.
- **MapView**: I wrote the tests first, so the code did not compile until `MapView` existed. The first run had one failure, and the mistake was in the test's own numbers: pin C was 6 px from the boss, not 10. I corrected the test values and the run went green.
- **Pages and DetailPanels**: I wrote all of these tests before the implementation. They were red only in the sense that they did not compile. They passed on the first run after implementing.
- **Mutation check**: I replaced the ZonePage generation guard with `if (true)`. That made `togglesLoadOnTheWorkerAndDropStaleResults` fail. I then restored the file.

## Files changed

- main:
  - `db/ZoneRepository.java`
  - `model/SpawnPin.java` (new)
  - `ui/widgets/MapView.java` (new)
  - `ui/widgets/MapImagePanel.java` (deleted)
  - `ui/widgets/SourceSection.java`
  - `ui/pages/ZonePage.java`
  - `ui/pages/NpcPage.java`
  - `ui/pages/ObjectPage.java`
  - `ui/pages/SpawnSection.java`
  - `ui/detail/DetailPanels.java`
- test:
  - `db/ZoneRepositoryTest.java`
  - `db/SpawnFixtureDb.java` (new)
  - `ui/widgets/MapViewTest.java` (new)
  - `ui/widgets/MapImagePanelTest.java` (deleted, cases moved)
  - `ui/pages/ZonePageTest.java`
  - `ui/pages/NpcPageTest.java`
  - `ui/pages/ObjectPageTest.java`
  - `ui/detail/DetailPanelsTest.java`

## Self-review

- **Completeness**: all items are covered. That means both queries, MapView, the three toggles, the compact "Pins ▾" menu, the ZonePage map, the NPC and object Spawns maps, the extended load records and every requested test.
- **EDT discipline**: map decoding and the default pins happen in `DetailPanels.load`. The toggle loads, and the conversion of their results to pins, run on the worker. Nothing queries the database on the EDT.
- **No map**: the zone page has no map area and no toggles. The NPC and object pages keep the table alone.
- **Quality**: clustering is O(n) on a grid and cached per width, pins and filter, so painting thousands of "All NPCs" pins does not recompute on every repaint.

## Concerns and judgment calls

1. **The fixture has no bosses or rares on a mapped zone.** The importer fixture has no boss or rare NPC spawning in Westfall, or in any zone with a fixture map. Hogger is an elite (rank 1). The ruling's premise for `ZonePageTest.mapShownWithPins` therefore does not hold on `FixtureDb` alone. I did not edit the shared importer fixtures, because that would ripple into other tests' counts. Instead I added the test helper `SpawnFixtureDb`. It copies the fixture file once per JVM and inserts four Westfall NPCs with spawns: Foe Reaper 4000 (rare elite), Vultros (rare), a test boss, and Thor (normal), plus one (-1, -1) point. The zone page, repository filter and DetailPanels pin tests use it.
2. **SELF pins open nothing.** Opening the page the user is already on would only reload it. A SELF pin gets a tooltip but no hand cursor and no navigation, and SELF entries in a cluster menu are disabled. This departs from the literal "click → nav.open" ruling.
3. **The filter hides only plain NPC pins.** `setFilter(true)` hides NPC-kind pins, which includes elites. Objects and SELF pins stay visible, so "Objects" also works together with "Bosses & rares".
4. **A failed toggle load is silent.** The map keeps the pins it already shows, and toggling again retries. The page has no status channel to report the error.
5. **Compact maps are not capped at 420 px.** The map scales to whatever width it is given, which is about 420 px in the compact window. There is no hard cap at 420.
6. **Map alignment is not visually checked.** I did not launch the window, so the spec ruling 13 acceptance is still the controller's visual check: Hogger on Elwynn and the Sentinel Hill flight master on Westfall.
7. **Two files are large.** `MapView.java` is 473 lines, including its nested types and painting. `ZonePage.java` is 363 lines. Both stay within the plan's scope, but they are on the large side.

## Fix report, round 1

Status: DONE
Commit: `fb9c718 fix(ui): Task 12 review: zone map pin load errors, map cache and paint`

### Findings fixed

1. **Toggle load errors (important).**
   - **New status interface:** `ui/StatusSink` has `message(String)`, `fail(Exception)` and a no-op `NONE`. `AppController` implements it in a `pageStatus` field: messages go to the status bar, failures go through the existing `fail(e)`, which logs and shows `"Error: …"`.
   - **Render overload:** `DetailPanels.render(detail, repos, nav, worker, status)` is new. `AppController.showEntity` passes its own worker and `pageStatus`. The three-argument `render` keeps `Async.SWING_WORKER` and `StatusSink.NONE`.
   - **While loading:** the zone map shows `"Loading pins…"`. A successful load shows `"<n> pins on the map"`.
   - **On failure:** if the load is still the current generation, the failure is reported. Each toggle whose data failed goes back: "Bosses & rares" is reselected, or "Objects" is turned off. The map then shows what it showed before.
   - **Tests:** `ZonePageTest.aFailedLoadIsReportedAndItsToggleGoesBack` covers a throwing pin source on a queued worker and on an inline one. `AppControllerTest.aZoneMapReportsItsPinLoadsInTheStatusBar` checks "5 pins on the map". It also closes the database under the page and expects `"Error: …"` with the Objects toggle back off. `DetailPanelsTest.renderReportsAZoneMapsLoadsToTheStatusSink` checks the render overload.
2. **`ui/pages/ZoneMapPanel` extracted.** It holds the `MapView`, the toggles, the compact "Pins ▾" menu, `PinSource` (moved from ZonePage), the worker loading, the generation counter and the status sink.
   - `ZonePage(d, ZoneMapPanel mapPanel, nav)` keeps the page layout and one nullable field, `mapPanel()`. The page is 158 lines, and the panel is 230.
   - `DetailPanels.render` builds the panel when the zone has a map.
   - `ZonePageTest` goes through `p.mapPanel()`.
3. **Least-recently-used map cache.** `ui/widgets/MapCache` keeps 4 decoded maps keyed by UiMap id. It is synchronized, decodes outside the lock, and the first decode wins, so concurrent loads share one image. `DetailPanels.load` uses `MapCache.shared()` for zone, NPC and object maps, all on the worker.
   - **Tests:** `MapCacheTest` covers reuse, eviction order, and that a map that fails to decode is not cached. `DetailPanelsTest.aMapShownAgainIsNotDecodedAgain` loads zone 40 twice and gets the same image. Hogger and the Library Book also share one Elwynn image.
4. **One "on the map" predicate.** `model/MapCoordinates` has `onMap(x, y)` (0 to 100 inclusive) and `sqlOnMap(alias)`, built from the same `MIN` and `MAX` constants.
   - **Users:** `ZoneRepository`'s two spawn queries, `SpawnSection` and `DetailPanels.mapZone` all use it. `mapZone` now picks the first spawn that is on the map.
   - **Tests:** `MapCoordinatesTest`. `SpawnFixtureDb` gains a point for Thor at 100.5, 50, and the repository test asserts it is not returned. The NPC page test adds a spawn at 100.5, 60 that is not pinned.
5. **MapView paint.**
   - **Hoisted:** the outline and ring strokes and the font metrics are created once per paint. The badge font is derived once per component font, cached in `badgeFont()`.
   - **Hover repaint:** `repaint(markerBounds(old))` and `repaint(markerBounds(new))` replace the full `repaint()`. `markerBounds` covers the dot, the hover ring and the count badge, plus a pixel.
   - **Test:** `MapViewTest.aMarkersBoundsHoldItsDotRingAndBadgeOnly`.

### Verification

- Full `./mvnw -B -q verify` exits 0: 1025 tests, 0 failures, 0 errors, 0 skipped.
- Two mutation checks, with the files restored afterwards:
  - With the toggle revert removed, `aFailedLoadIsReportedAndItsToggleGoesBack` fails.
  - With `MapCache` bypassed in `DetailPanels`, `aMapShownAgainIsNotDecodedAgain` fails.
- In this round I wrote the fixes before running the new tests, so the mutation checks are the evidence that the tests bite.

### Remaining notes

- **JPEG bytes:** the map's JPEG bytes are still read from the database on each load, about 200 KB. Only decoding is cached. Skipping the read would need a separate query for `ui_map_id` first.
- **Stale status messages:** a pin load that finishes after the user has left the zone page still sets the status message, because the generation guard is per panel. This is harmless, since the status just shows the count or the error.
- **File size:** `MapView.java` is now 519 lines because of `markerBounds` and the `radius` and `badgeFont` helpers.
