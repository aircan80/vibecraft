# Task 1 report: Schema v3 and the quest raw-field reader

Status: DONE. Commit `4e19548` on `feature/phase3a-quest-data` (main checkout).

## Implemented
- **schema.sql** (additive only):
  - `quest` gains `repeatable INTEGER NOT NULL DEFAULT 0`, `required_skill_id`, `required_skill_value`,
    `required_min_rep_faction`, `required_min_rep_value` (INTEGER) and `sort_label TEXT`, appended after the v2 columns.
    EntityWriter's INSERT names its columns, so they stay NULL/0 until Task 3 fills them.
  - `quest_objective(quest_id, ordinal, kind, entity_id, text, credit_group)`: no count column, no primary key
    (killCredit members may share an ordinal; Task 2 decides).
  - `quest_objective_point(quest_id, ordinal, zone_id, x, y)`.
  - `quest_source_item(quest_id, item_id, kind)` with PRIMARY KEY (quest_id, item_id, kind).
  - `quest_relation(quest_id, related_id, kind)` with PRIMARY KEY (quest_id, related_id, kind). A duplicate insert
    fails, which backs up Task 3's dedupe (`duplicatesCollapse`).
  - `ui_asset(name TEXT PRIMARY KEY, png BLOB NOT NULL)`.
  - The four spec indexes: `idx_quest_relation_related(related_id)`, `idx_quest_objective_entity(entity_id, kind)`,
    `idx_quest_giver_entity_role(entity_id, kind, role)` and `idx_quest_source_item_item(item_id)`. The old
    `idx_quest_giver_entity(kind, entity_id)` stays.
  - Two extra lookup indexes the Task 6 readers need: `idx_quest_objective_quest(quest_id, ordinal)` and
    `idx_quest_objective_point_quest(quest_id, ordinal)`. The relation and source-item tables get quest_id lookups
    from their primary keys.
  - Each table has a comment that repeats the spec's kind vocabularies.
- **SqliteWriter**: no schema version constant existed. I added `public static final int SCHEMA_VERSION = 3` and
  write it as `meta.schema_version`.
  - The build id was confirmed and needed no change. `ImportPipeline.meta` hashes the source commits, the importer
    version, `SqliteWriter.schemaSql()` and `built_at`, so every regeneration gets a new id.
  - The app's `DatabaseInstaller` re-installs whenever that id differs from the embedded one.
- **RawEntity** (new file, `importer/src/main/java/dev/wowforever/importer/sqlite/RawEntity.java`, package-private).
  - It is `EntityWriter`'s former private `Row`, moved over unchanged, with the same missing-field bookkeeping via a
    shared set. EntityWriter now builds rows through `row(t, values)`.
  - The plan put it at `importer/.../questie/RawEntity`, but no `questie` package exists. `LuaColumns` is
    package-private in `sqlite`, and Tasks 2–3 put their writers there too, so it lives in `sqlite`.
  - New `List<LuaValue> nested(String field, int... path)` returns the array values (indices 1..n, holes kept as
    NIL) of the table at the 1-based path. A scalar leaf comes back as a one-element list.
  - `nested` returns an empty list for nil, a missing step, or an unresolved value; the unresolved case is counted
    `unresolved.<kind>.<field>`. A step through a non-table is counted `badValue.<kind>.<field>`.
  - New `Optional<LuaTable> nestedTable(String field, int... path)` serves hash-keyed tables such as the
    triggerEnd zone map. A non-table value at the end of the path is counted as a badValue.
- **UiFields**: QUEST adds objectives, triggerEnd, sourceItemId, requiredSourceItems, extraObjectives,
  preQuestGroup, preQuestSingle, childQuests, inGroupWith, exclusiveTo, nextQuestInChain, parentQuest,
  breadcrumbForQuestId, breadcrumbs, requiredSkill and requiredMinRep.
  - requiredMaxRep and reputationReward stay out because nothing stores them.
- **Fixture**: quest 176 "Wanted: \"Hogger\"" was added with its real row from the cached QuestieDB at commit
  49e7dbca.

