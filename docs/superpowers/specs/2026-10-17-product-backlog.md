# Product backlog and decisions, v3

Date: 2026-10-17. Owner: product manager. Inputs: phase 3 design spec (incl. addenda §10–11), ROADMAP, memos from the
WoW expert, BA and UI designers 1–3, plans 2B and 3A, the three research reviews (loot/raids, professions/economy,
lore/exploration), and the owner's direction on WoW: Forever content ("let's make this the best WoW companion app ever").
Pending: the analyst's `wow-forever-new-systems.md` and the Forever ideation round, which produce v4.

## 1. Product principles
1. A **research and learning tool** in the spirit of Wowhead: it describes the world (what exists, where, who, how
   often, what leads to what). It never prescribes what *you* should do next and never tracks progress.
2. **WoW: Forever first.** Forever's new content and systems (camping, legacy points, talents, races, zones,
   instances, gear) are first-class, not an afterthought to Classic data. New content is labelled as new, and every
   release names at least one Forever-new acceptance example once one is known.
3. **Offline and self-contained**: ships its own database; no network at runtime, nothing reads the game client.
4. **Trust through provenance**: every rate names its source; disagreements are shown; unknown says unknown;
   estimates are never presented as facts; vanilla data is never passed off as Forever data.
5. **Glance while playing**: the answer is above the fold at 1280×800 and works in compact mode (420 px).
6. **WoW idiom, readability first**: game cues players recognise, no themed-toy gimmicks, nothing under 4.5:1.
7. **Quality bar**: no feature ships without headless tests, shipped-data tests on real ids, review, and screenshots.

## 2. Release train
Gates for every release: `./mvnw -B -q verify` green; per-task spec + code review and a final branch review;
screenshots of the release's acceptance examples in dark, light and compact; warm page load ≤ 300 ms p95 on the
shipped DB; small text ≥ 4.5:1 in both themes; no unexplained new unsupported/missing counters in the import report.

