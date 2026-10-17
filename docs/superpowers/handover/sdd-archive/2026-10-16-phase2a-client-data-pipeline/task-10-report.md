# Task 10 report: minimal app consumers

Status: DONE. The lead chose option B, so the shipped database was regenerated with `--skip-client-data` and
committed as a follow-up chore commit. `./mvnw -B verify` is green, and `ShippedDataTest` passes against the regenerated
file. The sections below the "Shipped database regeneration (option B)" heading supersede the first-round
concern 1.

## What I implemented

### Model (`app/.../model`)
- `ItemSummary` gains `Integer quality, Long iconId`.
- New `ItemCard(Integer quality, List<TooltipLine> lines, Long iconId, Integer inventoryType, Integer bonding,
  Long sellPrice)` with `ItemCard.none()` for items without client data.
- New `TooltipLine(String kind, String text, String colour)`.
- `ItemDetail` gains `ItemCard card`, which is never null.
- `DropSource`, `LootRow` and `InstanceLoot` gain `boolean disagrees, int sourceCount`.
- New `ZoneMap(long uiMapId, int width, int height, byte[] jpeg)`. `ZoneDetail` gains `Optional<ZoneMap> map`.
- `SearchHit` gains `Integer quality`, which is null for non-items and for items without client data.
- Every changed record keeps a constructor with its old arity, meaning "no client data" or "no provenance".
  Existing tests compile unchanged and the parallel UI branch can rebase without touching its call sites.

### Repositories (`app/.../db`)
- `ItemRepository.list` and `detail` select `quality` and `icon_id`.
- `detail` builds the card. It reads `quality, icon_id, inventory_type, bonding, sell_price` when
  `has_client_data = 1` and reads `item_tooltip_line` ordered by `ordinal`. Otherwise it returns `ItemCard.none()`.
- The dropped-by and instance-loot queries carry `disagrees` and `source_count`.
- `NpcRepository` drops and `InstanceRepository.bossLoot` carry `disagrees` and `source_count`. Vendor, reward and
  object lists stay false and 0.
- `SearchRepository` selects `i.quality` into `SearchHit.quality`.
- New `AssetRepository` has three members:
  - `icon(long id)` returns `Optional<byte[]>`.
  - `iconOrFallback(Long id)` returns the item's own icon. When the id is null or the blob is missing it returns
    icon 0. When icon 0 is missing too it returns empty.
  - `FALLBACK_ICON_ID` is 0.
- `ZoneRepository.map(long zoneId)` joins `zone.ui_map_id` to `zone_map`. `detail` includes the map.
- `Repositories` gains `assets` as its last component.

### UI
- New `ui/theme/QualityColors` has `text(int quality, boolean dark)` and `canonical(int quality)`.
  - It uses the brief's table, with the dark variant first and the light variant second.
  - Out-of-range qualities clamp to common (1).
  - `canonical` returns the dark-theme text colour.
- `Columns.Column` gains an optional `Function<T, Integer> quality` component plus `qualityFor(row)`. The old 4-arg
  and 5-arg constructors still exist. `ResultsTableModel.qualityAt(row, col)` was added.
- `LinkRenderer` paints a linked cell that has a quality in `QualityColors.text(q, ThemeManager.isDark())`, looked
  up per paint. Cells without a quality keep the link colour.
- `Columns.items()` and `Columns.searchHits()` name columns are quality-coloured.
- New `Columns.rateText(pct, disagrees)` appends `Columns.DISAGREES` (`" ≠"`). The Drop % columns of
  `dropSources`, `instanceLoot`, `lootRows` and `bossLoot` use it. `NUMERIC_TEXT` still sorts marked rates by their
  number.
- `HeaderPanel.setTitleColor(Supplier<Color>)` and `titleColor()` were added. The colour is asked on every paint,
  so a theme toggle recolours the title.
- New `TooltipLinesPanel` stacks one `JLabel` per stored line, in stored order.
  - Each label takes the line's `#rrggbb` colour, or `DEFAULT_TEXT` (white) when the colour is null or malformed.
  - A tab splits a line into left and right labels, as in `Chest\tCloth`.
  - Each label's tooltip shows the full line text.
  - The panel has a zero minimum size, so long lines truncate instead of widening the detail area.
  - It is hidden when there are no lines.
- New `MapImagePanel(ZoneMap)` scales the map to the panel width and keeps the aspect ratio. It never enlarges the
  map beyond its native size.
  - The preferred height follows the width and revalidates on resize.
  - It has a zero minimum size.
  - The JPEG is decoded lazily on first use. A map that does not decode paints nothing.
