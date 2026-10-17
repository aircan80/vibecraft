# Phase 2B final-review fix wave 2: report

Branch `feature/phase2b-ui`, worktree `H:\claude_java\.worktrees\phase2b-ui`, on top of `fca1220`.

Status: all 12 items done (item 12 came in a second message). `./mvnw -B -q verify` exits 0. Totals from the surefire reports:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 336 | 0 | 0 | 0 |
| app | 733 | 0 | 0 | 0 |

## Commits

| Commit | Items | Subject |
|---|---|---|
| 16c7f28 | 1 | fix(ui): mouse back/forward buttons work in the compact layout |
| 995e069 | 2, 3 (code) | fix(ui): restored windows fit the screen they reopen on; drop unused P1.7 state |
| 6e8939e | 5, 7 | fix(ui): zone map says when quest givers were cut; reagents are world drops |
| 74fb219 | 8, 9 | refactor(ui): test-only page helpers leave main; rerender Javadoc tells its queries |
| 3f2aaab | 10 | test: keep test preferences in memory, never in the user's registry |
| 014016e | 3, 4, 6 (docs), 11 | docs: roadmap marks v0.2.0 work done and logs deferred UI items; NOTICE credits libraries |
| ab2afd7 | 12 | fix(ui): a map above a section table never squeezes the table |

Every commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Per item

1. **Mouse buttons 4/5 in compact mode.** `MainFrame.java:210` now installs on `getRootPane()`.
   `MouseNavigation.install` (`MouseNavigation.java:38`) takes a `JRootPane`, so the content panel cannot be
   passed again. `sameWindow` compares the top of each tree: the first window going up, else the outermost
   container. This keeps the old answer for real windows and makes the positive case testable headless.
   Tests: `MouseNavigationTest.aSourceUnderTheRootPaneIsInTheAppWindow`,
   `theRootPaneStillCountsAfterTheContentPaneIsSwapped`, `aSourceInAnotherTreeIsNotInTheAppWindow`.
   `LayoutSwitchTest.mouseButtonsWorkInBothLayoutsAndWhenTheAppStartsCompact` drives the real
   `LayoutSwitch` (its fake window now sets a real `JRootPane`'s content pane): start compact, toggle twice,
   button 4 goes back each time, and the detached full layout is shown not to be an anchor.
2. **Bounds clamped to the screen they reopen on.** New `UiState.onScreen` (`UiState.java:123`) picks the
   screen the 40 px title strip overlaps most (first on a tie), caps width and height to it, then moves the
   window the least that puts it inside. `windowBounds` and `compactWindowBounds` use it. When the title bar is
   on no screen, the existing defaults still apply (centred on the default screen, or the compact window at the
   top right of the current screen). Tests in `UiStateTest`: 2560x1400 saved on a 1366x768 screen gives
   0,0,1366,768; a window across two screens goes to the one with more of its title bar; a screen left of and
   above the main one; a compact window clamped; the partly visible title bar test now expects the window
   moved fully on screen (920,380). `LayoutSwitchTest.windowsSavedOnABigScreenReopenWholeOnASmallOne` goes
   through `LayoutSwitch.boundsFor`, the seam the frame's window adapter feeds with `screens()`.
3. **Dead P1.7 persistence removed.** From `UiState`: `columnWidths`/`setColumnWidths`, `sort`/`setSort` and
   the `Sort` record, `lastSearchKind`/`setLastSearchKind`, `screenDeviceId`/`setScreenDeviceId`, their keys,
   `TABLE_KIND_MAX_LENGTH` and `checkKind`. `WindowStatePersistence.Snapshot` lost its screen id and
   `saveNow` no longer stores it. Pinned pages stay. The matching assertions left `UiStateTest`,
   `WindowStatePersistenceTest` and `LayoutSwitchTest`. ROADMAP D18 records the remainder.
4. **Plan gaps.** ROADMAP D19 (Home launch pad, spec 5.1) and D20 (not-found and unknown-entity states,
   review P1.8).
5. **Reagent is a world-pool class.** `ItemCategories.java:38`. Tests:
   `ItemCategoriesTest.theWorldPoolIsTheGenericDropClassesIncludingReagents`, and
   `NpcPageTest.onlyTheGenericPoolFoldsIntoWorldDropsNeverQuestItemsGearOrBags` gains a Reagent drop
   (World drops (5)).
6. **ROADMAP done markers.** A1, A2, A3, B7 to B10 and suggested-order steps 1 and 2 carry
   "*Done (v0.2.0).*" after their title; text kept, with an intro sentence explaining the marker. A1 notes
   that slot filters wait for D16.
7. **Quest-pin cap note.** New record `model/QuestPins` (pins plus a `capped` flag).
   `ZoneRepository.questGiversInZone(long)` (`ZoneRepository.java:180`) delegates to a package-private
   `questGiversInZone(long, int cap)` (line 188) that asks for `cap + 1` rows and drops the extra one.
   `ZoneMapPanel.PinSource.questPins` returns `QuestPins`. The panel keeps the flag and `showPins`
   (`ZoneMapPanel.java:302`) uses it for the Quests toggle; the load moved into `load(...)` (line 279), in
   the old order (NPCs, objects, quests). Tests: `ZoneRepositoryTest.questGiversCutAtTheCapSaySo` (cap 6 is
   exact and not cut, cap 2 is cut), `ZonePageTest.questGiversCutAtTheCapSaySoWhileQuestsShow` (three pins,
   capped: note shows, hides with the toggle, shows again, one load) and `questGiversNotCutShowNoNote`.
8. **Test-only code out of main.** `LinkTable.addTab` and `selectFirstEnabled` deleted with their only test
   (`LinkTableTest.tabsScrollInsteadOfWrapping`, which tested nothing else). `DetailPanels.build` and the 3-,
   5- and 6-argument `render` overloads moved to the new test helper `app/src/test/java/dev/wowforever/app/ui/detail/TestPages.java`;
   the callers in `ShippedDataTest`, `AccessibilityTest`, `DetailPanelsTest` and `QuestPageTest` use it.
   `DetailPanels` keeps the one 7-argument `render`, whose Javadoc now covers all its parameters.
9. **rerender Javadoc.** `AppController.java:213-222` now says the entity is not loaded again, but building
   the page runs its own queries: an instance page reads its first boss's loot (and the restored encounter's
   when another was selected), and a zone map with Quests on loads its quest givers again on the worker.
