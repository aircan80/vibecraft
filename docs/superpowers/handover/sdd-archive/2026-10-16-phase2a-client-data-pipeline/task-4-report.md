# Task 4 report: item core join, schema additions and alt values

**Status:** DONE_WITH_CONCERNS
**Commits** (not pushed):
- `14e3c12 feat(clientdata): Era ItemSparse fallback in fetch, convert and fixture`
- `84cb82c feat(importer): item core client data, schema additions and alt values`

## What was implemented

### Python (`tools/clientdata`), ruling 17
- **`fetch.py`**
  - `--fallback-product PRODUCT` resolves that product's newest build. `--fallback-build BUILD` pins it instead, and it needs `--fallback-product` (exit 2 otherwise).
  - Only `ItemSparse` is fetched from the fallback product, into `{cache}/{forever build}/csv/ItemSparse.era.csv`.
  - `build.lock` gains `fallback_product`, `fallback_build` and `tables["ItemSparse.era"]` (its sha256). Without a fallback the lock keeps its old shape, with no `fallback_*` keys.
  - The cached Era file is reused unless the previous `build.lock` names a different fallback build, or none; then it is fetched again. This keeps a newer Era build from being masked by a stale file.
  - `/api/builds` is now read once per run and shared by both resolutions (`load_builds`, plus an optional `builds=` argument on `resolve_build`).
  - An unknown fallback product exits 2 with a message and makes no table request.
- **`convert.py`** needed no code change: it already copies every `csv/*.csv`. A test now pins that `ItemSparse.era.csv` is copied byte for byte.
- **`fixture.py` / `make_fixture.py`**
  - When the cache has `ItemSparse.era.csv`, the Era rows of every subset item are written to `ItemSparse.era.csv`.
  - The "effective" sparse rows are Forever's row, else Era's. They drive the item-level, enchantment and reference closures.
  - Era rows never add items or sets (see concern 1).
  - `tables.py` holds the shared name `FALLBACK_NAME = "ItemSparse.era"`.
- **Cache population:** no network request was made. I copied the existing probe download into the cache as `~/.cache/wowforever-clientdata/1.60.1.70425/csv/ItemSparse.era.csv`. The probe is Era build 1.15.9.70003, sha256 `eef48b88…a5145`. The cache `build.lock` was not rewritten.
- The README documents the flags, the cache and lock layout, the closure rule and the fixture command, with `--fallback-product wow_classic_era --fallback-build 1.15.9.70003`.

### Java (`importer`)
- **`schema.sql`** follows spec §4.3 and rulings 14 to 17.
  - New `item` columns: `quality, inventory_type, bonding, sell_price, buy_price, max_stack, icon_id, item_set_id, description, allowable_class, allowable_race, required_skill, required_skill_rank, has_client_data INTEGER NOT NULL DEFAULT 0, client_source TEXT`.
  - New tables: `item_alt_value`, `item_stat`, `item_damage` (PK item_id), `item_armor` (PK item_id), `item_effect` (with a `trigger` column), `item_set`, `item_set_item`, `item_set_bonus`, `item_tooltip_line`, `icon`, `zone_map` and `drop_rate_source` (PK npc_id, item_id, source).
  - `zone.ui_map_id` was added.
  - `item_npc_drop` and `boss_loot` each gained `disagrees` and `source_count`, both `INTEGER NOT NULL DEFAULT 0`.
  - The columns are added in the CREATE statements, because the database is always written fresh.
  - New indexes: `idx_item_quality`, `idx_item_icon`, `idx_item_alt_value_item`, `idx_item_stat_item`, `idx_item_effect_item`, `idx_item_set_item_set`, `idx_item_set_item_item`, `idx_item_tooltip_line_item` and `idx_drop_rate_source_item`.
  - `meta` keys are not added in this task.
