Needs fixes — spec ✅, quality ❌ (Critical 0, Important 2, Minor 7)

# Task 6 review: 22ce22d + d985c88 (merged Dropped by, section filter, dropdown kind chips, master-list auto-collapse, Notable Type column)

Scope covers both Task 6 commits: 22ce22d and d985c88 ("Notable drops say what they are in a Type column"). 807185b (Task 19) in the second review package is not reviewed here. Important 2 below comes from d985c88.

Reviewed `git show 22ce22d` and `git show d985c88` against task-6-brief.md, the plan's Global Constraints and Review Focus 5, the report and
the review diff. Merge-data claims were checked read-only against the shipped `wowforever.sqlite`. No Maven run, as
instructed; verify was reported green on 22ce22d (61 / 448 / 897).

## Spec compliance: ✅

- **DropRow.Loot and DropRow.merged.** Rows are ranked by rate descending, with ties by name, then instance, then slot. Unknown rates come after a "Rate unknown (n)" divider. There is no divider when only one group exists (`DropRow.java:145`).
  - The record component is named `list` instead of the brief's `loot`. The interface's `loot()` accessor already returns `Optional<InstanceLoot>`, so the rename is justified.
- **Instance chip.** `ChipCellRenderer` draws a `Chip.Style.PLAIN` "Molten Core" chip in the Source column. It wraps the column's renderer, so link colours still hold.
- **Merge preserves data.** A boss list merged into its NPC row keeps the instance chip, and the summary still counts it.
  - Shipped DB: all 2,267 merged pairs have the same `drop_pct`, `disagrees` and `source_count` in `boss_loot` and `item_npc_drop`. No rate or "≠" mark is lost today.
  - The 30 boss-loot rows whose boss NPC has no `item_npc_drop` row still appear as Loot rows of their own.
- **No database on the EDT.** The merge runs in the `ItemPage` constructor over the already-loaded `ItemDetail`. `changeSourceFilter` → `rerender` → `reshow` reuses the loaded detail.
- **SourceFilterControl.** The constructor and `LEGEND` match the brief exactly, and the tooltip equals `LEGEND`. The accessible name is "Drop sources".
  - The control sits beside "Dropped by" when NPC rows exist and beside "Drops".
  - A section the filter emptied keeps its heading, so the control stays reachable.
- **Filter scope.** The filter never hides loot rows. The note counts NPC rows only (`drops.hidden()`), and a section left with only loot rows is not emptied.
- **NpcPage Notable.** "Notable" leads `DROP_ORDER`. Per the ruling, `ofNpcDrop` puts quality ≥ 4 and class 12 / "Quest" there. Everything else goes through the unchanged `ofDrop`. See Minor 6 for the one epic pool item this moves.
- **Kind chips.** The toolbar has no `KindChips`. The chips are the dropdown's top row, shown with hits and hidden with recents. A click narrows the hits at once, then calls `onKindSelect`, which re-looks up and marks the list stale. Accessible names ("Items, 9 results") are unchanged.
- **ListVisibility.** It has `WIDE_PX = 1600` and all five brief behaviours, exactly as their tests state. Ctrl+B pins and saves `pinned()` to `UiState.listVisible`, which defaults to false per the ruling.
- **AppController.**
  - `commit` and `openHit` call `onResultOpened` (`AppController.java:215`, `:237`), and `preview` never does. Review Focus 5 is met.
  - `MainFrame.onResultOpened` passes `getWidth()`, the real frame width at open time, and is ignored in compact mode.
  - `onResultsShown` fires only for a search that fills the list without an opened hit.
- **Menu removal.** The View › Drop sources menu is gone, with `sourceFilterMenu`, `DROP_SOURCES`, `Actions.rerender` and the unused imports. `MainFrame.rerender` stays because the layout switch still uses it. No dead code is left from the menu.
- **Named tests.** Every test the brief names is present:
  - `ItemPageTest`: `droppedByMergesInstanceLoot`, `filterNeverHidesInstanceRows`.
  - `SourceFilterControlTest.changesFilterAndRerenders`. Its tooltip check sits in a sibling test of the same class.
  - `NpcPageTest.notableDropsFirst`.
  - `SearchBarTest.noKindChipsInToolbar` and `SearchDropdownTest.kindChipsInDropdownFilter`.
  - The five `ListVisibilityTest` cases.
  - `AppControllerTest.commitCollapsesPreviewDoesNot`.
  - The modified `KindChipsTest`, `MainFrameContentTest` and `WindowMenusTest`.

### Changed expectations: honest

Each change follows from the brief or a ruling, and the report lists them all.

