# Task 7 report: type-ahead, kind chips, recent searches, paste parsing

Status: DONE_WITH_CONCERNS (concerns below are about what headless tests cannot show).
Commit: `1e21929 feat(ui): type-ahead search with kind chips, recents and paste parsing` on `feature/phase2b-ui`.

## What was implemented

- **`ui/PasteParser`** turns pasted text into a `ViewTarget`.
  - In-game links (`|cffa335ee|Hitem:19019:…|h[Name]|h|r`, quest links too) open the entity.
  - A link of another type (a spell link, say) searches for its bracketed name.
  - `item:ID` / `npc:ID` / `quest:` / `object:` / `zone:` strings open the entity, and so do full item strings.
  - Wowhead URLs (`…/item=ID`, `npc=`, `quest=`, `zone=`, `object=`, path or query form, any `*.wowhead.com` host) open the entity.
  - `[Name]` searches for the name with its apostrophe kept.
  - A bare number (optionally `#`, up to 10 digits, above 0) opens that item.
  - Anything else is empty, including null and blank text, `0`, non-Wowhead URLs, and text around a bracketed name.
- **`UiState.recentSearches()` / `addRecentSearch(String)`** keep the last 10, most recent first.
  - Entries are stripped, line breaks become spaces, and blank or null input is ignored.
  - De-duplication ignores case, and the newest spelling wins.
  - Each entry is capped at 200 chars so the newline-joined preference value stays far below the 8 KB limit.
- **`SearchRepository.countsByKind(text)`** uses one `GROUP BY kind` with the same MATCH, null-name and `Zone #` rules as `search`.
  - Items, NPCs, objects and quests count distinct names, as `collapse` folds them.
  - Instances and zones count every row.
  - So each count equals the size of `search(text, Optional.of(kind), ∞)`. Kinds without a hit are absent, and blank or symbol-only text or a failed query gives an empty map.
- **`ui/widgets/KindChips`** is a row of non-focusable pill `JToggleButton`s (`arc: 999`) in a `ButtonGroup`: All, Items, NPCs, Objects, Quests, Zones, Dungeons & Raids.
  - `setCounts` renders "Items 9" (`%,d`), with All as the sum. `clearCounts` goes back to bare labels.
  - Accessible names read "Items, 9 results".
  - `onSelect` fires only on a user change. `select()` sets a chip silently.
  - `label(EntityKind)` gives the plural names that the dropdown headings reuse.
