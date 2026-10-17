# Task 15 report: quest-giver pins on zone maps

**Status:** DONE_WITH_CONCERNS (MapView is 654 lines; see Concerns)
**Commit:** `4a77cf0 feat(ui): quest-giver pins on zone maps` on `feature/phase2b-ui`

## What was implemented

- **Models.** `model/QuestRef(questId, name, level, starts)` with `ref()`, `displayName()` and `label()` ("Wanted: Hogger (11)"). `model/QuestPin(giver, giverName, x, y, quests)` with `displayName()` and `starts()`.
- **Repository.** `ZoneRepository.questGiversInZone(zoneId)` is one parameterised query: `npc_spawn` UNION ALL `object_spawn` by zone, each joined to `quest_giver` by (kind, entity_id), with LEFT JOINs to the name tables and `quest`. It applies the shared on-map filter, skips item givers (no spawn table) and caps rows at `PIN_CAP`. Rows are grouped in Java to one pin per (giver, x, y), so duplicate spawn rows at the same point merge. Quests are de-duplicated and ordered by level; pins are ordered by giver name. `ZoneRepository.spawnsOf(EntityRef)` returns an NPC's or object's spawn points for the quest page.
- **MapView.**
  - New `PinKind.QUEST_START` and `QUEST_END`, with `PinKind.quest()`.
  - `Pin` gains `quests` and `emphasised`. The old 5-argument constructor is kept, so existing callers are unchanged.
  - `Pin.of(QuestPin)`: starts win, and starts are listed first. `Pin.giver(ref, name, x, y, starts)` makes the quest page's emphasised pin, which has no quests and opens the giver.
  - `cluster()` clusters the quest layer and the other pins separately and returns the quest layer first, so it is painted on top.
  - `clusterAt()` prefers a quest marker over any other marker within range.
  - `activate()`: a marker with one distinct quest opens that quest (`EntityKind.QUEST`). A marker with several opens `menuFor`: each quest, then "Giver: <name>" for each giver.
  - The quest cluster draws the glyph plus the existing count badge and hover ring.
- **PinGlyphs (new, 66 lines).** Bold page font at 14 px, or 20 px when emphasised, scaled with UIScale. Fill is #FFD100 for "!" and #B8B8B8 for "?". The outline is 1 px #0C1424 and the halo 1 px #F4EBD0: a 4 px halo stroke, then a 2 px outline stroke, then the fill. The glyph's visual bounds are centred on the pin.
- **QuestPinText (new, 97 lines).** Builds the quest tooltip.
  - One giver with one role: "Marshal Dughan — Wanted: Hogger (11), A Bundle of Trouble (5)".
  - Both roles: HTML lines "Marshal Dughan", "Starts: …", "Ends: …".
  - Several givers: "<n> pins" with one line per giver.
  - Any line over 80 characters wraps at 360 px (scaled). Real Elwynn data has Marshal Dughan with 19 quest roles, and one line would be very wide.
  - Also holds the menu's distinct-quest list.
- **ZoneMapPanel.**
  - New fourth toggle "Quests", also in the compact "Pins ▾" menu as a check item.
  - `PinSource.questPins(zoneId)`, backed by `PinSource.of(zones)`.
  - Loads through the same worker path: the generation guard, the "Loading pins…" status and the "n pins on the map" message.
  - On failure, Quests is turned off inside the `reverting` block together with the others, so the combined-failure guard holds for all three.
  - New constructor overload takes a `BooleanSetting questsShown`. The toggle starts from it and loads at once when it is on, and every change is saved to it. The old 7-argument constructor uses an in-memory setting that starts off.
- **Persistence.**
  - `UiState.questPinsShown()` and `setQuestPinsShown()` use the key `map.questPins`, default false. `questPinsSetting()` wraps them.
  - The new `ui/BooleanSetting` interface has `inMemory` and `of`.
  - `MainView.questPins()` defaults to in-memory, and `MainFrame` returns the UiState-backed setting.
  - `AppController` passes `view.questPins()` to a new `DetailPanels.render(..., filter, questPins)` overload. The existing overloads delegate with an in-memory setting.
