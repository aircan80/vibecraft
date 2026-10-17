**Needs fixes** — spec ✅, quality ❌ (Critical 1, Important 4, Minor 4)

# Task 5 review: shared page skeleton and section rules (c49470c)

Reviewed `git show c49470c` against `task-5-brief.md`, plan Global Constraints (lines 30–147), spec §6 and the implementer's report. Read-only; no Maven run (the controller reports verify green: lua-table 61 / importer 420 / app 864).

## Spec compliance ✅

Every brief requirement and named test is present:

- `SectionOrder.ITEM`, `NPC`, `ZONE` and `INSTANCE` are verbatim (`SectionOrderTest.theOrdersPerPageType`).
- `HeaderPanel.setPlate`, `setKindWord`, `addChip`, `addChipLink` (returns the `JButton`, a harmless widening) and `setFacts` exist. The plate paints `#0C1424` in both themes (`HeaderPanelTest.chipsAndPlate`).
- `SourceSection.ROW_LIMIT = 8`, `showAllButton()` reading "Show all 34", `setCollapsible` and `setCollapsed` exist.
- `ItemSummaryLine.of` is positive-only.
- `AppController.LOADING_DELAY_MS = 150` with the `MasterList.Delay` seam (ruled), and a finished load cancels the delay.
- `LoadingPage("Hogger", "Loading drops…")` exists, titled from the hit, else the list entry, else "NPC #448".
- In the compact window every section but the first starts collapsed.
- Named tests: `ItemPageTest.factsStripIsPositiveOnly` and `sectionOrder`, `NpcPageTest.sectionOrder`, `ZonePageTest.sectionOrder`, `InstancePageTest.questsSection`, `SourceSectionTest.eightRowsThenShowAll` and `compactCollapsesAllButFirst`, `HeaderPanelTest.chipsAndPlate`, `AppControllerTest.loadingPageOnlyAfterDelay`.
- `InstancePageTest.questsSection` uses fixture instance 2 with a constructed list. `DetailPanelsTest` pins `byZone(1581)` and `ShippedDataTest` pins instance 3 listing 166. That is an honest split of the brief's single test.
- Huge Gnoll Claw reads "1 NPC" in the shipped data, as ruled.

## Specific checks requested

- **The loading page can never replace a finished page. Verified.** Both callbacks run on the EDT. `timerDelay.cancel()` stops the timer and nulls the task, so a timer event already queued finds no task. Success and failure both cancel before rendering, and the scheduled run also checks `generation == detailGeneration`. Scheduling before `Async.run` handles the inline worker. `show`, `reshow` and a new `showEntity` all cancel.
- **History and back/forward are unaffected. Verified.** The loading page goes through `setPage(null, …)`. History is untouched, and `stateOf` returns null while it shows, so no scroll state leaks across entities. Everything runs on the EDT.
- **"Show all" keeps the heading count, sort and selection. Verified.** The cut is a height clip, so the model, sorter and selection are untouched. Exception: see I1 for grouped tables.
- **Ctrl+n with collapsed sections: broken.** See C1. There is no KeyMap conflict, because `Ctrl+1…6` is the only Ctrl+digit binding.
- **Collapsible headings are keyboard-operable, but the accessible name does not carry the state.** See I4.
- **Boss loot should be exempt from the 8-row limit.** See I3.
- **No database access on the EDT. Verified.** The instance quests load in `DetailPanels.load` on the worker. The boss-loot exception is unchanged.
- **Compact-mode layout tests still pin 420 px. Verified.** `ShippedDataTest.theCompactSpawnTableKeepsItsWholeRowUnderTheMap` keeps `setSize(420, 900)`. It now opens the collapsed Location section first, which is honest.

### Existing tests whose expectations changed (all honest)

| Test | Change | Reason |
|---|---|---|
| `ItemPageTest.everyKindHasItsSectionInTheFixedOrder` | New section order | `SectionOrder.ITEM`, unlisted titles after |
| `ItemPageTest.summaryCountsEachKind` | Drops "no vendor · no quests" | Positive-only facts |
| `ItemPageTest` source-filter test ("the NPCs shown, every loot list") | Same | Same |
| `ItemPageTest.aShortPageStartsAtTheTopWithoutAGapUnderTheHeader` | Summary is inside the header | Facts strip moved into `HeaderPanel` |
| `ZonePageTest.instancesAndSubZonesAreLinksAtTheTop` | "Quests", "NPCs" order | `SectionOrder.ZONE` |
| `ZonePageTest.mapShownWithPins` | Comment only | Assertion unchanged and still true |
| `NpcPageTest.spawnsOnMap`, `onlyTheSpawnsInTheMappedZone…` | "Spawns" to "Location" | Rename |
| `ObjectPageTest` (2 places), `SourcesPageIconTest` | "Spawns" to "Location" | Rename (ruled for objects too) |
| `ShippedDataTest.theCompactSpawnTableKeepsItsWholeRowUnderTheMap` | Rename, plus asserts Location starts collapsed and opens it | Compact collapse rule |
| `ShippedDataTest.theShippedPagesReadAsReviewed` | VanCleef assertion split, checks added | Additive |

