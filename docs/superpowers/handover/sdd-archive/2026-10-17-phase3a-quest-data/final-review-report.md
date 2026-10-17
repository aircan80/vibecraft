Verdict: Needs fixes (Critical 0, Important 1, Minor 6)

# Phase 3A final whole-branch review: d88031e..7cb870c

Reviewed on the clean checkout at 7cb870c (feature/phase3a-quest-data). I read the brief, the review package, the
ledger, the merge report and the code at the tip. I checked the shipped database and the pinned VMaNGOS world
database read-only (`?mode=ro&immutable=1`) with throwaway queries. I did not run `./mvnw verify`. The test result
relied on here is the merge agent's green verify on 7cb870c (lua-table 61, importer 415, app 798).

## Named risks: outcome

| Risk | Result |
|---|---|
| 1. Merge semantics | Clean. The four repositories gain only 3A methods, and no 2B method is lost (declaration diff against d88031e). No test method name from main is missing at the tip (1,004 on main, 1,147 at the tip). Both NOTICE copies are byte-identical (`cmp`). README sections read coherently. |
| 2. Loot precedence and LootMgr rules | Correct. `DropRateLoader` constants run 1 corrections, 2 AtlasLoot override, 3 Questie-Wowhead, 4 VMaNGOS, 5 AtlasLoot drop table, 6 VMaNGOS quest-only. `ServerDropRates` matches the pinned `LootMgr.cpp` (`AddEntry`, `Process`, `LootGroup::Roll`, `IsValid`): references are ungrouped, a reference's group selects one group of the referenced template, explicit chances are one exclusive roll with a 100 % row taking what is left, equal rows share the remainder, a negative reference chance is plain, a zero-chance reference is skipped, and forbidden items are dropped. |
| 3. Questie semantics | Correct. Negative pre ids become absolute ids with kind pre_group or pre_single (`QuestRelationWriter.java:54-56`). A negative zoneOrSort yields a sort label only (`EntityWriter.java:138`), and every app join guards `zone_or_sort > 0`. Missing targets are kept and counted, and the shipped report shows `quest.relation.missing = 1`. |
| 4. App readers | No EDT use: the readers are not wired to UI yet. All SQL is parameterised, apart from the carried column-name concatenation below. The chain builder issues one relation query and one head query per step, both indexed (`idx_quest_relation_related`, quest primary key). Quest 783's 108 steps cost about 200 indexed point lookups, which is acceptable. |
| 5. Shipped DB | `build-id.txt` equals `meta.build_id`. `DatabaseInstaller` re-extracts only when the build id differs. Each of the three shipped classes extracts the 80 MB file once into its own `@TempDir`. That is acceptable, and it also exercises the production extraction path. |
| 6. Build-time only | Holds. The app diff adds no network code. `fetch.py` extracts one named member, executes nothing, pins SHA-256 for the archive and the licence, and removes derived files on a checksum mismatch. The importer opens `mangos.sqlite` read-only and immutable. Nothing writes under the WoW install. |
| 7. World-drop product rule | The rule is too broad. See the Important finding. The fixture-count test updates in 7cb870c are honest: each keeps its original assertion and only adds the rows of 3A's larger fixture. |

## Critical

None.

## Important

### I1. World drops now hide high-rate, creature-specific farming drops

- **Where:** `app/src/main/java/dev/wowforever/app/ui/pages/ItemCategories.java:50`, pinned by
  `app/src/test/java/dev/wowforever/app/ui/pages/NpcPageTest.java:221`.
- **What is wrong:** `ofDrop` now folds any item of a pool class that 20 or more NPCs drop into the collapsed
  "World drops" group, whatever its rate. The justification in the commit and the javadoc is that VMaNGOS rates are
  "mostly estimates of a shared reference table". That holds for the low rates, about 0.02 to 2 %. It does not hold
  for the high rates, which come from a creature's own loot table or from Questie's Wowhead observations. On the
  shipped database, the drop rows the new rule moves into World drops include the following.

  | Rate source of the folded row | Rows at 10 % or more | Rows at 25 % or more |
  |---|---|---|
  | vmangos | 3,118 | 1,842 |
  | questie-wowhead | 477 | 298 |

  Examples at 25 % or more: Murloc Eye at 46 % average on 36 NPCs, Chunk of Boar Meat at 62 %, Crisp Spider Meat,
  Spider Ichor, Giant Egg, Essence of Fire at 62 %, and Healing Potion at 68 % on the NPCs that drop it heavily.
  These are the drops a player opens an NPC page to find, for cooking and profession farming. The owner put crafting
  research (created-by and reagent-for) on the 3A and 3B roadmap.
- **Why it matters:** Before 7cb870c, a rated drop always stayed in its class group. The new rule makes the NPC page
  hide the NPC's best-known farm drops behind a collapsed heading, which inverts the point of the grouping.
- **Hogger check:** Hogger does read as reviewed under the new rule: Quest items (2), then World drops (23)
  collapsed. Every pool drop of his is below 10 %. Tough Jerky at 6.09 % is a generic food drop, and it belongs in
  World drops.
