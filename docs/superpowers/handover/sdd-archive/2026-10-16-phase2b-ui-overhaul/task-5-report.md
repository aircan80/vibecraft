# Task 5 report: item page as one scrolling sources page

**Status:** DONE_WITH_CONCERNS (all concerns are small deviations or notes, listed at the end)
**Commit:** `673bf02 feat(ui): item page as a scrolling sources page with rate bars` (branch `feature/phase2b-ui`, not pushed)
**Tests:** `./mvnw -B -q -pl app -am test` green; `./mvnw -B -q verify` green (exit 0). App module: 260 tests, 0 failures, 0 errors, 0 skipped.

## What was implemented

- `ui/pages/ItemPage(ItemDetail, Navigator)`: a `JPanel` holding a borderless `JScrollPane` over `PageContent`, a vertical `BoxLayout` that is `Scrollable` (tracks the viewport width so tables fit, tracks height only when shorter than the viewport). Contents in order:
  1. `HeaderPanel` with the same look and subtitle as before ("Item #14149 · Armor · Cloth · iLvl 18 · Requires 13"), plus the "Starts quest: …" link. Built by a nested `ItemHeader` helper; uses `Labels.join` because `HeaderPanel.join` is package-private.
  2. A muted summary line from `ItemSummaryLine.of(d)`, for example "1 NPC · 1 loot list · no vendor · no quests" or "3 NPCs · 2 loot lists · 2 containers · 1 vendor · 2 quests". NPCs, loot lists and containers (objects + container items) appear only when non-zero. Vendors and quests always appear, as "no vendor" and "no quests" when zero. Quests count distinct quest refs across reward and related lists. Singular and plural forms are handled.
  3. One `SourceSection` per non-empty kind, in this order: Dropped by, In instance loot, Contained in, Sold by, Quest reward, Related quests. Empty kinds are not added.
  - Accessors: `sections()`, `section(String title)` (null when absent), `droppedBy()` (null when absent), `header()`, `summary()`, `scrollPane()`, `jumpTo(int)`.
  - Ctrl+1 to Ctrl+6 are bound for the sections that exist, in the page's `WHEN_IN_FOCUSED_WINDOW` input map, so they work wherever focus is in the window. `jumpTo` scrolls the section heading to the top of the viewport, selects the first selectable row if nothing is selected, and calls `requestFocusInWindow()` on the table.
- `ui/widgets/SourceSection(String title, int count, LinkTable<?> table)`: a gold heading ("Dropped by (3)", bold, colour looked up per paint from `Theme.current().accentGold()`) over an inline `LinkTable`. Its maximum height is its preferred height, so the box stacks sections tightly. It rejects a `LinkTable` that has its own scroll pane.
- `LinkTable.inline(cols, nav)` and `isInline()`: a new mode with no scroll pane. It shows `getTableHeader()` above the `JTable` in a `BorderLayout`, so its preferred height is the row count times the row height, plus the header. The existing scrolled constructor is unchanged for the other detail panels.
- `ui/widgets/RateBarRenderer`: prints "60.00 %" right-aligned via `Columns.percentText`, then paints a 48 × 10 px track (in `bgRaised`) with a proportional fill in `QualityColors.rateBand(pct)`. A null rate prints a muted "—" with no bar. The bar's space is always reserved so the numbers line up. `install(TableColumn)` sets the renderer and a preferred width of 130 (scaled with `UIScale`).
- `ui/widgets/OriginChip`: shows nothing for `questie` or null, an outlined rounded chip "AtlasLoot" for `atlasloot`, and "Questie + AtlasLoot" for `both`. Unknown origins show as is. The text and outline are muted and slightly smaller than the table font.
- `ui/widgets/DividerRows.install(LinkTable<T>, Predicate<T>)`: wraps every column renderer. A divider row shows its first-column text in muted colour and leaves the other cells blank. A `DefaultListSelectionModel` subclass steps over dividers in the direction of travel, falling back to the other direction. The table's row sorter is removed so the rank order and the divider stay in place.
- `ui/DropRow`, a sealed interface with `Source(DropSource)` and `Divider(int count)`, whose text is "Rate unknown (n)". `DropRow.ranked(List<DropSource>)` puts known rates first, highest first with ties by name (case-insensitive). If any rate is unknown, it then adds the divider and the unknown-rate rows by name.
- `ui/LootListRow(InstanceLoot, String title)`: the title reads "Scarlet Monastery › Armory trash", meaning instance display name, ›, boss or list display name. When two rows would read the same, each gets " · slot N". Rows are sorted by rate descending with unknown last, then title, then slot.
- `Columns`:
  - `dropSourcesRanked()`: Source (NPC link via `displayName()`, divider text on the divider), Level (`Levels` with 0 treated as unknown), Zone (zone link), Rate (`Double`), Origin.
  - `lootLists()`: Loot list (instance link), Boss (NPC link when present, plain otherwise), Rate (`Double`).
  - `sourceNames()`: the name only, used for Sold by, Quest reward and Related quests, where a Kind column would repeat one value.
  - Contained in keeps `itemSources()`, which has the Kind column.
  - Removed `instanceLoot()`, which was used only by the deleted panel.