- **`ui/widgets/SearchDropdown`** owns a non-focusable `JList<Row>`. `Row` is a sealed type: Heading, Hit(ListEntry), Recent, ShowAll(total).
  - It shows the top 8 hits grouped by kind, with groups in the order of their best hit, then "Show all n results" (never fewer than shown). With no hits it shows "No matches".
  - `showRecents` shows a "Recent" heading; with no recents it closes.
  - `install(field, listener)` binds keys in the field's `WHEN_FOCUSED` input map:
    - Up/Down move the selection, skipping headings. Up past the first row clears the selection.
    - Enter picks the selected row. With no selection it opens the top hit, but only if the text has not changed since the rows came (`markStale`); otherwise it runs the full search.
    - Shift+Enter runs the full search.
    - Esc closes the list.
  - Up/Down/Esc actions are disabled while the list is closed, so those keys pass on to the window (Task 9's Esc chain).
  - The mouse selects on hover and picks on click.
  - The `Host` seam puts the list on screen. `windowHost()` uses a non-focusable `JWindow` (type POPUP) under the field: at least 420 px wide and at most 520 px tall. It follows the owner window when that moves or resizes, and re-themes when the look and feel changes. Tests use `FakeDropdownHost` and never create a window.
- **`ui/SearchBar` rewrite** keeps `textField()` and `focus()`. `kindCombo()` and the package-private `fire()` / `debounceRunning()` are gone.
  - The constructor is now `SearchBar(Consumer<ViewTarget> onOpen, UiState state)`, a superset of the old `Consumer<ViewTarget.Search>`. A test constructor takes a `MasterList.Delay` and a `SearchDropdown`.
  - `attach(Source, Executor)` is called from `MainFrame.attach`. `Source.of(SearchRepository)` returns top-8 hits plus counts in one worker call.
  - Typing waits 250 ms through `MasterList.timerDelay`, then looks up on the worker via `Async.run`.
  - A generation counter, bumped by every text change, lookup and commit, drops overtaken results.
  - Results update the chip counts. They show in the dropdown only while the field has focus.
  - The focused empty field shows the recent searches.
  - A chip change re-queries the dropdown at once while it is open. While it is closed, a chip change re-runs the full search at once, as the old combo did.
  - Committed searches use the selected chip kind.
  - Opening a hit or running a search records the text in the recent searches.
  - Picking a recent search sets the text without triggering a lookup and runs the search.
  - The field's `paste()` is overridden. When the paste replaces the whole text (empty field or full selection) and `PasteParser` reads it, an entity opens directly and `[Name]` sets the text and searches. Otherwise the paste is an ordinary one.
- **`AppController`** gains `repos()` and `worker()` accessors. `entry(SearchHit)` becomes package-private so dropdown rows match master-list rows.
- **`MainFrame`** holds one `UiState` that both the split and the search bar use. Ctrl+F is unchanged.

## Tests and results

| Test class | Tests | Result |
|---|---|---|
| PasteParserTest | 25 | pass |
| KindChipsTest | 5 | pass |
| SearchDropdownTest | 16 | pass |
| SearchBarTest (rewritten) | 22 | pass |
| UiStateTest (+4) | 7 | pass |
| SearchRepositoryTest (+3 countsByKind) | 14 | pass |
| `./mvnw -B -q verify` (all modules) | app 342 | exit 0, 0 failures |

Covered edge cases:
- Empty and blank text never searches.
- Garbage paste gives an ordinary paste.
- No hits shows "No matches", and Enter then runs the full search.
- More than 8 hits shows 8 plus "Show all 30".
- Late results are dropped.
- Typing past the shown hits makes Enter run the full search.
- Nothing is looked up before `attach`.
- Disabling the bar disables the chips.

## TDD evidence

RED: the tests were written first. This command failed at test compilation with `cannot find symbol` for `PasteParser`, `KindChips`, `SearchDropdown`, `countsByKind`, `recentSearches`, the new `SearchBar` constructor and `Lookup`.

```
./mvnw -B -q -pl app -am test -Dtest='PasteParserTest,KindChipsTest,SearchDropdownTest,SearchBarTest,UiStateTest,SearchRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false
[ERROR] .../SearchBarTest.java:[15,37] cannot find symbol
[ERROR] .../widgets/SearchDropdownTest.java:[12,52] package dev.wowforever.app.ui.widgets.SearchDropdown does not exist
[ERROR] .../db/SearchRepositoryTest.java:[112,55] cannot find symbol
[ERROR] .../ui/PasteParserTest.java:[31,16] cannot find symbol
...
```

GREEN: the same command, after the implementation, finished with no output (quiet mode, exit 0). The per-class counts are in the table above.

Mutation check: each guard was broken on purpose and the matching test failed, then the sources were restored.
- Dropping the generation check in `SearchBar.lookUp` made `SearchBarTest.lateResultsForOlderTextAreDropped` fail.
- Dropping `!stale` in `SearchDropdown.enter` made `SearchDropdownTest.enterAfterTheTextChangedRunsTheFullSearchUnlessARowWasChosen` fail.
- Changing `count(DISTINCT name)` to `count(*)` made `SearchRepositoryTest.countsByKindFoldsSameNamesExceptPlaces` fail.

## Files changed

Main:
- `app/src/main/java/dev/wowforever/app/ui/PasteParser.java` (new)
- `app/src/main/java/dev/wowforever/app/ui/widgets/KindChips.java` (new)
- `app/src/main/java/dev/wowforever/app/ui/widgets/SearchDropdown.java` (new)
- `app/src/main/java/dev/wowforever/app/ui/SearchBar.java` (rewrite)
- `app/src/main/java/dev/wowforever/app/ui/UiState.java`
- `app/src/main/java/dev/wowforever/app/db/SearchRepository.java`
- `app/src/main/java/dev/wowforever/app/ui/AppController.java`
- `app/src/main/java/dev/wowforever/app/ui/MainFrame.java`

Test:
- `PasteParserTest`, `widgets/KindChipsTest`, `widgets/SearchDropdownTest` (new)
- `SearchBarTest` (rewritten)
- `UiStateTest`, `db/SearchRepositoryTest` (extended)
- Test helpers: `widgets/FakeDropdownHost` and `widgets/Keys`, which presses a key through a component's focused input map headlessly and reports whether it was consumed.

## Self-review

- **Completeness:** every file and test the brief names exists. The two Review Focus strings parse as required.
- **Fixed during review:** the dropdown window re-themed on every show, and its border colour went stale after a theme switch. It now re-themes only when the look and feel changes, and the border is set on each show.
- **Discipline:** tests never create a `JWindow`, `JFrame` or `JDialog`. All DB access goes through `Async` on the injected executor, and model updates happen in the EDT callback.
- **Pristine output:** the new tests print nothing. The only verify output line is the importer's existing `RealFilesTest` line.

## Concerns

1. **`windowHost()` is untested:** the real `JWindow` placement, owner-follow and re-theme code cannot run headlessly. It needs a visual check that the list sits under the field, does not take focus, and follows the window.
2. **Toolbar width:** seven chips with counts plus the 32-column field may overflow the toolbar at high scaling or narrow widths. Compact mode in Task 10 and polish in Task 14 may need to shrink the chips, for example by hiding zero-count chips.
3. **Interpretations made:**
   - A chip click re-runs the full search only while the dropdown is closed. While it is open, it re-filters the dropdown.
   - Enter opens the top hit only while the shown hits are for the current text.
   - Opening a hit records the typed text as a recent search.
   - Paste parsing applies only when the paste replaces the whole field text.
   - A pasted `[Name]` search uses the selected chip kind.
4. **"All" total can be slightly off:** the All count sums per-kind counts, so it can differ by a few from the rows the All search lists. The All search merges a zone with its single instance; the counts do not.

## Fix round 1

Commit: `3707610 fix(ui): dropdown fits its rows; chip counts follow the searched text`.

What changed:
1. **Dropdown height (Important).** `SearchDropdown.preferredHeight()` sums each row's renderer height plus the border insets, capped at 520 logical pixels. `Host.show` now takes that height, and the window host uses it instead of the scroll pane's preferred size. The scroll pane height had come from the default visible row count times the height of the first row, which is always a short heading.
2. **Stale chip counts (Important).** `SearchBar.Source` gains a `counts(text)` method. It defaults to the lookup's counts, and `Source.of` answers it with `countsByKind` alone. Committing a search and opening a hit both request the counts for that text on the worker, under the same generation guard. This covers Enter before the debounce, a pasted name and a picked recent search.
3. **Kind change marks the rows stale.** Changing the kind while the dropdown is open now marks its rows stale, so Enter before the new rows arrive runs the full search with the new kind.
4. **Host reports whether it showed.** `Host.show` returns a boolean, and the dropdown counts as showing only when it is true. The window host hides itself and returns false when the search box is not on screen.
5. **Shared hit-row factory.** The search-hit-to-row factory moved to a new `ui/widgets/ListEntries.of(SearchHit)` helper. The master list search results and the dropdown both use it, and the package-private `AppController.entry(SearchHit)` is gone.

New tests:
- `SearchDropdownTest` checks that the height equals the summed row heights plus the border, both for eight mixed hits and for one hit. It also checks that the height is capped with 60 recents, and that a host that cannot show leaves the dropdown closed, with Esc passed on and Enter running the full search.
- `SearchBarTest` covers the counts after Enter before the debounce, after a pasted name, and after a picked recent search. It also checks that counts overtaken by typing are dropped, and that Enter right after a kind change runs the full search.

Evidence:
- The covering tests passed: `SearchDropdownTest` 19, `SearchBarTest` 27 and `AppControllerTest` 31.
- Mutation check: restoring each old behaviour made all 8 new tests fail, 5 in `SearchBarTest` and 3 in `SearchDropdownTest`. The old behaviours were the scroll pane preferred height, always marking the list as showing, no counts on commit, and no stale mark on a kind change.
- `./mvnw -B -q verify` exited 0 with 350 app tests, 0 failures and 0 errors. Its only output is the importer's existing `RealFilesTest: parsed 41 files, 7 embedded chunks` line.

Still open: the window host itself (placement, following the window, the new height) still needs a visual check, since headless tests cannot create the window.
