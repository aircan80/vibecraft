# Task 11 report: Tooltip card, icons and quality colours everywhere

**Status:** DONE_WITH_CONCERNS (concerns are scope notes, not defects; see the end)
**Commit:** `25a777b feat(ui): tooltip card, icons and quality colours` on `feature/phase2b-ui`
**Verification:** `./mvnw -B -q verify` exit 0. App 588 tests (545 before), importer 336, 0 failures, 0 errors.

## What was implemented

### Data the brief assumed but the code lacked (resolved, not escalated)
The dispatch said `SearchHit.iconId()` existed and that loot rows could carry quality. Neither was true on the branch:
- `SearchHit` gains an `iconId` component (6-arg canonical). The 5-arg form stays as a constructor; `withCount` keeps the icon. `SearchRepository` selects `i.icon_id`.
- `LootRow` gains `quality` and `iconId` (13-arg canonical). The 11-arg form stays as a constructor. `NpcRepository.ITEM_COLUMNS` adds `i.quality, i.icon_id`; `loot()` and `ratedLoot()` carry them. This follows the Task 6 precedent of extending `LootRow` through the shared loot queries. NPC drops and wares, object contents, quest rewards and boss loot all get them.

### `ui/widgets/IconCache` (new)
- `icon(Long id, int size)` reads the PNG through `AssetRepository::icon` on the calling thread, decodes with `Images.decode`, and scales with bicubic interpolation. Large reductions halve in steps first.
- LRU of `CAPACITY = 2_000` (id, size) entries in an access-ordered `LinkedHashMap`, synchronised.
- A null or missing id falls back to id 0. Without id 0 it returns `empty(size)`, a transparent icon that is the same instance per size. Misses are cached too.
- `cached(id, size)` returns an `Optional` and never reads.
- `warm(ids, size)` loads a batch and is meant for workers.
- `forRenderer(id, size, component)` is for renderers. It returns the cached icon, or the empty icon while the cache's own loader fetches the miss and then repaints the component. The loader is a single low-priority daemon thread, and each key loads once. A failed load caches the empty icon so it is not retried on every paint.
- `shared()` / `install(cache)` is the app-wide instance. It defaults to a cache with no icons and no loader. `Main` installs `new IconCache(repos.assets())` before creating the `AppController`.
- Size constants: `ROW = 18`, `LIST = 28`, `CARD = 56`, `WARM_ROWS = 200`.

### Where icons are pre-warmed (all on worker threads)
| Page or list | Where | What |
|---|---|---|
| Item page | `DetailPanels.load` (already) | The 56 px card icon is decoded as `LoadedItem.icon`, with the question mark for items without one. `ItemPage` wraps it in an `ImageIcon`. |
| NPC page | `DetailPanels.load` | Drops and wares at 18 px. |
| Object page | `DetailPanels.load` | Contents at 18 px. |
| Quest page | `DetailPanels.load` | Rewards at 18 px. |
| Instance page | `DetailPanels.load` | The loot of the boss the page shows on opening, at 18 px. That is the first main encounter (else the first extra list) in the first difficulty, mirroring `InstancePage.selectBoss(0)`. Other bosses load on the EDT, which is the documented exception, so their icons come through `forRenderer`'s background fetch. |
| Item list pages | `ListRows.load` (also used for refilters) | The first `WARM_ROWS` (200) rows at 18 px. Later rows of a 21.5k list load in the background when first painted. |
| Master list search | `AppController.search` via `ListEntries.warmIcons` | Item hits (up to 200) at 28 px. |
| Search dropdown | `SearchBar.Source.of(...).lookup` via `ListEntries.warmIcons` | Item hits (up to 8) at 28 px. |

