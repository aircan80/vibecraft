# Phase 2B: UI Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the v0.1 browser into the companion app the UI review describes: dark by default, detail-first layout with a narrow preview list and an icon rail, links that behave like links, search that ranks the right thing first, pages that answer "what is likely" with rate bars and grouped loot, type-ahead, a home launch pad, filterable list pages, a keyboard map, compact mode, and (once phase 2A merges) the tooltip card, icons, quality colours, zone maps with pins and provenance markers.

**Architecture:** Pure `app` module work on Swing + FlatLaf. New packages `ui/theme`, `ui/widgets`, `ui/pages` replace `ui/detail`; `AppController` keeps its executor seam and generation guards; `UiState` (java.util.prefs) holds window/pinned/recent state. Part 1 (Tasks 1–10) needs no phase 2A data and runs in a worktree in parallel with 2A; Part 2 (Tasks 11–14) starts after 2A merges and this branch rebases onto `main`.

**Tech Stack:** Java 21, Swing, FlatLaf 3.6 (+ `flatlaf-extras` for `FlatSVGIcon`), JUnit 5, AssertJ; headless tests only (no JFrame/JDialog).

**Spec:** `docs/superpowers/specs/2026-10-16-phase2-client-data-and-ui-design.md` §5 (scope, deviations) with `docs/superpowers/specs/2026-10-16-ui-design-review.md` as the detailed reference (P1/P2 items, tokens, keyboard map, wireframes, asset specs).

## Global Constraints

- Branch `feature/phase2b-ui` from `main` (v0.1.0 + phase 2 docs), in the worktree `H:\claude_java\.worktrees\phase2b-ui`. Every v0.1 test keeps passing. Build from the reactor root in the worktree: `./mvnw -B -q -pl app -am test` (never bare `-pl app`, because phase 2A installs a newer `importer` SNAPSHOT into `~/.m2` while this branch runs). Full `./mvnw -B -q verify` before each commit.
- Headless tests: never instantiate `JFrame`/`JDialog`; pages and widgets are `JPanel`s; `AppController` tests use `Runnable::run`.
- All DB access off the EDT via the existing `Async`/executor seam; the only exception stays `InstancePage` boss-loot selection (documented).
- Theme tokens (dark / light) exactly as the UI review §P1.1: `bg.base #14171C/#F4F5F7`, `bg.surface #1B2027/#FFFFFF`, `bg.raised #242A33/#E9ECF0`, `text.primary #E4E1D8/#1C1F24`, `text.muted #8E97A5/#5C6470`, `accent.gold #D6B25E/#9A7419`, `link #9CC3E6/#1F5FA8`, `card.bg #0C1424`, `card.border #46587A`. Quality text colours (dark/light): 0 `#9D9D9D/#6B6B6B`, 1 `#E4E1D8/#1C1F24`, 2 `#3FE23A/#137A0E`, 3 `#4A9EFF/#0058B0`, 4 `#B861FF/#7E2BC4`, 5 `#FF9A33/#B35A00`, 6 `#E6CC80/#8A6D1E`; canonical colours for icon borders/card: 0 `#9D9D9D`, 1 `#FFFFFF`, 2 `#1EFF00`, 3 `#0070DD`, 4 `#A335EE`, 5 `#FF8000`, 6 `#E6CC80`, 7 `#00CCFF`. Rate bands: ≥ 25 % `#7FBF6A`, 5–25 % `#C9B458`, 1–5 % `#C98A4E`, < 1 % `#B3625A`.
- Search ranking: exact name match first, then kind priority Instance > Zone > Boss/Rare NPC > Item > Quest > NPC > Object, then bm25, then name; a zone that is the `zone_id` of exactly one instance opens that instance; same-name same-kind hits collapse with a count. Subtitles in words (never "iLvl 1", never raw `RAID40`).
- Keyboard map per the review §P2.6. `/way` copy uses TomTom syntax `/way <Zone Name> <x> <y>` with one decimal.
- Text ≥ 4.5:1 contrast in tables (use the text variants); colour never alone (numbers printed, glyphs for markers).
- Part 2 tasks consume the 2A contract only: `item_tooltip_line(kind, text, colour)`, `item.quality/icon_id/client_source`, `icon(id, png)`, `zone.ui_map_id` + `zone_map(ui_map_id, width, height, jpeg)`, `item_npc_drop.disagrees/source_count`, `drop_rate_source`, `boss_loot.disagrees`.
- Java is not on the shell PATH for subagents (prefix with the JAVA_HOME export). Commit after every task.

