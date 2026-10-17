# Phase 3A final fix wave: report

Status: DONE_WITH_CONCERNS (all items done; the concerns are notes, not defects)

Branch feature/phase3a-quest-data, from 7cb870c to 78389ca. Six commits, each with a full `./mvnw -B -q verify`
green before it. Every commit ends with the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

| Commit | Item |
|---|---|
| 118a777 | 1. I1, world-drop threshold |
| e9be4a2 | 2. M2, Vanilla rate label |
| 47fabc0 | 3. M4, LootMgr validity rules |
| 567ed87 | 4. M5, shipped-test gaps |
| 4679e94 | 5. M6, route column enum and negative scalar relation ids |
| 78389ca | 6. M3, rebuild reproducibility |

Item 7 (M1) was left alone as the brief says. It is carried to 3B Task 7.

## Verify on the final commit (78389ca, `./mvnw -B -q clean verify`, exit 0)

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 420 | 0 | 0 | 0 |
| app | 798 | 0 | 0 | 0 |

The importer gained 5 test methods: one in ServerDbSourceTest, two in ServerDropRatesTest, one in
QuestRelationWriterTest, and the new ServerDbPinTest. The app count is unchanged. Its new checks are assertions
added to existing test methods.

## 1. I1: world-drop threshold (118a777)

- `app/src/main/java/dev/wowforever/app/ui/pages/ItemCategories.java` adds `WORLD_DROP_MAX_PCT = 10.0`.
  `ofDrop` is now `dropperCount >= WORLD_DROP_NPCS && pool class && (dropPct == null || dropPct <
  WORLD_DROP_MAX_PCT)`. The class, constant and `ofDrop` javadoc were updated.
- `NpcPage.java` has an updated class javadoc.
- `README.md:55` now says pool items fold "when their rate is unknown or below 10 %". It also says that a shared
  drop at 10 % or more, such as Murloc Eye, stays in its own group. The next sentence now names "the World drops
  heading", because "its heading" had become ambiguous.
- Tests:
  - `NpcPageTest.aRatedPoolDropManyNpcsShareIsStillAWorldDrop` keeps its old rows. It adds a 40 % "Murloc Eye" and
    a "Chunk of Boar Meat" at exactly `WORLD_DROP_MAX_PCT`, both with many droppers. They read in Trade goods,
    rate-ordered. The boundary row pins that 10 % itself is not a world drop. The test failed before the rule
    change.
  - `ShippedDataTest.theShippedPagesReadAsReviewed`: Hogger (448) reads exactly 4 rows, "Quest items (2)" first
    and "World drops (23) ▸" last. Murloc Tidehunter (NPC 127) shows "Trade goods (3)" directly followed by
    "Murloc Eye", and its last row is a World drops heading. The measured rate is 50.0035 % from questie-wowhead,
    with 36 droppers. The comment in the test names the NPC and the rate.

## 2. M2: Vanilla rate label (e9be4a2)

- `app/src/main/java/dev/wowforever/app/model/RateSource.java`: `LABELS` gains `"vmangos" -> "Vanilla rate
  (VMaNGOS)"`, and the javadoc names the source.
- Test: `RateSourceTest.labelsNameTheAddon` has one new assertion. It failed before the change.
- `KNOWN`, the tooltip ordering, is unchanged, so `vmangos` still sorts after the known sources, by name. 3B Task 7
  can decide its place.

## 3. M4: LootMgr validity rules (47fabc0)

- `importer/src/main/java/dev/wowforever/importer/serverdb/ServerDbSource.java`: `rows()` mirrors
  `LootStoreItem::IsValid`. The forbidden-items check runs first, as before. Each skipped row then counts under its
  first broken rule:
  - `serverdb.invalid.mincountZero`: `mincountOrRef = 0`, on items and references alike.
  - `serverdb.invalid.unknownItem`: an item row whose item has no `item_template` row at patch 10 or earlier.
  - `serverdb.invalid.lowChance`: an item row with `|chance|` above 0 and below `MIN_CHANCE = 0.000001`.
    References are not checked.
  - `serverdb.invalid.maxcountBelowMin`: an item row with `maxcount < mincountOrRef`.
  - `serverdb.references.maxcountZero`: a reference with `maxcount = 0`. It is kept, rolls once as before, and is
    counted.
- `validityCounts()` returns the five counters in rule order, starting at 0.
- `item_template` joins the required tables. Every real VMaNGOS world database has it.
- `ServerDropRates.load` reports the five counters next to `serverdb.loot.forbidden`. The class javadoc says that a
  reference with `maxcount = 0` rolls once.
- `tools/serverdb/README.md` lists the five counters.
- Test fixture: `ServerDbFixture` creates `item_template` with a patch-0 row for every item a loot row names.
  `unknownItem(id)` leaves an item out. Every existing test still passes unchanged.
