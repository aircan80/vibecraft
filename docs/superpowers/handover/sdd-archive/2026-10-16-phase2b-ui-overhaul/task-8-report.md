# Task 8 report: category rail list pages with filter bars (tree removed)

Status: DONE_WITH_CONCERNS (concerns are minor; see the end).
Commit: `b7fc4b3 feat(ui): filterable list pages from the category rail` on `feature/phase2b-ui`.

## What was implemented

- **Filters as values (`model/`).** `sealed interface ListFilter permits ItemFilter, NpcFilter, QuestFilter, NoFilter`, each a record in its own file:
  - `ItemFilter(Integer classId, Integer subclassId, Integer minLevel, Integer maxLevel, Integer quality)`. A subclass without a class is rejected. `quality` stays null.
  - `NpcFilter(Long zoneId, Integer rank, Integer minLevel, Integer maxLevel)`.
  - `QuestFilter(Long zoneId, Integer minLevel, Integer maxLevel)`.
  - `NoFilter` for zones and instances.
  - Each has an `ALL` (or `INSTANCE`) constant and small `with…` copiers. `ListFilter.defaultFor(kind)` gives the "everything" filter. `fits(kind)` checks that a filter belongs to a kind.
- **`ViewTarget.List(EntityKind kind, ListFilter filter)`.** The constructor rejects a filter that does not fit the kind. `new List(kind)` uses the default filter. `ViewTarget.ItemClass` is deleted. Nothing in main code needed it once `BrowseTree` was gone. The thin compatibility path is `ViewTarget.List.itemClass(classId, subclassId)`, built on `ItemFilter.ofClass`. It maps v0.1's `-1` "all classes" and `-1` "whole class" conventions, and it opens the item list page with the class preselected.
- **Repository queries (`db/`).** All are parameterised and built on dynamic `WHERE` clauses. A null filter part adds no constraint.
  - `ItemRepository.list(ItemFilter)`: class, subclass (including `OTHER_SUBCLASSES`), and `item_level` bounds. It shares one private query builder with the old `list(classId, subclassId, limit, offset)`, which keeps its behaviour. A non-null `quality` throws `IllegalArgumentException` because the item table has no quality column yet. I chose this over silently ignoring it.
  - `NpcRepository.list(NpcFilter)`: zone, rank, and level-range overlap. The real columns are `min_level`/`max_level`, not `level_min`/`level_max`. An NPC with one known level counts it as both ends.
  - `QuestRepository.list(QuestFilter)`: `zone_or_sort` and `quest_level` bounds.
  - In all three, rows without a level drop out once any level bound is set. Results are ordered by name.
- **Limit semantics.** v0.1 capped only search (200 rows, "(first 200)" marker). Item class lists and zone lists were uncapped, and nothing had a "more" affordance. List pages are therefore uncapped. The shipped database has 21,543 items, 13,392 NPCs and 5,232 quests. The inline `JTable` paints only the visible rows.
- **Page (`ui/pages/`).**
  - `ListPage` extends `SourcesPage`. It shows a `HeaderPanel` titled as the rail names it. Below that comes a `FilterBar` for items, NPCs and quests. Then a single sortable `LinkTable.inline` on `ResultsTableModel` sits under a "Results (n)" `SourceSection`, with a muted "Nothing matches these filters." note when empty. Everything is inside the page's scroll.
  - Single-click on a name opens the entity through the existing `TableLinks` behaviour. `showRows(target, rows)` reloads the table in place and keeps the sort keys. `SourceSection` gained `setCount(int)` for the heading.
  - `FilterBar` provides combos for class › subclass (subclass disabled under "All classes"), zone and rank, each starting with "All …".
  - The bar also has two `JSpinner`s, 0 to 999, where 0 shows "Any" and means no bound. A small formatter parses typed text such as "12", "any" or blank.
  - Only user changes are reported, once each, and only when the filter actually changed. Programmatic setup and subclass refills are suppressed. Label and field pairs wrap together, and the bar reports its wrapped height so a narrow page clips nothing.
  - The rank order offered is Normal, Elite, Rare, Rare Elite, Boss.
- **Smaller page helpers.**
  - `ListRows.load(target, repos)` returns the rows of a target and is called off the EDT.
  - `ListOptions` holds the class tree and the zones sorted by name, plus `EMPTY`. The controller caches it after the first load.
  - `LevelBands` holds the instance banding.
