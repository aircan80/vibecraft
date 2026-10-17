# Task 2 report: quest objectives, points and source items

Status: DONE_WITH_CONCERNS (concerns are data-shape notes, not defects; see the last section)
Commit: 14b02e9 `feat(importer): quest objectives, exploration points and source items` on `feature/phase3a-quest-data`

## What was implemented
- `importer/src/main/java/dev/wowforever/importer/sqlite/QuestObjectiveWriter.java` (new). One responsibility: the three tables.
  - Ordinals are 0-based over creature (objectives[1]), object [2], item [3], reputation [4], killCredit [5], spell [6], then explore: the triggerEnd first, then each extraObjectives entry.
  - Creature/object/item/spell rows hold the entity id and the Questie text, or NULL when Questie has none.
  - A reputation row holds entity_id = the faction and text = the value as text, for example `576` / `42000`.
  - A killCredit group is one objective. It writes one `creature` row per distinct member at the group's ordinal, all with `credit_group` = that ordinal. The base creature comes first and is the only row carrying the group text. A base that is missing from the id list is still written first.
  - The spell objective's optional item is dropped, as ruled. It is not written to `quest_source_item`.
  - An explore row holds the triggerEnd text, or extraObjectives[3]. Points come from the triggerEnd zone map, or from the extra objective's spawnlist when it is a `{[zoneId] = {coords}}` map. A nil spawnlist means a text-only row. The zone maps are iterated with `entries()`, not `nested()`.
  - `sourceItemId` gives one `provided` row (0 is skipped). `requiredSourceItems` gives `required` rows, distinct through `LuaColumns.ids`. No duplicate can reach the primary key.
  - Counters: `quest.objective.kinds.<kind>` (rows per kind), `quest.objective.unknownKind` (an objectives key outside 1..6) and `quest.sourceItems` (rows). The last two are seeded at 0 so a clean run shows them.
  - Malformed values count `badValue.quest.<field>` through `LuaColumns`, with nothing written for them. This covers non-integer ids, non-table entries, bad coordinates, bad zone keys, non-string text, and an explore entry with neither text nor points.
  - Unresolved values count `unresolved.quest.<field>` once per field, because each field is read once through `RawEntity.nestedTable`/`scalar`/`ids`.
- `EntityWriter.quests` opens the writer in its try-with-resources and calls `write(id, r)` per quest, on the corrected `RawEntity`.

## Fixture ids used (copied byte-for-byte from QuestieDB 49e7dbca)
The real Forever quest DB has no killCredit, spell or extraObjectives rows and no two-zone triggerEnd. Those shapes exist only in Questie's correction files. So I copied the real data rows into `foreverQuestDB.lua` and the real correction entries into the fixture `legacy/classicQuestFixes.lua`. The import applies them exactly as it does on the real data.

| Quest | Data row | Correction entry | Shape exercised |
|---|---|---|---|
| 176 Wanted: "Hogger" | already present (Task 1) | none | item objective |
| 532 Battle of Hillsbrad | added | none | creature, creature, object, item in order |
| 452 Pyrewood Ambush | added | added (classicQuestFixes:341) | killCredit group of 9, base 2060 |
| 5561 Kodo Roundup | added | added (classicQuestFixes:3476) | killCredit {4700,4701,4702} replacing the raw creature objective; extraObjectives with a plain Desolace zone map (one point); sourceItemId 13892 |
| 8121 Take Four Bases | added | added (classicQuestFixes:4772) | triggerEnd in four zones (1638, 45, 1637, 130) |
| 1136 Frostmaw | added | added (classicQuestFixes:1075) | sourceItemId 5838 + requiredSourceItems {5810}; text-only extra objective |
| 8485 The Brokering of Peace | added | none (its real fix only touches preQuestSingle) | reputation objective {576, 42000}; sourceItemId 21155 |
| 9422 (existing) | existing | existing | creature + text-only extra "Silithyst" |

There is no real spell objective on a quest present in the data. Spell objectives appear only in Forever corrections for quests created by the generated base, such as 92474 and 97979. The spell test therefore sets 97979's real value `{nil,nil,nil,nil,nil,{{1259799},{20580}}}` on a fixture quest, plus a synthetic item to prove that the item is dropped.

