# Product backlog and decisions, v5

Date: 2026-10-17. Owner: product manager. Inputs: phase 3 design spec (incl. addenda §10–11), ROADMAP, memos from the
WoW expert, BA and UI designers 1–3, plans 2B and 3A, the three research reviews (loot/raids, professions/economy,
lore/exploration), the owner's Forever direction ("let's make this the best WoW companion app ever"), and the
Forever round: the analyst's new-systems memo, the verified data-feasibility memo for build 1.60.1.70425 (it wins
where memos disagree), and the WoW experts' Forever systems and content ideation memos. v5 adds the PM memos in
`.superpowers/sdd/2026-10-17-phase3b-quest-page-and-wow-look/` (pm-forever-dungeons, pm-map-layers, pm-world-map,
pm-data-sources, pm-instance-maps), the data-source review, and the owner's decisions on them. Ids marked "verify"
are named in a memo but not yet checked against the shipped DB.

## 1. Product principles
1. A **research and learning tool** in the spirit of Wowhead: it describes the world (what exists, where, who, how
   often, what leads to what). It never prescribes what *you* should do next and never tracks progress.
2. **WoW: Forever first.** Forever's new content and systems (camping, legacy points, talents, races, zones,
   instances, gear) are first-class, not an afterthought to Classic data. New content is labelled as new, and every
   release names at least one Forever-new acceptance example.
3. **Offline and self-contained**: ships its own database; no network at runtime; nothing reads the game client at
   runtime. The one exception is an explicit, opt-in, read-only import step the user starts (decision 23).
4. **Trust through provenance**: every value names its source; disagreements are shown; knowledge is tracked per
   field, so a blank or zero never reads as "unknown"; estimates are never facts; vanilla or Classic Era data is
   never passed off as Forever data.
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
| **v0.3.0** (3A + 3B) | Quests on the map, the WoW look | Quest page redesign, quest chain, objective pins, giver/turn-in pins, "Involved in quests", patrol routes, client-art rail icons, WoW chrome + Marcellus, Parchment theme, IA clean-up, merged Dropped by, master-list auto-collapse, community drop rates, coordinates + copy-to-game; exceptions (decision 7): client refresh to 70450 + Forever dungeons slice (T17, T18) with encounter-order badges, hidden-item overlay + build-watch + AtlasLoot Classic Forever (T19), collector addon + import (T20) | Scan test on the five acceptance quests; chain tests cover AND/OR, exclusive, breadcrumb, cycle, missing id; icons verified; VMaNGOS licence in NOTICE; every VMaNGOS rate reads "Vanilla rate" (decision 19); 286978 and 869 read hotfix stats and 16806 stays Classic Era (decision 25); City of Dalaran (2959) lists its 9 bosses with "Level 28+"; every §3 fix closed |
| **v0.3.1** | Maps | World map drill-down (Azeroth › continent › zone), then map layers: world-to-map transform, instance entrances and exits, flight paths | Zones rail › Eastern Kingdoms › Westfall in three clicks, also keyboard-only; 20 NPCs agree within 1 point between VMaNGOS world spawns and Questie map percentages; Westfall shows The Deadmines entrance and Sentinel Hill with Thor 523. If it runs long, the layers ship separately and the world map never slips |
| **v0.3.2** | Dungeon maps | Atlas floor maps on instance pages: floor tabs, legend, credit caption (decision 26) | GPL-2.0 text shipped; NOTICE and every caption credit Dan Gilbert, Lothaer, Arith Hsu and the Atlas Team; the four Forever beta dungeons that Atlas draws show their maps |
| **v0.4.0** | **WoW: Forever systems** | Forever foundation, talent browser + calculator + Classic diff, races grid + race pages, Legacy page, camping page + composer, stat glossary + "has stat" filter, class spells by level, DBCache import spike | Foundation tests in §4.1; every Classic-vs-Forever diff names both builds; Legacy calculator stops at 16 points and the talent calculator at 51; Skyborne ChrRaces 95 and 96 each show five classes; re-imported on the live build if it exists at release time |
| **v0.4.1** | **WoW: Forever content** | New/Changed chips + context strip + Forever hub, dungeon & raid ladder with opening dates, instance discovery pages, item set pages with Compare with Classic, situational trinket finder, new recipes + XP food list, zone Forever header, PvP pages (Darkspear Islands + its two factions), appearance panel | Hyjal Summit (map 2981) shows The Wild King as Reported, never Known; Rotmender's Raiment (set 2133) lists its five pieces with a Hidden chip each; no name-match block on any instance page (decision 18) |
| **v0.5.0** | Loot research | Loot filter (class, slot, resistance), loot pools + world-drop bucket, dungeon & raid prep tab | Molten Core filtered to Warlock + Waist shows Felheart Belt with its "Classic Era value" label; items whose class or slot is hidden are counted, never silently included; Hogger's own drops lead and world drops are a separate bucket |
| **v0.6.0** | Crafting and economy | Crafting graph + skill strip, profession browser, recipe source resolver, vendor/trainer finder, vendor economics, gathering nodes, disenchanting | Skill colours match SkillLineAbility on three recipes; Copper Ore shows its nodes on the Elwynn map |
| **v0.7.0** | Lore and exploration | Zone gazetteer, flight-path layer, storylines, faction pages, "Mentioned in" + quest-text search, flavour and readable text, hunter pet panel (incl. Fox), rare details, fishing per zone, Home as a learning entry point + glossary | Elwynn lists its sub-areas with level bands; Westfall shows its flight master on the layer |
| **v0.8.0** | Glance and compare | Compact pinned tabs + always-on-top, item comparison, pinned lists (wishlist) + export, "what changed in this build" | Compact screenshots at 420×720; build diff verified on two real builds |

