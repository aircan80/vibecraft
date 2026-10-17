### Task 6: Merged Dropped by, section filter, search chips and master-list auto-collapse

**Files:**
- Modify `{main}ui/DropRow.java` (+ instance-loot rows), `{main}ui/Columns.java` (`dropSourcesMerged()`), `{main}ui/pages/ItemPage.java` and `{main}ui/pages/NpcPage.java`.
  - `NpcPage`: drops are grouped "Notable" first (quality ≥ 4 or item class 12 Quest), then by category.
- Create `{main}ui/widgets/SourceFilterControl.java` (a small combo beside the heading, with the "≠" legend in its tooltip). Modify `{main}ui/WindowMenus.java`, which drops the View › Drop sources menu now that the filter lives on the sections.
- Modify `{main}ui/SearchBar.java` and `{main}ui/widgets/SearchDropdown.java`: kind chips move into the dropdown's top row, and the toolbar has none.
- Create `{main}ui/ListVisibility.java`. Modify `{main}ui/MainFrame.java` (wires `ListVisibility`; `toggleList` goes through it), `{main}ui/AppController.java` and `{main}ui/MainView.java` (+ `onResultOpened()`, `onResultsShown()`).
- Tests: create `{test}ui/ListVisibilityTest.java` and `{test}ui/widgets/SourceFilterControlTest.java`; modify `ItemPageTest`, `NpcPageTest`, `SearchBarTest`, `SearchDropdownTest`, `KindChipsTest`, `MainFrameContentTest`, `AppControllerTest` and `WindowMenusTest`.

**Interfaces:**
- Consumes: 2B `SourceFilters.apply`, `UiState.sourceFilter`, `InstanceLoot`, `LootListRow`.
- Produces:
  - `sealed interface DropRow` gains `record Loot(InstanceLoot loot)`. `DropRow.merged(List<DropSource> npcRows, List<InstanceLoot> loot) -> List<DropRow>` sorts by rate descending, then unknown rates after the divider. In the Source column, the instance shows as a `Chip` ("Molten Core").
  - `SourceFilterControl(SourceFilter current, Consumer<SourceFilter> onChange)`, with `static final String LEGEND`.
    - The `LEGEND` tooltip reads "≠ The sources disagree on this rate; hover the rate to see each source." Task 7 adds the line "V Vanilla rate (VMaNGOS); hover for details."
  - `final class ListVisibility`:
    - `ListVisibility(boolean savedVisible)`; `static final int WIDE_PX = 1600`.
    - `void resultsShown()`, `void previewed()` (no change), `void resultOpened(int windowWidth)`, `void toggle()` (Ctrl+B), `boolean visible()`, `boolean pinned()`.
- [ ] **Step 1: failing tests.**
  - `ItemPageTest.droppedByMergesInstanceLoot`: Felheart Belt 16806 lists its AtlasLoot boss row inside "Dropped by", with a "Molten Core" chip, sorted by rate among the NPC rows. No "In instance loot" section exists.
  - `ItemPageTest.filterNeverHidesInstanceRows`: with `SourceFilter` hiding AtlasLoot-only rows, the instance loot row stays and the NPC rows are filtered; the note counts NPC rows only.
  - `SourceFilterControlTest.changesFilterAndRerenders`: selecting "Hide AtlasLoot-only" calls `onChange`. `UiState.sourceFilter` round-trips. The tooltip equals `LEGEND`.
  - `NpcPageTest.notableDropsFirst`: an epic and a quest item lead the Drops table under "Notable".
  - `SearchBarTest.noKindChipsInToolbar`: the toolbar component tree contains no `KindChips`. `SearchDropdownTest.kindChipsInDropdownFilter`: clicking "NPCs" in the dropdown row narrows its hits to NPCs.
  - `ListVisibilityTest.previewNeverCollapses`: `resultsShown(); previewed();` → `visible()`.
  - `ListVisibilityTest.openingAResultCollapses`: `resultsShown(); resultOpened(1280)` → not visible.
  - `ListVisibilityTest.ctrlBKeepsTheListOpen`: `toggle()` while hidden → visible and `pinned()`; `resultOpened(1280)` → still visible; `toggle()` → hidden and unpinned.
  - `ListVisibilityTest.wideWindowsKeepTheList`: `resultOpened(1600)` → visible.
  - `ListVisibilityTest.searchingShowsTheList`: hidden, then `resultsShown()` → visible.
  - `AppControllerTest.commitCollapsesPreviewDoesNot`: with a `StubView` recording calls, `preview(ref)` makes no `onResultOpened`; `commit(ref)` and `openHit(...)` make one each.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. `UiState.listVisible` keeps storing the pinned state only.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): merged Dropped by, section source filter, search kind chips and master-list auto-collapse`.

