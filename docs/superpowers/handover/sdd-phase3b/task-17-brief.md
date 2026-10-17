### Task 17: Client data 70450 and the Forever dungeons (data)

Added 2026-10-17 as an owner-bug exception to backlog decision 7 (frozen v0.3.0 scope): the owner reported that the new WoW: Forever dungeons do not appear and asked whether the data is current. Facts verified on wago.tools (builds 1.60.1.70425 and 1.60.1.70450, identical for these tables): `Map` rows with `InstanceType = 1` and `ExpansionID = 0` for City of Dalaran 2959, Excavation Site: Wetlands 2998, Ruins of Lordaeron 2999 and The Hall of Thanes 3065; `LFGDungeons` 3271 "City of Dalaran" (ContentTuningID 4512), 3272 "Ruins of Lordaeron" (5256), 3273 "Hall of Thanes" (7000), 3274 "Excavation Site: Wetlands" (6874), all with `MapID = 0`; `ContentTuning.MinLevelSquish` 4512 → 28, 5256 → 15, 7000 → 13, 6874 → 26 (`MaxLevelSquish` equal, `LfgMin/MaxLevel` 0 or inconsistent — ignore them); `DungeonEncounter` (columns `Name_lang,ID,MapID,DifficultyID,OrderIndex,CompleteWorldStateID,Bit,Flags,SpellIconFileID,Faction,ItemSequenceLevel`) names 9 bosses for 2959, 4 for 2998, 7 for 2999 and 4 for 3065, all `DifficultyID 0`; map 3002 has encounters but no name (excluded). No loot exists client-side. The installed client is 1.60.1.70450. Runs after Task 5 and before Task 6 so later shipped-data tests and screenshots pin final data.

**Files:**
- Modify `tools/clientdata/fetch.py` / `convert.py` table lists (+ `DungeonEncounter`, `LFGDungeons`, `ContentTuning`; `Map` is already fetched), `tools/clientdata/build.lock` (build `1.60.1.70450`), `tools/clientdata/README.md`.
- Modify `importer/src/main/resources/dev/wowforever/importer/schema.sql` (`instance` + `source TEXT NOT NULL DEFAULT 'atlasloot'`, `client_map_id INTEGER`, `tuned_min_level INTEGER`, `bosses_state TEXT`, `loot_state TEXT`), `SqliteWriter.SCHEMA_VERSION = 4`.
- Create `importer/src/main/java/dev/wowforever/importer/clientdata/ClientInstanceLoader.java` and `ClientInstance` / `ClientEncounter` records; modify `ImportPipeline` (after the AtlasLoot instances; ids continue the AtlasLoot sequence), `AtlasWriter` or a new `ClientInstanceWriter`, `report/UiFields` if instances are UI-field checked.
- Modify `app/src/main/java/dev/wowforever/app/model/InstanceSummary.java` (+ `String source`, `Integer tunedMinLevel`, `String bossesState`, `String lootState`), `app/.../db/InstanceRepository.java` and `SearchRepository` (client instances are searchable like AtlasLoot ones).
- Regenerate `app/src/main/resources/data/wowforever.sqlite`, `build-id.txt`, `import-report.txt`.
- Tests: create `importer/.../clientdata/ClientInstanceLoaderTest.java` (fixture CSVs with the four maps, the 3002 unnamed map, one LFGDungeons row without a matching map); modify `SchemaTest` (v4 columns), `InstanceRepositoryTest`, `ShippedDataTest`, `ShippedProvenanceDataTest`.

