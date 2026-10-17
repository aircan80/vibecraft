# Task 6 report: merged Dropped by, section source filter, search kind chips, master-list auto-collapse

Commits on `feature/phase3b-quest-ui`:
- `22ce22d` is the feature, with parent `02ae8bf`.
- `d985c88` adds the Notable Type column after the controller's ruling.

Status: DONE. The Hogger question was ruled on and the ruling is applied.

## What was built

**Merged "Dropped by" (item page)**
- `DropRow` gains `record Loot(InstanceLoot list)`, and `Source` gains an optional `InstanceLoot instance`.
  - `Source(DropSource)` stays as a convenience constructor.
  - New row accessors: `loot()`, `pct()`, `name()` and `ref()`.
- `DropRow.merged(npcRows, loot)` ranks every row by rate, descending, with ties by name, then instance, then slot. Unknown rates come after the "Rate unknown (n)" divider.
  - A loot list whose boss NPC is among the NPC rows merges into that NPC's row, and the row shows the instance chip.
  - The shipped data needs this: 2,267 of the 2,297 boss-loot rows that have a boss NPC also have an `item_npc_drop` row for that NPC. Without merging, almost every boss item would list its boss twice.
  - Trash lists, bosses without an NPC, and bosses whose NPC row the filter hid stay rows of their own.
- `Columns.dropSourcesMerged()` handles every row kind. `dropSourcesRanked()` now delegates to it, so the existing LinkTable and TableLinks tests are unchanged.
  - A loot row reads as its boss or list name, such as "Trash".
  - It links the boss NPC, or else the instance.
  - Level and Zone are blank, and Origin is `atlasloot`, which shows as the "AtlasLoot" origin chip.
- New `widgets/ChipCellRenderer` draws a `Chip` with Style PLAIN after the Source cell, reading "Molten Core".
  - It wraps the column's own renderer and reports the text's foreground, so link colour checks still hold.
- The "In instance loot" section is removed, and `SectionOrder.ITEM` is unchanged.
  - The summary line still counts "n loot lists".
- Icon rule: if any row is a `DropRow.Loot`, the heading gets no icon. When every row is an NPC row (merged boss rows included), it keeps the NPC icon from `railTarget`.
- Filter: `SourceFilters.apply` still filters NPC rows only.
  - The heading counts the data rows shown.
  - The note counts the NPC rows hidden.
  - A section left holding only loot rows is not emptied.
- Rate tooltips on Loot rows use `(bossNpcId, itemId)` when `sourceCount > 0`.

**NPC page "Notable" group**
- `ItemCategories.NOTABLE = "Notable"` leads `DROP_ORDER`.
- `ofNpcDrop` puts quality ≥ 4, or class 12 (also className "Quest"), under Notable. Everything else goes through the unchanged `ofDrop`.
- Quest items are never pool classes, so the World drops rule (≥ 20 NPCs, unknown rate or < 10 %) is untouched.
- Each Notable row keeps its quality colour, because the Item column is coloured by quality.
- Each Notable row also says what it is in a **Type** column right after the item ("Quest item", "Gear", "Recipe"). This is commit `d985c88`.
  - `ItemCategories.notableType` and `LootTables.Grouping.typeOf` provide it, and the text is muted.
  - Other groups leave the cell blank, because their heading already says it.
  - The column is left out when no row is Notable (`VisibleColumns`).

**Section source filter**
- New `widgets/SourceFilterControl(SourceFilter current, Consumer<SourceFilter> onChange)` is a small `JComboBox<SourceFilter>`.
  - `LEGEND` is exactly "≠ The sources disagree on this rate; hover the rate to see each source." and the tooltip equals it.
  - Its accessible name is "Drop sources".
  - It fires only when the chosen filter differs from the one last shown.
- The control is set as the trailing component of "Dropped by" (only when NPC rows exist) and of "Drops".
  - `SectionHeading` gains a `trailing()` getter.
- Plumbing:
  - New page constructors take an `onFilterChange`; the old ones pass a no-op.
  - New overload `DetailPanels.render(..., Consumer<SourceFilter> onFilterChange, BooleanSetting)`.
  - New `AppController.changeSourceFilter(f)`: saves via `view.setSourceFilter`, then rerenders; nothing happens when `f` equals the current filter.
  - `MainView.setSourceFilter` is a default no-op; MainFrame saves to UiState.