### Renderers
- **Columns.** `Column` gains an `icon` component, a `Function<T, Long>` where null means no icon. A 7-arg constructor keeps old call sites. `hasIcon()` and `iconFor(row)` are added, plus `ResultsTableModel.iconAt`.
- **Item columns.** `Columns.items()` Name, `searchHits()` Name (items only) and the `lootRated()`/`lootRows()` Item column are item link columns. They take a quality colour and an icon, with id 0 for items without client data. The loot Item column is now quality-coloured too. `GroupedRows.columns` passes the icon through and gives headings none.
- **`LinkRenderer`.** It sets the 18 px icon from `IconCache.shared().forRenderer(...)` for icon cells and clears it on every other cell. It also sets a 4 px icon-text gap. Names keep `QualityColors.text(q, FlatLaf.isLafDark())`.
- **`MasterList.EntryRenderer`.** This renderer serves both the master list and the dropdown hits. It shows a 28 px icon WEST of the two text lines for ITEM entries; the question mark appears when there is no icon, and other kinds hide the icon. The title uses the quality text colour when it is not selected. `ListEntries.of(hit)` now passes `hit.quality()`/`hit.iconId()`, which closes the merge review's "SearchHit.quality loaded but unused" minor.

### `ui/widgets/ItemCardPanel` (new) and `TooltipLinesPanel` (reworked)
- **`TooltipLinesPanel` stays the single owner of line logic.** It handles colour parsing, wrapping and the slot split.
  - Lines are `LineText` (a package-private `JTextArea`). It is read-only, transparent, unfocusable, has no highlighter and uses the `Label.font`, kept across `updateUI`. It word-wraps with no HTML and keeps the exact stored colour.
  - Tab-split lines are a `BorderLayout` row with left in CENTER (it truncates first, with a tooltip) and right in EAST, TRAILING.
  - A non-caching stack layout computes each line's height for the current width through the text view, without touching bounds. Heights therefore follow every layout's width.
  - New accessors: `parts()`, `texts()`, `colours()`, `naturalWidth()` and `heightFor(width)`. They replace `labels()`, whose only callers were its own tests.
  - A package-private unframed constructor serves the card.