## 3. Quality fixes in flight (2B Task 14, not backlog features)
Each one is a v0.3.0 ship blocker if Task 14 leaves it open.
- **BUG-CSB-001 — Cozy Sleeping Bag quest chain missing (open, user-reported 2026-10-17).**
  The shipped database contains item 211527 and its reward quest 79976, "This Must Be The Place",
  plus quests 79192 ("Stepping Stones"), 79980 ("Scramble"), 79974 ("Wet Job") and 79975
  ("Eagle's Fist"), but contains no `quest_relation` rows referencing any of those quests.
  The chain renderer therefore has no recorded links to display. Confirm the full chain and ordering
  against the pinned Forever quest source, then trace whether relationships are missing upstream or lost
  during import; do not infer order from quest IDs or names. Add importer/shipped-data and chain-rendering
  regression coverage once verified. Acceptance: the reward quest exposes the verified connected chain,
  with every available step navigable and any genuinely unavailable steps identified honestly.
- Zone level bands read "1–60" for Elwynn and Westfall; zone header repeats "Zone #12 · Zone".
- AtlasLoot ordinals shown as "Slot" and "Trash · slot 24" instead of the equip slot.
- The world-drop pool is buried in Hogger's drops; recipes and trade goods show item level instead of required skill.
- Unresolved spell tokens in set bonuses (`$1293733s2`, `${$proccooldown/60}`).
- Map pins have no label, legend or hover text; spawn maps do not name the zone; Westfall shows a blank map block.
- Friendly NPCs mixed with hostiles and instance elites in zone NPC tables; Edwin VanCleef shown as "Rare Elite".
- The Recent dropdown stores half-typed fragments; "Westfall Deed" reads "Quest · iLvl 8"; search box out of sync
  with the page (verify); Thunderfury card lacks the Era footer and its Nature damage line (verify).

## 4. The Forever releases, sized (v0.4.0 and v0.4.1)
Verified in the shipped DB on 2026-10-17. 4,813 items show Classic Era stats with the footer "Stats from Classic Era
data; Forever values may differ". Rotmender's Garb (286978) and Worgenbane Talisman (273643) exist by name from
Questie, with no client data. Item sets 2133 and 2135 are absent. The stat map already names ids 31–136 except 83, 92,
98, 114, 115 and 132; ids 137–140 are unnamed.

