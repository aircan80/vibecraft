# Task 3 report: quest relations, repeatable flag, requirements and sort labels

Status: DONE_WITH_CONCERNS (one data-semantics question, below). Commit `69ee77f` on `feature/phase3a-quest-data`.

## What was built

- **QuestRelationWriter** (`importer/src/main/java/dev/wowforever/importer/sqlite/QuestRelationWriter.java`). It writes a per-quest `quest_relation` set, modelled on `QuestObjectiveWriter`.
  - Kinds per the brief: preQuestGroup is pre_group, preQuestSingle is pre_single, nextQuestInChain is next, childQuests is child, parentQuest is parent, exclusiveTo is exclusive, inGroupWith is group, breadcrumbForQuestId is breadcrumb_for and breadcrumbs is breadcrumb.
  - List fields go through `RawEntity.ids` (deduped by `LuaColumns.idList`). The writer also keeps a per-quest set of (kind, related id) pairs, so a duplicate can never reach the primary key. The insert is a plain `INSERT`.
  - A scalar id of 0 means "none" in Questie, as with `sourceItemId`. For example, 960's correction sets `nextQuestInChain = 0`. A scalar of the wrong type counts `badValue.quest.<field>`.
  - A related id absent from the quests being written is still written and counted once per row as `quest.relation.missing`. "Absent" is resolved against `t.rows().keySet()`, the exact set of quest ids being inserted, which is passed to the writer up front. The counter is always registered, so a clean run reads 0.
- **QuestSort** (`importer/src/main/java/dev/wowforever/importer/questie/QuestSort.java`). It is a plain static map from QuestSort id to label. The ids come from Questie's own `constants.sortKeys` in `src/corrections/enum/quests.lua`, which the fixture carries identically to the pinned commit. The labels are the in-game category names. Unknown ids read as `Sort #<id>`.
- **EntityWriter.quests** now also writes six columns:
  - `repeatable` is `specialFlags & 1`. It is 0 when specialFlags is nil or not an integer.
  - `required_skill_id` and `required_skill_value` come from `requiredSkill`.
  - `required_min_rep_faction` and `required_min_rep_value` come from `requiredMinRep`.
  - `sort_label` is `QuestSort.label(-zoneOrSort)` when zoneOrSort is below 0, and NULL otherwise. `zone_or_sort` stays raw.
  - It then calls `relations.write(id, r)` after the objectives. `zoneOrSort` and `specialFlags` are read once each, so bad-value counts are not doubled.
- **RawEntity.pair(field)** reads an `{id, value}` pair of integers. It returns null for nil or an empty table. Any other shape counts `badValue.quest.<field>` and returns null.
- **No schema change.**

## Files touched

- New main files: `QuestRelationWriter.java` and `questie/QuestSort.java`.
- Modified main files: `EntityWriter.java` and `RawEntity.java`.
- New test files: `QuestRelationWriterTest.java`, `EntityWriterTest.java` and `questie/QuestSortTest.java`. There is also `QuestFixture.java`, a shared package-private helper holding the corrected-fixture setup, the SQLite write and the `column`/`scalar`/`table` helpers.
- Modified test file: `QuestObjectiveWriterTest.java`. It now uses `QuestFixture` instead of its private copies. Its only assertion change is the `quest.sourceItems` total, which goes from 5 to 8 because new fixture quest 5149 has `requiredSourceItems {12886, 12887, 12888}`. The comment says so.
- Fixtures: `data/Forever/foreverQuestDB.lua` gains 8 rows. `src/corrections/Forever/legacy/classicQuestFixes.lua` gains 4 entries. Both changes are additions only.

## Fixture ids added and where they came from

All rows were copied verbatim from the pinned QuestieDB `49e7dbca` `data/Forever/foreverQuestDB.lua`. The rows came from the importer download cache. All correction entries were copied whole from the same commit's `src/corrections/Forever/legacy/classicQuestFixes.lua`. Nothing is synthetic. Malformed and duplicate cases are built in memory inside the tests, as Task 2 does.

