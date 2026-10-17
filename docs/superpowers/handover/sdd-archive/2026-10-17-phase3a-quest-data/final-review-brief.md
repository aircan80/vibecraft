# Phase 3A final whole-branch review brief

Plan: docs/superpowers/plans/2026-10-17-phase3a-quest-data.md (10 tasks; Task 10 section added later).
Spec: docs/superpowers/specs/2026-10-17-phase3-quest-companion-and-wow-look-design.md (§3 data, §5 icons, §8 acceptance quests).
Ledger: .superpowers/sdd/2026-10-17-phase3a-quest-data/progress.md (all rulings; read it for the plan defects found in real data).
Range: d88031e (main tip, v0.2.0 + plan 3B docs) .. 7cb870c on feature/phase3a-quest-data: exactly what lands on main. The 3A branch commits are listed in the package; the merge brought main in, so diffs of 2B files appear only where 3A changed them. Review package path is given in the dispatch.

## Named risks (check these first)
1. Merge semantics: the merge of main resolved 11 two-sided files by hand — NOTICE.md (both copies), README.md, Item/Npc/Quest/ZoneRepository.java, ShippedDataTest.java (2B additions vs the 3A split into ShippedDataTest / ShippedQuestDataTest / ShippedProvenanceDataTest on ShippedDataSupport) and three repository tests. Verify no 2B test was dropped, no 2B repository method lost, both NOTICE copies identical, README sections coherent.
2. Loot precedence (Task 9): Questie corrections > AtlasLoot override > Questie-Wowhead > VMaNGOS > AtlasLoot drop table > VMaNGOS quest-only; drop_rate_source.quest_only. VMaNGOS rolls per LootMgr.cpp (reference rows ungrouped; reference.group selects the referenced template's group; group roll exclusive at most one; equal share of the remainder; negative reference chance = plain; zero-chance reference skipped; forbidden_items). Check the implementation against these rules, not against the plan's original wording (the plan was wrong; ledger Task 9).
3. Questie semantics (Task 3): negative preQuestGroup ids are strict prerequisites written as abs id (kind pre_group/pre_single); zoneOrSort < 0 → sort_label never zone id; missing relation targets kept and counted (quest.relation.missing).
4. Fail-soft readers (Sql.hasTable) now return real data from the shipped DB: QuestRepository.objectives/relations/sourceItems/chain (QuestChainBuilder BFS, depth cap 50, loop flag, spine), NpcRepository.routes / ZoneRepository.routesInZone (Route.isLoop ≥ 3 points, last within 2.0), ItemRepository.createdBy/reagentFor/taughtBy, AssetRepository.uiIcon. Look for EDT violations, unbounded queries, N+1 patterns on the quest page paths, SQL built by concatenation.
5. Shipped DB: 80,625,664 bytes, extracted by DatabaseInstaller per run; three shipped test classes each extract it (3×) — confirm test time acceptable and extraction keyed by build id.
6. Build-time only: tools/serverdb (fetch.py, extract.py, serverdb.lock pinned to db-sqlite-4641790.zip), downloaded archive opened read-only immutable from ~/.cache; python run with -I; no runtime network; WoW install never written.

## Carried-forward minors from per-task reviews (fold into your findings where still true; ignore if already fixed)
- T2: explore zone-map walk duplicates RawEntity.spawns with a different NIL-hole policy; explore row can be written with neither text nor points; malformed triggerEnd double-counts badValue; missing tests for duplicate requiredSourceItems and objective naming an absent NPC; constructor can leak a Batch on failure.
- T3: pre_* abs rule keyed on a string prefix; negative scalar ids (nextQuestInChain) written as-is uncounted; RawEntity.pair ignores trailing values; QuestFixture.corrected() duplicates bound(); duplicatesCollapse only exercises idList's dedupe.
- T4: clear out/ui_icons before regenerating; _lock_ui_icons swallows a malformed lock; UiAssetLoader.read duplicates IconLoader.read; half-pixel crop asymmetry undocumented; no PNG validation.
- T5: 16663 "both sides" check should key on quest_id; named constants for pinned ids; rail icon names duplicated in test; rs.next() ignored once.
- T6: kind as raw String (carried to 3B); exists()+head(root) double query.
- T7: spawn-shaped values double-count badValue and lack a test; empty/odd-keyed tables ignored silently; zone ids ≤ 0 accepted; SQL column name concatenated in routes(); dead null filter.
- T8: reagent-order app test uses ascending ids; Crafts.reagentsOf linear scan; convert --tables "" copies only the lock; empty SkillLine cell → 0 not null.
- T9: mincountOrRef = 0 rows should be skipped+counted; maxcount = 0 references counted as one loop; chance < 1e-6 rows not modelled; row order for sums > 100; 1.59 M pairs in memory (filter to listed pairs).
- T10: DB extracted once per shipped test class; railIconsAreEmbedded ignores rs.next(); bare id literals in pins; possibly unused imports in ShippedDataTest; no lower bound on the 208 quest-only headlines.

## Output
Write the full review to .superpowers/sdd/2026-10-17-phase3a-quest-data/final-review-report.md: verdict (Approved / Needs fixes), then findings grouped Critical / Important / Minor, each with file:line, what is wrong, why it matters, and the fix you would make. Reply with the verdict and counts only.
7. Merge reconciliation commit 7cb870c: ItemCategories.ofDrop now folds pool-class items dropped by ≥ 20 NPCs into World drops regardless of rate (VMaNGOS rates cover the generic pool). Judge this product rule: does Hogger's page still read as reviewed (Quest items 2, World drops 23 collapsed) and does a rated 6 % Tough Jerky belong in World drops? The alternative is carrying pct_source on LootRow. Also check the 2B fixture-count test updates in that commit are honest (fixture grew: NPC 550, quest 452, 26 quests).