### 4.1 Foundation (first, 4 tasks, L)
| # | Task | Data source | Size | Done when |
|---|---|---|---|---|
| F1 | Knowledge-state model: `field_knowledge(entity, id, field, state, source, data_version)`, six states (Known, Hidden by Blizzard, Not yet discovered, Reported, Unannounced, None), one text+icon chip widget | importer + app | M | A blank or zero never renders as unknown, and unknown never renders as blank (tests on 286978 and 16806); the importer only upgrades states |
| F2 | Era baseline diff: pinned Era 1.15.9.70003 tables beside the Forever build; table `forever_diff(kind, id, status new/changed, fields)`; both build ids in `meta` | wago.tools Era tables (2A pipeline): Item, ItemSparse, ItemSet, ItemSetSpell, AreaTable, Map, LFGDungeons, Faction, TaxiNodes, SkillLineAbility, Talent/TalentTab, CharBaseInfo | M | Diffs are computed, never typed; a fixture with one new, one changed and one unchanged row of each kind passes |
| F3 | Stat ids 31–140 named from WoWDBDefs `ItemStatType`; merged Hit (31) and Crit (32) labels. The hotfix overlay (3B Task 19) adds ids 101 (13056 Frenzied Striker), 125 (11 items: 7683 Bloody Brass Knuckles, 13019 Harpyclaw Short Bow, 273033 Gravespike Repeater), 126 (3: 5444 Miner's Cape, 9391 The Shoveler, 279836 Thorn Protecting Girdle), 129 (282778 Mark of the Red Flight), 134 (2: 2169 Buzzer Blade, 279835 Vine Pruner's Cloak) and 139 (4771 Harvest Cloak); 125–139 look like Era's situational Equip bonuses | ItemSparse StatModifier_bonusStat | S | No "Stat #n" in the shipped DB |
| F4 | Hidden items: an `Item` row without `ItemSparse` gets state Hidden; Era values are relabelled "Classic Era value"; the 2,433 new hidden items get pages from slot, icon, effects, appearance and set | Item, ItemXItemEffect, ItemModifiedAppearance, ItemSet | S | 286978 renders a page with slot, set and the lock line "Stats hidden until this item drops on a realm" |

### 4.2 v0.4.0 features, ranked
| # | Feature | Data source (verified unless marked) | Size |
|---|---|---|---|
| 1 | Talent browser + calculator (51 points, milestones 11/16/21/31, build string, nothing stored) + Diff vs Classic | TraitSystem 10, TraitTree, TraitNode, TraitNodeEntry, TraitDefinition→SpellName, TraitEdge, TraitCond, TraitCost, TraitCurrency 3820 (SourcedMax 51), SkillLineXTraitTree; Era Talent/TalentTab as baseline only | L |
| 2 | Races grid + race pages (2 active + 2 passive racials, start zone, Classic column) | ChrRaces 95/96, CharBaseInfo (filter unplayable rows), ChrRaceRacialAbility, AreaTable/Map (Zephras Isle); Era CharBaseInfo for the six new combos | M |
| 3 | Legacy page: perk calculator (16-point cap), challenges (131, by ruleset set), reward track | TraitSystem 45, trees 1187/1188/1189, TraitCurrency 4225 (SourcedMax 16), TraitCost 4845; TraitCurrencySource→Achievement, Criteria, CriteriaTree, SuperDistrictSetID 60/61 (ruleset reading: verify); RenownRewards CovenantID 48, Faction 2802, CurrencyTypes 3485 | M |
| 4 | Camping page (profession × tier matrix, campfire tiers, Blueprints) + composer (hypothetical party → coverage) | 36 summon-object spells 1307172–1307397 (SpellEffect Effect 76), SpellName, Spell descriptions, 56 placing items, 29 Blueprint items (RequiredSkill/Rank; 17 hidden), QuestSort 666 | M + S |
| 5 | Stat glossary + "has stat" filter + Classic→Forever stat mapping | F3; ItemSparse | S |
| 6 | Class spells by level ("new", "now baseline", "was a talent") | SkillLineAbility, Spell levels (verify table), Era baseline | M |
| 7 | DBCache import spike, reduced to a fallback note: the Raidbots overlay (T19) already covers hidden items | the user's WoW install, read-only | S |
Cut line: if v0.4.0 runs over, item 6 moves to v0.4.1; items 1–5 never move.

### 4.3 v0.4.1 features, ranked
| # | Feature | Data source | Size |
|---|---|---|---|
| 1 | New / Changed chips, context strip ("New here: 2 areas, 1 dungeon"), list filter, Forever hub page | F2 `forever_diff`; Questie Forever quests | M |
| 2 | Dungeon & raid ladder (Classic + Forever, level band, size, New badge, opening dates) | LFGDungeons, Map 2959/2999/2998/3065; Achievement category 14821 `Instance_ID` leaks 2981/2994/3001/3052 (Reported); Blizzard-announced bands and dates as cited curated facts (decision 24) | S |
| 3 | Instance discovery pages (header facts, bosses with states, coverage line "Loot: 0 confirmed items") | DungeonEncounter (35 new bosses: 4 beta dungeons, Sunken Temple 8, Deadmines 1) as Known; achievement boss names as Reported; F1 | M |
| 4 | Item set pages + Compare with Classic (moved from v0.5.0) | ItemSet (+55, incl. 2133, 2135), ItemSetSpell, Spell descriptions, Era baseline; hidden members via F4 | M |
| 5 | Situational trinket finder (vs creature type, environment) | Stat ids 125–140 (e.g. 128 Attack Power against Undead); ItemXItemEffect→Spell→SpellEffect (spell 1292252 on 273643; aura mask: verify) | M |
| 6 | New recipes by skill band + XP food list + Comprehension scrolls (list only; the graph stays v0.6.0) | SkillLineAbility diff vs Era (7,847 vs 6,143), SpellReagents, 3A craft tables; XP aura id and Comprehension skill line: verify | M |
| 7 | Zone "Forever" header: level band, new sub-areas | AreaTable diff, ContentTuning (verify), UiMap | S |
| 8 | PvP pages: Darkspear Islands battleground, Darkspear Raiders and Theramore Expeditionary Force (pulled from v0.7.0) | Map (Darkspear Islands), Faction, FactionTemplate, CurrencyTypes; ItemExtendedCost, BattlemasterList: verify; vendor lists Not yet discovered | M |
| 9 | Appearance panel ("Shares appearance with N items") | ItemModifiedAppearance, TransmogSet/TransmogSetItem (7/63) | S |
Cut line: items 8–9 move to v0.5.0 if v0.4.1 runs over.

