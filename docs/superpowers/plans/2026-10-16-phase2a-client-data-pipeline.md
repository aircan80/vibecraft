# Phase 2A: Client Data Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich the shipped `wowforever.sqlite` with Blizzard client data for the Forever build (item quality, stats, damage/armour, tooltip lines, item sets, 56 px icons, fully revealed zone maps) and with drop-rate provenance, through a build-time pipeline, and surface the new data minimally in the existing app.

**Architecture:** A Python tool (`tools/clientdata/`) fetches DB2 CSVs and BLP files from a configurable base URL (wow.tools.local against the owner's local client, or wago.tools) into a cache and converts images to PNG/JPEG. The Java importer gains a `clientdata` stage that reads that output directory, computes item values, renders tooltip lines, embeds assets as BLOBs, and records provenance; the app's repositories and a few panels read the new columns.

**Tech Stack:** Python 3.13 + Pillow (BLP decode) + requests + pytest in a project venv (build-time only); Java 21, existing Maven reactor; sqlite-jdbc; Swing.

**Spec:** `docs/superpowers/specs/2026-10-16-phase2-client-data-and-ui-design.md` (sections 2–4 and 8). Supporting: `2026-10-16-forever-datamining-feasibility.md`.

## Global Constraints

- Branch from `main` (tag `v0.1.0`): `feature/phase2a-client-data`. Every v0.1 test keeps passing. `./mvnw -B verify` green at the end of every task; Python tests via `tools/clientdata/.venv` and `pytest`.
- Network only in `tools/clientdata/fetch.py`; the Java importer and the app never touch the network. Fetch is throttled (≤ 4 requests/s), sends `User-Agent: wow-forever-browser-pipeline (+https://github.com/<owner>/wow-forever-browser)`, and is idempotent against its cache. Default `--base-url http://localhost:8080` (wow.tools.local); alternative `https://wago.tools`. Products: `wow_classic_beta` now, `wowf` later. Build pinned in `tools/clientdata/build.lock`.
- Never modify anything under `C:\Program Files (x86)\World of Warcraft`.
- Schema additions exactly as spec §4.3 amended by rulings 14–16 (`zone.ui_map_id`, `zone_map(ui_map_id PK, …)`, `item_alt_value`, `boss_loot.disagrees/source_count`). `item_tooltip_line` is the UI contract. Quality colours: 0 `#9d9d9d`, 1 `#ffffff`, 2 `#1eff00`, 3 `#0070dd`, 4 `#a335ee`, 5 `#ff8000`, 6 `#e6cc80`, 7 `#00ccff`.
- Anti-goals: no guessed numbers (unverifiable category → NULL + `clientdata.unverified.<category>` counter); unknown spell placeholder → raw text + `clientdata.unformatted`; `--skip-client-data` still yields a valid DB; the app opens a v0.1-shaped DB without client tables gracefully? No: the shipped DB always has the tables; `--skip-client-data` creates them empty.
- Coverage gate: `--fail-on-unsupported` exits 2 when `clientdata.items.matched` < 85 % of named items.
- Assets: icons 56×56 RGBA PNG with the in-game border cropped, fallback icon id 0; maps 1002×668 JPEG q85, one per UiMap.
- Java is not on the shell PATH for subagents (prefix with the JAVA_HOME export). Commit after every task.

## Review Focus

1. A CSV cell containing commas, quotes or newlines inside quotes (item descriptions and spell text do) must parse exactly; a shifted column silently corrupts every later field. Pinned in Task 3 (`CsvTableTest.quotedCellsWithCommasNewlinesAndEscapedQuotes`).
2. An item whose `ItemSparse` row exists but whose stat budget columns are all zero (white vendor items) must produce no `item_stat` rows and no "+0" lines, not a crash. Pinned in Task 5 (`ItemStatsCalculatorTest.zeroBudgetProducesNoStats`).
3. A spell description with an unknown placeholder must come through raw, counted, and never throw. Pinned in Task 6 (`SpellDescriptionFormatterTest.unknownPlaceholderKeepsRawText`).
4. A zone whose UiMap has no art or whose tiles are missing from the output directory must be skipped with a counter, not abort the import. Pinned in Task 7 (`ZoneMapLoaderTest.missingTilesAreCountedNotFatal`).
5. Running the importer with `--skip-client-data` and opening the result in the app must work (no icons, white item names). Pinned in Task 9 (`ImportPipelineTest.skipClientDataStillProducesOpenableDb`) and Task 10 (`ItemRepositoryTest.itemWithoutClientDataHasNullQuality`).

---

### Task 1: Python fetch tool with cache, throttle and lock file

**Files:**
- Create: `tools/clientdata/README.md`, `tools/clientdata/requirements.txt` (`Pillow>=11`, `requests>=2.32`, `pytest>=8`), `tools/clientdata/clientdata/__init__.py`, `tools/clientdata/clientdata/fetch.py`, `tools/clientdata/clientdata/http.py`, `tools/clientdata/clientdata/tables.py`, `tools/clientdata/fetch.py` (CLI shim), `tools/clientdata/tests/test_fetch.py`, `tools/clientdata/pytest.ini`, `.gitignore` entries (`tools/clientdata/.venv/`, `tools/clientdata/.pytest_cache/`)
- Create: `tools/clientdata/build.lock` is generated, not committed until Task 11.

**Interfaces:**
- `tables.REQUIRED_TABLES`: the spec §4.1 table list (46 names) as a tuple.
- `http.Client(base_url, user_agent, rate_per_second=4.0, session=None)`: `get_text(path) -> str`, `get_bytes(path) -> bytes`; raises `FetchError(path, status)` on non-200; sleeps so that at most `rate_per_second` requests start per second.
- `fetch.resolve_build(client, product, build=None) -> Build(product, build, date)`: reads `/api/builds` (JSON list of `{product, version, created_at …}`; confirm the exact field names against the live response and against wow.tools.local's `/api/builds` and document both in README) and returns the newest for `product` unless `build` is given.
- `fetch.fetch_tables(client, build, cache_dir) -> dict[str, Path]`: for each required table GET `/db2/{Table}/csv?build={build}` into `cache_dir/{build}/csv/{Table}.csv` unless present and non-empty.
- `fetch.fetch_files(client, product, fdids: Iterable[int], cache_dir) -> dict[int, Path]`: GET `/api/casc/{fdid}?download&branch={product}` into `cache_dir/{build}/files/{fdid}.blp` unless present; a 404 is recorded in `missing.txt`, not fatal.
- `fetch.write_lock(cache_dir, build, tables, files) -> Path`: writes `build.lock` JSON `{product, build, fetched_at, base_url, tables: {name: sha256}, file_count, missing_count}`.
- CLI: `python fetch.py --product wow_classic_beta [--build B] [--base-url URL] --cache DIR [--icons-from ITEM_CSV] [--maps]`: fetches tables; with `--icons-from` reads `Item.csv` from the cache and fetches every distinct `IconFileDataID`; with `--maps` fetches every `UiMapArtTile.FileDataID` and `WorldMapOverlayTile.FileDataID`.

- [ ] **Step 1: Create the venv and write the failing tests**

`python -m venv tools/clientdata/.venv && tools/clientdata/.venv/Scripts/pip install -r tools/clientdata/requirements.txt` (Windows path; README documents the POSIX form).

```python
# tests/test_fetch.py — uses http.server in a thread serving canned responses from a dict
def test_resolve_build_picks_newest_for_product(server): ...   # two wow_classic_beta entries, one wowf → newest beta
def test_fetch_tables_skips_cached_files(server, tmp_path): ... # second call makes zero requests (server.hits unchanged)
def test_fetch_files_records_404_as_missing(server, tmp_path): ...
def test_throttle_respects_rate(server): ...                     # 8 requests at rate 4/s take ≥ 1.5 s
def test_lock_file_contents(tmp_path): ...                       # keys present, sha256 matches file
def test_user_agent_header_sent(server): ...
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q`. Expected: import errors.
- [ ] **Step 3: Implement `http.py`, `tables.py`, `fetch.py`**
- [ ] **Step 4: Run tests to verify they pass** — same command. Expected: 6 passed.
- [ ] **Step 5: Smoke against wago.tools for one table** — Run: `tools/clientdata/.venv/Scripts/python tools/clientdata/fetch.py --product wow_classic_beta --base-url https://wago.tools --cache ~/.cache/wowforever-clientdata --tables ItemClass` (add a `--tables` filter for this). Expected: `ItemClass.csv` cached (small), lock written. Record the `/api/builds` field names in README.
- [ ] **Step 6: Commit** — `git add tools/clientdata .gitignore && git commit -m "feat(clientdata): fetch tool with cache, throttle and lock file"`

---

### Task 2: Python convert tool: icons and maps

**Files:**
- Create: `tools/clientdata/clientdata/convert.py`, `clientdata/icons.py`, `clientdata/maps.py`, `tools/clientdata/convert.py` (CLI shim), `tools/clientdata/tests/test_convert.py`, fixtures `tools/clientdata/tests/fixtures/icon.blp` (the real 134124 icon from `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\research\icons\134124.beta.blp`, 3.9 KB), `tiles/*.blp` (four real Westfall tiles from `…\research\westfall\`, ~45 KB each) and small CSV excerpts for `UiMapArt`, `UiMapArtTile`, `UiMapArtStyleLayer`, `UiMapXMapArt`, `UiMap`, `WorldMapOverlay`, `WorldMapOverlayTile` covering one map.

**Interfaces:**
- `icons.convert_icon(blp_path, out_png, size=56, crop_px=None) -> None`: Pillow opens the BLP, converts to RGBA, crops the in-game border (default crop 4 px of a 64 px source; constant documented), resizes with LANCZOS to `size`, saves PNG. `icons.fallback_png(out_png)`: a 56×56 question-mark PNG drawn with Pillow (no external asset).
- `maps.MapSpec(ui_map_id, art_id, rows, cols, base_tiles: dict[(row,col)] -> fdid, overlays: list[Overlay(offset_x, offset_y, rows, cols, tiles)], layer_w, layer_h)`; `maps.read_specs(csv_dir) -> list[MapSpec]` from the UiMap* and WorldMapOverlay* CSVs (chain in spec §4.1); `maps.compose(spec, files_dir) -> Image` stitches base tiles at `(col*256, row*256)`, alpha-composites every overlay tile at `(offset_x + col*256, offset_y + row*256)`, crops to `(layer_w, layer_h)` (1002×668 for style 1); `maps.save_jpeg(img, path, quality=85)`.
- CLI: `python convert.py --cache DIR --build B --out DIR`: writes `out/csv/*.csv` (copies), `out/icons/{fdid}.png` for every cached BLP referenced by `Item.IconFileDataID`, `out/icons/0.png` fallback, `out/maps/{ui_map_id}.jpg`, `out/maps.json` (`[{ui_map_id, name, width, height, tiles, overlays, missing_tiles}]`), and copies `build.lock`. Deterministic: PNG/JPEG written without timestamps.

- [ ] **Step 1: Write the failing tests**
```python
def test_icon_converts_to_56px_rgba(tmp_path): ...     # size (56,56), mode RGBA, corner pixel alpha > 0
def test_fallback_icon_is_56px(tmp_path): ...
def test_read_specs_builds_chain(fixture_csv_dir): ...  # one MapSpec: 3×4 base tiles, N overlays, layer 1002×668
def test_compose_stitches_and_crops(fixture): ...       # output size (1002,668); pixel at an overlay location differs from base-only compose
def test_missing_tile_is_recorded_not_fatal(fixture): ...
def test_convert_is_deterministic(tmp_path): ...        # two runs → identical bytes
```
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Implement** — Pillow's `BlpImagePlugin` reads BLP2 DXT1/3/5; if a tile fails to decode, record it in `missing_tiles` and leave the area transparent.
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit** — `git commit -m "feat(clientdata): convert icons to PNG and compose zone maps"`

---

### Task 3: Java CSV reader, client-data source and fixture subset

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/clientdata/CsvTable.java`, `ClientDataSource.java`, `ClientDataOptions.java`, `BuildLock.java`
- Create fixtures: `importer/src/test/resources/fixtures/clientdata/` = a converted output directory in miniature: `csv/` with row subsets of every required table for ~30 items (the fixture item ids from v0.1 that have ItemSparse rows: 25, 730, 2874, 5191, 14145, 14148, 14149, 16800, 19019 plus 17204, 19137, 18422, 13262, 12640, 16865, 10329, 2092, 6948, 117, 159, 4500, 7736 and a set piece per Tier 1 e.g. 16800's set 203), `icons/*.png` for their icons (converted with Task 2 from real BLPs fetched by Task 1 — run the real fetch for these ids only), `icons/0.png`, `maps/1436.jpg` (Westfall, real, converted) and `maps/1429.jpg` (Elwynn) plus `maps.json`, `build.lock`.
- Test: `importer/src/test/java/dev/wowforever/importer/clientdata/CsvTableTest.java`, `ClientDataSourceTest.java`

**Interfaces:**
- `CsvTable`: `static CsvTable read(Path csv)` (UTF-8, optional BOM, RFC 4180 quoting, embedded newlines), `List<String> columns()`, `int column(String name)` (throws `IllegalArgumentException` naming the file and column), `Iterable<Row> rows()`, `Row.get(String col) -> String`, `Row.getInt`, `Row.getLong`, `Row.getDouble` (empty → `Optional`/null variants), `Map<Long, Row> indexByLong(String idColumn)`, `Map<Long, List<Row>> groupByLong(String col)`. Streams the file (no full-file String) so the 5 MB ItemSparse loads in < 1 s.
- `BuildLock(product, build, fetchedAt, baseUrl, Map<String,String> tableSha)` + `static BuildLock read(Path)`.
- `ClientDataSource(Path outDir)`: `BuildLock lock()`, `CsvTable table(String name)` (cached), `Optional<Path> icon(long fdid)`, `Optional<Path> map(long uiMapId)`, `List<MapInfo> maps()` from `maps.json` (`record MapInfo(long uiMapId, String name, int width, int height, int tiles, int overlays, int missingTiles)`).
- `ClientDataOptions(Optional<Path> dir, boolean skip)` parsed from `--client-data-dir` / `--skip-client-data` (added to `ImportOptions`; neither given → error naming both flags).

- [ ] **Step 1: Fetch and convert the fixture inputs** using Tasks 1–2 against wago.tools for `wow_classic_beta` (all 46 tables, the ~30 items' icons, Westfall and Elwynn tiles), then cut the CSV subsets with a small script kept at `tools/clientdata/make_fixture.py` (committed; takes the item id list and writes the subset preserving headers and every row referenced transitively: ItemXItemEffect → ItemEffect → Spell/SpellEffect/SpellMisc/SpellDuration; ItemSet/ItemSetSpell; RandPropPoints/ItemDamage*/ItemArmor* rows for the item levels used; ItemClass/ItemSubClass whole; UiMap chain rows for the two maps; AreaTable rows for zones 12, 40 and their sub-zones).
- [ ] **Step 2: Write the failing tests**
```java
class CsvTableTest {
  @Test void quotedCellsWithCommasNewlinesAndEscapedQuotes() { /* inline CSV: a,"x, ""y""\nz",3 → three columns, middle = x, "y"<newline>z */ }
  @Test void bomAndCrlfAreTolerated() {}
  @Test void unknownColumnNamesTheFile() {}
  @Test void itemSparseFixtureLoadsExpectedColumns() { /* ID, ItemLevel, OverallQualityID, StatModifier_bonusStat_0, StatPercentEditor_0 present; row 17204 quality 5 */ }
}
class ClientDataSourceTest {
  @Test void readsLockTablesIconsAndMaps() { /* lock.product() == "wow_classic_beta"; icon(134124) present; map(1436) present; maps() has 2 */ }
  @Test void optionsRequireDirOrSkip() {}
}
```
- [ ] **Step 3: Run tests to verify they fail** — `./mvnw -B -q -pl importer test`.
- [ ] **Step 4: Implement** — [ ] **Step 5: Run tests to verify they pass** — [ ] **Step 6: Commit** — `git commit -m "feat(importer): client-data CSV reader, source directory and fixtures"`

---

### Task 4: Item core join, schema additions and alt values

**Files:**
- Modify: `importer/src/main/resources/dev/wowforever/importer/schema.sql` (all §4.3 tables/columns incl. rulings 14–16), `sqlite/EntityWriter.java` or a new `sqlite/ClientItemWriter.java`, `sqlite/SqliteWriter.java` (write order: items first, then client tables)
- Create: `importer/src/main/java/dev/wowforever/importer/clientdata/ItemSparseLoader.java`, `ClientItem.java` (record of all per-item client fields), `ItemClassNames.java` (reads `ItemClass`/`ItemSubClass` CSVs; replaces the static `ItemClasses` names when client data is present)
- Test: `ItemSparseLoaderTest.java`, `SqliteWriterClientDataTest.java`

**Interfaces:**
- `ItemSparseLoader.load(ClientDataSource src, Set<Long> questieItemIds, ImportReport report) -> Map<Long, ClientItem>`: joins `Item` (class, subclass, inventory type, icon fdid) and `ItemSparse` (name `Display_lang`, quality `OverallQualityID`, item level, required level, bonding, sell/buy price, max stack `MaxCount`, item set, description `Description_lang`, allowable class/race, required skill/rank, flags, start quest) for ids in `questieItemIds`; counts `clientdata.items.matched` (ItemSparse present), `clientdata.items.iconOnly` (Item only), `clientdata.items.unmatched`; writes `clientdata.unmatched.ranges` note (id ranges of unmatched items in 10k buckets).
- `ClientItem(long id, boolean hasSparse, Integer quality, Integer itemLevel, Integer requiredLevel, Integer classId, Integer subclassId, Integer inventoryType, Integer bonding, Long sellPrice, Long buyPrice, Integer maxStack, Long iconId, Long itemSetId, String name, String description, Integer allowableClass, Long allowableRace, Integer requiredSkill, Integer requiredSkillRank, List<Integer> flags, Long startQuestId, List<StatShare> statShares, Integer delayMs, Double dmgVariance, Integer damageType)`; `record StatShare(int statId, int share)`.
- Writer rules (ruling 14): when `hasSparse`, client `name`, `item_level`, `required_level`, `class_id`, `subclass_id` replace Questie's in `item` and `search_index`; differing Questie values go to `item_alt_value(item_id, field, 'questie', value)`; `count("clientdata.alt.<field>")`. `has_client_data` = `hasSparse ? 1 : 0`; `icon_id` set when the Item row exists.
- `item_class`/`item_subclass` rows come from `ItemClassNames` when client data is present (fallback to the static table otherwise).

- [ ] **Step 1: Write the failing tests** (loader on the fixture: 17204 quality 5, icon 134124, set null; 16800 set 203; 25 "Worn Shortsword" hasSparse; a questie-only fixture id (190179) unmatched; writer: `item.quality`, `has_client_data`, `item_alt_value` row when the fixture's Questie name differs — pick 2874 whose Questie name "An Unsent Letter" vs client name; assert at least the field recorded)
- [ ] **Step 2: Run tests to verify they fail** — [ ] **Step 3: Implement** — [ ] **Step 4: Run tests to verify they pass** (whole importer suite) — [ ] **Step 5: Commit** — `git commit -m "feat(importer): item core client data, schema additions and alt values"`

---

### Task 5: Stats, damage, armour calculators and the known-items gate

**Files:**
- Create: `clientdata/ScalingTables.java` (RandPropPoints, ItemDamage*, ItemArmor*, ArmorLocation loaders), `ItemStatsCalculator.java`, `ItemDamageCalculator.java`, `ItemArmorCalculator.java`, `StatNames.java` (stat id → "Stamina" etc., the ITEM_MOD table from the wowdev wiki; resistances 51–55 mapped to schools)
- Create: `importer/src/test/resources/fixtures/clientdata/known-items.csv` and `clientdata/KnownItemsOracle.md` (how it was produced)
- Test: `ItemStatsCalculatorTest`, `ItemDamageCalculatorTest`, `ItemArmorCalculatorTest`, `KnownItemsTest` (fixture-driven; also runs against the real converted data when `~/.cache/wowforever-clientdata/out/<build>` exists)

**Interfaces:**
- `ScalingTables.load(ClientDataSource)`; `randPropBudget(int itemLevel, int quality, int inventoryType) -> OptionalInt` (column by quality: Epic/Superior/Good; slot group by inventory type per the wowdev wiki table; document the mapping in a comment table); `dps(DamageKind kind, int itemLevel, int quality) -> OptionalDouble`; `armor(int itemLevel, int quality, int inventoryType, int material) -> OptionalInt`; `shieldArmor(...)`.
- `ItemStatsCalculator.stats(ClientItem, ScalingTables) -> List<ItemStat(int statId, int value)>`: `value = Math.round(budget * share / 10000.0)`; zero shares skipped; resistances (stat ids 51–55 and 56 if present) included in order.
- `ItemDamageCalculator.damage(ClientItem, ScalingTables) -> Optional<ItemDamage(int min, int max, int school, int delayMs, double dps)>`.
- `ItemArmorCalculator.armor(ClientItem, ScalingTables) -> Optional<ItemArmor(int armor, int block)>`.
- Each returns empty and counts `clientdata.unverified.<category>` when its inputs are inconsistent (missing table row, unknown kind).
- `known-items.csv` columns: `item_id, name, expected_lines` (expected lines `|`-separated in game order, e.g. `Binds when picked up|One-Hand Sword|44 - 115 Damage|Speed 1.90|(41.8 damage per second)|+5 Agility|+8 Stamina|+8 Fire Resistance|+9 Nature Resistance|Requires Level 60`), `compare_kinds` (comma list of tooltip kinds the pipeline implements that this row verifies). Produced with the **oracle** (ruling 10): run wow.tools.local (download `Release-win-x64.zip` 0.9.9 from `https://github.com/Marlamin/wow.tools.local/releases/latest` into `C:\Users\aircan80\tools\wow.tools.local\`, run it with `-wowFolder "C:\Program Files (x86)\World of Warcraft" -wowProduct wow_classic_beta`, read `/dbc/tooltip/item/{id}` for the 25 ids) or, if that cannot be made to run within an hour, read Wowhead's Forever item pages for those 25 items by hand (one WebFetch per item; document which oracle was used in `KnownItemsOracle.md`).

- [ ] **Step 1: Write the failing tests** (worked examples with hand-computed values from the fixture tables; `zeroBudgetProducesNoStats`; `KnownItemsTest` reading the CSV and comparing `TooltipBuilder` output — stub `TooltipBuilder` until Task 6 by comparing the stat/damage/armour lines only: `compare_kinds` limited to `damage,speed,dps,armor,stat,resist` in this task)
- [ ] **Step 2: Run tests to verify they fail** — [ ] **Step 3: Implement; iterate on the formula until every known item in the compared kinds matches** (if a category cannot be matched after honest effort, remove it from every row's `compare_kinds`, count `clientdata.unverified.<category>`, and say so in the report) — [ ] **Step 4: Run tests to verify they pass** — [ ] **Step 5: Commit** — `git commit -m "feat(importer): item stat, damage and armour computation with known-items gate"`

---

### Task 6: Spell description formatter, item effects, sets and tooltip lines

**Files:**
- Create: `clientdata/SpellDescriptionFormatter.java`, `SpellTables.java` (Spell, SpellName, SpellEffect, SpellMisc, SpellDuration loaders), `ItemEffectLoader.java`, `ItemSetLoader.java`, `TooltipBuilder.java`, `TooltipLine.java` (`record TooltipLine(int ordinal, String kind, String text, String colour)`), `BindingNames.java`, `InventoryTypeNames.java`, `ClassRaceNames.java` (ChrClasses/ChrRaces CSVs)
- Modify: writer for `item_effect`, `item_set`, `item_set_item`, `item_set_bonus`, `item_tooltip_line`
- Test: `SpellDescriptionFormatterTest` (table of 30 real descriptions from the fixture spells with expected output; `unknownPlaceholderKeepsRawText`), `ItemEffectLoaderTest`, `ItemSetLoaderTest`, `TooltipBuilderTest` (order and colours), extend `KnownItemsTest` to all kinds.

**Interfaces:**
- `SpellDescriptionFormatter(SpellTables).format(long spellId, String template) -> Formatted(String text, boolean complete)`: supports `$sN`, `$oN` (over-time = base × ticks), `$d`/`$D` (duration from SpellMisc→SpellDuration, "10 sec"/"2 min"), `$aN` (radius), `$xN` (chain targets), `$tN` (period), `$/N;sN`, `${expr}` with `+ - * /` and parentheses, `$lsingular:plural;`, `$<id>sN`, `$g…:…;` (first form), colour codes `|cffRRGGBB … |r` stripped, `\n` kept; absolute values for negative base points when the template reads "by $s1" (rule: `$sN` prints `abs(base)` unless preceded by a sign in the template). Unknown `$…` → left as-is, `complete=false`.
- `TooltipBuilder.build(ClientItem, stats, damage, armor, effects, set, names) -> List<TooltipLine>` in spec order; colours: name = quality colour; use/equip/proc `#1eff00`; flavour `#ffd100`; item level/sell price muted `#9d9d9d`; everything else `#ffffff`. Kinds exactly as spec §4.3.
- Writer: `item_effect(trigger 0 Use/1 Equip/2 Chance on hit, spell_id, text)`, sets, `item_tooltip_line`; counters `clientdata.unformatted` (with spell ids in a note), `clientdata.tooltip.lines`.

- [ ] Steps 1–5 as before; commit `feat(importer): spell description formatting, item effects, sets and tooltip lines`

---

### Task 7: Icons and zone maps into the database

**Files:**
- Create: `clientdata/IconLoader.java`, `ZoneMapLoader.java`; modify writer for `icon`, `zone_map`, `zone.ui_map_id`; `meta` keys.
- Test: `IconLoaderTest`, `ZoneMapLoaderTest` (`missingTilesAreCountedNotFatal`, area → UiMap via Questie's `areaIdToUiMapId.lua` fixture + `UiMapAssignment` fallback + parent inheritance), writer tests reading the BLOB back and decoding it with `ImageIO` (56×56; JPEG 1002×668).

**Interfaces:**
- `IconLoader.load(ClientDataSource, Collection<ClientItem>) -> Map<Long, byte[]>`: PNG bytes for every distinct `iconId` with a file, plus id 0 fallback; counts `clientdata.icons`, `clientdata.icons.missing`.
- `ZoneMapLoader.load(ClientDataSource, Path questieDbRoot, Map<Long, Zone> zones) -> ZoneMaps(Map<Long /*zoneId*/, Long /*uiMapId*/> zoneToMap, Map<Long, MapImage(int w, int h, byte[] jpeg)> maps)`: reads `support/Forever/Zones/areaIdToUiMapId.lua` (long string `ZoneDB.private.areaIdToUiMapIdOverride`, via the existing lua-table API), then `UiMapAssignment.csv` for unmapped ids, then parent inheritance; only UiMaps with a file in `maps/` are included; counters `clientdata.maps`, `clientdata.maps.missingArt`, `clientdata.zones.mapped/unmapped`.
- `meta`: `client_product`, `client_build`, `client_fetched_at`, `client_item_count`, `icon_count`, `zone_map_count`.

- [ ] Steps 1–5; commit `feat(importer): embed icons and zone maps`

---

### Task 8: Drop-rate provenance and conflict report

**Files:**
- Modify: `drops/DropRates.java` (keep every source: `allSources(npcId, itemId) -> List<DropRate>`), `drops/DropRateLoader.java`, `sqlite/NpcDrops.java`/`AtlasWriter.java` (write `drop_rate_source`, `disagrees`, `source_count` on `item_npc_drop` and `boss_loot`), `report/ImportReport.java` (`== Conflicts ==` section: top 50 disagreements by absolute difference and counts per source pair), `ImportPipeline`.
- Test: `DropRatesTest` (all sources kept; disagreement rule > 2 points), writer tests (fixture pair (639, 2874): sources questie-correction 60.0, atlasloot-override 57.0 (item-level), questie-wowhead 56.07 → `source_count 3`, `disagrees 1`), `ImportReportTest` conflicts section.

- [ ] Steps 1–5; commit `feat(importer): drop-rate provenance and conflict report`

---

### Task 9: Pipeline integration, CLI flags and coverage gate

**Files:**
- Create: `clientdata/ClientDataStage.java` (orchestrates Tasks 4–7 in order; no-op that creates empty tables when skipped); modify `ImportPipeline`, `ImportOptions` (`--client-data-dir`, `--skip-client-data`), `Main` (exit 2 when `clientdata.items.matched` < 85 % of named items unless skipped), README importer section, `import-data` Maven profile (adds `--client-data-dir ${clientdata.dir}` property with the default `~/.cache/wowforever-clientdata/out/current`).
- Test: `ImportPipelineTest.endToEndWithClientDataFixture` (every new table populated; `item_tooltip_line` for 17204 contains "Use:"-kind line; `zone.ui_map_id` for 40 = 1436; icon BLOB for 134124; `drop_rate_source` rows), `skipClientDataStillProducesOpenableDb`, `MainTest.coverageGateExits2`.

- [ ] Steps 1–5; commit `feat(importer): client-data stage wired into the pipeline with coverage gate`

---

### Task 10: Minimal app consumers

**Files:**
- Modify: `app/.../model/ItemSummary` (+ `quality`, `iconId`), `ItemDetail` (+ `ItemCard card`: `record ItemCard(Integer quality, List<TooltipLine> lines, Long iconId, Integer inventoryType, Integer bonding, Long sellPrice)`), `DropSource`/`LootRow` (+ `boolean disagrees`, `int sourceCount`), `ZoneDetail` (+ `Optional<ZoneMap>`: `record ZoneMap(long uiMapId, int width, int height, byte[] jpeg)`); repositories (`ItemRepository.detail/list`, `SearchRepository` subtitles use quality text? no — only colour), new `AssetRepository.icon(long id) -> Optional<byte[]>`, `ZoneRepository.map(long zoneId)`; `Repositories`.
- Modify UI: `Columns.items()`/search hits: item names coloured by quality (text variants from the UI review table: 0 `#9D9D9D`, 1 `#E4E1D8`/`#1C1F24`, 2 `#3FE23A`/`#137A0E`, 3 `#4A9EFF`/`#0058B0`, 4 `#B861FF`/`#7E2BC4`, 5 `#FF9A33`/`#B35A00`, 6 `#E6CC80`/`#8A6D1E`) via a `QualityColors` class; `ItemDetailPanel`: 56 px icon left of the header (fallback icon when missing), quality-coloured name, a `TooltipLinesPanel` (plain `JLabel` stack with colours) under the header; drop/loot tables show "≠" after the rate when `disagrees`; `ZoneDetailPanel`: a `MapImagePanel` (scaled to width, aspect kept) at the top when a map exists. No pins yet.
- Test: repository tests on the fixture DB rebuilt with the client-data fixture (update `FixtureDb` to pass `--client-data-dir`), `QualityColorsTest`, `ItemDetailPanelTest.cardShowsIconQualityAndLines`, `ItemRepositoryTest.itemWithoutClientDataHasNullQuality`, `ZoneDetailPanelTest.mapShownWhenPresent`, `AppControllerTest` unchanged.

- [ ] Steps 1–5; commit `feat(app): quality colours, icons, tooltip lines, zone map images and disagreement markers`

---

### Task 11: Real data run, shipped-data tests, docs

**Files:**
- Create/modify: `app/src/main/resources/data/wowforever.sqlite` (regenerated), `build-id.txt`, `import-report.txt`, `tools/clientdata/build.lock` (committed), `README.md` (Client data section: prerequisites, the three commands, sizes, the beta→wowf switch), `NOTICE.md` (Blizzard assets attribution: "World of Warcraft® and Blizzard Entertainment® are trademarks or registered trademarks of Blizzard Entertainment, Inc. Icons and map art are © Blizzard Entertainment and are included for reference; no affiliation"), `ShippedDataTest` additions.

- [ ] **Step 1: Run the real pipeline** on this machine: start wow.tools.local against the local install (`wow_classic_beta`; if it cannot serve the endpoints the fetcher expects within an hour, use `--base-url https://wago.tools`), `fetch.py --product wow_classic_beta --cache … --icons-from --maps`, `convert.py`, then `java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite --client-data-dir … --fail-on-unsupported`. Expected exit 0; report shows `clientdata.items.matched ≥ 85 %`, icons ≈ 2,900, maps ≈ 51, conflicts section present, unverified counters listed.
- [ ] **Step 2: Sanity queries** (paste results in the report): DB size (expect ≈ 55 MB, must be < 100 MB); `select count(*) from item where has_client_data=1`; `select quality, count(*) from item group by quality`; `select count(*) from icon`; `select count(*) from zone_map`; tooltip lines for 17204, 19019, 10329 printed; `select count(*) from item_npc_drop where disagrees=1`; top 10 conflicts.
- [ ] **Step 3: `ShippedDataTest` additions**: has_client_data ≥ 85 % of named items; every such item has an icon row; `zone_map` ≥ 45; 17204 tooltip has a Use line and quality 5; 19019 quality 5 with a Chance-on-hit line; Westfall (40) has a map 1002×668; `drop_rate_source` count ≥ rated drop rows; `KnownItemsTest` passes against the shipped DB.
- [ ] **Step 4: Launch the app, visually check** (controller) an item card, quality colours in a list, and the Westfall map on the zone page.
- [ ] **Step 5: Commit** data, lock and docs: `git commit -m "data: client data for wow_classic_beta <build> (stats, icons, maps, provenance)"` plus `docs:` commit.