## Tests
`importer/src/test/java/dev/wowforever/importer/sqlite/QuestObjectiveWriterTest.java` (10 tests). It builds the fixture tables after all 18 Forever correction layers, the same way `ImportPipeline` does, and writes them through `SqliteWriter`:
- `creatureAndItemObjectivesInOrder` (532, 176, 9422)
- `killCreditGroupSharesOneGroup` (452, 5561 with its explore point)
- `killCreditBaseOutsideItsListComesFirstWithTheGroupText`
- `explorePointsKeepTheirZone` (8121 with four zones; 1136 and 9422 text-only extras)
- `providedAndRequiredItems` (1136, 2, 5561, 176; counter equals table rows)
- `reputationObjectiveKeepsFactionAndValue` (8485)
- `spellObjectiveKeepsTheSpellButNotItsItem`
- `kindCountersCountRows` (each kind counter equals the table count; unknownKind present at 0)
- `unknownKindsAndMalformedEntriesAreCountedNotWritten` (unknownKind 2; badValue for objectives, triggerEnd and extraObjectives; unresolved counted once)
- `aCorrectionAddingAnItemObjectiveLandsInQuestObjective`: a Lua correction layer file replaces 176's objectives with `{nil, nil, {{1931}, {3618, "Gobbler's Head"}}}`. It applies with no skips, and both item rows land in `quest_objective`.

`ImportPipelineTest.endToEndOnFixtures` now also checks:
- `table.quest_objective`, `table.quest_objective_point` and `table.quest_source_item` are positive.
- `table.quest_source_item` equals `quest.sourceItems`.
- `quest.objective.kinds.creature` is positive, and `quest.objective.unknownKind` is 0 and rendered.
- 5561's credit group has 3 rows and its explore point is in zone 405.

### TDD evidence
- Red: the test class was written first, with the fixture already added and no writer. The result was `Tests run: 10, Failures: 10`, all assertions on empty tables. The correction-layer test failed on rows, not on skips.
- Green: after `QuestObjectiveWriter` and the EntityWriter wiring, the result was 10/10.
- One refinement came out of self-review: an explore entry with neither text nor points is now a bad value instead of an empty row. Its test lines and its code were added in the same step, so they were not separately red-first.

### Results
`./mvnw -B -q verify`: exit 0.

| Module | Tests | Failures | Errors |
|---|---|---|---|
| app | 204 | 0 | 0 |
| importer | 352 | 0 | 0 |
| lua-table | 61 | 0 | 0 |

The console lines seen during the importer run, such as "Era overlap oracle" and "RealFilesTest", come from existing tests. They are not new output.

## Files changed
- `importer/src/main/java/dev/wowforever/importer/sqlite/QuestObjectiveWriter.java` (new)
- `importer/src/main/java/dev/wowforever/importer/sqlite/EntityWriter.java`
- `importer/src/test/java/dev/wowforever/importer/sqlite/QuestObjectiveWriterTest.java` (new)
- `importer/src/test/java/dev/wowforever/importer/ImportPipelineTest.java`
- `importer/src/test/resources/fixtures/questiedb/data/Forever/foreverQuestDB.lua`
- `importer/src/test/resources/fixtures/questiedb/src/corrections/Forever/legacy/classicQuestFixes.lua`

## Self-review
- Completeness:
  - All six objective kinds are covered: creature, object, item, reputation, killCredit and spell.
  - Both explore sources are covered: triggerEnd and extraObjectives, with and without points.
  - Both source-item kinds are covered, with the three counters, the corrections test and the recorded fixture ids.
- No count is invented anywhere, and the schema has no count column.
- The writer is about 240 lines with one responsibility, so the file size matches the plan's intent.
- Output is quiet: the new tests print nothing.

## Concerns (for the lead)
1. **Brief versus data.** The brief asks for 176 with "creature + item objectives". The real 176 row has only an item objective, so 176 stays real. The creature-plus-item ordering test uses real quest 532 instead. Task 5's acceptance relies on the real 176 data, so 176 was not altered.
2. **Display-order flags are ignored.** Questie's `QuestieCorrections.itemObjectiveFirst[...]` and `killCreditObjectiveFirst[...]` flags, which appear in both fixture correction files, reorder objectives for display. The fixed ordinal order from the ruling ignores them. If 3B should match Questie's numbering for those quests, this would need a follow-up.
3. **Ignored extraObjectives fields.** The extraObjectives `objectiveIndex` (field 4) and dbReference list (field 5, for example `{{"object", 586726}}`) are not stored, because the ruling says text only. Field 5 names the entity to interact with. It would be the natural pin source for most Forever extra objectives, which have no spawnlist.
4. **`_add` does not work on objectives.** Corrections on `objectives` are replacements in Questie's real files. `objectives_add` is reported as unsupported by the existing applier, because the field has no shape; that is pre-existing behaviour, see `CorrectionApplierTest.reportsUnresolvedValuesAndUnsupportedOps`. So the corrections test uses a replacement that adds an item objective.
5. **Spell item dropped.** The spell objective's item is dropped as ruled. No real Forever spell objective with an item was found.
6. **Killcredit kind.** KillCredit member rows use kind `creature`, since the schema's kind list has no killCredit kind. They count toward `quest.objective.kinds.creature`.
7. **Primary checkout used.** The session environment switched its primary directory to `.worktrees/phase2b-ui` mid-task. All work, as instructed, was done in the main checkout `H:\claude_java` on `feature/phase3a-quest-data`.
