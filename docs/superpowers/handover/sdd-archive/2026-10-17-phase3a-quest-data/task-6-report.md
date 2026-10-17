# Task 6 report: app readers for quest objectives, relations, source items, chain and UI icons

Status: DONE_WITH_CONCERNS (concerns are design calls for review, nothing is broken).
Commit: `a0e7094 feat(app): quest objective, relation and chain readers` on `feature/phase3a-quest-data`.

## What was built

No UI. All reads go through `db.read(c -> Sql.…)` like the existing repositories.

- `QuestRepository.objectives(questId) -> List<QuestObjective>`: one record per `quest_objective` row, ordered by
  ordinal, then rowid, which is the importer's insertion order and puts the killCredit base creature first. The
  column `entity_id` maps to `targetId`. Points from `quest_objective_point` attach to every row with the same
  ordinal. Points with a NULL x or y are dropped.
- `QuestRepository.relations(questId) -> List<QuestRelation>`: the quest's own rows (`quest_id = ?`), ordered by
  kind and then related id.
- `QuestRepository.sourceItems(questId) -> List<QuestSourceItem>`: LEFT JOIN on item for name, quality and
  icon_id. Provided items come first, then required. Each group is ordered by name, with unknown items last. A
  missing item keeps its id with null name, quality and icon. `displayName()` gives "Unknown item #id".
- `QuestRepository.chain(questId) -> QuestChain`: built by the package-private `QuestChainBuilder`.
- `AssetRepository.uiIcon(name) -> Optional<byte[]>` reads `ui_asset`.
- Records:
  - `QuestObjective(questId, ordinal, kind, targetId: Long, text, creditGroup: Integer, points: List<Point>)` with
    a nested `QuestObjective.Point(zoneId, x, y)`. A new record was needed because `Spawn` carries a zoneName the
    objective points don't have.
  - `QuestRelation(questId, relatedId, kind)`.
  - `QuestSourceItem(itemId, kind, name, quality, iconId)`, with `ref()` and `displayName()`.
  - `QuestChain(steps, currentIndex, spine)` with nested `Step(questId, name, level, zoneName, connector, depth,
    loop)` and the enum `Connector`. The enum values are REQUIRES_ALL, REQUIRES_ONE, EXCLUSIVE, LEAD_IN, UNLOCKS
    and DURING. Each value's `label()` returns the spec's text, for example "Requires all of:".
  - QuestChain helpers are `current()`, `standalone()`, `spineIndex()`, `previous()` and `next()`. They serve the
    "‹ Previous · Next ›  Step 2 of 4" stepper. `MAX_DEPTH` is 50.
  - `Step` has `ref()` and `displayName()`, which gives "Unknown quest #id".

## Files

- `app/src/main/java/dev/wowforever/app/db/QuestRepository.java` (modified; `ZONE_JOIN` is now package-private
  and there is a new `relation` row mapper)
- `app/src/main/java/dev/wowforever/app/db/QuestChainBuilder.java` (new)
- `app/src/main/java/dev/wowforever/app/db/AssetRepository.java` (modified)
- `app/src/main/java/dev/wowforever/app/model/QuestChain.java`, `QuestObjective.java`, `QuestRelation.java`,
  `QuestSourceItem.java` (new)
