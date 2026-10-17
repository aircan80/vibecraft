Approved — spec ✅, quality ✅ (0 Critical, 0 Important, 4 Minor, 2 Nit)

# Task 17 review: client build 1.60.1.70450 and the four Forever dungeons (commit 5c27b3f)

Reviewed read-only: the brief, the PM memo, the implementer's report, the full diff c49470c..5c27b3f, the committed
files via `git show 5c27b3f:<path>`, and the shipped database at 5c27b3f and at c49470c (both opened with the tools
venv Python, `?mode=ro&immutable=1`). Maven was not run; the verify on 5c27b3f was reported green by the controller
(lua-table 61, importer 431, app 867, pytest 82).

## Spec compliance: ✅

| Requirement | Result |
|---|---|
| `schema.sql` v4 columns: `source TEXT NOT NULL DEFAULT 'atlasloot'`, `client_map_id`, `tuned_min_level`, `bosses_state`, `loot_state` | ✅ appended after `zone_id`; shipped `pragma table_info(instance)` matches |
| `SqliteWriter.SCHEMA_VERSION = 4` | ✅ shipped `meta.schema_version = 4` |
| Loader: `InstanceType 1`, `ExpansionID 0`, name join lower-cased with leading "the " removed | ✅ `ClientInstanceLoader.java:108-120`, `joinKey` at `:174` |
| Tuned level from `ContentTuning.MinLevelSquish`, null when 0 or absent, counted `noTuning` | ✅ `:192-197`, tested with MinLevelSquish 0 and ContentTuningID 0 |
| Encounters: `DifficultyID 0`, ordered by `OrderIndex` then `ID` | ✅ `:199-214`; the tie and heroic cases are tested |
| Writer rows: key `client:<mapId>`, Map name, `map_id = client_map_id = mapId`, content type, `level_min = tuned`, `level_max NULL`, zone, `source 'client'`, `bosses_state 'client_named'`, `loot_state 'none'` | ✅ shipped rows 34–37 checked column by column. `'DUNGEON'` and the map-name zone fallback follow controller rulings |
| Bosses: `npc_id`/`level` NULL, `is_extra_list 0`, `sort_order` from 1, no `boss_loot` | ✅ 24 shipped boss rows (ids 357–380), 0 client boss_loot rows |
| AtlasLoot rows `atlasloot/atlasloot/atlasloot` | ✅ 33 rows, `client_map_id` and `tuned_min_level` NULL |
| Counters `instances 4`, `encounters 24`, `unmatched`, `noTuning 0`, `skipped` | ✅ in `import-report.txt`; `skipped` is absent unless skipped, the same convention as `clientdata.crafts.skipped` |
| Every named test present | ✅ `fourDungeonsWithTheirEncountersInOrder`, `theArticleDoesNotBreakTheJoin`, `missingTablesSkipTheLoader`, `SchemaTest.v4InstanceColumns`, `InstanceRepositoryTest.clientInstanceCarriesItsState`, `ShippedDataTest.foreverDungeonsAreShipped`, `ShippedProvenanceDataTest.clientBuildIsCurrent` |
| `build.lock` at 1.60.1.70450 with the three new tables, Era fallback and UI icons | ✅ 51 + 2 Era tables, `fallback_build 1.15.9.70003`, six `ui_icons` all "first", `file_count 6313`, `missing_count 0` |
| Shipped facts | ✅ client instances exactly {2959, 2998, 2999, 3065}; 2959 has 9 bosses, first "Arcane Anomaly", last "Mana Wraith", tuned 28; 3065 first "Faldrim Anvilmar"; all four in `search_index` |
| Meta | ✅ `client_build 1.60.1.70450`, `client_item_count 20492`, `schema_version 4` |

### Specific checks

- **"No item, quest or drop row changed".** Confirmed, and more strongly than claimed. An `EXCEPT` both ways over the
  shared columns of all 41 tables, search_index excepted, finds differences only in `instance` (4 new rows),
  `boss` (24 new rows) and `meta`. The meta changes are `build_id`, `built_at`, `schema_version`, `client_build` and
  `client_fetched_at`. The old and new counts of item 21,543, quest 5,232, item_npc_drop 164,447, drop_rate_source
  158,244, item_stat, item_tooltip_line, icon, npc, zone, zone_map and craft are equal.
- **AtlasLoot rows untouched, Classic dungeons not duplicated.** The 33 AtlasLoot instance rows equal the old ones on
  every old column, and the 356 AtlasLoot boss rows are identical. `boss_loot` is still 2,886. The 15 Classic
  dungeons that LFGDungeons also names are excluded through AtlasLoot's `InstanceID`
  (`clientdata.instances.atlasloot = 18`). Only the four Forever maps are client rows.
- **Reproducible refresh.** The README's three commands carry `B=1.60.1.70450`, the Era fallback flags, `--maps`,
  `--ui-icons` and the `needed_icons.py` step. The text says the first fetch without `--fdids` precedes
  `needed_icons.py` and that `build.lock` is copied after the import. A future refresh is one build bump plus the
  commands. No hotfix step was added, as expected.