- `WindowMenus`: the Drop sources submenu is removed, with `sourceFilterMenu` and `DROP_SOURCES`.
  - `Actions` loses `rerender`, and `addViewItems` loses its `UiState` parameter.

**Kind chips in the dropdown**
- `SearchDropdown` owns the `KindChips` as its top row, with a BorderLayout of chips over the list, all inside the line border. The row shows with hits and hides with recents.
  - A chip click narrows the hits on screen to that kind at once. The "Show all" total becomes the chip's count.
  - It then calls `onKindSelect`; `SearchBar` re-looks up there, as before.
  - New: `kindChips()`, `onKindSelect()`, `chipRowHeight()`.
- Height: the list part keeps the 520 px cap and the chip row is added on top, so eight hits plus chips do not scroll.
- `KindChips` wraps:
  - `setWrapWidth(w)` and `heightAt(w)` mirror FlowLayout's row breaking.
  - vgap is now 4, and `count(kind)` returns the counts shown.
- `SearchBar` takes its chips from the dropdown, and the toolbar has no chips.
  - The compact window still gets the `KindMenu` beside the box, as before. It is a menu, not chips, and is not hidden in full mode.

**Master-list auto-collapse**
- New `ui/ListVisibility(boolean savedVisible)` with `WIDE_PX = 1600`.
  - `resultsShown()` shows the list.
  - `previewed()` changes nothing.
  - `resultOpened(w)` hides the list unless it is pinned or `w ≥ 1600`.
  - `toggle()`: a shown list hides and is unpinned; a hidden list shows and is pinned.
- `MainFrame.buildContent` makes one ListVisibility per content and applies it to the split.
  - Ctrl+B (`TOGGLE_LIST`) goes through it and saves `pinned()` to `UiState.listVisible`.
  - `MainFrame.onResultOpened()` and `onResultsShown()` are ignored in compact mode. They use `updateList` and `listVisibility` statics, which tests reach.
- `MainView` has `onResultOpened()` and `onResultsShown()` defaults.
- `AppController` calls `onResultOpened` at the start of `commit` (also when the row is already on screen, which is Enter after a preview) and in `openHit`.
- `onResultsShown` is called when a search fills the master list without an opened hit and the view shows a master list. A dropdown hit's quiet list fill does not re-show it.

## Red → green

- **Red.** I stashed the main sources (`git stash push -u -- app/src/main`) and ran the new and changed test classes against the old code. testCompile failed on the missing features:
  - `ListVisibility` (ListVisibilityTest, MainFrameContentTest).
  - `ChipCellRenderer`, `SourceFilterControl` and the 6-arg `ItemPage` (ItemPageTest).
  - `WindowMenus.Actions` arity and `addViewItems` (WindowMenusTest).
  - `MainView` overrides in StubView.
  - `KindChips` in SearchBarTest.
  - Before the implementation, the new NpcPageTest expectations ("Notable (n)") and ShippedDataTest also fail on the old `ofDrop` grouping.
- **During green.** Three failures came up and were fixed:
  - My own `filterNeverHidesInstanceRows` expectation was missing the divider.
  - The dropdown height cap was cutting the chip row. Fixed by capping the list only.
  - `SourceFilterControl` compared against the build-time filter instead of the last chosen one.
- **Green.** Focused classes pass, and so does the full `./mvnw -B verify` (exit 0).

| Module | Tests | Failures | Errors |
|---|---|---|---|
| Lua table parser | 61 | 0 | 0 |
| Data importer | 449 | 0 | 0 |
| Browser application (shipped data, 70450 DB) | 897 | 0 | 0 |

These counts come from the verify run before `d985c88`, which was again exit 0. The importer has 449 tests because Task 19's commit `807185b` landed between my two commits; it was 448 at `22ce22d`.