- **Instance list.** Instances are grouped by `level_min` into the bands 1–20, 21–40, 41–59 and 60 or more, followed by "Level unknown" for null or values below 1. Empty bands are left out. Each band is a separate inline table under its own `SourceSection`, not divider rows. That keeps every band sortable, which `DividerRows` cannot do.
- **Column catalogue tweaks.** Instance names now go through `Labels.instanceTitle`, so same-named wings read "Blackrock Spire › Lower". This keeps the v0.1 list behaviour and matches the zone page. `Columns.zones()` gained a "Parent" link column, so the zones list shows name, kind and parent, sorted by name.
- **`AppController`.**
  - Rail targets map to `ViewTarget.List(kind)` with the default filter. A `List` target loads options and rows on the worker, with the generation guard on `detailGeneration`, and shows a `ListPage` in the detail pane. The master list is no longer touched by rail targets.
  - A filter change goes through `refilter`. It is ignored when the page is no longer shown. Otherwise it replaces the list's history entry (`replaceCurrent`) and reloads off the EDT, and stale reloads are dropped. If a search was opened while the list stayed on screen, the filtered list is pushed after the search instead of overwriting it.
  - Back and Forward rebuild the page from the stored target, so the filters come back preselected.
  - Removed: the zone-picker flow, `openZoneList`/`zoneList`, item-class listings and their master-list entry helpers.
- **`BrowseTree` and `BrowseTreeTest` deleted.** `MainFrame` and `MainFrameTest` needed no change. `BrowseTree` was already unreferenced by `MainFrame` before this task, so the brief's premise that `MainFrame` used it did not hold.

## Tests and results

- `./mvnw -B -q -pl app -am test`: green, with 381 app tests, 0 failures and 0 errors.
- `./mvnw -B -q verify`: green. The app module had 381 tests, importer 141, and lua-table 61, with 0 failures, 0 errors and 0 skips. `ShippedDataTest` ran against the shipped database.
- New and updated tests:
  - **`ListFilterTest`**: default filters, value equality of list targets, filter/kind mismatch rejected, subclass needs class, `ofClass` compatibility mapping, withers.
  - **`ItemRepositoryTest`**: `listWithLevelRange`, filter list equal to the class list, unknown levels kept without bounds, and quality refused.
  - **`NpcRepositoryTest`**: `npcsByRank`, level-range overlap, and the unfiltered list by name equal to `byZone` for a zone filter.
  - **`QuestRepositoryTest`**: `questListByZoneAndLevel`.
  - **`ListPageTest`**: 14 tests.
    - Filters produce the expected query (`ListRows` against the repositories).
    - The bar shows its initial filter without reporting it.
    - Class and subclass combos.
    - Level spinners show "Any".
    - Typed level text parses.
    - Wrapping on a narrow page.
    - A filter change reloads the table, count and empty note.
    - Results are sortable.
    - A single click opens the entity.
    - NPC and quest bars.
    - Zones by name with kind and parent link.
    - Instance level bands, on the fixture and on synthetic edges.
  - **`AppControllerTest`**:
    - Rail targets map to default-filter lists.
    - Each rail target opens a `ListPage` with the default filter and leaves the master list's search alone.
    - Item list counts.
    - The item-class link preselects the class.
    - A filter change reloads in place and replaces history, so Back skips filter steps.
    - Back after opening an entity from a filtered list restores the same filter, preselected, with the same rows.
    - A filter change after a search pushes after it.
    - Stale filter reloads and stale list pages are dropped.
    - A filter change from a page no longer shown is ignored.
    - Instance wing names.
  - **Preview and commit tests moved.** They used item-class lists in the master list and now use a search list (`"c"` with the item kind, three or more rows). The zone-picker tests became a zone-commit test.
  - **`ShippedDataTest.itemClassListsAreNotCapped`**: now checks the list page's row count and its "Results (n)" heading.

## TDD evidence

- **First red.** All tests were written before the code. The first run failed to compile with "cannot find symbol" for `ItemFilter`, `NpcFilter`, `QuestFilter`, `list(…Filter)`, `ListPage` and `ViewTarget.List.itemClass`.
- **First green.** After implementation, one test failed: the zones assertion picked a zone with no content. It now uses The Deadmines with parent Westfall. Then everything passed.
- **Second cycle.** `theFilterBarWrapsOnANarrowPage` failed on its height assertion, then passed after the wrap was implemented. `aFilterChangeAfterASearchPushesTheFilteredListAfterIt` passed on its first run because that path was already written; it was added as coverage.

## Files

- **Added (main):** `model/ListFilter.java`, `model/ItemFilter.java`, `model/NpcFilter.java`, `model/QuestFilter.java`, `model/NoFilter.java`, `ui/pages/ListPage.java`, `ui/pages/FilterBar.java`, `ui/pages/ListRows.java`, `ui/pages/ListOptions.java`, `ui/pages/LevelBands.java`.
- **Modified (main):** `db/ItemRepository.java`, `db/NpcRepository.java`, `db/QuestRepository.java`, `nav/ViewTarget.java`, `ui/AppController.java`, `ui/Columns.java`, `ui/widgets/SourceSection.java`.
- **Deleted:** `ui/BrowseTree.java`, `test/.../ui/BrowseTreeTest.java`.
- **Tests added:** `model/ListFilterTest.java`, `ui/pages/ListPageTest.java`.
- **Tests modified:** `ui/AppControllerTest.java`, `db/ItemRepositoryTest.java`, `db/NpcRepositoryTest.java`, `db/QuestRepositoryTest.java`, `ShippedDataTest.java`.

