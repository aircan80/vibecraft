# Task 10 report: Compact mode

**Status:** DONE_WITH_CONCERNS (the concerns are small and listed at the end).
**Commit:** `45fba57 feat(ui): compact second-screen mode` on `feature/phase2b-ui`.
**Verification:** `./mvnw -B -q verify` exits 0. 656 tests run, 0 failures, 0 errors, 0 skipped. The app module has 454 of them.

## What was implemented

- **`ui/CompactShell`** is a `JPanel` content pane. Its top bar holds back, forward, the search slot and a pin toggle for always on top. Below it come the page slot and the status slot. It has no rail, no master list and no menu bar. `claim(search, page, status)` moves the window's shared search bar, page holder and status line into the shell. The shell carries the `CompactColumns.KEY` client property, so the tables inside it are compact. A right-click context menu on the top bar is set through `setMenu`.
- **`MainFrame`** keeps one search bar, one page holder (`detailHolder`) and one status line. `showLayout(boolean)` moves these into the full layout or the compact one. It swaps the menu bar (none in compact) and the content pane, then runs `updateComponentTreeUI` on the root pane. That catches up a layout that was off screen through a theme or text size change.
- **F10 (`toggleCompactMode`)** works in this order:
  1. It saves the current layout's bounds to its own slot through `persistence.saveNow()`.
  2. It un-maximises when going compact and sets `compactMode`.
  3. It swaps the layout and calls `setBounds` with the other slot's bounds, validated against the screens. There is no `pack`.
  4. It re-maximises when the full window was maximised.
  5. It blanks the page and calls `controller.rerender()`.
- **Startup honours `compactMode`.** `WindowStatePersistence.install` places the compact window from `compactWindowBounds` and never maximises it. The frame then calls `showLayout(uiState.compactMode())`.
- **`UiState.compactWindowBounds(screens, current)`** returns the saved compact bounds when their title strip is on a screen. Otherwise it returns a 420×720 logical-pixel window (`UIScale.scale`, shrunk to fit) at the top right of the current screen. The title-strip check is shared with `windowBounds` as `reachable`.
- **`WindowStatePersistence.saveNow`** writes only the compact bounds in compact mode. It never touches the full window's maximised flag. A maximised compact window saves no bounds. `install` now returns the saver.
- **`AppController.rerender()`** shows the current target again without touching the history. The same controller instance drives both layouts.
- **Compact tables** have three parts:
  - `LinkTable.setCompact(boolean)` removes the columns that `CompactColumns.kept` does not keep and puts them back in model order. Each `TableColumn` is kept with its renderer and width, so rate bars, origin chips and divider rows survive.
  - `rowTooltip(viewRow)` and the table's `getToolTipText` return the hidden values, for example "Level 20–21 · Zone: The Deadmines". A value that starts with a digit reads "Title value". Any other value reads "Title: value", so the brief's example comes out exactly.
  - `ui/pages/CompactColumns` sits beside `VisibleColumns`. It keeps the first link column (else column 0), Rate and Origin.
- **Ctrl+Shift+T** works as Task 9 built it. It now also updates the pin button, and the pin button calls the same `toggleAlwaysOnTop`.
- **Accessible names.** The compact back and forward buttons reuse `MainFrame.historyButton`, which names them. The pin is named "Always on top". The kind menu shows text and has the accessible name "Search kind: X".

## Calls made where the rulings left a choice

- **Chips: folded into a kind popup, always shown in compact.** I did not hide them below 420 px. `widgets/KindMenu` is a button reading "Items ▾". It opens a menu of the chips as they read, counts included. Choosing an entry clicks that chip, so the search reacts exactly as it does to a chip click. `SearchBar.setCompact(boolean)` hides the "Search:" label and swaps chips for the menu.
- **Density: read from the enclosing shell, neither a global nor a constructor parameter.** A `HierarchyListener` on each `LinkTable` re-reads `CompactColumns.within(this)` whenever an ancestor is re-parented. I chose this over threading a parameter through `DetailPanels.render`, six page constructors, `ListPage`, `LootTables` and `SpawnSection`. A one-off walk at `setDetail` time would also miss `InstancePage`, which builds its loot tables later, on each boss selection. Hierarchy events fire headless, so the tests cover this path.
- **No `Shell` abstraction.** `MainFrame` has a `compact` flag and one `showLayout` method. The full layout's slots are the toolbar, a `detailSlot` panel and the content's SOUTH position.
- **Status line kept in compact.** The ruling lists the top bar and the page. Without the status line, compact mode would hide error messages, "Loading…", Ctrl+D feedback and text size feedback.
- **A full search in compact** shows its results as the page. Enter with no hit, Shift+Enter and "Show all" lead here, and without this the results would go to the hidden master list. `MainView.showsMasterList()` is a new default method that returns true. When it is false, the controller puts a fresh `MasterList` into the detail area. That list has commit only and no previews: Enter or a click opens a row. A newer page load drops the results through a `detailGeneration` guard.
- **Help in compact** is in the top bar's right-click menu: Full window (F10), Dark theme, Keyboard shortcuts, About. The keyboard map still applies. No new key was added; KeyMap has no Help key.
- **Ctrl+B does nothing in compact.** The hidden full layout's list would otherwise toggle and save its visibility.
- **Dark theme menu items** read the saved theme each time their menu opens. Either layout can switch the theme, so this keeps the two check items in sync.
- **The search dropdown is clamped to its screen.** I changed `SearchDropdown.left`. The dropdown is at least 420 px wide and starts at the text field. With the compact window docked at the screen's top right, it would have run off screen.

