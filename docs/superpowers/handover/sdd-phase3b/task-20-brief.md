### Task 20: In-game collector addon and its import (two halves)

Added 2026-10-17 (PM memo `pm-data-sources.md`, section "Task 20"). The addon half touches no Java and starts at once in a worktree on branch `feature/collector-addon` from `main`; it must be ready for the owner by 19 October because the beta ends about 22 October. The importer half runs on the 3B branch after Task 19 and before Task 16.

**Addon half — files:** create `tools/collector/WowForeverCollector/WowForeverCollector.toc` (Interface version of the beta client, title, `## SavedVariables: WowForeverCollectorDB`), `Core.lua`, `README.md` (install into the beta's `Interface/AddOns` by the owner — the app never writes there; the run plan below; where the SavedVariables file lands: `WTF/Account/<account>/SavedVariables/WowForeverCollector.lua`), `tools/collector/fixtures/WowForeverCollector.sample.lua` (a hand-written SavedVariables file in the exact schema), and a `lua-table` module test that parses the sample with the existing Lua table reader.

**Schema 1 of `WowForeverCollectorDB`** (account-wide; never a character name, realm or guild anywhere):
- `session`: build (`GetBuildInfo`), date, addon version.
- `loot`: on `LOOT_OPENED`, each slot's item link and quantity, each `GetLootSourceInfo` GUID reduced to type and id (Creature or GameObject), instance name, mapID and difficulty (`GetInstanceInfo`), uiMapID and player x,y; de-duplicated per source GUID so a corpse counts once.
- `encounters`: `ENCOUNTER_START` / `ENCOUNTER_END` with encounterID, name, success.
- `vendors`: on `MERCHANT_SHOW`, the NPC id, then each item's link, price, stack, limited stock and extended cost.
- `trainers`: on `TRAINER_SHOW`, the NPC id, then each service's name, cost, required level and required skill.
- `quests`: on accept and turn-in, questID, giver or ender id and type, objectives text, rewards, uiMapID and x,y.
- `apiFailures`: every call that returned a secret or nil value where data was expected, with the call name and where it happened.

**Owner's run plan (README):** 19 Oct install; session 1 (~1 h): one run of City of Dalaran (28+) or Excavation Site (26+), one vendor and one trainer in a city, accept and turn in one quest, `/reload`, log out; share the SavedVariables file; 20–21 Oct session 2 only if fixes were needed. Acceptance: at least one Forever boss → item row "Observed in game", or `apiFailures` explains why not.

**Importer half — files:** importer option `--collector-file <path>` (repeatable, opt-in), parsed with the lua-table reader; provenance source `collector` reading "Observed in game"; loot sources become presence rows on `item_npc_drop` / `item_object_drop` with no rate, plus encounters; vendors into `item_vendor`; trainers and quests kept raw in a `collector_observation` table; an observation upgrades a `Reported` fork row to `Known`. Tests: a fixture file imports deterministically; the schema has no name/realm/guild fields; duplicate source GUIDs count once.

- [ ] Addon half: write the sample fixture and its parser test first; implement the addon; commit `feat(collector): in-game collector addon, schema 1` on `feature/collector-addon`.
- [ ] Importer half: failing tests → implement → full verify → commit `feat(importer): import collector observations as Observed in game`.

---

## Self-review

### Spec coverage

| Requirement (source) | Task |
|---|---|
| §4.1 header: "!" plate, name, "Lv 7 (req. 4)", zone, faction/class, Repeatable chips; stepper "Step 2 of 4", hidden for standalone | 11 |
| §4.2 Objectives + Map side by side (stacked in compact) | 11, 12 |
| §4.2 rows: number, verb, target link, muted text, where line; "You carry" / "You will need"; row click filters, chip "Showing objective 2 ✕"; top 5 sources + "Show all 9 sources"; killCredit one row; instance link; escort | 10, 12 |
| §4.3 Given by / Turned in to with zone and coordinates; item start line, map pins the item's sources | 11 |
| §4.4 chain tree, every connector, 2 steps either side + "Show n earlier", depth cap, loop, standalone copy | 13 |
| §4.5 Rewards, then Related items (collapsed) | 11 (choice vs always and reputation: open question 5) |
| §4 map: zone chips with glyphs, opens on the giver's zone, edge banner, pin art, legend toggles, no-map note | 8, 11 |
| §4 keys G/T/O/M/C/R, 1–9, Esc, `[` `]`, Z in KeyMap and help | 15 |
| §4 compact quest page | 11, 12, 13 |
| §4 scan test at 1280×800 on the five quests | 11, 12, 16 |
| §5 rail icons: plates, crop, gossip glyphs, states, tooltips, 16 px section icons, SVG fallback | 3 |
| §5 chrome: hairline panels, Marcellus SC headings with rule, 20 pt titles, 6 px gold bars with ticks, outline chips, 22 px status bar with provenance and toggles | 1, 2 |
| §5 typography: Marcellus/Marcellus SC (OFL), Cinzel/Georgia fallback, Inter | 1 |
| §5 themes: Slate values; Parchment values; navy card and plates in both | 1, 4 |
| §6 skeleton: header, positive facts, fixed section orders, "Name (count)", 8 rows then "Show all" | 5 |
| §6 merged Dropped by (instance chip, by rate, filter on NPC rows only) | 6 |
| §6 NPC notable drops first; Quests merged; Location | 6, 14, 5 |
| §6 zone and instance section orders; instance Quests | 5 |
| §6 master list shows while searching, auto-collapses, Ctrl+B keeps, visible ≥ 1600 px | 6 |
| §6 kind chips only in the search dropdown; filter beside headings; "≠" hover legend | 6 |
| §6 empty/loading copy ("Loading…" after 150 ms) | 5 |
| §6 compact sections collapsed except the first | 5 |
| §7 rulings (icons, order, no counts, merged filter, fonts) | 3, 11, 12, 6, 1 |
| §10 patrol routes: dashed 2 px, arrowheads every ~10 points, end marker plus tooltip, loops closed, line hit-test, zone toggle off by default | 9 |
| §11.1 community drop rates; decision 19 "Vanilla rate" plus the boss note | 7 |
| §11.4 cursor coordinates on every map, right-click /way, in-game item and quest links | 8, 15 |
| Backlog v0.3.0 "Involved in quests" | 14 |
| Backlog §3 quality fixes closed | 16 (audit), 8 (zone caption) |
| Release gates: verify, ≤ 300 ms p95, contrast, screenshots dark/light/compact | 16, 1, 4 |
| §6 Home dashboard; compact pinned tabs with Ctrl+1..6; always-on-top mini tooltip | Not planned (open question 1; v0.7.0 / v0.8.0 in the backlog) |
| §11.2 dungeon prep tab, §11.3 vendor/trainer finder, §11.5 crafting UI | Not planned: backlog v4 puts them in v0.5.0, v0.6.0 and v0.4.1/v0.6.0 |

### Type consistency across tasks

- `QuestPageData`, `ObjectiveRow`, `ObjectiveSource`, `GiverSpot` and `QuestFacts` are produced in Task 10 and consumed in Tasks 11–12 with the same names.
  - `ObjectiveRow.number` is 1-based throughout: the Task 8 `Pin.number`, Task 12 badges, and the Task 15 "1…9" keys.
  - The 3A ordinals start at 0 and are never shown.
- `PinKind.KILL/COLLECT/INTERACT/EXPLORE` and `PinKind.Layer` come from Task 8 and are used by Task 12. Routes are `MapView.RouteLine` (Task 9), not pins, so they never cluster.
- `QuestChain.Connector.LEADS_TO` is added in Task 13, both in the builder and in `ChainRows`. No earlier task switches on `Connector`, so adding the constant breaks no exhaustive switch until Task 13. Task 13 updates `Connector.label()` users.
- `Chip` (Task 2) replaces `RankChip`. Tasks 5, 6, 11 and 14 use `Chip`; nothing keeps `RankChip`.
- `Tokens.PARCHMENT` replaces `Tokens.LIGHT` in Task 4. Tasks 1–3 refer to `Tokens.LIGHT` only through `Theme.current()`, so the rename touches `ThemeManager` and the tests only.
- `LootRow` gains `pctSource` (Task 7). `DropSource` already has it. `RateBarRenderer`'s new three-argument constructor (Task 7) keeps the two older constructors, so the Task 2 tests stay valid.
- `SourceSection.heading()` changes type from `JLabel` to `SectionHeading` (Task 2). Task 5 (`showAllButton`) and Task 6 (`setTrailing`) build on that.
- `WayCommand.of` (Task 8) replaces `SpawnSection.wayCommand`, and `NpcPageTest`'s "/way Westfall 42.3 67.4" assertion moves with it.
- `Repositories` gains `questLocator()` (Task 10). Every `Repositories` built in tests goes through `Repositories.of(db)`, so no positional constructor call breaks. Verify that in Task 10 and fix any direct `new Repositories(...)`.
- `DetailPanels.LoadedQuest` changes shape in Task 10, consumed in Task 11. The old `QuestPage.GiverMap` is deleted in Task 11.

### Open questions, with the controller's rulings (2026-10-17)

**Ruling:** all fifteen defaults below are accepted as written. Two carry a condition: Q4 is confirmed by the controller's visual check in Task 3 (if the 1.5× glyphs disappoint, Task 16 records a data task to fetch the Interface/Icons second choices); Q7 is checked in game by the owner once, with the colour-wrapped form as the fallback. Q1, Q14 and Q15 stay out of 3B per backlog v4 decision 7 (frozen v0.3.0 scope).

Each question has the default applied.

1. **Home dashboard and compact pinned tabs.**
   - The conflict: spec §6 and §7 put both in. Backlog v4's v0.3.0 row does not list them, places compact pinned tabs and always-on-top in v0.8.0, Home as a learning entry in v0.7.0, and decision 7 freezes the scope.
   - **Default:** out of 3B. Home keeps its welcome text, and pinned pages wait for ROADMAP D17.
2. **Repeatable acceptance quest.**
   - The conflict: 7791 A Donation of Wool is not repeatable in the data. `special_flags` is null and `repeatable` is 0 for every "A Donation of …" quest.
   - **Default:** 7791 is the per-city/"Requires all of" acceptance case. 7796 Additional Runecloth, the repeatable follow-up of 7795, is the "Repeatable" chip case.
3. **Drakefire Amulet's Horde branch.**
   - The conflict: 6602 is not linked to 6502 by any relation (3A Task 5 finding).
   - **Default:** 6502 tests the 15-step prerequisite chain and the instance objective. 4604 tests exclusive alternatives, and no test claims a 6502↔6602 branch.
4. **Gossip glyphs on the rail.**
   - The issue: `rail.npcs` and `rail.quests` are 16 px art and look small on a 28 px plate (3A controller note).
   - **Default:** a bicubic 1.5× scale to 24 px from the native PNG. The controller's visual check in Task 3 may switch to 2× nearest-neighbour cropped to 24, or to the Interface/Icons second choices. The second choices need a 3A data re-fetch.
5. **Rewards "choice vs always, reputation".**
   - The issue: 3A imported no reward-choice or reputation-reward fields; only `quest_reward_item` exists.
   - **Default:** Rewards stays one list. The split waits for a data task.
6. **Missing chain targets.**
   - The conflict: spec §3 says "Quest #id (not in database)", while the 3A ruling says "Unknown quest #id".
   - **Default:** "Unknown quest #2955", muted, not a link (the later ruling).
7. **In-game link colour codes.**
   - The issue: spec §11.4 gives `|Hitem:…|h[Name]|h` without the `|cff…|`/`|r` colour wrapper. The game client may reject a pasted link without it.
   - **Default:** the spec's exact form, and the controller checks it in game once. If it fails, switch to the colour-wrapped form, which `PasteParser` already accepts.
8. **Headline "Vanilla" marker.**
   - The issue: decision 19 calls it "a label in the existing provenance tooltip".
   - **Default:** the tooltip label plus a muted " V" glyph after a rate whose headline source is VMaNGOS, explained in the section's "≠"/"V" legend. Without the glyph, a headline vanilla rate reads as a Forever fact until hovered.
9. **Rate bar colour.**
   - The conflict: spec §5's "6 px dark-gold→gold" replaces the 2B rate-band colours on the bar.
   - **Default:** follow the spec. `QualityColors.rateBand` stays for any other caller. The number still carries the value, so nothing relies on colour.
10. **Contrast adjustments to the spec palettes.**
    - Slate's alternate row moves to `#1E232B`, because epic text on `#242A33` is 4.25:1.
    - Parchment tables, lists and fields sit on raised `#FBF5E6`, with alternate row `#F6EEDC` and warn `#8A5A00`. On surface `#EADCBB`, poor is 3.92, uncommon 4.04, legendary 3.96 and gold text 4.49.
    - Parchment artifact `#7A5E14` and heirloom `#00687F` are additions.
    - **Default:** apply them as written in the Global Constraints.
11. **Status bar versus a map corner label for coordinates.**
    - The conflict: the brief says status bar, spec §11.4 says a corner label on every map.
    - **Default:** the corner label, which also works in compact mode where the status bar is narrow.
12. **Spawn counts per sub-zone.**
    - The issue: spec §4's "Fargodeep Mine · 23 spawns" needs sub-zone per spawn, which the data lacks (`npc_spawn` has only `zone_id`).
    - **Default:** the zone name ("Elwynn Forest · 23 spawns").
13. **Reputation and spell objective names.**
    - The issue: there is no faction or spell name table in the DB.
    - **Default:** "Reputation: Faction #id, value" and "Cast: Spell #id", shown muted, with no pins.
14. **Zone NPC filters "rares, vendors, trainers" in spec §6.**
    - The overlap: they touch the v0.6.0 vendor/trainer finder.
    - **Default:** not in 3B. The zone NPC section keeps the 2B sort (bosses and rares first).
15. **Instance "Entrance" section (spec §6).**
    - The issue: there is no entrance data.
    - **Default:** left out.
