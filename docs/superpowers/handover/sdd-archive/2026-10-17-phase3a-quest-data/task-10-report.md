# Task 10 report: one shipped-database regeneration for Tasks 7–9

Status: DONE_WITH_CONCERNS (one existing shipped-data pin changed meaning; see Concerns 1)
Branch: `feature/phase3a-quest-data` (checkout H:\claude_java, base cd2795e)

Commits:
- `5a8bc0d` data: NPC waypoints, crafting and community drop rates. It holds the sqlite, build-id.txt,
  import-report.txt, ShippedDataTest.java and the new ShippedDataSupport.java, ShippedQuestDataTest.java and
  ShippedProvenanceDataTest.java.
- `befc422` docs: README data notes for waypoints, crafting and VMaNGOS rates. It holds README.md only.

Both commits carry the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Nothing was pushed.

## Commands run

```
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -q -pl importer -am package -DskipTests
java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite --fail-on-unsupported \
    --client-data-dir ~/.cache/wowforever-clientdata/out/current --server-db-dir ~/.cache/wowforever-serverdb/4641790
                                                                       # exit 0, 6.5 s, stderr empty
./mvnw -B -q verify                                                    # exit 0 (run twice, the second after the
                                                                       # import clean-up and Javadoc edit)
```

- **Inputs.** SpellReagents.csv, SkillLineAbility.csv and SkillRaceClassInfo.csv were already in
  `out/current/csv`, so there was no convert. `mangos.sqlite` was already in the server-db cache, so there was no
  download. No network was used and the WoW installation was not touched.
- **build.lock.** `tools/clientdata/build.lock` equals `out/current/build.lock` ignoring CR/LF; Task 8 committed it.
  Nothing was staged for it.

## Report counters

Expected against actual:

| Counter | Expected | Actual |
|---|---|---|
| clientdata.uiAssets | 6 | 6 |
| npc.waypoints.npcs | 479 | 479 |
| npc.waypoints.points | 23,015 | 23,015 |
| clientdata.crafts | 3,335 | 3,335 |
| clientdata.crafts.noSkill | 967 | 967 |
| clientdata.crafts.reagents | 8,707 | 8,707 |
| clientdata.crafts.recipes | 1,869 | 1,869 |
| clientdata.crafts.recipeItems | 1,917 | 1,917 |
| clientdata.crafts.extraCreates | 1 | 1 (spell 423212, 2 rows) |
| clientdata.crafts.rowsWithoutSpell | 0 | 0 |
| clientdata.crafts.skipped | absent | absent |
| serverdb.loot.rows | 227,232 | 227,232 |
| serverdb.unmatched.npcs | 6 | 6 |
| serverdb.skipped | absent | **0** (see Concerns 2) |
| quest.relation.missing | 1 | 1 |
| clientdata.coverage.pct | ≥ 85 (96) | 96 |
| meta schema_version | 3 | 3 |

The other new or changed counters:

```
serverdb.creatures = 4164
serverdb.loot.forbidden = 3
serverdb.rates = 1593222
serverdb.rates.questOnly = 2582
serverdb.rates.questOnlyPairs = 2590
serverdb.reference.rows = 9976
serverdb.references.depthCapped = 0
serverdb.references.missing = 0
serverdb.references.zeroChance = 0
serverdb.zeroChance.ungrouped = 0
provenance.pairs = 146923              (was 11339)
provenance.disagreeing = 5912          (was 201)
provenance.sources.vmangos = 146548    (new; the other provenance.sources.* are unchanged)
table.craft = 3335
table.craft_reagent = 8707
table.craft_recipe_item = 1917
table.drop_rate_source = 158244        (was 11696)
table.meta = 16                        (was 14: serverdb_asset, serverdb_commit)
table.npc_waypoint = 23015
```

- **Unchanged counters.** Every other counter, the quest and ui_asset ones included, is unchanged.
- **Conflicts section.** The top-50 list is now led by Questie-Wowhead against VMaNGOS pairs. It also gains five
  "X vs vmangos" summary lines, such as `questie-wowhead vs vmangos: 4569 disagreeing of 9155 shared` and
  `vmangos vs atlasloot: 1436 disagreeing of 2034 shared`.