- Tests:
  - `ServerDbSourceTest.rowsTheServersLootValidityCheckRejectsAreSkippedAndCountedOncePerRule`. Each rule has a
    fixture row in both tables. The valid edge cases are kept: a grouped zero chance, maxcount equal to mincount, a
    reference with a tiny chance, and a reference with maxcount 0.
  - `ServerDropRatesTest.rowsTheServersValidityCheckRejectsNeverDropAndAreReportedOneCounterPerRule`. It includes
    the group-share case the review named. A `mincountOrRef = 0` row no longer joins a group, so the group's only
    valid zero-chance row takes 100 %.
  - `ServerDropRatesTest.theValidityCountersAreReportedAtZeroWhenNoRowBreaksARule`.
- Measured read-only against the pinned `mangos.sqlite` at patch 10: 0 rows for every rule in both tables
  (227,235 creature rows and 9,976 reference rows), so the shipped database is unchanged and was not regenerated.

## 4. M5: shipped-test gaps (567ed87)

- `ShippedQuestDataTest.railIconsAreEmbedded`: `assertThat(rs.next()).as(name).isTrue()`.
- `ShippedProvenanceDataTest.vmangosHeadlinesMostDrops`: the quest-only VMaNGOS headline count is
  `isBetween(100L, 400L)`. It is 208 in the shipped database, measured read-only.
- These are guard assertions on data that is already correct, so there was no failing state to show first.

## 5. M6: latent SQL and relation data (4679e94)

- `NpcRepository.routes(Connection, RouteKey, long)` takes a package-private enum `RouteKey { NPC("npc_id"),
  ZONE("zone_id") }` in place of the column string. Its callers are `NpcRepository.routes(long)` and
  `ZoneRepository.routesInZone`. The existing tests cover both paths: `NpcRepositoryTest` route tests and
  `ZoneRepositoryTest.routesInZoneHoldTheDefiasMessengersPatrol`.
- `QuestRelationWriter`: a negative scalar id in nextQuestInChain, parentQuest or breadcrumbForQuestId is skipped
  and counted as `badValue.quest.<field>`. The javadoc says so.
- Test: `QuestRelationWriterTest.aNegativeScalarRelationIdIsCountedNotWritten` covers a negative nextQuestInChain
  and a negative parentQuest. A positive breadcrumbForQuestId is still written, and no `related_id <= 0` row
  exists. The test failed before the change.

## 6. M3: rebuild reproducibility (78389ca)

- `tools/serverdb/README.md` has a new "Rebuilding this exact database" section. It covers three points:
  - `db_latest` replaces its assets, so the pinned URL may 404 and `fetch.py` then fails cleanly.
  - The cached `~/.cache/wowforever-serverdb/4641790/db-sqlite-4641790.zip` should be kept or mirrored. Placed back
    in that directory, it passes the SHA-256 check and is extracted with no download. The licence comes from the
    pinned commit's raw URL, which does not move.
  - `serverdb.lock` and the pom's `serverdb.dir` default must change together.
- `importer/pom.xml` has a comment on `serverdb.dir` that names the coupling. The value is unchanged.
- I did not derive the default inside Maven. It would need an antrun filter chain that exports a property, then a
  nested property evaluation in the exec arguments. I could not check that without a full `import-data` run, so it
  did not meet the brief's "small, safe" bar.
- In its place I added `ServerDbPinTest.thePomDefaultDirectoryIsTheLockedAssetsCacheDirectory`. It reads the lock
  and `pom.xml`, then checks two things: the lock's commit starts with the asset's short commit, and the pom default
  directory equals that short commit. With the pom temporarily set to `1234567`, it failed with
  `expected "4641790" but was "1234567"`. The drift is now caught at unit-test time, not by the shipped tests after
  a regeneration.

## Choices that differ from the brief, and notes

- **The pin test is an addition.** The brief asked for documentation when a Maven derivation is not small. I added
  a one-method unit test as well, because it is cheap and turns the documented rule into a check.
- **Counter names are mine.** The brief did not give them for M4: `serverdb.invalid.mincountZero`,
  `serverdb.invalid.unknownItem`, `serverdb.invalid.lowChance`, `serverdb.invalid.maxcountBelowMin` and
  `serverdb.references.maxcountZero`.
- **The validity counters count rows at read time.** The rule counters in `ServerDropRates` count per computed
  template pass. The new ones count rows like `serverdb.loot.forbidden`, because the rows are dropped before any
  template is built.
- **The shipped import report lacks the new counters.** `app/src/main/resources/data/import-report.txt` was not
  regenerated, as the brief says, so it does not list the five counters yet. They appear, at 0, on the next
  `import-data` run. No test reads them from the committed report.
- **Spawn queries are out of scope.** `NpcRepository.spawns(Connection, String table, String idColumn, long)` also
  concatenates a table and column name into its SQL. Its callers pass literals: `npc_spawn`/`npc_id` from
  NpcRepository and `object_spawn`/`object_id` from ObjectRepository. The review and the brief named only the
  routes method, so I left this one as it is. The same enum treatment would close it.
- **The Murloc Eye check follows a Questie rate.** Murloc Tidehunter's 50 % comes from questie-wowhead, not
  VMaNGOS. The threshold applies to any rate source, as the brief specifies.