- `app/src/test/java/dev/wowforever/app/db/QuestChainTest.java` (new, 16 tests)
- `app/src/test/java/dev/wowforever/app/db/SchemaDb.java` (new test helper, an empty database built from the
  importer's `schema.sql`)
- `app/src/test/java/dev/wowforever/app/db/QuestRepositoryTest.java` (10 new tests on the fixture database and
  `SchemaDb`)
- `app/src/test/java/dev/wowforever/app/db/AssetRepositoryTest.java` (1 new test)

## Chain algorithm in ten lines

1. Start at the quest, at depth 0. Claim it and put it on the path.
2. Backward edges, read from whichever side declares them: pre_group gives REQUIRES_ALL, pre_single gives
   REQUIRES_ONE, breadcrumb and the reverse of breadcrumb_for give LEAD_IN, parent and the reverse of child give
   DURING.
3. Forward edges, all UNLOCKS: the quests that pre_group or pre_single this one, next, child, the reverse of
   parent, breadcrumb_for and the reverse of breadcrumb. next only ever points forward, which is the "hint only"
   rule.
4. When one target has several edges, the strongest connector wins, in enum order. A prerequisite beats a lead-in,
   and a lead-in beats a parent.
5. At each step, every unclaimed child is claimed before any child is expanded, so each quest appears once and
   hangs off the nearest step. "Unlocks" lists each quest once.
6. An edge to a quest on the current path adds a `loop=true` step and stops there. An edge to a quest claimed
   elsewhere is skipped.
7. Exclusive targets, from either side, are claimed as leaves beside their quest at the same depth, with connector
   EXCLUSIVE.
8. Expansion stops at |depth| = 50, so the chain reaches at most 50 steps before and 50 after the quest.
9. Display order: backward subtrees in post-order (a step's own prerequisites first), then the quest and its
   alternatives, then forward subtrees in pre-order. Siblings are sorted by connector, level, name and id.
10. Spine:
    - Backward, it takes a prerequisite, or a lead-in only when there is no prerequisite. It prefers the one whose
      next names the step, then the highest level.
    - Forward, it takes an unlock. It prefers the step's own next, then the lowest level.
    - DURING, EXCLUSIVE and loop steps never join the spine.

`depth` is the signed distance from the current quest. The current quest's connector is null. A target absent from
the quest table becomes a step with a null name, level and zone. An unknown current quest gives a standalone chain
of one unnamed step.

## TDD evidence

1. **Red, compile.** The new tests did not compile against the missing API: `QuestChain`, `QuestObjective`,
   `QuestRelation`, `QuestSourceItem`, `uiIcon` and the new repository methods.
2. **Red, assertions.** With the models in place and the repository methods stubbed, 33 tests ran and 24 failed.
   That was 14 of 16 in QuestChainTest, 9 of 13 in QuestRepositoryTest and 1 of 4 in AssetRepositoryTest. The
   tests that passed on the stubs were the standalone or unknown-quest and connector-label cases.
3. **Green.** After implementation all 33 ran with 0 failures.

## Verify

`./mvnw -B verify` from H:\claude_java, run after the final refactor:

| Module | Tests | Failures | Errors |
|---|---|---|---|
| Lua table parser | 61 | 0 | 0 |
| Data importer | 364 | 0 | 0 |
| Browser application | 241 | 0 | 0 |

The app total includes 30 ShippedDataTest tests on the 67 MB database. BUILD SUCCESS.

I also ran a throwaway stress test, then deleted it without committing it. It ran chain, objectives and
sourceItems over all 5,232 quests in the shipped database:

| Measure | Result |
|---|---|
| Total time | about 5.1 s |
| Worst single quest | 19 ms (quest 8941) |
| Largest tree | 120 steps (783 "A Threat Within", the Northshire starter fan-out) |
| Longest spine | 29 (quest 8905) |
| Chains with a loop step | 14 |
| Standalone quests | 1,530 |

## Concerns and decisions for review

1. **Tests use a temp file, not an in-memory database.** QuestChainTest builds its database from the schema in a
   `@TempDir` file. `Database` only opens files read-only and has a private constructor, so a temp file avoids a
   test-only factory in production code.
2. **A breadcrumb's target reads as UNLOCKS.** When the current quest is a breadcrumb, its target appears as
   UNLOCKS. The spec defines no "leads to" connector. In the data, breadcrumb_for usually comes with next anyway.
3. **The parent quest sits before the quest.** It is placed at depth -1 as DURING, and its own prerequisites are
   walked backward. Children are UNLOCKS, per the spec's "next + child".
4. **Only loops on the current path are flagged.** A cycle among siblings is not marked. Each quest still appears
   only once and the walk always ends. An example is a quest reached backward and also forward through next. A
   pure two-quest cycle shows one loop step.
5. **Exclusive alternatives of every walked step are added, as leaves only.** On real starter quests this makes
   wide trees, for example the six "The Adventurer" variants in 783's chain. The spec's "2 steps either side +
   Show N earlier steps" collapsing will matter in 3B.
6. **The chain does not say when it hit the depth cap.** QuestChain has no truncated flag because none was
   specified. The UI cannot tell a chain cut at 50 from one that ends there. That is a cheap addition if wanted.
7. **The spine tie-break is my own.** Backward picks the highest level and forward the lowest, after the next hint.
   The spec does not define it.
8. **Unknown quests return a chain, not empty.** `chain()` of an unknown quest returns a one-step chain rather than
   `Optional.empty()`, so callers never branch. `detail()` is already empty for such ids.

## Follow-up after review (commit d2ebab6)

Commit: `d2ebab6 fix(app): quest chain expands exclusives at the current quest only, reports truncation, empty for
unknown quests`.

Rulings accepted as they were: concerns 1, 2, 3, 4 and 7.

- **Concern 2.** A breadcrumb's target stays UNLOCKS for now. Phase 3B may add a "Leads to:" connector.
- **Concern 7.** The spine tie-break is now documented in the QuestChain Javadoc.

Fixes:

1. **Exclusives (concern 5).** Exclusive alternatives are now claimed only for the current quest at depth 0. The
   builder passes `withAlternatives=false` when it recurses into child steps.
   - The current quest's exclusive branch is still tested, by the Pamela test and the fixture 5142 test.
   - A new test covers an exclusive declared only on the other quest's side.
   - Another new test asserts that neither a prerequisite's nor an unlock's exclusives are added.
2. **Truncation (concern 6).** `QuestChain` gained `truncated` as its fourth component. It is true when a step at
   |depth| = 50 still has an edge in its direction to a quest that is not yet claimed. The test uses a synthetic
   chain of 60 quests:

   | Quest viewed | Truncated |
   |---|---|
   | 159, walking backward | true |
   | 100, walking forward | true |
   | 150, which reaches exactly 100 at depth -50 | false |
   | 130, with 60 steps | false |

3. **Unknown quests (concern 8).** `chain(unknownId)` now returns `QuestChain.EMPTY`: no steps, `currentIndex` -1,
   an empty spine and `truncated` false.
   - The constructor accepts that shape only for an empty chain.
   - `current()` now returns `Optional<Step>`, which is empty for an unknown quest. This is an API change from the
     first commit.
   - A new `isEmpty()` method was added.
   - For the empty chain, `spineIndex()` is -1, `previous()` and `next()` are empty, and `standalone()` is false.
   - Tests cover this on the schema database, including a relation row that names the unknown id, and on the
     fixture with quest 604.

TDD evidence: with the old recursion restored, QuestChainTest ran 17 tests and only the new ancestor-exclusives
test failed. With the fix back in place, all 17 passed.

Quest 783 "A Threat Within" went from 120 steps to 108. The rest is genuine unlock fan-out, not exclusives:

| Depth | Steps | What they are |
|---|---|---|
| 1 | 3 | unlocks |
| 2 | 12 | unlocks, including the 7 class letters |
| 3 | 2 | unlocks |
| 4 | 2 | unlocks |
| 5 | 1 | "The Great Outdoors" |
| 6 | 87 | "Camping 101: <profession>" quests, about 7 copies per profession, each requiring "The Great Outdoors" |

The 3B tree must collapse that fan-out.

A throwaway check over all 5,232 shipped quests ran without errors in about 4.6 s. It found 0 truncated chains and
14 chains with a loop step. The probe was deleted, not committed.

`./mvnw -B verify` passed:

| Module | Tests | Failures | Errors |
|---|---|---|---|
| Lua table parser | 61 | 0 | 0 |
| Data importer | 364 | 0 | 0 |
| Browser application | 243 | 0 | 0 |

The app's classes include QuestChainTest with 17 tests, QuestRepositoryTest with 14 and ShippedDataTest with 30.

## Fix round 1 (commit 43b5b32)

Commit: `43b5b32 fix(app): quest chain steps carry their parent; breadth-first placement; docs`, on top of 9efed5d.

1. **Parent link.** `Step` now carries `parentQuestId: Long`, the quest of the step it hangs under. It is null for
   the current quest. Exclusive alternatives name the current quest, and loop steps name the step they hang under.
   - The component order is `Step(questId, name, level, zoneName, connector, parentQuestId, depth, loop)`.
   - The QuestChain Javadoc now says the UI groups a parent's children by connector, one connector line per group.
     It also says a parent's children are not always consecutive in `steps`.
   - The nested Goal 10 test asserts `11<10, 13<12, 14<12, 12<10, 10<-`.
   - A new test covers the parent ids of an exclusive step and of a loop step.
2. **Breadth-first placement.** Each direction is now walked breadth-first.
   - Every step one away is claimed before any step two away. Steps of one depth claim in display order, so each
     quest sits at its shallowest depth.
   - Loop detection now checks the step's own ancestor chain, through the Node parent links. The old global path
     set is gone.
   - The truncation check is exact. When a step at depth 50 is processed, every claim at depth 50 or less is
     already made.
   - The new test uses an uneven diamond: R unlocks A and B, A unlocks X, and Y requires X or B. It pins Y under B
     at depth 2.
   - Red evidence: with the queue temporarily switched to depth-first order, only that test failed (1 of 20).
3. **Stale Javadoc.** The `QuestRepository.chain()` Javadoc now says an unknown quest gives `QuestChain.EMPTY`.
4. **Parent ruling test.** A new test checks that a parent quest's own prerequisites are walked. Quest 51 is done
   during 50, and 50 requires 49, giving `49:REQUIRES_ONE:-2 (under 50), 50:DURING:-1, 51`.
5. **Single head query.** `head()` now returns `Optional<Head>` and is cached. `exists()` is gone, so the root costs
   one query.

Left as is, per the ruling: the relation and objective `kind` stays a raw String. Phase 3B may make it an enum.

A throwaway probe over all 5,232 shipped quests ran without errors in about 4.5 s, then was deleted:

| Measure | Result |
|---|---|
| Largest chain | 108 steps (quest 783, unchanged) |
| Truncated chains | 0 |
| Chains with a loop step | 14 |
| Deepest step | 28 |

`./mvnw -B verify` passed:

| Module | Tests | Failures | Errors |
|---|---|---|---|
| Lua table parser | 61 | 0 | 0 |
| Data importer | 365 | 0 | 0 |
| Browser application | 251 | 0 | 0 |

The app's classes include QuestChainTest with 20 tests, QuestRepositoryTest with 14 and ShippedDataTest with 30.
