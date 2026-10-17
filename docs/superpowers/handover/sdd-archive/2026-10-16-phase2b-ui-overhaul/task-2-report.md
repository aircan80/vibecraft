# Task 2 report: master list, icon rail and detail-first split

**Status:** DONE_WITH_CONCERNS (small, deliberate deviations listed under Concerns)
**Commit:** `d226e9a feat(ui): master list, icon rail and detail-first split` on `feature/phase2b-ui` (not pushed)

## What was implemented

- **`ui/widgets/MasterList`** (a `JPanel`): a bold heading `"<title> (<n>)"` or `"<title> (first <n>)"` over a `JList<ListEntry>` in a scroll pane.
  - Two-line `EntryRenderer`. The title uses `Theme.current().textPrimary()`. The subtitle uses `textMuted()` at list font size −1. Selected rows use the list selection colours.
  - The row height is fixed from a prototype entry. The cell width is fixed, so a 21,543-row "All items" list is never measured row by row.
  - The list tracks the viewport width. There is no horizontal scrollbar, and long names end in an ellipsis.
  - `Delay` seam: `interface Delay { void schedule(Runnable r); void cancel(); }`. `timerDelay(ms)` is a one-shot `javax.swing.Timer`, and the default is `PREVIEW_DELAY_MILLIS = 120`.
  - A selection change that is not adjusting runs `cancel()` and then `schedule(previewSelected)`. The preview reads the selection when it fires.
  - Enter (`WHEN_FOCUSED` binding "commit") or a left double-click on a cell calls `commitSelected()`. That cancels the pending preview and calls `onCommit` immediately.
  - API: `show(title, entries, capped)`, `setEntries`, `selectIndex`, `commitSelected`, `entries()`, `selected()`, `heading()`, `jlist()`, `onPreview`, `onCommit`.
  - `setEntries` drops any pending preview.
  - The renderer overrides `validate()` to call `doLayout()`. Without this, a container with no native peer never lays out, and the cells paint blank in headless printing and tests. I found this with a headless render.
- **`ui/widgets/ListEntry`**: a top-level record `(EntityRef ref, String title, String subtitle, Integer quality, Long iconId)`. A null subtitle becomes "". `toString()` returns the title, so JList type-ahead matches names.
- **`ui/widgets/Rail`**: a vertical, non-floatable `JToolBar` with one `JToggleButton` per `RailTarget` in a `ButtonGroup`.
  - Each button has a tooltip and an accessible name equal to its label.
  - The normal icon uses the text.primary tint. The selected icon uses the accent gold tint.
  - The preferred and minimum width is `UIScale.scale(48)`.
  - API: `onSelect(Consumer<RailTarget>)` fires on click, `select(target)` marks a button without firing (null clears), and `button(target)` returns a button.
- **`ui/widgets/RailTarget`**: `HOME, ITEMS, NPCS, QUESTS, ZONES, INSTANCES`, each with a label and an icon name.
- **`ui/widgets/Icons`**: `icon(name, size)` and `icon(name, size, Function<Theme, Color> tint)` load `dev/wowforever/app/ui/icons/<name>.svg` as a `FlatSVGIcon`.
  - A `ColorFilter` maps every colour to the theme token at paint time, keeping alpha, so icons follow a theme switch.
  - An unknown name throws `IllegalArgumentException`.
- **Six hand-written 24×24 single-`<path>` SVGs**: home (house), items (sword), npcs (person), quests (scroll), zones (map pin) and dungeons (castle).
  - The sword's sub-polygons share one winding so their overlaps fill solidly. The scroll and the pin use `fill-rule="evenodd"` for their holes.
  - I checked them visually in a headless render in both themes.
- **`ui/UiState`** (minimal): `new UiState(Preferences)` and `load()`, which uses the `dev/wowforever/app/ui` user node that `ThemeManager` also uses.
  - `dividerLocation()` defaults to `UIScale.scale(MasterList.WIDTH)` (300).
  - `setDividerLocation(px)` ignores values ≤ 0.
  - The key is `layout.dividerLocation`.