- **QuestPage and DetailPanels.**
  - New `DetailPanels.LoadedQuest(detail, starts, ends)` replaces the raw `QuestDetail` record for quests. It holds two `QuestPage.GiverMap(zoneId, map, points)`, built off the EDT in `load`.
  - For each role, the first NPC or object giver with an on-map spawn in a zone whose map decodes is used, through `MapCache`. All of that giver's on-map points in that zone are pinned.
  - "Given by" shows the map above its table with emphasised QUEST_START pins. "Turned in to" shows its own map with QUEST_END pins.
  - When both are in the same zone and every end pin sits at a start pin's spot, "Given by" shows one map with both. The same giver at the same spot is a single "!". "Turned in to" then has no map.
  - Without a map the tables show alone, as before.

## Tests (TDD)

The tests were written first. `test-compile` then failed with "cannot find symbol" for `QuestPin`, `QuestRef`, `BooleanSetting`, `questGiversInZone`, `questsToggle`, `PinKind.QUEST_*` and the rest. After implementing, two MapView pixel tests failed at the natural 1002 px size: anti-aliasing leaves no fully covered pixel in the glyph's stem there. The pixel check now accepts a blend of up to 24 per channel, and all tests pass.

- **SpawnFixtureDb** gains Westfall quests and givers; the shared fixture is unchanged.
  - Thor (900004, on the map at 56.6, 52.6, plus his two off-map points) starts The People's Militia (900101, level 18) and Patrolling Westfall (900102, level 14), and ends Patrolling Westfall.
  - The fixture object The Last Guardian (175740, two Westfall points, plus a duplicate row at 52.4, 53.1) ends 900101.
  - Item 16305 starts Sealed Orders (900105), which is skipped.
  - No NPC spawns were added, so the existing Westfall pin counts are unchanged.
- **ZoneRepositoryTest.questGiversInZone** checks:
  - The order: object then Thor.
  - The duplicate point merging into one pin, and the off-map points skipped.
  - Both roles ordered by level, and `starts()`.
  - The item giver skipped.
  - An unnamed NPC giver in the plain fixture ("Unknown NPC #2155", quest 490 both roles).
  - An unknown zone returning nothing.
- **ZoneRepositoryTest.spawnsOfAnNpcOrObject**.
- **MapViewTest**, eight new tests:
  - The kind from quests, with starts winning and listed first.
  - The glyph colour pixel checks: #FFD100 at the "!" pin, #B8B8B8 at the "?" pin, and nowhere else.
  - Layer separation: a quest pin 4 px from an NPC pin does not cluster with it, and two quest pins cluster. The hit test prefers the quest layer, the glyph is painted over the dot, and the multi-giver tooltip and menu are right.
  - A click on a one-quest pin opens the quest.
  - A several-quest pin's menu lists "Wanted: Hogger (11)", "A Bundle of Trouble (5)" and "Giver: Marshal Dughan", and each item opens its target.
  - The tooltip of a giver with both roles: Starts first, then Ends, under a "!".
  - A long list wraps.
  - The emphasised giver pin opens the giver and is drawn larger.
- **ZonePageTest.questToggleLoadsAndShowsQuestPins** checks:
  - The toggle is off by default.
  - Turning it on loads "quests 40" and shows "6 pins on the map" with the expected kinds, and saves the setting.
  - It combines with "All NPCs", so Thor shows both a dot and a "!".
  - A click on The Last Guardian's "?" opens quest 900101.
  - Turning it off and on again does not reload.
  - A new panel with the saved setting starts with Quests on and loads at once.
- **ZonePageTest, other changes.**
  - The combined-failure test now runs with three toggles: three queued loads, the first two superseded, the third failing. All three toggles revert, the setting follows, and nothing is queued afterwards.
  - The single-failure test covers Quests too.
  - The compact-menu test covers the fourth item.
- **QuestPageTest.giverOnMap** checks:
  - Quest 900101: a Westfall map in "Given by" with Thor's emphasised "!" (a click opens Thor), and a separate "Turned in to" map with The Last Guardian's two "?" pins.
  - Quest 900102: one map with a single "!" and no "Turned in to" map.
  - Quest 2 and quest 490: no maps, with the tables unchanged.
- **UiStateTest.questPinsShownDefaultsToOffAndRoundTrips**.
- **WindowMenusTest.aZoneMapsQuestsToggleIsSavedAndTheNextZonePageStartsWithIt** wires the controller through StubView and UiState.

**Results:** `./mvnw -B verify` succeeded. Importer modules ran 61 and 336 tests, and app ran 662, all with 0 failures and 0 errors.

**Query cost**, measured read-only on the worktree's full `app/target/classes/data/wowforever.sqlite`:

| Zone | Rows | Pins | Time |
|---|---|---|---|
| Elwynn Forest | 357 | 99 | 3.5 ms |
| Westfall | 184 | 66 | 1.3 ms |
| Stormwind City | 655 | 132 | 2.3 ms |
| Teldrassil | 221 | 61 | 1.3 ms |
| Durotar | 236 | 62 | 1.7 ms |

`EXPLAIN QUERY PLAN` shows `idx_npc_spawn_zone` and `idx_object_spawn_zone` for the zone, `idx_quest_giver_entity (kind=? AND entity_id=?)` for the join, and primary-key lookups for names and quests. A temporary B-tree handles the ORDER BY. No schema change was needed, because `quest_giver(kind, entity_id)` is already indexed.

## Files changed

The main sources are under `app/src/main/java/dev/wowforever/app`:

- New: `model/QuestPin.java`, `model/QuestRef.java`, `ui/BooleanSetting.java`, `ui/widgets/PinGlyphs.java`, `ui/widgets/QuestPinText.java`.
- Modified: `db/ZoneRepository.java`, `ui/widgets/MapView.java`, `ui/pages/ZoneMapPanel.java`, `ui/pages/QuestPage.java`, `ui/detail/DetailPanels.java`, `ui/UiState.java`, `ui/MainView.java`, `ui/MainFrame.java`, `ui/AppController.java`.

The tests are under `app/src/test/java/dev/wowforever/app`: `db/SpawnFixtureDb.java`, `db/ZoneRepositoryTest.java`, `ui/widgets/MapViewTest.java`, `ui/pages/ZonePageTest.java`, `ui/pages/QuestPageTest.java`, `ui/UiStateTest.java`, `ui/WindowMenusTest.java` and `ui/StubView.java`.

## Self-review

- **Completeness against the brief and rulings.**
  - Done: glyph colours, outline and halo; 14 px scaled with UIScale and larger when emphasised; cluster glyph with badge.
  - Done: the separate layer that is drawn on top and hit first; start wins, with starts listed first in the tooltip.
  - Done: an independent "Quests" toggle, default off, persisted with a round-trip test, and loading through PinSource with status messages and revert.
  - Done: the combined guard extended to three toggles; the one-quest click and the multi-quest menu with the giver item.
  - Done: the QuestPage maps for both sections, merged when same zone and spot, loaded off the EDT through MapCache, with the tables alone when there is no map.
  - Done: the repository grouping, both roles, item givers skipped, on-map filter, parameters, ordering and index use; the requested tests and MapView cases.
- **EDT discipline.** Quest pins load on the worker through `Async.run`. The quest page's spawn queries and map decoding run in `DetailPanels.load`, which the controller calls on the worker. The EDT only builds pins from loaded data.
- **Compact menu.** "Quests" is a `JCheckBoxMenuItem` that shares the toggle's model, and it is tested.
- **Fixture discipline.** The shared FixtureDb is untouched; only `SpawnFixtureDb` was extended.
- **Line endings.** Some files picked up CRLF from a Python edit script and were normalised back to LF before the commit. Git now reports no EOL warnings.

## Concerns

1. **MapView size.** MapView is 654 lines, against the plan's guideline of about 550. The glyph painting is in `PinGlyphs` and the quest tooltip text in `QuestPinText` (a split the controller did not name). What remains is the `Pin` and `Cluster` record extensions, the layered clustering and hit test, and the quest branch of `menuFor` and `activate`, which needs the navigator. A further split would move `Pin` and `Cluster` into their own files, which changes the public `MapView.Pin` API that many callers use. I did not do that unasked.
2. **The saved setting follows the toggle, including after a failed load.** When a quest load fails, the toggle goes off and the saved value goes off with it. The test asserts this. The alternative would keep the user's "on" so the next zone retries. I chose the simpler meaning, that the saved value is what the toggle shows.
3. **Same zone but different spots gives two maps of one zone.** The ruling says to merge only when the zone and the spot are the same. So a quest started and ended by different givers in the same zone shows two maps of that zone, one in each section.
4. **The quest page's emphasised pin opens the giver.** It lists no quests and opens the giver rather than doing nothing like SELF pins, because the giver is another entity. Its tooltip is just the giver's name.
5. **A capped load is silent.** The row cap `PIN_CAP` (20,000) applies to joined rows, not pins. Real zones read at most about 650 rows, so the cap is far away. If it were ever hit, the last giver could lose some quests without any notice.
6. **The halo is not pixel-tested.** The pixel tests check the fill colours, which allow some anti-alias blending, but not the halo. It needs the controller's visual check in both themes.