## New tests
- `ListVisibilityTest`: previewNeverCollapses, openingAResultCollapses, ctrlBKeepsTheListOpen, wideWindowsKeepTheList, searchingShowsTheList, aSavedPinStartsShownAndStaysPinned, ctrlBOnAListASearchShowedHidesIt.
- `SourceFilterControlTest`: changesFilterAndRerenders (onChange, UiState round-trip), choosingThePagesOwnFilterDoesNothing, theTooltipIsTheRateLegendAndTheItemsReadAsTheFilters (tooltip == LEGEND).
- `ItemPageTest`: droppedByMergesInstanceLoot (Felheart Belt 16806 shaped as the shipped DB: Molten Core trash NPCs and AtlasLoot "Molten Core › Trash", plus a rated Ragnaros list ranking among the NPCs), filterNeverHidesInstanceRows, theFilterControlSitsBesideTheDroppedByHeading, aBossListMergesIntoItsNpcRowWhichShowsTheInstance, lootListsOfTheSameNameKeepTheirSlotOrder.
- `NpcPageTest`: notableDropsFirst, theFilterControlSitsBesideTheDropsHeading.
- `SearchBarTest.noKindChipsInToolbar`, `SearchDropdownTest.kindChipsInDropdownFilter`.
- `KindChipsTest`: theChipsWrapToTheWidthTheyAreGiven, theCountsShownCanBeRead.
- `MainFrameContentTest`: anUnpinnedListShowsForResultsAndCollapsesWhenOneOpens, ctrlBPinsTheListAgainstCollapsing.
- `AppControllerTest`: commitCollapsesPreviewDoesNot (also covers openHit and the list preview), searchResultsInTheCompactWindowShowNoList.
- `WindowMenusTest.theFilterControlBesideDropsSavesItAndShowsTheCurrentPageAgainFiltered` replaces the menu-driven test, with the same VanCleef assertions.

## Changed expectations, and why
- **ShippedDataTest.theShippedPagesReadAsReviewed**: Hogger's first row is now "Notable (2)", not "Quest items (2)". "World drops (23) ▸" and the row count of 4 are unchanged.
  - Reason: the controller ruled that Notable means quality ≥ 4 or item class 12 Quest, shown first, and that the < 10 % rule is unchanged.
- **NpcPageTest.columnsEmptyOrUniformForEveryRowAreHidden** (`d985c88`): VanCleef's drops now show "Item, Type, Item level, Rate, Origin". His quest item is Notable, so it says its type, and the rate and origin renderers moved one column right.
- **NpcPageTest**, because quest items now lead under Notable:
  - vanCleefsLootIsGroupedByItemClassAndRankedByRate: "Notable (1), An Unsent Letter, Gear (1), Cruel Barb, …", and the link check moved to row 3.
  - worldDropsComeLastInACollapsedGroupThatAClickOpensAndEnterCloses: "Notable (2)".
  - aRatedPoolDropManyNpcsShareIsStillAWorldDrop: "Notable (1)".
  - dropRowsLinkToTheirItem: activates row 3, Cruel Barb, since row 1 is now the quest item.
- **ItemPageTest**, because "In instance loot" merged into "Dropped by":
  - onlyNonEmptySectionsInOrderWithCounts: 14149 is "Dropped by (1)" only.
  - everyKindHasItsSectionInTheFixedOrder: "Dropped by (4)", with VanCleef's list merged and the trash list a row, and no "In instance loot".
  - sectionOrder: one section for 14149.
  - everySourceShowsByDefaultWithoutANote: "Dropped by (4)".
  - hidingAtlasLootOnlyDropsKeepsQuestieAndBothAndNeverTouchesTheLootLists: the loot is checked on VanCleef's merged row.
  - hidingQuestieOnlyRowsKeepsAtlasLootAndBoth: the loot-section assertions are dropped.
  - rateCellsWithLoadedSourcesListThemInTheirTooltip: the loot tooltip is checked on a Loot row, Mr. Smite.
  - lootListsReadInstanceThenListAndLinkBoth became aBossListMergesIntoItsNpcRowWhichShowsTheInstance.
  - lootListsThatReadTheSameNameTheirSlot became lootListsOfTheSameNameKeepTheirSlotOrder. Rows read the boss name with the instance as a chip; the "· slot n" titles belonged to the old LootListRow column.
  - theJumpKeysStillReachTheSectionsUnderTheCard: one section.
