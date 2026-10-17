# Integration report: main (phase 2A) merged into feature/phase2b-ui

Status: DONE_WITH_CONCERNS. Merge commit `fb7a1ba Merge main (phase 2A client data) into feature/phase2b-ui`
(parents 0dc8772 and 3da3ab9). No follow-up commits. `./mvnw -B verify` is green for every module.

## Test counts (`./mvnw -B verify`, all green)

| Module | Tests |
|---|---|
| Lua table parser | 61 |
| Data importer | 336 |
| Browser application | 535 (2B had 485) |

`ShippedDataTest` ran all 19 checks against the shipped DB (65,966,080 bytes, client data present).
`git diff main -- app/src/main/resources/data` is empty. No conflict markers remain.

## Conflicts and how each was resolved

- **LootRow**: one record with the fields of both sides, in this order: 2B's `classId/className/subclassId`, then
  2A's `disagrees/sourceCount`. It keeps all three older constructors: the 6-argument one, 2B's 9-argument one and
  2A's 8-argument one.
- **SearchHit**: one record `(ref, name, subtitle, int count, Integer quality)` with 2B's validation. It keeps the
  3-argument constructor, `(…, int count)` and `(…, Integer quality)`, and adds `withCount(int)`.
- **SearchRepository**: keeps 2B's SQL, ranking, collapsing and `Labels` subtitles, and selects 2A's `i.quality`.
  `collapse` now uses `withCount`, so a collapsed item hit keeps its quality.
- **NpcRepository / InstanceRepository**: keep 2B's shared `ITEM_COLUMNS`/`itemJoin`/`loot` helpers and
  difficulty queries. They also read 2A's `disagrees` and `source_count` through a new `NpcRepository.ratedLoot`.
  It is used for NPC drops and boss loot, in every difficulty.
- **ItemRepository** (auto-merged, re-read): 2B's `list(ItemFilter)` with level bounds sits alongside 2A's
  card, quality, icon and provenance columns. Against main, the only removed line is the class Javadoc wording.
- **Columns**:
  - Keeps 2B's catalogues: `dropSourcesRanked`, `lootLists`, `lootRated`, `sourceNames`, `questGivers`,
    coordinate spawns and `Labels.instanceTitle`.
  - Keeps 2A's `DISAGREES`, `rateText`, `qualityLink`, the `Column` quality overloads and `items`/`searchHits`
    quality colours. Both `RATE` and `DISAGREES` stay.
  - The rate columns stay `Double`-valued, because 2B's tests and `RateBarRenderer` need the number. The "≠"
    marker reaches them through a new 7th `Column` component, `disagrees`, a per-row flag with
    `disagreesFor(row)`. A 6-argument overload keeps the existing call sites working.
  - `ResultsTableModel.disagreesAt` exposes the flag, and `RateBarRenderer` renders `rateText(pct, disagrees)`.
    The flag is set in `dropSourcesRanked`, `lootLists` and `lootRated`. The legacy string-valued `lootRows` keeps
    2A's `rateText`. Rate columns therefore show "≠" in every table.
- **GroupedRows.columns** used to rebuild columns with the 5-argument constructor. It now forwards both quality and
  the `disagrees` flag, so grouped NPC and instance loot tables keep the marker. A new `GroupedRowsTest` covers
  this.
- **LinkRenderer** (auto-merged, re-read): 2B's hover underline and plain text for no link, plus 2A's quality
  colour via `FlatLaf.isLafDark()`. Nothing from main was dropped.
- **QualityColors** (add/add): one class in `ui/theme` with `text(int, boolean)`, `canonical(int)` and 2B's
  `rateBand`. Its values are the UI review's quality table, which 2B already used, and both plans cite that table:
  - quality 0–7, heirloom included;
  - light Poor `#6B6B6B`;
  - `canonical` returns the in-game colours, for icon borders and the card.

  It takes 2A's rule that out-of-range qualities show as common. The two test classes are merged into one.
- **HeaderPanel**: 2B's version, with `LinkButton` and badges, plus 2A's `setTitleColor(Supplier<Color>)` and
  `titleColor()`. The title colour is read on every paint.
- **DetailPanels**:
  - Keeps 2B's page dispatch, plus 2A's `load` that decodes the icon and map off the Event Dispatch Thread.
  - The loader records are renamed `LoadedItem(ItemDetail, BufferedImage icon)` and
    `LoadedZone(ZoneDetail, BufferedImage map)`, because the names `ItemPage`/`ZonePage` clash with
    `ui.pages.ItemPage`/`ZonePage`. Pages ignore the icon and map for now.
  - `ItemPage` now shows 2A's quality-coloured title via `HeaderPanel.setTitleColor`. This keeps main's visible
    behaviour.
