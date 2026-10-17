# Task 11 report: real data run, shipped-data tests, docs

Status: DONE_WITH_CONCERNS (concerns at the end). Worked in the main checkout `H:\claude_java`, branch
`feature/phase2a-client-data`.

## Commits

| SHA | Subject |
|---|---|
| 33dbee1 | feat(clientdata): lock merge, 5xx retry, needed icons, proc chance and disputed items |
| b5421b1 | fix(importer): disputed note names where Thunderfury's damage comes from |
| 5951db7 | data: client data for wow_classic_beta 1.60.1.70425 (stats, icons, maps, provenance) |
| dc760dc | docs: client data pipeline, NOTICE and shipped-data tests |

The tests for the code went in with the code (33dbee1). The ShippedDataTest additions went in with the docs (dc760dc),
after the data commit, so every commit from 5951db7 on is green. The trailer is `Co-Authored-By: Claude Opus 5.5`: the
session's attribution instruction names that model, and earlier commits on the branch name the model that wrote them.
The brief said Fable 5.1.

## Code steps

- **tables.py:** `SpellAuraOptions` and `SpellRadius` added to `REQUIRED_TABLES` (44). The fixture cutter
  (`fixture.py`) selects `SpellAuraOptions` by SpellID and `SpellRadius` by the kept effects' `EffectRadiusIndex_0/_1`.
  The Python tests cover both.
- **`$h`:** `SpellTables` reads `SpellAuraOptions` optionally (`ClientDataSource.optionalTable`, `DifficultyID` 0
  preferred). `procChance` gives 1 to 100 and nothing for 0 or 101; 101 marks a proc without a percentage roll.
  `SpellDescriptionFormatter` formats `$h`/`$H` as text and inside `${}`.
- **Fixture:** `SpellAuraOptions.csv` (32 rows) and `SpellRadius.csv` (3 rows) were cut with the README's `ARGS` into
  the scratchpad. Only those two files were copied. Every other cut CSV is byte-identical to the fixture's, except
  `UiMapAssignment.csv`, which the fixture extends by hand. The fixture `build.lock` is unchanged.
- **Formatter tests:**
  - 7617 Totem of Infliction now formats "1% chance", and 1293674 formats "15% chance".
  - 12022 gives `$a1` = 30 yards and 440212 gives 5 yards.
  - A ProcChance of 101 keeps `$h` raw.
  - A new test, `withoutTheOptionalTablesProcChanceAndRadiusStayRaw`, uses a temp source holding only the five
    required spell tables.
  - In ItemSetLoaderTest, the Scarlet (4) bonus is now complete.
- **`clientdata.disputed`:** `ClientDataStage.DISPUTED_ITEMS` holds 19019 with its dispute text, documented in the
  Javadoc. The counter is always emitted, and a note lists the items. `ClientDataStageTest` adds two tests, and
  ImportPipelineTest checks the counter is wired.
- **Fetch tool, from the controller's extra item:**
  - `write_lock` merges into the build's existing lock. This run's table hashes replace earlier ones, and other
    entries and the `fallback_*` keys are kept.
  - `file_count` is the number of non-empty `files/*.blp`, and `missing_count` the number of ids in `missing.txt`.
  - `Client._get` retries HTTP 5xx after 2, 4 and 8 s, then raises `FetchError`. A 404 or other 4xx is never retried.
  - Tests cover the merge (unit and CLI), the retry, the persistent 5xx, no retry on 4xx, and an icon download that
    survives a 500. All tests patch the delays to 0.
- **`needed_icons.py`:** a module plus CLI shim with tests. On the pre-import DB it printed 2,925 ids, the same as
  the controller's.

## Real run

The converted output in `C:\Users\aircan80\.cache\wowforever-clientdata\out\current` was reused as the controller made
it: 45 CSVs, 2,925 PNGs including 0.png, 60 map JPEGs, and the lock with 44 tables plus `ItemSparse.era`.

```
./mvnw -B -q -pl importer -am package -DskipTests
java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite \
    --client-data-dir C:/Users/aircan80/.cache/wowforever-clientdata/out/current --fail-on-unsupported
exit=0
```

The import ran twice. The second run followed the disputed-note wording fix, b5421b1, and is the committed one.
`tools/clientdata/build.lock` is a copy of `out/current/build.lock`, which is byte-identical to the cache lock.

Excerpt from the import report:

