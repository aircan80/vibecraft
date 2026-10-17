# WoW expert review 1 — loot and raid research (2026-10-17)

## What a Wowhead user would miss
- Thunderfury page: only "Rise, Thunderfury!"; missing the bindings (Garr, Baron Geddon), Thunderaan, the materials quest and chain order; no "who can use it"; the card lacks the Era footer the belt has; Era Thunderfury has a separate "+ Nature Damage" line — confirm where 82–153 comes from.
- Scarlet Belt: set pieces are plain grey text, not links, no drop locations; source "Scarlet Monastery > Trash" has no wing and no rate.
- Hogger: 25 drops are mostly the generic 15–20 world-drop pool shown as his own loot; no "Wanted: Hogger" link at the top, no respawn/health/gold; Item level is the wrong column for recipes/quest items (skill required matters).
- Molten Core: no class tag on tier pieces (Felheart = warlock); no equip-slot column; no attunement, Majordomo cache, rune dousing; no fire-resistance context; trash profession drops missing; 3 % epics are a shared pool and nothing says so.
- Maps: pins without label/legend/hover text; Hogger's spawn map does not name the zone.

## Additions
1. Class and slot filter on boss/instance loot ("only what a Warlock can wear, Feet") — ItemSparse AllowableClass, InventoryType, subclass, armour proficiency. The main loot-council/BiS workflow.
2. All sources on the item page aggregated across bosses/difficulties with rates side by side and a confidence note — AtlasLoot + Questie now, VMaNGOS later.
3. Pool and group semantics: boss-specific vs world-drop/reference pools; "one item from this group" — VMaNGOS reference_loot_template + groupid.
4. Item set page: pieces with drop locations, resolved bonuses, "farm the full set" by instance — ItemSet, ItemSetSpell, Spell/SpellEffect, AtlasLoot.
5. Legendary / long-chain component tree (quest chain + required items + sources, recursive) — Questie preQuest, requiredSourceItems, objectives + AtlasLoot.
6. Raid prep extension of "what do I need": attunements, keys, quintessence, resistance gear finder (sort by fire resistance per slot) — Questie + ItemSparse resistances.
7. Simple compare: two items side by side with stat deltas; "upgrades in this instance for slot X".
8. Local wishlist: pin items with source/boss/rate, export.

## Looks wrong (→ 2B Task 14 polish)
1. Unresolved spell tokens in the Scarlet set bonus ($1293733s2, ${$proccooldown/60}).
2. "Slot" column (1, 2, 16, 25…) and "Trash · slot 24" are AtlasLoot positions; replace with the equip slot.
3. Elwynn "Level 1–60" (a 1–10 zone); pins without labels/legend; the Scarlet Belt page still shows "Hogger" in the search box (page and search box out of sync — a tour artefact: the search typed "Hogger" after opening the belt; verify).

## Build first
Class and slot filter on instance/boss loot with the real equip slot replacing the AtlasLoot ordinal — ItemSparse data already loaded; turns the instance page into "what drops for me here".