- **Notes.** One note is new: `clientdata.crafts.extraCreates` names 423212.

meta now holds `serverdb_asset = db-sqlite-4641790.zip` and
`serverdb_commit = 464179081673cfd240f7ffe7f0daf96bca0b5a70`.

| build_id | |
|---|---|
| Before | `900b74572d67f8c8b70474eb6df3576586581f294f23f91e34e62cde7712b2b2` |
| After | `f35f4f21775d123b48f1ce2f33309c175bac494fe57ddd0547d2c6d724a79722` |

## Table counts (regenerated DB)

| Table | Rows |
|---|---|
| boss | 356 |
| boss_loot | 2,886 |
| craft | 3,335 |
| craft_reagent | 8,707 |
| craft_recipe_item | 1,917 |
| drop_rate_source | 158,244 |
| game_object | 7,033 |
| icon | 2,925 |
| instance | 33 |
| item | 21,543 |
| item_alt_value | 2,665 |
| item_armor | 7,603 |
| item_class / item_subclass | 17 / 100 |
| item_container_drop | 4,095 |
| item_damage | 2,359 |
| item_effect | 6,734 |
| item_npc_drop | 164,447 |
| item_object_drop | 26,334 |
| item_related_quest | 690 |
| item_set / item_set_bonus / item_set_item | 200 / 571 / 1,119 |
| item_stat | 18,471 |
| item_tooltip_line | 161,815 |
| item_vendor | 15,455 |
| meta | 16 |
| npc | 13,392 |
| npc_spawn | 124,486 |
| npc_waypoint | 23,015 |
| object_spawn | 53,600 |
| quest | 5,232 |
| quest_giver | 10,287 |
| quest_objective | 5,529 |
| quest_objective_point | 170 |
| quest_relation | 7,666 |
| quest_reward_item | 3,360 |
| quest_source_item | 1,391 |
| search_index | 47,584 |
| ui_asset | 12 |
| zone | 596 |
| zone_map | 57 |

## DB size

| | Bytes |
|---|---|
| Before (cd2795e, built in Task 5) | 67,129,344 |
| After (5a8bc0d) | 80,625,664 |
| Delta | +13,496,320 (+20.1 %) |

I measured the contributions by dropping each data set from a vacuumed scratch copy:

| Data set | Bytes |
|---|---|
| VMaNGOS `drop_rate_source` rows | 9,326,592 |
| `npc_waypoint` | 1,363,968 |
| The three craft tables | 724,992 |
| The rest (filled-in `drop_pct`/`pct_source` on item_npc_drop and boss_loot, statistics) | about 2.1 MB |

The README now says 81 MB, using the same decimal MB as before: 67,129,344 bytes was "67 MB".

## Facts pinned and the choices made

- **NPC 550 Defias Messenger.** It has one route of 171 points, all in zone 40, Westfall. `NpcRepository.routes(550)`
  returns that single route with `loop = true`, as Task 7 reported.
- **The first non-loop NPC is 412, Stitches.** It is the lowest npc_id with a route that is not a loop. It has one
  route of 161 points in zone 10, Duskwood, and the route ends 48.0 map units from its start. Plan 3B's screenshot
  can use it.
  - Real data has 564 routes. 453 are loops and 111 are not.
  - Other long open routes include 2447 Narillasanz in Alterac Mountains (217 points, route 2), 10182 Rexxar in
    Desolace (167 points) and 844 Antonio Perelli in Duskwood (114 points).
  - The next open route by id is 812's in zone 44. It has 2 points, and a two-point route is never a loop.
- **Heavy Leather.** The craft is spell 20649, as in Task 8's fixture. It creates 1 of item 4234 from 5 Medium
  Leather (2319), at Leatherworking 125/135 trivial. It has no recipe item, since trainers teach it.
- **The chosen crafting item is Heavy Leather (4234).** One craft creates it and 133 crafts use it as a reagent.
  Medium Leather (2319) is a reagent of 91 crafts, 20649 among them.
