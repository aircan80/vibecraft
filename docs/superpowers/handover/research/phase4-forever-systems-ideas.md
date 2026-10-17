# Phase 4: Forever systems ideation (WoW expert, 2026-10-17)

Scope: camping, Legacy, talents/classes, races, transmog/collections, PvP reps, Hardcore, merged stats. Sizes follow the backlog (S ≤ 1 task, M 2–3, L 4+). Data tags: **client** (DB2 in 70425, verify), **server/crowd** (not in the client), **unknown** (until discovered).

## 1–2. Questions and proposals per system

### Camping
1. What does my profession place at camp, at what skill, with which buff and reagents?
2. Which camp buffs overlap the group's class buffs?
3. Which boss drops Blueprint X?
4. What does a Cook need for 5 and 10 slots?

**Camping page.** A matrix of profession rows by tier (20/140/300): object, buff text and duration, skill, reagents, Blueprint. A campfire strip shows Cooking 1/90/200 and slot counts. Blueprint items get item pages with "Dropped by: not yet discovered". A **camp composer** takes a hypothetical party's professions and shows coverage and class-buff overlap, never a "best" pick.
Data: Spell/SpellEffect summon-object effects, GameObjects, SkillLineAbility, SpellReagents, Item/ItemSparse (client). Buff exclusivity: unknown, possibly server rules. Blueprint sources: server/crowd. **M** (composer +S).

### Legacy points
1. What are the 65 challenges and how is each earned?
2. What does each of the 21 perks do per rank?
3. What can I fit in 16 points?
4. What is on the Reward Track, at what totals?

**Legacy page**, three tabs. *Challenges*: category, requirement, points, ruleset-flow note (Hardcore flows out, not in). *Perks*: tree calculator with the 16-point cap, rank costs and a copyable build string, nothing stored. *Reward Track*: cosmetics by total, linked to item and companion pages.
Data: unknown; check TraitTree/TraitNode/TraitDefinition/TraitCost and Achievement/Criteria/CriteriaTree first. If absent, a hand-curated, cited data file. **M** if client tables exist, **L** if curated.

### Talents and classes
1. What does the Forever tree look like and what changed from Classic?
2. What do the new 16-point talents and new spells do?
3. What does my class learn at each level, and what became baseline?
4. What is each pet family's unique ability, and where are foxes?

**Talent browser** per class: three trees, rank tooltips, milestone rows 11/16/21/31 marked, a calculator with prerequisites and a build string, and a "Diff vs Classic" toggle. **Class page**: spells by level with rank, "now baseline" flags and dual-spec facts. **Pet families**: family abilities and tameable creatures.
Data: Talent/TalentTab or mainline Trait* (whichever is populated), SpellLevels, SkillLineAbility, SpellName plus the spell-token resolver (backlog §3 fix), CreatureFamily (client). Training cost: server/crowd. Tameable Forever creatures: Questie/crowd. **L** total; browser + diff M, calculator S, spells-by-level M.

### Races
1. Which race/class combos exist, and which are new?
2. What are each race's two active and two passive racials, versus Classic?
3. Where does each race start?
4. How do Windshapers and High Order differ?

**Race grid**: races by classes, cells marked available, new in Forever or unavailable, with Skyborne split by faction. **Race page**: racials with tooltips, a Classic-vs-Forever column, starting-zone link, and the paid-pack note as a sourced fact.
Data: ChrRaces, ChrClasses, CharBaseInfo, racial SkillLine via SkillLineAbility, AreaTable (client). Base stats: server-side. **M** (grid S).

### Transmog and collections
1. Which items share this item's appearance?
2. Which appearances come from instance X, and does the look go to all looters or only the binder?
3. Which sets are reworked Classic and which are new?
4. Which mounts and companions exist, and from where?

**Appearance panel** on item pages ("Shares appearance with N items"). **Instance appearances tab** with the grant rule labelled. **Collections browser** for mounts and companions by source. No collected ticks: that needs the account.
Data: ItemModifiedAppearance, ItemAppearance, TransmogSet/TransmogSetItem, Mount, BattlePetSpecies (client, verify populated). Grant rule: [C] general rule, per-item exceptions unknown. Sources: server/crowd. Panel **S**, tab **M**, collections **M**.

### PvP reputations
1. What do Darkspear Raiders and Theramore Expeditionary Force sell at each standing, and for how much honor?
2. How is reputation gained?
3. What are the rank rewards and the weekly cap?
4. What does Darkspear Islands look like: nodes, flag, bracket?