### 4.4 Not until data exists (parking lot)
These enter only through a data update, as Reported first and Known once corroborated. The crowd-data paths are:
(a) Questie Forever updates, already our quest/NPC source; (b) an AtlasLoot Forever module when published;
(c) public hotfix-cache aggregators, adopted for Raidbots' cache in T19 (others remain cross-check only);
(d) our own collector addon (T20), with the opt-in DBCache import as a fallback; (e) the live-build re-import from
4 Nov 2026.
- Launch-instance bosses beyond DungeonEncounter and the achievement leaks; all Forever boss and trash loot: (a)(b)(c).
- Blueprint drop sources for the 29 Blueprints: (a)(b).
- Stats, names and quality of the 12,618 hidden items (2,433 new): (c)(d), one data update at a time.
- Givers, objectives and rewards of the ~1,800 QuestV2 ids Questie lacks; changed rewards on old chains: (a).
- Trainer lists and costs, PvP vendor stock, reputation gains, tameable Forever creatures (Fox): (a)(c).
- Camp buff values and stacking rules beyond spell text: (e), or Blizzard's cited general rule.
- Encrypted content (66 items, 90 quests, 872 spells): (e).

## 5. Prioritised backlog
Value = to a researching player. Effort S ≤ 1 task, M 2–3, L 4+. VM = VMaNGOS SQLite world DB. Forever rows are
sized in §4 and listed here once.

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
| Community drop rates ("Vanilla rate") | H | M | VM loot templates | 0.3 | In | Replaces "rate unknown"; labelled vanilla |
| Coordinates + /way + in-game links | H | S | none | 0.3 | In | Bridges research to the game |
| Client refresh to 70450 + Forever dungeons slice | H | M | Map, LFGDungeons, ContentTuning, DungeonEncounter | 0.3 (T17, T18) | In | Owner bug report; boss names exist in the client, loot does not |
| Encounter-order badges on boss lists | M | S | DungeonEncounter, AtlasLoot | 0.3 (T18) | In | "Encounter order in the game client", never "kill order" |
| Hidden-item overlay (Raidbots DBCache) | H | M | Raidbots mirror, pinned by SHA-256 | 0.3 (T19) | In | Reveals 3,173 Era-fallback and 626 no-data items today |
| Build-watch script | H | S | patch service, wago, Raidbots, QuestieDB | 0.3 (T19) | In | Our data lagged because nothing said a build moved |
| AtlasLoot Classic Forever replaces Hoizame | H | S | fork, GPL-2.0 | 0.3 (T19) | In | Forever boss loot, labelled "AtlasLoot Forever, unverified" |
| Collector addon + import | H | M | own observations | 0.3 (T20) | In | Server-side facts only players can see; beta run before ~22 Oct |
| QuestieDB re-pin weekly and at launch | H | S | QuestieDB | process | In | The gap was the pin's age |
| World map drill-down | H | M | UiMap, UiMapArt, UiMapAssignment | 0.3.1 | In | Owner: "more intuitive than having to search" |
| World-to-map transform | H | M | UiMapAssignment | 0.3.1 | In | Enables entrances and flight paths |
| Instance entrances and exits on zone maps | H | S | VM areatrigger_teleport, client AreaTrigger (verify) | 0.3.1 | In | Owner request; moved from v0.4.1 |
| Dungeon floor maps (Atlas) | H | M | Atlas map module, GPL-2.0 | 0.3.2 | In | Owner approved with credit (decision 26) |
| Forever foundation (F1–F4) | H | L | §4.1 | 0.4.0 | In | Without it every Forever page lies or looks broken |
| Talent browser + calculator + diff | H | L | Trait* (§4.2) | 0.4.0 | In | Every returning player's first question |
| Races grid + race pages | H | M | ChrRaces, CharBaseInfo, ChrRaceRacialAbility | 0.4.0 | In | Small, certain data; showcases Skyborne |
| Legacy page + calculator | H | M | TraitSystem 45, Achievement, RenownRewards | 0.4.0 | In | The account system everyone asks about |
| Camping page + composer | H | M | camp spells, Blueprint items | 0.4.0 | In | No Classic analogue; client-backed |
| Stat glossary + "has stat" filter | H | S | ItemSparse | 0.4.0 | In | Merged hit/crit misreads otherwise |
| Class spells by level | M | M | SkillLineAbility | 0.4.0 | In | Below the cut line |
| DBCache import spike | L | S | user's install | 0.4.0 | In (fallback note) | The Raidbots overlay covers hidden items; kept as the fallback if Raidbots disappears |
| Forever v0.4.1 content (§4.3, 9 features) | H | L | §4.3 | 0.4.1 | In | Places new content among Classic, with states |
| Loot filter: class, slot, resistance | H | S | ItemSparse (shipped) | 0.5 | In | "What drops for me here"; hidden fields counted |
| Loot pools + world-drop bucket | H | M | VM reference ids, groupid | 0.5 | In | A mob's own loot vs a shared pool is a trust issue |
| Dungeon & raid prep tab | H | M | relations, objectives | 0.5 | In | Quests, attunements, keys tied to an instance |
| Crafting graph + skill strip | H | M | craft tables (3A T8) | 0.6 | In | Builds on the v0.4.1 recipe list |
| Profession browser | M | M | SkillLine, SkillLineAbility | 0.6 | In | Browse recipes by skill |
| Recipe source resolver | H | M | VM npc_trainer, npc_vendor | 0.6 | In | Trainer, vendor, drop or quest, with cost |
| Vendor/trainer finder | H | S | npc_flags (shipped) | 0.6 | In | Role filter + Services map toggle |
| Vendor economics | M | S | ItemSparse, VM npc_vendor | 0.6 | In | Price, stack, limited stock |
| Gathering nodes | H | M | VM gameobject loot, Lock | 0.6 | In | "Gathered from" + node map |
| Disenchanting | M | M | VM disenchant loot | 0.6 | In | Enchanters' core question |
| Zone gazetteer | H | M | AreaTable, TaxiNodes | 0.7 | In | Zone page as hub; extends the v0.4.1 header |
| Flight-path layer | H | M | TaxiNodes, TaxiPath, TaxiPathNode | 0.3.1 | In | Owner request; moved from v0.7.0 |
| Storylines browser | M | M | quest_relation, QuestLineXQuest | 0.7 | In | Follow an arc as one piece |
| Faction pages | M | M | Faction DB2 | 0.7 | In | Reputation sources; PvP pair ships in v0.4.1 |
| "Mentioned in" + quest-text search | M | M | quest text index | 0.7 | In | Learning by name |
| Flavour and readable text | M | S | ItemSparse, PageText | 0.7 | In | Lore at a glance |
| Hunter pet panel | M | S | CreatureFamily (incl. 319 Fox) | 0.7 | In | Tameable, family |
| Rare details (spawns, placeholders, respawn) | M | S | Questie, VM spawntimesecs | 0.7 | In | Static respawn time is data, not a tracker |
| Fishing per zone | L | S | VM fishing loot | 0.7 | In | Fits the gazetteer |
| Home learning entry + glossary | M | M | counts, tiles | 0.7 | In | Adds a "recorded in this data update" tile |
| Compact pinned tabs + always-on-top | H | M | none | 0.8 | In | Glance while playing |
| Item comparison | M | M | item stats | 0.8 | In | Hidden stats read "hidden", never a delta |
| Pinned lists (wishlist) + export | M | S | local prefs | 0.8 | In | Replaces notes/favourites |
| What changed in this build | M | S | F2 diff, build vs build | 0.8 | In | Reuses the foundation; effort drops from M |
| Collections browser (mounts, companions) | M | M | Mount (141), sources server-side | — | Later | Sources unknown at launch |
| Hardcore ruleset note | L | S | cited curated text | — | Later | Hardcore arrives in winter 2026/27 |
| Legendary component tree | M | L | chain + crafting + sources | — | Later | Composes three features; legendary details unannounced |
| CDN/CASC mining (port the wowsims db2tool design) | M | L | patch service, CDN | — | Later | Design page now; build only if wago fails or changes terms |
| Rates from aggregated collector data | M | M | collector files | — | Later | After launch |
| Boss positions from the collector | M | M | collector | — | Later | Probably blocked: no dungeon UiMaps (verify in the T20 beta run) |
| CMaNGOS disagreement report | L | M | GPL-3.0 | — | Later | Build-time report only, never in the shipped file |
| Spawn-cluster facts | M | M | spawns, VM respawn | — | Later | After gathering nodes |
| Mini tooltip window | M | M | none | — | Later | After pinned tabs |
| Upgrade finder | M | L | stat weights | — | Later | Needs a character; loot filter covers the research half |
| Localisation, 3D models | L | L | locales, asset pack | — | Later | Size, art redistribution |
| Name-matched items on instance pages | M | S | name match | — | Out | A guess, not provenance (decision 18) |
| Retail-client dungeon floor art | — | — | retail client | — | Out | Wrong layouts for reworked dungeons; nothing for Forever's |
| Wowhead, fan sites, Blizzard Game Data API as shipped sources | — | — | — | — | Out | Test oracle or cross-check only |
| Travel times, fastest route, recommended flight path | — | — | — | — | Out | Guide features (decision 8) |
| Challenge checklist ticks, collected appearances, death tracking | — | — | account data | — | Out | Stores or reads progress (decision 17) |
| "Best alt for points", "best perks", "best camp", recommended talent builds | L | M | opinion | — | Out | Recommenders (decision 17) |
| Farming route loop, yield/hour | L | M | estimates | — | Out | Prescriptive estimate (decision 9) |
| Prose patch notes, global changelog feed | L | M | hand-typed | — | Out | Changes appear in context, from data diffs |
| Live rare timers, auto-update | L | M | network | — | Out | Breaks offline |
| Quest progress, next-quest advice, level routes, XP rates | — | — | — | — | Out | Leveling guide (decision 8) |