- **taughtBy.** Pattern: Fine Leather Gloves (2408) teaches 2164, Fine Leather Gloves, which creates 2312 and has
  recipeItemIds [2408]. Prowler Steak (1225758) is taught by 238645 and 250394, and either id finds it. The two
  recipe items are not in the item table.
- **Hogger's claw (448, 1931).** drop_rate_source holds `questie-wowhead 83.9965 quest_only 0` and
  `vmangos 100.0 quest_only 1`. item_npc_drop holds `83.9965 questie-wowhead`, with disagrees 1 and source_count 2.
- **Headline sources, 164,447 rows.** These match Task 9's final run exactly.

  | Source | Rows |
  |---|---|
  | vmangos | 137,373 |
  | none | 17,548 |
  | questie-wowhead | 9,334 |
  | questie-correction | 106 |
  | atlasloot | 67 |
  | atlasloot-override | 19 |

  146,899 rows have a rate. 208 quest-only VMaNGOS rows headline, and every one has source_count 1.
  2,456 drop_rate_source rows are quest-only.
- **Orphans.** No npc_waypoint row names an absent NPC or zone, and no craft_reagent or craft_recipe_item row names
  an absent craft.

## Tests

The shipped-data tests are split, as the ledger asked. They grew by 10 tests and about 140 lines.

- **ShippedDataSupport.java (new, abstract).** It holds the installer `@BeforeAll`/`@AfterAll` and the shared
  helpers `scalar`, `column`, `longs`, `rows`, `count`, `committedReport` and `reportCounter`.
  - `scalar` now asserts `rs.next()`, which fixes the Task 5 minor "rs.next() result ignored".
  - The state is static. That is safe because the build configures no parallel execution, and the Javadoc says so.
  - Each class extracts the DB once, about 0.2 to 0.4 s per class.
- **ShippedDataTest (19 tests, 574 to 316 lines).** The quest section and the helpers moved out unchanged, and 12
  unused imports are gone.
  - `knownFacts` changed for Eye of Sulfuras (17204) from Ragnaros (11502). The headline is now VMaNGOS at 3.0 %,
    with disagrees set, and the AtlasLoot row of 8.05 % is still asserted in drop_rate_source. See Concerns 1.
- **ShippedQuestDataTest (14 tests, new).** It holds the 11 quest tests from Task 5, moved verbatim, and the
  `prerequisiteClosure` helper. It adds three tests:
  - `defiasMessengerPatrolsWestfall`: at least 30 rows for 550 in zone 40, and `routes(550)` is a single loop route
    in zone 40 with at least 30 points.
  - `stitchesWalksAnOpenRoute`: `routes(412)` is a single route in zone 10, not a loop, with at least 100 points.
    `ZoneRepository.routesInZone(10)` holds 412's route and at least one loop.
  - `committedImportReportShowsTheWaypointCounters`: `npc.waypoints.npcs` equals the distinct NPCs in npc_waypoint
    and is over 400. `npc.waypoints.points` equals the row count. No route names an absent NPC.
- **ShippedProvenanceDataTest (7 tests, new).**
  - `heavyLeatherIsMadeFromMediumLeather`: `createdBy(4234)` is the single craft 20649, Leatherworking, yield "1",
    no recipe item, and one reagent of 5 × 2319 Medium Leather.
  - `heavyLeatherIsAReagentOfOverAHundredCrafts`: `reagentFor(4234)` equals the SQL count and is at least 100, and
    each craft lists 4234. `reagentFor(2319)` contains 20649.
  - `recipeItemsTeachTheirCrafts`: craft_recipe_item has over 1,500 rows. `taughtBy(2408)` is 2164 Fine Leather
    Gloves. `taughtBy` on 238645 and on 250394 finds Prowler Steak with both recipe ids.
  - `committedImportReportShowsTheCraftCounters`: crafts, reagents and recipeItems equal the table counts,
    rowsWithoutSpell is 0, and no `clientdata.crafts.skipped` line exists.
  - `hoggersClawKeepsTheObservedRate`: both drop_rate_source rows with their quest_only values, and the
    item_npc_drop headline, 83.9965 questie-wowhead with disagrees 1 and source_count 2.
  - `vmangosHeadlinesMostDrops`: loose bounds of vmangos 120k to 150k, no rate 10k to 25k and questie-wowhead 5k to
    15k. No quest-only VMaNGOS headline has source_count over 1. Over 1,000 rows are quest-only. The meta
    serverdb_asset and serverdb_commit are checked.
  - `committedImportReportShowsTheServerDbCounters`: serverdb.skipped is 0, loot.rows is over 200,000,
    unmatched.npcs is at most 10, and `provenance.sources.vmangos` equals the vmangos row count.
