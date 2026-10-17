# Product backlog and decisions, v2

Date: 2026-10-17. Owner: product manager. Inputs: phase 3 design spec (incl. addenda §10–11), ROADMAP, memos from the
WoW expert, BA and UI designers 1–3, plans 2B and 3A, and the three research reviews (loot/raids,
professions/economy, lore/exploration).

## 1. Product principles
1. A **research and learning tool** in the spirit of Wowhead: it describes the world (what exists, where, who, how
   often, what leads to what). It never prescribes what *you* should do next and never tracks progress.
2. **Offline and self-contained**: ships its own database; no network at runtime, nothing reads the game client.
3. **Trust through provenance**: every rate names its source; disagreements are shown; unknown says unknown;
   estimates are never presented as facts.
4. **Glance while playing**: the answer is above the fold at 1280×800 and works in compact mode (420 px).
5. **WoW idiom, readability first**: game cues players recognise, no themed-toy gimmicks, nothing under 4.5:1.
6. **Quality bar**: no feature ships without headless tests, shipped-data tests on real ids, review, and screenshots.

## 2. Release train
Gates for every release: `./mvnw -B -q verify` green; per-task spec + code review and a final branch review;
screenshots of the release's acceptance examples in dark, light and compact; warm page load ≤ 300 ms p95 on the
shipped DB; small text ≥ 4.5:1 in both themes; no unexplained new unsupported/missing counters in the import report.

| Release | Theme | Scope | Must be true to ship |
|---|---|---|---|
| **v0.2.0** (2B) | Companion-app foundation | Phase 2B as planned, ending with zone "!" giver pins | Gates; README screenshots; the quality fixes in §3 are closed or listed as v0.3.0 blockers |
| **v0.3.0** (3A + 3B) | Quests on the map, the WoW look | Quest page redesign, quest chain, objective pins, giver/turn-in pins, "Involved in quests", patrol routes, client-art rail icons, WoW chrome + Marcellus, Parchment theme, IA clean-up, merged Dropped by, master-list auto-collapse, community drop rates, coordinates + copy-to-game | Scan test on the five acceptance quests; chain tests cover AND/OR, exclusive, breadcrumb, cycle, missing id; icons verified; VMaNGOS licence in NOTICE; every §3 fix closed |
| **v0.4.0** | Loot research | Loot filter (class, slot, resistance), "New in Forever" badge, loot pools + world-drop bucket, item set pages, dungeon & raid prep tab | Molten Core filtered to Warlock + Waist shows Felheart Belt; Hogger's own drops lead and world drops are a separate bucket |
| **v0.5.0** | Crafting and economy | Crafting graph + skill strip, profession browser, recipe source resolver, vendor/trainer finder, vendor economics, gathering nodes, disenchanting | Skill colours match SkillLineAbility on three recipes; Copper Ore shows its nodes on the Elwynn map |
| **v0.6.0** | Lore and exploration | Zone gazetteer, flight-path layer, storylines, faction pages, "Mentioned in" + quest-text search, flavour and readable text, hunter pet panel, rare details, fishing per zone, Home as a learning entry point + glossary | Elwynn lists its sub-areas with level bands; Westfall shows its flight master on the layer |
| **v0.7.0** | Glance and compare | Compact pinned tabs + always-on-top, item comparison, pinned lists (wishlist) + export, "what changed in this build" | Compact screenshots at 420×720; build diff verified on two real builds |

## 3. Quality fixes in flight (2B Task 14, not backlog features)
Each one is a v0.3.0 ship blocker if Task 14 leaves it open.
- Zone level bands read "1–60" for Elwynn and Westfall; zone header repeats "Zone #12 · Zone".
- AtlasLoot ordinals shown as "Slot" and "Trash · slot 24" instead of the equip slot.
- The world-drop pool is buried in Hogger's drops; recipes and trade goods show item level instead of required skill.
- Unresolved spell tokens in set bonuses (`$1293733s2`, `${$proccooldown/60}`).
- Map pins have no label, legend or hover text; spawn maps do not name the zone; Westfall shows a blank map block.
- Friendly NPCs mixed with hostiles and instance elites in zone NPC tables; Edwin VanCleef shown as "Rare Elite".
- The Recent dropdown stores half-typed fragments; "Westfall Deed" reads "Quest · iLvl 8"; search box out of sync
  with the page (verify); Thunderfury card lacks the Era footer and its Nature damage line (verify).

## 4. Prioritised backlog
Value = to a researching player. Effort S ≤ 1 task, M 2–3, L 4+. VM = VMaNGOS SQLite world DB.