- **`ItemCardPanel`** lays out the card as follows.
  - It is custom-painted on both themes with a navy `BACKGROUND` (#0C1424), a 1 px `BORDER` (#46587A) line and `RADIUS` 4 rounded corners. It is non-opaque so the corners show the page.
  - The 56 px icon sits at the top left. Beside it is a column with the name (bold, +1 pt, `QualityColors.canonical(quality)`), then the other lines. The name comes from the `name` line, else from the given display name.
  - Width is the natural width capped at `maxWidth()` (400 logical px). Below that width, long lines wrap.
  - `getPreferredSize` computes the height for the laid-out width. A width change revalidates once, so the page picks up the new wrapped height.
- **`source_note` lines** (muted "Stats from Classic Era data") render as ordinary lines in their stored colour.

### `ItemPage`
- There is a new constructor `ItemPage(ItemDetail, BufferedImage icon, Navigator)`. The 2-arg form shows an empty icon. `DetailPanels.render` passes `LoadedItem.icon()`.
- When the item has tooltip lines, the card goes at the top in a holder with the page margins. The holder sizes the card to `min(preferred, page width)`. Then come the header with its title row hidden (the card has the name), and then the summary line and the sections.
- Without tooltip lines, the page shows the existing header unchanged. This covers items with no client data and icon-only items such as 212347.
- Ctrl+1 to Ctrl+6 and the sections are untouched. `HeaderPanel` gains `setTitleShown`/`isTitleShown`.

## Testing and TDD evidence
- **`IconCacheTest`** has 12 tests: scale to size, size cache hit (one read), `cached` never reads, fallback to 0 for missing or null ids, missing-0 gives a stable transparent empty icon (miss cached), LRU eviction, `warm`, background fetch with one load and one repaint, no retry after a failed load, no loader stays empty, fixture `AssetRepository`, and `install`/`shared`.
  - Red: with `IconCache.java` moved aside the test failed to compile. The first green run had one failing assertion, a test bug: JLabel's own `repaint()` calls were counted, so the test now measures the delta.
- **Repository tests.** Added `NpcRepositoryTest.lootRowsCarryTheItemQualityAndIcon`, `InstanceRepositoryTest` (rated boss loot keeps quality and icon) and `SearchRepositoryTest.itemHitsCarryTheirIconOtherHitsDoNot`. These were red by compile, since the accessors were missing.
- **Table renderers, in `LinkTableTest`.** Item list rows show the 18 px cached icon and quality colour, with the question mark for no client data. Loot rows are quality-coloured with their icon. Only item search hits get an icon. An uncached icon renders empty and never reads; the source throws if read. Other link columns show no icon.
  - `GroupedRowsTest`: grouped icon columns, with none on headings.
- **Master list and dropdown.** `MasterListTest` covers an item row's 28 px icon and quality colour (selection colour when selected), the question mark versus no icon for an NPC, and an uncached icon rendering empty without a read. `SearchDropdownTest.anItemHitShowsItsIconAndQualityColour` covers the dropdown.
- **`TooltipLinesPanelTest`** was rewritten for the new accessors. It adds a wrap test (at 160 px the line spans more than two lines, keeping its colour) and a slot CENTER/EAST test.
- **`ItemCardPanelTest`** has 8 tests:
  - lines in order with their stored colours and the name in canonical orange
  - icon beside the name with the lines below
  - icon present at 56 px
  - slot split with the right text at the right edge, using 19682's real slot line
  - wrapping inside a 260 px card with every line fitting
  - a short card narrower than the maximum
  - `source_note` rendered as a line
  - fallback name
  - navy and border pixels plus transparent corners on FlatDark and FlatLight (painted with `printAll`)
- **`ItemPageTest`.** `cardShown` checks the card, its name colour, its lines and the 56 px icon. It also checks that the card is above the header facts line, which is above the summary, and that the card is at most 400 px. Also added: `cardShownWithoutAnIconShowsAnEmptyOneOfTheSameSize`, `cardAbsentWithoutClientData` (1176 and 212347) and `theJumpKeysStillReachTheSectionsUnderTheCard`.
- **Pre-warm tests.** These were red first: all 5 failed on the "warmed" assertion before the pre-warm code existed.
  - `DetailPanelsTest`: NPC 639 drops and RFC's first boss.
  - `AppControllerTest`: a search warms item hits and carries quality and icon, and the item list warms its first rows.
  - `SearchBarTest`: the dropdown lookup warms.
- **Visual check.** A throwaway headless render test, deleted before the commit, painted the item pages for 17204 and 19682 and NPC 639 at 700 and 300 px on both themes. The card, the wrapping, the slot split and the 18 px icons in the grouped drop table all looked right. No real window was launched.

## Files changed
- **New:** `ui/widgets/IconCache.java`, `ui/widgets/ItemCardPanel.java`, `IconCacheTest`, `ItemCardPanelTest`.
- **Modified main:** `Main`, `db/NpcRepository`, `db/SearchRepository`, `model/LootRow`, `model/SearchHit`, `ui/AppController`, `ui/Columns`, `ui/LinkRenderer`, `ui/ResultsTableModel`, `ui/SearchBar`, `ui/detail/DetailPanels`, `ui/detail/HeaderPanel`, `ui/pages/GroupedRows`, `ui/pages/ItemPage`, `ui/pages/ListRows`, `ui/widgets/ListEntries`, `ui/widgets/MasterList`, `ui/widgets/TooltipLinesPanel`.
- **Modified tests:** `InstanceRepositoryTest`, `NpcRepositoryTest`, `SearchRepositoryTest`, `AppControllerTest`, `SearchBarTest`, `DetailPanelsTest`, `LinkTableTest`, `GroupedRowsTest`, `ItemPageTest`, `MasterListTest`, `SearchDropdownTest`, `TooltipLinesPanelTest`.

## Self-review
- **EDT discipline.** No renderer reads the database. `LinkRenderer` and `EntryRenderer` only call `forRenderer`, and the misses load on the icon-loader thread. Tests prove an uncached icon never reaches the source. The card icon is decoded in `load`; the EDT only wraps it, since the scale is a no-op at 56×56 ARGB. One exception remains: instance boss loot loads on the EDT for bosses after the first. That was already documented, and its icons arrive through the background fetch.
- **Both themes.** The card keeps its tokens through `updateComponentTreeUI`, and a pixel test covers FlatDark and FlatLight. Table and list name colours use `FlatLaf.isLafDark()` per paint. `LineText` re-derives its font on `updateUI`, so text-size changes apply.
- **Width safety.** The card never forces the page wider. Its minimum is icon plus padding, and the holder clamps it to the page width.
- **Line endings.** Some working copies were CRLF from checkout. Every edited file is LF (repo `eol=lf`), and the diff shows no whole-file churn.

## Concerns / judgement calls for the reviewer
1. **The header stays under the card as a facts line.** The ruling reads "the card at the top … otherwise the existing header". I kept the header's subtitle line ("Item #id · class · iLvl · Req" and the Starts-quest link) under the card, with only its title row hidden. Dropping it would lose the item id and the starts-quest link for items with client data.
2. **A background-fetch path beyond the ruling.** The ruling has renderers only read the cache, and they still only read it. But pre-warming every row of a 21.5k-item list, or every instance boss loaded on the EDT, is not feasible. So `forRenderer` schedules a single background load for a miss and repaints when it lands. Pre-warm covers what a page shows first.
3. **ItemSource tables have no icons or quality colours.** These are quest "Related items" and the item page's container items in "Contained in". `ItemSource` has no quality or icon, and they are not in the ruling's list (loot tables, drops, instance loot, list pages). Adding them means extending `ItemSource` and two queries (`ItemRepository.sources`, `QuestRepository` related items). This is a small follow-up if wanted.
4. **Icon sizes are logical pixels, not HiDPI-aware.** `UIScale` is not applied and there is no multi-resolution image. At 150 % Windows scaling the JRE upscales the 18/28 px images, so they may look soft. A `BaseMultiResolutionImage` could be added later in `IconCache`.
5. **Common-quality loot names are no longer link-blue.** An Unsent Letter now renders in the common text colour (white or near-black), as the quality ruling implies. The hand cursor and hover underline still mark the link.
6. **Changed signatures.** `TooltipLinesPanel.labels()` was replaced by `parts()`/`texts()`/`colours()`; it had no production callers. `LootRow` and `SearchHit` gained record components; the old constructors are kept.

## Fix round 1 (commit `9c80508`)

**Verification:** `./mvnw -B -q verify` exit 0. App 593 tests (was 588), importer 336, 0 failures. The build output no longer shows the IconCache warning.

1. **(Important) Quest-giver item rows now get the item quality and icon.**
   - `QuestGiver` gains `quality` and `iconId`; the 3-arg constructor is kept.
   - The giver query selects `i.quality, i.icon_id`. The item join is already limited to `kind = 'item'`, so NPC and object givers stay null.
   - `Columns.questGivers()` Name is an item link: quality colour, plus an 18 px icon for ITEM refs (the question mark without one) and no icon for NPCs or objects.
   - `DetailPanels.load` warms the item givers' icons for the quest page.
   - Tests:
     - `QuestRepositoryTest.itemGiversCarryTheItemQualityAndIcon` uses a fixture copy with an item giver row, since no fixture item giver has client data.
     - `QuestPageTest.itemGiversShowTheirIconAndQualityColourOtherGiversDoNot` covers the epic colour with its cached icon, the question mark for an item without client data, and an NPC with no icon in the link colour.
     - `DetailPanelsTest.aQuestPageWarmsTheIconsOfItsItemGivers`.
2. **Expected-failure warning silenced.** `simplelogger.properties` sets the IconCache logger to `error`, following the AppController precedent. Production still logs the warning with its trace.
3. **Every repaint target is kept.** `IconCache` keeps an identity set of repaint targets per loading key and repaints all of them when the icon lands. Test: `everyComponentThatAskedForAMissingIconIsRepaintedOnceItIsIn`, which checks two components, one load, and each repainted once.
4. **Card icon scaling moved off the EDT.** `DetailPanels.load` now scales the card icon to 56 px ARGB on the worker. `ItemPage` wraps the given image as is. `DetailPanelsTest` asserts the 56×56 ARGB size.
5. **Name colour without a quality.** A card without a quality colours its name with the stored `name` line colour, through `TooltipLinesPanel.colour`, now package-private so it remains the single parser. It falls back to white. Test: `withoutAQualityTheNameKeepsItsStoredColour`, which was red before the change.
6. **Test polish.**
   - The mojibake "Armor Â· Cloth" is fixed. The cause was my edit helper reading stdin in the Windows code page; it now decodes UTF-8, and no other `Â` remains in the sources.
   - Inline fully qualified names are replaced with imports in IconCacheTest, LinkTableTest (including the older `java.util.Optional`), MasterListTest and ItemPageTest (including the older `javax.swing.JLabel` cast).
   - The master-list row-height test now asserts the item row's preferred height is at least 28 plus its insets, and the fixed cell height is at least that.