| Release | Theme | Scope | Must be true to ship |
|---|---|---|---|
| **v0.2.0** (2B) | Companion-app foundation | Phase 2B as planned, ending with zone "!" giver pins | Gates; README screenshots; the quality fixes in §3 are closed or listed as v0.3.0 blockers |
| **v0.3.0** (3A + 3B) | Quests on the map, the WoW look | Quest page redesign, quest chain, objective pins, giver/turn-in pins, "Involved in quests", patrol routes, client-art rail icons, WoW chrome + Marcellus, Parchment theme, IA clean-up, merged Dropped by, master-list auto-collapse, community drop rates, coordinates + copy-to-game | Scan test on the five acceptance quests; chain tests cover AND/OR, exclusive, breadcrumb, cycle, missing id; icons verified; VMaNGOS licence in NOTICE; every §3 fix closed |
| **v0.4.0** | **WoW: Forever content** (reserved) | "New in Forever" badge + list filter, camping, legacy points, new talents, new races, new zones/instances/gear (placeholders until v4) | Every shipped feature's data confirmed in the Forever build; Forever-new examples verified in the shipped DB; no vanilla rate on Forever-new content |
| **v0.5.0** | Loot research | Loot filter (class, slot, resistance), loot pools + world-drop bucket, item set pages, dungeon & raid prep tab | Molten Core filtered to Warlock + Waist shows Felheart Belt; Hogger's own drops lead and world drops are a separate bucket |
| **v0.6.0** | Crafting and economy | Crafting graph + skill strip, profession browser, recipe source resolver, vendor/trainer finder, vendor economics, gathering nodes, disenchanting | Skill colours match SkillLineAbility on three recipes; Copper Ore shows its nodes on the Elwynn map |
| **v0.7.0** | Lore and exploration | Zone gazetteer, flight-path layer, storylines, faction pages, "Mentioned in" + quest-text search, flavour and readable text, hunter pet panel, rare details, fishing per zone, Home as a learning entry point + glossary | Elwynn lists its sub-areas with level bands; Westfall shows its flight master on the layer |
| **v0.8.0** | Glance and compare | Compact pinned tabs + always-on-top, item comparison, pinned lists (wishlist) + export, "what changed in this build" | Compact screenshots at 420×720; build diff verified on two real builds |

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
Value = to a researching player. Effort S ≤ 1 task, M 2–3, L 4+, ? = sized in v4. VM = VMaNGOS SQLite world DB.

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
| "New in Forever" badge + list filter | H | S | absent from VM + client build | 0.4 | In | Makes new content findable everywhere |
| Camping | H | ? | analyst report | 0.4 | In (placeholder) | Owner request; scope from the ideation round |
| Legacy points | H | ? | analyst report | 0.4 | In (placeholder) | Owner request; scope from the ideation round |
| New talents (talent browser) | H | ? | client talent tables (verify) | 0.4 | In (placeholder) | Wowhead-core research; no stored character needed |
| New races (racials, start zones, class combos) | H | ? | client race tables (verify) | 0.4 | In (placeholder) | Owner request |
| New zones, instances, gear | H | ? | client + Questie Forever data | 0.4 | In (placeholder) | Mostly existing pages + the badge; gaps from v4 |
| Loot filter: class, slot, resistance | H | S | ItemSparse (shipped) | 0.5 | In | Loot review's first build; "what drops for me here" |
| Loot pools + world-drop bucket | H | M | VM reference ids, groupid | 0.5 | In | A mob's own loot vs a shared pool is a trust issue |
| Item set pages | M | S | item_set (shipped) | 0.5 | In | Pieces, sources, resolved bonuses |
| Dungeon & raid prep tab | H | M | relations, objectives | 0.5 | In | Quests, attunements, keys tied to an instance |
| Crafting graph + skill strip | H | M | craft tables (3A T8) | 0.6 | In | Professions review's first build |
| Profession browser | M | M | SkillLine, SkillLineAbility | 0.6 | In | Browse recipes by skill |
| Recipe source resolver | H | M | VM npc_trainer, npc_vendor | 0.6 | In | Trainer, vendor, drop or quest, with cost |
| Vendor/trainer finder | H | S | npc_flags (shipped) | 0.6 | In | Role filter + Services map toggle |
| Vendor economics | M | S | ItemSparse, VM npc_vendor | 0.6 | In | Price, stack, limited stock |
| Gathering nodes | H | M | VM gameobject loot, Lock | 0.6 | In | "Gathered from" + node map |
| Disenchanting | M | M | VM disenchant loot | 0.6 | In | Enchanters' core question |
| Zone gazetteer | H | M | AreaTable, TaxiNodes | 0.7 | In | Lore review's first build; zone page as hub |
| Flight-path layer | H | M | TaxiNodes, TaxiPath | 0.7 | In | Orientation |
| Storylines browser | M | M | quest_relation | 0.7 | In | Follow an arc as one piece |
| Faction pages | M | M | Faction DB2 | 0.7 | In | Reputation sources |
| "Mentioned in" + quest-text search | M | M | quest text index | 0.7 | In | Learning by name |
| Flavour and readable text | M | S | ItemSparse, PageText | 0.7 | In | Lore at a glance |
| Hunter pet panel | M | S | Creature family | 0.7 | In | Tameable, family |
| Rare details (spawns, placeholders, respawn) | M | S | Questie, VM spawntimesecs | 0.7 | In | Static respawn time is data, not a tracker |
| Fishing per zone | L | S | VM fishing loot | 0.7 | In | Fits the gazetteer |
| Home learning entry + glossary | M | M | counts, tiles | 0.7 | In | Continents, storylines, factions, rares; random quote Later |
| Compact pinned tabs + always-on-top | H | M | none | 0.8 | In | Glance while playing |
| Item comparison | M | M | item stats | 0.8 | In | Stat deltas, no character |
| Pinned lists (wishlist) + export | M | S | local prefs | 0.8 | In | Replaces notes/favourites |
| What changed in this build | M | M | previous build DB | 0.8 | In | Forever is a moving beta |
| Legendary component tree | M | L | chain + crafting + sources | — | Later | Composes three features; build after they ship |
| Spawn-cluster facts | M | M | spawns, VM respawn | — | Later | After gathering nodes |
| Mini tooltip window | M | M | none | — | Later | After pinned tabs |
| Upgrade finder | M | L | stat weights | — | Later | Needs a character; loot filter covers the research half |
| Localisation, 3D models | L | L | locales, asset pack | — | Later | Size, art redistribution |
| Farming route loop, yield/hour | L | M | estimates | — | Out | Prescriptive estimate (decision 9) |
| Recommended talent builds | L | M | opinion | — | Out | Prescriptive (decision 8); the browser shows what exists |
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
7. **v0.3.0 scope is frozen and ships as one release.** It takes only small items that consume 3A data ("Involved in quests").
8. **What "leveling guide" means here**: a feature is Out when it needs your character, your progress or a
   recommended order. Describing quests, talents and races as they exist is research; recommending builds is not.
9. **Farming**: facts are In (spawn counts, respawn time, nearby services); route loops and yield-per-hour estimates
   are Out, because they are recommendations built on assumptions.
