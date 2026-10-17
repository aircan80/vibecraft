# Task 13 report: provenance markers and source filter

Status: DONE_WITH_CONCERNS (concerns are judgement calls, listed at the end; nothing is left unimplemented)
Commit: `10c9710 feat(ui): provenance markers and source filter` on `feature/phase2b-ui`

## What was implemented

- **Amber "≠" marker.** `Tokens.warn` was added as a new `Theme.warn` component: dark `#E0A83A`, light `#9A6B00`. It is also in both theme `.properties` files as `@warnColor`.
  - `RateBarRenderer` paints a marked rate in two steps. It fills the background, then the label UI paints the number moved left by the marker's width in the usual colour. `getText()` leaves the marker out during that paint only. The marker glyph is then drawn in amber with `FlatUIUtils.drawString`.
  - `getText()` still returns "60.00 % ≠", so the glyph stays and the existing text assertions and compact tooltips are unchanged.
  - When the text does not fit, the label cuts it short as before and the marker is not recoloured.
- **Rate source tooltips.**
  - New `model/RateSource(source, Double pct)` record, with `Key(npcId, itemId)`, `ORDER`, `label()` and `keys(npcId, loot)`.
  - `ItemRepository.sources(npcId, itemId)` is a parameterised query, plus `sources(Collection<Key>)` that reads many pairs on one connection.
  - `RateBarRenderer(Function<Object, List<RateSource>>)` and `install(column, sourcesOf)` set the cell tooltip from `sourcesText`. The format is "Questie 2.31 %  ·  AtlasLoot 1.10 %". A NULL pct reads "no rate", and no sources means a null tooltip.
  - The renderer only looks things up in an already-loaded map.
- **Pre-loading off the EDT.**
  - `DetailPanels.load` now fills `LoadedItem.rateSources` and `LoadedNpc.rateSources`. They cover the item's drops and its loot-list bosses that have an NPC id, and the NPC's drops. Only rows with `sourceCount > 0` are queried.
  - `InstancePage` loads the sources on the EDT inside the documented boss-loot exception. It goes through the new default `BossLoot.sources(...)`, and `BossLoot.of(repos)` backs it with the repository.
- **Source filter.**
  - New `model/SourceFilter` enum: `ALL`, `HIDE_ATLASLOOT_ONLY`, `HIDE_QUESTIE_ONLY`. It has `label()` and `keeps(origin)`. "both" and a null origin are always kept.
  - `UiState.sourceFilter()` and `setSourceFilter()` use the key `view.sourceFilter`. `ALL` removes the key, and a garbled value reads as `ALL`.
  - `MainView.sourceFilter()` defaults to `ALL`, and `MainFrame` returns the saved value. `AppController.showEntity` renders with `view.sourceFilter()` through the new `DetailPanels.render(..., filter)` overload.
- **Menus.** New `ui/WindowMenus.sourceFilterMenu(state, onChange)` builds the "Drop sources" submenu: three radio items, with the saved one re-selected whenever the submenu opens.
  - `MainFrame` adds it to the View menu and to the compact context menu, both with `this::rerender`. The existing inline rerender lambda was folded into that same method.
  - Choosing an item that is already selected does nothing.
- **Filter application.** New `ui/pages/SourceFilters` provides `apply`, `note` and `annotate`. `SourcesPage.addFilteredSection` wraps it, and `SourceSection.setNote()`/`note()` show a muted line under the table.
  - `ItemPage` filters Dropped by on `DropSource.origin`, and In instance loot on `InstanceRepository.BOSS_LOOT_ORIGIN`. The summary line is built from the filtered detail.
  - `NpcPage` filters Drops on `LootRow.origin`.
  - Headings count the rows kept. A note "n rows hidden by the source filter" (or "1 row ...") appears only when rows were hidden. A section whose rows were all hidden keeps its heading "(0)" and note, and hides its empty table.
- **InstancePage difficulty control.** It is unchanged. The filter does not apply to the instance page, as the rulings scope it to the item and NPC pages, and the existing two-difficulty test still passes.
- **Task 12 residual.** When a pin load fails, `ZoneMapPanel` now deselects Objects before reselecting Bosses.
  - It does this under a `reverting` guard, so neither toggle change runs `pinsChanged`, and then calls `showPins()` once.
  - The order alone was not enough. Either order starts a stray load without the guard: Objects-off first still sees All NPCs selected and unloaded.

## Tests and results

`./mvnw -B -q verify` passes with exit 0.

| Module | Tests run | Failures | Errors |
|---|---|---|---|
| importer | 336 | 0 | 0 |
| app | 660 | 0 | 0 |
| lua-table | 61 | 0 | 0 |