- **`clientdata.ClientItem`** has exactly the brief's fields plus `String clientSource`, which is `"forever"`, `"era"` or null. It nests `record StatShare(int statId, int share)`. The lists are copied and made non-null.
- **`clientdata.ItemSparseLoader.load(ClientDataSource, Set<Long>, ImportReport)`** returns `Map<Long, ClientItem>` in id order. It holds only ids that have an Item row or an ItemSparse row.
  - Forever's ItemSparse row wins. Otherwise the Era row is used, if `ItemSparse.era` exists. The Item row always comes from Forever.
  - Every column read is checked up front. A missing column throws `IllegalStateException("client table ItemSparse (ItemSparse.csv) has no column X; …")`, and likewise for `ItemSparse.era` and `Item`.
  - The client's "none" values become null: ItemSet, StartQuestID, IconFileDataID, RequiredSkill and ItemDelay of 0, and an empty Display_lang or Description_lang. RequiredSkillRank is null when there is no skill.
  - Stat slots are kept where `StatModifier_bonusStat_n >= 0`. Both builds use -1 for an empty slot, which I checked on the full tables.
  - Counters, always emitted, even at 0: `clientdata.items.matched`, `.matched.forever`, `.matched.era`, `clientdata.items.iconOnly` and `clientdata.items.unmatched`.
  - The note reads `clientdata.unmatched.ranges: 0-9999 (1), 990000-999999 (2)`, or `none` when nothing is unmatched.
- **`clientdata.ItemClassNames(classes, subclasses)`** comes from `.load(ClientDataSource)`.
  - Classes are keyed by `ItemClass.ClassID` with the name from `ClassName_lang`. They are not keyed by the row `ID`, which differs.
  - Subclasses are keyed by `ItemSubClass.(ClassID, SubClassID)` with the name from `DisplayName_lang`. An empty display name falls back to `VerboseName_lang`, and a row with neither name is skipped.
  - Maps are ordered by id. A missing column fails naming the table and the column.
- **`clientdata.ClientDataSource.optionalTable(name)`** returns `Optional<CsvTable>`, cached like `table`.
- **`clientdata.BuildLock`** gains `fallbackProduct` and `fallbackBuild`, which are null when absent. A non-string value fails naming the field. The old 5-argument constructor is kept.
- **`sqlite.ClientItemWriter`** is new and package-private. It merges each Questie item row with its `ClientItem` and owns the `item_alt_value` batch.
  - **With a sparse row:** the client's name, item_level, required_level, class_id and subclass_id replace Questie's.
    - A Questie value that is non-null and different is written as `item_alt_value(id, <column name>, 'questie', value)` and counted as `clientdata.alt.<column name>`.
    - The comparison is numeric across Long, Integer and Double.
    - A null client value keeps Questie's value.
  - **Item-only:** the class is taken from the Item row only if Questie's is NULL. The subclass is taken only if Questie's is NULL and the class agrees, so a Questie class is never overwritten.
  - `icon_id` is set whenever there is an Item row. `has_client_data` is `hasSparse ? 1 : 0`. `client_source` is set only with a sparse row.
- **`EntityWriter.items`** uses the merged row. `nameless.item` now counts rows whose *final* name is null, so a Questie stub named by the client is no longer nameless. `search_index` is built from `item.name` as before, so it uses the client name.
- **`SqliteWriter.write(Path, ImportModel, ImportReport, Map<Long, ClientItem>, ItemClassNames)`** is new.
  - The existing 2- and 3-argument overloads delegate with `Map.of()` and `null`, which is exactly the v0.1 behaviour.
  - `classNames != null` writes `item_class` and `item_subclass` from the client. Otherwise the static `ItemClasses` table is used.
- **Wiring:** `ImportPipeline` is unchanged and still calls the 3-argument `write`. A `ClientDataStage` that calls `ItemSparseLoader.load` and passes the map and `ItemClassNames` to `SqliteWriter` arrives in Task 9.

## Fixture counts after regeneration

Regeneration followed the README procedure: `make_fixture` into a scratch mini cache, then `convert.py` into the fixture directory. Icons, maps, `maps.json` and `build.lock` came out byte-identical.

| Table | Rows before | Rows after |
|---|---|---|
| ItemSparse.era (new) | – | 35 |
| RandPropPoints, ItemArmorQuality, ItemArmorShield, ItemArmorTotal, ItemDamage* (8) | 12 each | 18 each |
| Every other table | unchanged | unchanged |

- **The 35 Era rows:** 17 are used as fallback. These are the 9 Item-only ids (5191, 7736, 10329, 14145, 14148, 14149, 16800, 16865, 19137) and the 8 set-203 pieces (16803 to 16810).
- The other 18 are overlapping rows of items that Forever already has. They are kept so the "Forever wins" rule is tested on real data.
- **New item levels:** 18, 24, 37, 39, 66 and 78.
- **Size:** the fixture is now 714,882 bytes, up from 693,338.
- **Loader on the fixture:** every Item-only id now resolves through Era, so `iconOnly` is 0 on the full fixture. The iconOnly path is tested on a copy without `ItemSparse.era.csv`.

