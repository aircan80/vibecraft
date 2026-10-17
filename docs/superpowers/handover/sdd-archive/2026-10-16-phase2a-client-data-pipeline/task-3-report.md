# Task 3 report: Java CSV reader, client-data source and fixture subset

**Status:** DONE_WITH_CONCERNS
**Commit:** `805f959 feat(importer): client-data CSV reader, source directory and fixtures` (not pushed)

## What was implemented

### Python (tools/clientdata)
- `fetch.py --fdids 1,2,3`: fetches exactly the listed FileDataIDs. Non-numeric input gives exit 2 with a message naming the flag. Two new tests are in `tests/test_fetch.py`.
- `make_fixture.py` (shim) with `clientdata/fixture.py`: `--cache --build --out --items` plus `--sets`, `--ui-maps`, `--areas`, `--extra-levels` (default 60) and `--list-fdids icons|tiles`. It writes `OUT/csv/{Table}.csv` for all 42 required tables. Rows are copied **byte for byte** (original quoting and line endings, source order) under the original header line. It follows references transitively:
  - Items gain the pieces of their sets, and each item's own `ItemSparse.ItemSet` is followed to a fixed point.
  - `ItemXItemEffect` leads to `ItemEffect`, then to spells.
  - Spells come from item effects and `ItemSetSpell`. The closure follows `SpellEffect.EffectTriggerSpell`, the `$<id>`, `$@spelldescN` and `$?sN` text references, and enchanting effects (53/54/92/156). Those effects lead to `SpellItemEnchantment` and back to the enchantment's proc, equip or use spells. It stops after 4 rounds.
  - The kept spells select rows in Spell, SpellName, SpellEffect, SpellMisc and SpellDuration (via `DurationIndex`).
  - RandPropPoints and ItemArmorQuality are kept by ID, which is the item level. The ItemDamage* tables, ItemArmorTotal and ItemArmorShield are kept by `ItemLevel`. Both cover the levels used plus 60.
  - The UiMap chain is kept for the given UiMaps. AreaTable keeps the given areas and all their descendants. Map keeps the ids referenced by the kept AreaTable and UiMapAssignment rows.
  - Small lookup tables are kept whole. These are ArmorLocation, ItemClass, ItemSubClass, ItemNameDescription, ChrRaces, ChrClasses, SkillLine and Faction, all under 200 KB. Above 200 KB, the tool would keep only rows referenced from ItemSparse.
  - Light tests are in `tests/test_fixture.py` with 3 tests. They cover a synthetic chain with quoted newlines, the spell closure, levels, areas, the map chain and the CLI.
- README: `--fdids` is documented, plus the exact commands that rebuild the fixture.

### Java (`dev.wowforever.importer.clientdata`)
- `CsvTable`
  - `read(Path)` and `read(String source, Reader)` parse with an RFC 4180 state machine over a `BufferedReader`, reading in 64K chunks. No full-file String is built and there is no `split`.
  - Supported: quoted commas, doubled quotes, CRLF/LF inside quotes kept verbatim, CRLF/LF/CR record ends, optional BOM, and blank lines skipped. Input is strict UTF-8, so malformed bytes throw.
  - **Strict by design.** A record whose field count differs from the header's, a character after a closing quote, an unterminated quote and a duplicate header each throw `IllegalStateException("File.csv:line: …")`. A shifted column never passes silently.
  - API: `columns()`, `hasColumn`, `column(name)`, `rows()`, `size()`, `source()`, `indexByLong(col)` and `groupByLong(col)`.
    - `column(name)` throws `IllegalArgumentException` naming the file and the column.
    - `indexByLong` uses a LinkedHashMap. It throws on a duplicate key and skips empty keys.
    - `groupByLong` skips empty keys.
  - `Row` API:
    - `get(String)` and `get(int)` return `""` for an empty cell.
    - `getInt`, `getLong` and `getDouble`, by name or index, return `OptionalInt`, `OptionalLong` or `OptionalDouble`, empty for an empty cell. A non-number throws `NumberFormatException` naming file, line and column.
    - `intOr`, `longOr` and `doubleOr` take a fallback.
    - `line()` gives the record's start line.
  - Rows are materialised as `String[]`. Values of 8 characters or fewer are shared to save memory. Parsing is streamed.