- `DetailPanels.render` now dispatches `ItemDetail` to `ItemPage`.
- Deleted `ui/detail/ItemDetailPanel` and `ItemDetailPanelTest`. The tests from the old panel that still apply (unknown NPC placeholder, header subtitle, starts-quest link, boss without NPC is plain text, instance link) were ported to `ItemPageTest`.
- `AppControllerTest`: switched the instance checks to `ItemPage`. `clickThroughFromItemToNpcAndBack` now calls `((ItemPage) view.detail).droppedBy().activateRow(0)`. In the fixture, 14149 has one source, Taragaman, at 33.3 %. `DetailPanelsTest` now expects `ItemPage`.

## Page structure as built

```
ItemPage (BorderLayout)
└─ JScrollPane (no border, unit increment 16)
   └─ PageContent (BoxLayout PAGE_AXIS, Scrollable, tracks viewport width)
      ├─ HeaderPanel           title + "Item #… · class · subclass · iLvl · Requires" [+ Starts quest link]
      ├─ JLabel (muted)        "3 NPCs · 2 loot lists · 2 containers · 1 vendor · 2 quests"
      ├─ SourceSection "Dropped by (n)"        LinkTable<DropRow>     Source | Level | Zone | Rate(bar) | Origin(chip)  [divider, unsorted]
      ├─ SourceSection "In instance loot (n)"  LinkTable<LootListRow> Loot list | Boss | Rate(bar)
      ├─ SourceSection "Contained in (n)"      LinkTable<ItemSource>  Name | Kind
      ├─ SourceSection "Sold by (n)"           LinkTable<ItemSource>  Name
      ├─ SourceSection "Quest reward (n)"      LinkTable<ItemSource>  Name
      └─ SourceSection "Related quests (n)"    LinkTable<ItemSource>  Name
```

Each `SourceSection` is a `BorderLayout` holding the gold heading (NORTH) and the inline `LinkTable` (CENTER). The inline `LinkTable` holds `JTableHeader` (NORTH) and `JTable` (CENTER).

## TDD evidence

1. I wrote `RateBarRendererTest`, `OriginChipTest`, `SourceSectionTest` and `ItemPageTest` first. The first run failed to compile because `ItemPage`, `SourceSection`, `RateBarRenderer`, `OriginChip`, `DropRow` and `LinkTable.inline` were missing.
2. After implementing, all 25 new tests passed.
3. I then made the Ctrl+n test stricter: it now requires that a section's y is positive and that the viewport scrolled a positive amount. It failed with the message "[laid out]". The cause is that headless components have no peer, so `validate()` does nothing and the first version passed vacuously. I fixed the test with a recursive top-down `doLayout()`, and it passed.
4. Mutation check: I disabled the `scrollRectToVisible` call in `ItemPage.jumpTo`, and `ItemPageTest` failed. I then restored the file.