## Self-review

- **Empty results.** "Results (0)" is shown with the empty note. The instance page with no rows shows only the note.
- **Null levels.** They are excluded only when a bound is set, and this is documented on each filter record and query. NPCs with one known level are handled by `coalesce`. Instances with a null or 0 `level_min` go to "Level unknown".
- **Filter persistence through Back.** It is tested from an entity back to the list, with the combo preselected and the same rows. Forward returns to the entity.
- **Min above max.** This is not clamped. The query then returns no rows and the empty note shows.
- **Line endings.** Some early edits produced CRLF. They were converted back to LF before the commit, in line with the repo's `eol=lf` rule.
- **Not done.** The real window was not launched to eyeball the layout, because tests are headless by rule. The page reuses the existing `SourcesPage`, `SourceSection` and `HeaderPanel` look.

## Concerns

1. **`FilterBar.java` is 324 lines.** It covers three bar shapes, the "Any" spinner formatter and the wrap-height calculation. It is still one responsibility, but larger than the other page helpers. A split into a spinner formatter class and a wrap-panel helper is easy if wanted. I did not split it on my own.
2. **`ItemFilter.quality` is refused, not ignored.** It throws in `ItemRepository.list(ItemFilter)`. When 2A lands, that check becomes the quality `WHERE` clause.
3. **Uncapped list pages.** There is no "more" affordance, matching v0.1, which had none. The unfiltered NPC page loads about 13k rows, and the item page about 21.5k rows plus source-count subqueries, as v0.1's "All items" already did. If that feels slow on the real database, a page cap is the follow-up.
4. **Test files touched beyond the list.** Several v0.1 controller tests asserted the removed behaviour: master-list item-class lists and the zone picker. These were rewritten to the new behaviour or moved to search lists, not deleted. Their intent (preview slots, commit, stale drops) is kept.

## Fix round 1

Commit: `8dcbd02 fix(ui): rail follows the page; debounced level filters and range hint`.

1. **Rail highlight sync (important).**
   - `MainView` gained `selectRail(RailTarget)`. `MainFrame` forwards it to `Rail.select`, which uses `setSelected`, so no action fires and the rail never opens anything on its own.
   - `AppController.railSection(ViewTarget)` maps Home to HOME and each list kind to its rail target. Entities and searches map to null, which clears the selection.
   - `show()` calls it on every navigation: open, preview, commit, Back and Forward. Filter changes keep the same kind.
   - Tests: `theRailFollowsTheTargetOnScreen` covers start (Home), opening a list, a filter change, opening an entity from the list (none), Back (restored), a search (none), Back again (restored), and Back to Home. `railSectionsOfTargets` checks the mapping and that every rail target round-trips through it.
2. **Level spinner debounce.**
   - Spinner changes are reported through a `MasterList.Delay`, defaulting to `timerDelay(FilterBar.LEVEL_DELAY_MILLIS = 250)`. Each tick cancels and reschedules, so a held arrow gives one reload.
   - Combos stay immediate. A combo change also drops any pending level report, because it carries the current levels itself.
   - `ListPage` has a second constructor that takes the delay as a seam. Tests use `ManualDelay`.
   - Test: `levelTicksAreDebouncedButCombosAreNot`. The existing spinner tests now fire the delay.
3. **Quest levels at or below zero are unknown.** Once any level bound is set, `QuestRepository.list` adds `quest_level > 0`. The `QuestFilter` doc says so. Test: `questLevelsOfZeroAndBelowAreUnknown` inserts quests with levels -1 and 0 into a temp copy of the fixture. They are listed without bounds and excluded by a minimum or a maximum.
4. **Empty note by kind.** Zone and instance pages say "No entries.". Filtered pages keep "Nothing matches these filters.". Test: `theEmptyNoteSpeaksOfFiltersOnlyWhereThereAreSome`.
5. **Min above max hint.** A muted "Minimum is above maximum" label sits in the bar. It is shown at once on spinner change, before the debounce, and also when the page opens with such a filter. The wrap-height calculation now skips hidden widgets. Test: `aMinimumAboveTheMaximumShowsAHint`.

**TDD.** The new tests failed to compile first, because `selectRail`, `railSection`, `rangeHint` and the delay constructor were missing. They pass after the fix.

**Results.** `./mvnw -B -q -pl app -am test` is green. `./mvnw -B -q verify` is green, with app 387, importer 141 and lua-table 61 tests, 0 failures and 0 skips.

**Concern.** `FilterBar.java` grew to about 370 lines with the debounce and hint. It is still one responsibility, but it is the largest page helper. The spinner formatter and the wrap-height logic remain the obvious split points.
