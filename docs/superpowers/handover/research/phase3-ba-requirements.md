# BA memo — quest feature requirements + WoW-like clean-up (2026-10-17)

## Givers and turn-in
1. Same zone: "Given by"/"Turned in to" show name, zone, coordinates; one map with a yellow "!" giver pin and a "?" turn-in pin.
2. Cross zone: one map tab per zone labelled with its pin count, defaulting to the giver's zone.
3. Multiple givers / several spawns: all listed and pinned; hovering a row highlights its pins.
4. Item-started: "Given by" shows the item labelled "Starts this quest", linked; the map pins its droppers/containers, no "!".
5. Objects: labelled as objects with an object marker.
## Objective items
6. Several droppers: item with count; sources by drop rate desc, top 10 + "Show all"; spawns pinned in the objective colour, tooltip "NPC: drops Item (n%)".
7. From an object (chest/herb): the object is the source; its spawns pinned.
8. No source: "Source unknown in data"; no pins, no error.
9. requiredSourceItems under "Must carry", separate, no counts; sources mapped when known.
## Kill and interact
10. Creature/object objectives: "Kill 10 x Name"; spawns pinned; a killCredit group = one row listing every credited NPC; spell objectives text only.
11. Legend toggles Giver / Turn-in / Objective layers; map zooms to fit the active tab's pins.
## Chains
12. Previous/next: "Quest chain" lists steps from the earliest ancestor to the last descendant; name + level; current highlighted.
13. preQuestGroup → "Requires all of:"; preQuestSingle → "Requires any one of:"; both may appear.
14. childQuests → indented "Leads to" branches; a quest in both next and child shown once.
15. exclusiveTo → "Alternative to (only one can be done)"; inGroupWith → "Part of a group with".
16. No chain: full mode "Not part of a chain"; compact hides the section.
17. Bad data: cycles stop at the repeated quest "(loop in data)", depth cap 50; missing id → "Quest #id (not in database)", not clickable.
## Navigation
18. Pin → NPC/object page; chain step → quest; objective item → item page.
19. Back/forward restore page, scroll position and selected map tab.
20. Zone "!" pins (Task 15) — one quest opens it, several → pick list.
## Compact / missing maps
21. Compact: map behind "Show map"; chain collapses to previous/current/next + "+N more"; sources top 3.
22. Zone without map: coordinates as text + "No map for <zone>", no empty tab.
## Constraints
N1 fully offline. N2 quest page incl. chain < 300 ms p95 on the 66 MB DB (warm). N3 no DB reads on the UI thread; placeholder while loading; last click wins. N4 indexed queries only.
## QA edge cases
Missing referenced ids; self-reference and 2-cycle; giver/finisher without spawns or only in unmapped zones; count 0/missing (never "0 x"); objective item that is also a required source item; quest with no giver/finisher; item-started quest whose item has no source; an NPC giving 20+ quests; giver = finisher (one "!?" pin); chain > 50 deep, 10+ children; duplicate ids in preQuestGroup; compact toggled while loading.
## Database additions
- quest_objective(quest_id, kind creature|object|item|spell, entity_id, count, credit_group, display_order)
- quest_required_source_item(quest_id, item_id)
- quest_relation(quest_id, related_id, kind pre_single|pre_group|next|child|exclusive|group); keep references to missing quests, importer logs the count
- quest_giver.kind allows "item" for start
- indexes: quest_relation(related_id), quest_objective(entity_id, kind), quest_giver(entity_id, kind, role)
- reuse npc_spawn/object_spawn and item-source tables; CONFIRM object spawns and object-contained items are shipped
- optional precomputed quest_chain_root(quest_id, root_id) only if N2 fails

## WoW-like clean-up (C1–C8)
C1 rail icons in order Home, Items = bag, NPCs = character head, Quests = yellow "!", Zones = folded map, Dungeons & Raids = portcullis; active highlighted. C2 tooltips ≤ 500 ms + accessible names + keyboard. C3 same header block on every entity page (icon, name in quality/difficulty colour, chips kind/level/zone; missing value drops its chip; zone chip opens the zone). C4 empty sections never rendered. C5 rarely used sections start collapsed with counts, state remembered. C6 quest page order: header, giver+turn-in, objectives, map, chain, rewards, collapsed extras. C7 scan test: at 1280×800 a new player names giver, turn-in and first objective location within 5 s without scrolling; ≥ 4 of 5 players on 5 sample quests (same-zone, cross-zone, item-started, multi-source item, long chain). C8 the same holds in compact with the map toggle as the only click.