New or changed tests:
- `RateBarRendererTest`:
  - Pixel check that the marker region has amber pixels and no foreground pixels, while the number region has foreground pixels and no amber ones. It also checks that the text keeps "≠".
  - An agreed rate has no marker and no amber.
  - Both theme tokens have the right values.
  - `sourcesText` with two sources, a NULL pct, and none.
  - Per-row tooltip in a real `ResultsTableModel`.
- `ItemRepositoryTest`: `sources` order on fixture pair 639/2874, with all four real sources. Also the bulk version leaves out pairs without sources.
- `RateSourceTest` covers order and labels. `SourceFilterTest` covers `keeps` and labels.
- `UiStateTest`: round trip of `sourceFilter` and the garbled-value default.
- `ItemPageTest`, all three filters with fixture rows of every origin:
  - Counts, the note text, the muted note colour, and that a filter hiding nothing adds no note.
  - All loot lists hidden gives a "(0)" heading, the note and a hidden table, and the filtered summary.
  - Tooltips on Dropped by and In instance loot, read through `JTable.getToolTipText(MouseEvent)`.
- `NpcPageTest`: all three filters on each origin, all rows hidden, and the drop tooltip.
- `DetailPanelsTest`:
  - `LoadedItem` and `LoadedNpc` load sources only for rated rows.
  - The tooltip on the fixture item page.
  - `render` with a filter on NPC 639: "Drops (2)" and "3 rows hidden by the source filter".
- `InstancePageTest`: the boss-loot tooltip for VanCleef's "An Unsent Letter" shows all four sources. A `BossLoot` lambda without `sources` gives no tooltips.
- `WindowMenusTest`:
  - Items, labels and the saved selection.
  - Choosing a radio item through a real `AppController` with `Runnable::run` saves the filter and re-renders NPC 639 filtered, without a history entry. A repeat choice does nothing.
  - A menu reopened after the other layout's menu changed the filter shows the new one.
- `ZonePageTest`: a combined NPC+Objects failure puts both toggles back and queues no further load.
- `ThemeManagerTest`: the properties files contain the warn colours.

## TDD evidence

- The tests were written before the production code. The page, renderer and menu tests did not compile against the old API, which was the first red.
- **Marker test.** With `paintMarked` temporarily replaced by `super.paintComponent`, `theDisagreementMarkerAloneIsPaintedInTheWarningColour` failed on "[amber marker]". It passed once restored.
- **Zone residual.** With the `reverting` guard temporarily disabled, `aFailedLoadOfNpcsAndObjectsTogetherPutsBothTogglesBackWithoutLoadingAgain` failed on "[going back from a combined failure loads nothing]". It passed once restored.

## Files changed

- Main, new: `model/RateSource.java`, `model/SourceFilter.java`, `ui/WindowMenus.java`, `ui/pages/SourceFilters.java`.
- Main, modified: `db/ItemRepository.java`, `ui/AppController.java`, `ui/MainFrame.java`, `ui/MainView.java`, `ui/UiState.java`, `ui/detail/DetailPanels.java`, `ui/pages/BossLoot.java`, `ui/pages/InstancePage.java`, `ui/pages/ItemPage.java`, `ui/pages/LootTables.java`, `ui/pages/NpcPage.java`, `ui/pages/SourcesPage.java`, `ui/pages/ZoneMapPanel.java`, `ui/theme/Theme.java`, `ui/theme/Tokens.java`, `ui/widgets/RateBarRenderer.java`, `ui/widgets/SourceSection.java`.
- Resources: both `WowForever*Laf.properties`.
- Tests, new: `model/RateSourceTest`, `model/SourceFilterTest`, `ui/WindowMenusTest`.
- Tests, modified: `db/ItemRepositoryTest`, `ui/StubView`, `ui/ThemeManagerTest`, `ui/UiStateTest`, `ui/detail/DetailPanelsTest`, `ui/pages/{InstancePageTest, ItemPageTest, NpcPageTest, Tables, ZonePageTest}`, `ui/widgets/RateBarRendererTest`.

## Self-review

- **Completeness.** Every ruling is covered: amber marker on the glyph only, tooltip, pre-loaded sources with no EDT query outside the boss-loot exception, a persisted filter, both menus through one helper, rerender, counts and notes, the difficulty control, and the zone residual.
- **No EDT queries.** The renderer only reads the map. The item and NPC pages get their maps from `DetailPanels.load` on the worker. The instance page stays inside its documented on-EDT exception.
- **Discipline.** `MainFrame` cannot be tested headless, so its two `WindowMenus` call sites and the `sourceFilter()` override were checked by reading them. The behaviour itself is tested through `WindowMenus` and `AppController`.
- **Line endings.** All changed files use LF, as `.gitattributes` requires.