- New package-private `Images.decode(byte[])` decodes in memory through `MemoryCacheImageInputStream`, without
  ImageIO's disk cache.
- `ItemDetailPanel(ItemDetail, BufferedImage icon, Navigator)` is the new constructor. The old `(ItemDetail,
  Navigator)` constructor delegates with no icon.
  - A 56 px icon label (`ICON_SIZE`) sits left of the header. Its icon is null when there is none, so nothing is drawn.
  - The title is quality-coloured when the card has a quality.
  - The `TooltipLinesPanel` sits under the header.
- `ZoneDetailPanel` adds a `MapImagePanel` between the header and the tabs when the zone has a map. `map()` returns
  `Optional<MapImagePanel>`.
- `DetailPanels.load` returns `DetailPanels.ItemPage(ItemDetail detail, BufferedImage icon)` for items. The icon
  bytes, including the fallback, are read and decoded in `load`, which `AppController` already runs off the EDT.
  `render` and `name` handle `ItemPage`.

### Tests
- `FixtureDb` now passes `ClientDataOptions.of(Fixtures.clientDataRoot())` and `coverageMinPct` 80.
- `ItemRepositoryOtherSubclassTest` used Weapon subclass 9 as an unknown subclass. With client data the subclass
  table is the client's, which has 9 (Warglaives) through 20. The test now uses 99.
- `ItemDetailPanelTest.dropPercentFormatted` became `dropPercentFormattedAndMarkedWhenSourcesDisagree`. Item 2874's
  drop by NPC 639 has `disagrees = 1` in the provenance data, so the expectation is now `"60.00 % ≠"`.
- New or extended tests:
  - `ItemRepositoryTest`:
    - `cardCarriesQualityIconAndTooltipLinesInOrdinalOrder` covers 17204 and its Use line.
    - `setItemCardListsTheSetLines` covers 19682.
    - `itemWithoutClientDataHasNullQuality` covers 1176.
    - `listCarriesQualityAndIcon`.
    - `dropSourcesCarryDisagreementAndSourceCount`.
  - `NpcRepositoryTest.dropRowsCarryDisagreementAndSourceCount`.
  - `InstanceRepositoryTest.bossLootCarriesDisagreementAndSourceCount`.
  - `SearchRepositoryTest.itemHitsCarryQualityOtherHitsDoNot`.
  - `ZoneRepositoryTest.mapJoinsTheZonesUiMap` and `zoneWithoutAMapHasNone`.
  - `AssetRepositoryTest` covers icon present and missing, the fallback, and no fallback row on a copied database.
  - `QualityColorsTest` covers dark, light, clamping and canonical.
  - `TooltipLinesPanelTest` covers order and colours, the null or invalid colour default, the tab split, and no lines.
  - `MapImagePanelTest` covers decoding, scaling and the upscale cap, undecodable bytes, and painting.
  - `ItemDetailPanelTest`:
    - `cardShowsIconQualityAndLines`.
    - `missingIconFallsBackToTheQuestionMark` covers 1307, whose icon blob is missing. It compares pixels with icon 0.
    - `itemWithoutClientDataHasNoLinesAndAPlainName`.
    - `withoutAnyIconTheIconSpaceStaysEmpty`.
  - `ZoneDetailPanelTest.mapShownWhenPresent` covers Westfall (40). `noMapWithoutOne` covers 2437.
  - `LinkTableTest`:
    - `disagreeingRatesAreMarkedInEveryDropTable`.
    - `markedRatesStillSortByTheirNumber`.
    - `itemNamesAreColouredByQuality`.
    - `itemSearchHitsAreColouredByQuality`.
- `AppControllerTest` is unchanged and passes.

## TDD evidence

RED: `./mvnw -B -q -pl app -am test`, run after writing the tests and before any main code. Excerpt:

```
app/src/test/java/dev/wowforever/app/db/ItemRepositoryTest.java:[8,32] cannot find symbol
app/src/test/java/dev/wowforever/app/db/ZoneRepositoryTest.java:[6,32] cannot find symbol
app/src/test/java/dev/wowforever/app/ui/detail/ItemDetailPanelTest.java:[9,29] cannot find symbol
app/src/test/java/dev/wowforever/app/ui/detail/LinkTableTest.java:[19,35] cannot find symbol
app/src/test/java/dev/wowforever/app/ui/detail/MapImagePanelTest.java:[7,32] cannot find symbol
app/src/test/java/dev/wowforever/app/ui/detail/TooltipLinesPanelTest.java:[5,32] cannot find symbol
app/src/test/java/dev/wowforever/app/db/AssetRepositoryTest.java:[26,21] cannot find symbol
app/src/test/java/dev/wowforever/app/db/InstanceRepositoryTest.java:[42,23] cannot find symbol
...
```