- **ItemDetailPanel / ZoneDetailPanel** (modify/delete): stay deleted. `TooltipLinesPanel`, `MapImagePanel` and
  `Images` moved to `ui/widgets` with their tests. `Images` and `Images.decode` became public, because
  `DetailPanels` now uses them from another package.
- **ItemDetailPanelTest / ZoneDetailPanelTest** (modify/delete): deleted. Their non-panel assertions moved:
  - into `DetailPanelsTest`:
    - a missing icon blob (1307) decodes to the fallback's pixels;
    - 17204 loads with a decoded icon, a quality-5 card and 5 lines;
    - Westfall's map is decoded in `load` at width 1002;
    - zone 2437 loads without a map;
    - each loader record renders its page;
  - into `TooltipLinesPanelTest`: the fixture card lines for 17204 in order, the green Use line, and no lines for
    1176;
  - into `ItemPageTest`: the title is in the quality colour for 17204 and plain for 1176; VanCleef's rate on 2874
    renders "60.00 % ≠" and stays the number 60.0.

  The remaining tests in those files were v0.1 tab-panel tests that 2B's page tests already replace. Main had
  changed only their subtitle text.
- **ItemRepositoryTest, NpcRepositoryTest, SearchRepositoryTest**: union of both sides' test methods.
- **LinkTableTest**: union of imports and tests. 2A's `disagreeingRatesAreMarkedInEveryDropTable` targeted
  `dropSources`, `bossLoot` and `instanceLoot`, which 2B replaced. It now asserts the rendered rate text on
  `dropSourcesRanked`, `lootRated` and `lootLists`, and the cell value on `lootRows`. 2A's quality-hit test now
  uses the 5-argument `SearchHit`, because `(…, "Item", 3)` resolves to the count overload.
- **ShippedDataTest, InstanceRepositoryTest** (auto-merged, re-read): every check from both sides is present.

## Test expectations changed by the richer fixture (never weakened)

- **`ItemRepositoryTest.listWithLevelRange`**: items of item level 8 or lower are now the exact list of 7, because
  client data gives levels to quest and consumable items.
- **`ListPageTest.resultsAreSortable`**: the descending item levels now start with 80, 66, 65, 60, 24.
- **`ItemPageTest.vanCleefLeadsTheDropsAtSixtyPercent`**: the rendered rate is now "60.00 % ≠", and the value is
  still 60.0.

## Assertions deferred to Part 2

- **Task 11** (card, icons): from 2A's `ItemDetailPanelTest`:
  - the icon is shown at 56 px beside the title;
  - the tooltip card sits inside the item page;
  - an item without an icon leaves the icon space hidden;
  - the fallback icon is visible on the page.

  The loading half of each is tested now.
- **Task 12** (maps): from 2A's `ZoneDetailPanelTest`:
  - the zone page shows a `MapImagePanel` holding the very image `load` decoded;
  - there is no map without one;
  - a map that did not decode shows nothing.

  The decode half is tested now.
- **Task 13** (provenance): the "≠" text now shows in every rate cell. The amber colour and the source tooltip
  are still to do, and should build on `Column.disagrees` and `ResultsTableModel.disagreesAt`.

## Concerns

1. **QualityColors values.** Three of 2A's expectations contradicted 2B and the UI review table:
   - light Poor `#9D9D9D`;
   - `canonical` equal to the dark text colour;
   - quality 7 treated as out of range.

   I resolved them to the spec table, so 2A's tests now assert the spec values. No main code called `canonical`.
   Please confirm this ruling.
2. **New `Column` component.** `Column` gained a 7th `disagrees` component. It is additive, and every existing
   constructor arity still works.
3. **`SearchHit` overloads.** `(…, int count)` and `(…, Integer quality)` are both 4-argument constructors. An int
   literal picks `count`, so new code should prefer the 5-argument form.
4. **Quality filter.** `ItemRepository.list(ItemFilter)` still refuses a quality filter, as the ledger ruled for
   Part 2. The quality column now exists in the DB.
5. **Main-only catalogues.** Main's `Columns.dropSources`, `instanceLoot`, `bossLoot`, `contentTypeText` and X/Y
   spawn columns are not in the merge. They are merge-base members that 2B had already renamed or replaced, not 2A
   additions.