**PvP faction page**: standing ladder, rewards per tier, tabard at Exalted, costs in honor. **Battleground page**: map, nodes, 30–60 bracket, 15v15. I propose pulling these two factions forward from v0.7's faction pages.
Data: Faction, FactionTemplate, CurrencyTypes, ItemExtendedCost, ItemSparse required faction and standing, BattlemasterList, AreaPOI (client). Vendor lists and rep gains: server/crowd. Weekly cap: unknown. **M**.

### Hardcore
1. What are the Forever Hardcore rules?
2. How does Hardcore progress flow into Legacy?

**Ruleset note**: a cited glossary entry plus a callout on the Legacy page. No death tracking, no danger maps. Data: curated text. **S**, low priority until winter.

### Merged hit/crit and new stats
1. What does "Hit" mean now?
2. What are parry and dodge reduction, and which items carry them?
3. Which items have stat X?
4. Is this item's stat line known or hidden?

**Stat mapping and glossary**: a Forever stat-type table mapped to labels, tooltips using merged labels, and a glossary entry. "Stats unknown" replaces zeros on obfuscated items. The v0.5 loot filter gains "has stat".
Data: ItemSparse StatModifier_bonusStat/bonusAmount (client); new enum values unknown until discovered by diffing distinct values against Classic Era; labels from GlobalStrings ITEM_MOD_* (client). **S** + **S**.

## 3. Classic vs Forever diff
Every diff is computed from two client builds, never hand-typed, and the Classic side names its build.
1. **Talents (highest value).** Keep the same tree grid and badge cells New, Changed, Moved, Removed or Now baseline. A changed talent shows Classic and Forever text side by side. A per-tree summary counts each kind.
2. **Baseline spells.** A per-class level ladder with badges: new spell, moved level, was a talent, was a raid buff.
3. **Racials.** A two-column card per race, Classic on the left, Forever's 2+2 on the right, with new, changed and removed badges.
4. **Race/class grid.** The grid itself with the six new combos and Skyborne highlighted, a legend, and a "new only" filter.
5. **Stats.** A short mapping table, Classic stat to Forever stat, linked from every item tooltip that uses a merged stat.

## 4. Top 5
1. **Talent browser with Classic diff.** Every returning player's first question; the client likely holds the trees, and no Classic tool has the diff.
2. **Race grid and race pages with racial diffs.** Small, high-certainty data that showcases the Skyborne and the six new combos.
3. **Camping page.** A system with no Classic analogue, mostly client-backed, with honest unknowns for Blueprint sources.
4. **Legacy page with perk calculator.** The account system everyone asks about; a hypothetical planner stays research.
5. **Stat mapping and "stats unknown".** Foundational: without it every Forever item misreads merged hit/crit and shows zeros as facts.

**Where I draw the line.** In: tools that take hypothetical input and show consequences, such as the Legacy and talent calculators or the camp composer. Out: anything that reads or stores your account or character progress, such as challenge ticks or collected appearances. Also Out: anything that ranks options, such as "best alt", "best perks" or "best camp". "Challenges a Warrior can earn" is In; "level a Warrior next for the most points" is Out.

Next tier: PvP faction and battleground pages (M), appearance panel (S), Hardcore note (S).

## 5. Risks
- **Obfuscated items.** About 40% of items lack stats, and Blueprints and Reward Track items may be among them. ItemSparse may hold placeholders rather than zeros; sample known [C] items before trusting any value.
- **Beta cap 30.** Talent and spell tooltips above level 30 may be placeholders. Mark them "beta build, unconfirmed above 30" and re-import on the launch build (4 Nov).
- **Speculative table names.** Legacy and camping tables are guesses. Run a discovery task first: diff 70425's DB2 list against Classic Era's and search for Trait*, Legacy and camp names. If absent, the system is server-side, becomes curated data, and slips under decision 12.
- **Classic baseline.** Every diff needs a Classic Era client build's DB2 at build time. Confirm one is available locally or pin a file set; without it the diffs cannot ship.
- **No Dungeon Journal.** Blueprint sources and boss appearance lists are unknown at launch, and VMaNGOS has no Forever data. A crowd-data import path is needed.
- **Schema drift.** The mainline UI may mean Trait* talents instead of Talent/TalentTab, and Skyborne may be one ChrRaces row or two. Parsers must detect which is populated.
- **Buff stacking.** Camp-versus-class exclusivity may be server rules. Show it per spell only when spell data proves it; otherwise show Blizzard's general rule with its citation.
- **Wowhead talent data** comes from BlizzCon testing, not the client, so mismatches with our client import are expected and should not be "fixed" by hand.
