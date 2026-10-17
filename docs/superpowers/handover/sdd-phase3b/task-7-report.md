# Task 7 report: community drop rates read "Vanilla rate"

Commit: `cc4d7c2` on `feature/phase3b-quest-ui` (parent `d985c88`).
Status: DONE.

## What was built

- **Model.**
  - `RateSource` gains `questOnly` as its third component. A two-argument constructor passes false.
  - `BOSS_NOTE = "Vanilla rate; Forever loot reworked"`, `VMANGOS = "vmangos"` and `vanilla()` are new.
  - `ORDER` now ranks vmangos after the four AtlasLoot sources (questie, questie-wowhead, questie-correction, atlasloot, atlasloot-override, atlasloot-forever, atlasloot-forever-override, vmangos, others by name). The label "Vanilla rate (VMaNGOS)" from the 3A fix wave stays.
  - `LootRow` gains `pctSource` as its last component. The former canonical 16-argument constructor is now a secondary one passing null.
  - `InstanceLoot` gains `pctSource` as its last component, with a 9-argument secondary constructor passing null. It is needed for the item page's merged loot-list rows.
- **Repositories.**
  - NPC drops and boss loot select `pct_source` into `LootRow`. The item page's loot lists select `bl.pct_source`.
  - The rate source queries select `quest_only`.
  - `NpcRepository.bosses(Collection<Long>)` returns the ids that are `boss.npc_id` or whose NPC zone kind is dungeon or raid. It runs one query per 500 ids, because an item can have up to 1,732 rated droppers.
- **Loading.**
  - `DetailPanels.LoadedItem` and `LoadedNpc` gain `Set<Long> bossNpcs`, loaded in `DetailPanels.load` off the EDT.
  - The item page asks about its rated droppers.
  - The NPC page asks about itself, and only when a drop has sources.
- **Renderer.**
  - The new constructor `RateBarRenderer(sourcesOf, onBoss, headlineSource)` sits beside a matching `install` overload. The old constructors and overloads delegate with "no boss, no headline".
  - A known rate whose headline is vmangos reads `"12.50 % V"`. With a disagreement it reads `"9.00 % V ≠"`.
  - The number is painted in the usual foreground. The V is painted in text.muted, or in the selection foreground on a selected row. The ≠ stays amber.
  - `vanillaBounds()` sits beside `markerBounds()`.
  - `sourcesText(sources, boss)` appends " on quest" to a quest-only source. On a boss with a vmangos source it adds a second line, `BOSS_NOTE`, separated by `\n`.
  - The tooltip is plain text with `\n`. FlatLaf's `FlatToolTipUI` shows that as several lines (checked: it has `isMultiLine`). `sourcesText(List)` keeps working as the boss = false case.
- **Pages.**
  - `LootTables.grouped(..., boolean boss, nav)` and `decorate` pass the boss flag and each entry's `pctSource`. `plain` tables pass the row's `pctSource`.
  - `NpcPage` gets a new constructor with `bossNpcs` and uses `bossNpcs.contains(id)`.
  - `InstancePage` is always boss.
  - `ItemPage` gets a new constructor with `bossNpcs`. An NPC row is a boss when its NPC is in the set, and a loot-list row is always boss loot. The headline comes from `DropSource.pctSource` or `InstanceLoot.pctSource`.
- **Columns.** `VANILLA = " V"` and `rateText(pct, vanilla, disagrees)` are new. The old two-argument `rateText` delegates.
- **Legend.** `SourceFilterControl.LEGEND` now has a second line, `V Vanilla rate (VMaNGOS); hover for details.`
- **README.** The rate-mark sentence now explains V, the boss line and "on quest".
- **NOTICE.md.** The root file and the app copy already carry the VMaNGOS GPL-2.0 section from 3A Task 9. Nothing was added.

## Red, then green

Red, run before the implementation (`RateSourceTest,RateBarRendererTest,ShippedProvenanceDataTest`): 33 tests, 2 failures, 10 errors.
- The 10 errors were unresolved compilation problems for the new API in RateSourceTest and RateBarRendererTest, plus the boss test in ShippedProvenanceDataTest.
- `ShippedProvenanceDataTest.hoggerClawHasAVanillaRate` failed with expected `"Questie 84.00 %  ·  Vanilla rate (VMaNGOS) 100.00 % on quest"`.
- `ShippedProvenanceDataTest.aQuestOnlyVanillaHeadlineIsMarkedAndReadsOnQuest` failed with expected `"100.00 % V"`.

Green, after the implementation:

| Test class | Tests | Failures |
|---|---|---|
| RateSourceTest | 4 | 0 |
| RateBarRendererTest | 18 | 0 |
| ShippedProvenanceDataTest | 11 | 0 |
| SourceFilterControlTest | 3 | 0 |
| NpcRepositoryTest | 15 | 0 |
| DetailPanelsTest | 22 | 0 |
| ItemPageTest | 33 | 0 |
| NpcPageTest | 25 | 0 |
| InstancePageTest | 17 | 0 |
| ItemRepositoryTest | 21 | 0 |
| InstanceRepositoryTest | 9 | 0 |