## 6. Decisions log
1. **Objectives + map above Given by / Turned in to.** The stepper and "!"/"?" pins above the fold meet the BA's concern.
2. **No objective counts.** Questie has none.
3. **Rail icons from client art on navy plates**; SVG only as fallback. Client art is for icons only (owner), with
   one explicit exception: Atlas's community-drawn dungeon maps (decision 26).
4. **Marcellus SC headings, Inter body.** Cinzel fallback; Friz Quadrata never ships.
5. **Parchment replaces the current light theme**; dark "Slate" stays the default.
6. **Merged Dropped by**: instance loot joins NPC drops as chips; the source filter applies to NPC rows only.
7. **v0.3.0 scope is frozen and ships as one release.** Recorded exceptions:
   - the vanilla label (decision 19);
   - the client refresh to 70450 and the Forever dungeons slice, from the owner's bug report;
   - the data-source task T19 and the collector T20, by controller ruling, because the beta ends about 22 Oct.
   New owner requests go to v0.3.1 and later.
8. **What "leveling guide" means here**: Out when a feature needs your character, your progress or a recommended
   order. Describing quests, talents and races as they exist is research.
9. **Farming**: facts In; route loops and yield-per-hour estimates Out (owner confirmed).
10. **VMaNGOS**: 3A Task 9 reads the official SQLite release, pinned by tag and checksum; other tables land with
    their first consumer. VMaNGOS rates may headline when Questie has none (owner confirmed).