## Column names relied on (verified against the fixture headers)
- **Item:** `ID, ClassID, SubclassID, InventoryType, IconFileDataID`
- **ItemSparse and ItemSparse.era:** `ID, Display_lang, Description_lang, ItemLevel, OverallQualityID, RequiredLevel, InventoryType, Bonding, SellPrice, BuyPrice, Stackable, ItemSet, StartQuestID, AllowableClass, AllowableRace_0, RequiredSkill, RequiredSkillRank, ItemDelay, DmgVariance, DamageType, Flags_0..4, StatModifier_bonusStat_0..9, StatPercentEditor_0..9`
  - The Era table has all of these. It has 142 columns against Forever's 104.
- **ItemClass:** `ClassID, ClassName_lang`
- **ItemSubClass:** `ClassID, SubClassID, DisplayName_lang, VerboseName_lang`

## TDD evidence
- **Python**
  - New tests: 6 in `test_fetch.py`, 1 in `test_convert.py` and 2 in `test_fixture.py`.
  - The first run gave 6 failed and 28 passed. The convert test and the fixture "no era table" test passed at once: convert already copied every CSV, and the second fixture test pins existing behaviour.
  - After implementing: 34 passed.
- **Java**
  - New tests: `ItemSparseLoaderTest` (8), `ItemClassNamesTest` (3), `SqliteWriterClientDataTest` (6), and 2 more in `ClientDataSourceTest`.
  - The first run was a compilation failure ("cannot find symbol" for ClientItem, ItemSparseLoader, ItemClassNames, optionalTable and fallbackProduct).
  - After implementing, two assertions in my own test were wrong about the data: 17204 has `Flags_1 = 28676`, and 2874 has a description. I corrected the expectations against the CSV.
  - Final runs:
    - `./mvnw -B -q -pl importer test` passes 175 tests with 0 failures.
    - `./mvnw -B -q verify` passes: importer 175, app 146, lua-table 61.
    - `pytest tools/clientdata` gives 34 passed.
- **Self-review checks, all asserted in the tests:**
  - 17204 has quality 5, `forever`, icon 134124 and set NULL.
  - 16800 has quality 4, `era`, `has_client_data = 1` and set 201.
  - 999999 is unmatched, with no client columns and `has_client_data = 0`.
  - Alt values: `25:name:questie:Worn Short Sword` and `2874:item_level:questie:15`.
  - `search_index` holds the client name, and the old Questie token no longer matches.
  - Every new table, column and index is present.

## Files changed
- `tools/clientdata/{README.md, clientdata/fetch.py, clientdata/fixture.py, clientdata/tables.py, tests/test_fetch.py, tests/test_convert.py, tests/test_fixture.py}`
- `importer/src/test/resources/fixtures/clientdata/csv/{ItemSparse.era.csv (new), RandPropPoints, ItemArmorQuality, ItemArmorShield, ItemArmorTotal, ItemDamage*}.csv`
- `importer/src/main/resources/dev/wowforever/importer/schema.sql`
- `importer/src/main/java/dev/wowforever/importer/clientdata/{ClientItem, ItemSparseLoader, ItemClassNames}.java` (new) and `{BuildLock, ClientDataSource}.java`
- `importer/src/main/java/dev/wowforever/importer/sqlite/ClientItemWriter.java` (new) and `{EntityWriter, SqliteWriter}.java`
- `importer/src/test/java/dev/wowforever/importer/clientdata/{ItemSparseLoaderTest, ItemClassNamesTest}.java` (new) and `ClientDataSourceTest.java`
- `importer/src/test/java/dev/wowforever/importer/sqlite/SqliteWriterClientDataTest.java` (new)