## Findings

### Critical

**C1. Ctrl+n in the compact window jumps to the wrong section or throws `IndexOutOfBoundsException`.**
- **Where:** `app/src/main/java/dev/wowforever/app/ui/pages/SourcesPage.java:223-236` together with `app/src/main/java/dev/wowforever/app/ui/widgets/SourceSection.java:256-270`.
- **What is wrong:** `jumpTargets()` keeps only sections whose `table().isVisible()` is true, meant to skip sections the source filter emptied. `setCollapsed(true)` hides every direct child but the heading, and for an ordinary section the `LinkTable` is that child. So in the compact window every collapsed section drops out of `jumpTargets()`. A Location section with a map is the exception, because its table sits inside the hidden body and keeps its own visible flag.
- **The effect:** take an NPC page with Drops, Sells, Location, Starts quests and Ends quests in compact mode. `jumpTargets()` is then [Drops, Location]. Ctrl+2 opens Location instead of Sells. Ctrl+3 to Ctrl+5 throw `IndexOutOfBoundsException` on the EDT. `bindJumpKeys` counted 5 targets at construction, before the page entered the compact shell.
- **Why it matters:** the `if (section.isCollapsed())` branch in `jumpTo` is dead for ordinary sections, so the report's claim that "Ctrl+n opens a collapsed section" is untrue. This also regresses Ctrl+n in compact mode, which worked before this task. No test covers `jumpTo` in compact mode.
- **Fix:** do not use the table's visible flag for "emptied by the filter". Either:
  - record it explicitly, for example `SourceSection.markEmptied()` called from `SourceFilters.annotate` and an `isEmptied()` getter; or
  - read the remembered value for a collapsed section, `collapsed ? shownBeforeCollapse.get(table) : table.isVisible()`, exposed as `SourceSection.hasRowsShown()`.

  Use that in `jumpTargets()`. Add a test that puts a five-section `NpcPage` under `CompactColumns.KEY`, calls `jumpTo(1)`, and asserts that "Sells" opens and has focus. Also assert that `jumpTo(4)` does not throw.

### Important

**I1. "Show all n" and the 8-row cut count group-heading and divider rows, so the button disagrees with the heading.**
- **Where:** `SourceSection.java:158-159`. The text is `showAllText(Math.max(count, rows))`, the cut is `rows > ROW_LIMIT`, and `rows = table.getRowCount()`.
- **What is wrong:** several tables carry non-data rows:
  - NPC Drops and instance boss loot use `LootTables.grouped`, which has category or slot heading rows;
  - the item page's "Dropped by" has the unknown-rate `DropRow.Divider`.
- **Example:** "Drops (20)" with 6 group headings reads "Show all 26". "Dropped by (8)" with a divider gets a cut and a "Show all 9" button that reveals one row.
- **Why it matters:** spec §6 and the brief pair the heading "(34)" with "Show all 34". A number that disagrees with the heading reads as a bug. The cut is also unpredictable: 8 rows may show only 4 or 5 items under headings.
- **Fix:**
  - Use the section's `count` for the button text.
  - Cut only when `count > ROW_LIMIT`.
  - Make the limit count data rows. Let `LinkTable.setRowLimit` take a predicate that says which rows count, the same one `DividerRows` already uses, and clip after the 8th data row.
  - Add a `SourceSectionTest` case with divider rows.

**I2. The rail list pages are now cut to 8 rows.**
- **Where:** `app/src/main/java/dev/wowforever/app/ui/pages/ListPage.java:74` (the "Results" section) and `:69` (the Dungeons & Raids level bands). Both use `SourceSection`, so they inherit `ROW_LIMIT`.
- **What is wrong:** the Items, NPCs, Quests and Zones list pages open with 8 rows and a button such as "Show all 20,492". The brief and spec §6 scope the rule to entity-page sections, the shared page skeleton. A browse list showing 8 rows is a regression no test caught, because the clip leaves `getRowCount()` unchanged.
- **Why it matters:** browsing by rail is a primary path. Every filter change on a fresh page shows 8 rows.
- **Fix:** give `SourceSection` an opt-out, for example `setRowLimited(false)` or a constructor flag, and use it in `ListPage`. Add a `ListPageTest` assertion that `shownRows()` equals the row count. Also decide whether level bands should collapse in compact mode; leaving them open is the safer default.

**I3. Boss loot should be exempt from the 8-row limit, which answers the controller's question.**
- **Where:** `app/src/main/java/dev/wowforever/app/ui/pages/InstancePage.java:364`.
- **Why exempt it:**
  - The boss loot table is the instance page's primary content, not one source list among several. Spec §6 describes it as "Loot (by boss, slot toggle)", already shaped by the encounter selection and the slot groups.
  - `showLoot` builds a new `SourceSection` on every encounter switch, so "Show all" resets each time. A user scanning bosses would click it on every boss.
  - The slot heading rows use up the 8 (I1), so a boss would show about 5 items.
  - A boss has a few dozen rows at most.
  - The implementer's reason, keeping Quests reachable, is weak. The loot pane scrolls, and Quests can get a Ctrl+n target.
