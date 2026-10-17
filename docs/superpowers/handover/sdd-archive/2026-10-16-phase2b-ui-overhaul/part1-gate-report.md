# Part 1 gate fix wave — report

Status: DONE. One commit on `feature/phase2b-ui`: `b19c617 fix(ui): Part 1 gate — search list, item page gap, boss levels, empty list, short build id, compact instance layout` (on top of 499c6c0).

## Per finding

### (a) Search → master list
- `SearchBar.Opener` (extends `Consumer<ViewTarget>`) adds `openHit(Entity hit, Search search)`; its default only opens the hit, so `t -> {}` / `opened::add` call sites are unchanged. A dropdown hit (Enter or click) now calls `openHit` with `new Search(text, selected kind)`; pasted pages still go through `accept`. Shift+Enter / Show all / recents unchanged (still `accept(Search)`).
- `AppController.openHit(hit, search)`: `open(hit)` (one history push), then `loadResults(search, hit.ref())`. With an `opened` ref, `loadResults` only fills the master list and selects the row; it does not touch the detail or the window title (so the compact window keeps the page too).
- `MasterList.selectQuietly(ref)`: selects and scrolls to the row, cancels the preview the selection scheduled; false when the row is absent.
- `MainFrame` passes an `Opener` routing `openHit` to the controller.
- Tests: `SearchBarTest.anOpenedHitCarriesTheSearchForItsTextAndKind`; `AppControllerTest.openingADropdownHitAlsoFillsTheListWithItsSearchUnderOneHistoryEntry` (third row selected, no pending preview, title is the page's, Back reaches Home in one step), `anOpenedHitMissingFromItsSearchLeavesTheListUnselected`, `anOpenedHitWithoutAMasterListKeepsItsPageInTheDetail`. Mutation check: with the `loadResults` call removed, the first two controller tests fail.

### (b) Item page gap
- Root cause: `PageContent` used a `BoxLayout` and tracks the viewport height when the page is shorter; BoxLayout handed the spare height to the `HeaderPanel` (a panel with an unbounded maximum height), so it grew to 692 px at 1000×800 and pushed the summary and sections down.
- Fix: `PageContent.TopStack`, a small top-anchored vertical layout: each visible child at its preferred height, width clamped to its min/max and placed by its X alignment, spare height left below the last child. Applies to every page on `PageContent` (item, NPC, object, quest, zone, list, instance loot pane).
- Test: `ItemPageTest.aShortPageStartsAtTheTopWithoutAGapUnderTheHeader` (1000×800: header at its preferred height, summary right below, first section within one header height of the header's bottom). Red before the fix: header height 692 vs 58.
- The `layOut` helper moved from `ItemPageTest` to the shared `Tables` test helper, with `boundsIn`.

### (c) Boss levels
- `InstanceRepository.detail` LEFT JOINs `npc` on `boss.npc_id`; a boss level that is null or ≥ `SKULL_LEVEL` (999, now a public constant on the repository) takes `COALESCE(npc.max_level, npc.min_level)` when that is between 1 and 998; otherwise AtlasLoot's value stays. Note: the schema columns are `min_level`/`max_level` (the brief said `level_min/level_max`).
- `InstancePage.levelText` returns null for null, ≤ 0 and skull levels, so the encounter label reads "1. Lucifron  Boss" and the boss header has no "Level ??".
- Tests: `InstanceRepositoryTest.aSkullBossLevelIsFilledFromItsNpc` (Lucifron 999 → 63; Magmadar without an NPC row keeps 999; Taragaman keeps AtlasLoot's 16); `InstancePageTest.aSkullLevelTakesTheNpcLevelAndAnUnknownLevelIsLeftOut` (replaces the old "Lv ??" test; also the boss header subtitle). Both red before the fix (999, "Lv ??").

### (d) Empty master list heading
- `MasterList` center is a `CardLayout` of the rows and a muted, top-aligned placeholder label `"Search results appear here"` (`MasterList.PLACEHOLDER`, foreground and background looked up per paint). `clear()` shows it and hides the heading; `show`/`setEntries` bring the heading and rows back. A new list starts cleared. A search with 0 hits keeps its "Search: x (0)" heading.
- `AppController.showHome` calls `clear()` instead of `show("Results", [], false)`.
- Tests: `MasterListTest.anEmptyListBeforeAnySearchShowsAPlaceholderInsteadOfAHeading`; `AppControllerTest.startOpensHome` asserts the placeholder.

### (e) Build hash
- `StatusBar.setBuild(id)` shows `StatusBar.buildLabel(id)` = "Data build " + first 8 characters, with the whole id as the label's tooltip; null clears both. `setInfo` replaced (its only caller was the controller).
- The build label hides while the status line sits inside a component carrying `CompactColumns.KEY` (the compact shell), re-checked on every re-parent (same mechanism as `LinkTable`), and comes back in the full layout.
- Home page text uses `buildLabel`; About still lists the full `build_id` from meta (unchanged, covered by `MainFrameTest.aboutTextShowsNoticeAndDatabaseMeta`).
- Tests: new `StatusBarTest` (short label + tooltip, short ids unchanged, hidden under the compact key and back); `CompactShellTest.theStatusLineHidesTheDataBuildInTheShell`; `AppControllerTest.theHomePageAndStatusShowTheShortBuildId` (red before the welcome change: the home text held the full hash) and `startOpensHome` now expects exactly "Data build b-42".

### (f) Compact instance page
- `InstancePage.doLayout` (after `super.doLayout`, so the split already has its new bounds) turns the split vertical when the page is narrower than `STACK_BELOW` = 600 (scaled with `UIScale`), divider at 40 % of the height; back to horizontal (`resetToPreferredSizes`) when wider. `isStacked()` exposes the state.
- Test: `InstancePageTest.aNarrowPageStacksTheEncountersAboveTheLootAndAWideOneSetsThemSideBySide` (420×720: stacked, encounter list at least its preferred width, loot below and page-wide; 1000×720: loot right of the list; back to 420: stacked again).

## Test commands and output

```
./mvnw -B -q -pl app -am test -Dtest=<class list> -Dsurefire.failIfNoSpecifiedTests=false   (per finding, red then green)
./mvnw -B -q verify   -> exit 0; only console line: "RealFilesTest: parsed 41 files, 7 embedded chunks" (pre-existing importer test print)
```

Surefire totals after verify: app 483 run, 0 failures, 0 errors, 0 skipped (470 before, +13); importer 141 run, 0 failures. All v0.1 tests included.

## Files changed
- main: `db/InstanceRepository.java`, `ui/AppController.java`, `ui/MainFrame.java`, `ui/SearchBar.java`, `ui/StatusBar.java`, `ui/pages/InstancePage.java`, `ui/pages/PageContent.java`, `ui/widgets/MasterList.java`
- test: `db/InstanceRepositoryTest.java`, `ui/AppControllerTest.java`, `ui/CompactShellTest.java`, `ui/SearchBarTest.java`, `ui/StatusBarTest.java` (new), `ui/pages/InstancePageTest.java`, `ui/pages/ItemPageTest.java`, `ui/pages/Tables.java`, `ui/widgets/MasterListTest.java`

## Self-review
- Completeness: all six findings fixed, each with at least one headless test.
- TDD: (b), (c), (e home text) and (a) were seen failing on behaviour before the fix ((a) by removing the fix and re-running). (d), (f) and the StatusBar API were red at compile time (new API) and green after; I did not separately watch a behavioural red for them.
- Scope: no restructuring beyond the brief; the only shared-helper move is `layOut` into the test `Tables`.
- Pristine output: verify prints nothing from the app module.

## Concerns
- (b) `TopStack` replaces BoxLayout for every page body. Children never stretch vertically now; no current page relies on that (searched: no glue/struts in pages). Worth a glance at the quest page (wrapped objectives text) in the screenshot tour.
- (a) After `openHit`, the status line ends on "n results" for the list load, as for any search; the window title keeps the page's name.
- (a) In the compact window the search still fills the hidden master list (so F10 back to full shows it); the detail keeps the page.
- (c) A boss whose NPC has a level range shows the NPC's highest level.
- (f) The 40 % stacked divider is a judgement call; the split stays user-draggable.
- Commit trailer is `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, per my session's attribution instruction.

## Fix round 2 (re-review: TopStack minimum-width regression)

Status: DONE. Commit `fix(ui): page bodies shrink children to the page width again` (on b19c617).

- Cause: `PageContent.TopStack.layoutContainer` floored each child's width at its minimum width. The page tracks the viewport width and has no horizontal scrollbar, so children wider than the page were cut off. A line-wrapped `JTextArea` reports its current width as its minimum, so quest objectives never narrowed after the page had been wide. The wrapping `FilterBar` reports its one-row width, so it stopped wrapping. Long header rows and zone link rows were clipped too.
- Fix: `int width = Math.min(c.getMaximumSize().width, available);`, with no minimum-width floor, as in BoxLayout. The class doc now says why.
- Tests:
  - `QuestPageTest.theWrappedObjectivesNarrowWithThePageAfterBeingWide` lays the page out at 900 then 420 px. The objectives and header end at most 420 px wide, and the text re-wraps to a taller preferred height. One layout pass is enough.
  - `ListPageTest.theLaidOutFilterBarShrinksToANarrowedPageAndWraps` lays the page out at 900 then 420 px. The filter bar, header and results section end at most 420 px wide, and the filter bar gets taller because it wraps.
  - Both failed before the fix, on the "at most 420" width assertions.
- Divider: `InstancePageTest.aNarrowPageStacksTheEncountersAboveTheLootAndAWideOneSetsThemSideBySide` now checks the stacked split after the first layout. The divider sits at 40 % ± 2 % of the height minus the divider. The encounter pane is laid out at that height ± 2 px. This passed without a code change: `setDividerLocation(double)` runs after `super.doLayout()`, so the split already has its new size, and the split's own layout pass in the same validation keeps the location. The reset the reviewer suspected does not happen here. A package-private `InstancePage.split()` accessor was added for the test.
- Verify: `./mvnw -B -q verify` exit 0. App 485 tests, importer 141, 0 failures. The only console line is the existing importer `RealFilesTest` print.
- Concern: the divider check runs headless and uses the same layout order as Swing's validation (parent `doLayout`, then the children). Interactive behaviour on a real window is left to the controller's screenshot tour.