Test coverage:
- **`ItemPageTest`** (13 tests):
  - Fixture 14149 shows only "Dropped by (1)" and "In instance loot (1)".
  - A constructed item with every kind shows all six sections in order with their counts. Contained in has a Kind column and merges objects and container items. Single-kind sections have one column.
  - Summary text for 14149 and for the constructed item. The summary is muted.
  - Fixture 2874 has VanCleef first, the renderer shows "60.00 %", and there is no divider.
  - Constructed rows sort by rate descending with ties by name, then "Rate unknown (2)", then the unknown rows by name. The divider is muted, has no link, leaves the Rate cell blank, is skipped by selection going down and going up, and the row sorter is null.
  - Chips are "Questie + AtlasLoot" and "AtlasLoot" on non-questie rows and null on the questie row. The Rate column uses `RateBarRenderer`.
  - Drop links: the NPC and zone refs are present, the source renders in link colour, and `activateRow` opens the NPC.
  - 190179 shows the divider, then "Unknown NPC #185333".
  - Loot list: columns are Loot list, Boss, Rate. The title is "Ragefire Chasm › Taragaman the Hungerer", with an instance link and a boss NPC link. The rate shows "33.30 %". Activation opens the instance.
  - Duplicate loot lists get the slot suffix and are ordered by rate, then slot. A boss without an NPC has no link.
  - Ctrl+4 then Ctrl+1 scroll to the expected section tops and select row 0. Ctrl+7 is unbound.
  - Ctrl+1 on a table that starts with the divider selects row 1.
  - Header subtitle and the starts-quest link.
- **`RateBarRendererTest`** (5 tests): text and right alignment; band colours at 30, 10, 3 and 0.5 equal `#7FBF6A`, `#C9B458`, `#C98A4E` and `#B3625A`; null gives a muted "—" and no bar; the painted pixel inside the bar has the band colour and the track is 48 × 10; `install` widens the column.
- **`SourceSectionTest`** (4 tests): heading text and gold colour; no `JScrollPane` ancestor, header inside the section, and preferred height equal to rows × row height + header; maximum height equal to preferred height; rejects a scrolled `LinkTable`.
- **`OriginChipTest`** (3 tests): questie and null give no chip and empty text; atlasloot and both give the chip texts; painted output shows an outline for "both" and nothing at all for questie.

## Files

Created (main, under `app/src/main/java/dev/wowforever/app/ui/`):
- `pages/ItemPage.java` (189 lines)
- `pages/ItemSummaryLine.java`
- `pages/PageContent.java`
- `widgets/SourceSection.java`
- `widgets/RateBarRenderer.java`
- `widgets/OriginChip.java`
- `widgets/DividerRows.java`
- `DropRow.java`
- `LootListRow.java`

Modified:
- `Columns.java`
- `detail/LinkTable.java` (inline mode)
- `detail/DetailPanels.java`

Deleted:
- `detail/ItemDetailPanel.java`

Tests created:
- `pages/ItemPageTest.java`
- `widgets/RateBarRendererTest.java`
- `widgets/SourceSectionTest.java`
- `widgets/OriginChipTest.java`

Tests modified:
- `AppControllerTest.java`
- `detail/DetailPanelsTest.java`

Tests deleted:
- `detail/ItemDetailPanelTest.java`

Nothing outside the worktree was touched.

## Concerns and deviations