| Quest | Shape it provides |
|---|---|
| 595, 597, 599 | The Bloodsail Buccaneers chain: next 597, 599 and 604, with pre_single links back. 604 is not in the fixture, so it is the missing fourth. No upstream correction touches these three, and they carry no objectives or source items. |
| 5142, 5601 | Little Pamela and Sister Pamela: an exclusive pair, each `breadcrumbForQuestId = 5149` (from the correction) and next 5149. |
| 5149 | Pamela's Doll: the correction sets `breadcrumbs {5142, 5601}` and empties the raw `preQuestSingle {5142, 5601}`. |
| 3644 | Membership Card Renewal: specialFlags 1, requiredSkill {202, 200}, zoneOrSort -201 (Engineering). The correction sets `preQuestSingle {3639, 3641, 3643}` over the raw {3639}. |
| 247 | The Hunt Completed: `preQuestGroup {2, 23, 24}`. It is a real pre_group whose first target, quest 2, is in the fixture. |

Upstream also has `classicQuestReputationFixes` entries for these quests, mostly `reputationReward = {}`. They were not copied because no writer reads reputationReward yet.

Two other candidates were rejected. The Warrior chain 1638, 1639, 1640 has a correction that sets 1638's nextQuestInChain to 0, which breaks the chain. Quest 1640's correction also adds an objective, which would have moved more of the Task 2 counts.

The fixture's existing rows already supply other shapes. Quest 960 is repeatable and created by a correction. Quests 8121 and 8485 have requiredMinRep. Quest 452 has specialFlags 2, so it is not repeatable. Quest 5691 has `sortKeys.PRIEST`, so its sort_label is Priest.

## Tests

The brief's tests:
- `QuestRelationWriterTest`: `bothPrerequisiteKindsAreKept`, `missingRelationTargetsAreKeptAndCounted`, `duplicatesCollapse` and `breadcrumbsBothWays`.
- `QuestSortTest`: `negativeZoneOrSortBecomesASortLabel`.
- `EntityWriterTest`: `questFlagsAndRequirements`.

I added one test, `QuestRelationWriterTest.malformedRelationsAreCountedNotWritten`, which covers bad-value counting for scalar and list fields.

The exact missing count on the corrected fixture is 14:
- Quest 2: 6383, 23 and 24.
- Quest 136: 138.
- Quest 247: 23 and 24.
- Quest 532: 529 and 539.
- Quest 599: 604.
- Quest 960: 944.
- Quest 1136: 1131.
- Quest 3644: 3639, 3641 and 3643.

I counted these by hand from the rows and corrections before running the test, and the run matched. The test also checks the counter against `select count(*) from quest_relation where related_id not in (select id from quest)`.

## TDD evidence

| Run | Result |
|---|---|
| Red, before QuestSort existed | `QuestSortTest` failed to compile: cannot find symbol `QuestSort`. |
| Red, QuestSort present, no writer or columns | 17 tests, 6 failures: all 5 `QuestRelationWriterTest` tests and `EntityWriterTest.questFlagsAndRequirements` (expected `1:202:200:null:null:-201:Engineering`, got `0:null:null:null:null:-201:null`). The 10 `QuestObjectiveWriterTest` tests and `QuestSortTest` passed. |
| Green | `QuestSortTest` 1/1, `QuestObjectiveWriterTest` 10/10, `QuestRelationWriterTest` 5/5, `EntityWriterTest` 1/1. |

QuestSort was drafted before its test. I moved it aside to take the red run and then restored it unchanged.

## Full verify

`./mvnw -B -q verify` from H:\claude_java exited 0.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 359 (352 before) | 0 | 0 | 0 |
| app | 204 | 0 | 0 | 0 |

## Real import with --skip-client-data

`java -jar importer/target/importer.jar --out <scratch>/real.sqlite --skip-client-data --fail-on-unsupported` ran on the pinned sources and exited 0. No primary-key conflict occurred.

