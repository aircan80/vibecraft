# Task 9 report: pipeline integration, CLI flags and coverage gate

**Status:** DONE_WITH_CONCERNS
**Commit:** `8c460ea feat(importer): client-data stage wired into the pipeline with coverage gate` (branch `feature/phase2a-client-data`, not pushed)

## What was implemented

- **`clientdata/ClientDataStage`** (new). `run(ClientDataOptions, tables, zones, questieDbRoot, report) -> ClientDataBundle`.
  Order: `ClientDataSource` + `lock()` (stale-Era guard fires here) → `ItemSparseLoader.load` (all item ids after corrections)
  → `ItemClassNames.load` → `ItemValues.compute(ScalingTables.load)` → `ItemTooltips.build` (Questie names as fallback)
  → `IconLoader.load` → `ZoneMapLoader.load` → bundle with the lock.
  Skip: counts `clientdata.skipped = 1`, returns `ClientDataBundle.EMPTY`.
  Adds `clientdata.items.named` (items whose `name` is a string after corrections), `clientdata.coverage.pct`
  (`matched * 100 / named`, floored; 100 when nothing is named) and an informational `clientdata.items.named.matched`.
  `coverageTooLow(report)`: false when skipped or no named items, else `matched * 100 < 85 * named` (exact integer math).
- **`ImportPipeline`**: the stage runs after the zone stage, before the writer; the writer gets `write(out, model, report, bundle)`.
  `countRows` already counts every table, so all twelve new tables get `table.<name>` (0 after a skip). Javadoc updated.
- **`ImportOptions`**: `clientData` is now a non-optional `ClientDataOptions`; `parse` requires exactly one flag (neither → message naming both
  + usage; both → "mutually exclusive" + usage). The two client-data-less convenience constructors are gone; the remaining convenience
  constructor takes a `ClientDataOptions`, so every caller states its choice. Usage text updated.
- **`Main`**: the gate adds to the exit-2 conditions (existing ones unchanged); stderr names matched/named/pct and points to the
  `clientdata.unmatched.ranges` note. The skips message is printed only when skips/unsupported counters are non-zero.
- **`ClientDataSource.lock()`**: stale-Era guard. With `csv/ItemSparse.era.csv` present, the lock must carry `fallback_product`,
  `fallback_build` and `tables["ItemSparse.era"]`; otherwise `IllegalStateException`:
  "ItemSparse.era.csv present but build.lock has no fallback entry (…); re-run fetch.py --fallback-product wow_classic_era and convert.py, or delete the stale <path>".
- **`importer/pom.xml`**: module properties `clientdata.dir` = `${user.home}/.cache/wowforever-clientdata/out/current` and
  `clientdata.args` = `--client-data-dir "${clientdata.dir}"`; the `import-data` execution now uses `commandlineArgs`
  (`--out "…/wowforever.sqlite" --fail-on-unsupported ${clientdata.args}`); a profile `import-data-skip-client-data`
  activated by `-Dclientdata.skip=true` sets `clientdata.args` to `--skip-client-data`. Verified with `help:effective-pom`
  for default / `-Dclientdata.skip=true` / `-Dclientdata.dir=D:/x/y`, and `exec:java` with an equivalent quoted Windows-path
  argument string against the fixtures (exit 0, scratch output only). The shipped DB was not regenerated.
- **README** "Rebuild the data": jar command with `--client-data-dir`, profile properties, a "Client data" bullet
  (required flags, skip behaviour, stale Era guard) and the coverage gate in the `--fail-on-unsupported` bullet.
- **app `FixtureDb`**: passes `ClientDataOptions.skipped()` (Task 10 switches it to the client fixture).

## Fixture changes

- `fixtures/clientdata/build.lock`: `tables["ItemSparse.era"]` (the real sha of the fixture CSV), `fallback_product: wow_classic_era`,
  `fallback_build: 1.15.9.70003`. CRLF kept as committed.
- `fixtures/questiedb/data/Forever/foreverItemDB.lua`: added `[17204] Eye of Sulfuras` and `[19682] Bloodvine Vest`
  (levels/classes from the client rows, no drops). Without them no Questie fixture item is in a client set (`item_set` stayed 0:
  16800 is in Era set 201, which the ItemSet fixture lacks) and 17204 is not an item row, so the brief's "17204 has a use line" was
  unreachable. Only `RawEntityLoaderTest`'s exact id list needed updating; all app tests stay green.

## Tests (TDD evidence)