## Tests (TDD)
- Red first: test compilation failed on the missing `RawEntity` and `SqliteWriter.SCHEMA_VERSION`.
- New `SchemaTest.v3TablesExist` checks exact column lists, the ui_asset primary key and NOT NULL, that the quest
  v2 columns come first and the v3 columns last, and that there is no count column. It also checks each index's
  columns through `pragma_index_info` and that `meta.schema_version` is 3.
- New `RawEntityTest` has four tests:
  - `questNestedTables` runs on fixture quest 176. It covers startedBy/finishedBy groups, objectives
    `{nil,nil,{{1931}}}` with positions kept, a scalar leaf, an index past the end, objectivesText,
    reputationReward, nil fields, and that no counters are touched.
  - `steppingThroughAScalarIsABadValue` checks the badValue counts.
  - `triggerEndZonesAreReachableAsATable` uses a two-zone synthetic triggerEnd.
  - `anUnresolvedFieldReadsAsEmptyAndIsCounted` checks the unresolved count.
- Updated `ImportReportTest`:
  - `uiFieldsFollowTheAcceptanceBar` now uses reputationReward as its non-UI quest field.
  - New `questFieldsOfThePhase3QuestPageAreUiFields`.
- Updated `CorrectionApplierTest.reportsUnresolvedValuesAndUnsupportedOps`: the skipped `objectives_add` is now a UI
  field, so the expected UI-skip count goes from 1 to 2. Excluding non-UI skips stays covered by
  `ImportReportTest.countsSkipsOnUiFieldsIncludingDroppedRowsAndLayers`.
- While iterating, one test of mine was wrong: a zone-keyed map is an integer-keyed sparse array. I corrected the
  test so `nested("triggerEnd", 2, 40)` reaches the zone's coordinate pairs.
- Results: `./mvnw -B -q -pl importer -am test` passes, with 342 importer tests run before the final additions.
  `./mvnw -B -q verify` exits 0.
  - The only console output is the existing era-overlap oracle printout from `EraOverlapOracleTest`, which this task
    did not change.

## Files changed
- importer/src/main/resources/dev/wowforever/importer/schema.sql
- importer/src/main/java/dev/wowforever/importer/sqlite/RawEntity.java (new)
- importer/src/main/java/dev/wowforever/importer/sqlite/EntityWriter.java
- importer/src/main/java/dev/wowforever/importer/sqlite/SqliteWriter.java
- importer/src/main/java/dev/wowforever/importer/report/UiFields.java
- importer/src/test/java/dev/wowforever/importer/sqlite/SchemaTest.java (new)
- importer/src/test/java/dev/wowforever/importer/sqlite/RawEntityTest.java (new)
- importer/src/test/java/dev/wowforever/importer/report/ImportReportTest.java
- importer/src/test/java/dev/wowforever/importer/corrections/CorrectionApplierTest.java
- importer/src/test/resources/fixtures/questiedb/data/Forever/foreverQuestDB.lua

## Self-review
- Every table, column and index in spec §3 is present with the spec's names. The changes are additive, and the app
  module's tests pass against the fixture DB that now includes quest 176.
- `RawEntity` is a pure move of `Row` plus the two accessors. The behaviour of items, npcs, objects and quests is
  unchanged, and all existing writer tests pass.
- The commit trailer is my own attribution line (Claude Opus 5.5), as the brief allows.

## Concerns / notes for later tasks
- In the real data, quest 176's objectives carry only the item objective, Huge Gnoll Claw 1931. There is no
  creature objective for Hogger 448. Task 5 expects one, so it may need to come from a correction layer or the
  expectation may need revising.
- The new UI fields make `--fail-on-unsupported` fail on skips in them, and on unresolved values once Tasks 2–3 read
  them. The shipped import report has no skips today, but Task 5's real import is where this will show.
- The `repeatable` column is `NOT NULL DEFAULT 0` rather than the spec's bare `INTEGER`, so it is never NULL.
- `quest_objective` has no primary key, because the credit_group shape is Task 2's decision.