- **WoW installation and network.** The Python change only adds three table names. The tools still read wago.tools.
  The app gains no network code.
- **Shipped test pins.** `count("instance") == 33` became "AtlasLoot = 33 exactly, all ≥ 37". The Deadmines test
  filters AtlasLoot rows and keeps `hasSize(33)`. `schemaVersionIsThree` became "≥ 3", and the exact 4 moved to
  `clientBuildIsCurrent`. `client_item_count` is a floor. No fact about a named entity was loosened.
- **InstanceSummary callers.** `InstanceRepository.summary` reads the four columns. `ZoneRepository` reuses
  `SUMMARY_SELECT` and the mapper. Test-only callers use the kept 7- and 8-argument constructors, which default to
  AtlasLoot.
- **Search.** The index takes every instance name. Each client dungeon's zone holds exactly that one instance, so a
  zone hit merges into it like a Classic dungeon's. Exact-name matches still rank first, so "Dalaran" and "Wetlands"
  keep their zones on top.
- **Fixture database.** `FixtureDb` runs the same `ImportPipeline` on the importer fixtures, so the app's fixture
  database has the v4 schema and the four client dungeons.

## Code quality: ✅

The loader is small and documented, and it follows the optional-table pattern of `CraftLoader`. The writer
continues both id sequences without coupling to AtlasWriter's counters. The fixture rows are byte-for-byte client
rows, the tests cover order ties, other difficulties, missing columns, missing tables and the zone tie-break, and
the one-commit choice is justified.

### Minor

1. **Client dungeons read as an exact level outside the planned Task 18 copy.**
   `app/src/main/java/dev/wowforever/app/ui/Labels.java:95` formats `levels(28, null)` as "Level 28". The search
   subtitle (`SearchRepository.instanceSubtitle`), the zone page's dungeon list and the current list page therefore
   say "Level 28" for City of Dalaran. The PM memo asks for "Level 28+": the tuned value is a floor, not a band.
   The Task 18 plan names the list cell and the page chip only. Fix in Task 18: route every instance level label
   through the tuned-level formatter when `source = 'client'`, including the search subtitle and the zone page. Add
   it to Task 18's test list. No change is needed in Task 17.
2. **Dire Maul escapes duplication only because its LFGDungeons names differ.**
   `importer/src/main/java/dev/wowforever/importer/ImportPipeline.java:123` collects AtlasLoot `InstanceID`s, and
   AtlasLoot has none for Dire Maul. Map 429 "Dire Maul" stays out only because LFGDungeons 70450 names the wings
   "Dire Maul - East/West/North". A later build that adds an LFGDungeons "Dire Maul" row would import it as a second
   Dire Maul with no loot. `foreverDungeonsAreShipped` pins the exact id set, so a regeneration would fail loudly
   rather than ship it. Fix, optional now: add 429 to the exclusion set, or exclude a candidate whose name equals an
   AtlasLoot instance name. Record it either way in the tools README "Dungeon tables" section.
3. **The docs omit three of the rules and counters the report relies on.** `tools/clientdata/README.md:170`
   ("Dungeon tables") does not mention:
   - the zone link rule: area name first, then map name, a `dungeon` zone first, then the lowest id;
   - the `clientdata.instances.atlasloot` and `clientdata.instances.unmatched` counters;
   - the unmatched note that lists the 16 maps.

   The root `README.md:168` counter paragraph names `clientdata.crafts.*` but not `clientdata.instances.*`. Fix: add
   one sentence to each, so the next refresh can tell an expected 16 from a regression.
4. **The client map id lives in two columns, depending on the source.** For AtlasLoot rows the client map id is in
   `instance_id` and `client_map_id` is NULL. For client rows `instance_id` is NULL and the id is in `client_map_id`
   and `map_id`. `map_id` holds an AreaTable id for AtlasLoot rows. This follows the brief, and the report flags the
   `map_id` part. v0.4.1's generalised instance pages will need one way to find "the client map of an instance".
   Fix, optional now: also fill `client_map_id` from AtlasLoot's `InstanceID`, or document in `schema.sql` which
   column holds the client map id per source.

### Nit

1. `importer/src/main/java/dev/wowforever/importer/sqlite/AtlasWriter.java:21`: a 172-column Javadoc line was not
   reflowed. `ClientInstanceWriter.java:66` and `ClientInstanceLoader.java:42` are 121 columns.
2. `importer/src/test/java/dev/wowforever/importer/clientdata/ClientInstanceLoaderTest.java:96` splits the literal
   as `"Hall of" + " Thanes"`, which reads like a formatter artefact. Write `"Hall of Thanes"` on the next line.

## Notes for the controller

- The report's concerns are accurate. Task 18 must assume 16 unmatched maps, and client dungeon pages currently
  show bosses with an empty loot pane and no state copy.
- The `serverdb.invalid.*` and `serverdb.references.maxcountZero` report lines come from code already on the
  branch. They are all 0 and do not change any row, as the content comparison above confirms.