## Review Focus

1. A results list of 9,575 rows (Armor) with the preview-on-arrow behaviour must not fire 9,575 detail loads when the user holds the Down key: pinned in Task 2 (`MasterListTest.holdingArrowCoalescesPreviews` with a fake clock) and the generation guard.
2. A search for a term matching both a raid zone and its instance ("Molten Core") must open the instance page, while a zone with several instances (a city with two dungeons? none in data, but "Blackrock Mountain"-style) still opens the zone: pinned in Task 4 (`SearchRankingTest.raidZoneCollapsesIntoItsInstance`, `zoneWithTwoInstancesStaysAZone`).
3. Mouse buttons 4/5 must navigate only when the app window is active and must not steal events from text fields' own handling: pinned in Task 3 (`MouseNavigationTest`).
4. Restored window bounds that fall entirely off every current screen (monitor unplugged) must be replaced by a centred default: pinned in Task 9 (`UiStateTest.offscreenBoundsAreReset`).
5. Pasted in-game links with colour codes and names containing apostrophes (`|cffa335ee|Hitem:19019:…|h[Thunderfury, Blessed Blade of the Windseeker]|h|r`, `[Captain Sanders' Hidden Treasure]`) must resolve: pinned in Task 7 (`PasteParserTest`).

---

## Part 1 — independent of phase 2A data

### Task 1: Theme tokens, dark default, quality colours

**Files:** Create `app/src/main/java/dev/wowforever/app/ui/theme/Theme.java` (token constants as `Color` for the active mode + `static Theme current()`), `Tokens.java` (both palettes), `QualityColors.java` (`text(int quality, boolean dark)`, `canonical(int quality)`, `rateBand(double pct)`), `app/src/main/resources/dev/wowforever/app/ui/theme/WowForeverDark.properties` and `WowForeverLight.properties` (FlatLaf properties: `@background`, `@foreground`, `@accentColor`, `Component.linkColor`, `Table.selectionBackground`, `Table.alternateRowColor`, `Component.focusWidth`, `Component.arc`); modify `ui/ThemeManager.java` (default `dark`, `apply(mode)`, `isDark()`, `toggle(root)` keeps working; loads the properties via `FlatLaf.registerCustomDefaultsSource` or subclasses `FlatDarkLaf`/`FlatLightLaf` with `IntelliJTheme`-style properties).
**Tests:** `QualityColorsTest` (every quality 0–7 has text/canonical; band thresholds at 25/5/1 exactly), `ThemeManagerTest` (default dark when no preference; preference round-trip; `Theme.current()` flips with the mode; properties resources load and contain the token values).
- [ ] Steps: write failing tests → run → implement → pass → commit `feat(ui): theme tokens, dark default and quality colours`.

### Task 2: Detail-first layout: master list, icon rail, split persistence

