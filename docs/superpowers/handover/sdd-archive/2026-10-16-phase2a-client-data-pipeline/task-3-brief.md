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