The first implementation run also had genuine behavioural REDs.
- `MapImagePanelTest.decodesTheJpeg` and `ItemDetailPanelTest.cardShowsIconQualityAndLines` failed with
  "Expecting actual not to be null". `Images.decode` used try-with-resources around
  `ImageIO.read(ImageInputStream)`, which closes the stream itself. The second close threw and the catch turned it
  into "empty". Fixed by not closing the stream again.
- `dropPercentFormatted` failed with `expected: "60.00 %" but was: "60.00 % ≠"`, which is the new marker on fixture
  data. The expectation was updated.

GREEN: `./mvnw -B -pl app -am test -DexcludedGroups=shipped-data`

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in dev.wowforever.app.ui.theme.QualityColorsTest
[INFO] Tests run: 174, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full reactor: `./mvnw -B verify`

```
[ERROR] dev.wowforever.app.ShippedDataTest.bossLootItemsShowTheirInstanceLoot -- <<< ERROR!   (no such column: i.quality)
[ERROR] dev.wowforever.app.ShippedDataTest.knownFacts -- <<< ERROR!                           (no such column: i.quality)
[ERROR] dev.wowforever.app.ShippedDataTest.itemClassListsAreNotCapped -- <<< FAILURE!         (expected 9575L but was 0L)
[ERROR] Tests run: 181, Failures: 1, Errors: 2, Skipped: 0
[INFO] Lua table parser ................................... SUCCESS
[INFO] Data importer ...................................... SUCCESS
[INFO] Browser application ................................ FAILURE
```

## Files changed
- Main, new: `model/ItemCard.java`, `model/TooltipLine.java`, `model/ZoneMap.java`, `db/AssetRepository.java`,
  `ui/theme/QualityColors.java`, `ui/detail/TooltipLinesPanel.java`, `ui/detail/MapImagePanel.java`,
  `ui/detail/Images.java`.
- Main, modified: `model/{ItemSummary,ItemDetail,DropSource,LootRow,InstanceLoot,ZoneDetail,SearchHit}.java`,
  `db/{ItemRepository,NpcRepository,InstanceRepository,SearchRepository,ZoneRepository,Repositories}.java`,
  `ui/{Columns,LinkRenderer,ResultsTableModel}.java`,
  `ui/detail/{DetailPanels,HeaderPanel,ItemDetailPanel,ZoneDetailPanel}.java`.
- Tests, new: `db/AssetRepositoryTest.java`, `ui/theme/QualityColorsTest.java`, `ui/detail/TooltipLinesPanelTest.java`,
  `ui/detail/MapImagePanelTest.java`.
- Tests, modified: `db/{FixtureDb,ItemRepositoryOtherSubclassTest,ItemRepositoryTest,NpcRepositoryTest,
  InstanceRepositoryTest,SearchRepositoryTest,ZoneRepositoryTest}.java`,
  `ui/detail/{ItemDetailPanelTest,ZoneDetailPanelTest,LinkTableTest}.java`.

## Self-review
- **Edge cases are covered.**
  - A null quality gives the plain title and the link colour in tables.
  - A missing icon blob gives the question mark. A missing question mark gives an empty icon space and no exception.
  - A missing map gives no map panel. An undecodable map paints nothing.
  - Zero sources give false and 0 on vendor, reward and object rows.
  - A malformed colour gives the default colour.
- **Off the EDT:** icon bytes are read and decoded in `DetailPanels.load`. The zone map JPEG is decoded lazily on
  first paint, which the brief allows. It needs no decode for layout because `ZoneMap` carries the size.
- **Additive UI:** no existing UI class was renamed or restructured.
  - `HeaderPanel` gained one setter and one getter.
  - The two detail panels gained a top composite.
  - `Columns.Column` gained a component with the old constructors kept.
- **Scope beyond the brief's model list:** `InstanceLoot` also carries `disagrees` and `sourceCount`. Its "In
  instance loot" Drop % column reads the same `boss_loot` rates. Without them, item 2874 would show "≠" under
  Dropped by but not under In instance loot.