## New tests

- **RateSourceTest.**
  - `vmangosReadsVanillaRate` checks the label, that vmangos sorts after atlasloot-override and atlasloot-forever and before unknown sources, and `BOSS_NOTE`.
  - `aQuestOnlyRateIsTheChanceWhileOnTheQuest` checks the flag.
- **RateBarRendererTest.**
  - `vmangosHeadlineIsMarked` runs in both themes. It checks `"12.50 % V"`, that the V region has muted pixels and no foreground pixels, that the number has foreground pixels, and that the first tooltip line starts with "Vanilla rate (VMaNGOS) 12.50 %".
  - `otherHeadlinesAndUnknownRatesCarryNoVanillaMarker` checks that a questie headline and an unknown rate carry no V.
  - `bossTooltipSaysLootReworked` checks the exact two lines on a boss, that a boss without a vmangos source gets no such line, and that a non-boss gets no such line.
  - `disagreementStillMarked` checks `"2.31 % ≠"` and that both sources are in the tooltip.
  - `aVanillaHeadlineTheSourcesDisagreeOnCarriesBothMarkers` checks `"9.00 % V ≠"`, that the V sits left of the ≠, that the V is muted and not amber, and that the ≠ is amber.
  - `aQuestOnlyVanillaRateReadsOnQuest` uses Hogger's claw values.
- **ShippedProvenanceDataTest** (shipped database 70450). The controller ruled that the shipped test lives here, not in ShippedQuestDataTest.
  - `hoggerClawHasAVanillaRate`: a vmangos row exists for (448, 1931). Hogger's NPC page shows the claw as `"84.00 % ≠"` with the tooltip `"Questie 84.00 %  ·  Vanilla rate (VMaNGOS) 100.00 % on quest"`, and no boss line.
  - `aQuestOnlyVanillaHeadlineIsMarkedAndReadsOnQuest` uses the shipped example whose headline itself is quest-only. Morgan the Collector (473) drops The Collector's Ring (2239) at 100 % from vmangos, with source_count 1. Its page shows `"100.00 % V"` and the tooltip `"Vanilla rate (VMaNGOS) 100.00 % on quest"`.
  - `dungeonBossVanillaRatesSayForeverLootIsReworked` covers four pages:
    - Edwin VanCleef's NPC page shows An Unsent Letter as `"100.00 % V ≠"`, and the tooltip ends with the boss line.
    - The letter's item page shows the same boss line on the VanCleef row.
    - Morgan's world-NPC tooltip has no boss line.
    - On The Deadmines instance page, VanCleef's encounter shows the letter starting with "100.00 % V", and its tooltip ends with the boss line.
- **NpcRepositoryTest** (fixture database).
  - `rowsCarryTheirHeadlineSourceAndSourcesTheirQuestOnlyFlag` works on a copy of the fixture. It checks that `pct_source` reaches NPC drops, boss loot, item loot lists and droppedBy, and that `quest_only` reaches `sources`, `sourcesOfNpc` and `sourcesOfItem`.
  - `bossesAreEncountersAndNpcsOfDungeonsAndRaids` checks encounters, a non-boss, an unknown id, more than two chunks of ids, and the zone-kind rule alone on a copy with the boss rows deleted.
- **DetailPanelsTest.**
  - `bossNpcs` is {639} for VanCleef, empty for Lucifron because no rated drops means the lookup is skipped, and empty for Hogger.
  - The new `anItemLoadsWhichOfItsRatedDroppersAreBosses` checks that An Unsent Letter yields {639}.

## Full verify

`./mvnw -B -q verify` exited 0 on the 70450 database, shipped-data tests included. It ran before the README sentence was added. `ReadmeKeyMapTest` passed again after that edit.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 449 | 0 | 0 | 0 |
| app | 918 | 0 | 0 | 0 |

## Changed expectations

- **SourceFilterControlTest** `theTooltipIsTheRateLegendAndTheItemsReadAsTheFilters`: the legend now has the second line `V Vanilla rate (VMaNGOS); hover for details.`, as the controller asked.

No other existing expectation changed. The fixture database has no vmangos rows, so every existing tooltip and rate text stays the same.

## Open choices

