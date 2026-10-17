# Merge main (v0.2.0) into feature/phase3a-quest-data

Status: done, verify green.

| Commit | What |
|---|---|
| `807f7dd` | Merge main (v0.2.0) into feature/phase3a-quest-data (parents befc422, d88031e). Conflict resolutions only. |
| `7cb870c` | fix: reconcile phase 2B UI tests and world drops with the phase 3A data (semantic fixes, see below). |

The merge commit on its own is not green: 12 app tests fail on it, all fixed by `7cb870c`. That split keeps the merge a pure textual resolution, as allowed by the brief.

## Conflicts and resolutions

| File | Resolution |
|---|---|
| `NOTICE.md`, `app/src/main/resources/NOTICE.md` | 3A's trademark line ("Icons, UI textures and map art"), the VMaNGOS and Blizzard client data sections, then 2B's "Third-party libraries" section. Both files are byte-identical (`cmp`), MainFrameTest passes. |
| `README.md` | Tests section: one app bullet merging 2B's text (headless panels, README keyboard table check, one page of every kind rendered) with 3A's (quest objectives and relations, rail UI icons, patrol routes, crafting, VMaNGOS rates), plus 3A's tools bullet. Known limitations: 2B's "The tooltip shows each one as `?`" kept on the placeholder line; 3A's "PvP and faction vendors are not imported. Crafting recipes and NPC patrol routes are in the database, but the browser does not show them yet." replaces 2B's "Crafting, PvP and faction vendors are not imported" (3A data wins). 2B's "Using the app" guide, keymap table and screenshots and 3A's data and rebuild notes merged cleanly. |
| `ItemRepository.java` | 3A's `createdBy` / `reagentFor` / `taughtBy` / `crafts` kept, followed by 2B's `card` javadoc (set pieces naming their items). |
| `NpcRepository.java` | 3A's `routes(long)` and `routes(Connection, String, long)` kept, then 2B's `list(NpcFilter)` and `itemJoin`, then 2B's `plainLoot` javadoc. 2B's drop query with `dropper_count` is unchanged. |
| `QuestRepository.java` | 3A's `HashMap` import kept; class javadoc merged ("objectives, relations and chains, filtered lists, zone lists ..."). |
| `ZoneRepository.java` | Union of both import sets; 3A's `routesInZone` followed by 2B's `npcSpawnsInZone`, `objectSpawnsInZone`, `questGiversInZone(long)` and `questGiversInZone(long, int)` with its cap flag, and `pin`. |
| `NpcRepositoryTest`, `QuestRepositoryTest`, `ZoneRepositoryTest` | Union of imports; every test of both sides kept. Counts: Npc base 3, main 10, 3A 6, merged 13. Quest base 3, main 7, 3A 14, merged 18. Zone base 5, main 12, 3A 7, merged 14. |
| `ShippedDataTest.java` | Only the imports conflicted. 3A's three-class structure on `ShippedDataSupport` kept. 2B's `EntityKind`, `JPanel` and `JTable` imports added. 2B's `Repositories`, `AfterAll` and `BeforeAll` imports dropped because `ShippedDataSupport` owns the database lifecycle. |

Signature check: 3A's app-side changes to the four repositories are pure additions. 2B's callers of `ZoneRepository.questGiversInZone`, the NPC drop rows and `QuestRepository.relatedItems` compile and run unchanged.

## Shipped tests moved

Git applied 2B's ShippedDataTest changes into 3A's ShippedDataTest. All of them are page-rendering or UI facts, so all stay in `ShippedDataTest`:

- `theCompactSpawnTableKeepsItsWholeRowUnderTheMap` (new in 2B), with its private `layOut` helper.
- `everyPageKindRendersFromTheShippedDatabase` (new in 2B). It renders quests 176 and 7787 on the 80 MB database, and it passes.
- `theShippedPagesReadAsReviewed` (new in 2B).
- `itemClassListsAreNotCapped` (existing; 2B's MasterList/ListPage rewrite of it is kept).

No 2B test had a quest or provenance subject, so nothing went to `ShippedQuestDataTest` or `ShippedProvenanceDataTest`. A scripted check finds every test name from main's ShippedDataTest and from 3A's three classes in the merged classes: 43 tests, which is 22 + 14 + 7, and none is missing.

## Semantic fixes (commit 7cb870c)

1. **World drops on the NPC page (product rule).** 2B folded a drop into the collapsed "World drops" group only when its rate was unknown, it was of a generic-pool class and 20 or more NPCs drop it. 3A's VMaNGOS rates cover 137,373 drop rows, including the generic pool. On Hogger, 23 of 25 drops now have a VMaNGOS rate. His page showed 29 rows, with Recipes (11), Trade goods (5) and Consumables (6), instead of his claw, the quest note and a collapsed pool. That failed `theShippedPagesReadAsReviewed`. It also failed `theCompactSpawnTableKeepsItsWholeRowUnderTheMap`, because the taller page had a scrollbar from the first layout.
   - **Change.** `ItemCategories.ofDrop` no longer requires an unknown rate. A pool-class item (Recipe, Trade Goods, Reagent, Consumable, Miscellaneous) that 20 or more NPCs drop is a world drop whatever its rate. Quest items, gear and bags are still never world drops. "Reagent" is still in the pool.
   - **Result.** Hogger reads Quest items (2) and then World drops (23) collapsed, with the rates visible when it is opened.
   - **Updated with it.** The ItemCategories and NpcPage javadoc and the README "NPC, dungeon, object and quest pages" sentence follow the new rule.
   - **New test.** `NpcPageTest.aRatedPoolDropManyNpcsShareIsStillAWorldDrop` pins the new rule. All existing 2B NpcPage and ItemCategories tests pass unchanged.
2. **2B fixture tests against 3A's enlarged fixture.** 3A's importer fixture adds NPC 550 Defias Messenger (Westfall, levels 14 to 15, normal rank, with spawns), quest 452 at level 15, quest 176 Wanted: "Hogger", and brings the fixture to 26 quests. These 2B tests were updated to the 3A fixture:
   - `NpcRepositoryTest.unfilteredNpcListHasEveryNpcByName` now expects 16 NPCs instead of 15.
   - `NpcRepositoryTest.npcLevelRangeOverlapsTheNpcLevels` now includes Defias Messenger in the 15 to 25 range.
   - `QuestRepositoryTest.questListByZoneAndLevel` now includes quest 452 in levels 15 to 16 and expects 26 quests in all instead of 10.
   - `SearchRankingTest.exactNameMatchRanksFirst` now expects "hogger" to return NPC 448, then item 90001, then quest 176. The exact name still ranks first.
   - `ZoneRepositoryTest.bossesAndRaresOnlyKeepsRanksBossRareEliteAndRare` now has Defias Messenger among Westfall's NPC pins. The bosses-and-rares filter is unchanged.
   - `AppControllerTest.aFilterChangeReloadsAndReplacesTheHistoryEntry` and `ListPageTest.npcFilterBarHasZoneRankAndLevels` now expect 16 NPCs.
   - `ZonePageTest.instancesAndSubZonesAreLinksAtTheTop` now expects Westfall's sections to be NPCs and Quests.
   - `ZonePageTest.mapShownWithPins` now expects 5 pins with Defias Messenger, and the bosses filter still hides it.
   - `ZonePageTest.togglesLoadOnTheWorkerAndNeverRunAQueryAlreadyInFlight` now expects 6 pins.

Other checks:
- **Surefire settings.** The `systemPropertyVariables` in app/pom.xml, which hold the in-memory preferences factory, are intact, and PreferencesIsolationTest passes.
- **Keymap parity.** ReadmeKeyMapTest passes, so the README keymap table matches KeyMap.

## Verify

The command is `./mvnw -B verify` from H:\claude_java on JDK 21.0.12. It includes the shipped-data group on the 80 MB database. It ran on the committed tip `7cb870c` and the build succeeded.

| Module | Tests | Failures |
|---|---|---|
| lua-table | 61 | 0 |
| importer | 415 | 0 |
| app | 798 | 0 |

These shipped-data and parity classes all pass: ShippedDataTest has 22 tests, ShippedQuestDataTest 14, ShippedProvenanceDataTest 7, MainFrameTest 2, ReadmeKeyMapTest 2 and PreferencesIsolationTest 2.

## To double-check

- **World-drop rule.** This is a product call: the rule now ignores the rate. The alternative is to fold only VMaNGOS-estimated or unknown rates, which needs `pct_source` on the NPC drop rows (`LootRow`), a model change I did not make. With the current rule, a 6 % Tough Jerky on Hogger also sits in World drops.
- **Commit trailer.** Both commits carry `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` as the brief asked. The session's own attribution would have been Claude Opus 5.5.