10. **Tests never touch real Preferences.** `app/pom.xml` configures surefire's `systemPropertyVariables`
    with `java.util.prefs.PreferencesFactory=dev.wowforever.app.InMemoryPreferencesFactory` (new test class,
    an `AbstractPreferences` over maps). This covers `ThemeManagerTest`'s static node and every UiState test.
    `PreferencesIsolationTest` fails the build if the factory is not active and checks the node behaviour.
    Chosen over a temporary node because `ThemeManager` reads a fixed static node.
11. **NOTICE.** There was no FlatLaf attribution to sit next to, so a new "Third-party libraries" section
    lists every library in the shaded jar: FlatLaf and FlatLaf Extras (Apache-2.0), JSVG (MIT, through
    FlatLaf Extras), SQLite JDBC (Apache-2.0) and SLF4J API and Simple (MIT). Licences checked against the
    POMs in the local Maven repository (JSVG 1.4.0 says MIT). `app/src/main/resources/NOTICE.md` is a byte
    copy; `MainFrameTest` parity passes.

12. **Compact Spawns coordinates row clipped.** Reproduced headless on the shipped database: Hogger (448) in a
    compact-keyed panel 420 px wide. Pass 1 lays the page out with a vertical scrollbar (the map was first
    measured at its image width), so the map is 379 px wide. Pass 2 has no scrollbar, but the Spawns section is
    measured with the map still at 379 px. The map is then laid out at 396 px and takes its taller height in the
    BorderLayout's NORTH, so the table in CENTER drops from 16 to 5 px. The map's height then matched its width,
    so its resize listener never asked for another layout and the squeeze stayed. The reviewer's suspect,
    height derived from the current width, is the root. The trigger is the scrollbar changing the width between
    measuring and laying out.
    Fix:
    - `SourceSection.addAboveTable` (`SourceSection.java:61`) uses a new `AboveTable` layout (line 77). The
      table always gets its preferred height, and the lead (the map) takes the rest, so the map shrinks first.
    - `MapView.needsLayout` (`MapView.java:320`) replaces the old "height differs from heightFor(width)"
      check. A map shorter than its width wants always asks for a new layout. A taller one asks too, except
      right after a shorter one asked. A map is taller on a wider page, so near the scrollbar threshold the
      page can have no stable layout. Asking every time would flip the scrollbar forever. With this rule the map
      keeps a few spare pixels under its image instead.
    - This also covers the quest page's maps (same `addAboveTable`). The zone map keeps its own layout, but its
      relayout rule changes too and gets the same protection against looping.
    Tests:
    - `ShippedDataTest.theCompactSpawnTableKeepsItsWholeRowUnderTheMap` covers three layout passes at 420 px.
      The table keeps at least one row height and the table keeps its preferred height in every pass. The map
      width changes between passes, proving the scenario ran, and the row is "25.0, 92.9". It fails against
      the old SourceSection.
    - `SourceSectionTest.aLeadAboveTheTableGivesUpHeightBeforeTheTableDoes` covers the layout rule.
    - `MapViewTest.theMapAsksForALayoutWhenItsHeightNoLongerFollowsItsWidthWithoutBouncingAScrollbar` covers
      the relayout sequence.
    - `NpcPageTest.spawnsOnMap` now checks the map-above-table order instead of the old BorderLayout NORTH
      constraint.

## Left as ruled (not done)

- Fonts frozen at construction until navigation.
- History unbounded.
- Ctrl-only shortcuts on macOS.
- Tab inside inline tables.
- The 580 px shortcuts dialog width.
- `SearchRepository` importing `ui.Labels`.
- Tooltip cap per giver.
- File sizes.
- Repeated `@TempDir` setup.

## Notes and concerns

- Item 11 widens the ask slightly: the NOTICE lists SQLite JDBC and SLF4J too, since a libraries section that
  skipped shipped libraries would be incomplete. Drop those two lines if only JSVG and FlatLaf were wanted.
- Item 2 changes behaviour on purpose: a window whose title bar only partly shows is now moved fully onto
  the screen instead of being kept where it was.
- Item 10: the in-memory factory is set by surefire only. A test run from an IDE that does not use the Maven
  configuration still uses real Preferences; `PreferencesIsolationTest` fails there, which makes that visible.
- Earlier test runs left `dev/wowforever/test/...` nodes under the user's real Preferences (on Windows,
  `HKCU\Software\JavaSoft\Prefs\dev\wowforever\test`). Nothing here deletes them; they are harmless leftovers
  the owner may remove.
- Item 12: right after the scrollbar flips, the map can briefly show a few pixels cut from its bottom or a few
  spare pixels under it, until the next layout. The table itself is never cut. The compact NPC screenshot may
  change slightly when it is re-shot.
- Docs and screenshots were not touched beyond `docs/ROADMAP.md`.