```
clientdata.coverage.pct = 96            (named 21294, named.matched 20485)
clientdata.items.matched = 20492        (forever 15679, era 4813), iconOnly 791, unmatched 260
clientdata.icons = 2925                 clientdata.icons.missing = 1  (note: 4559256)
clientdata.maps = 57                    clientdata.maps.missingTiles = 0
clientdata.unverified.armor = 0  .block = 186  .damage = 114  .stats = 0
clientdata.unformatted = 171            ($h no longer among the tokens; was the main kind before)
clientdata.unformatted.tokens: $qN 100, $ecN 18, $<id>u 12, $proccooldown 12, $ecim 11, $<id>qN 5, $aN 4, ${...} 4, ...
clientdata.disputed = 1
clientdata.disputed: 19019 (Thunderfury, Blessed Blade of the Windseeker: damage 82 - 153 from Forever's item level 80
  one-hand table (the Era row agrees and adds 16 - 30 Nature), Wowhead's Forever tooltip shows 44 - 115)
clientdata.damage.secondaryDropped = 11
== Conflicts ==  (present)
3236 15852 questie-correction=100.0 questie-wowhead=1.9969 |Δ|=98.00
92 4627 questie-correction=100.0 questie-wowhead=4.8365 |Δ|=95.16
... ; questie-wowhead vs atlasloot: 107 disagreeing of 242 shared
```

The 16–30 Nature is only on the Era row (`MinDamage_1`/`MaxDamage_1`). The Forever row used for 19019 has no
secondary damage, so the note says so rather than "82 - 153 plus 16 - 30".

## Sanity queries

Queries ran read-only with the venv's `sqlite3`.

```
ls -l: 65,736,704 bytes   page_count*page_size: 16049 * 4096 = 65,736,704   (< 100 MB; brief expected ~55 MB)
  of which icon PNGs 12,740,939 bytes, zone map JPEGs 11,330,675 bytes
select count(*) from item where has_client_data=1: 20492   (named items 21301, named without client data 809)
client_source: NULL 1051, era 4813, forever 15679
quality: NULL 1051, 0 1316, 1 7151, 2 6065, 3 3976, 4 1951, 5 25, 6 4, 7 4
icon: 2925 (id 0 present)          zone_map: 57
Westfall (zone 40): ui_map 1436, 1002x668, 209,186-byte JPEG
item_npc_drop disagrees=1: 201     drop_rate_source 11696 >= rated drops 11315
meta: client_build 1.60.1.70425, client_fallback_build 1.15.9.70003, client_item_count 20492
```

Tooltip 17204 Eye of Sulfuras (quality 5, forever):
```
name Eye of Sulfuras | binding Binds when picked up | use Use: The Eye of Sulfuras can be combined with the Sulfuron
Hammer to create Sulfuras, legendary hammer of Ragnaros. | item_level Item Level 60 | sell_price Sell Price: 20g
```
Tooltip 19019 Thunderfury (quality 5, forever):
```
name | Binds when picked up | Unique | One-Hand <tab> Sword | 82 - 153 Damage | Speed 1.90 | (61.84 damage per second)
| +5 Agility | +8 Stamina | +8 Fire Resistance | +9 Nature Resistance | Requires Level 60 | Chance on hit: Blasts your
enemy with lightning, dealing 300 Nature damage ... slowing its attack speed by 20% for 12 sec. | Item Level 80
| Sell Price: 25g 53s 55c          item_damage: 82, 153, school 0, 1900 ms, 61.84 dps
```
Tooltip 10329 Scarlet Belt (quality 2, era):
```
Binds when equipped | Waist <tab> Mail | 123 Armor | +8 Strength | +7 Stamina | Durability 35 / 35 | Requires Level 32
| Chain of the Scarlet Crusade (0/6) | 6 set_item lines | (2) +10 Shadow Resistance | (3) +30 Attack Power against
Undead | (4) Taking damage has a 15% chance to grant Enraging Light ... | (5) Improves your chance to hit by 1.0%
| (6) ... absorbing $1293733s2 damage ... ${$proccooldown/60} min. | Item Level 37 | Sell Price: 25s 7c
| source_note: Stats from Classic Era data; Forever values may differ
```
Top 10 conflicts, as the largest source spread per pair:

| NPC | Item | Name | Sources | Spread |
|---|---|---|---|---|
| 3236 | 15852 | Kodo Horn | correction 100 / wowhead 2.00 | 98.0 |
| 92 | 4627 | Large Stone Slab | correction 100 / wowhead 4.84 | 95.2 |
| 949 | 7267 | Pristine Spider Silk | correction 100 / wowhead 5.44 | 94.6 |
| 10184 | 21108 | Draconic for Dummies | correction 100 / wowhead 6.05 | 94.0 |
| 4539 | 5801 | Kraul Guano | correction 100 / wowhead 8.31 | 91.7 |
| 14460 | 11829 | Un'Goro Ash | correction 100 / wowhead 8.81 | 91.2 |
| 4538 | 5801 | Kraul Guano | correction 100 / wowhead 9.04 | 91.0 |
| 11502 | 21110 | Draconic for Dummies | correction 100 / wowhead 9.24 | 90.8 |
| 3455 | 5078 | Theramore Medal | correction 100 / wowhead 15.56 | 84.4 |
| 6521 | 11829 | Un'Goro Ash | correction 100 / wowhead 17.68 | 82.3 |

These are quest-item corrections at 100 % against Wowhead's observed rates.

