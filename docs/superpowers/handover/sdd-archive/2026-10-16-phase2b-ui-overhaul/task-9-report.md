# Task 9 report: keyboard map, window state persistence, text size, accessibility

Status: DONE_WITH_CONCERNS (small, listed below). Commit `607b911` on `feature/phase2b-ui`.

## What was implemented

- **`ui/KeyMap.java`** (new). `record Binding(String keys, String action, String description)`; `BINDINGS` holds exactly the ten review P2.6 rows in order. A private stroke table maps each row's action to its key strokes and handler names (`record Stroke(String key, String handler)`).
  - `install(root, handlers)` binds every root stroke on the root pane (`WHEN_IN_FOCUSED_WINDOW`). It throws if a handler in `ROOT_HANDLERS` is missing.
  - `/` is bound as `typed /`. Its action is disabled while the focus owner is a `JTextComponent`, so the key passes on and a slash is typed. A package-private overload takes the focus owner supplier for tests.
  - `Up/Down, Enter` and `Ctrl+1…6` are `DOCUMENTED_ONLY`. Mouse 4/5 has no stroke in the history row, which binds only Alt+Left/Right.
  - `helpTable()` builds the Help › Keyboard shortcuts `JTable` (Keys, Action) from `BINDINGS`.
  - Text size strokes: Ctrl+=, Ctrl+Shift+=, Ctrl+Plus, numpad Ctrl+Add for larger; Ctrl+-, numpad Ctrl+Subtract for smaller; Ctrl+0, Ctrl+Numpad0 for reset.
- **`ui/UiState.java`** completed, all in one `Preferences` node:
  - Window: `bounds`, `maximised`, `screenDeviceId`, `alwaysOnTop`.
  - Layout: `dividerLocation` (existing), `listVisible`, `compactBounds`, `compactMode`.
  - View: `textSizePct`, clamped to 90–150 and snapped down to 10 % steps.
  - Tables: `columnWidths(tableKind)` as a map by column title, and `sort(tableKind)` as `Sort(column title, SortOrder)`.
  - Search and pins: `lastSearchKind` as `Optional<EntityKind>`, `pinned` with `togglePinned`, and `recentSearches` (existing).
  - `windowBounds(List<Rectangle> screens)` keeps saved bounds that intersect a screen. Otherwise it returns 1280×800, shrunk to fit, centred on the first (default) screen. Garbled saved values read as defaults.
- **`ui/WindowStatePersistence.java`** (new). `install(frame, state)` restores bounds, maximised state and always-on-top. It saves 500 ms after the last move, resize or window-state change (`MasterList.timerDelay`), and at once on `windowClosing`.
  - A maximised window keeps its last normal bounds. A minimised window saves nothing.
  - In compact mode the bounds go to `compactBounds`, ready for Task 10.
  - The `Delay` seam is injectable for tests.
- **`ui/TextSize.java`** (new). It captures the base `defaultFont` once at construction. Changes call `UIManager.put("defaultFont", base.deriveFont(size))` then `FlatLaf.updateUI()`, and are persisted. `applySaved()` runs at the end of the `MainFrame` constructor.
- **`MainFrame`**:
  - Keys now go through `KeyMap.install(getRootPane(), handlers())`; the old private `bind` is gone. Ctrl+B moved from the content's input map to the root pane, and the content keeps the `TOGGLE_LIST` action.
  - The list starts hidden when `listVisible` is false, and each toggle persists it.
  - Esc chain: `SearchBar.escape()` closes the dropdown, else clears the text. Failing both, the master list takes the focus.
  - Ctrl+D runs `togglePin(state, current)`, which shows "Pinned" or "Unpinned" and stores `item:ID`-style keys via `Labels.idText`.
  - F10 shows "Compact mode arrives in the next task". Ctrl+Shift+T toggles `setAlwaysOnTop` and persists it.
  - The text-size keys show "Text size N %".
  - Help gets a "Keyboard shortcuts" item. F10 is mapped to `"none"` on the menu bar so FlatLaf's menu "takeFocus" cannot take it.
  - The toolbar was extracted to a static `toolbar(...)` so tests can build the real one.