11. **World drops are never a mob's own loot.** Pools show as a separate bucket with their level range.
12. **Forever comes right after the quest release (owner confirmed).** It ships only on confirmed data and never
    waits on unconfirmed data; loot research does not jump the queue.
13. **Crafting follows loot (v0.6.0)**; Home becomes the learning entry point in v0.7.0.
14. **Acceptance examples come from real data** and are checked in the shipped DB before a plan is written.
15. **The six-state, per-field knowledge model is the data model for Forever content** (controller ruling). States
    upgrade with data updates and downgrade only on a retraction; a newly filled field shows "Recorded in data update <date>".
16. **Every Classic-vs-Forever diff is computed from two pinned builds**, and the Classic side names its build.
17. **Planners In, recommenders Out, no stored progress.** Talent, Legacy and camp calculators take hypothetical
    input and store nothing; no character profile (owner confirmed).
18. **No name-matched items on instance pages in v0.4.** Any list on an instance page reads as loot, whatever its
    label. The page offers a plain "Search items named 'Krol'dok'" link instead, which is the user's own query. Set
    pages already carry the set name. Revisit once the first confirmed drops arrive.
19. **All VMaNGOS rates read "Vanilla rate"**, and on dungeon bosses "Vanilla rate; Forever loot reworked", because
    Blizzard reworked all dungeon loot. *This changes v0.3.0*: it is a label in the existing provenance tooltip.