- `MiniJson` (package-private) is a minimal recursive-descent JSON reader for `build.lock` and `maps.json`, written because there is no JSON library on the classpath and none was added. It covers objects in insertion order, arrays, strings with all escapes including `\uXXXX`, Long/Double numbers, literals, and errors with the character offset. It has its own `MiniJsonTest`.
- `BuildLock(product, build, fetchedAt, baseUrl, Map<String,String> tableSha)` with `read(Path)`. A malformed lock throws `IllegalStateException` naming the file. `file_count` and `missing_count` are accepted but not kept, because they describe only the last fetch run.
- `ClientDataSource(Path outDir)`
  - `lock()` is cached. It throws `IllegalStateException` when `build.lock` is absent.
  - `table(name)` is cached in a ConcurrentHashMap. It throws `IllegalStateException` naming the table when the file is missing.
  - `hasTable`, `icon(fdid)`, `map(uiMapId)` and `dir()`.
  - `maps()` returns `List<MapInfo>` from `maps.json` and is empty when the file is absent. `record MapInfo(long uiMapId, String name, int width, int height, int tiles, int overlays, int missingTiles)` is nested in `ClientDataSource`.
- `ClientDataOptions(Optional<Path> dir, boolean skip)`. The compact constructor throws `IllegalArgumentException` when neither is given ("either --client-data-dir <dir> or --skip-client-data is required") and when both are given. Factories are `of(Path)` and `skipped()`.
- `ImportOptions`
  - It has a new 7th component, `Optional<ClientDataOptions> clientData`. The existing 6- and 5-argument constructors are kept and delegate with `Optional.empty()`, so existing callers and tests are unchanged, including the app module's `FixtureDb`.
  - `--client-data-dir <dir>` and `--skip-client-data` are parsed and listed in USAGE.
  - Giving both flags is a usage error. Giving one yields `Optional.of(...)`.
  - **Giving neither yields `Optional.empty()` for now.** The "required" behaviour lands in Task 9, together with the stage that consumes it. The pipeline ignores the option until then.
- Test helper `Fixtures.clientDataRoot()` follows the existing pattern. The `clientdata` tree was also added to the out-of-jar copy list, so app tests that use the importer test-jar get it too.

## Fixture inventory (`importer/src/test/resources/fixtures/clientdata`)

The source is `wow_classic_beta` build 1.60.1.70425 from https://wago.tools. All 42 tables were fetched once, about 15 MB. After that, only the 34 icon BLPs and the 59 tile BLPs were fetched. There were no 404s.

The items are the 22 listed ids, plus 190179 because the client has it, plus **215129** (see the concerns section). Two sets are included: **203** as instructed and **421**, which I added. The maps are UiMaps 1436 and 1429. The areas are 12 and 40 with their descendants.

| CSV | rows | CSV | rows |
|---|---|---|---|
| AreaTable | 47 | ItemNameDescription | 92 (whole) |
| ArmorLocation | 23 (whole) | ItemSet | 2 (203, 421) |
| ChrClasses | 9 (whole) | ItemSetSpell | 4 |
| ChrRaces | 58 (whole) | ItemSparse | 18 |
| Faction | 253 (whole) | ItemSubClass | 100 (whole) |
| Item | 35 | ItemXItemEffect | 10 |
| ItemArmorQuality | 12 | Map | 1 |
| ItemArmorShield | 12 | RandPropPoints | 12 |
| ItemArmorTotal | 12 | SkillLine | 154 (whole) |
| ItemClass | 17 (whole) | Spell | 19 |
| ItemDamageAmmo | 12 | SpellDuration | 6 |
| ItemDamageOneHand | 12 | SpellEffect | 24 |
| ItemDamageOneHandCaster | 12 | SpellItemEnchantment | 1 (7210 Dismantle) |
| ItemDamageRanged | 12 | SpellMisc | 19 |
| ItemDamageThrown | 12 | SpellName | 19 |
| ItemDamageTwoHand | 12 | UiMap | 2 |
| ItemDamageTwoHandCaster | 12 | UiMapArt | 2 |
| ItemDamageWand | 12 | UiMapArtStyleLayer | 1 |
| ItemEffect | 10 | UiMapArtTile | 24 |
| | | UiMapAssignment | 2 |
| | | UiMapXMapArt | 2 |
| | | WorldMapOverlay | 26 |
| | | WorldMapOverlayTile | 35 |

There are 12 item levels: 1, 2, 5, 11, 16, 45, 55, 60, 61, 65, 76 and 80.