- **`nav/NavigationHistory.replaceCurrent(t)`**: replaces the current entry in place and keeps forward entries. On an empty history it pushes.
- **`ViewTarget.ItemClass.ALL_CLASSES = -1`**: `ItemClass(-1, -1)` means "All items", and the repository is called with no class filter.
- **`ui/Subtitles`** (package-private stop-gap for Task 4's `Labels`). Examples:
  - `"Armor · Cloth · iLvl 19 · Req 14"`
  - `"NPC · Level 11 · Elite · Elwynn Forest"`
  - `"Object · Elwynn Forest"`
  - `"Quest · Level 12 · Westfall"`
  - `"Subzone · Elwynn Forest"`
  - `"Raid (40) · Level 55–60 · Burning Steppes"`

  Unknown, null and ≤ 0 levels are skipped. Rank "Normal" is skipped.
- **`AppController`**:
  - It wires `view.masterList().onPreview(e -> preview(e.ref()))` and `onCommit(e -> commit(e.ref()))` in its constructor.
  - The `Table`/`ResultsTableModel` path is replaced by a `Listing(title, entries, page, zonePicker)` record built from `ListEntry`s, with the same generation guard, status line and capped title.
  - `preview(ref)` shows the detail only and leaves the list alone. It uses `replaceCurrent` when the current slot is a preview and `push` otherwise.
  - `commit(ref)` takes the preview slot if there is one, otherwise it pushes, and then pins the entry.
  - `open`, `back` and `forward` clear the preview flag.
  - `static ViewTarget target(RailTarget)` gives the rail mapping:
    - HOME → `Home`
    - ITEMS → `ItemClass(-1, -1)`
    - NPCS → `List(NPC)`
    - QUESTS → `List(QUEST)`
    - ZONES → `List(ZONE)`
    - INSTANCES → `List(INSTANCE)`
  - `List(NPC)` and `List(QUEST)` show "NPCs: pick a zone" and "Quests: pick a zone" from `zones().all()`. Previewing a zone row shows the zone page. Committing a zone row runs `openZoneList(kind, zoneId)`, which lists "NPCs in Elwynn Forest" and so on.
  - Home clears the list ("Results (0)"). The welcome text now mentions the icons, preview, Enter/double-click and Ctrl+B.
- **`MainView`**: `MasterList masterList()` replaces `results()`.
- **`MainFrame`**:
  - `buildContent(toolbar, rail, list, detail, status, UiState)` builds the layout below.
  - The divider starts at `state.dividerLocation()`. A `DIVIDER_LOCATION_PROPERTY` listener saves it while the list is visible.
  - Ctrl+B is bound on the content pane (`WHEN_IN_FOCUSED_WINDOW`, action key `MainFrame.TOGGLE_LIST`). It hides the list and sets the divider size to 0, or shows the list and restores the divider size and the saved location.
  - View › "Show list" (Ctrl+B) is a checkbox menu item on the same action, kept in sync through component shown/hidden events.
  - `attach(controller)` no longer takes a tree.
  - The rail opens `AppController.target(t)`.
- **`Main`**: no longer loads `BrowseTree.Data` or constructs a `BrowseTree`. `BrowseTree` and its tests stay for Task 8 and still compile and pass.
- **Deleted**: `ResultsPanel` and `ResultsPanelTest`. `ResultsTableModel`, `Columns` and `TableLinks` are kept, and the `TableLinks` doc no longer links to `ResultsPanel`.
- **`app/pom.xml`**: added `com.formdev:flatlaf-extras:${flatlaf.version}` (3.6), which brings in `com.github.weisj:jsvg:1.4.0` at runtime.

## Layout as built

```
+--------------------------------------------------------------------------------+
| NORTH  JToolBar:  [<-] [->] [ search field ..................... ] [kind v]    |
+------+----------------------------+--------------------------------------------+
| Rail | MasterList (300 lpx,       || Detail holder                             |
| 48px |  divider saved in UiState) ||                                           |
| [H]  | Search: westfall (5)       ||  (detail panel of the previewed or        |
| [I]  | Subterranean Cape          ||   committed entity)                       |
| [N]  |   Armor · Cloak · iLvl 24  ||                                           |
| [Q]  | Hogger                     ||                                           |
| [Z]  |   NPC · Level 11 · Elite   ||                                           |
| [D]  | ...                        ||                                           |
|      |  <---- JSplitPane(HORIZONTAL, list | detail); Ctrl+B hides the list ---->|
+------+----------------------------+--------------------------------------------+
| SOUTH  StatusBar: "5 results"                         "Database build ..."     |
+--------------------------------------------------------------------------------+
CENTER = JPanel(BorderLayout): WEST rail, CENTER split
```

A headless render of the real `buildContent`, with FlatLaf dark and light installed, confirmed the two-line rows, the muted subtitles, the ellipsis on long names, the gold selected rail icon and the 300 px list. I rendered it on the EDT with a scratch program, which is not committed.

## TDD evidence

1. I wrote the tests first: `MasterListTest`, `RailTest`, `ManualDelay`, `UiStateTest`, `MainFrameContentTest`, `SubtitlesTest`, the new `NavigationHistoryTest` cases, the rewritten `AppControllerTest` and `StubView`, and the updates to `ShippedDataTest` and `MainFrameTest`. I also deleted `ResultsPanelTest`.
2. `./mvnw -B -q -pl app -am test` then failed to compile with about 30 "cannot find symbol" errors for `MasterList`, `ListEntry`, `RailTarget`, `replaceCurrent`, `UiState` and others.
3. I implemented the code. The first compile failed because `MainFrame.list()` and `MasterList.list()` clash with `java.awt.Component.list()` (void). I renamed them to `MainView.masterList()` and `MasterList.jlist()`. After that, all 189 app tests passed.
4. The headless render showed blank rows and a horizontal scrollbar. I added `getScrollableTracksViewportWidth() = true`, the fixed cell width, horizontal scrollbar NEVER and `EntryRenderer.validate()`. I also added layout assertions to `rendererShowsTitleAndSubtitle`. All tests stayed green.
5. Final runs:
   - `./mvnw -B -q -pl app -am test`: 189 tests, 0 failures, 0 errors.
   - `./mvnw -B -q verify`: exit 0.
   - The shaded `wow-forever-browser.jar` contains `FlatSVGIcon`, jsvg and the six SVGs, and `java -jar … --self-test` prints the row counts.

Key tests:
- **`MasterListTest.holdingArrowCoalescesPreviews`**: 50 `selectIndex` calls, then nothing is previewed. After `fire()`, exactly one preview arrives, for row 49. `ManualDelay` accumulates scheduled runs and only `cancel()` clears them, so a list that forgot to cancel would preview 50 times.
- **`enterCommitsAtOnceAndDropsThePendingPreview`**, **`doubleClickCommitsTheClickedRow`**, **`replacingTheEntriesDropsThePendingPreview`**, **`headingShowsTheCount`**, **`rendererShowsTitleAndSubtitle`**.
- **`RailTest`**: six buttons in order with tooltips, accessible names and `FlatSVGIcon`s where `hasFound()` is true and `getImage()` is not null headless. Clicking fires the target, `select` does not fire, the width is `UIScale.scale(48)`, and an unknown icon throws.
- **`NavigationHistoryTest.replaceCurrentDoesNotGrowHistory`**, plus tests that forward entries survive and that replacing on an empty history pushes.
- **`AppControllerTest.previewReplacesCurrentWithoutPushing`**: three previews take one slot after the list. Back goes to the list, then to Home.
- **`AppControllerTest.commitPushesAndKeepsTheCommittedPage`**, **`commitBeforeThePreviewFiresOpensAtOnce`**, **`previewAfterBackDoesNotOverwriteTheEntryGoneBackTo`**, **`stalePreviewIsDropped`**, **`linkedZoneShowsDetailAndKeepsTheList`**, **`allItemsWhenClassIsMinusOne`**, **`railTargets`**, **`npcListIsAZonePickerWhoseCommitListsTheZonesNpcs`**, **`questListIsAZonePicker`**.
- **`MainFrameContentTest`**: the content builds headless with toolbar, rail, list, detail and status in place. The divider starts at the saved value, and moving it saves. Ctrl+B is in the input map; it hides the list (divider size 0, saved width untouched), and pressing it again restores the size and location.
- **`UiStateTest`**: the default is 300 logical px, the value round-trips, and values ≤ 0 are ignored. It uses a throw-away `Preferences` node that is removed afterwards.

## Files

Added:
- `app/src/main/java/dev/wowforever/app/ui/widgets/{MasterList,ListEntry,Rail,RailTarget,Icons}.java`
- `app/src/main/java/dev/wowforever/app/ui/{UiState,Subtitles}.java`
- `app/src/main/resources/dev/wowforever/app/ui/icons/{home,items,npcs,quests,zones,dungeons}.svg`
- `app/src/test/java/dev/wowforever/app/ui/widgets/{ManualDelay,MasterListTest,RailTest}.java`
- `app/src/test/java/dev/wowforever/app/ui/{MainFrameContentTest,UiStateTest,SubtitlesTest}.java`

Modified:
- `app/pom.xml`
- `Main.java`
- `nav/NavigationHistory.java`, `nav/ViewTarget.java`
- `ui/AppController.java`, `ui/MainFrame.java`, `ui/MainView.java`, `ui/TableLinks.java` (doc only)
- Tests: `ShippedDataTest`, `NavigationHistoryTest`, `AppControllerTest`, `MainFrameTest` (the content test moved to `MainFrameContentTest`), `StubView`

Deleted:
- `ui/ResultsPanel.java`
- `ui/ResultsPanelTest.java`

## Concerns

1. **The first preview pushes instead of replacing.** The lead's decision 3 says previews call `replaceCurrent`. Taken literally, the first preview would overwrite the list target, so Search or Items would vanish from history. A commit would then be a no-op push, because the entity would already be current, and the next preview would overwrite the committed page. I used a preview-slot flag instead: the first preview after any navigation pushes, and later previews `replaceCurrent`. A commit takes the preview slot and pins it, and `open`, `back` and `forward` end the slot. Twenty rows arrowed through still cost one history entry, and Back returns to the list. `previewReplacesCurrentWithoutPushing` pins this behaviour. Going literal is a two-line change in `AppController.preview` if the lead prefers it.
2. **Renamed accessors.** `MainView.list()` became `masterList()`, and the JList getter is `jlist()`, because `java.awt.Component.list()` exists with a void return and cannot be overridden in `MainFrame` or `MasterList`.
3. **Commit is Enter or double-click, not single click.** I followed decision 2. Review §P1.2 says "Enter or click commits". A single click selects and previews after 120 ms.
4. **"12 px smaller" subtitle.** I read this as slightly smaller and used list font −1 pt. Change one constant in `EntryRenderer` if a fixed 12 px size was meant.
5. **`ListEntry` is a top-level record** in `ui.widgets`, not nested in `MasterList`, for shorter imports in later tasks. Quality and icon are carried but not drawn yet (Part 2).
6. **The rail selection is not synced to navigation.** Back, forward and search do not move the highlighted rail button. `Rail.select(...)` exists for Task 8 to wire.
7. **"All items" lists every item.** That is 21,543 rows, uncapped, as the lead decided. It loads off the EDT, and the fixed cell size keeps the JList fast.
8. **Side effects outside the worktree's files.**
   - Running the shaded jar's `--self-test` used the normal user data directory, which is the existing install path.
   - The scratch render program switched the theme preference and restored it.
   - `UiStateTest` and `MainFrameContentTest` create and remove `dev/wowforever/test/ui-state-*` preference nodes.
   - Nothing under `H:\claude_java` outside the worktree was edited.

## Fix round 1 (commit c8953a8 `fix(ui): single-click commits in the master list; preview slot guard`)

1. **(Important) A single click commits.**
   - `MasterList.java:144`: the mouse handler commits on a left click with `getClickCount() == 1`, on a real row only (`rowAt` checks the cell bounds). `commitSelected()` cancels the pending preview that the press scheduled.
   - The extra clicks of a double-click (count 2 or more) are ignored. A later separate click on the same row reaches the controller, which treats it as a no-op (item 3).
   - Javadoc updated: class doc at `MasterList.java:38-42` and `onCommit` at `MasterList.java:166-169`. `AppController.java:40-42` says "Enter, a click", and the welcome text at `AppController.java:374` says "Enter or a click opens it".
   - `doubleClickCommitsTheClickedRow` is replaced by `singleClickCommits`, `theSecondClickOfADoubleClickIsIgnored` and `clickBelowTheLastRowCommitsNothing`. `singleClickCommits` covers commit, a dropped preview and no later preview.
   - `AppControllerTest.clickingTheSameRowTwiceKeepsOneHistoryEntryAndOneLoad` checks that a second commit of the same row adds no history entry and no load: Back goes straight to the list.
2. **(Minor) The preview slot is only taken when a new entry is pushed.**
   - `AppController.java:123-127`: `preview` returns early when the entity is already the current page (`isCurrent`, `AppController.java:161-163`). `push` would have dropped it as a duplicate, so `previewSlot` stays false and nothing reloads.
   - Test: `previewingThePageOpenedByALinkKeepsItsSlot`. The page is opened on purpose, the same row is previewed (no load), and the next row is previewed. That preview pushes, so Back returns to the page opened on purpose and then to the list.
3. **(Minor) Committing the row on screen does not reload it.**
   - `AppController.java:146-150`: `commit` of the current page only pins it (`previewSlot = false`) and skips `show`. This covers the previewed row and a repeated click.
   - Test: `committingThePreviewedRowDoesNotLoadItAgain`. Preview, then commit of the same row: the worker load count is unchanged, the same detail component instance stays, and the next preview takes a new slot.

TDD: the new tests ran against the old code first.

```
./mvnw -B -pl app -am test -Dtest=MasterListTest,AppControllerTest -Dsurefire.failIfNoSpecifiedTests=false
Tests run: 39, Failures: 4  (singleClickCommits, committingThePreviewedRowDoesNotLoadItAgain,
  clickingTheSameRowTwiceKeepsOneHistoryEntryAndOneLoad, previewingThePageOpenedByALinkKeepsItsSlot)
```

After the fix:

```
same command: AppControllerTest 29/29, MasterListTest 10/10, BUILD SUCCESS
./mvnw -B -q -pl app -am test: exit 0; surefire total 194 tests, 0 failures, 0 errors, 0 skipped
```

Concern 3 of the original report (double-click instead of single click) is resolved.