- **Decision: tooltip background.** `TooltipLinesPanel` always uses a dark tooltip background (`#10121C`) and maps a
  null colour to white. The stored colours are the game's tooltip colours (`#ffffff`, `#1eff00`, `#ffd100`), and
  white or yellow text would be unreadable on the light theme's background. "Default text colour" therefore means
  the tooltip's default, as in game. The parallel UI branch may restyle this.
- **Decision: no map upscaling.** `MapImagePanel` never enlarges the map beyond 1002×668. Upscaling would blur it and
  push the tabs off-screen on wide windows.
- **Duplicated quality:** `ItemSummary.quality` and `ItemCard.quality` both exist, as the brief specifies. The panel
  colours the title from the card.

## Concerns
1. **The shipped database lacks the phase 2 schema** (`app/src/main/resources/data/wowforever.sqlite`, commit 21db9fd).
   The app now selects `item.quality`, `icon_id`, `disagrees`, `source_count` and `zone.ui_map_id`, so it fails
   against that file. Item pages, item lists and search throw or log "no such column: i.quality". Three
   `ShippedDataTest` tests fail.
   - The plan says the app does not need to open v0.1-shaped databases, and Task 11 regenerates the shipped file.
   - I asked the lead and committed under option A: the failures are documented here and fixed by Task 11.
   - Until Task 11 lands, `./mvnw verify` on this branch fails in `app` on those three tests only. Running the app
     from this commit against the bundled database is broken.
2. **Long tooltip lines truncate** with an ellipsis in the narrow default detail area, about 470 px. Use lines run to
   roughly 650 px. Each label's tooltip shows the full text. Wrapping is left to the UI branch's card redesign.
3. **Subzones share their parent's map.** Zone 1581 (The Deadmines) has `ui_map_id` 1436, the Westfall map, in the
   fixture, so its page shows the Westfall map. That is the importer's mapping, not something the app decides.

## Commit
- `3358b99 feat(app): quality colours, icons, tooltip lines, zone map images and disagreement markers`.
- The trailer reads `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. That is the model that ran this
  task and the attribution this session was configured with. The dispatch message asked for a Claude Fable 5.1 line.
- No reply to the shipped-database question arrived before the commit, so it went in under option A as stated.

## Shipped database regeneration (option B, lead's decision)

Command, from `H:\claude_java`, using the Task 9 import-data profile's skip variant:

```
./mvnw -B -q -pl importer -am verify -Pimport-data -Dclientdata.skip=true -DskipTests
```

- The command exited 0. It ran `--fail-on-unsupported --coverage-min 85 --skip-client-data`.
- It rewrote three files under `app/src/main/resources/data/`: `wowforever.sqlite`, `build-id.txt` and
  `import-report.txt`. The build id is the one `DatabaseInstaller.EMBEDDED_BUILD_ID` reads.
- The sources came from the pins in `sources.properties`: QuestieDB 49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4 and
  AtlasLootClassic 8e99341e4e779328460bf7684c0d5b22ce50ddf1. Both were served from `~/.cache/wowforever-importer`.

Count check, comparing the v0.1 file (`git show HEAD:...`) with the regenerated file:

| table | v0.1 | regenerated |
|---|---|---|
| item | 21,543 | 21,543 |
| npc | 13,392 | 13,392 |
| instance | 33 | 33 |
| item_npc_drop | 164,447 | 164,447 |
| game_object | 7,033 | 7,033 |
| quest | 5,232 | 5,232 |
| zone | 596 | 596 |
| boss_loot | 2,886 | 2,886 |
| npc_spawn | 124,486 | 124,486 |
| search_index | 47,579 | 47,577 |

- **Correction skips:** the "Skips by layer/field" and "First 50 skips" sections of the report are empty, so there
  are zero correction skips.
- **search_index:** it differs by exactly the two placeholder rows `zone 3545 'Zone #3545'` and
  `zone 5931 'Zone #5931'`. The importer deliberately stopped indexing "Zone #id" placeholders, and the app already
  filtered them out of search. No other search row differs.
- **Phase 2 columns and tables are present but empty:**
  - `item where has_client_data <> 0` is 0 rows.
  - Items with any non-null client column (quality, inventory_type, bonding, sell_price, buy_price, max_stack,
    icon_id, item_set_id, description, client_source) are 0 rows.
  - `zone.ui_map_id` is set on 0 rows.
  - `icon`, `zone_map`, `item_tooltip_line`, `item_stat` and `item_set` each hold 0 rows.
  - The report counter `clientdata.skipped` is 1.
- **Provenance is populated:** drop-rate provenance does not depend on client data, so it is filled. `drop_rate_source`
  has 11,696 rows. `item_npc_drop` has 201 rows with `disagrees = 1` and `boss_loot` has 110.

Verification: `./mvnw -B verify` from `H:\claude_java`

```
[INFO] Lua table parser ................................... SUCCESS
[INFO] Data importer ...................................... SUCCESS
[INFO] Browser application ................................ SUCCESS
[INFO] BUILD SUCCESS
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in dev.wowforever.app.ShippedDataTest
app surefire total: 182 tests, 0 failures, 0 errors
```

Commit: an earlier attempt amended the data into the feature commit as `3bbd15e`. Following the lead's follow-up
message, the branch was moved back to `3358b99` with `git reset --soft`, which kept the working tree and the staged
data. The data was then committed on top as
`b0e49bb chore(app): regenerate shipped database on the phase 2 schema (no client data yet)`. The file grew from
26.2 MB to 28.2 MB because of the provenance table and the new indexes.

The first-round concern 1, about the shipped database lacking the schema, is resolved. Concerns 2 and 3 still stand:
long tooltip lines truncate, and subzones share their parent's map.

## Fix report: tooltip card tokens (lead follow-up)

What changed: `TooltipLinesPanel` uses the UI review card tokens on both themes.
- The background is `BACKGROUND = #0C1424` (`card.bg`).
- A new constant `BORDER = #46587A` (`card.border`) is drawn as a 1 px line border around the existing 6/8 px
  padding.