The 19 spells kept are 430, 433, 8690, 18112, 18381, 18382, 21160, 21740, 21741, 21992 (Thunderfury, reached via its trigger), 23553, 23554, 27648, 402265, 435467, 435481, 439164, 442206 and 1293654. The 4xxxxx ids are the Dismantle formula chain.

- **Icons:** 34 converted PNGs (56x56) plus `icons/0.png`, 35 files in all. They are exactly the `IconFileDataID`s of the 35 Item rows and include 134124, the Eye of Sulfuras.
- **Maps:** `maps/1429.jpg` (Elwynn Forest, 12 tiles, 12 overlays) and `maps/1436.jpg` (Westfall, 12 tiles, 14 overlays). Both are 1002x668 with 0 missing tiles, and the Westfall map was checked visually. `maps.json` lists both.
- **`build.lock`:** a copy of the cache lock. `tables` holds the SHA-256 of the **full** 42 tables, not of the subsets, and `file_count` is 34, from the last fetch run.
- **Size:** 693,338 bytes in 81 files. csv is 123 KB, icons 138 KB, maps 428 KB, and maps.json plus build.lock 4 KB. This is under the 1.5 MB target.
- **Git:** `.gitattributes` gains `*.jpg binary` and `importer/src/test/resources/fixtures/clientdata/** -text`, because one quoted ItemSparse cell holds a real CRLF. `git ls-files --eol` shows `i/mixed attr/-text`, so the bytes are preserved.

## Real column names observed

**Item** (17 columns): `ID, ClassID, SubclassID, Material, InventoryType, SheatheType, ItemPetFoodID, Sound_override_subclassID, IconFileDataID, ItemGroupSoundsID, ContentTuningID, ModifiedCraftingReagentItemID, AmmunitionType, CraftingQualityID, ItemSquishEraID, RecraftReagentCountPercentage, OrderSource`

**ItemSparse** (104 columns): `ID, Description_lang, Display3_lang, Display2_lang, Display1_lang, Display_lang, ExpansionID, DmgVariance, LimitCategory, DurationInInventory, QualityModifier, BagFamily, StartQuestID, LanguageID, ItemRange, StatPercentageOfSocket_0..9, StatPercentEditor_0..9, StatModifier_bonusStat_0..9, Stackable, MaxCount, MinReputation, RequiredAbility, AllowableRace_0, AllowableRace_1, SellPrice, BuyPrice, VendorStackCount, PriceVariance, PriceRandomValue, Flags_0..4, OppositeFactionItemID, ModifiedCraftingReagentItemID, ContentTuningID, PlayerLevelToItemLevelCurveID, ItemLevelOffsetCurveID, ItemLevelOffsetItemLevel, ItemSquishEraID, ItemNameDescriptionID, RequiredTransmogHoliday, RequiredHoliday, Gem_properties, Socket_match_enchantment_ID, TotemCategoryID, InstanceBound, ZoneBound_0, ZoneBound_1, ItemSet, LockID, PageID, ItemDelay, MinFactionID, RequiredSkillRank, RequiredSkill, ItemLevel, AllowableClass, ArtifactID, SpellWeight, SpellWeightCategory, SocketType_0..2, SheatheType, Material, PageMaterialID, Bonding, DamageType, ContainerSlots, RequiredPVPMedal, RequiredPVPRank, RequiredLevel, InventoryType, OverallQualityID, AmmunitionType`

Notes for later tasks:
- `AllowableRace` is split into `_0` and `_1`.
- There is no `ItemSparse.Quality`. Use `OverallQualityID`.
- `RandPropPoints` columns are `ID` (the item level), `DamageReplaceStat[F]`, `DamageSecondary[F]`, `EpicF_0..4`, `SuperiorF_0..4`, `GoodF_0..4`, `Epic_0..4`, `Superior_0..4` and `Good_0..4`.
- `ItemArmorQuality` has no ItemLevel column: its `ID` is the item level.
- `ItemSubClass` and `ChrClasses` do not start with `ID`.
- `UiMap` starts with `Name_lang`.
- `SpellEffect` uses `EffectMiscValue_0/1` and `EffectBasePointsF`.

## TDD evidence

1. Python:
   - I added the `--fdids` tests first. They failed with "unrecognized arguments: --fdids", 2 failed and 23 passed. After the change, 25 passed.
   - The fixture tests were written right after `fixture.py` and passed on the first run, so they were not red-first.