New/changed tests were written before the implementation. First run after implementing: 29/30 targeted tests green, one RED
`endToEndWithClientDataFixture` — `[item_set] Expecting actual: 0L to be greater than: 0L` (the fixture gap above), fixed by the
Questie fixture items. Then `RawEntityLoaderTest.loadsEveryFixtureRowWithoutProblems` RED on the id list, updated.
Mutation check: with `coverageTooLow` returning false and `checkFallback` removed, `MainTest.coverageGateExits2`,
`MainTest.coverageGateComparesMatchedWithNamedItems` and `ClientDataSourceTest.staleEraFileWithoutLockEntryFails` failed (3 failures);
code restored afterwards.

- `ImportPipelineTest.endToEndWithClientDataFixture`: all 12 new tables > 0 and equal to their `table.<name>` counter; 17204 has a `use`
  line; `zone.ui_map_id` 40 = 1436; icon 134124 decodes 56×56; `client_build` 1.60.1.70425; `client_fallback_build` 1.15.9.70003;
  `client_item_count` = `has_client_data` rows = matched + iconOnly; `clientdata.coverage.pct` present and = matched*100/named; ranges note present.
- `ImportPipelineTest.skipClientDataStillProducesOpenableDb`: client tables (all but `drop_rate_source`, which is Questie/AtlasLoot
  provenance) exist and are empty with `table.<name> = 0`; `has_client_data` 0 everywhere; no `client%` meta; no `ui_map_id`; `clientdata.skipped = 1`.
- `MainTest.coverageGateExits2`: real path — a temp copy of the client fixture with header-only `ItemSparse.csv` and `ItemSparse.era.csv`;
  `--fail-on-unsupported` → exit 2, stderr "named items (0 %), below 85 %", report `clientdata.coverage.pct = 0`; without the flag → exit 0.
  Plus `neitherClientDataFlagReturnsOneWithUsage`, `skippedClientDataNeverFailsTheCoverageGate`, `coverageGateComparesMatchedWithNamedItems`
  (85/100 passes, 85/101 fails, skipped never fails). `successfulImportReturnsZeroAndPrintsReport` now runs with the client fixture.
- `ImportOptionsTest`: every flag incl. `--client-data-dir`; defaults with `--skip-client-data`; neither → error naming both; both → error.
- `ClientDataSourceTest.staleEraFileWithoutLockEntryFails`: no fallback keys / keys without table sha / table sha without keys all fail; full entry passes.
  Lock test updated for 43 tables and the fallback fields.

Results: `./mvnw -B -q -pl importer verify` exit 0 (jar rebuilt); root `./mvnw -B -q verify` exit 0 —
importer 317, app 146, lua-table 61 tests, 0 failures/errors/skips.

## Jar runs on the fixture

`java -jar importer/target/importer.jar --out <tmp>/f.sqlite --questiedb-dir importer/src/test/resources/fixtures/questiedb --atlasloot-dir importer/src/test/resources/fixtures/atlasloot --client-data-dir importer/src/test/resources/fixtures/clientdata --fail-on-unsupported` → **exit 0**

```
clientdata.alt.subclass_id = 1
clientdata.armor.items = 4
clientdata.coverage.pct = 100
clientdata.damage.items = 4
clientdata.damage.secondaryDropped = 0
clientdata.effects = 5
clientdata.effects.otherTrigger = 0
clientdata.icons = 15
clientdata.icons.missing = 1
clientdata.items.iconOnly = 0
clientdata.items.matched = 15
clientdata.items.matched.era = 5
clientdata.items.matched.forever = 10
clientdata.items.named = 15
clientdata.items.named.matched = 12
clientdata.items.unmatched = 5
clientdata.maps = 2
clientdata.maps.missingTiles = 0
clientdata.sets = 1
clientdata.stats.items = 5
clientdata.stats.unknownId = 0
clientdata.tooltip.items = 15
clientdata.tooltip.lines = 129
clientdata.tooltip.missingKind.durability = 1
clientdata.unformatted = 0
clientdata.unverified.armor = 0
clientdata.unverified.block = 0
clientdata.unverified.damage = 0
clientdata.unverified.stats = 0
clientdata.zones.explicitNoMap = 1
clientdata.zones.mapped.assignment = 1
clientdata.zones.mapped.override = 1
clientdata.zones.mapped.parent = 2
clientdata.zones.missingArt = 1
clientdata.zones.unmapped = 583
table.drop_rate_source = 11   table.icon = 15            table.item = 20
table.item_alt_value = 1      table.item_armor = 4       table.item_damage = 4
table.item_effect = 5         table.item_set = 1         table.item_set_bonus = 1
table.item_set_item = 3       table.item_stat = 15       table.item_tooltip_line = 129
table.zone_map = 2            table.meta = 13            table.search_index = 639
notes: clientdata.unmatched.ranges: 0-9999 (4), 200000-209999 (1)
       clientdata.icons.missing: 134939
meta: client_product=wow_classic_beta, client_build=1.60.1.70425, client_fetched_at=2026-10-16T20:34:38Z,
      client_fallback_product=wow_classic_era, client_fallback_build=1.15.9.70003, client_item_count=15,
      icon_count=15, zone_map_count=2
```

