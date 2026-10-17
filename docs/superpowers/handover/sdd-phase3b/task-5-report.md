# Task 5 report: shared page skeleton and section rules

Commit `c49470c` on `feature/phase3b-quest-ui` (parent `e318076`).

## What was built

- **SectionOrder** (new, `ui/pages`). It holds `ITEM`, `NPC`, `ZONE` and `INSTANCE` verbatim from the brief.
  - `sort(order, blocks, title)` is a stable sort. Titles not in the list keep their relative order after the listed ones, as the controller ruled.
  - `after(order, anchor, title)` inserts one title after another.
- **SourcesPage.addSectionsInOrder(order)**. Pages register sections, plus other titled blocks through `addBlock(title, component)`, such as the zone map and link rows. The call then re-adds them in order under the header and keeps `sections()` in the same order.
  - `jumpTo` (Ctrl+n) expands a collapsed section before scrolling to it.
- **Page orders.**
  - Item: Dropped by, Sold by, Quest reward, Contained in, then In instance loot and Related quests.
  - NPC: Drops, Sells, Location, then Starts quests and Ends quests.
  - Zone: header, Map, Dungeons & raids, Sub-zones, Quests, NPCs, Objects.
  - Object: order unchanged. Its spawn section is renamed too (see open choices).
- **HeaderPanel** gains `setPlate(Icon)`, `setKindWord(String)`, `setFacts(String)`, `addChip(Chip)` and `addChipLink(text, ref, nav)` (returns the `JButton`). It also gains `facts()`, `chips()`, `kindWord()` and a public `factsLabel()`.
  - The plate is a 28 px rounded square in `Theme.current().plate()` with the icon centred.
  - The kind word is muted Marcellus SC, 12 pt, after the title and badges.
  - The facts row shows the facts label, then the chips in the order added. It is hidden while it has neither.
  - A chip link is a `LinkButton` with a muted outline border.
  - Pages now set a plate and kind word through `SourcesPage.plateIcon(RailTarget)`. That helper uses the rail art, falling back to the small icon.
    - Item (only without a card), NPC, Zone and Instance get both.
    - Object gets the kind word only, because objects have no rail target.
    - QuestPage is untouched and left to Task 11.
- **ItemSummaryLine** is positive-only: no "no vendor" and no "no quests". The item page now shows it as the header's facts strip.
  - `ItemPage.summary()` and `summaryLabel()` read the header's facts.