2. Java:
   - I wrote `CsvTableTest`, `ClientDataSourceTest` and `MiniJsonTest` first. `./mvnw -B -q -pl importer test` then failed with a compilation error: "package CsvTable does not exist", "cannot find symbol ClientDataSource".
   - After implementing, the first run hit one compile error in the test's own generics. Once that was fixed, all tests were green.
   - The added `realCrlfInsideAQuotedDescriptionKeepsColumnsAligned` test was written against the rebuilt fixture.
3. Final runs:
   - `./mvnw -B -q -pl importer test` passes **156 tests, 0 failures**. The new tests are CsvTableTest 9, ClientDataSourceTest 4 and MiniJsonTest 2.
   - `./mvnw -B -q -pl app -am test` passes, with 146 app tests.
   - `pytest tools/clientdata` gives **25 passed**.
4. Real-data checks, not in the suite:
   - Parsing all 42 full tables with `CsvTable` gives row counts **and multi-line cell counts identical to Python's `csv` module**. Spell.csv has 2,154 multi-line cells, ItemSparse 1 and Map 1.
   - Timing: ItemSparse (5.3 MB, 19,234 rows x 104 columns) reads and indexes in 112 ms cold and 71–92 ms warm. SpellEffect (3.8 MB) takes about 60–80 ms. All are well under 1 s.

## Files changed
- `.gitattributes`
- `importer/src/main/java/dev/wowforever/importer/ImportOptions.java`
- `importer/src/main/java/dev/wowforever/importer/clientdata/{CsvTable,BuildLock,ClientDataSource,ClientDataOptions,MiniJson}.java`
- `importer/src/test/java/dev/wowforever/importer/Fixtures.java`
- `importer/src/test/java/dev/wowforever/importer/clientdata/{CsvTableTest,ClientDataSourceTest,MiniJsonTest}.java`
- `importer/src/test/resources/fixtures/clientdata/**`, 81 files
- `tools/clientdata/{make_fixture.py,README.md}`, `tools/clientdata/clientdata/{fixture.py,fetch.py}`, `tools/clientdata/tests/{test_fixture.py,test_fetch.py}`

Nothing was installed globally. Nothing under the WoW install was touched. Network use was the one full table fetch plus 93 file fetches from wago.tools, throttled to 4 requests per second.

## Concerns
1. **ItemSparse coverage is partial in this build.** Only 19,234 of 31,852 Item rows have an ItemSparse row.
   - 10 of the 23 named fixture ids have **Item rows only**: 5191, 14145, 14148, 14149, 16800, 19137, 16865, 10329, 7736, and all 8 pieces of set 203. They are the "iconOnly" case in Task 4.
   - No Tier 1 set has its pieces in ItemSparse.
   - So Task 6 can test set tooltips with real piece data, I added **set 421 Bloodvine Garb** (19682–19684). Its pieces are fully in ItemSparse and it has an `ItemSetSpell` bonus (18382, threshold 3).
   - Set 203 is kept as instructed. It has a real ItemSet row and 3 ItemSetSpell rows, but its pieces have no sparse rows.
   - This also matters for Task 11's expectations of matched and unmatched counts.
2. **190179 (Avelina's Heart) exists in this client,** with both an Item and an ItemSparse row, so it is included. As a result, no fixture id is "unmatched". Tests for that case need an id that is absent from Item, such as 999999.
3. **Item 215129 is extra.** "Formula: Enchant Weapon - Dismantle" was added to the fixture because it is the only real ItemSparse cell with a quoted CRLF. It is pinned by a test. Its spell chain also fills `SpellItemEnchantment`, which would otherwise be empty.
4. The brief says "all 46 tables", but `REQUIRED_TABLES` and the README list 42. I fetched 42.
5. The fixture `build.lock` hashes are of the full tables, so a later task must not verify the fixture CSVs against them. Its `file_count` is 34 and reflects only the last fetch run.
6. These are intentional deviations or additions to the interface:
   - Typed getters return `OptionalInt`, `OptionalLong` and `OptionalDouble`, with `*Or(col, fallback)` helpers.
   - `MiniJson` and its test are an extra class and test.
   - `hasTable`, `dir()`, `size()`, `hasColumn`, `source()` and `Row.line()` are extras.
   - `MapInfo` is nested in `ClientDataSource`.
   - `ImportOptions` client-data flags are optional until Task 9, while `ClientDataOptions` itself already enforces exactly one of the two.