- Both are plain colours, not `UIResource`s, so a theme switch keeps them.
- The class Javadoc states the design intent: the card is a WoW tooltip, not a themed panel.

Test: `TooltipLinesPanelTest.cardUsesTheDarkCardTokensOnEitherTheme` checks four things before and after
`SwingUtilities.updateComponentTreeUI`: the token values, opacity, the line-border colour, and that a label keeps its
game colour.

- RED: `./mvnw -B -q -pl app -am test -Dtest=TooltipLinesPanelTest` failed to compile with `TooltipLinesPanelTest.java:[53,33] cannot find symbol` because `BORDER` did not exist yet.
- GREEN: the same command gave `Tests run: 5, Failures: 0, Errors: 0`.
- Full suite: `./mvnw -B -q verify` exited 0 with no ERROR lines.

| module | tests | failures | errors |
|---|---|---|---|
| lua-table | 61 | 0 | 0 |
| importer | 319 | 0 | 0 |
| app | 183 | 0 | 0 |

`ShippedDataTest` ran 7 tests with 0 failures.

Commit: `f6287bc fix(app): tooltip card keeps the dark card tokens on both themes`.

Commits on the branch from this task:
- `3358b99 feat(app): ...`
- `b0e49bb chore(app): ...`
- `f6287bc fix(app): ...`

The two new commits carry `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`, as the lead asked.

## Fix round 1 (review findings)

Commit: `0716ed3 fix(app): decode zone maps off the EDT; tolerate throwing image plugins; hide a missing icon; theme
lookup from the LaF`.

### What changed

1. **The zone map is decoded off the EDT** (Important).
   - `DetailPanels.load` returns a new `DetailPanels.ZonePage(ZoneDetail detail, BufferedImage map)` for zones.
   - The JPEG is decoded there with `Images.decode`, on the worker that `AppController` already uses. `map` is null
     when the zone has no map or the bytes do not decode.
   - `render` and `name` handle `ZonePage`.
   - `MapImagePanel` now takes a non-null, already decoded `BufferedImage` and sizes itself from the image. It no
     longer decodes anything.
   - `ZoneDetailPanel(ZoneDetail, BufferedImage mapImage, Navigator)` is the new constructor. The old two-argument
     constructor delegates with no map image. A null map image shows no map panel, so undecodable bytes still show
     nothing.
2. **`Images.decode` catches `IOException | RuntimeException`**, and the Javadoc says corrupt data never throws.
   - On JDK 21, `ImageIO.read(ImageInputStream)` already wraps a reader's unchecked exception from `read` into an
     `IIOException`.
   - A plugin whose `canDecodeInput` probe throws unchecked still escapes `ImageIO.read`, because ImageIO only
     swallows `IOException` there. That is the path the new catch covers.