## Tests (TDD)

**Red:** I wrote the tests first. The first `-pl app -am test` run failed to compile on the missing APIs. Those were `CompactShell`, `KindMenu`, `CompactColumns`, `setCompact`/`rowTooltip`, `rerender`, `history().size()`, `compactWindowBounds`/`COMPACT_*` and `showsMasterList`. The dropdown test failed the same way on the missing `SearchDropdown.left`. Then I implemented until green.

| Test | Covers |
|---|---|
| `CompactShellTest` (7) | Shell layout. Item page drop table shows exactly Source, Rate, Origin, and the tooltip carries level and zone. A page leaving the shell gets all 5 columns back. F10 toggling with a real controller keeps the same `ViewTarget`, the same history size and canBack, and re-renders into the shell with compact columns, then back. A compact full search shows results as the page; commit opens the row and Back returns to the results. Kind menu folds and unfolds. Buttons and pin state. |
| `UiStateTest` (+4) | `compactBoundsSeparate`: the two slots never overwrite each other, and clearing one leaves the other. Default top-right placement on the current screen. Off-screen compact bounds reset. Shrink to a small screen. |
| `WindowStatePersistenceTest` (+2) | Compact saves keep the full window's maximised flag. A maximised compact window saves no bounds. |
| `LinkTableTest` (+4) | Compact keeps name, Rate and Origin with the rest in the tooltip; restores model order. Colon and number formatting, also through `getToolTipText(MouseEvent)`. Renderers survive. Density follows the enclosing area. |
| `AccessibilityTest` (+1) | Walks the compact shell: back, forward, pin and the search clear button are all named. |
| `AppControllerTest` (+1) | `rerender` re-creates the list page with the same target and history; before any open it does nothing. |
| `NavigationHistoryTest` (+1) | `size()`. |
| `SearchDropdownTest` (+1) | The dropdown stays on the anchor's screen. |

## Files changed

- New: `app/src/main/java/dev/wowforever/app/ui/CompactShell.java`
- New: `app/src/main/java/dev/wowforever/app/ui/pages/CompactColumns.java`
- New: `app/src/main/java/dev/wowforever/app/ui/widgets/KindMenu.java`
- New: `app/src/main/resources/dev/wowforever/app/ui/icons/pin.svg`, a hand-drawn push pin in the house style
- New: `app/src/test/java/dev/wowforever/app/ui/CompactShellTest.java`
- Modified: `MainFrame`, `MainView`, `AppController`, `SearchBar`, `UiState`, `WindowStatePersistence`, `detail/LinkTable`, `widgets/SearchDropdown`, `nav/NavigationHistory`
- Modified tests: `StubView` (holder panel, `showsMasterList` flag), `UiStateTest`, `WindowStatePersistenceTest`, `LinkTableTest`, `AccessibilityTest`, `AppControllerTest`, `NavigationHistoryTest`, `SearchDropdownTest`

## Self-review

- **Startup in compact mode.** Bounds come from the compact slot, the window is not maximised, and the shell is shown before `attach`. `controller.start()` then fills the shared holder inside the shell.
- **Toggling twice returns to the saved full bounds.** The F10 that leaves the full window calls `saveNow`, which writes the full slot. While compact, the debounced saves write only the compact slot, because `compactMode` is set before `setBounds` and the save reads the frame when it fires. The F10 that returns reads `windowBounds`, then re-maximises if the full window was maximised.
- **Text size applies in both layouts.** `TextSize` reinstalls the font through `FlatLaf.updateUI`, which updates only the shown layout. `showLayout` runs `updateComponentTreeUI` on the root pane, which catches up the other layout. The page is re-rendered in any case.
- **Discipline.** I worked only in the worktree and never used stash. I did not launch the real window or build artifacts. Python on Windows had rewritten some edited files with CRLF line endings; I converted them back to LF before committing.
- **Not verified:** the swap on a real window. That is F10 with real native bounds, un-maximise and re-maximise, and the right-click menu. The controller's screenshot pass at the Part 1 gate should cover the compact screen.

## Concerns

1. **`MainFrame` grew by about 150 lines,** to roughly 520. About half is the compact context menu and the dark-theme sync. It is still one class, as the rulings allowed. A later split could move both menus into a small `WindowMenus` class.
2. **Some pages are cramped at 420 px.** `InstancePage` keeps its encounter-list and loot split side by side. The `ListPage` filter bar may clip. The home page text still mentions "the icons on the left" and the list. I did not change these pages; that was outside this task.
3. **`ui.detail.LinkTable` now imports `ui.pages.CompactColumns`.** I placed the rule beside `VisibleColumns` as asked. `detail` already depended on `pages` through `DetailPanels`, so this adds no new package cycle.
4. **One change outside the brief:** I clamped `SearchDropdown` to its screen. It is a small change with its own test. Without it, the dropdown would run off the screen's right edge in the default compact position.

