**Approved** — C1, I1-I4, M1-M4 closed; 0 critical, 0 important, 1 minor (new, optional)

# Task 5 fix round 1 re-review (0e69058)

Read-only review of `review-5c27b3f..0e69058.diff` and `git show 0e69058:<path>`. No Maven run (verify reported green: 61 / 431 / 876).

## Findings closed

- **C1 closed.** `SourceSection.markEmptied`/`isEmptied` (SourceSection.java:211) replaces the table visible flag; `SourceFilters.annotate` calls it; `SourcesPage.jumpTargets` filters on `!isEmptied()`. `NpcPageTest.ctrlNumberInTheCompactWindowOpensTheCollapsedSectionItJumpsTo` builds a five-section page in a compact shell, asserts [open, closed x4], `jumpTo(1)` opens Sells with row 0 selected, `jumpTo(4)` opens Ends quests. Before the fix the collapsed ordinary sections dropped out of the targets, so index 1 would have hit Location and index 4 would have thrown. The test would have failed.
- **I1 closed.** Button text is the section count; the cut applies only when `count > ROW_LIMIT` and rows lie below the cut (`isCut`). `LinkTable.setRowLimit` counts data rows via `setNonDataRows`, wired in `DividerRows.install` (so both `LootTables.grouped` and the item "Dropped by" table are covered). `SourceSectionTest.theCutCountsDataRowsAndTheButtonReadsTheHeadingsCount` covers 10 vendors with 3 dividers (10 rows shown, "Show all 10", preferred height checked) and 8 with 2 dividers (no cut).
- **I2 closed.** `setRowLimited(false)` applied in `ListPage` to every section after the bands are built. `ListPageTest.aRailListIsNeverCutAtEightRows` asserts `shownRows == rowCount` for the item list and every instance band, and no button.
- **I3 closed.** Boss loot opts out (`InstancePage` lootSection); Quests keeps the limit. `InstancePageTest.questsSection` asserts both and the `SectionOrder.INSTANCE` order (M4).
- **I4 closed.** `SectionHeading.setFoldState` gives role TOGGLE_BUTTON, EXPANDABLE plus EXPANDED/COLLAPSED, name "<text>, expanded|collapsed" computed live, with fired events. `SourceSectionTest.theHeadingSaysWhetherItsSectionIsOpen` covers both states, count change, and back to a plain heading. Focus ring and link underline are painted, not asserted (accepted).
- **M1 closed.** `LoadingPage.showFailure` called from the error callback when the loading page is up; `AppControllerTest.aLoadThatFailsAfterTheLoadingPageShowedSaysSoThere` asserts the line no longer reads Loading and the status shows the error.
- **M2 closed.** The hierarchy listener ignores a null parent; `takenOutAndPutBackASectionKeepsWhatTheUserOpened` covers the instance-page rebuild.
- **M3 closed.** `setCollapsed` ends with `updateLimit()` and the cut no longer reads the table visible flag; `rowsGivenWhileCollapsedGetTheButtonOnOpening` covers it.
- **M4 closed.** `addSections` sorts the right pane by `SectionOrder.INSTANCE`; javadoc explains the two panes.

## Regression checks

- The opt-out leaves the heading count untouched: `updateLimit` only changes the row limit and button, and `ListPageTest` still asserts "Results (n)".
- Ctrl+n opens collapsed sections (the new compact test) and still skips emptied ones (`aSectionLeftWithoutRowsStaysSoThroughCollapsing`).
- The accordion on instance bands is unchanged by the opt-out; full app suite reported green with no existing expectation edited.
- No new work on the EDT or database access.

## Open findings

**M5 (minor, optional).** `SourceSection.java:211-218`: `markEmptied` on a section that is already collapsed records `false` for `table.getParent()` when the table sits inside a lead body. Opening it then hides the whole body, including the map, whereas marking an open section hides only the table. Only reachable if a map section is emptied by the filter while collapsed; no current caller does that. Fix by recording the table itself, or leave as is.
