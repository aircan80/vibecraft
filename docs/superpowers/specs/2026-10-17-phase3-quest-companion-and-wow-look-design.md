# Phase 3 — Quest companion and the WoW look: design

Date: 2026-10-17. Owner requests (verbatim):
- "when looking at an area map to be able to see where quests are given (with the questie '!' symbol). When you
  click on it you can go to that quest." (built as 2B Task 15)
- "when looking at a quest to know who gives it and where it's turned in. Any item you need for that quest we should
  also be able to locate on the map somehow. If it's a quest chain I'd like to be able to see that somehow (have a
  UI / BA / and WoW expert determine best way to present this)"
- "have a wow game expert, a business analyst and 3 UI designers come up with a way to clean this up and make this
  more wow like. One thing I'd like is the icons for the 'menu' to be like wow (quests, items can be bag, areas can
  be map, etc.)"
- "icons can be taken from blizzard art"

Panel memos (WoW expert, BA, UI designers 1–3) are summarised in this document; the full memos are in the session
notes. Where the panel disagreed, the ruling is stated with its reason.

## 1. Intent
A player at a quest page should answer, without scrolling at 1280×800: where do I pick it up, what do I do and where,
where do I hand it in, what comes next. Zone maps show quest givers. The whole app should read as a WoW companion
(the cues players recognise instantly) without becoming a themed toy: readability beats atmosphere.

## 2. Goals / non-goals / anti-goals
Goals: (1) quest page with giver and turn-in on a map; (2) every objective (kill, collect, interact, explore) locatable
on the map, including the item sources behind a collect objective and the items you must carry; (3) the quest chain
(before / this / after, prerequisite groups, breadcrumbs, exclusive alternatives, children); (4) WoW-idiom rail icons
from client art; (5) a consistent WoW-like chrome (headings, panels, chips, bars, typography, parchment light theme);
(6) the information-architecture clean-ups that reduce noise.
Non-goals: progress tracking (no "3/10"), nothing reads the game client at runtime, no network.
Anti-goals: textures/frames copied from the game UI, fantasy fonts in body text, gold spreading over all chrome,
anything under 4.5:1 for small text, animations, a page that needs scrolling to find giver/turn-in/first objective.