- **DetailPanelsTest.renderAppliesTheSourceFilterToItemAndNpcPages**: item 16800 under Hide AtlasLoot-only is now "Dropped by (1)" with "1 row hidden…". Its boss loot list is never filtered, which is the brief's rule.
- **SearchDropdownTest.heightFitsEveryRowWithoutScrollingOrPadding**: the height includes `chipRowHeight()` with hits. With recents there is no chip row.
- **CompactShellTest.inTheShellTheKindChipsFoldIntoAKindMenu** and **LayoutSwitchTest.assertFullLayout**: the chips are never inside the search bar, and the full layout has no kind menu.
- **UiStateTest.freshStateHasTheDefaults**: `listVisible()` defaults to false (see the open choices).
- **MainFrameContentTest**:
  - toggleListHidesAndShowsTheListAndRemembersIt and movingTheDividerSavesIt start with the list pinned, `setListVisible(true)`, because an unpinned list now starts hidden.
- **WindowMenusTest**, because the menu is gone:
  - theDropSourcesMenuOffersEveryFilterWithTheSavedOneSelected and aMenuOpenedAfterTheOtherLayoutsMenuChangedTheFilterShowsTheNewOne are removed.
  - bothLayoutsGetTheSameViewAndHelpItemsFromOneBuilder no longer lists "Drop sources" and has no rerender call.
  - choosingAFilterSavesIt… now drives the page control.

## Ruling applied
- **Hogger.** The controller ruled that Notable means quality ≥ 4 or class 12 Quest, shown first. Hogger therefore reads "Notable (2)", then "World drops (23)" collapsed.
- **Notable rows stay recognisable.** Each keeps its quality colour and shows its category in the Type column (`d985c88`).
- `NpcPageTest.notableDropsFirst` checks the order, the Type cells and the epic and legendary quality colours. It also checks that a table without Notable rows has no Type column.
- Red: run against the pre-`d985c88` main sources, the test failed on the missing Type column at NpcPageTest:258.

## Open choices made
- **`UiState.listVisible` default.** It was true and is now false. It stores the pin only, so a true default would pin every fresh install and switch auto-collapse off.
  - Users who had saved true stay pinned.
  - A fresh start shows no list until the first search.
- **Ctrl+B on a list a search showed** (visible, unpinned): it hides the list. Toggle means "visible becomes hidden"; pinning needs Ctrl+B while the list is hidden. The menu's "Show list" check box stays consistent with this.
- **Wide windows.** `resultOpened(≥ 1600)` keeps the list as it is and does not force it open. Nothing shows the list at startup by width alone.
- **Merging boss lists into their NPC row**, instead of listing the NPC and the list side by side. This deduplicates about 99 % of boss-loot rows in the shipped DB.
- **The compact window keeps its KindMenu.** It is a menu, not chips, and the dropdown carries the chips too.
- **No "≠" mark in the control itself.** The legend lives in its tooltip and accessible description only.
- Screens were not checked by eye. All verification is headless, as the constraints require.

## Fix round 1 (commit 247e6ed, on top of cc4d7c2)

Every finding in task-6-review.md is fixed.

**Important 1: Esc reaches a collapsed list**
- `MainFrame.escape()` now runs `revealList(content)` in the full layout before it focuses the list. A list that an opened result collapsed comes back through `ListVisibility.resultsShown`, so it is shown without being pinned.
- `onResultOpened()` reads the focus owner before collapsing the list. When the owner was inside the list and the list is now hidden, focus moves to the page (`focusTargetIn`) through the new static `focusAfterCollapse(owner, list, holder)`. With no page in the holder yet, the holder takes the focus.
- Two new tests pin this:
  - `MainFrameContentTest.escBringsACollapsedListBackUnpinned`: collapsed, then Esc's reveal makes the list and its `jlist()` visible, unpinned and unsaved; the next opened result collapses it again.
  - `MainFrameContentTest.enterInTheListMovesTheFocusToThePageWhenTheListCollapses`.

**Important 2: Notable never reads "Junk" (ruling)**
- `ItemCategories.notableType` names each Notable row as follows:
  - Miscellaneous (class 15) uses its subclass through `miscellaneousType`: 1 Reagent, 2 Pet, 3 Holiday, 5 Mount. Junk (0), Other (4) and unknown subclasses read "Other".
  - Any other class that the groups do not name reads as its class name ("Container").
  - An unknown class reads "Other".