| Feature | Value | Effort | Data | Rel. | Decision | Rationale |
|---|---|---|---|---|---|---|
| Quest page redesign | H | L | 3A objectives, relations | 0.3 | In | Pick-up, do, hand-in, next without scrolling |
| Quest chain (stepper + tree) | H | M | quest_relation | 0.3 | In | AND/OR prerequisites are hard to find in-game |
| Objective pins | H | M | objectives, spawns, drops | 0.3 | In | Top reason to open a quest |
| Giver/turn-in pins (quest + zone "?") | H | S | quest_giver | 0.3 | In | Completes the zone "!" layer |
| "Involved in quests" on NPC/object pages | H | S | quest_objective | 0.3 | In | Reverse of objectives; consumes 3A data |
| Patrol routes | M | S | npc_waypoint | 0.3 | In | Owner request |
| Client-art rail icons | M | S | ui_asset | 0.3 | In | Owner request |
| WoW chrome + Marcellus | M | M | none | 0.3 | In | Token-driven, unifies pages |
| Parchment theme | M | S | none | 0.3 | In | Tokens already contrast-checked |
| IA clean-up (skeleton, hide empty, counts, kind labels in search) | H | M | none | 0.3 | In | Removes noise on every page |
| Merged Dropped by + section filter + "≠" legend | H | S | drop_rate_source | 0.3 | In | All sources side by side, by rate |
| Master-list auto-collapse | M | S | none | 0.3 | In | Gives the page 300 px |
| Community drop rates | H | M | VM loot templates | 0.3 | In | Replaces "rate unknown"; existing provenance UI |
| Coordinates + /way + in-game links | H | S | none | 0.3 | In | Bridges research to the game |
| Loot filter: class, slot, resistance | H | S | ItemSparse (shipped) | 0.4 | In | Loot review's first build; "what drops for me here" |
| "New in Forever" badge on items, NPCs, instances | H | S | absent from VM + client build | 0.4 | In | Owner: Forever's new content is first-class |
| Loot pools + world-drop bucket | H | M | VM reference ids, groupid | 0.4 | In | A mob's own loot vs a shared pool is a trust issue |
| Item set pages | M | S | item_set (shipped) | 0.4 | In | Pieces, sources, resolved bonuses |
| Dungeon & raid prep tab | H | M | relations, objectives | 0.4 | In | Quests, attunements, keys tied to an instance |
| Crafting graph + skill strip | H | M | craft tables (3A T8) | 0.5 | In | Professions review's first build |
| Profession browser | M | M | SkillLine, SkillLineAbility | 0.5 | In | Browse recipes by skill |
| Recipe source resolver | H | M | VM npc_trainer, npc_vendor | 0.5 | In | Trainer, vendor, drop or quest, with cost |
| Vendor/trainer finder | H | S | npc_flags (shipped) | 0.5 | In | Role filter + Services map toggle |
| Vendor economics | M | S | ItemSparse, VM npc_vendor | 0.5 | In | Price, stack, limited stock |
| Gathering nodes | H | M | VM gameobject loot, Lock | 0.5 | In | "Gathered from" + node map |
| Disenchanting | M | M | VM disenchant loot | 0.5 | In | Enchanters' core question |
| Zone gazetteer | H | M | AreaTable, TaxiNodes | 0.6 | In | Lore review's first build; zone page as hub |
| Flight-path layer | H | M | TaxiNodes, TaxiPath | 0.6 | In | Orientation |
| Storylines browser | M | M | quest_relation | 0.6 | In | Follow an arc as one piece |
| Faction pages | M | M | Faction DB2 | 0.6 | In | Reputation sources |
| "Mentioned in" + quest-text search | M | M | quest text index | 0.6 | In | Learning by name |
| Flavour and readable text | M | S | ItemSparse, PageText | 0.6 | In | Lore at a glance |
| Hunter pet panel | M | S | Creature family | 0.6 | In | Tameable, family |
| Rare details (spawns, placeholders, respawn) | M | S | Questie, VM spawntimesecs | 0.6 | In | Static respawn time is data, not a tracker |
| Fishing per zone | L | S | VM fishing loot | 0.6 | In | Fits the gazetteer |
| Home learning entry + glossary | M | M | counts, tiles | 0.6 | In | Continents, storylines, factions, rares; random quote Later |
| Compact pinned tabs + always-on-top | H | M | none | 0.7 | In | Glance while playing |
| Item comparison | M | M | item stats | 0.7 | In | Stat deltas, no character |
| Pinned lists (wishlist) + export | M | S | local prefs | 0.7 | In | Replaces notes/favourites |
| What changed in this build | M | M | previous build DB | 0.7 | In | Forever is a moving beta |
| Legendary component tree | M | L | chain + crafting + sources | — | Later | Composes three features; build after they ship |
| Spawn-cluster facts | M | M | spawns, VM respawn | — | Later | After gathering nodes |
| Mini tooltip window | M | M | none | — | Later | After pinned tabs |
| Upgrade finder | M | L | stat weights | — | Later | Needs a character; loot filter covers the research half |
| Localisation, 3D models | L | L | locales, asset pack | — | Later | Size, art redistribution |
| Farming route loop, yield/hour | L | M | estimates | — | Out | Prescriptive estimate (decision 9) |
| Live rare timers, auto-update | L | M | network | — | Out | Breaks offline |
| Quest progress, next-quest advice, level routes, XP rates | — | — | — | — | Out | Leveling guide (decision 8) |