3. **The icon label is hidden when the image is null.** `ItemDetailPanel` hides `iconLabel` so the title starts at
   the left edge on a database without icons. The fallback question mark still shows whenever icon 0 exists.
4. **Paint-time theme lookups use `FlatLaf.isLafDark()`.** This applies to the `LinkRenderer` quality colour and the
   `ItemDetailPanel` title colour supplier. `ThemeManager.isDark()`, the Preferences read, is still used for the
   saved choice at startup and on toggle. It is no longer called per paint.

### Tests

- `MapImagePanelTest` was rewritten for `MapImagePanel(BufferedImage)`. It covers holding the image, rejecting null,
  scaling with the upscale cap, and painting.
- `ZoneDetailPanelTest`:
  - `mapShownWhenPresent` now goes through `DetailPanels.load` and `render`. It asserts that `ZonePage.map()` was
    decoded in `load` at 1002 px wide and that the panel shows that same image.
  - `noMapWithoutOne` covers 2437 through `load` and `render`.
  - The new `aMapThatDidNotDecodeShowsNothing` passes a null image.
- New `ImagesTest`:
  - `decodesPngAndJpeg`.
  - `nullAndGarbageDecodeToNothing` uses null, empty and 4 KiB of random bytes.
  - `corruptImagesNeverThrow` uses a PNG signature followed by noise, a truncated JPEG and a scrambled JPEG.
  - `aPluginThatThrowsUncheckedWhileProbingDecodesToNothing` and
    `aReaderThatThrowsUncheckedWhileReadingDecodesToNothing` register a test `ImageReaderSpi` for a made-up "BOOM"
    format. It throws `IllegalStateException` while probing or while reading, and is deregistered in `finally`.
- `ItemDetailPanelTest.withoutAnyIconTheIconSpaceStaysEmpty` asserts the label is not visible.
  `cardShowsIconQualityAndLines` asserts it is visible. Expected title colours use `FlatLaf.isLafDark()`.
- `LinkTableTest`: the new `qualityColourFollowsTheInstalledTheme` installs `FlatDarkLaf` and then `FlatLightLaf`,
  checks the dark and light quality colours, and restores the previous look-and-feel. The existing quality tests use
  `FlatLaf.isLafDark()`.

### TDD evidence

- **RED, structural:** `./mvnw -B -q -pl app -am test -Dtest='ZoneDetailPanelTest,MapImagePanelTest,ImagesTest,ItemDetailPanelTest,LinkTableTest'` failed to compile:
  ```
  MapImagePanelTest.java:[19,31] incompatible types: java.awt.image.BufferedImage cannot be converted to dev.wowforever.app.model.ZoneMap
  ZoneDetailPanelTest.java:[72,59] cannot find symbol            (DetailPanels.ZonePage)
  ZoneDetailPanelTest.java:[93,16] constructor ZoneDetailPanel ... cannot be applied to given types;
  ```
- **RED, the unchecked catch:** the catch was temporarily reverted to `catch (IOException e)`. The garbage and
  corrupt-image tests and the read-path plugin test still passed, because the JDK wraps those cases, so they cannot
  show the fix. The probe-path test fails:
  ```
  [ERROR] dev.wowforever.app.ui.detail.ImagesTest.aPluginThatThrowsUncheckedWhileProbingDecodesToNothing <<< FAILURE!
    "java.lang.IllegalStateException: corrupt boom header
  [ERROR] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
  ```
- **Fuzzing:** before writing the plugin test I fuzzed 8,000 random header mutations of the fixture PNG and JPEG and
  tried oversized PNG dimensions. The JDK readers produced only checked `IIOException`s. Garbage bytes alone
  therefore cannot exercise the new catch, which is why the plugin test exists.
- **GREEN:** the covering classes pass with these counts.

  | test class | tests | failures |
  |---|---|---|
  | ZoneDetailPanelTest | 8 | 0 |
  | MapImagePanelTest | 4 | 0 |
  | ImagesTest | 5 | 0 |
  | ItemDetailPanelTest | 12 | 0 |
  | LinkTableTest | 12 | 0 |
  | DetailPanelsTest | 6 | 0 |

- **Full suite:** `./mvnw -B -q verify` from `H:\claude_java` exited 0 with no ERROR lines.

  | module | tests | failures | errors |
  |---|---|---|---|
  | lua-table | 61 | 0 | 0 |
  | importer | 319 | 0 | 0 |
  | app | 190 | 0 | 0 |

  `ShippedDataTest` ran 7 tests with 0 failures.