- **Readers on the shipped DB.** All four now return real rows, each with at least one assertion:
  `NpcRepository.routes`, `ItemRepository.createdBy`, `reagentFor` and `taughtBy`. `ZoneRepository.routesInZone` is
  covered too.

`./mvnw -B -q verify` exits 0. The surefire totals:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 415 | 0 | 0 | 0 |
| app | 269 (was 259) | 0 | 0 | 0 |

The shipped-data classes are ShippedDataTest 19, ShippedQuestDataTest 14 and ShippedProvenanceDataTest 7, which
makes 40, up from 30. Pytest was not run, because tools/ is unchanged.

TDD note: these are data pins on a regenerated artifact. The first run of the new classes was green. The one red
was the existing `knownFacts` pin, which is covered under Concerns 1.

## README (befc422)

- **Rebuild the data.** The intro names VMaNGOS as the third pinned source. A new list covers patrol routes,
  crafting (with the `clientdata.crafts.skipped` behaviour) and the VMaNGOS rates and their precedence, and notes
  that the browser does not show these yet.
- **Commands.** Both import commands, in "Rebuild the data" and in the client-data section, now pass
  `--server-db-dir ~/.cache/wowforever-serverdb/4641790`.
- **Headline counts.** The table gains npc_waypoint, craft, craft_reagent, craft_recipe_item and drop_rate_source.
- **Counters.** The counters sentence adds `npc.waypoints.*`, `clientdata.crafts.*` and `serverdb.*`.
- **Sizes.** The fetch cache is 50 tables and 219 MB, and `out/current` is 59 MB, both measured. The shipped
  database is 81 MB with the breakdown above. The tests section says 81 MB, and its bullet adds patrol routes,
  crafting and the VMaNGOS rates.
- **Known limitations.** "Crafting, PvP and faction vendors are not imported" becomes "PvP and faction vendors are
  not imported", plus a sentence that crafting and patrol routes are in the database but not shown yet.
  "Drop percentages exist for about 11,000 of the 164,447 drop rows" becomes about 146,900 of them, about 137,400
  from VMaNGOS, and calls those estimates for Forever's reworked loot.
- **NOTICE.** Task 9 already credits VMaNGOS, and the two copies are identical (MainFrameTest checks this), so
  NOTICE is unchanged.

## Self-review

I read the diff once. The moved quest tests are byte-identical apart from the class they live in. One moved line
is 121 characters; I left it as it was. All three new or changed test files are free of unused imports. The test
classes keep `@Tag("shipped-data")`, so `-DexcludedGroups=shipped-data` still skips all three. The scratch files,
such as the query scripts and logs, live in the scratchpad only.

## Concerns

1. **A user-visible headline changed: Eye of Sulfuras from Ragnaros.** Under Task 9's ruled precedence
   (Wowhead, then VMaNGOS, then AtlasLoot), this drop now shows VMaNGOS 3.0 % instead of AtlasLoot's 8.05 %, with
   the disagreement flag.
   - The v0.1 pin `isBetween(7.5, 8.6)` in `knownFacts` failed, and I updated it to the ruled behaviour.
   - This is general: `vmangos vs atlasloot: 1436 disagreeing of 2034 shared`. Wherever Questie has no rate and both
     AtlasLoot and VMaNGOS rate a pair, the VMaNGOS number now headlines.
   - If boss loot should prefer AtlasLoot, that is a precedence change for a later task.
2. **`serverdb.skipped` is reported as 0, not absent.** The brief said "serverdb.skipped absent". The importer
   registers the counter up front and writes 0 when the server DB was read. The test asserts 0.
