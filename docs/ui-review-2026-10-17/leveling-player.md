# Leveling and exploration player review

This is a simulated player task walkthrough based on the shipped v0.4.1 screenshots and current Swing source. It is not a study with real WoW players. No production code changed. Priorities describe proposed design work: P0 blocks dependable interpretation, P1 improves the main journeys, P2 adds convenience.

## Evidence inspected

- `native-final/03-westfall-hover.png` and `04-westfall-zone.png` under `.local/v041-acceptance/`. The last is a loading frame; it cannot establish the appearance or performance of the loaded zone.
- `standard-final/quest-176-slate-full.png`, `npc-550-routes-slate.png`.
- `travel-final/dark-travel-40-1280.png`.
- `catalog-final/hub-0-dark-1216.png`, `ladder-0-dark-1216.png`, `talents-0-dark-1216.png`, `race-95-dark-420.png`.
- Source: `QuestPage`, `quest/QuestHeader`, `quest/GiverSections`, `quest/QuestMapPanel`, `ZonePage`, `ZoneMapPanel`, `WorldMapPage`, `DungeonLadderPage`, `TalentPage`, `RailTarget`, `QuestFilter`, and `SectionOrder`.

## Keep

The familiar parchment maps, quest !/? markers, visible objective/source coordinates, in-game link copy, clickable geography breadcrumbs, separate entrance and exit labels, and clear beta/unconfirmed language already support a WoW player’s mental model. Retain these. The quest’s desktop split between tasks and map is substantially stronger than the catalog pages’ long lists.

## 1. “I am level 14 in Westfall. What can I do next?”

Current route is geography or global search, then a zone page or quest list. Quest filtering supports zone and minimum/maximum quest level. There is no shared player context in that filter. The level-appropriate decision requires the player to repeatedly interpret quest level, required level, faction, and prerequisites themselves. The zone page puts a large map before the quest section, so it does not immediately answer the question despite containing the data.

**P1:** Add an optional planning context, `Level 14 · Alliance · Warrior`, with an explicit editable setting rather than implied live character detection. On a zone page offer stable tabs or section jumps: `Overview | Quests (53) | NPCs | Travel | Dungeons`. The Quests tab should retain the zone map alongside a filterable list and show `Quest level`, `Requires`, `Faction`, and `Chain` as distinct information. Start with a selectable level range rather than claiming “available to you,” because quest completion and some conditions are not tracked.

Suggested target flow: `Zones → Westfall → Quests → Level range 12–16 → a quest → Start / Objectives / Turn-in`. Keep context when returning. The player should not need to re-enter the level range after every quest.

**P0 interpretation rule:** “No prerequisites recorded” must never become “No prerequisites,” and incomplete imported conditions must remain visible. Unknown required level/faction must remain unknown and should be discoverable through an `Include unknown eligibility` toggle.

## 2. “Where is the giver, and where does this patrol go?”

The Hogger quest screenshot already links objective and map, but two identically named Wanted Posters require reading coordinates to distinguish them. Given-by rows show the first recorded spawn; source confirms this is not an exhaustive location summary. The patrol map capture shows a small route without persistent identity/context in the captured map itself.

**P1:** Make `Start`, `Objectives`, and `Turn in` explicit map modes adjacent to the map title. In the corresponding list each row gets `Show on map`, a matching marker, and `Copy coordinates`. A quest giver with several spawn positions should say `3 recorded locations` with an expand action; preserve object/NPC/item distinction. Selecting a list row should highlight its marker without forcing a different page.

**P1:** Patrol detail needs a persistent selected-entity title, `Recorded patrol · The Defias Messenger`, route legend, and `Show entire route` action. Make it clear that coordinates describe recorded patrol positions, not current location. Routes and flight routes must use different labels and line styles.

**P2:** A temporary quest shortlist could support planning several nearby tasks. It must be user-maintained; do not infer completed quests or shortest paths from absence of data.

## 3. “Azeroth → Eastern Kingdoms → Westfall, then go back”

The continent screenshot’s breadcrumbs and map hover are promising. However, closely spaced black labels overlap or truncate (`Hillsbrad Foo…`, `Redri…`, `Swamp …`). The icon-only rail makes returning to geography a memorization task. The map’s native hover tooltip says `0 instances` for Westfall while its travel map shows Deadmines entrances; a player can interpret this as contradictory even if the counts use different geographic relationships.

**P1:** Keep breadcrumbs visible at every geography depth, add a clear current-zone title and accessible labeled `Up to Eastern Kingdoms` action. Back/Forward must remain history; breadcrumbs move through geography. Preserve the map’s selected region and position when returning.