## Concerns
1. **16800 is in set 201, not 203.** The brief expected "16800 set 203", but the Era data puts Arcanist Boots in set 201 (Arcanist Raiment). Set 203 is Felheart Raiment, pieces 16803 to 16810, and those do resolve to 203. The tests assert 201 for 16800 and 203 for the Felheart pieces. Era rows do not widen the fixture, so sets 201, 163 and 209, which are referenced by Era rows of 16800, 10329 and 16865, are not in the fixture's `ItemSet`. Task 6 will see those `item_set_id`s with no `item_set` row, which is a realistic dangling case.
2. **`max_stack` uses `Stackable`, not `MaxCount` as the brief said.** `MaxCount` is the "Unique (n)" count: Tough Jerky has `MaxCount 0` and `Stackable 20`, and 2874 has `MaxCount 1`. `ClientItem` has no unique-count field, so Task 6 needs `MaxCount` added for its "Unique" tooltip line.
3. **Era rows carry absolute stats, so Task 5 needs more than `statShares`.** Era rows have `StatPercentEditor_n = 0` and carry absolute values in `StatModifier_bonusAmount_0..9`, along with `MinDamage_/MaxDamage_0..4`, `Resistances_0..6` and `MaxDurability`. For Era items, `statShares` hold the stat ids with share 0. Task 5 must read those extra Era columns, or extend `ClientItem`, to give Era items real stats and damage.
4. **The fixture includes 18 overlapping Era rows.** The lead expected 17 Era rows; the fixture has 35. All 35 are real rows of fixture items, and the extra 18 test that Forever wins.
5. **A stale Era file can be used without provenance.** If `fetch.py` later runs *without* `--fallback-product`, the lock loses the `fallback_*` keys, but a previously cached `ItemSparse.era.csv` stays in `csv/`. `convert.py` copies it and the loader uses it. Task 9 could require the lock to name a fallback build before using `ItemSparse.era`, or `convert.py` could skip the file when the lock does not list it.
6. **`item_class` and `item_subclass` change names when client data is present.** The client's names come from `DisplayName_lang`, as ruled. They are shorter and partly OBSOLETE-tagged: "Axe" for both one- and two-handed axes, "Jewelry(OBSOLETE)", and "WoW Token" as class 18. The app shows whatever the DB holds, so 2B may want `VerboseName_lang` for weapon subclasses.
7. The fixture `build.lock` has no `fallback_*` keys, as allowed. `build_id` will change for the shipped DB because `schema.sql` changed; regeneration is Task 11.

## Fix round 1

**Commit:** `fix(importer): verbose client subclass names; alt-value tests and trimming`

### What changed
1. **(Important) Subclass names now prefer the verbose name.** `importer/src/main/java/dev/wowforever/importer/clientdata/ItemClassNames.java:42` reads `VerboseName_lang` first and falls back to `DisplayName_lang` when it is empty. The class Javadoc at lines 9-13 now says so.
   - Weapon subclasses are now distinct, for example "One-Handed Axes" and "Two-Handed Axes" where both used to be "Axe".
   - Rows without a verbose name keep their display name, for example 2/14 "Miscellaneous" and the class 0 and 9 rows.
   - The static `ItemClasses` table remains the fallback when there is no client data, in `SqliteWriter.writeItemClasses`.
2. **(Minor) Names are trimmed before comparison.** `importer/src/main/java/dev/wowforever/importer/sqlite/ClientItemWriter.java:92-94` compares two strings after `strip()`. A whitespace-only difference no longer creates an alt row. The stored item name is still the client's value.
3. **(Minor) New writer tests in `SqliteWriterClientDataTest`.**
   - `everyReplacedFieldIsRecordedUnderItsColumnName` gives 17204 Questie values `requiredLevel 1`, `class 15` and `subClass 4`, against the client's 0, 7 and 11. It asserts the rows `class_id:questie:15`, `required_level:questie:1` and `subclass_id:questie:4`, each counter at 1, and no name or item_level alt.
   - `namesDifferingOnlyInSurroundingWhitespaceAreNotAltValues` covers the trimming.

### Covering tests
- **`ItemClassNamesTest`**
  - `weaponSubclassNamesAreDistinct` is new. It asserts 2/0 is "One-Handed Axes" and 2/1 is "Two-Handed Axes", that they differ, and that no name repeats under class 2.
  - `emptyVerboseNameFallsBackToTheDisplayName` replaces the old test that ran the fallback the other way.
  - `readsClassAndSubclassNamesKeyedByClassIds` now expects "One-Handed Swords", "Daggers", "Shields" and "Miscellaneous".
- **`SqliteWriterClientDataTest`**
  - `clientClassNamesReplaceTheStaticTable` now expects (2,7) to be "One-Handed Swords".
  - The two writer tests above are new.
- **Red before the fix:** 5 failures. These were ItemClassNamesTest x3, `clientClassNamesReplaceTheStaticTable` and the whitespace test. `everyReplacedFieldIsRecordedUnderItsColumnName` passed against the existing code, so it pins behaviour that was already correct and guards the field labels.

### Command and output

    ./mvnw -B -q -pl importer test

The run exits 0. The importer has 178 tests, with 0 failures, 0 errors and 0 skipped. `ItemClassNamesTest` passes 4 of 4 and `SqliteWriterClientDataTest` passes 8 of 8.