**Interfaces:**
- Consumes: the client CSVs in `out/current/csv/`; the optional-table pattern from 3A Task 8 (`optionalTable`: absent tables → the loader is skipped with `clientdata.instances.skipped = 1` and a note).
- Produces:
  - `ClientInstanceLoader.load(Path csvDir, ImportReport) -> List<ClientInstance>` with `record ClientInstance(long mapId, String name, Integer tunedMinLevel, Integer areaTableId, List<ClientEncounter> encounters)` and `record ClientEncounter(long id, String name, int orderIndex)`.
    - A `Map` row qualifies when `InstanceType = 1`, `ExpansionID = 0` and its `MapName_lang` equals an `LFGDungeons.Name_lang` after both are lower-cased and a leading `"the "` is removed. Unmatched dungeon maps are counted as `clientdata.instances.unmatched` (3002 today).
    - `tunedMinLevel` = `ContentTuning.MinLevelSquish` of the LFG row's `ContentTuningID`; null when 0 or absent (counted `clientdata.instances.noTuning`).
    - Encounters: every `DungeonEncounter` row with that `MapID` and `DifficultyID = 0`, sorted by `OrderIndex` then `ID`.
  - Writer: one `instance` row per `ClientInstance` with `key = "client:" + mapId`, `name` from `Map.MapName_lang` (so "The Hall of Thanes"), `map_id = mapId`, `content_type = 'dungeon'`, `level_min = tunedMinLevel`, `level_max = NULL`, `zone_id` = the AtlasLoot/Questie zone whose name equals the Map's `AreaTableID` area name when one exists, else NULL; `source = 'client'`, `client_map_id = mapId`, `tuned_min_level`, `bosses_state = 'client_named'`, `loot_state = 'none'`. One `boss` row per encounter: `name`, `npc_id NULL`, `level NULL`, `is_extra_list 0`, `sort_order` = position from 1. No `boss_loot`. AtlasLoot rows keep `source = 'atlasloot'`, `bosses_state = 'atlasloot'`, `loot_state = 'atlasloot'`.
  - Report counters: `clientdata.instances` (4), `clientdata.encounters` (24), `clientdata.instances.unmatched`, `clientdata.instances.noTuning`, `clientdata.instances.skipped`.
  - `InstanceSummary` carries the four new fields; `InstanceRepository.list/summary` read them.

- [ ] **Step 1: failing tests.**
  - `ClientInstanceLoaderTest.fourDungeonsWithTheirEncountersInOrder`: the fixture gives 4 instances; 2959 has 9 encounters starting "Arcane Anomaly" and ending "Mana Wraith"; 2998's order is Saltspine, Shadetooth, Highland Horror, Relic Guardian (OrderIndex 0, 1000, 1500, 2000); 3065 has `tunedMinLevel 13`; the unnamed map 3002 is counted unmatched, not loaded.
  - `ClientInstanceLoaderTest.theArticleDoesNotBreakTheJoin`: `Map` "The Hall of Thanes" matches LFG "Hall of Thanes".
  - `ClientInstanceLoaderTest.missingTablesSkipTheLoader`: a copy of the fixture without `DungeonEncounter.csv` yields no instances and `clientdata.instances.skipped = 1`.
  - `SchemaTest.v4InstanceColumns`.
  - `InstanceRepositoryTest.clientInstanceCarriesItsState` on the fixture DB.
  - `ShippedDataTest.foreverDungeonsAreShipped`: `instance` rows with `source = 'client'` are exactly the client map ids {2959, 2998, 2999, 3065}; 2959 has 9 bosses, `tuned_min_level 28`, first boss "Arcane Anomaly"; 3065's first boss is "Faldrim Anvilmar"; none has `boss_loot`; each is found by `SearchRepository` by name.
  - `ShippedProvenanceDataTest.clientBuildIsCurrent`: `meta.client_build = "1.60.1.70450"`, `client_item_count ≥ 20492` (a floor, not an equality), `schema_version = 4`.
- [ ] **Step 2:** run the importer tests and expect FAIL; implement; run again and expect PASS.
- [ ] **Step 3: refresh and regenerate.** Bump `build.lock` to `1.60.1.70450`, fetch every table (plus the three new ones, the Era fallback and UI icons as the README describes), convert, run `./mvnw -B -pl importer -am -Pimport-data verify`, copy `build.lock`, update `README.md` (build line, "Dungeons & raids" sentence) and `tools/clientdata/README.md`. Record in the report every shipped count that changed (items, quests, drop rows) and the import-report deltas.
- [ ] **Step 4:** full `./mvnw -B -q verify`; fix shipped tests that pinned totals by turning them into floors or ids (never by loosening a fact about a named entity).
- [ ] **Step 5:** commit `data: client build 1.60.1.70450 and the four Forever dungeons from Map, LFGDungeons and DungeonEncounter`.

