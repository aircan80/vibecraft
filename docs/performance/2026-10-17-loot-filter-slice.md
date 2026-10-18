# Loot research filter slice

Encounter loot supports class restriction and equipment slot filters in both interfaces. This is a bounded v0.5 vertical slice; resistance filters, instance-wide research and world-drop pools are still pending.

The class selector tests `allowable_class` only. It does not establish armor/weapon proficiency, level suitability or a recommendation. A null mask is unknown, `-1` unrestricted, and zero a known mask admitting no selected class. Equipment slot prefers indexed native `Item.InventoryType` from the exact active `client_build`, product `wow_classic_beta`, locale `enUS`; it otherwise uses the source-qualified `item.inventory_type`. There is no other-build or Era fallback; `boss_loot.slot` remains AtlasLoot ordering and is never interpreted as an equipment slot.

Repository reads use the connection-local Forever item projection. No Era fallback is added. Existing loot presence/rate source notes remain visible and independent from item metadata. Filter changes only transform an already loaded encounter list. Filters survive encounter/difficulty changes and page history restoration.

With an active filter, known mismatches are excluded. Remaining rows missing an active field are counted once as unknown and do not appear as matches. Per-field unknown counts overlap; this is explained in the count tooltip. With no filters, all rows remain visible, including hidden metadata. Counts describe loot rows in the selected encounter/difficulty, not unique items across the whole instance.

## Molten Core acceptance correction

The old roadmap asks for Felheart Belt 16806 in Molten Core with Warlock + Waist. The canonical row reports that item's mask 256 and inventory type 6 from `client_source='era'`. Native Forever `Item.InventoryType` independently supplies slot 6, but the class mask remains unknown under the strict Forever policy. Therefore this filter cannot honestly display the belt as a Forever match. Its unfiltered item route still allows explicit Classic comparison.

A read-only SQL audit of the bundled database on 2026-10-17 found 224 Molten Core loot rows across all encounters/difficulties: 181 have Era-only metadata. All 224 rows have an exact active Forever native slot. Warlock + Waist across that aggregate gives 0 known matches, 209 known exclusions, 15 unknown rows. Individual selected-encounter counts are smaller. These are source-coverage results, not missing loot import or a reason to reinterpret Era as Forever.

## Validation

Focused tests cover equipment slot versus addon ordering, overlapping unknown counts, known mismatch precedence, zero versus missing, unrestricted masks, observation preservation and filter control restoration. The SQL boundary test now seeds an Era-only waist/class mask and verifies both remain null through the actual NPC loot repository. Existing encounter/NPC/repository tests are included in the app regression run.

Chest includes native Chest (5) and Robe (20); Ranged includes native Ranged (15) and Ranged right (26). Other slot choices retain their explicit distinct meanings. History restoration loads the saved encounter/difficulty once.

## Resistance criteria and remaining v0.5 scope

The encounter workspace now adds recorded resistance school and inclusive minimum-value criteria. Only explicit numeric `item_stat` evidence attached to `forever` or `hotfix` client rows with client data qualifies. Schools are stat IDs 51–56 and the explicit all-resistances stat 124. The latter is a separate recorded stat criterion, not a derived sum with school-specific stats. Missing schools remain unknown, even when another school is present. Explicit zero is known and can match minimum zero. Native `ItemSparse.StatPercentEditor_*` budgets are not resistance amounts and are never cast into values. Era-only stats remain excluded from evidence.

A bounded batch query attaches immutable resistance facts to each encounter snapshot using at most 450 distinct item IDs per query. It uses the existing `item_stat(item_id)` index and item primary key. No native ItemSparse decode or extra query occurs when changing school/minimum criteria; source notes remain independent. Per-field unknown counts include resistance and can overlap, while the overall unknown count counts each row once.

Read-only Python/SQLite query measurement on the shipped database (2026-10-17): Molten Core's 160 distinct loot item IDs took 0.996 ms on first execution and 0.327 ms warm p95 across 30 executions. These measure the bounded attachment SQL, not full page-load latency or a cold OS cache. `EXPLAIN QUERY PLAN` uses item PK + `idx_item_stat_item`, with a small group-by temporary B-tree. Molten Core currently has no qualifying numeric Forever resistance facts, so resistance filtering there honestly reports unknowns. Known examples elsewhere: The Deadmines Gold-flecked Gloves 5195 (Fire 5), Emberstone Staff 5201 (Fire 8), and Rockslicer 872 (Nature 7).

Scope audit: `NpcPage` already separates generic world drops into a collapsed final bucket, so this slice does not duplicate that behavior. Class/slot/resistance encounter filtering is implemented. An instance-wide loot pool workspace and dungeon/raid preparation tab remain unimplemented. The old Molten Core/Felheart acceptance remains incompatible with strict Forever class-source policy and is corrected above. This slice does not declare v0.5 complete or satisfy the release's full native screenshot/performance gates.