- **SourceSection.**
  - `ROW_LIMIT = 8`, `showAllButton()` ("Show all 34", using the heading's count), `showAll()` and `shownRows()`.
  - The cut is a height clip: `LinkTable.setRowLimit(n)` makes an inline table as tall as its first n rows. The model and the `JTable` keep every row, so row counts in existing tests are unchanged.
  - Selecting a row below the cut, for example by arrowing down, shows all rows. The listener follows `DividerRows` swapping in its selection model.
  - `setCollapsible`, `setCollapsed`, `isCollapsible` and `isCollapsed`. A collapsed section shows only its heading with the count and a ▸ glyph (▾ when open).
    - A click toggles it, and so do Enter or Space while the heading has focus.
    - Collapsing remembers what was already hidden, so a table emptied by the source filter stays hidden after expanding.
  - `addFooter(JComponent)` replaces `SpawnSection`'s direct `add(..., SOUTH)`. The note now sits in the same foot panel.
  - Compact rule: a hierarchy listener, the same mechanism `LinkTable` uses for compact columns.
    - Inside `CompactColumns.KEY` the section becomes collapsible, and every section but the first of its container starts collapsed.
    - Moving within the same kind of window keeps what the user opened.
    - Leaving compact mode expands every section and makes them non-collapsible.
  - `CompactShell` only got a javadoc sentence; it already carries the key.
- **NPC "Spawns" is now "Location"** (`SpawnSection.TITLE`).
- **Instance quests.**
  - `DetailPanels.load(INSTANCE)` returns a new `LoadedInstance(InstanceDetail, List<QuestSummary>)`. The quests come from `repos.quests().byZone(zoneId)` on the worker, or are empty when the instance has no zone.
  - `InstancePage` has new constructors taking the quests. It shows a "Quests (n)" `SourceSection` with the quest rail icon and no Zone column, in the right-hand pane below the loot. The section is re-added on every encounter switch.
- **LoadingPage** (new): `LoadingPage(title, what)`, `of(kind, title)` with plate and kind word, and `what(kind)`.
  - `what(kind)` gives "Loading drops…" for an NPC, "Loading sources…" for an item, "Loading zone…" for a zone, and so on.
- **AppController.**
  - `LOADING_DELAY_MS = 150` and a new 5-argument constructor taking the loading delay seam. The 4-argument constructor uses `MasterList.timerDelay(150)`.
  - `showEntity` schedules the loading page before starting the load, so an inline worker cancels it at once. The run checks the detail generation.
  - Success, failure, every `show` and the rerender path cancel it.
  - The title is taken from:
    - the dropdown hit (new `openHit(hit, search, title)`, fed by `SearchBar.Opener`'s new 3-argument default and `MainFrame`);
    - else the master-list entry;
    - else "NPC #448".

## Red, then green

1. First focused run, with the new tests before any implementation: compilation failed with `cannot find symbol` for every new API across these tests:
   - `HeaderPanelTest`, `SectionOrderTest` and `SourceSectionTest`;
   - `AppControllerTest` (no 5-argument constructor, no `LoadingPage`, no 3-argument `openHit`);
   - `DetailPanelsTest` (`LoadedInstance`) and `ShippedDataTest` (`questsSection`).
2. After implementing, the focused run had 186 tests and 1 failure. It was my own test bug: `isNotInstanceOf` on a still-null detail. I fixed the assertion and the run went green.
3. The first full verify failed 2 shipped-data tests, both legitimate expectation changes (see below). The second full verify passed.

`./mvnw -B verify` result, BUILD SUCCESS:

| Module | Tests | Failures |
|---|---|---|
| lua-table | 61 | 0 |
| importer | 420 | 0 |
| app (shipped-data included) | 864 | 0 |

## Tests added

- New classes: `SectionOrderTest` (3 tests) and `HeaderPanelTest`.
  - `HeaderPanelTest.chipsAndPlate` checks chips after the facts and in order, and the plate pixel `#0C1424` in Slate and Parchment with the icon centred.
  - It also has tests for chip links and the kind word / empty facts row.
- `SourceSectionTest`: `eightRowsThenShowAll`, `eightRowsOrFewerShowNoButton`, `selectingARowBelowTheFoldShowsAll`, `rowsGivenLaterFollowTheLimit` and `compactCollapsesAllButFirst`.
- `ItemPageTest`: `sectionOrder` and `factsStripIsPositiveOnly`. The latter builds Huge Gnoll Claw 1931 with Hogger and quest 176 and expects "1 NPC · 1 quest".
- `NpcPageTest.sectionOrder`: Edwin VanCleef 639 from the fixture leads with Drops and has Location. A full NPC orders as Drops, Sells, Location, Starts quests, Ends quests.
- `ZonePageTest.sectionOrder`: Westfall 40 from the spawn fixture has the map before Dungeons & raids, then Quests and NPCs. All three kinds order as Quests, NPCs, Objects.
- `InstancePageTest.questsSection`, `DetailPanelsTest.anInstanceLoadsTheQuestsFiledUnderItsZone`, and two `AppControllerTest` tests:
  - `loadingPageOnlyAfterDelay`;
  - `theLoadingPageOfAHitReadsTheHitsNameAndALinkItsKindAndId`.
- `ShippedDataTest.theShippedPagesReadAsReviewed` gains these checks:
  - VanCleef's first section is Drops;
  - the 1931 facts;
  - Deadmines instance 3 lists The Defias Brotherhood and Red Silk Bandanas, with the heading count matching.

## Tests whose expectations changed

- `ItemPageTest.everyKindHasItsSectionInTheFixedOrder`: new section order.
- `ItemPageTest.summaryCountsEachKind` and `hidingAtlasLootOnlyDropsKeeps…`: no "no vendor · no quests".
- `ItemPageTest.aShortPageStartsAtTheTopWithoutAGapUnderTheHeader`: the summary is now inside the header, not right under it.
- `ZonePageTest.instancesAndSubZonesAreLinksAtTheTop`: sections read Quests, NPCs. A comment in `mapShownWithPins` was updated; its assertion still holds.
- The "Spawns" to "Location" rename touched these tests:
  - `NpcPageTest.spawnsOnMap` and `onlyTheSpawnsInTheMappedZone…`;
  - `ObjectPageTest` (2 places) and `SourcesPageIconTest`;
  - `ShippedDataTest.theCompactSpawnTableKeepsItsWholeRowUnderTheMap`.
- `theCompactSpawnTableKeepsItsWholeRowUnderTheMap` also changed because Hogger's Location section now starts collapsed in compact mode. The test asserts that and opens it before checking the map layout.

## Open choices and concerns

1. **The shipped Huge Gnoll Claw reads "1 NPC", not "1 NPC · 1 quest".** The shipped `item_related_quest` table has no row for 1931. Quest 176 links to the claw only through `quest_objective` (176, item 1931), and no item-page query reads objectives yet.
   - The brief's facts test passes on a constructed `ItemDetail` with that quest.
   - The shipped assertion says "1 NPC" honestly, with a comment that Task 14's Quests section brings "· 1 quest".
   - If the controller wants "1 quest" now, `ItemDetail` needs objective quests, which overlaps Task 14.
2. **Instance id.** The fixture's Deadmines is instance 2, and the fixture has no quests filed under zone 1581.
   - `InstancePageTest.questsSection` therefore passes a constructed quest list to the fixture instance 2.
   - `DetailPanelsTest` checks that load uses `byZone(1581)`.
   - The real check, instance 3 listing quest 166, is in `ShippedDataTest`.
3. **"Location" also renames the object page's section.** `SpawnSection` is shared, and two names for the same thing seemed worse. The brief named only the NPC.
4. **The Sub-zones row stays right after Dungeons & raids.** `SectionOrder.after` places it there instead of after Objects, because it is a link row next to its sibling rather than a section.
5. **The instance Quests section sits in the loot pane below the loot.** The left pane would widen the encounter column to the table's width.
   - The 8-row limit applies to boss loot as well. The spec's skeleton applies it to every section, and it keeps Quests reachable.
6. **The loading-delay seam is typed `MasterList.Delay`.** There is no separate `LoadingDelay` interface, so `ManualDelay` works unchanged.
7. **Enter and Space on a focused collapsible heading are not in `KeyMap.BINDINGS`.** They act like a button's own activation keys. Ctrl+n, which is declared, also opens a collapsed section. If the keymap rule should cover widget activation keys, either declare them or drop the key listener.
8. **The kind word repeats the subtitle.** It sits next to subtitles such as "NPC #639 · Level 21". I kept the subtitles to avoid churning many assertions. Worth a screenshot look.
9. **No screenshots taken.** All checks are headless. The plate, kind word, Show all row, glyph and loading page have not been seen rendered.

## Fix round 1 (review task-5-review.md, controller rulings)

**C1, Ctrl+n in compact mode.** "Emptied by the filter" is now recorded explicitly.
- `SourceSection.markEmptied()` and `isEmptied()` replace the table's visible flag. `SourceFilters.annotate` calls it.
- If the section is collapsed when marked, the remembered visibility is set instead.
- `SourcesPage.jumpTargets()` filters on `!isEmptied()`, so collapsed sections stay jump targets.
- `jumpTo` opens a collapsed target.
- New test `NpcPageTest.ctrlNumberInTheCompactWindowOpensTheCollapsedSectionItJumpsTo`:
  - a five-section NPC page under `CompactColumns.KEY` starts with only the first section open;
  - `jumpTo(1)` opens "Sells" and selects its first row;
  - `jumpTo(4)` opens "Ends quests" without throwing.
- Focus cannot be observed headless; the test asserts the selection that `jumpTo` makes before it requests focus.

**I1, the "Show all" count and the cut.**
- The button text uses the section's count.
- The cut applies only when the count is above 8 and some rows actually fall below the cut.
- `LinkTable.setRowLimit` now counts data rows. `LinkTable.setNonDataRows(predicate)` marks the rows to skip, and `DividerRows.install` passes its own divider predicate there.
- Group headings and dividers above the cut still show.
- The selection auto-expand uses `shownRows()`.
- New test `SourceSectionTest.theCutCountsDataRowsAndTheButtonReadsTheHeadingsCount`:
  - 10 vendors with 3 dividers make 13 rows; 10 show, including 2 dividers, and the button reads "Show all 10";
  - 8 vendors with 2 dividers get no cut.

**I2, list pages.** New `SourceSection.setRowLimited(boolean)` and `isRowLimited()` opt-out.
- `ListPage` turns the limit off for its Results section and for every level band.
- New test `ListPageTest.aRailListIsNeverCutAtEightRows`: `shownRows` equals the row count for the item list and for every instance band.
- Level bands stay collapsible in compact mode, as an accordion with the first band open. I chose that deliberately.

**I3, boss loot.** The loot section on `InstancePage` uses the same opt-out. The instance Quests section keeps the limit. `InstancePageTest.questsSection` asserts both.

**I4, accessibility.** `SectionHeading.setFoldState(Boolean)` handles the fold state; `SourceSection` drives it from `setCollapsible` and `setCollapsed`.
- When foldable, the heading reports:
  - accessible role TOGGLE_BUTTON;
  - states EXPANDABLE plus EXPANDED or COLLAPSED;
  - an accessible name of "<text>, expanded" or "<text>, collapsed", computed live so count changes follow.
- State and name property changes are fired.
- A focused heading paints a gold ring around its text. The "Show all" link paints a gold underline while it has focus.
- New test `SourceSectionTest.theHeadingSaysWhetherItsSectionIsOpen` checks names, role, states and the fired events in both states, and a plain name once the heading is not foldable.
- Focus painting itself is not asserted, because a headless test cannot hold focus.

**M1, a failed load.** A load that fails while the `LoadingPage` shows replaces its line with "Could not load: <message>" through `LoadingPage.showFailure`. Test: `AppControllerTest.aLoadThatFailsAfterTheLoadingPageShowedSaysSoThere`.

**M2, re-parenting.** The hierarchy listener ignores the event when the section's own parent becomes null. A section taken out and put back keeps what the user opened, as the instance page's encounter switch does. Test: `SourceSectionTest.takenOutAndPutBackASectionKeepsWhatTheUserOpened`.

**M3, rows given while collapsed.** `setCollapsed` ends with `updateLimit()`, and the cut no longer depends on the table's visible flag. Test: `SourceSectionTest.rowsGivenWhileCollapsedGetTheButtonOnOpening`.

**M4, the instance order.** `InstancePage` now adds its right-pane sections, Loot then Quests, through `SectionOrder.sort(SectionOrder.INSTANCE, …)`. The `SectionOrder` javadoc describes the two-pane layout. `InstancePageTest.questsSection` asserts the right pane follows `INSTANCE`.

Other tests added in this round: `aSectionThatOptsOutShowsEveryRow` and `aSectionLeftWithoutRowsStaysSoThroughCollapsing`.

Full `./mvnw -B verify` result (client build 70450 database), BUILD SUCCESS:

| Module | Tests | Failures |
|---|---|---|
| lua-table | 61 | 0 |
| importer | 431 | 0 |
| app | 876 | 0 |

The app count went from 867 to 876: 9 new tests and no existing expectations changed.

Not done: focus ownership and the painted focus ring are not asserted, because headless tests cannot hold the keyboard focus.
