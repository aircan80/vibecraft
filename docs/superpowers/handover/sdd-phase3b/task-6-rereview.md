Approved (Critical 0, Important 0, Minor 2)

# Task 6 fix round 1 re-review (247e6ed on cc4d7c2)

Read-only review of the fix diff, the report's fix-round section and the committed sources. No Maven run; verify was reported green (61 / 449 / 923).

## Findings closed

- **Important 1, Esc and focus.** `MainFrame.escape()` calls `revealList` (full layout only) after `searchBar.escape()` fails, so the chain order (close dropdown, clear search, focus list) is intact. `revealList` uses `ListVisibility::resultsShown`: shown, not pinned, not saved. `onResultOpened` reads the focus owner before collapsing and moves focus to the page via `focusAfterCollapse`. Tests: `escBringsACollapsedListBackUnpinned` (visible, `jlist` visible, `pinned()` false, `UiState` unchanged, next open collapses again) and `enterInTheListMovesTheFocusToThePageWhenTheListCollapses`. Both fail to compile on the old sources.
- **Important 2, "Junk" labels.** `notableType` returns the subclass for Miscellaneous (Mount, Pet, Reagent, Holiday, else Other), the class name for unnamed classes, and Other for unknown. `aNotableItemIsNeverCalledJunk` pins Mount, Other, Other, Pet, Container and a plain junk item left unlabelled. `ShippedDataTest` asserts Swift Zulian Tiger on NPC 14509 reads "Mount". Before the fix the cell read "Junk".
- **Minor 1, `DropRow.merged`.** Only one list with the same rate merges, and it is removed from the pool, so a second list or a differing-rate list stays a Loot row. The merged list's `disagrees` is ORed in. `DropRowTest` has six cases covering these shapes.
- **Minor 2, slot disambiguation.** The " · slot n" suffix is added only when two rows read the same, and sorting is by slot number. `lootListsThatReadTheSameNameTheirSlot` expects "· slot 4" before "· slot 24"; `listsThatWouldReadTheSameNameTheirSlot` covers a different instance with the same name.
- **Minor 3, dead code.** `LootListRow`, `lootLists()`, `dropSourcesRanked()` and `DropRow.ranked` are removed, and the tests moved to the merged columns.
- **Minor 4, README.** Chips, collapse, Esc and Ctrl+B, and the filter control are described. The keymap row is added to `KeyMap.BINDINGS` and the README table with matching text, so `ReadmeKeyMapTest` still holds. `KeyMapTest` expectations were updated.
- **Minor 5, chips by keyboard.** `SearchDropdown` handles Up from the box, Left/Right (falling back to the caret action when off the chips), Enter via `doClick`, and Down back to the hits. Typing, Esc, hide and recents leave the row. `kindChipsAreReachableByKeyboard` and `leftAndRightStillMoveTheCaretOffTheChips` cover it.
- **Minor 6, Javadoc.** Reworded to say Notable comes first and moves an epic pool item out of World drops.
- **Minor 7, accessible name.** `Cell` names itself "<text>, <instance>". `aBossListMergesIntoItsNpcRowWhichShowsTheInstance` asserts "Taragaman the Hungerer, Ragefire Chasm".

## Regression checks

- Auto-collapse still fires only through `onResultOpened`; preview is untouched. The existing `previewKeepsIt` style assertions remain in `MainFrameContentTest`.
- Pin persistence is unchanged. `revealList` goes through `resultsShown` and the new test asserts `UiState.listVisible` stays false.
- The Esc chain order matches the help table. The compact layout skips the reveal.

## Open findings (Minor)

1. `MainFrameContentTest.java` (`escBringsACollapsedListBackUnpinned`): the test calls the static `revealList` directly, so it does not prove `MainFrame.escape()` calls it. Deleting that call in `escape()` at `MainFrame.java` (the `if (!layout.isCompact())` block) would leave the suite green. Drive Esc through the installed key action to pin the wiring.
2. `enterInTheListMovesTheFocusToThePageWhenTheListCollapses`: same shape. It tests `focusAfterCollapse` but not that `onResultOpened` applies its result.
