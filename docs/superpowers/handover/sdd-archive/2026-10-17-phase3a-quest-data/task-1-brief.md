### Task 1: Schema v3 and the quest raw-field reader

**Files:**
- Modify: `importer/src/main/resources/dev/wowforever/importer/schema.sql` (add `quest_objective`, `quest_objective_point`, `quest_source_item`, `quest_relation`, `quest` columns `repeatable, required_skill_id, required_skill_value, required_min_rep_faction, required_min_rep_value, sort_label`, the four indexes from the spec, `ui_asset(name TEXT PRIMARY KEY, png BLOB NOT NULL)`), `importer/.../sqlite/SqliteWriter` (DDL version bump), `importer/.../questie/RawEntity` (+ accessors for nested tables), `importer/.../report/UiFields` (QUEST adds the new fields).
- Test: `SchemaTest.v3TablesExist`, `RawEntityTest.questNestedTables` on fixture quest 176.

**Interfaces:**
- Produces: `RawEntity.nested(String key, int... path) -> List<LuaValue>`; schema tables as named in the spec.

- [ ] Write failing tests; implement; run `./mvnw -B -q -pl importer -am test`; commit `feat(importer): schema v3 for quest objectives, relations, source items and ui assets`.