**Files:** Create `ui/widgets/MasterList.java` (`JList<ListEntry>` with a two-line cell renderer: name on line 1, kind + key facts on line 2; `record ListEntry(EntityRef ref, String title, String subtitle, Integer quality /*nullable*/, Long iconId /*nullable*/)`; `setEntries(List<ListEntry>)`; `onPreview(Consumer<ListEntry>)` fired 120 ms after the selection settles (`javax.swing.Timer`, restart on each change; injectable `Timer` factory/clock for tests); `onCommit(Consumer<ListEntry>)` on Enter/click; `selectIndex(i)`), `ui/widgets/Rail.java` (vertical `JToolBar` of `JToggleButton`s: Home, Items, NPCs, Quests, Zones, Dungeons & Raids with `FlatSVGIcon`s from `app/src/main/resources/dev/wowforever/app/ui/icons/*.svg` — simple hand-written 24 px SVGs committed; tooltips; `onSelect(Consumer<RailTarget>)`), `ui/widgets/Icons.java` (svg loading helper), `nav/NavigationHistory.replaceCurrent(ViewTarget)`; modify `MainFrame.buildContent` to toolbar | rail | `JSplitPane(list ~300 px | detail)` with the divider persisted through `UiState` (Task 9 adds the rest of the state; create a minimal `UiState` now with `dividerLocation` get/set), `Ctrl+B` toggles the list; `AppController` routes list previews (`replaceCurrent` + detail load) vs commits (`push`); `ResultsPanel` is replaced by `MasterList` (keep `ResultsTableModel`/`Columns` for list pages in Task 8). Add `com.formdev:flatlaf-extras` to `app/pom.xml` (version = FlatLaf's).
**Tests:** `MasterListTest` (`holdingArrowCoalescesPreviews`: 50 selection changes within the timer window → one preview; `enterCommits`; renderer shows title/subtitle), `RailTest` (six buttons, selection fires target), `NavigationHistoryTest.replaceCurrentDoesNotGrowHistory`, `AppControllerTest.previewReplacesCurrentWithoutPushing`, `MainFrameContentTest` (content builds headless; divider from `UiState`).
- [ ] Steps as usual; commit `feat(ui): master list, icon rail and detail-first split`.

### Task 3: Links behave like links; mouse navigation

**Files:** modify `ui/TableLinks.java` (single-click opens a link cell; `MouseMotionListener` tracks the hovered cell → `LinkRenderer` underlines + hand cursor; double-click still works), `ui/LinkRenderer.java`, create `ui/MouseNavigation.java` (`install(AppController, Window)`: `Toolkit.addAWTEventListener(MOUSE_EVENT_MASK)`, on `MOUSE_RELEASED` with `getButton()` 4 → back, 5 → forward, only when the event's window is the app window and the source is not a text component with its own binding), replace the back/forward glyph buttons with SVG icons (`arrow-left.svg`, `arrow-right.svg`), `MainFrame` wiring.
**Tests:** `TableLinksTest.singleClickOnLinkCellOpens`, `singleClickOnNonLinkCellDoesNothing`, `hoverTracksCell`; `MouseNavigationTest` (synthesised `MouseEvent`s with button 4/5 on a headless `JPanel` owned by a stub "window" → back/forward called; events on a `JTextField` ignored; events for another window ignored).
- [ ] Commit `feat(ui): single-click links, hover affordance and mouse back/forward`.

### Task 4: Search ranking, merged hits, worded subtitles

**Files:** modify `db/SearchRepository.java` (ranking per Global Constraints; `kind_priority` CASE; boss/rare NPC detection via `npc.rank` 3/4 or `boss.npc_id` existence; the zone→instance merge: a zone hit whose id is the `zone_id` of exactly one instance becomes that instance hit (dedupe if the instance also matched); collapse same-name same-kind hits into one `SearchHit` with `count` and the first ref — add `int count` to `SearchHit`), create `ui/Labels.java` (`contentType("RAID40") → "Raid (40)"`, `zoneKind("raid") → "Raid zone"`, `rank(int)`, `levels(a, b)`, `itemSubtitle(className, subclassName, itemLevel, requiredLevel)` → "Mail waist · iLvl 37 · Req 32" (omit null parts; never "iLvl 1" for quest/junk: omit iLvl when ≤ 1)), subtitles built through `Labels`.
**Tests:** `SearchRankingTest` (fixture DB: exact match first; "Molten Core"-style case: build a temp DB where a zone is the zone of one instance → the instance hit wins and no separate zone hit; `zoneWithTwoInstancesStaysAZone`; same-name signposts collapse with count 4; kind priority order), `LabelsTest` (each mapping, including unknown content type passthrough).
- [ ] Commit `feat(ui): search ranking, merged instance hits and worded labels`.

### Task 5: Item page as one scrolling sources page

**Files:** Create `ui/pages/ItemPage.java` (header via `HeaderPanel`; summary line "n NPCs · n loot lists · n vendors · n quests"; one `SourceSection` per non-empty kind in the order Dropped by, In instance loot, Contained in (objects + containers), Sold by, Quest reward, Related quests; `Ctrl+1…6` jumps to sections), `ui/widgets/SourceSection.java` (heading with count + a `JTable` without its own scroll pane, header from `getTableHeader()`, viewport height = rows), `ui/widgets/RateBarRenderer.java` (number + 48 px bar in the band colour; `—` for null), `ui/widgets/OriginChip.java` (outlined chip "AtlasLoot" / "Questie + AtlasLoot" only when origin ≠ questie), `ui/Columns.java` additions (`dropSourcesRanked()`: Source link, Level, Zone link, Rate (bar renderer), Origin chip; drop rows sorted known-rate desc, then an "Rate unknown (n)" sub-group rendered as a muted divider row), boss-loot rows read "Scarlet Monastery › Armory trash" with the list name appended when two rows would read the same; `DetailPanels` → `Pages.build(ref, repos, nav)` dispatch (keep the old detail panels compiling until each page replaces them, then delete `ui/detail/ItemDetailPanel`).
**Tests:** `ItemPageTest` (fixture 14149: sections present only when non-empty, order, counts; 2874: rows sorted by rate desc with VanCleef 60.00 % first; unknown-rate rows after a divider; origin chip only on non-questie rows; `Ctrl+1` focuses section 1), `RateBarRendererTest` (band colours at 30/10/3/0.5; null renders "—"), `SourceSectionTest` (height equals rows).
- [ ] Commit `feat(ui): item page as a scrolling sources page with rate bars`.

### Task 6: NPC, instance and zone pages

**Files:** Create `ui/pages/NpcPage.java` (header with rank chip: Elite gold outline, Rare silver, Boss gold fill; loot grouped under headings Gear / Recipes / Quest items / Trade goods / Consumables / Junk by item class (mapping in `ItemCategories.java`: Weapon+Armor → Gear, Recipe → Recipes, Quest → Quest items, Trade Goods+Reagent → Trade goods, Consumable → Consumables, else Junk; "Other" for null), each group sorted by rate desc with unknown last; columns hidden when empty for every row (Slot, Zone equal to page zone, uniform Origin); Sells / Starts / Ends sections; Spawns section "42.3, 67.4" with a "Copy /way" button → `/way <Zone> 42.3 67.4` to the clipboard via a `ClipboardPort` seam for tests), `ui/pages/InstancePage.java` (numbered encounter `JList` "1. Lucifron  Lv 63  Boss", extra lists under "Other loot lists"; loot grouped by slot type Weapons / Armour / Jewellery / Recipes / Other; header "Raid (40) · Level 60–63 · <zone link>" + "NPCs in <zone>" link; segmented difficulty control only when `boss_loot.difficulty` has > 1 value), `ui/pages/ZonePage.java` (header + level range computed from NPC levels; Bosses and Rares first; Quests sorted by level; instances and sub-zones as compact links at the top; map placeholder area reserved), `ui/pages/ObjectPage.java`, `ui/pages/QuestPage.java` (ported from the detail panels with the new section style). Delete the replaced `ui/detail/*` classes once all pages exist.
**Tests:** `NpcPageTest` (639: loot grouped, sorted; hidden columns; `/way Westfall 42.3 67.4` copied via the port; rank chip text), `InstancePageTest` (numbering, extra lists section, difficulty control absent with one value), `ZonePageTest` (level range, bosses first, instance links), `ItemCategoriesTest`.
- [ ] Commit `feat(ui): NPC, instance, zone, object and quest pages`.

### Task 7: Type-ahead, kind chips, recent searches, paste parsing

**Files:** Create `ui/widgets/SearchDropdown.java` (non-focusable `JWindow` with a `JList` under the field; top 8 hits grouped by kind; Up/Down/Enter/Esc forwarded from the field's `InputMap`; "Show all n results" row), `ui/widgets/KindChips.java` (`JToggleButton`s All/Items/NPCs/Objects/Quests/Zones/Dungeons & Raids with counts from `SearchRepository.countsByKind(text)`), `ui/PasteParser.java` (`Optional<ViewTarget> parse(String)`: `|Hitem:ID:…|h[Name]|h`, `item:ID`, `npc:ID`, Wowhead URLs `…/item=ID`, `/npc=ID`, `/quest=ID`, `/zone=ID`, `[Name]` → search, bare number → item id), `UiState.recentSearches` (last 10), `SearchBar` rewrite using these; `SearchRepository.countsByKind`.
**Tests:** `PasteParserTest` (the two Review Focus strings, URLs, bare numbers, garbage → empty), `SearchDropdownTest` (headless: model grouped by kind, Enter opens top hit, Esc closes), `KindChipsTest` (counts render, selection fires kind), `SearchRepositoryTest.countsByKind`.
- [ ] Commit `feat(ui): type-ahead search with kind chips, recents and paste parsing`.

### Task 8: Category rail list pages with filter bars (tree removed)

**Files:** Create `ui/pages/ListPage.java` (filter bar + `JTable` on `ResultsTableModel`; filter combos/spinners feed repository queries), `db/ItemRepository.list(ItemFilter)` (`record ItemFilter(Integer classId, Integer subclassId, Integer minLevel, Integer maxLevel, Integer quality /*null until 2A*/)`), `NpcRepository.list(NpcFilter(zoneId, rank, minLevel, maxLevel))`, `QuestRepository.list(QuestFilter(zoneId, minLevel, maxLevel))`, `ZoneRepository.all()` list page, `InstanceRepository.all()` list page grouped by level band; `BrowseTree` and its tests deleted; `Rail` targets → list pages; `ViewTarget.List` gains an optional filter.
**Tests:** `ListPageTest` (filters produce the expected query; changing a filter reloads; sortable), repository filter tests on the fixture DB (`listWithLevelRange`, `npcsByRank`), `AppControllerTest` for rail navigation.
- [ ] Commit `feat(ui): filterable list pages from the category rail`.

### Task 9: Keyboard map, window state persistence, text size, accessibility

**Files:** `ui/KeyMap.java` (bindings per review §P2.6 on the root pane; `Help → Keyboard shortcuts` dialog content as a `JTable` built from the same table so the help never drifts), `ui/UiState.java` complete (bounds, maximised, screen device id, dividers, compact mode, always-on-top, text size, column widths + sort per table kind, last search kind, pinned, recent), `MainFrame` save on `windowClosing` and a 500 ms debounced move/resize; restore validates bounds intersect a current screen else centre 1280×800; text size 90–150 % via `UIManager.put("defaultFont", …)` + `FlatLaf.updateUI()`; focus ring (`Component.focusWidth` = 2, gold); accessible names on every icon-only button; `Esc` chain (close dropdown → clear search → focus list).
**Tests:** `UiStateTest` (round-trip every field through a `Preferences` test node; `offscreenBoundsAreReset`), `KeyMapTest` (every documented binding present in the input map; help table rows equal the binding list), `AccessibilityTest` (every `AbstractButton` without text in `MainFrame.buildContent` has an accessible name).
- [ ] Commit `feat(ui): keyboard map, window state persistence, text size and accessibility`.

### Task 10: Compact mode

**Files:** `ui/CompactShell.java` (420×720 single column: search with dropdown, then the page; tables show name, rate and one chip; other columns in the row tooltip), `MainFrame` swaps content panes on F10 with the same controller; `setAlwaysOnTop` toggle (Ctrl+Shift+T) persisted; separate bounds in `UiState`.
**Tests:** `CompactShellTest` (builds headless; compact column set; toggling preserves current target), `UiStateTest.compactBoundsSeparate`.
- [ ] Commit `feat(ui): compact second-screen mode`.

### Part 1 gate
- [ ] Full `./mvnw -B -q verify` green in the worktree; controller visual check with the screenshot script (home, search, item, NPC, instance, zone, compact); ledger "Part 1 complete". Then wait for 2A to merge, rebase `feature/phase2b-ui` onto `main`, resolve conflicts in `Columns`/`ItemPage`/`ZonePage` in favour of 2B's structure while keeping 2A's data accessors, and continue.

## Part 2 — after phase 2A merges

### Task 11: Tooltip card, icons and quality colours everywhere

**Files:** `ui/widgets/ItemCard.java` (custom-painted navy card, 1 px border, 4 px radius, 56 px icon beside the name, `JLabel` stack of `item_tooltip_line` rows coloured by the `colour` column; `slot` lines split on `\t` into left/right), `ui/widgets/IconCache.java` (`icon(id, size)` → `ImageIcon` from the `icon` table BLOB with bicubic downscale, LRU 2,000; fallback id 0), item names in every table/list/dropdown rendered with `QualityColors.text`, 18 px icons in rows and 28 px in the master list; `Columns`/`MasterList` renderers; `ItemPage` card at the top; `db/AssetRepository` (from 2A Task 10) reused; a muted "Stats from Classic Era data" line is already a tooltip line (`source_note`) — just render it.
**Tests:** `ItemCardTest` (lines in order with colours; slot split), `IconCacheTest` (fallback, size cache), renderer tests, `ItemPageTest.cardShown`.
- [ ] Commit `feat(ui): tooltip card, icons and quality colours`.

### Task 12: Zone maps with spawn pins

**Files:** `ui/widgets/MapView.java` (`JComponent` painting a cached `BufferedImage` scaled to width with aspect; pins at `x%·w, y%·h`; hover → name tooltip; click → `nav.open`; pins within 8 px cluster into a count badge; `setPins(List<Pin(EntityRef, name, x, y, kind)>)`, `setFilter(bossesAndRaresOnly)`), `ZonePage` top area shows the map with Bosses and Rares pins by default + "All NPCs" toggle + "Objects" toggle; `NpcPage` Spawns section shows the map with that NPC's pins; `ZoneRepository.map(zoneId)` (from 2A) + spawn queries (`npcSpawnsInZone(zoneId, bossesOnly)`, `objectSpawnsInZone`).
**Tests:** `MapViewTest` (headless: pin hit-testing, clustering threshold, scaling math), `ZonePageTest.mapShownWithPins`, `NpcPageTest.spawnsOnMap`, repository spawn-query tests. Controller visual check: Hogger's pins on Elwynn, Sentinel Hill flight master on Westfall (alignment acceptance from spec ruling 13).
- [ ] Commit `feat(ui): zone maps with spawn pins`.

### Task 13: Provenance markers and source filter

**Files:** rate cells show an amber "≠" when `disagrees`; hover tooltip lists every `drop_rate_source` row (`SearchRepository`/`ItemRepository.sources(npcId, itemId)`); `UiState.sourceFilter` (all / hide AtlasLoot-only / hide Questie-only) applied in `ItemPage` and `NpcPage`; `InstancePage` difficulty control if present; Settings in `View` menu.
**Tests:** renderer marker test, tooltip text test, filter application tests.
- [ ] Commit `feat(ui): provenance markers and source filter`.

### Task 15: Quest-giver pins on zone maps (owner request, 2026-10-17)

_Added 2026-10-17 at the owner's request; it ran after Task 13 and before Task 14, so the README screenshots show it._

Owner's words: "when looking at an area map to be able to see where quests are given (with the questie '!' symbol).
When you click on it you can go to that quest."

**Data:** `quest_giver(quest_id, kind npc|object|item, entity_id, role start|end)` (5,293 start rows, 4,994 end rows)
joined through `npc_spawn(npc_id, zone_id, x, y)` / `object_spawn(object_id, zone_id, x, y)` for the zone; `quest(id,
name, quest_level, …)`. Item givers have no spawn and are skipped.

**Files:**
- `db/ZoneRepository.questGiversInZone(zoneId) -> List<QuestPin>` with
  `record QuestPin(EntityRef giver, String giverName, double x, double y, List<QuestRef> quests)` and
  `record QuestRef(long questId, String name, Integer level, boolean starts)` — one pin per giver spawn point, quests
  grouped per giver (both roles loaded; `starts` tells them apart). Parameterised SQL, on-map filter
  (`MapCoordinates`), ordered by giver name then quest level.
- `ui/widgets/MapView`: new `PinKind.QUEST_START` (Questie-style yellow "!" glyph: a bold "!" in `#FFD100` with a
  1 px dark outline, 14 px tall at 100 %) and `PinKind.QUEST_END` (grey "?" glyph, same style), drawn above circle
  pins; a quest pin's tooltip lists the giver and its quests ("Marshal Dughan — Wanted: Hogger (11), A Bundle of
  Trouble (5)"); click on a pin with ONE quest opens that quest (`nav.open(QUEST ref)`); click on a pin with several
  opens the existing cluster-style popup listing the quests (each item opens its quest; a last item "Giver: <name>"
  opens the giver). Pins of the same giver at the same spot are one pin; quest pins cluster with each other but NOT
  with NPC/object pins (separate layer; a cluster of quest pins shows "!" with a count badge).
- `ui/pages/ZoneMapPanel`: a fourth toggle "Quests" (off by default; on in the compact "Pins ▾" menu too) that loads
  quest pins through the existing PinSource/worker path (generation guard, status "Loading pins…", error → revert);
  `UiState.questPinsShown` remembers the toggle (default false). "Quests" can be combined with the other toggles.
- `ui/pages/QuestPage`: the "Given by" section gets the map of the giver's zone with the giver's QUEST_START pin
  (SELF-style emphasis), and "Turned in to" the QUEST_END pin, reusing the NPC/object spawn-map machinery
  (`DetailPanels.load` loads the maps off the EDT; `MapCache`).
**Tests:** `ZoneRepositoryTest.questGiversInZone` (fixture: a Westfall giver with two quests, an object giver, an item
giver skipped, an off-map spawn skipped), `MapViewTest` (quest glyph painted, quest pins cluster only with quest
pins, single-quest click opens the quest, multi-quest click opens the menu with quest + giver items),
`ZonePageTest.questToggleLoadsAndShowsQuestPins` (toggle persisted; combined with "All NPCs"), `QuestPageTest.giverOnMap`,
`UiStateTest` round-trip. Visual check by the controller: Elwynn Forest with "Quests" on — "!" pins at Northshire
Abbey and Goldshire; click Marshal Dughan's pin → quest list.
- [ ] Commit `feat(ui): quest-giver pins on zone maps`.

### Task 14: Final polish, screenshots, docs, CI

**Files:** README screenshots (captured with the controller's script, committed under `docs/screenshots/`), README "Using the app" section with the keyboard map, `ShippedDataTest`/UI smoke additions, CI unchanged unless needed.
- [ ] Commit `docs: screenshots and usage guide`.