- **Row copy**. `LinkTable` binds Ctrl+C and Ctrl+Shift+C on its own table through `KeyMap.installRowCopy`, enabled only while a row is selected.
  - Ctrl+C copies the first link column's text, or the first column's.
  - Ctrl+Shift+C copies `Labels.idText`: `item:ID`, `npc:ID`, `quest:ID`, `object:ID`, `zone:ID`, `instance:ID`, and nothing for a boss.
  - Copies go through `setClipboard(ClipboardPort)`, which defaults to the system clipboard. `setIdText(...)` overrides the id text.
  - `SpawnSection` sets the page's `ClipboardPort` and the TomTom `/way` text, so points without coordinates copy nothing.
- **Accessibility**. `SearchBar` names FlatLaf's icon-only clear button "Clear search" (accessible name and tooltip). It uses a container listener registered before `showClearButton`, so the name survives theme changes. The rail and history buttons were already named.
- **Focus ring**. `Component.focusColor = @accentColor` is in both theme `.properties` files; `focusWidth = 2` was already there.

## Tests and results

`./mvnw -B -q -pl app -am test` is green, and the full `./mvnw -B -q verify` exits 0. The only console line is the pre-existing `RealFilesTest` print from another module.

| Test class | Tests | New coverage |
|---|---|---|
| KeyMapTest | 9 | review rows verbatim; help rows equal the list; documented-only rows; every root stroke in the root input map, and nothing else; each key runs its handler; numpad and shift variants; `/` guard; missing handler throws; row copy keys on a LinkTable |
| UiStateTest | 17 | defaults; round trip of every field; clearing; text-size clamp and snap; garbled values; pin toggle; bounds kept on a screen; `offscreenBoundsAreReset`; missing bounds centred; small screen |
| WindowStatePersistenceTest | 5 | debounced save, one per pause; close saves at once; maximised keeps normal bounds; minimised saves nothing; compact bounds |
| TextSizeTest | 3 | steps and clamps with persisted size; saved size scaled from the base; base captured once |
| AccessibilityTest | 2 | every text-less, non-UIResource `AbstractButton` under the real `buildContent` and toolbar has a name (at least 9 found); the clear button is named |
| MainFrameTest | +1 | `togglePin` messages and keys |
| MainFrameContentTest | +1, 1 rewritten | toggle persists `listVisible`; a hidden list starts hidden; Ctrl+B is no longer on the content |
| SearchBarTest | +2 | Esc chain order; clearing works without the focus |
| ThemeManagerTest | +1 | focus width 2 and gold focus colour in both themes |
| LinkTableTest | +4 | copy name and id; keys pass on without a selection; NPC id; custom id text |
| NpcPageTest | +1 | Ctrl+Shift+C on a spawn copies `/way`; no coordinates copies nothing |
| LabelsTest | +1 | `idText` for every kind |

## TDD evidence

- All new and changed tests were written before the code. The red run used the original `UiState`, swapped back in temporarily from `git show HEAD:`. It failed to compile with "cannot find symbol" and "package KeyMap does not exist" across AccessibilityTest, KeyMapTest, MainFrameContentTest, MainFrameTest and more; the output was cut at 60 lines.
- The first green attempt hit one compile error: the `enabled` parameter in `KeyMap.bind` was shadowed by `AbstractAction`'s own field. Renaming it fixed the build.
- Mutation check: with the clear button's `setAccessibleName` removed, both AccessibilityTest tests failed, naming `TextField.clearButton`. The line was then restored.

## Files

New: `app/src/main/java/dev/wowforever/app/ui/{KeyMap,TextSize,WindowStatePersistence}.java`; `app/src/test/java/dev/wowforever/app/ui/{KeyMapTest,TextSizeTest,WindowStatePersistenceTest,AccessibilityTest}.java`.
Changed: `ui/{UiState,MainFrame,SearchBar,Labels}.java`, `ui/detail/LinkTable.java`, `ui/pages/SpawnSection.java`, both theme `.properties` files, and the tests listed above.

## Self-review findings and concerns