20. **Hidden items keep their Era values, labelled "Classic Era value"**; stat filters and comparisons exclude them
    and say how many they skipped.
21. **The Forever theme splits into v0.4.0 (systems) and v0.4.1 (content).** *This changes v0.5.0–v0.7.0*:
    - Item set pages move from v0.5.0 to v0.4.1, because 55 new sets and 2,246 hidden members need the knowledge model.
    - A new-recipes list moves ahead of v0.6.0.
    - The PvP factions and the zone Forever header move ahead of v0.7.0.
    - "What changed in this build" shrinks to S because it reuses the foundation diff.
22. **Talents read Trait* tables only.** Era Talent/TalentTab rows (ClassID 0 in Forever) are the Classic baseline,
    nothing more. Beta tooltips above level 30 carry "Seen in beta 70425" until the live build confirms them.
23. **DBCache.bin from the user's install is a fallback, not a feature**, now that the Raidbots overlay exists. It is read-only, per-machine and optional, and only an explicit import
    the user starts may read it. Principle 3 now states this exception.
24. **Curated facts** (opening dates, announced level bands, Hardcore rules) live in one reviewed data file. Each
    fact carries a citation and a state (Known from Blizzard, Unannounced or Reported) and is never mixed into a diff.
25. **Item data precedence**: Blizzard hotfix rows (Raidbots cache, header build = `build.lock` build) > Forever
    base ItemSparse > Classic Era fallback > Questie name. Revealed rows accumulate across snapshots.
    - Footers: "Forever stats revealed by a Blizzard hotfix · beta 70450 · cache <date>", "Classic Era values.
      Forever stats are still hidden." or "Stats hidden until this item drops on a realm". Beta data is never called "live".
    - The six-source drop-rate order is unchanged. The AtlasLoot Classic Forever fork takes the two AtlasLoot slots,
      labelled "AtlasLoot Forever". Its Forever boss loot is Reported ("unverified") until Questie or the
      collector confirms a row.
26. **Atlas dungeon maps approved by the owner (2026-10-17)** for v0.3.2. They are an explicit exception to
    decision 3, because they are community-drawn maps, though based on Blizzard minimap data.
    - Conditions: the GPL-2.0 text ships with them; NOTICE and every map caption credit Dan Gilbert, Lothaer,
      Arith Hsu and the Atlas Team; no courtesy email for now (owner).
    - Our boss list stays the authority, and we draw no pins of our own on Atlas images.
27. **Encounter order is a fact, not advice.** Boss lists carry numbered badges in the client's encounter order,
    captioned "Encounter order in the game client" (T18).

## 7. Open questions for the owner
Answered and recorded: no character profile (decision 17); VMaNGOS headline rate, labelled (decisions 10, 19); no
yield per hour (9); client art for icons only (3), except Atlas maps (26); Forever at v0.4 ahead of loot (12).
Settled by controller ruling and reported to the owner: Raidbots' hotfix cache is adopted (decision 25).
1. After the DBCache spike, do you want an opt-in "import from my WoW install" step in a later release?
   **Default:** decide on the spike memo; nothing reads the install until you say yes.
2. At launch (4 Nov), if Raidbots publishes no cache path for the launch product, should we fall back to your own
   client's cache through the opt-in import? **Default:** yes, as a one-off import you start.
3. Should hidden items keep their Classic Era stats on screen? **Default:** yes, labelled (decision 20).
4. Should v0.4.0 wait for the live build (4 Nov 2026)? **Default:** no; ship on the current build with the beta label.
   The live re-import is a data update.
5. Are cited, hand-curated facts acceptable for opening dates and announced level bands? **Default:** yes (decision 24).

## 8. How I work
- New expert input produces v(n+1) of this file with a changelog entry.
- A feature enters a plan only from this backlog with decision In and a release; a placeholder must be sized and
  given data first.