Same with `--skip-client-data` → **exit 0**

```
clientdata.skipped = 1
table.drop_rate_source = 11   table.icon = 0             table.item = 20
table.item_alt_value = 0      table.item_armor = 0       table.item_damage = 0
table.item_effect = 0         table.item_set = 0         table.item_set_bonus = 0
table.item_set_item = 0       table.item_stat = 0        table.item_tooltip_line = 0
table.zone_map = 0            table.meta = 5             table.search_index = 636
table.item_class = 16         table.item_subclass = 71
meta: no client_* / *_count keys; has_client_data = 0 for all 20 items
```

Neither flag → exit 1 with "either --client-data-dir <dir> or --skip-client-data is required" and the usage.

## Files changed

- `importer/src/main/java/dev/wowforever/importer/clientdata/ClientDataStage.java` (new)
- `importer/src/main/java/dev/wowforever/importer/{ImportPipeline,ImportOptions,Main}.java`
- `importer/src/main/java/dev/wowforever/importer/clientdata/ClientDataSource.java`
- `importer/pom.xml`, `README.md`
- `importer/src/test/java/dev/wowforever/importer/{ImportPipelineTest,MainTest,ImportOptionsTest}.java`
- `importer/src/test/java/dev/wowforever/importer/clientdata/ClientDataSourceTest.java`
- `importer/src/test/java/dev/wowforever/importer/entity/RawEntityLoaderTest.java`
- `importer/src/test/resources/fixtures/clientdata/build.lock`
- `importer/src/test/resources/fixtures/questiedb/data/Forever/foreverItemDB.lua`
- `app/src/test/java/dev/wowforever/app/db/FixtureDb.java`

## Concerns

1. **Gate ratio mixes populations (as ruled).** `clientdata.items.matched` counts every Questie item id, nameless correction stubs included;
   the denominator counts only named items. On the fixture three nameless stubs match the client, so the ruled ratio is 15/15 = 100 % while
   only 12 of 15 named items match (80 %, which would fail a strict gate). On real data (249 nameless items of 21,543) the inflation is at most
   about 1.2 points, so the gate still works there. I implemented the ruling as written and added the `clientdata.items.named.matched`
   counter for visibility. If the lead prefers named∩matched as the numerator, it is a one-line change in `ClientDataStage`, but the fixture would
   then fail the gate (80 %) and need more client rows or fewer unmatched named items.
2. **Questie fixture grew by two items** (17204, 19682). Needed for the brief's 17204/sets assertions; all importer and app tests pass, but
   Task 10 authors should know the fixture now has 20 item rows.
3. `import-data` now uses `commandlineArgs` instead of the `<arguments>` list, so `-Dexec.args=…` on the command line would replace
   the whole argument string. A profile that adds a single optional argument needs a string property; the list form cannot drop an argument.

---

# Fix round 1

**Commit:** `dd93343 fix(importer): strict coverage ratio over named items; --coverage-min option`

## Finding 1: strict coverage ratio

- `clientdata/ClientDataStage.java:64-68`: `clientdata.coverage.pct` = `named.matched * 100 / named` (floored; 100 without named items).
- `ClientDataStage.java:85-90`: `coverageTooLow(report, minPct)` compares `NAMED_MATCHED * 100 < minPct * named` (exact). `MIN_COVERAGE_PCT` became `DEFAULT_MIN_COVERAGE_PCT = 85` (`:38`). Class Javadoc updated.
- `Main.java:50,58-61`: stderr now reads "client data covers <named.matched> of <named> named items (<pct> %), below --coverage-min <min> %; see the clientdata.unmatched.ranges note; …".

## Finding 2: `--coverage-min <pct>`

