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