- The subclass names come from the shipped `item_subclass` rows for class 15. They are mapped in code because `LootRow` carries the subclass id, not its name.
- Tests:
  - `NpcPageTest.aNotableItemIsNeverCalledJunk` covers Swift Zulian Tiger-like (Mount), Desecrated Breastplate-like and Bindings-like (Other), an epic pet (Pet) and an epic Container (Container). A plain Miscellaneous junk item stays under "Junk".
  - ShippedDataTest checks High Priest Thekal 14509: the Swift Zulian Tiger's Type cell reads "Mount".

**Minors**
1. `DropRow.merged`:
   - An NPC row takes in only the first of its boss's lists that gives the same rate.
   - A second list of the same boss stays a row of its own, and so does a list whose rate differs.
   - A merged list's `disagrees` is ORed into the rate mark.
   - The new `DropRowTest` has 6 cases: merge, second list, differing rate, disagree OR, slot suffix, divider.
2. Loot rows that would read the same, such as one list with several slots, end in " · slot n" and sort by slot number. `DropRow.Loot` gained a `title`. The test is renamed back to `ItemPageTest.lootListsThatReadTheSameNameTheirSlot` and expects "Armory trash · slot 4", then "Armory trash · slot 24".
3. `LootListRow`, `Columns.lootLists()`, `Columns.dropSourcesRanked()` and `DropRow.ranked` are deleted. LinkTableTest and TableLinksTest now use `dropSourcesMerged()`. The loot-list "≠" check now runs on a `DropRow.Loot` row.
4. README:
   - The chips are described as the dropdown's top row, with their keys.
   - Opening a result collapses the list (Esc brings it back, Ctrl+B keeps it).
   - The filter is the control beside Dropped by and Drops, with the legend in its tooltip; dungeon loot lists are never filtered.
   - The keymap table has a new row.
5. Kind chips by keyboard:
   - With hits shown, Up from the box (nothing selected) goes onto the chip row, at the selected chip.
   - Left and Right move along the row, Enter picks a chip as a click does, and Down returns to the first hit.
   - Typing, Esc, closing the list or showing recents leaves the row.
   - A gold FlatLaf outline marks the chip the keyboard is on (`KindChips.setHighlighted`), because the chips sit in a non-focusable window and the search box keeps the focus.
   - Off the chip row, Left and Right still run the field's own caret actions.
   - The keys are declared as a DOCUMENTED_ONLY row in `KeyMap.BINDINGS`, the help table and the README, kept equal by ReadmeKeyMapTest. The row is appended last, so the index-based KeyMapTest lookups stay put.
   - Tests: `SearchDropdownTest.kindChipsAreReachableByKeyboard` and `leftAndRightStillMoveTheCaretOffTheChips`.
6. The `ofNpcDrop` Javadoc now says Notable comes first, so an epic pool-class item (Pattern: Invulnerable Mail) is Notable rather than a world drop. It says every other drop is grouped as before. The earlier report line claiming the World drops rule was untouched is superseded by this.
7. The `ChipCellRenderer.Cell` accessible name is "<text>, <instance>". `aBossListMergesIntoItsNpcRowWhichShowsTheInstance` asserts "Taragaman the Hungerer, Ragefire Chasm".

**Changed expectations this round**
- `KeyMapTest.theBindingListIsTheReviewKeyboardMap` and `documentedOnlyRowsAreTheOnesBoundElsewhere` include the new chip row.
- `ItemPageTest.lootListsThatReadTheSameNameTheirSlot` expects the slot suffix again, per Minor 2.
- `LinkTableTest.disagreeingRatesAreMarkedInEveryDropTable` checks the loot-list case on a `DropRow.Loot` row in the merged columns (Rate is column 3), per Minor 3.

**Red → green**
- Red: with the main sources stashed, the new tests failed to compile on the missing `revealList` and `focusAfterCollapse`, `miscellaneousType`, `KindChips.highlighted` and `setHighlighted`, and `DropRow.Loot(list, title)`.
- Green: focused classes pass, then the full `./mvnw -B verify` exits 0.

| Module | Tests | Failures | Errors |
|---|---|---|---|
| Lua table parser | 61 | 0 | 0 |
| Data importer | 449 | 0 | 0 |
| Browser application | 923 | 0 | 0 |

The app had 912 tests at cc4d7c2, measured by running the app suite on a stash of this round's changes. This round adds 11, which makes 923.
