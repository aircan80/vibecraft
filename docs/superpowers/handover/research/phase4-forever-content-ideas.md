# Phase 4: WoW: Forever content ideas (content expert, 2026-10-17)

Inputs: `wow-forever-new-systems.md` and backlog v3. Scope: zones, instances, gear, recipes, quests. "Verify" marks a DB2 guess to check against build 70425 before planning. Sizes follow the backlog: S ≤ 1 task, M 2–3, L 4+.

## 1–2. Player questions and features, by area (ranked)

**Zones** (Zephras Isle, Riverglades, Hyjal, Dalaran, Shen'Dralas)

| # | Question | Feature | Data | Size |
|---|---|---|---|---|
| 1 | What is new in this zone, and at what levels? | Zone page "Forever" header: level band, new sub-areas outlined on the map | AreaTable, ContentTuning for levels (verify), UiMap/UiMapAssignment, WorldMapOverlay | S |
| 2 | Which old zones gained new areas? | "Changed areas" list per zone | AreaTable diff vs a Classic Era client baseline | M |
| 3 | What is here: entrances, flight points, quest hubs? | Zone hub counts + entrance pins | AreaPOI, Map, TaxiNodes; quest hubs server-side | M |
| 4 | Which reputations belong to Hyjal or Dalaran? | Faction stub with "sources not yet discovered" | Faction DB2; rep sources server-side | S |

**Dungeons and raids** (nine dungeons, Barrow Deeps, Hyjal Summit)

| # | Question | Feature | Data | Size |
|---|---|---|---|---|
| 1 | Which dungeons exist for levels 13–60, Classic and Forever together? | Dungeon & raid ladder: one table with level band, size, location, New badge | LFGDungeons, Map, ContentTuning (verify); Blizzard-announced bands as labelled fallback | S |
| 2 | Who are the bosses and what drops? | Instance page with discovery states (§3) | JournalEncounter likely empty; boss names and loot server-side / crowd-sourced | L |
| 3 | When does it open? | "Opens 9 Dec 2026" line on raid pages | Hand-curated, Blizzard-sourced table with citation | S |
| 4 | Where is the entrance? | Entrance pin on the zone map | AreaPOI or AreaTrigger (verify) | S |

**Gear, sets and trinkets** (about 4,800 items, 48 new and 26 reworked sets)

| # | Question | Feature | Data | Size |
|---|---|---|---|---|
| 1 | What does this item do? | Tooltip with an explicit "Stats hidden" state (§3) | ItemSparse; zero vs absent must be verified | S |
| 2 | How did my Classic set change? | Set page "Compare with Classic" toggle: pieces and bonus text side by side | ItemSet, ItemSetSpell, Spell descriptions; Era baseline | M |
| 3 | Which trinkets work against Undead, or underwater? | Situational trinket finder by creature type or environment | ItemEffect → Spell → SpellEffect / aura target-type mask (verify); may itself be obfuscated | M |
| 4 | What do "parry reduction" and merged hit mean? | Stat glossary + stat filter with the new stat types | ItemSparse stat type ids; new mapping | S |

**Recipes and XP food** (600+ recipes, Mage Comprehension)

| # | Question | Feature | Data | Size |
|---|---|---|---|---|
| 1 | What is new for my profession, by skill band? | Profession page "New in Forever" list grouped by skill band | SkillLineAbility diff vs Era, SpellReagents, recipe items | M |
| 2 | Which foods give bonus XP, and how much? | XP-food table: food, XP %, stats, duration, skill | Spell auras: an XP-percent aura (verify id), SpellDuration | S |
| 3 | What does this new reagent go into? | Reagent reverse lookup | Existing craft tables | S |
| 4 | Which scrolls can a Mage read? | Comprehension scroll list | SkillLine + SkillLineAbility for the minor tradeskill (verify) | S |

**Quests** (1,000+ new, changed rewards on old chains)

| # | Question | Feature | Data | Size |
|---|---|---|---|---|
| 1 | Which quests in this zone are new? | Zone quest list with New badge and filter | QuestV2 ids, QuestPOIBlob/QuestPOIPoint (verify); titles may be server-side | M |
| 2 | Did this old quest's reward change? | "Changed" chip: "Reward added: caster ring" | Forever rewards server-side; Questie Forever or crowd-sourced, compared with VMaNGOS | M |
| 3 | What chain does it belong to? | Existing chain stepper fed by questlines | QuestLineXQuest order | M |
| 4 | Where are its objectives? | Objective areas from client POIs | QuestPOIBlob/QuestPOIPoint | M |

## 3. The "unknown until discovered" model

**Principle.** Knowledge is tracked per field, not per page. A page with a known name, location and level band is a real page even when its bosses are unknown. Blank or "0" never means unknown.

**States**, stored per entity field and rendered as text chips with an icon, never colour alone:

| State | Meaning | Wording on the page |
|---|---|---|
| Known | Client data, Blizzard or verified community source | Value + source chip, as today |
| Hidden by Blizzard | Item exists, stats obfuscated | "Stats hidden until this item drops on a realm" (lock icon) |
| Not yet discovered | Exists, no source has recorded it | "Not yet recorded. A later data update fills this in." |
| Reported | Third-party claim, unverified | "Reported by lfcarry.com, unverified", collapsed |
| Unannounced | Blizzard has only promised it | "Announced for spring 2027, no details" |
| None | Verified empty | "No rewards" |

**Instance with no announced bosses.** The header shows name, location, level band, size, opening date and entrance pin. The Bosses section reads "Bosses not yet discovered. Forever has no Dungeon Journal, and bosses stay secret until first encounter." A coverage line follows: "Bosses: not yet recorded · Loot: 0 confirmed items". This describes our data, not the player's progress. Reported lists, such as the 13 Hyjal names, sit collapsed under "Reported, unverified". Items whose names match the instance, such as Krol'dok Battlegear, may appear only in a collapsed "Name match, not a confirmed drop" block. The PM should rule on this one heuristic.

**Obfuscated items.** The tooltip keeps name, icon, quality, slot, item level and required level when present. The stat block becomes one locked line. Stat filters skip these items and say so: "312 items with hidden stats not matched". Comparison shows "hidden", never a delta.

**Quests Questie does not know.** The quest page renders from client data: id, questline position, POI areas. Giver, turn-in and rewards read "Not yet recorded". The zone "!" layer omits these quests and counts them in a footnote.

**How refreshes fill it in.** There is no network at runtime, so discovery arrives with each shipped database.
1. A `field_knowledge` table stores entity, field, state, source and the data version it came from.
2. The importer only upgrades states, from Not yet discovered or Reported to Known. It downgrades only when a source retracts.
3. A newly filled field shows "Recorded in data update 2026-11-20" for one data version.
4. A Home tile counts the update: "14 bosses, 220 stat lines, 85 quest givers recorded".
5. Beta-only facts carry "Seen in beta 70425" until a live build confirms them.

## 4. "New in Forever" without a changelog

- **Two chips.** "New" means absent from the Classic Era baseline. "Changed" means present in both with differing fields, and its hover names the change.
- **Baseline.** Diff against a Classic Era client extract, not only VMaNGOS absence. VMaNGOS is 1.12, so later Era additions would show as false "New".
- **One-line context strip** on zone and instance pages: "New here: 2 areas, 34 quests, 1 dungeon · Changed: 6 quest rewards". Each count links to a filtered list.
- **List filter** everywhere: All, New in Forever, Changed, Classic unchanged.
- **Compare with Classic** toggle on item, set and quest pages shows only differing fields, side by side.
- **Forever hub page** lists new zones, instances, sets and recipe counts as a catalogue.
- **Anti-changelog rules.** Changes appear in context only and come from data diffs only. There is no prose patch note and no global feed by default.

## 5. Top 5

1. **Field-level knowledge states + instance discovery page (L).** Without it, every Forever page either lies or looks broken.
2. **New/Changed chips on an Era baseline, with the context strip (M).** This makes Forever first-class on every existing page cheaply.
3. **Dungeon & raid ladder with opening dates (S).** It places nine new dungeons among Classic ones as facts, with no character needed.
4. **New recipes + XP-food table (M).** Recipe data is client-side and least likely obfuscated, so it ships with high confidence.
5. **Situational trinket finder + stat glossary (M).** This mechanic is unique to Forever and is pure research, not advice.

Next in line: the quest reward diff, which waits on server-side reward data.

## 6. Risks and data gaps

- **Obfuscation scope.** It may cover spell effects, set bonuses or recipe outputs, not only stats. Measure zero vs absent in ItemSparse first.
- **Reworked Classic loot.** All dungeon loot changed, so VMaNGOS rates on Classic dungeons are vanilla. Label them "Vanilla rate; Forever loot reworked".
- **Server-side gaps.** Boss names, loot, quest givers, rewards and trainer lists are not in the client.
- **Questie coverage.** It stays thin for weeks after 4 Nov, and Midnight-style addon limits may slow its collection.
- **Beta cap.** Content above level 30 is unverified, and the launch build needs a re-import.
- **No baseline yet.** An Era client extract is a new import.
- **Third-party lists.** The Hyjal boss list is low confidence and must stay "Reported".
- **Crowd-sourced licence.** Any community dataset needs a licence check.
- **Refresh cadence.** Discovery reaches users only through shipped data updates.
- **Unconfirmed names.** Riverglades vs "Riverlands" and Shen'Dralas levels are open.