- Every feature carries BA-format acceptance criteria (numbered, testable, edge cases) and a named example:
  - **v0.3.0:** quest 176 (starts at Wanted Poster objects 68/156561, ends at NPC 240, item objective 1931); 166 (Defias
    chain); 6502/6602; 7787 (after 7785/7786); 7791 (repeatable); NPC 550 (patrol route).
  - **v0.3.0 data exceptions:** 286978 and 869 (hotfix), 16806 (stays Classic Era), 273643 (hotfix); City of Dalaran
    2959 (9 bosses, Level 28+).
  - **v0.3.1:** Westfall 40 (The Deadmines entrance, Sentinel Hill with Thor 523), Eastern Kingdoms 1415.
  - **v0.3.2:** City of Dalaran (Atlas floors A/B), The Deadmines.
  - **v0.4.0:** items 286978 (Rotmender's Garb, Hidden until T19 reveals it; pick another hidden item then) and 16806 (Felheart Belt, Classic Era value); TraitSystem 10
    with currency 3820 (51 points); TraitSystem 45 with trees 1187–1189 and currency 4225 (16 points); RenownRewards
    CovenantID 48 (ranks 25/40/55); ChrRaces 95 and 96; camp spells 1307172–1307397 and Blueprint: Fermenter (Alchemy
    140); stat ids 31/32 and 128.
  - **v0.4.1:** maps 2959 (City of Dalaran), 2999, 2998, 3065; achievement-leaked 2981 (Hyjal Summit, The Wild King)
    and 3052 (Barrow Deeps); ItemSet 2133 (Rotmender's Raiment: 286978, 286980, 271207, 271214, 286979) and 2135
    (Krol'dok Battlegear); 273643 (Worgenbane Talisman, spell 1292252); Faction Darkspear Raiders (id: verify).
  - **v0.5.0:** Molten Core with Warlock + Waist shows Felheart Belt 16806; Hogger 448; The Deadmines.
  - **v0.6.0:** Linen Cloth 2589 (reagent for), Heavy Leather 4234 (created by; verify after 3A Task 8), Copper Ore
    2770 (gathered from), Greater Magic Essence 10939 (disenchanting).
  - **v0.7.0:** Elwynn Forest 12, Westfall 40 with Thor 523, Gryan Stoutmantle 234 (friendly), Mor'Ladim 522 (rare),
    Edwin VanCleef 639 (boss rank), CreatureFamily 319 (Fox).
- A release ships only when every gate in §2 is met; a failed gate moves the feature, never the bar.

## Changelog
- v1 (2026-10-17): first backlog from the phase 3 spec, ROADMAP and five panel memos.
- v2 (2026-10-17): folded in the loot/raids, professions/economy and lore/exploration reviews; train to v0.7.0;
  screenshot bugs moved to §3; quest 176's objective corrected to an item.
- v3 (2026-10-17): owner direction on WoW: Forever. Added principle 2, reserved v0.4.0 for Forever content with
  placeholders, and moved later themes to v0.5.0–v0.8.0.
- v4 (2026-10-17): Forever systems sized on verified build data.
  - The placeholders are replaced by §4: a four-task foundation first, then ranked features with data sources,
    acceptance ids and cut lines.
  - Forever splits into v0.4.0 (systems) and v0.4.1 (content); there is a parking lot for data that does not exist yet.
  - Legacy drops from L to M, because it lives in Trait* and Achievement tables; camping is M, from spells and items.
  - Talents read Trait* tables, not Talent/TalentTab.
  - Forced changes elsewhere:
    - v0.3.0 labels VMaNGOS rates "Vanilla rate".
    - Item set pages move from v0.5.0 to v0.4.1.
    - A new-recipes list, the PvP factions and the zone Forever header move ahead of their themes.
    - The v0.5.0 loot filter counts items with hidden fields.
    - "What changed in this build" shrinks to S.
  - Decisions 15–24 added, including the name-match ruling (no block, a search link instead). Principle 3 gains the
    opt-in import exception and principle 4 the per-field knowledge rule.
  - Owner answers recorded; five new open questions.
- v5 (2026-10-17): owner requests and data sources after the Forever round.
  - v0.3.0 gains recorded exceptions (decision 7): the client refresh to 70450 and the Forever dungeons slice,
    encounter-order badges, the hidden-item overlay, build-watch and AtlasLoot Classic Forever (T19), and the
    collector addon (T20).
  - New v0.3.1 "Maps": the world map drill-down first, then the transform, entrances and exits, and flight paths.
    The flight-path layer moves from v0.7.0 and entrance pins from v0.4.1.
  - New v0.3.2 "Dungeon maps": Atlas floor maps, approved by the owner with credit and without the email
    (decision 26, an exception to decision 3).
  - Decisions 25 (item data precedence and footers), 26 and 27 added; decisions 3, 7 and 23 amended.
  - Backlog rows added for the above, plus Later rows (CDN/CASC design, collector rates, collector boss positions,
    CMaNGOS report) and Out rows (retail floor art, third-party shipped sources, travel-time advice).
  - The DBCache spike shrinks to a fallback note; parking-lot paths (c) and (d) updated; open question 2 replaced.