1. **Row copy keys are bound on each LinkTable, not the root pane.** A focused `JTable`'s own `ctrl C` copy action, in its ancestor input map, consumes the key before root-pane bindings see it. The ruling's "acts on the focused LinkTable row" is met through `KeyMap.installRowCopy`, so the stroke data still lives in KeyMap. KeyMapTest checks a LinkTable's input map for these keys rather than the root's.
2. **Pin covers entity pages only.** Home, search and list pages show "Only item, NPC, object, quest, zone and instance pages can be pinned". Task 14 may want list pages pinnable, and would need a key format for them.
3. **Some UiState fields are stored but not yet used.** Column widths and sort per table kind, and `lastSearchKind`, have no consumer yet: no table kinds exist to key on, and restoring a kind chip silently felt like a behaviour change outside this task. `screenDeviceId` is saved, but restore decides by bounds only.
4. **Package cycle.** `ui.detail.LinkTable` now imports `ui.pages.ClipboardPort`, while `ui.pages` already imports `ui.detail`. Java allows it and no architecture test exists, but moving `ClipboardPort` to `ui` would remove the cycle.
5. **F10 no longer focuses the menu bar.** That was a FlatLaf/Windows default; Alt still opens the menus.
6. **No real-window run was done.** Tests are headless by rule, and launching the app would write to the user's real preferences. The `MainFrame` wiring has no headless coverage: the Esc fallback that focuses the list, the window listeners, and the handler map. Those need the controller's visual check at the Part 1 gate.

## Fix round 1 (commit b9b04c0)

All six findings are fixed. `./mvnw -B -q verify` exits 0, and the app module now has 433 tests.

1. **Esc chain (Important).** `SearchBar.escape()` now clears through the quiet path, so clearing never reopens the recent searches. The clear also drops pending and running lookups and resets the chip counts. With recents present, the third Esc returns false and focus moves to the list.
   - Related gap found while testing: the dropdown's own Esc key, the real first press in a focused box, hid the list but left the debounced lookup pending, so the hits came back a moment later. `SearchDropdown.Listener` gained a default `closed()` method, which `SearchBar` implements as `stop()`. `escape()` uses `stop()` too.
   - Tests: the chain test now expects two Esc presses and then a fall-through. Two cases were added with recents present and a pending lookup: one through `escape()`, one through the real ESCAPE key. Removing the `closed()` call makes the key-path test fail.
2. **`ClipboardPort` moved** from `ui.pages` to `ui.widgets`. Imports were updated in `DetailPanels`, `LinkTable`, `NpcPage`, `ObjectPage` and `SpawnSection`; the three page classes now import it explicitly. This removes the detail-to-pages dependency.
3. **Pins capped.** `UiState.MAX_PINNED` is 50, and `setPinned` drops the oldest beyond it. It also drops the oldest while the joined text exceeds `Preferences.MAX_VALUE_LENGTH`, so the Ctrl+D write cannot throw. Tests cover 60 pins keeping the newest 50, and 50 pins of 300 characters each fitting into one preference.
4. **Text size.** `TextSize.set` reinstalls the font and calls `FlatLaf.updateUI()` only when the clamped size changed. The new test checks that at 150 % and at 90 % the installed font object stays the same.
5. **AccessibilityTest is deterministic.** It installs `WowForeverDarkLaf` and `Tokens.DARK` directly, then restores the previous look and feel and theme afterwards.
   - Deviation: I did not use `ThemeManager.apply(Mode.DARK)`, because it writes the user's real theme preference. Calling it from a test would change the developer's own setting.
6. **Bounds restore.** `windowBounds` keeps saved bounds only when their top 40 px strip (`TITLE_STRIP`) intersects a screen.
   - `offscreenBoundsAreReset` now also covers a window whose body is on screen while its title bar is above it.
   - The new `aWindowWhoseTitleBarPartlyShowsIsKept` keeps a window with part of its strip showing, and resets one whose strip starts below the screen.

**TDD evidence:** the new and changed tests ran red first, after only the `MAX_PINNED` constant had been added so the suite compiled. Six failed: two SearchBarTest Esc cases, the TextSizeTest limit case, and three UiStateTest cases (both pin cases and `offscreenBoundsAreReset`). The Esc-key test was written after that run. Its first green attempt failed because the test set text in a way that hides the list; a fixed version passed, and the mutation check above confirms it catches a missing `closed()` call.
