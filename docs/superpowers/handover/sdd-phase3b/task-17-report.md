# Task 17 report: client build 1.60.1.70450 and the Forever dungeons

Status: DONE_WITH_CONCERNS (two rules of the brief had to be adjusted to reach its stated result; see "Choices")
Commit: `5c27b3f` data: client build 1.60.1.70450 and the four Forever dungeons from Map, LFGDungeons and
DungeonEncounter (parent `05a63a0`; trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`). Not pushed.

One commit, not two: the app's instance query reads the v4 columns, so a code-only commit fails the shipped tests
against the old database.

## What was built

- **Python tools.** `LFGDungeons`, `ContentTuning` and `DungeonEncounter` joined `REQUIRED_TABLES` (48 to 51).
  `convert.py` copies every cached CSV, so it needed no change.
- **Importer.**
  - New `ClientInstanceLoader`, records `ClientInstance`, `ClientEncounter` and `ClientInstances` (instances plus
    zone ids).
  - New `ClientInstanceWriter`. It appends after the AtlasLoot rows, ids continuing both sequences.
  - `ClientDataBundle` gained `instances`, with a 9-argument constructor kept. `ClientDataStage.run` gained an
    overload taking the AtlasLoot map ids. `ImportPipeline` passes AtlasLoot's `InstanceID`s.
  - `AtlasWriter` writes `source`, `bosses_state` and `loot_state = 'atlasloot'`.
  - `schema.sql` adds the five columns and `SCHEMA_VERSION` is 4.
- **App.**
  - `InstanceSummary` gained `source`, `tunedMinLevel`, `bossesState`, `lootState` and constants. The old 8- and
    7-argument constructors build AtlasLoot summaries.
  - `InstanceRepository` reads the columns; `ZoneRepository` shares its select.
  - `SearchRepository` needed no change: the search index already takes every `instance` name.
  - `Labels.instanceWing` returns null for a `client:` key. Without it, list, search and page titles read
    "The Hall of Thanes › client 3065".
- **Fixture.** Rows copied byte for byte from the 70450 cache (identical files in 70425):
  - Map 36, 2959, 2998, 2999 and 3065.
  - LFGDungeons 1 (Wailing Caverns, no map in the fixture), 5, and 3271 to 3274.
  - Six ContentTuning rows.
  - Every DungeonEncounter row of maps 36, 2959, 2998, 2999, 3002 and 3065. Map 3002 has no Map row in either build.
  - AreaTable row 16544 from the 70425 cache.
- **Docs.** README: build lines, a dungeon data paragraph, self-test example, sizes. Tools README: "Dungeon tables"
  section, fixture paragraph, build references. NOTICE (both copies): the new build.

## Choices the brief left open or got wrong

1. **The join alone loads 19 dungeons, not 4.** LFGDungeons 70450 lists the Classic dungeons too: Deadmines,
   Shadowfang Keep, Wailing Caverns and others. 15 more Map rows match by name.
   - AtlasLoot's `InstanceID` is the client map id for every Classic dungeon, so a dungeon map whose id is an
     AtlasLoot `InstanceID` stays AtlasLoot's.
   - Counter `clientdata.instances.atlasloot` is 18 in the shipped build. The loader takes the id set:
     `load(ClientDataSource, Set<Long> atlasLootMapIds, ImportReport)`.
2. **`unmatched` cannot be "3002 only".** 3002 has no Map row, and 15 other dungeon maps are neither AtlasLoot's nor
   named in LFGDungeons: test maps, Season of Discovery leftovers, Dire Maul 429 (AtlasLoot has no InstanceID for it).
   - Rule: dungeon Map rows plus encounter-only maps, minus AtlasLoot's, without a name match.
   - Shipped value: 16, all named in a report note. Fixture value: 1 (3002).
3. **Zone link.** The brief's rule (the zone named like the Map's `AreaTableID` area) links only Dalaran: the other
   three have AreaTableID 0.
   - Questie has dungeon zones named exactly like the maps, so the area name is tried first, then the map name. A
     `dungeon` zone wins a tie, then the lowest id.
   - Shipped links: 2959 to 16544, 2998 to 16732, 2999 to 16611, 3065 to 16919.
   - In the fixture, 16732 is named from its enum symbol without the colon, so 2998 stays unlinked there.
4. **`content_type`.** It is `'DUNGEON'`, AtlasLoot's word, not the brief's `'dungeon'`, so the column keeps one
   vocabulary. `Labels.contentType` reads either.
5. **Loader signature.** It takes a `ClientDataSource`, like `CraftLoader`, not a `Path`.
6. **`map_id`** holds the client map id for client rows, as the brief says. For AtlasLoot rows it is an AreaTable id.
   No code reads `instance.map_id` today.

## Red to green

- Python: `test_fetch.py` failed on 48 vs 51 and the dungeon-table test (2 failed, 40 passed). Green after
  `tables.py`; the whole tools suite is 82 passed.
- Importer: `ClientInstanceLoaderTest`, `SchemaTest.v4InstanceColumns` and
  `ImportPipelineTest.theForeverDungeonsFollowTheAtlasLootInstances` failed to compile before the loader existed.
  Green after: importer 431 tests. One fixture expectation changed: 2998 has no zone in the fixture (choice 3).
- App, non-shipped: `InstanceRepositoryTest.clientInstanceCarriesItsState` and `LabelsTest` went green. Two tests
  pinned fixture totals and were updated:
  - `MainSelfTestTest`: instances 3 to 7.
  - `ListPageTest`: level bands, now 4 in 1–20, 2 in 21–40 and 1 unknown, with the names pinned.
- App, shipped, against the old database: 46 run, 1 failure (`clientBuildIsCurrent`), 5 errors (no `source`
  column). Green after the regeneration.
- Shipped pins changed:
  - `minimumCounts` and the Deadmines test now pin AtlasLoot instances = 33 exactly, and all instances ≥ 37.
  - `ShippedQuestDataTest.schemaVersionIsThree` became `schemaVersionHasTheQuestTables` (≥ 3). The exact 4 is pinned
    in `clientBuildIsCurrent`.

Twice, `target` held classes with "Unresolved compilation problems" written by an IDE's Eclipse compiler during
editing. `./mvnw clean` cleared them; the source compiled with javac throughout.

## Refresh log

- `fetch.py --build 1.60.1.70450 --fallback-product wow_classic_era --fallback-build 1.15.9.70003 --maps --ui-icons`
  cached 51 tables, the Era fallback and 3,392 of 3,392 files. All six UI icons resolved to their first choice, the
  same fdids as 70425.
- `needed_icons.py` (70450) listed 2,925 fdids, the same set as with 70425. `fetch.py --fdids` cached 2,925 of 2,925
  files, 6,313 in all, 0 missing.
- Run through a wrapper adding `tools/clientdata` to `sys.path`, because `python -I` drops the script directory. No
  User-Agent contact was set, as in earlier runs, so the tool sent its placeholder.
- Tables changed against 70425: Item, ItemEffect, ItemNameDescription, ItemSparse, ItemXItemEffect, SkillLineAbility,
  Spell, SpellAuraOptions, SpellEffect, SpellMisc and SpellName. 39 tables are identical, Map included.
- LFGDungeons, ContentTuning and DungeonEncounter of 70425 were fetched into a scratch cache: byte-identical to 70450.
- `convert.py` wrote a fresh directory: 2,924 icons, 1 failed (4559256, BLP encoding 3, as before), 60 maps, 6 UI
  icons.
  - The old `out/current` is kept as `out/1.60.1.70425`.
  - The output is 67 MB, not 59: the old one lacked `ManifestInterfaceData.csv` (8.8 MB), which had only been
    converted with `--ui-icons-only`.
- `./mvnw -B -pl importer -am -Pimport-data verify`: BUILD SUCCESS. `out/current/build.lock` was copied to
  `tools/clientdata/build.lock`. WoW installation untouched.

## Count deltas

| Counter or table | Before | After |
|---|---|---|
| table.instance | 33 | 37 |
| table.boss | 356 | 380 |
| table.search_index | 47,584 | 47,588 |
| clientdata.instances | absent | 4 |
| clientdata.encounters | absent | 24 |
| clientdata.instances.atlasloot | absent | 18 |
| clientdata.instances.unmatched | absent | 16 |
| clientdata.instances.noTuning | absent | 0 |
| meta client_build | 1.60.1.70425 | 1.60.1.70450 |
| meta schema_version | 3 | 4 |
| build_id | f35f4f21… | 6d4b8500… |
| DB bytes | 80,625,664 | 80,629,760 |

- **Unchanged.** Items (21,543; client data 20,492), quests (5,232), item_npc_drop (164,447), drop_rate_source
  (158,244), icons (2,925), zone maps (57) and every other count.
- **Content check.** `EXCEPT` both ways against the committed database finds no differing row in 20 tables, including
  item, item_stat, item_tooltip_line, item_effect, craft, icon, zone_map, quest, npc and the drop tables. The 70450
  table changes touch only items and spells outside the browser's item set.
- **Not from this task.** The report gains `serverdb.invalid.*` and `serverdb.references.maxcountZero` (all 0). They
  come from code already on the branch since the last regeneration.

## Verify

`./mvnw -B verify` on the final tree: lua-table 61, importer 431, app 867 tests, 0 failures, BUILD SUCCESS. Tools
pytest: 82 passed.

## Concerns

- Choices 1 to 3 change the brief's rules. The PM memo and Task 18 should assume 16 unmatched maps, not one.
- Task 18 owns the UI copy. Today a client dungeon page shows its bosses with no loot pane content and no state
  wording, and the list shows it like any other dungeon.