## 3. Data (phase 3A, importer)
QuestieDB `foreverQuestDB.lua` carries per quest (questKeys): startedBy {creatures, objects, items}, finishedBy
{creatures, objects}, objectives {creatureObjective [(id, text, icon)], objectObjective, itemObjective,
reputationObjective, killCreditObjective [({ids}, baseId, text)], spellObjective}, triggerEnd {text, {zone:
[coords]}}, sourceItemId, requiredSourceItems, preQuestGroup, preQuestSingle, childQuests, inGroupWith,
exclusiveTo, nextQuestInChain, parentQuest, breadcrumbForQuestId, breadcrumbs, zoneOrSort, requiredSkill,
requiredMinRep/MaxRep, reputationReward, specialFlags (1 = repeatable), extraObjectives. NOTE: Questie objectives
carry no counts (counts live in the game's quest log); objective rows show the objective text and target without a
number. Shipped already: quest(…objectives_text…), quest_giver(quest_id, kind npc|object|item, entity_id, role),
quest_reward_item, item_related_quest, npc_spawn 124,486, object_spawn 53,600, item_npc_drop, item_object_drop
26,334.

New tables (schema v3):
- `quest_objective(quest_id, ordinal, kind creature|object|item|reputation|spell|explore, entity_id, text,
  credit_group)` — killCredit members share a credit_group (the base creature is ordinal's representative);
  reputation rows carry faction id + value in text; explore rows carry the trigger text, with their points in
  `quest_objective_point(quest_id, ordinal, zone_id, x, y)`.
- `quest_source_item(quest_id, item_id, kind provided|required)` — sourceItemId → provided; requiredSourceItems →
  required.
- `quest_relation(quest_id, related_id, kind pre_single|pre_group|next|child|parent|exclusive|group|breadcrumb_for|
  breadcrumb)` — references to quests absent from the DB are KEPT (the UI shows "Quest #id (not in database)");
  the importer counts them (`quest.relation.missing`).
- `quest` gains `repeatable INTEGER` (specialFlags & 1), `required_skill_id`, `required_skill_value`,
  `required_min_rep_faction/value`, `sort_label TEXT` (zoneOrSort < 0 → QuestSort label; > 0 → zone id stays in
  zone_or_sort).
- Indexes: quest_relation(related_id), quest_objective(entity_id, kind), quest_giver(entity_id, kind, role),
  quest_source_item(item_id).
- Rail icon textures: six client icons (see §5) fetched by fdid through the existing client-data pipeline and
  stored in `ui_asset(name, png)`; add to NOTICE.
Corrections (Questie's correction layers) apply to all of the above through the existing pipeline. Shipped-data
tests: the five acceptance quests (§8) have the expected relations/objectives.

## 4. Quest page (phase 3B)
Section order (ruling: UI-1's order, which the BA's C6 and the WoW expert's priority agree with):
1. Header block (shared skeleton, §6): icon "!" plate, name, "Lv 7 (req. 4)", zone chip, faction/class chips,
   "Repeatable" chip when set; below it the chain stepper "‹ Previous · This quest · Next ›  Step 2 of 4" (hidden
   for standalone quests).
2. Objectives (left) + Map (right), side by side (single column in compact, map above). Objective rows: numbered
   badge, verb from the kind (Kill / Collect / Use / Explore / Reputation / Cast), target name (link), the Questie
   objective text in muted, and a "where" line: "Fargodeep Mine · 23 spawns" / "Drops from Kobold Laborer 34 %,
   Kobold Tunneler 28 %, +3 more" / "Source unknown — not in the quest database yet." Below the rows: "You carry:
   Sealed Letter (given at start)" and "You will need: …" (no pins). Clicking a row filters the map to that
   objective (giver/turn-in at 50 % opacity; chip "Showing objective 2 ✕"). Collect objectives pin the top 5
   sources by rate with "Show all 9 sources"; killCredit groups are one row listing every credited NPC with all
   their spawns. Instance objectives: "Inside The Deadmines — open dungeon page ›". Escorts/events with no points:
   the giver location + "Escort".
3. "Given by" / "Turned in to": LinkTables (NPC/object/item, zone, coordinates); item starts read "Starts from item:
   Bloodstained Note — drops from ›" and the map pins the item's sources instead of "!".
4. "Quest chain": vertical tree, rows "Lv 10  Name — Zone", current = 3 px gold bar + bold; connectors "Requires all
   of:" (preQuestGroup), "Requires one of:" (preQuestSingle), "Choose one (exclusive):" joined by "or"
   (exclusiveTo), "Optional lead-in:" (breadcrumbs), "Unlocks:" (next + child quests, each once), "During:"
   (parentQuest). Built by walking prerequisite edges backward and forward (nextQuestInChain is a hint only);
   depth cap 50; cycles stop with "(loop in data)". Long chains show 2 steps either side + "Show 6 earlier steps".
   Standalone: "Standalone quest — no prerequisites or follow-ups." (hidden in compact).
5. "Rewards" (choice vs always, reputation), then "Related items" (collapsed).
Map: one MapView; zone chips above it list every zone involved with the glyphs it holds ("[Elwynn Forest ! ① ②]
[Stormwind City ?]"); opens on the giver's zone; an edge banner "Turn-in is in Stormwind City ›" switches. Pins
(same art both themes, 1.5 px #0C1424 outline + 1 px #F4EBD0 halo): Start = gold disc "!"; Turn-in = gold disc "?";
Kill = red #E5705C circle with the objective number; Collect = green #7CC46B diamond; Interact = blue #6FA8DC
rounded square; Explore = hollow ring. Legend chips under the map toggle layers. No map for a zone: "No map for
Deadwind Pass yet." + a coordinate list.
Keys: G/T/O/M/C/R jump to sections; 1–9 filter to objective n; Esc clears; [ ] previous/next in the chain; Z cycles
zone tabs (all declared in KeyMap and the help table).
Compact: stepper "‹ Prev · 2/4 · Next ›", map 400×267 above the objectives, zone chips as a dropdown, Given by /
Turned in to one line each, chain/rewards/related collapsed with counts.
Acceptance "scan test" (BA C7): at 1280×800 the giver, the turn-in and the first objective's location are visible
without scrolling; the controller verifies with screenshots of the five acceptance quests.

## 5. Rail icons and WoW chrome (phase 3B)
Rail icons = client textures (owner ruling), each on a 28 px navy #0C1424 plate in both themes, Interface/Icons art
cropped to the central 86 %, 24 px bicubic; GossipFrame art centred at native size. First choices (verify each
against the client listfile; second choices in brackets): Home INV_Misc_Rune_01 (Spell_Nature_AstralRecal); Items
INV_Misc_Bag_08 (Button-Backpack-Up); NPCs GossipFrame/GossipGossipIcon (INV_Misc_Head_Human_01); Quests
GossipFrame/AvailableQuestIcon (INV_Misc_Note_01); Zones INV_Misc_Map_01 (WorldMap/UI-World-Icon); Dungeons &
Raids INV_Misc_Head_Dragon_01 (INV_Misc_Key_03). States: idle full colour at 85 % (never desaturated); hover 1 px
gold hairline at 50 %; selected solid gold border + soft glow + 3 px gold bar on the rail edge; tooltips on the navy
card. The same icons at 16 px (no plate) head the matching sections and list pages. Fallback for an unverifiable
texture: original monochrome SVG per UI-2's descriptions.
Chrome (UI-2, agreed by UI-1 and the WoW expert): raised panels get a 1 px gold hairline at 35 % and 4 px radius;
section headings in the heading font, small caps, gold, with a 1 px gold rule fading right; page titles 20 pt (items
in quality colour); rate bars 6 px dark-gold→gold on bg.raised with faint 10 % ticks; chips outline-only (Elite gold,
Rare silver #C0C6CF, Rare Elite silver+gold text, Boss red #C8463C); status bar 22 px with a gold top hairline,
provenance left ("Forever beta 1.60.1 · 20,492 items"), theme/density toggles right. Typography: Marcellus +
Marcellus SC (OFL) for headings, fallback Cinzel, Georgia; Inter for everything else; never bundle Friz Quadrata.
Themes: dark "Slate" (bg.base #12161D; rare/epic small text #4A9EF0 / #B864F5); light "Parchment" (base #F3E9D2,
surface #EADCBB, raised #FBF5E6, text #2B2416, muted #6B5E45, link #1F4E8C, gold #8A6D1E for borders/bars/large
headings and #7A5E14 for gold text < 18 pt; quality on parchment poor #6B6B6B, uncommon #1A7A00, rare #0057B0, epic
#7B22B8, legendary #A85300). The tooltip card and rail plates stay navy in both themes. What not to do: no
textures/frames/portraits/cursors from the game UI; client art for icons only; no fantasy font in body/tables; gold
only for headings, selection, borders, bars; no pulsing glows; compact mode intact.

## 6. Information-architecture clean-up (phase 3B)
Shared page skeleton: header (icon plate, name in quality/difficulty colour, kind word, chips level / zone / faction
/ rank; pin + back on the right), a one-line facts strip of positive facts only (no "no vendor · no quests"), then
sections in a fixed order per type, hidden when empty, headings "Name (count)", 8 rows then "Show all 34".
Section orders: Item — Dropped by (merged with instance loot: instance shown as a chip, sorted by rate; the source
filter still applies only to NPC drop rows) / Sold by / Quest reward / Contained in / Starts a quest. NPC — Drops
(notable first: epics and quest items, then by category) / Sells / Quests (starts "!" and ends "?" merged) /
Location (map). Quest — §4. Zone — Map / Dungeons & Raids / Quests / NPCs (filters: rares, vendors, trainers) /
Objects. Instance — Encounters (click filters Loot) / Loot (by boss, slot toggle) / Trash / Quests / Entrance.
Home becomes a dashboard: big search, Pinned (grid), Recent (last 12), "What's new in build …" (per-kind counts),
data build footer. Master list: shows while searching, auto-collapses when a result is opened, Ctrl+B keeps it;
visible by default at ≥ 1600 px. Kind chips only in the search dropdown. The drop-source filter moves onto the
sections (a small control beside the heading) and "≠" gets a hover legend. Empty/loading copy per UI-3 (header
renders immediately; "Loading drops…" only after 150 ms). Compact: search + one page + pinned pages as a tab strip
(max 6; Ctrl+1..6 switch tabs), sections collapsed to "Heading (count)" except the first, always-on-top toggle. A
~300 px always-on-top mini tooltip for one pinned item is a later follow-up.

## 7. Rulings on panel disagreements
- Icons: client textures on navy plates (owner), SVG only as a fallback.
- Section order on the quest page: Objectives + Map before Given by / Turned in to (UI-1, WoW expert); the BA's
  "giver and turn-in first" is satisfied by the header stepper and the map's "!"/"?" pins being visible above the fold.
- Objective counts: none (Questie has none); UI-1's "×10" is dropped.
- "Dropped by" merged with instance loot (UI-3) keeps the Task 13 ruling that the source filter never hides instance
  loot rows.
- Fonts: Marcellus SC (UI-2) over Cinzel (UI-1); both OFL; Cinzel is the fallback.
- Home dashboard and master-list auto-collapse (UI-3) are in; the random featured item is out.

## 8. Acceptance quests (WoW expert)
Wanted: Hogger (176, poster start, turn-in elsewhere); The Defias Brotherhood chain (Westfall → Deadmines, verify
ids); Drakefire Amulet (6502, ~15 steps, Horde counterpart 6602 as an exclusive branch); Rise, Thunderfury! (7787,
after 7785/7786, item-gated); A Donation of Wool (~7791, repeatable, per-city variants); spare: the OOX-17/TN
distress beacon (item start, escort without points).

## 9. Order and sizing
- Plan 3A (importer + schema + shipped data + icon textures): ~6 tasks, runs now in the main checkout, in parallel
  with the end of phase 2B (Tasks 15 and 14, final review, merge).
- Plan 3B (UI: quest page, chain, objective pins, rail icons and chrome, IA clean-up, themes): ~12 tasks, starts after
  2B merges to main; same subagent-driven process; v0.3.0 at the end.
- Left to the builders: exact chain-walk algorithm, pin glyph rendering details, the Marcellus bundling (resource +
  licence file), the crop/plate rendering of client icons, QuestSort label table.