10. **VMaNGOS**: 3A Task 9 reads the official SQLite release directly, pinned by tag and checksum, with no dump parsing.
    It imports loot templates with reference and group ids plus npc_vendor. Other tables land with their first consumer.
11. **World drops are never a mob's own loot.** Pools show as a separate bucket with their level range.
12. **WoW: Forever content is v0.4.0, right after the quest release**, ahead of loot, crafting and lore, because it is
    what no Classic tool covers. v0.4.0 ships the features whose data is confirmed in the Forever build and never
    waits on unconfirmed data; unconfirmed placeholders move to the next release, and loot research does not jump the queue.
13. **Crafting follows loot (v0.6.0)**; its data still lands in 3A. **Home becomes the learning entry point in v0.7.0**,
    once storylines, factions and the gazetteer exist to link to.
14. **Acceptance examples come from real data.** Quest 176 has an item objective only, so 3A Task 5's assertion of
    a creature objective for Hogger must be corrected before it runs.

## 6. Open questions for the owner
1. Should the app store a character (level, faction, class)? **Default:** no. Filters remember the last class chosen.
2. May a VMaNGOS rate, which reflects vanilla, headline where Questie has none? **Default:** yes, labelled
   "community (VMaNGOS)"; Questie wins when both exist; Forever-new content never gets a vanilla rate.
3. Do you want a labelled "estimated yield per hour" for farming spots? **Default:** no (decision 9).
4. May later features ship more client art, such as talent, race, profession or faction icons? **Default:** icons only.
5. Is Forever content as v0.4.0, pushing loot research to v0.5.0, the order you want? **Default:** yes (decision 12).

## 7. How I work
- New expert input produces v(n+1) of this file with a changelog entry.
- A feature enters a plan only from this backlog with decision In and a release; a placeholder must be sized and
  given data first.
- Every feature carries BA-format acceptance criteria (numbered, testable, edge cases) and a named example, verified
  in the shipped DB before the plan is written:
  - **v0.3.0:** quest 176 (starts at Wanted Poster objects 68/156561, ends at NPC 240, item objective 1931); 166 (Defias
    chain); 6502/6602; 7787 (after 7785/7786); 7791 (repeatable); NPC 550 (patrol route).
  - **v0.4.0:** Forever-new item, NPC, instance, talent and race examples, chosen from the analyst's report in v4.
  - **v0.5.0:** Molten Core with Warlock + Waist shows Felheart Belt 16806; set Felheart Raiment 203; Hogger 448; The Deadmines.
  - **v0.6.0:** Linen Cloth 2589 (reagent for), Heavy Leather 4234 (created by; verify after 3A Task 8), Copper Ore
    2770 (gathered from), Greater Magic Essence 10939 (disenchanting).
  - **v0.7.0:** Elwynn Forest 12, Westfall 40 with Thor 523, Gryan Stoutmantle 234 (friendly), Mor'Ladim 522 (rare),
    Edwin VanCleef 639 (boss rank).
- A release ships only when every gate in §2 is met; a failed gate moves the feature, never the bar.

## Changelog
- v1 (2026-10-17): first backlog from the phase 3 spec, ROADMAP and five panel memos.
- v2 (2026-10-17): folded in the loot/raids, professions/economy and lore/exploration reviews.
  - The train ran to v0.7.0: loot, then crafting and economy, then lore, then glance and compare.
  - Crafting moved behind loot. The Home dashboard became the learning entry point.
  - Set browser became item set pages. Vendor stock merged into vendor economics. Zone rares filter merged into rare details.
  - Notes/favourites became pinned lists with export. Live rare timers stay Out; static respawn times are In.
  - Added "Involved in quests" to v0.3.0, 21 new features, the "New in Forever" badge, a farming ruling and a VMaNGOS ruling.
  - Screenshot bugs are listed in §3 as quality fixes, not features. Quest 176's objective is corrected to an item.
- v3 (2026-10-17): owner direction on WoW: Forever content.
  - Added principle 2 (Forever first) and "vanilla is never passed off as Forever" to the provenance principle.
  - Reserved v0.4.0 as "WoW: Forever content", with placeholders for camping, legacy points, new talents, new races and
    new zones/instances/gear. The badge moved there from loot research.
  - Loot, crafting, lore, and glance and compare each moved back one release, to v0.5.0 through v0.8.0.
  - Added "Recommended talent builds" as Out. Decision 12 sets the Forever release rule.
  - The v0.3.0 one-release question became part of decision 7; open question 5 now asks about the Forever-first order.
  - Placeholders are sized and given data in v4, after the analyst's report and the Forever ideation round.