- **Fix:** apply the same opt-out as I2 to `lootSection`. Keep the limit on the instance Quests section.

**I4. The collapsible heading is keyboard-operable but invisible to assistive technology and to keyboard users.**
- **Where:** `SourceSection.java:212-245`, with `SectionHeading`, a plain `JPanel`.
- **What is wrong:**
  - `setCollapsible(true)` makes the heading focusable, but the focused heading paints no focus indicator. FlatLaf paints none for a `JPanel`.
  - Its accessible role stays PANEL with no accessible name. No `AccessibleState.EXPANDED` or `COLLAPSED` is ever set, and `setCollapsed` changes only the ▸/▾ glyph. A screen reader hears "panel", not "Location (3), collapsed".
  - The "Show all" button also has `setFocusPainted(false)` and an empty border, so its keyboard focus is invisible too.
- **Why it matters:** the controller asked for accessible names that say the state. The plan's accessibility bar (colour never the only sign) points the same way: the state is now carried only by a small glyph.
- **Fix:**
  - Give the heading an accessible name of `heading.text() + (collapsed ? ", collapsed" : ", expanded")`, updated in `setCollapsed`, and fire the matching `AccessibleState` change. An alternative is to make the toggle a real `JToggleButton` inside the heading, which also brings Space/Enter and a FlatLaf focus ring.
  - Paint a focus ring, or a gold underline, while the heading has focus.
  - Let the "Show all" link button paint focus.
  - Add test assertions on the accessible name in both states.

### Minor

**M1. A load that fails after the loading page shows leaves "Loading drops…" on screen indefinitely.**
- **Where:** `app/src/main/java/dev/wowforever/app/ui/AppController.java:446-449`.
- **What is wrong:** the error path cancels the delay, but by then the `LoadingPage` may be up. Only the status line says "Error: …".
- **Fix:** in the error callback, when `page instanceof LoadingPage`, either replace its line ("Could not load: …") or show the not-found page. Add a test: fire the delay, run a failing load, and assert the page no longer reads "Loading…".

**M2. Removing a section from its parent resets its compact state.**
- **Where:** `SourceSection.java:187-194`.
- **What is wrong:** on removal `followWindow` sees no parent, treats the section as "not compact", and expands it. On re-add it collapses it again. `InstancePage.showLoot` calls `lootPane.removeAll()` on every encounter switch (`InstancePage.java:365`, re-add at `:372`). In compact mode the instance Quests section therefore re-collapses after every switch, which contradicts the javadoc's "keeps what the user opened".
- **Fix:** return early from the hierarchy listener while `getParent() == null`. Optionally keep `inCompact` from the last real parent.

**M3. A collapsed section that gets new rows loses its "Show all" button when expanded.**
- **Where:** `SourceSection.java:159` and `:270`.
- **What is wrong:** while collapsed the table is hidden, so `updateLimit()`, triggered by a model change or `setCount`, computes `cut = false` and hides `showAllRow`. `setCollapsed(false)` restores only direct children (heading, table or body, foot), not `showAllRow` inside `foot`. The table then clips to 8 rows with no button.
- **Fix:** call `updateLimit()` at the end of `setCollapsed`, or drop the `table.isVisible()` term and rely on the collapsed `foot` hiding the row.

**M4. `SectionOrder.INSTANCE` is not used by any code; it is referenced only in javadoc.**
- **What is wrong:** `InstancePage` hard-codes its layout: encounters left, loot then Quests right. That matches the order today, but nothing pins it.
- **Fix:** either add an `InstancePageTest` assertion that the visible blocks follow `SectionOrder.INSTANCE`, or note in `SectionOrder`'s javadoc that the instance page's two-pane layout realises the order by hand.

### Not findings, for awareness

- The compact expanded state is lost whenever the page is rebuilt, for example by `rerender` after a source filter change. `PageState` saves only the scroll position. This is acceptable for now, but worth adding to `Scrolled` later.
- Spec §6 says the "header renders immediately". The implementation shows the old page for 150 ms, then the header and loading line together. This follows the brief's test ("no LoadingPage before the delay fires"), so no change is needed.
- The report says nothing was seen rendered. A screenshot pass should check:
  - that the ▸/▾ glyphs exist in the body font;
  - the kind word next to subtitles such as "NPC #639";
  - the plate's alignment beside the 20 pt title.

## Strengths

- The cut is a height clip, which keeps the model, sort, selection, copy and existing row-count tests intact.
- The loading-page race handling is careful and correct.
- `SectionOrder.sort` is a stable comparator sort, simple and well tested.
- The instance quests load is put on the worker cleanly through `LoadedInstance`.
- The tests that changed were changed honestly, and the open choices are documented plainly in the report.