**Undecodable icon 4559256 (BLP encoding 3):** it is used by 247877 Rough Wolf Pelt, 269715 Savannah Lion Hide and
279941 Tanning Rack, all with client data. They keep `icon_id` 4559256 with no icon row. The app's
`AssetRepository.iconOrFallback` returns icon 0 for them, which is the ruling's "else id 0 exists". It is documented
in the tools README as a known limitation.

## ShippedDataTest additions

`app/src/test/java/dev/wowforever/app/ShippedDataTest.java`, plus `KnownItemsCsv.java`:
- `clientDataCoversTheNamedItems`: 85 % or more of named items have client data.
- `clientSourceIsForeverOrEraAndForeverDominates`: client_source is forever, era or NULL, never set without client
  data, and era is below forever.
- `committedImportReportShowsTheCoverage`: `/data/import-report.txt` has `clientdata.coverage.pct` of 85 or more.
- `everyClientItemHasAnIcon`: an item without its own icon row needs icon 0, and there are more than 2,000 icons.
- `zoneMaps`: zone_map has 45 rows or more, and Westfall is 1002x668.
- `legendaryTooltips`: 17204 is quality 5 with one "Use: " line, and 19019 is quality 5 with one "Chance on hit:
  Blasts your enemy with lightning" line.
- `everyRatedDropHasItsSources`: drop_rate_source has at least as many rows as the drops with a rate.
- `knownItemsMatchTheOracle`: the importer's `known-items.csv`, found from the working directory upwards, runs
  against the stored `item_stat`, `item_armor`, `item_damage` and `item_tooltip_line`. It uses the same kinds,
  tolerances and normalisation as `KnownItemsTest`.
  - Rows whose item is absent from the DB are skipped; that is only 17783, which Questie does not list.
  - The DB's other 27 rows all match, and at least 25 compared rows are required.

## Tests

- **Python:** `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q` gives 44 passed.
- **Maven:** `./mvnw -B -q verify` exits 0.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 323 | 0 | 0 | 0 |
| app | 198 | 0 | 0 | 0 |

- The importer's `KnownItemsTest.realClientDataMatchesTheOracle` and `EraOverlapOracleTest` ran against the real
  data and were not skipped.
- The first full verify after the import failed twice, and both are fixed:
  - The bundled `app/src/main/resources/NOTICE.md` must equal the root `NOTICE.md`, so it is now synced.
  - `knownItemsMatchTheOracle` reported 17783 as "no client data"; it now skips items that are not in the DB.

## Files changed

- **Tools:** `tools/clientdata/clientdata/{tables,fixture,fetch,http,needed_icons}.py`,
  `tools/clientdata/needed_icons.py`, `tools/clientdata/tests/{test_fetch,test_fixture,test_needed_icons}.py`,
  `tools/clientdata/README.md` and `tools/clientdata/build.lock`.
- **Importer:** `importer/.../clientdata/{SpellTables,SpellDescriptionFormatter,ClientDataStage}.java`. Tests:
  `SpellDescriptionFormatterTest`, `ItemSetLoaderTest`, `ClientDataStageTest` (new) and `ImportPipelineTest`.
  Fixture: `fixtures/clientdata/csv/{SpellAuraOptions,SpellRadius}.csv`.
- **App:** `app/src/main/resources/data/{wowforever.sqlite,build-id.txt,import-report.txt}`,
  `app/src/main/resources/NOTICE.md`, `ShippedDataTest.java`, and `KnownItemsCsv.java` (new).
- **Root:** `README.md` (Client data section, counts, sizes, limitations, credits) and `NOTICE.md` (Blizzard
  attribution, verbatim text from the brief).

## Self-review

- **Brief steps:**
  - Step 1 is done with the controller's cache, as ruled; wow.tools.local was not used.
  - Steps 2, 3 and 5 are done. Step 4 was skipped per the ruling, and the app was not launched.
- **Rulings:**
  - The two tables are required in Python and optional in Java.
  - `$h` is handled, with tests.
  - Coverage is 96 %, at least 85.
  - The disputed counter and note are data-driven.
  - The extra shipped checks are in.
  - The DB size is recorded.
- **Extra items:** the lock merge, the 5xx retry and `needed_icons.py` are done, with tests and README entries. The
  BLP encoding 3 limitation is in the tools README.
- **Behaviour:** the Java side still works without the two new tables; a test pins it.

## Concerns

- **DB size:** the DB is 66 MB, not about 55 MB. It is under the 100 MB budget; icons take 12.7 MB and maps 11.3 MB.
- **Icons:** 925 items with client data have no icon id and fall back to "?". 3 more have the undecodable icon.
- **Maps:** convert produced 60 maps, but only the 57 assigned to imported zones are embedded.
- **Unformatted descriptions:** 171 spells still have placeholders. The largest kind is `$qN` with 100 spells, then
  `$ecN`/`$ecim` and `$proccooldown`. The README lists this as a limitation.
- **Commit trailer:** Opus 5.5, not Fable 5.1, as explained above.
- **About dialog:** `MainFrame.aboutText` was not changed to mention the Blizzard attribution. NOTICE.md and the
  bundled copy carry it, which the brief asked for.