- `ImportOptions.java:31`: new record component `int coverageMinPct`, range-checked 0–100 in the compact constructor (`:54-56`). The 7-argument constructor (`:60-64`) and the 6-argument one default it to 85, so `FixtureDb` and the other callers compile unchanged.
- `ImportOptions.java:105,122-133`: `--coverage-min` parses an integer. A non-integer or out-of-range value raises "--coverage-min must be an integer from 0 to 100, not <v>" plus the usage. Usage text at `:44-45`.
- `importer/pom.xml:18`: property `clientdata.coverageMin` = 85, and `:128` passes `--coverage-min ${clientdata.coverageMin}`. The profile comment (`:108-111`) notes `-Dexec.args` replaces everything.
- `README.md:51`: `-Dclientdata.coverageMin` and the reviewer's minor, that `-Dexec.args` replaces the whole argument list including `--out`. `README.md:55`: the gate counts only named items, and `--coverage-min` sets the threshold.
- `FixtureDb` needed no change. It runs `ImportPipeline` directly with `failOnUnsupported=false` and `--skip-client-data`, so it never reaches the gate.

## Covering tests

- `MainTest.coverageGateComparesNamedMatchedWithNamedItems` (`:122`): matched-only counts fail, 85/100 passes, 85/101 fails at 85 and passes at 84, 0 never fails, 100 fails, and skipped never fails.
- `MainTest.fixtureFailsTheDefaultCoverageGateOnNamedItems` (`:96`, new, real path): the fixture with the default threshold exits 2. Stderr shows "client data covers 12 of 15 named items (80 %), below --coverage-min 85 %", and the report shows matched 15, named.matched 12, pct 80.
- `MainTest.successfulImportReturnsZeroAndPrintsReport` (`:30`): adds `--coverage-min 80` and asserts `clientdata.coverage.pct = 80`. In `coverageGateExits2` the message now reads "below --coverage-min 85 %".
- `ImportPipelineTest.endToEndWithClientDataFixture` (`:118`, `:139-147`): options with `coverageMinPct` 80. It asserts pct = named.matched*100/named = 80, matched > named.matched, the gate passes at 80 and fails at 85.
- `ImportOptionsTest.parsesEveryFlag` (with `--coverage-min 80`) and `appliesDefaults` (85). The new `coverageMinAcceptsZeroToHundred` checks that 0 and 100 are accepted. It rejects 101, -1, "eighty" and "80.5" with usage, a missing value, and 101 passed to the constructor.
- Mutation check: putting the old numerator back failed `endToEndWithClientDataFixture`, `coverageGateComparesNamedMatchedWithNamedItems` and `fixtureFailsTheDefaultCoverageGateOnNamedItems`. Ignoring `minPct` failed `endToEndWithClientDataFixture`, `coverageGateComparesNamedMatchedWithNamedItems` and `successfulImportReturnsZeroAndPrintsReport`. The code was restored after both.

## Commands and output

```
./mvnw -B -q -pl importer test -Dtest=ImportOptionsTest,ClientDataSourceTest,ImportPipelineTest,MainTest
  MainTest 10, ImportPipelineTest 6, ImportOptionsTest 9, ClientDataSourceTest 7 — 0 failures
./mvnw -B -q -pl importer verify   -> exit 0 (jar rebuilt)
./mvnw -B -q verify                -> exit 0; importer 319, app 146, lua-table 61 tests, 0 failures/errors/skips
help:effective-pom -Pimport-data: "... --fail-on-unsupported --coverage-min 85 --client-data-dir "C:\Users\aircan80/.cache/wowforever-clientdata/out/current""
  with -Dclientdata.coverageMin=80 -Dclientdata.skip=true: "... --fail-on-unsupported --coverage-min 80 --skip-client-data"
```

Fixture jar runs (`--questiedb-dir/--atlasloot-dir` fixtures, `--fail-on-unsupported`):

```
--client-data-dir fixtures/clientdata --coverage-min 80  -> exit 0
--client-data-dir fixtures/clientdata (default 85)       -> exit 2
   client data covers 12 of 15 named items (80 %), below --coverage-min 85 %; see the clientdata.unmatched.ranges note; failing because of --fail-on-unsupported
--skip-client-data                                       -> exit 0
--skip-client-data --coverage-min 120                    -> exit 1  "--coverage-min must be an integer from 0 to 100, not 120"

client (80) counters: clientdata.coverage.pct = 80, items.matched = 15 (forever 10, era 5), items.named = 15,
  items.named.matched = 12, items.unmatched = 5, iconOnly = 0; table.item_set = 1, item_tooltip_line = 129,
  icon = 15, zone_map = 2, drop_rate_source = 11 (other counters unchanged from the first report)
skip counters: clientdata.skipped = 1; table.item_set/item_tooltip_line/icon/zone_map = 0, drop_rate_source = 11
```

## Note for Task 11

With the default threshold of 85, the shipped-data run must reach 85 % of named items, now counted strictly. Pass `-Dclientdata.coverageMin` only as a deliberate, documented decision.