1. **`droppedBy()` returns `LinkTable<DropRow>`, not `LinkTable<DropSource>`.** The divider is a pseudo-row in the model, as decided, so the row type has to be `DropRow`. `activateRow(0)` works exactly as the controller test expects.
2. **When the divider appears.** It appears whenever any unknown-rate rows exist, including items where every rate is unknown (190179 shows "Rate unknown (1)" above its only row). This follows the lead's test wording and the review's "unknown rows last under a muted Rate unknown (n)". If the divider should appear only between two non-empty groups, it is a one-line change in `DropRow.ranked`.
3. **The Level column.** It uses `Columns.Levels` ("21", "20–21"), with 0 treated as unknown like `Labels.levels`, instead of the "Level 21" text from `Labels.levels`. That text would repeat the column title in every cell.
4. **The drop table is not sortable.** Its row sorter is removed so the divider stays between its groups. The other section tables keep their sorters.
5. **Duplicate loot lists are told apart by slot** (" · slot 24"), per the lead's decision. The brief said to append the "list name". The boss name appears both in the "Instance › Boss" title and in the Boss column, because the Boss cell is what links to the NPC.
6. **No `Pages.build` dispatch.** The brief mentions `Pages.build(ref, repos, nav)`, but the lead's decision 5 keeps the dispatch in `DetailPanels.render`. The packages `ui.detail` and `ui.pages` now depend on each other: `DetailPanels` uses `ItemPage`, and `ItemPage` uses `HeaderPanel` and `LinkTable`.
7. **Tabular figures.** Java2D has no OpenType `tnum` switch. The numbers are right-aligned with a fixed two-decimal format, so they line up only if the UI font's default digits are tabular. Segoe UI's are.
8. **Focus.** Keyboard focus cannot be asserted headlessly because components have no peer. The tests assert the binding, the scroll position and the row selection. `requestFocusInWindow()` is called but not observed.
9. **`Columns.java` is 314 lines**, over the ~200 guideline. It was already about 290 lines before this task, and the lead named it as the place for `dropSourcesRanked()`. Splitting it would be a separate refactor.
10. **No visual check.** I did not launch the app; verification is by headless tests only.

## Fix round 1

**Commit:** `31f01c5 fix(ui): item header without iLvl 1; divider only between rate groups`
**Status:** DONE

What changed:
1. **No "iLvl 1" in the header (Important).** `ItemPage.ItemHeader.of` now builds the subtitle with `Labels.join("Item #" + id, Labels.itemSubtitle(class, subclass, itemLevel, requiredLevel))`. Fixture 14149 reads "Item #14149 · Cloth · iLvl 18 · Req 13". The level-1 quest item reads "Item #2874 · Quest · Starts quest: The Unsent Letter". `ItemPageTest` pins both strings.
2. **Divider only between the two groups.** `DropRow.ranked` adds "Rate unknown (n)" only when both known-rate and unknown-rate rows exist.
   - `unknownNpcRendersPlaceholderUnderTheDivider` became `unknownNpcRendersPlaceholderWithoutADividerWhenNoRateIsKnown`. Fixture 190179 now shows only "Unknown NPC #185333".
   - `jumpingToADividerLedTableSelectsTheFirstSource` became `allUnknownRatesHaveNoDividerAndJumpSelectsTheFirstSource`. Two unknown-rate rows show sorted by name with no divider, and Ctrl+1 selects row 0.
   - The mixed-rate divider test, which covers skipping the divider in both directions, is unchanged.
3. **Removed the unused `Columns.dropSources()`.** `TableLinksTest` now drives a `LinkTable<DropRow>` over `dropSourcesRanked()`. `LinkTableTest` now uses `dropSourcesRanked()`, where Rate is a `Double` and null when unknown. Its numeric-text sorting and tab tests use `Columns.lootRows()`, whose "Drop %" column is a numeric text column. I also added `inlineTableHasNoScrollPaneAndShowsItsHeader`.
4. **One `positive` helper.** `Labels.positive(Integer)` is now package-private and documented. The duplicate in `Columns` is gone, and the Level column calls `Labels.positive`. `Columns.java` is now 299 lines.
5. **Scale-aware rate bar tests.** `RateBarRendererTest` compares the track size and the column width with `UIScale.scale(...)`. The painted cell size is scaled too, so the pixel check works at any user scale.

Tests run, from the worktree root with the JDK 21 prefix:

```
./mvnw -B -q -pl app -am test -Dtest='ItemPageTest,RateBarRendererTest,LinkTableTest,TableLinksTest,LabelsTest,AppControllerTest,DetailPanelsTest' -Dsurefire.failIfNoSpecifiedTests=false
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.pages.ItemPageTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.widgets.RateBarRendererTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.detail.LinkTableTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.TableLinksTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.LabelsTest
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.AppControllerTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ui.detail.DetailPanelsTest

./mvnw -B -q verify
(no output) exit 0. The app module ran 261 tests: 0 failures, 0 errors, 0 skipped.
```

Remaining notes: `Columns.java` is still 299 lines, over the ~200 guideline. None of the earlier concerns 3 to 10 changed.