- **ShippedDataTest and NpcPageTest:** "Notable (n)", per the ruling. World drops (23) and the row count are unchanged.
- **ItemPageTest and DetailPanelsTest:** "In instance loot" folds into "Dropped by". 16800 under Hide AtlasLoot-only now reads "Dropped by (1)", with its boss list as a Loot row and "1 row hidden…".
- **UiStateTest:** the `listVisible` default is now false, per the ruling.
- **MainFrameContentTest:** the existing toggle and divider tests pin the list first.
- **WindowMenusTest:** the two menu tests were removed, and the VanCleef assertions now drive the page control.
- **CompactShellTest and LayoutSwitchTest:** the chips are never under the search bar.
- **SearchDropdownTest:** the height includes the chip row.
- **One weakening worth noting:** `lootListsOfTheSameNameKeepTheirSlotOrder` now accepts two identical "Armory trash" rows. The 2B test had pinned their "· slot n" disambiguation. See Minor 2.

## Code quality: ❌

### Important

1. **Esc can no longer reach a collapsed master list.** Locations: `MainFrame.java:340` `escape()`, and `LayoutSwitch.java:121` `escapeTarget()`.
   - **What is wrong.** In the full layout, `escapeTarget()` always returns `parts.list().jlist()`. After this task, the list is hidden after every opened result unless pinned or the window is ≥ 1600 px. So once the search box has nothing to close or clear, Esc calls `requestFocusInWindow()` on an invisible list. The call fails silently, and Esc does nothing.
   - **Why it matters.** The help table and README promise "Esc: close dropdown, then clear search, then focus list". The broken state is now the default state after opening any result. A keyboard user has no way back to the results except Ctrl+B, which permanently pins the list and saves that pin, or re-running the search.
   - **Related focus problem.** Enter in the list (`commit`) hides the list while it holds the focus. Where the focus lands is left to AWT's auto-transfer on hide. No code moves it into the opened page, and no test covers it.
   - **Fix.**
     - In `escape()`, when the full layout's list is hidden, run `updateList(content, ListVisibility::resultsShown)` before focusing it. That shows the list without pinning it, matching the documented chain.
     - In `onResultOpened()`, if the focus owner is inside the list being hidden, request focus on `focusTargetIn(detailHolder's page)`.
     - Pin both with a `MainFrameContentTest` case: the list collapsed, then Esc → visible, unpinned, `escapeTarget` showing.