**P1:** Prefer selected/hovered region labels plus a searchable adjacent region list over permanently crowded labels. The list should show full names and level bands. Expose rail labels by default at desktop widths or provide an obvious expand control.

**P0 interpretation rule:** Replace ambiguous instance counts with a qualified count such as `Recorded instances: 0 · Entrances: 2` only when each count is accurately computed. Missing coverage should read `Not recorded`, never zero. Do not silently infer a zone’s instance membership from an entrance.

## 4. “Which dungeon can our level-15 group try, and what is new?”

The ladder is a long sorted list with no filters. It mixes Classic and Forever records, source minimums, client tuning, beta week-one announcement bands, wings, and discoveries. A level-15 player has to scan and reconcile these. Long literal Blizzard URLs dominate rows. Discovery entries are at the bottom rather than part of a clear separate discovery area.

**P1:** Use a compact dungeon browser with `Level range`, `Dungeon / Raid`, `Classic / Forever / All`, and `Known / Reported discovery` filters. Each row/card should summarize name, source-qualified level information, group size when supported, geography, and a visible `New` badge. Separate wings under their parent dungeon where supported. Offer `Details`, `Entrance`, and `Quests` actions.

Target flow: `Dungeons → Level 15 → Forever → The Hall of Thanes → Overview → Entrance / Bosses & loot / Quests / Maps`. A short `Beta announcement: 13–18 · Final launch band unannounced` label can coexist with `Client tuning minimum: 13`; a `Source` disclosure opens the citation. Neither should be presented as a confirmed recommended range.

## 5. “I want to try the new race and plan talents”

The compact race page leads with source/build metadata and a comparison table whose third column truncates every row. The talent screenshot is a list of names, zero ranks, and large numeric record IDs without visible headers; its class/spec dropdown says Enhancement while the visible list includes talents a player may associate with several historical trees. Regardless of native data organization, the current presentation makes it hard to understand structure and prerequisites.

**P1:** Race detail should lead with name, available classes as readable chips, and four racial ability cards. Link supported classes to class spells and talents. Put Classic comparisons and record IDs in a secondary section; retain `Starting zone not recorded` as a concise unknown-state line. Do not invent starting quests or starting locations.

**P1:** Talent planning should become a recognizable node/tree or tier layout using only actual native relationships. Show point count, rank/max rank, prerequisites, lock reason, and selected talent detail. Class/spec controls should explain what source-defined tree is being displayed. Keep `+ Rank` and `− Rank` available to keyboard users; move internal IDs and source paths into an evidence drawer. Unresolved formula values must remain qualified; no guessed gameplay effects.

**P2:** Offer named local hypothetical build saves, with an explicit `Planning only` label. Current page explicitly says nothing is saved, so navigating away can discard planning work; evaluate a dirty-build warning or optional persistence separately from character progression tracking.

## Proposed first mockup

Use a familiar app shell: labeled left rail, global search, Back/Forward, breadcrumb strip, and a stable content title. A Westfall planning screen could be:

```text
Home    Search everything…                         Level 14 · Alliance
Forever Azeroth › Eastern Kingdoms › Westfall
Zones   Westfall                            [Overview] [Quests] [Travel]
Quests  Level range [12–16]  Include unknown [ ]     18 matches
Dungeons
        Quest list                       Westfall map
        Selected zone quest              selected quest pins only
        Quest level · Required level    [Start] [Objectives] [Turn in]
        Alliance · Conditions incomplete
        [Show details] [Show on map]
        Evidence and source differences ▸
```

This is a structural sketch; populate the visual mockup with an actual zone-matched quest. The critical change is supporting a decision and preserving the player’s context while the existing source evidence remains one action away.

## Acceptance tasks for the redesign

1. A first-time user can identify Zones, Quests, Dungeons, and Forever without hovering rail icons.
2. From Azeroth, reach Westfall, select a quest, and return with geography and filters preserved.
3. From a quest, distinguish start, objective, and turn-in markers and locate each without opening unrelated NPC pages.
4. Identify a dungeon with recorded level information relevant to level 15 and explain the distinction between beta tuning, announcements, and final unknowns.
5. From a race, reach a supported class’s talents, inspect a prerequisite, and add a valid rank without reading internal record IDs.
6. At 420px, no core action or meaning depends on a truncated column; at desktop width, use map/list space rather than stretching tiny text into a sparse full-width catalog.