## 5. Decisions log
1. **Objectives + map above Given by / Turned in to.** The stepper and the "!"/"?" pins above the fold meet the BA's
   concern; the scan test decides.
2. **No objective counts.** Questie has none.
3. **Rail icons from client art on navy plates**; SVG only as fallback. Owner ruling.
4. **Marcellus SC headings, Inter body.** Cinzel fallback; Friz Quadrata never ships.
5. **Parchment replaces the current light theme**; dark "Slate" stays the default.
6. **Merged Dropped by**: instance loot joins NPC drops as chips; the source filter applies to NPC rows only and a
   footnote counts what it hides.
7. **v0.3.0 scope is frozen.** It takes only small items that consume 3A data ("Involved in quests").
8. **What "leveling guide" means here**: a feature is Out when it needs your character, your progress or a
   recommended order. Describing quests by level, zone and chain is research.
9. **Farming**: facts are In (spawn counts, respawn time, nearby services); route loops and yield-per-hour estimates
   are Out, because they are recommendations built on assumptions.
10. **VMaNGOS**: 3A Task 9 reads the official SQLite release directly, pinned by tag and checksum, with no dump parsing.
    It imports loot templates with reference and group ids plus npc_vendor. Other tables land with their first consumer.
11. **World drops are never a mob's own loot.** Pools show as a separate bucket with their level range.
12. **Crafting UI moves to v0.5.0** behind loot research. Loot needs no new data; its data still lands in 3A.
13. **Home becomes the learning entry point in v0.6.0**, once storylines, factions and the gazetteer exist to link to.
14. **Acceptance examples come from real data.** Quest 176 has an item objective only, so 3A Task 5's assertion of
    a creature objective for Hogger must be corrected before it runs.

## 6. Open questions for the owner
1. Should the app store a character (level, faction, class)? **Default:** no. Filters remember the last class chosen.
2. May a VMaNGOS rate, which reflects vanilla, headline where Questie has none? **Default:** yes, labelled
   "community (VMaNGOS)"; Questie wins when both exist; Forever-new content never gets a vanilla rate.
3. Do you want a labelled "estimated yield per hour" for farming spots? **Default:** no (decision 9).
4. May later features ship more client art, such as profession or faction icons? **Default:** icons only.
5. Should v0.3.0 ship as one release? **Default:** yes.

## 7. How I work
- New expert input produces v(n+1) of this file with a changelog entry.
- A feature enters a plan only from this backlog with decision In and a release.
- Every feature carries BA-format acceptance criteria (numbered, testable, edge cases) and a named example, verified
  in the shipped DB before the plan is written:
  - **v0.3.0:** quest 176 (starts at Wanted Poster objects 68/156561, ends at NPC 240, item objective 1931); 166 (Defias
    chain); 6502/6602; 7787 (after 7785/7786); 7791 (repeatable); NPC 550 (patrol route).
  - **v0.4.0:** Molten Core with Warlock + Waist shows Felheart Belt 16806; set Felheart Raiment 203; Hogger 448; The Deadmines.
  - **v0.5.0:** Linen Cloth 2589 (reagent for), Heavy Leather 4234 (created by; verify after 3A Task 8), Copper Ore
    2770 (gathered from), Greater Magic Essence 10939 (disenchanting).
  - **v0.6.0:** Elwynn Forest 12, Westfall 40 with Thor 523, Gryan Stoutmantle 234 (friendly), Mor'Ladim 522 (rare),
    Edwin VanCleef 639 (boss rank).
- A release ships only when every gate in §2 is met; a failed gate moves the feature, never the bar.

## Changelog
- v1 (2026-10-17): first backlog from the phase 3 spec, ROADMAP and five panel memos.
- v2 (2026-10-17): folded in the loot/raids, professions/economy and lore/exploration reviews.
  - The train now runs to v0.7.0. v0.4.0 is loot, v0.5.0 crafting and economy, v0.6.0 lore, v0.7.0 glance and compare.
  - Crafting moved from v0.4.0 to v0.5.0. The Home dashboard moved from v0.4.0 to v0.6.0 as the learning entry point.
  - Set browser became item set pages. Vendor stock merged into vendor economics. Zone rares filter merged into rare details.
  - Notes/favourites became pinned lists with export. Live rare timers stay Out; static respawn times are In.
  - Added "Involved in quests" to v0.3.0, plus 21 new features, a farming ruling and a VMaNGOS SQLite ruling.
  - Screenshot bugs are listed in §3 as quality fixes, not features. Acceptance examples are now verified against
    the shipped DB, and quest 176's objective is corrected to an item.
  - The Parchment question became decision 5; a farming-estimate question took its place.
  - Added the "New in Forever" badge per the owner's direction. Forever's own systems (camping, legacy points,
    talents, new races) need a Forever-systems expert review before v3 can place them.