2. **d985c88: the Type column calls 40 epic and legendary boss drops "Junk".** Location: `ItemCategories.java` `notableType`, at `default -> group; // Gear, Junk, Other`.
   - **What is wrong.** `notableType` maps a Notable row through `of(className)`, and `of` sends every class other than Weapon, Armor, Recipe, Quest, Trade Goods, Reagent and Consumable to `JUNK`. In the shipped DB, 40 NPC-dropped items of quality ≥ 4 have class "Miscellaneous". Each one now reads "Junk" in the Type column, on the very rows Notable exists to highlight. Checked read-only:
     - Swift Zulian Tiger 19902 (epic, High Priest Thekal 14509, 2 %).
     - Bindings of the Windseeker 18563 and 18564 (legendary, Baron Geddon 12056 and Garr 12057, 3 %).
     - Desecrated Breastplate 22349 and the other Naxxramas tier tokens (epic, Thane Korth'azz 16064 and others).
   - **Why it matters.** "Junk" means grey vendor trash to a WoW player. Printing it beside a legendary or a mount in a research tool is wrong and erodes trust. Before d985c88 the "Junk" heading was already questionable for these items. The Type column now repeats the mislabel row by row on the most-viewed boss pages. The test only covers "Quest item" and "Gear", so nothing catches this.
   - **Fix.** For Notable rows, do not fall back to the group name for unmapped classes. Use the item's own class name ("Miscellaneous") or, better, a subclass-aware label ("Mount", "Token", "Reagent"). At minimum, map `JUNK` and `OTHER` to the raw `className`, or to blank.
     - Add a `NpcPageTest` row with an epic Miscellaneous item, or a shipped-data assertion on High Priest Thekal 14509, so its Type cell does not read "Junk".

### Minor

1. **`DropRow.merged` can silently drop data on shapes the shipped DB does not have yet.** Location: `DropRow.java:150–155`.
   - **What is wrong.**
     - `findFirst()` merges one list per boss NPC. The exclusion filter `!npcs.contains(l.bossNpcId())` then discards every further list of that boss: a boss in two instances or lists, or the AtlasLoot Forever lists T19 brings in.
     - On a merged row, the list's own `dropPct`, `disagrees` and `sourceCount` are never read. The Rate column uses the NPC's rate and the NPC's disagree flag only.
   - **Why it matters.** Today both are no-ops: the shipped DB has 0 bosses with two lists for one item, and every merged pair agrees exactly. T19 replaces the AtlasLoot source, though, and a later mismatch would vanish without a trace.
   - **Fix.**
     - Merge only the first list, and keep the others as `Loot` rows. The filter should exclude only the list actually merged.
     - When the merged list's rate differs from the NPC's, keep it as its own row, or OR its `disagrees` into the row's mark.
     - Add a unit test for each case.
2. **Identical loot rows read the same.** Location: `ItemPageTest.lootListsOfTheSameNameKeepTheirSlotOrder`.
   - **What is wrong.** Two rows of the same list with different slots now render as two identical "Armory trash" rows with identical links.
   - **Why it matters.** The shipped DB has none today, so the impact is small.
   - **Fix.** Either collapse duplicate (boss, item) rows into one, or keep a "· slot n" suffix only when two rows would read the same, as `LootListRow` did.
3. **Dead code from the removed "In instance loot" section.**
   - **What is wrong.** `LootListRow` and `Columns.lootLists()` (`Columns.java:242`) have no main-code caller now; only `LinkTableTest` uses `lootLists()`. `DropRow.ranked` and `Columns.dropSourcesRanked()` (`Columns.java:214`) also survive only for tests.
   - **Fix.** Delete `LootListRow` and `lootLists()` and move their test to the merged columns. Either inline `ranked` and `dropSourcesRanked` into the tests' callers, or mark them as test conveniences.
4. **README is stale.**
   - **What is wrong.** Line 35 says "the kind chips beside the box". Line 77's "Drop sources filter" section still says "View › Drop sources … Instance loot is never filtered".
   - **Why it matters.** Both describe UI that no longer exists, and no later plan task names these paragraphs.
   - **Fix.** Say that the chips are the dropdown's top row, and that the filter is the control beside the Dropped by / Drops headings, with the "≠" legend in its tooltip. Say that dungeon loot lists in Dropped by are never filtered.
5. **The kind chips are still not keyboard-reachable.** Location: `KindChips.java:51` `setFocusable(false)`.
   - **What is wrong.** The chips now live in a non-focusable `JWindow`. In the full window they are also outside the main window's accessibility tree. The full window has no keyboard way to pick a kind.
   - **Why it matters.** This is unchanged from 2B, where the toolbar chips were not focusable either. The compact window's `KindMenu` stays keyboard-usable, and the accessible names are unchanged.
   - **Fix, if wanted.** Bind a key in the text field's input map while the dropdown shows hits, for example Tab / Shift+Tab to cycle the chip, just as Up and Down are bound only while it is open. Declare it in `KeyMap.BINDINGS`, the help table and the README.
6. **The "World drops rule unchanged" comment is slightly overstated.** Location: `ItemCategories.java:76`.
   - **What is wrong.** The comment says "Quest items are never world drops, so the World drops rule is the same". That holds for quest items, but an epic pool-class item now leaves World drops for Notable.
   - **Why it matters.** The shipped DB has exactly one such item: Pattern: Invulnerable Mail 12728, an epic recipe with 21 droppers. Grouping it under Notable is correct per the ruling.
   - **Fix.** Reword the Javadoc and the report line so they do not claim the rule is untouched.
7. **A chip cell has no accessible name.** Location: `ChipCellRenderer.java:61`.
   - **What is wrong.** For rows with a chip, the renderer component is a bare `JPanel` with no accessible name. A screen reader then reads nothing for the Source cell, and the instance is never announced. Before this task, the plain `LinkRenderer` label carried the text.
   - **Fix.** Override `getAccessibleContext()` in `Cell` to name it "<text>, <instance>". Assert that name in `aBossListMergesIntoItsNpcRowWhichShowsTheInstance`.

### d985c88: other checks (all pass)

- **Notable rows keep their quality colour.** `notableDropsFirst` asserts this for epic and legendary rows.
- **Type column placement.** It sits after Item, with muted text that reuses the Classes renderer and its contrast. Non-Notable rows are blank.
- **Column visibility.** `VisibleColumns` drops the column when every cell is blank, so a page without Notable rows has no Type column. The same is true when all rows are Notable with the same type. Type is not in `HIDDEN_WHEN_UNIFORM`, so a mixed table keeps it.
- **Grouping API.** The extra component in the `Grouping` record has a five-argument constructor kept for older callers, and nothing else changes.
- **Changed expectation.** `columnsEmptyOrUniformForEveryRowAreHidden` now expects "Item, Type, Item level, Rate, Origin" on VanCleef's drops, with the renderer indices shifted by one. This is honest: VanCleef's quest item An Unsent Letter is Notable.
- **Shipped-data tests.** `ShippedDataTest` reads column 0 only, so it is unaffected.
- **Minor 6 still applies.** The `ofNpcDrop` Javadoc still says the World drops rule is the same, and d985c88 did not touch it.

### Observations (no action required)

- **Felheart Belt test fixture.** `droppedByMergesInstanceLoot` uses a hand-built Felheart Belt (3 NPCs plus a hypothetical Ragnaros list). The shipped 16806 has 15 NPC rows and only the Molten Core "Trash" list, with no boss row. A shipped-data assertion on 16806 would pin the real page: a "Trash" row with the Molten Core chip after the divider.
- **Pins saved under the old meaning.** `UiState.listVisible` keeps its key. Users who saved `true` under the old meaning ("shown") are now pinned and never see auto-collapse. The report states this, and it follows the ruling.