| Relation kind | Rows |
|---|---|
| pre_single | 3266 |
| next | 2028 |
| exclusive | 1467 |
| breadcrumb | 217 |
| breadcrumb_for | 216 |
| group | 202 |
| pre_group | 183 |
| child | 45 |
| parent | 42 |

| Other result | Value |
|---|---|
| quest.relation.missing | 3 |
| Quests | 5232 |
| Repeatable quests | 730 |
| Quests with a required skill | 225 |
| Quests with a minimum reputation | 280 |
| Negative zoneOrSort rows without a label | 0 |
| Labels falling back to "Sort #id" | 0 |

## Concerns

1. **Negative relation ids from a correction.** Two of the three real missing rows are (4964, -4962, pre_group) and (4975, -4963, pre_group). They come from classicQuestFixes `[questKeys.preQuestGroup] = {4976, -4962}` and `{4976, -4963}`, the Completed Orb quests. Questie appears to use a negative prerequisite id as a "must not have completed" marker. The brief has no rule for this. I applied the stated rule: the related quest is absent, so the row is kept and counted as missing, with the sign kept. If the UI should show these as "not after" or drop them, that needs a ruling, possibly a new kind. The third missing row, (4604, 2955, pre_single), is an ordinary absent target.
2. **QuestObjectiveWriterTest edits.** I refactored it onto the shared `QuestFixture` helper. Its only behavioural edit is the source-item total moving from 5 to 8, which the new fixture quest 5149 causes.

## Follow-up: negative prerequisite ids (commit 0f82085)

The lead ruled from Questie's `QuestieDB:IsPreQuestGroupFulfilled`: a negative preQuestGroup entry means the quest with the absolute id must be completed, and an exclusive alternative does not count. Concern 1 above is resolved by this fix.

- **Writer.** `QuestRelationWriter` now writes negative pre_group ids, and defensively negative pre_single ids, as the absolute id with the same kind. "Missing" is resolved against the absolute id. A negative id and its absolute id in one list collapse to one row. The class comment cites the Questie function and its rule.
- **Fixture.** Real quest 4964, The Completed Orb of Dar'Orahil, was added. Its row and its whole classicQuestFixes entry were copied from QuestieDB 49e7dbca. The correction sets objectives `{{{6266}}}`, empties preQuestSingle, and sets `preQuestGroup {4976, -4962}` and `exclusiveTo {4963}`.
- **New test.** `QuestRelationWriterTest.negativePrerequisiteIdsAreStrictPrerequisitesOfTheAbsoluteId` checks five things:
  - Quest 4964 has exactly pre_group 4962, pre_group 4976 and exclusive 4963.
  - No negative related id exists anywhere in the table.
  - Quest 4962 is absent from the fixture and has exactly one row.
  - An in-memory `preQuestSingle {-136, 136}` collapses to one pre_single 136.
  - The missing count stays at the fixture total.
- **Count updates from adding 4964.**
  - The fixture's `quest.relation.missing` goes from 14 to 17, for 4976, 4962 and 4963.
  - The `duplicatesCollapse` total goes from 15 to 18.
  - The creature objective count in `QuestObjectiveWriterTest.kindCountersCountRows` goes from 15 to 16, because of 4964's correction. Each count comment says why.

TDD: with the fixture and test in place, 18 tests ran and only the new test failed. After the fix, 18 of 18 pass.

Full `./mvnw -B -q verify` exited 0.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 360 | 0 | 0 | 0 |
| app | 204 | 0 | 0 | 0 |

Real `--skip-client-data` import: it exited 0, and `quest_relation` holds 7666 rows. `quest.relation.missing` fell from 3 to 1, leaving only (4604, 2955, pre_single). No related id is negative. Quests 4964 and 4975 now link to pre_group 4976 plus 4962 and 4963 respectively.

The Python edit scripts left CRLF working copies of three files. The committed blobs were LF. I restored those working copies from the index.