## Fix round 1

**Commit:** `499c6c0 fix(ui): testable F10 layout switch; compact tooltips and Esc`.
**Verification:** `./mvnw -B -q verify` exits 0. 672 tests run, 0 failures, 0 errors, 0 skipped. The app module has 470 of them.

### Findings and fixes

1. **The F10 orchestration ran in no test.** The new headless `ui/LayoutSwitch` now owns the slot moves for the toolbar, detail slot, content, shell, search bar, holder and status line. It also owns the static `boundsFor(compact, state, screens, current)` decision and the toggle order. The frame is reached only through a small `LayoutSwitch.Window` port: save bounds, clear or set maximised, show content with or without the menu bar, place, screens. `MainFrame` implements that port in `layoutWindow()`, so it keeps only `setContentPane`, `setJMenuBar`, `setExtendedState` and `setBounds`, plus `updateComponentTreeUI`. `WindowStatePersistence.install` uses `boundsFor` too. The new `LayoutSwitchTest` (13 tests) drives the real toolbar, content, shell, search bar, controller and a real `WindowStatePersistence` behind a recording fake frame. It covers:
   - the exact event order, with the mode set before the window is placed;
   - both directions, with the status line re-added at SOUTH, the search bar back last in the toolbar with its chips, the holder back in the detail slot, and the menu bar on and off;
   - each layout coming back to its own saved bounds;
   - full maximised state restored;
   - `boundsFor` for saved, unsaved and off-screen compact bounds and for the full window;
   - the target and history kept across a toggle.

   The hand-acted toggle test left `CompactShellTest`.
2. **`CompactShell` depended on `MainFrame`.** The back and forward factory moved to `widgets/HistoryButtons`. `MainFrame.historyButton` is gone, and its tests use the new factory.
3. **`LinkTable` depended on `ui.pages`.** `CompactColumns` moved to `ui.detail` and now owns the `ORIGIN` title. `VisibleColumns.ORIGIN` points at it.
4. **Compact tooltips:**
   - `getToolTipText` now joins the row tooltip with the cell's own tooltip. The cell's tooltip goes on a second HTML line and is escaped unless it is HTML already.
   - Hidden-column text now comes from each column's renderer, as `JLabel.getText()` with markup stripped, instead of `value.toString()`.
   - The renderer gets column -1 because a hidden column has no view index. This exposed a bug: `DividerRows`' renderer converted the view index. It now remembers its column's model index, which is also safer while columns are hidden.
5. **Esc in compact focused the hidden list.** It now focuses the page's first table or list, through `LayoutSwitch.escapeTarget()`. On a page without one, such as home, it does nothing.
6. **Compact to full on a search left the detail blank.** `AppController` now remembers the last non-search target shown, and a list refilter updates it. `rerender()` of a search in the full layout first shows that page, or Home when there was none, then runs the search again into the list. The `setDetail(new JPanel())` blanking before re-rendering is gone.
7. **A maximised compact window stayed maximised in full.** `toggle()` now clears the maximised state in both directions before placing the other layout. It re-maximises only when the full window was saved maximised.

### TDD evidence

- The DividerRows bug showed up red. `CompactShellTest` failed with an `ArrayIndexOutOfBoundsException` at -1 until the renderer used its model column.
- Some fixes were written before their tests. For those, I reverted each fix temporarily (`mutate.py` in the scratchpad: no Esc page target, no clearing of maximised when going to full, no previous page on re-render, `value.toString()` text, no cell tooltip join). Seven tests then failed: five in `LayoutSwitchTest` and the two new `LinkTableTest` tooltip tests. After I restored the fixes, all passed.

### Files

- New: `ui/LayoutSwitch.java`, `ui/widgets/HistoryButtons.java`, `test/.../ui/LayoutSwitchTest.java`, `test/.../ui/detail/CompactColumnsTest.java`.
- Moved: `ui/pages/CompactColumns.java` to `ui/detail/CompactColumns.java`, with a reworked API: `Cell`, `tooltip(List<Cell>)`, `join`.
- Changed: `MainFrame` (now 482 lines, down from about 520), `AppController`, `CompactShell`, `WindowStatePersistence`, `detail/LinkTable`, `pages/VisibleColumns`, `widgets/DividerRows`.
- Changed tests: `StubView` (`masterListShown` is now a `BooleanSupplier`), `CompactShellTest`, `LinkTableTest` (+2), `AccessibilityTest`, `MainFrameContentTest`.

### Remaining concerns

The concerns from the first report about cramped pages at 420 px still stand. The real window is still not exercised: native un-maximise and re-maximise, and the right-click menu. That is left to the screenshot pass at the Part 1 gate.