## Concerns

1. **Real source names differ from the brief.** The data has `questie-wowhead`, `questie-correction`, `atlasloot` and `atlasloot-override`, not just `questie` and `atlasloot`.
   - The tooltip keeps the fixed Questie-then-AtlasLoot order. Labels: `questie` and `questie-wowhead` read "Questie", then "Questie correction", "AtlasLoot" and "AtlasLoot override". Unknown sources come last, by name.
   - The shipped data has no NULL pct, but NULL is handled as "no rate".
2. **"Hide AtlasLoot-only rows" hides every "In instance loot" row.** Boss loot always has origin `atlasloot`, which is `InstanceRepository.BOSS_LOOT_ORIGIN`. That follows the ruling's "loot lists" literally.
   - The section keeps a "(0)" heading and the note, so the user can see that rows were hidden.
   - If the intent was to filter only Dropped by on the item page, remove the `lists` filtering in `ItemPage`.
3. **Pre-existing behaviour, not changed.** In the compact window, a hidden rate column renders with column -1, so its row tooltip text has no "≠". The new source tooltip still shows there.

## Fix round 1

Commit: `3beb4e6 fix(ui): Task 13 review: instance loot unfiltered, one source query per page`

1. **Instance loot is no longer filtered.** `ItemPage` filters only Dropped by. In instance loot is built from the unfiltered list with a plain `addSection`: no note, and the summary keeps "n loot lists". The unused `InstanceRepository` and `InstanceLoot` imports are gone, and the class Javadoc is fixed. The test is now `hidingAtlasLootOnlyDropsKeepsQuestieAndBothAndNeverTouchesTheLootLists`. It asserts "In instance loot (1)", no note, and "3 NPCs · 1 loot list · no vendor · no quests".
2. **One source query per page.** The per-pair loop `sources(Collection<Key>)` is replaced.
   - `ItemRepository.sourcesOfNpc(npcId, itemIds)` runs one query, `WHERE npc_id = ?` (the primary-key prefix). `sourcesOfItem(itemId, npcIds)` runs one query, `WHERE item_id = ?` (indexed).
   - Both return the map grouped by pair, each list in `RateSource.ORDER`. Pairs not asked for are left out, and an empty id list runs no query.
   - `DetailPanels.load` makes one call per item page (the rated drop NPCs and loot-list bosses) and one per NPC page (the rated drop items).
   - `BossLoot.sources(npcId, itemIds)` makes one query per boss selection. The InstancePage Javadoc is updated.
   - `RateSource.keys` is replaced by `RateSource.ratedItems`.
   - Tests: `anNpcsSourcesComeBackByPairForTheItemsAskedForOnly` (639: only 2874 and 5191 come back, though 5193 also has sources; NPCs and items without sources come back empty) and `anItemsSourcesComeBackByPairForTheNpcsAskedForOnly`. `InstancePageTest.aBossSelectionAsksForItsSourcesOnceForItsRatedItems` records exactly one call for VanCleef, with his five rated items.
3. **Light-theme amber pixel check.** The marker pixel test is now parameterised and paints under both `Tokens.DARK` and `Tokens.LIGHT`, then restores the theme.
4. **NPC group headings.** NpcPageTest now asserts the group headings and their counts under each filter. All shows "Gear (3)" and "Trade goods (1)". Hide AtlasLoot-only shows only "Gear (2)", because the emptied group goes. Hide Questie-only shows "Gear (2)" and "Trade goods (1)".
5. **Tidying.**
   - The AppController `rerender` and UiState class Javadocs are reflowed.
   - WindowMenusTest ends with a newline.
   - `GroupedRows.Entry.valueOf(row, type)` is a typed accessor that replaces the unchecked cast in `LootTables.grouped`.
   - The two no-sources helpers are now one constant, `LootTables.NO_SOURCES`, a `Function<Object, ...>`. The `sources` parameter of `grouped` takes `Function<? super LootRow, ...>`.
6. **Jump keys skip emptied sections.** `SourcesPage.jumpTargets()` holds the sections whose table is visible, and `bindJumpKeys` and `jumpTo` index into it. Test: `ItemPageTest.jumpKeysSkipASectionTheFilterEmptied`. Dropped by is emptied by Hide Questie-only, Ctrl+1 selects the first Sold by row, and Ctrl+2 is not bound.

Results: the covering tests pass. The full `./mvnw -B -q verify` exits 0 with no failures.

| Module | Tests run |
|---|---|
| importer | 336 |
| app | 664 |
| lua-table | 61 |

Concern 2 from the first round is resolved by the ruling. Concerns 1 (source labels) and 3 (compact-window marker, pre-existing) still stand.
