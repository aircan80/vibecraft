# Phase 2A fix wave: report

Branch `feature/phase2a-client-data`, main checkout `H:\claude_java`, commits `e892b26..06b1d90` on top of `dc760dc`.
Status: DONE_WITH_CONCERNS. The F1 table list was amended by the lead (ItemSparse + ItemEffect via ParentItemID); see "Concerns".

## Commits

| SHA | Subject |
|---|---|
| e892b26 | fix(app): icon-only items show their own icon, not the fallback |
| 9ee4a47 | test(app): shipped import report lists the unverified counters and the conflicts |
| 3cc8a1a | fix(importer): name the Forever stat ids the Classic Era Equip spells name |
| a8cda87 | fix(importer): the item join builds each id index once |
| 831713b | fix(tools): Era ItemEffect fallback, pipeline User-Agent, localhost:5000 default, build string check |
| d3cd69f | fix(importer): Era-sourced items take their Use, Equip and proc effects from Era |
| fdc14bf | data: regenerate the shipped database with Era item effects and named stat ids |
| a6d5635 | docs: Era ItemEffect fallback, fetch User-Agent and base URL, refreshed numbers |
| 06b1d90 | fix(importer): an Era ItemEffect without ParentItemID is reported, not fatal |

Trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (my session's required attribution line, as the brief allows).
The docs commit was amended once, before anything else built on it, to include the bundled `app/src/main/resources/NOTICE.md`
copy (a test keeps it equal to the root NOTICE); every commit is green.

## Per finding

### F1. Era-sourced items lose their Use/Equip/Chance-on-hit lines

**Ruling amended by the lead after my NEEDS_CONTEXT: fallback tables ("ItemSparse", "ItemEffect"), joined through ItemEffect.era.ParentItemID.**
Era build 1.15.9.70003 has no `ItemXItemEffect` table: wago.tools answers HTTP 400 for
`/db2/ItemXItemEffect/csv?build=1.15.9.70003`. Its `ItemEffect` (200, 16,965 rows) carries the item itself in a
`ParentItemID` column (header `ID,LegacySlotIndex,TriggerType,Charges,CoolDownMSec,CategoryCoolDownMSec,SpellCategoryID,SpellID,ChrSpecializationID,PlayerConditionID,ParentItemID`).
So the fallback list is `("ItemSparse", "ItemEffect")`, not the three tables named in the brief; no
`ItemXItemEffect.era.csv` exists anywhere. Everything else is as ruled.

- `tools/clientdata/clientdata/tables.py`: `FALLBACK_TABLES`, `fallback_name()`, `FALLBACK_NAMES`; `FALLBACK_NAME`
  kept for ItemSparse.
- `fetch.py`: `fetch_fallback_sparse` became `fetch_fallback`, which fetches every fallback table into
  `csv/{Table}.era.csv`, locked as `tables["{Table}.era"]`; same stale-build rule (all refetched when the lock's
  `fallback_build` differs); a table missing from the cache is fetched alone (the upgrade path used for the real cache).
  `--fallback-product/--fallback-build` unchanged.
- `convert.py`: unchanged; it already copies every `*.csv`. A test now pins that both era tables are copied.
- `fixture.py` (make_fixture): keeps `ItemEffect.era` rows by `ParentItemID` for the Era-sourced subset items (no
  Forever ItemSparse row) and adds their spells to the spell closure (Forever's spell tables).
- `ClientDataSource.checkFallback`: every `csv/*.era.csv` present needs `fallback_product`, `fallback_build` and its
  own `tables["{Table}.era"]`; new `ERA_SUFFIX`.
- `SpellTables.contains(spellId)`: a Forever `Spell` or `SpellName` row exists.
- `ItemEffectLoader`: for an item whose `clientSource` is `era` and that has no Forever effect link, effects come
  from `ItemEffect.era` by `ParentItemID`; Forever rows win whenever the item has any. Spell text comes from
  Forever's tables. An Era effect whose spell has neither a Forever Spell nor SpellName row is dropped and counted as
  `clientdata.era.effectUnresolved` (always emitted). The muted `source_note` is untouched (TooltipBuilder).
- Fixture: `ItemEffect.era.csv` with 18 rows for 13 Era-sourced fixture items (5191, 14145, 16800, 16803 to 16810,
  16865, 19137), the 12 Forever spells they reach (Spell, SpellName, SpellMisc, SpellEffect, SpellAuraOptions rows,
  all additive), and `tables["ItemEffect.era"]` in the fixture `build.lock` (full-table sha, like the others; CRLF
  kept, one-line diff).

Tests:
- `ItemEffectLoaderTest.anEraSourcedItemTakesItsEffectsFromTheEraTableWithForeverSpellText`: Onslaught Girdle gets
  two Equip effects (7597 crit, 15464 hit) with Forever text; Arcanist Boots 18384 and 9416; Thunderfury (Forever)
  unchanged; unresolved 0.
- `...withoutAnEraEffectTableEraSourcedItemsHaveNoEffects` (temp copy without the file).
- `...foreverRowsWinAndEraSpellsMissingFromForeverAreCounted` (temp copy plus three Era rows: one for a Forever item,
  one for an Era-sourced item that has a Forever link, one with an unknown spell): first two ignored, third counted as 1.
- `TooltipBuilderTest.anEraSourcedItemShowsItsEraEquipLinesAndTheSourceNote` (19137 end to end).
- `ClientDataSourceTest.everyEraFileNeedsItsOwnLockEntry`; fixture lock size 43 to 44.
- Python: `test_fallback_tables_are_item_sparse_and_the_item_effect_tables`,
  `test_fallback_product_fetches_the_era_tables_beside_the_forever_tables` (also asserts no ItemXItemEffect request),
  `test_fallback_is_cached_until_the_fallback_build_changes`,
  `test_only_the_missing_era_tables_download_beside_a_cached_era_item_sparse`, `test_fallback_build_pins_the_fallback`,
  `test_convert_copies_every_era_table`,
  `test_era_effects_are_kept_for_era_sourced_items_and_feed_the_spell_closure`,
  `test_no_era_effect_table_in_the_cache_writes_none`.
- `ItemEffectLoaderTest.anEraEffectTableWithoutParentItemIdIsReportedNotFatal` (added on the amended ruling: an ItemEffect.era without ParentItemID is counted as `clientdata.era.effectTableUnusable` = 1 with a note naming the missing column, Era effects are skipped, Forever effects intact, no exception) and `aUsableEraEffectTableReportsNoProblem` (the counter is emitted only on the problem, so the shipped report needed no regeneration).
- `ShippedDataTest.eraSourcedItemsShowTheirEraEffects` (19137 exact lines, 16914 two Equip lines, at least 85 % of
  Era epic weapons and armour have effect lines) and the report test asserts the `era.effectUnresolved` counter.

### F2. Icon-only items show "?" although their icon is shipped

- `DetailPanels.load` takes the summary's icon id, the card's only when the summary has none.
- No fixture item was icon-only (all three named unmatched items, 750, 1176 and 1262, have ItemSparse rows in
  reality, and 1176 is pinned as "no client data" by an existing test). I added the real icon-only item Illari's Key
  (212347): its Questie entry (name, item level 1, class 12, subclass 0; its object drop and quest left out, they are
  not in the fixture) in the fixture `foreverBaseItem.lua`, its Forever Item row and its Use effect "Opening" (3366)
  by an additive make_fixture cut, and icon `134236.png` converted from the cached BLP (byte-identical to
  `out/current`).
- Consequence: the fixture covers 12 of 16 named items (75 %). Updated: `ImportPipelineTest` (75, options 75),
  `MainTest` (four strings, `--coverage-min 75`), `FixtureDb.FIXTURE_COVERAGE_MIN_PCT = 75`.
- `ImportPipelineTest` asserted `has_client_data` rows = matched + iconOnly. The writer sets `has_client_data` only
  for an ItemSparse row (ClientItemWriter javadoc), so that only held while iconOnly was 0. Corrected to matched, plus
  explicit checks for 212347 (has_client_data 0, icon_id 134236, icon row present).

Tests: `DetailPanelsTest.anIconOnlyItemLoadsItsOwnIconNotTheFallback` (failed before the fix with the fallback
pixels; Smelling Salts still gets the fallback), `ItemRepositoryTest.iconOnlyItemCarriesItsIconOnTheSummaryButHasNoCard`,
`ShippedDataTest.iconOnlyItemsCarryTheirEmbeddedIcon` (212347; every icon-only item's icon is in `icon`: 488 of 488).

### M3. User-Agent

`fetch.user_agent(contact)`: `wow-forever-browser-pipeline/0.2 (+<contact>)`; contact from `--user-agent CONTACT`,
else `WOWFOREVER_CONTACT`, else the placeholder `contact: set WOWFOREVER_CONTACT`; blank values fall through. No
personal data by default. Documented in the tools README ("Politeness") and the root README.
Tests: `test_user_agent_defaults_to_the_pipeline_prefix_and_a_placeholder_contact`,
`test_user_agent_contact_from_the_environment_or_the_flag`, `test_cli_sends_the_pipeline_user_agent`.
`known_items.py` keeps its own UA string (not in scope).

### M4. Default base URL

`DEFAULT_BASE_URL = "http://localhost:5000"`; `--base-url` help names both options. Tools README flags line and root
README prerequisites updated. Test: `test_default_base_url_is_wow_tools_local`.

### M5. Build string validation

`check_build` (`^\d+(\.\d+){3}$`, fullmatch) on the resolved newest build and on a pinned `--build`/`--fallback-build`;
`BadBuildError` makes the CLI exit 2 with a message before any table request or cache write (the main product's
`LookupError` is now also caught instead of a traceback).
Tests: `test_resolve_build_rejects_a_listed_build_that_is_not_four_numbers` (`../../evil` and six other bad shapes),
`test_cli_refuses_a_hostile_build_before_writing_anything` (no cache dir, no `escape` dir, no /db2 request),
`test_cli_refuses_a_hostile_fallback_or_pinned_build`.

### M7. ShippedDataTest report checks

`committedImportReportListsTheUnverifiedCountersAndTheConflicts`: the four `clientdata.unverified.*` lines, the
`== Conflicts ==` heading, and (with the data commit) `clientdata.era.effectUnresolved`.

### M8. Stat ids

- 84 to 89 were rendered but counted: `StatNames` now names them, so `ItemValues` (which counts `!isKnown`) no longer
  counts them.
- Remaining ids named from evidence: the Era ItemEffect Equip spells of the same items (the 2A cache, Forever spell
  names/descriptions) and the computed values matching the Era spell amounts (fishing poles +5/+20/+25/+35, Herbalist's
  Gloves +5, Goblin Mining Helmet +5):
  90 Two-handed Axes, 91 Two-handed Maces, 96 Daggers, 103 Swords, 112 Herbalism, 113 Mining, 117 Fishing,
  119/121 fire/frost spell penetration, 124 All Resistances, 127/128/131 attack power against Demons/Undead/Beasts,
  135/136 spell damage against Demons/Undead (135 by analogy with 127/128 on the two Mark of the Champion items).
  `StatText` renders them as green Equip lines in the Era spell's wording ("Equip: Increased Fishing +5.").
- Left unknown and counted (no Era item has a matching spell): 83, 92, 98, 114, 115, 132. 114 is very likely Skinning
  (Keen Skinning Knife) and 132 possibly mechanical slaying, but nothing in the data confirms them.
Tests: `StatTextTest` (new: school line, the 15 Era-worded lines in id order, placeholders, and every id 0 to 140
renders a placeholder exactly when `isKnown` is false), `StatNamesTest.namesTheForeverIdsTheClassicEraNames`,
`ItemValuesTest.unknownStatIdsAreCountedAndNoted` (87 rendered, only 92 counted), `TooltipBuilderTest` synthetic
placeholder switched from 90 to 92, `ShippedDataTest.schoolSpellDamageIsNamed`.

### M10. README caster weapons

Real split of `clientdata.unverified.damage = 114` (computed from the regenerated DB and `out/current` ItemSparse,
following `ItemDamageCalculator`'s rule): 98 Forever caster weapons (Flags_4 0x100), 15 weapons without a speed,
1 Frying Pan (280317) with no damage table. The brief's 99 is 98 in this build. README line rewritten accordingly.

### M11. Double ItemSparseLoader.load

`ClientDataSource.indexById(name)` builds each table's id index once (unmodifiable, cached); `ItemSparseLoader` uses
it after its column check, so the second load for set pieces reuses the indexes. Test:
`ClientDataSourceTest.idIndexesAreBuiltOnceAndReadOnly`.

### Also fixed while there

- README: nameless items 249 to 242 (ledgered Task 11 minor), `item_tooltip_line` 161,815, sizes, the stale-era-file
  sentence covers both era tables, Era rows give "stats and effects".
- NOTICE (root and bundled copy) name both Era tables.
- ROADMAP A.6: the 50 most-changed names in the conflict report (deferred, as ruled).
- Tools README: the fixture section's stale "fixture build.lock has no fallback_* keys" sentence.

## Data refresh

Commands (main checkout, venv Python):

```
python tools/clientdata/fetch.py --product wow_classic_beta --build 1.60.1.70425 --base-url https://wago.tools \
    --cache C:\Users\aircan80\.cache\wowforever-clientdata --fallback-product wow_classic_era --fallback-build 1.15.9.70003
#   tables cached: 44; fallback ItemSparse, ItemEffect cached from wow_classic_era 1.15.9.70003
#   only /db2/ItemEffect/csv?build=1.15.9.70003 downloaded (645,103 bytes); cache lock gained exactly tables["ItemEffect.era"]
python tools/clientdata/convert.py --cache C:/Users/aircan80/.cache/wowforever-clientdata --build 1.60.1.70425 \
    --out C:/Users/aircan80/.cache/wowforever-clientdata/out/current
#   {"icons": 2924, "icons_failed": 1, "maps": 60, "missing_tiles": 0}   (4559256, BLP encoding 3, known)
./mvnw -B -q -pl importer -am package -DskipTests
java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite \
    --client-data-dir C:/Users/aircan80/.cache/wowforever-clientdata/out/current --fail-on-unsupported
#   exit 0 (coverage gate default 85 %); import-report.txt and build-id.txt written by the importer
cp C:/Users/aircan80/.cache/wowforever-clientdata/out/current/build.lock tools/clientdata/build.lock
```

An earlier fetch attempt with the three-table list failed on the ItemXItemEffect 400 before writing anything (lock
verified byte-identical afterwards).

Sanity numbers (read-only queries on the shipped DB, before = `dc760dc`, after = `fdc14bf`):

| Measure | Before | After |
|---|---|---|
| Era-sourced items with Use/Equip/Chance-on-hit lines | 397 | 1,396 |
| Era-sourced items with `item_effect` rows | 414 | 1,413 |
| Era epic (quality 4+) weapons and armour with effect lines | 75 of 547 | 490 of 547 |
| `clientdata.effects` / `table.item_effect` | 5,429 | 6,734 |
| `clientdata.tooltip.lines` | 160,510 | 161,815 |
| `clientdata.era.effectUnresolved` | (none) | 1 (spell 19200, Pattern: Warbear Woolies 15754, trigger 0) |
| `clientdata.stats.unknownId` | 224 | 8 (ids 83, 92, 98, 114, 115, 132) |
| tooltip lines with "Stat #" | 50 | 8 |
| `clientdata.coverage.pct` | 96 | 96 (20,485 of 21,294 named) |
| items with client data / era / iconOnly | 20,492 / 4,813 / 791 | unchanged |
| icons / zone maps | 2,925 / 57 | unchanged |
| DB size | 65,736,704 bytes | 65,966,080 bytes |
| build id | `c294e300...` | `a082da8f380edbef48ec1210cfd3545ba2e1afb680d98c6a6dabb9db9ce9eb48` |

Spot checks: 19137 Onslaught Girdle "Equip: Improves your chance to get a critical strike with melee attacks by 1.0%."
and "Equip: Improves your chance to hit by 1.0%.", then the source note; 16914 Netherwind Crown "Equip: Restores 4
mana per 5 sec." and "Equip: Increases damage and healing done by magical spells and effects by up to 32."; 6365
Strong Fishing Pole "Equip: Increased Fishing +5."; 212347 icon_id 134236 with its icon row.

## Test commands and output

```
./mvnw -B -q verify                      -> exit 0
  lua-table 61 tests, importer 336, app 204; 0 failures, 0 errors, 0 skipped (surefire XML totals, after 06b1d90)
tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q
  56 passed in 20.20s
```

Verify output is not silent: the existing `EraOverlapOracleTest` prints its oracle summary and mismatch list, and
`RealFilesTest` prints one line (both unchanged by this wave, numbers identical to Task 5's).
Each code fix was test-first: the new tests were run and seen failing (compile errors for new API, assertion failures
otherwise: 13 Python, F2 pixel comparison, 4 M8, F1 compile) before the implementation.

## Files changed (dc760dc..a6d5635, 46 files)

- Tools: `tools/clientdata/clientdata/{fetch,fixture,tables}.py`, `tools/clientdata/tests/test_{fetch,convert,fixture}.py`,
  `tools/clientdata/README.md`, `tools/clientdata/build.lock`.
- Importer main: `clientdata/{ClientDataSource,ItemEffectLoader,ItemSparseLoader,SpellTables,StatNames,StatText}.java`.
- Importer tests: `ImportPipelineTest`, `MainTest`, `clientdata/{ClientDataSourceTest,ItemEffectLoaderTest,ItemValuesTest,StatNamesTest,StatTextTest (new),TooltipBuilderTest}`.
- Fixtures: `questiedb/.../generated/foreverBaseItem.lua`; `clientdata/build.lock`, `clientdata/icons/134236.png`,
  `clientdata/csv/{Item,ItemEffect,ItemEffect.era (new),ItemXItemEffect,Spell,SpellAuraOptions,SpellEffect,SpellMisc,SpellName}.csv`.
- App: `ui/detail/DetailPanels.java`, `resources/NOTICE.md`, `resources/data/{wowforever.sqlite,import-report.txt,build-id.txt}`;
  tests `ShippedDataTest`, `db/{FixtureDb,ItemRepositoryTest}`, `ui/detail/DetailPanelsTest`.
- Docs: `README.md`, `NOTICE.md`, `docs/ROADMAP.md`.

## Self-review

- Completeness: F1, F2, M3, M4, M5, M7, M8, M10, M11 done; ROADMAP line added; the `\n` in Use/Equip lines left for
  2B Part 2 as ruled.
- Quality: fixes stay in the existing classes and patterns (counters always emitted, column checks up front, byte-
  for-byte fixture cuts, CRLF/LF of each file preserved). No restructuring; one new public method each on
  `ClientDataSource` (`indexById`, `ERA_SUFFIX`) and `SpellTables` (`contains`).
- Discipline: worked only in `H:\claude_java` on the feature branch (the harness twice reported a worktree as the
  primary directory; I kept to the brief's main checkout), no stash, app never launched, WoW install untouched,
  network only to wago.tools for the Era ItemEffect (one probe request plus the tool's fetch of the same file).
- Testing: real behaviour (fixture data and temp copies), no mocks of the code under test.
- Data: sanity numbers above; shipped-data tests pin the new behaviour.

## Concerns

1. **F1 table list differs from the original ruling.** Era 1.15.9.70003 has no ItemXItemEffect; the lead amended the
   ruling to ItemSparse plus ItemEffect (linked by ParentItemID), as implemented, and asked for the tolerance test
   added in 06b1d90.
2. **Fixture coverage is now 75 %** (12 of 16 named) because the real icon-only item 212347 joined the fixture; the
   fixture threshold and five assertions moved from 80 to 75.
3. **M8 names rest on Era evidence, one by analogy** (135 Demons spell damage, from Mark of the Champion's paired ids).
   The Equip wording is Classic's; Forever's own tooltip wording for these stats is unverified.
4. **M10 number:** the regenerated data gives 98 caster weapons, not the 99 in the brief and ledger.
5. The docs commit was amended once (my own last, unpublished commit) to keep every commit green.