1. **"on quest" placement.** "on quest" is inline after the quest-only source's rate, for example "Vanilla rate (VMaNGOS) 100.00 % on quest". It is not a separate line. That keeps the brief's "first line starts with 'Vanilla rate (VMaNGOS) 12.50 %'", and it marks the source wherever it appears, whether or not it is the headline.
2. **Tooltip line breaks.** Lines are separated with `\n` in plain text, which FlatLaf renders as multi-line. They are not HTML. Single-line tooltips stay byte-identical to before.
3. **Loot-list rows on the item page are always "boss".** They are AtlasLoot dungeon or raid lists, including trash lists. In practice only lists with a boss NPC have sources, so only those can show the line.
4. **Trash counts as boss.** The NPC's zone kind (dungeon or raid) makes even trash mobs "bosses" for the note, as the ruling says. The rank chip is unaffected.
5. **Order of markers.** The V comes before the ≠ ("9.00 % V ≠"). On a selected row the V takes the selection foreground rather than muted, so its contrast holds. The ≠ stays amber, as before.
6. **The headline source is not reordered in the tooltip.** Sources keep `RateSource.ORDER`, so a vmangos headline next to a Questie source is listed second. The V on the number says which source is the headline.

## Fix round 1 (commit `5fccf8f`, on top of `247e6ed`)

Every finding in task-7-review.md is fixed.

- **Important 1: the compact tooltip line break.** `CompactColumns.join` now escapes each plain part and then turns its line breaks into `<br>`, for both the row and the cell part. The boss note keeps its own line in the compact window.
  - The new test `CompactColumnsTest.joinKeepsTheLineBreaksOfPlainParts` checks `join("Item level 23", "a
b")`, which is `"<html>Item level 23<br>a<br>b</html>"`. It also checks a row with a line break next to an escaped cell.
- **Minor 1 (ruling): world bosses get no note.**
  - `NpcRepository.bosses` counts an encounter only when its instance has a zone (`JOIN instance i ... WHERE i.zone_id IS NOT NULL`).
  - In the shipped database, AtlasLoot's World Bosses list (26) is the only instance without a zone, and every zoned instance is of kind dungeon or raid. The shipped test pins both facts.
  - `InstancePage` passes `zoneId != null` instead of `true`.
  - On the item page, a loot-list row now counts as a boss only when its boss NPC is in the loaded set. It is no longer always a boss.
  - Tests:
    - `NpcRepositoryTest.worldBossesAreNoDungeonBosses` uses a fixture copy with VanCleef's instance unzoned and VanCleef moved to Hogger's zone.
    - `ShippedProvenanceDataTest.worldBossVanillaRatesSayNothingOfForever` covers Azuregos, Lord Kazzak and the four Dragons of Nightmare (6109, 12397, 14887-14890). It checks that they are not bosses, and that Azuregos's NPC page and his World Bosses encounter show Vanilla rates without the note.
    - `ItemPageTest.aWorldBossLootListsVanillaRateSaysNothingOfForever`.
- **Minor 2: text.primary.** `vmangosHeadlineIsMarked` now gives the table the theme's text.primary and bg.raised. It asserts the renderer's foreground and background equal those tokens, that the number has text.primary pixels, and that the V has none.
- **Minor 3: the zone-kind assertion.** The copy now makes 11520's zone an open-world one and asserts exactly {639, 448}.
- **Minor 4: the item page's loot-list branch.** `ItemPageTest.aDungeonLootListsVanillaRateIsMarkedAndSaysForeverReworkedTheLoot` builds a standalone `DropRow.Loot` row with pctSource vmangos. It checks `"20.00 % V"` and the tooltip `"Vanilla rate (VMaNGOS) 20.00 %
" + BOSS_NOTE`.
- **Minor 5: binding each id once.** `bosses` binds each id once through `WITH ids(id) AS (VALUES (?), ...)`. `BOSS_CHUNK` is now 900, below SQLite's classic 999-parameter limit. The test asserts the limit and sends `2 * BOSS_CHUNK + 2` ids.

**Red, then green.** I saved the main-code diff as a patch, checked out the old main code and ran the new tests. Four failed, exactly the four new rule tests:
- `CompactColumnsTest.joinKeepsTheLineBreaksOfPlainParts`
- `NpcRepositoryTest.worldBossesAreNoDungeonBosses`
- `ItemPageTest.aWorldBossLootListsVanillaRateSaysNothingOfForever`
- `ShippedProvenanceDataTest.worldBossVanillaRatesSayNothingOfForever`

I then reapplied the patch. git stash was not used. With the fix, everything is green.

`./mvnw -B -q verify` exited 0:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 449 | 0 | 0 | 0 |
| app | 934 | 0 | 0 | 0 |

**Changed expectations.**
- `NpcRepositoryTest.bossesAreEncountersAndNpcsOfDungeonsAndRaids` is tightened: an exact set, an explicit open-world zone for 11520, and ids sized from `BOSS_CHUNK`.
- `RateBarRendererTest.vmangosHeadlineIsMarked` now measures against theme tokens.

No behaviour expectation of earlier tasks changed.

**Data aside, unchanged.** Maws 15571, an AQ40 boss with no zone and no boss row, still gets no note, as the reviewer observed. No ruling covered it.