- **Fix:** Fold a pool-class drop that 20 or more NPCs share when its rate is unknown or below a threshold. Use
  `WORLD_DROP_MAX_PCT = 10.0` as a named constant: `world = dropperCount >= 20 && pool class && (dropPct == null ||
  dropPct < WORLD_DROP_MAX_PCT)`. Hogger still reads as reviewed, the Tough Jerky case still folds, and Murloc Eye
  and the meats come back to Trade goods.
  - Change `aRatedPoolDropManyNpcsShareIsStillAWorldDrop` so it also asserts the opposite case: a 40 % pool item
    with many droppers stays in its class group.
  - Add one shipped assertion on a murloc page, for example that Murloc Eye stays in Trade goods.
  - When 3B Task 7 adds `pctSource` to `LootRow`, the threshold can be narrowed to VMaNGOS rates if the owner
    prefers. A threshold is still needed, because the 25 % and higher VMaNGOS rows are creature-specific.
  - Update the README sentence at `README.md:55` ("whether or not they have a rate").

## Minor

### M1. quest_only is stored, but nothing reads it or plans to

- **Where:** `importer/src/main/java/dev/wowforever/importer/sqlite/DropProvenance.java:41-46`,
  `tools/serverdb/README.md:77`, and the 3B plan, which has no `quest_only` consumer.
- **What is wrong:** 208 `item_npc_drop` headlines are VMaNGOS quest-only rates, the chance while on the quest. They
  render as plain per-kill rates. 1,967 rows carry the disagreement mark only because a quest-only rate is compared
  with another source. On Hogger's claw, Questie-Wowhead's 84 % is set against VMaNGOS 100 % quest-only. The README
  documents the disagreement as intended, and a Wowhead quest-item rate is also conditional on the quest, so the
  comparison is defensible. The headline case is not defensible: the ruling calls these "a different quantity", but
  no plan ever tells the user so.
- **Fix:** Add a line to 3B Task 7 so that a headline whose `vmangos` row has `quest_only = 1` reads "on quest" in
  the tooltip. In the same place, record the product decision on whether quest-only rates feed `disagrees`.

### M2. Main shows VMaNGOS rates without the "Vanilla rate" label until 3B Task 7

- **Where:** `app/src/main/java/dev/wowforever/app/model/RateSource.java:14-18`.
- **What is wrong:** After this merge, 137,373 of 164,447 drop rows headline a `vmangos` rate. The tooltip names the
  source as the raw string "vmangos", and nothing marks it as a vanilla rate. Decision 19 makes the label a v0.3.0
  ship gate, and 3B Task 7 owns it.
- **Fix:** Do not cut a build from main before 3B Task 7. Alternatively, add `"vmangos" -> "Vanilla rate (VMaNGOS)"`
  to `LABELS` now, which is a one-line change and is compatible with 3B Task 7.

### M3. Server-DB rebuild reproducibility

- **Where:** `importer/pom.xml:21` and `tools/serverdb/serverdb.lock`.
- **What is wrong:** The profile default hardcodes `4641790`, which duplicates the lock. Bumping the lock without the
  pom makes the import skip VMaNGOS. The shipped tests would then fail on `serverdb.skipped`, so the gap is caught,
  but late. Separately, the asset lives on the moving `db_latest` release. Once VMaNGOS republishes, the pinned
  asset URL may 404, and a fresh machine could no longer rebuild. `fetch.py` fails cleanly in that case.
- **Fix:** Derive the default directory from the lock in a small `exec` or `properties` step, or document that both
  must change together. In `tools/serverdb/README.md`, say that `db_latest` replaces its assets and that the cached
  archive in `~/.cache/wowforever-serverdb/4641790/` should be kept, or mirrored, to rebuild this database.

### M4. Carried from Task 9, confirmed still present: LootMgr validity rules not modelled

- **Where:** `importer/src/main/java/dev/wowforever/importer/serverdb/ServerDropRates.java:67` (a
  `mincountOrRef = 0` row joins a group) and `:167` (`Math.max(1, maxcount)` rolls a `maxcount = 0` reference once).
- **What is wrong:** `IsValid` also skips rows whose chance is below 1e-6, items missing from `item_template`, and
  rows with `maxcount < mincountOrRef`. The importer models none of these.
- **Measured:** In the pinned `mangos.sqlite` at patch 10, every one of these cases has 0 rows. The issue is latent.
- **Fix:** Skip and count these rows in `ServerDbSource.rows` with one counter per rule, mirroring `IsValid`, so that
  a future pin cannot shift group shares silently.

### M5. Carried from Task 10, confirmed still present: shipped-test gaps

- **Where:** `app/src/test/java/dev/wowforever/app/ShippedQuestDataTest.java:154` ignores the result of
  `rs.next()`. `app/src/test/java/dev/wowforever/app/ShippedProvenanceDataTest.java:113-117` asserts only that no
  quest-only headline has a second source. Nothing asserts that quest-only headlines exist; 208 do today.
- **Fix:** Use `assertThat(rs.next()).as(name).isTrue()`. Add `isBetween(100, 400)` on the quest-only headline
  count.

### M6. Carried from Tasks 3 and 7, confirmed still present and latent

- **Where:** `app/src/main/java/dev/wowforever/app/db/NpcRepository.java:126` concatenates the column name into the
  SQL. Its only callers pass the literals `npc_id` and `zone_id`.
- **Where:** `importer/src/main/java/dev/wowforever/importer/sqlite/QuestRelationWriter.java:61-63` writes a
  negative scalar id, such as nextQuestInChain, as-is and uncounted. The shipped database has 0 relation rows with
  `related_id <= 0`.
- **Fix:** Replace the column parameter with a two-value enum. Count a negative scalar id as
  `badValue.quest.<field>` and skip it.

The other carried minors from the per-task reviews were not re-verified line by line. None of them showed up as
wrong data in the shipped database. The shipped database has 0 explore rows with neither text nor points, 0 waypoint
rows with a null coordinate, and 0 waypoint rows with a zone id of 0 or less.
