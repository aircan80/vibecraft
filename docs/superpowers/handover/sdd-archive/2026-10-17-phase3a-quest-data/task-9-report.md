# Task 9 report: community drop rates from the VMaNGOS world database

Status: DONE_WITH_CONCERNS. Commit `1f8fd57` on `feature/phase3a-quest-data`: `feat(importer): community drop rates from the VMaNGOS world database`.
I did not regenerate the shipped database, and I did not add the shipped-data check. Both belong to Task 10.

## What was built

**Python, `tools/serverdb/`.** It uses only the standard library. Tests need pytest, and the clientdata venv works.
- `fetch.py` reads the committed `serverdb.lock` and downloads `https://github.com/vmangos/core/releases/download/db_latest/db-sqlite-4641790.zip` into `~/.cache/wowforever-serverdb/4641790/`.
  - On the first download it pins the archive's SHA-256 in the lock. Later runs verify against the pin. On a mismatch the run fails and deletes the file.
  - It retries HTTP 5xx after 2, 4 and 8 seconds and fails on any other error. I **copied** this policy from `tools/clientdata/clientdata/http.py` rather than importing it, because that client needs `requests`.
  - It extracts only the member `sqlite-dump/mangos.sqlite`, by exact name, to `mangos.sqlite`. Nothing from the archive is executed.
  - **Licence.** The zip has no licence file. `fetch.py` downloads the repo `LICENSE` at the pinned full commit, pins its SHA-256 the same way, and stores it as `LICENSE` beside the data.
  - It writes a copy of the lock as `<cache>/serverdb.lock`. The importer turns that copy into `meta.serverdb_asset` and `meta.serverdb_commit`.
  - The lock validates the asset name against `db-sqlite-<hex>.zip` and requires a matching full 40-digit commit, so the lock cannot inject a path.
- `extract.py` is the inspection script, per the ruling. It opens the database read-only and prints total and patch-10 row counts for `creature_loot_template`, `reference_loot_template`, `gameobject_loot_template` and `npc_vendor`.
- `README.md` documents the attach-directly choice. `pytest.ini`, `requirements.txt` (pytest only) and `.gitignore` entries are added too.

**Java, `importer/.../serverdb/`.**
- `ServerDbSource` opens `<dir>/mangos.sqlite` read-only through `SQLiteConfig.setReadOnly`. It returns empty when the file is absent, and refuses a file that lacks `creature_template`, `creature_loot_template` or `reference_loot_template`. It queries in place, with no CSV step.
- `LootRow` is one loot-template row.
- `ServerDropRates` computes the rates and puts them into `DropRates`.
- `ServerDbStage` handles the cases where the stage is skipped and reads the lock copy for the meta keys.
- Option `--server-db-dir <dir>` goes in `ImportOptions`, with `USAGE` text and an 8-argument overload, so existing callers are unchanged.
- The `-Pimport-data` profile gets property `serverdb.dir`, default `${user.home}/.cache/wowforever-serverdb/4641790`. It is always passed as `--server-db-dir`. `help:effective-pom` confirmed both the default and a `-Dserverdb.dir` override.
- **Skip behaviour.** Without `--server-db-dir`, the run reports `serverdb.skipped = 1`. If the directory has no `mangos.sqlite`, the run also reports `serverdb.skipped = 1` and adds the note `serverdb: no mangos.sqlite in <dir>; community drop rates skipped (run tools/serverdb/fetch.py)`. In both cases no rows or meta are written.
- `MiniJson` became public so the stage can read the lock copy.
- NOTICE gets a "VMaNGOS world database" section. The root copy and the app copy are identical, and `MainFrameTest` checks that. The main README gets a rebuild bullet, a licensing line and a tools-tests bullet.

## Rate rules (SQL and columns)

All rows are read at patch 1.12, which is VMaNGOS patch index 10:
- Loot rows: `SELECT entry, item, ChanceOrQuestChance, groupid, mincountOrRef, maxcount FROM <table> WHERE patch_min <= 10 AND patch_max >= 10`.
- **Creature to loot table:** `SELECT t.entry, t.loot_id FROM creature_template t WHERE t.patch = (SELECT max(p.patch) FROM creature_template p WHERE p.entry = t.entry AND p.patch <= 10) AND t.loot_id <> 0`.
  - The brief said entry is the creature id. In VMaNGOS a creature reaches its `creature_loot_template` entry through `creature_template.loot_id`.
  - 21 creatures use another creature's table, for example 10076 uses 8929, and 13516 to 13519 use 13359.
  - Every loot entry is some creature's `loot_id`. Creature entries are matched to the Questie NPC id.
- **Row chance.** `|ChanceOrQuestChance|`, capped at 100. A negative value is quest-only and counts as `serverdb.rates.questOnly`.
- **Group 0** rows roll independently.
- **A group** (`groupid > 0`) picks at most one row. Explicit chances keep their value.
  - **Equal-share rule:** 0-chance rows share `max(0, 100 - sum(explicit)) / n` equally.
  - A 0-chance row in group 0 never drops and counts as `serverdb.zeroChance.ungrouped`. The real data has none.
- **A reference** (`mincountOrRef < 0`) rolls its row chance `p` once, then the referenced `reference_loot_template` entry `-mincountOrRef` `maxcount` times. This follows the MaNGOS `LootTemplate::Process` multiplicator.
  - An inner probability `q` becomes `p * (1 - (1 - q)^maxcount)`.
  - References resolve recursively. The fifth level is the last one followed. A sixth level counts as `serverdb.references.depthCapped`, and cycles stop there too. An unknown reference counts as `serverdb.references.missing`.
  - The memo covers creature tables and top-level references only.
- **Several paths to one item** combine as independent rolls, `1 - prod(1 - p)`.
- The result is a percentage rounded to six decimals, which removes float noise.
- **Pairs.** Rates go into `DropRates` for every creature in the imported NPC table. `drop_rate_source` rows with `source = 'vmangos'` are written by the existing `DropProvenance` for every pair that `item_npc_drop` or `boss_loot` holds, and the existing disagreement logic (more than 2 percentage points) covers them.
  - **VMaNGOS-only pairs add no drop rows.** Its reference tables are the world-drop pools, and decision 11 says world drops are never a mob's own loot.
  - This differs from "a vmangos row for every pair the server DB rates". Writing all 1.59 M pairs would add world drops to mobs and about 1.4 M orphan provenance rows.

## Precedence

`DropRateLoader` constants are now public, with VMaNGOS slotted in after the corrections. The relative order of the existing sources is unchanged.

| Precedence | Source |
|---|---|
| 1 | `QUESTIE_CORRECTION` |
| 2 | `VMANGOS` (`ServerDropRates.PRECEDENCE`) |
| 3 | `ATLASLOOT_OVERRIDE` |
| 4 | `QUESTIE_WOWHEAD` |
| 5 | `ATLASLOOT` |

The constants were previously 1 to 4 and private, and nothing persists them.

## Lock contents (`tools/serverdb/serverdb.lock`, committed)

```json
{
  "repo": "vmangos/core",
  "tag": "db_latest",
  "commit": "464179081673cfd240f7ffe7f0daf96bca0b5a70",
  "published_at": "2026-09-28T12:29:04Z",
  "asset": "db-sqlite-4641790.zip",
  "sha256": "bb2f1dd69171043a52657574f54948fd30941530fdc6eec12bce62c655428501",
  "world_db": "sqlite-dump/mangos.sqlite",
  "license": {"path": "LICENSE", "spdx": "GPL-2.0", "sha256": "b8632c574ee53ddf15ed7dd65b4a36611da50a77dffda62f978a30c076005456"}
}
```

`fetch.py` wrote the archive SHA-256 on the first real download. It equals the `sha256:` digest GitHub publishes for the asset. A second run verified the cache without downloading and left the lock byte-identical.

Contents of the cache directory, none of which are committed:

| File | Size |
|---|---|
| zip | 42,482,870 B |
| `mangos.sqlite` | 146,067,456 B |
| `LICENSE` | 18,737 B |
| `serverdb.lock` | copy of the lock |

`extract.py` on the real database:

| Table | Rows | Patch-10 rows |
|---|---|---|
| `creature_loot_template` | 232,486 | 227,235 |
| `reference_loot_template` | 10,231 | 9,976 |
| `gameobject_loot_template` | 13,850 | 13,839 |
| `npc_vendor` | 13,269 | - |

## Real strict import (scratchpad, never the shipped path)

The command used `java -jar importer/target/importer.jar --out <scratch>/wowforever.sqlite --fail-on-unsupported --client-data-dir ~/.cache/wowforever-clientdata/out/current --server-db-dir ~/.cache/wowforever-serverdb/4641790`. It exited 0 in about 7 s, with empty stderr.

| Counter | Value |
|---|---|
| `serverdb.loot.rows` | 227,235 |
| `serverdb.reference.rows` | 9,976 |
| `serverdb.creatures` | 4,164 |
| `serverdb.unmatched.npcs` | 6 |
| `serverdb.rates` (pairs fed to `DropRates`) | 1,593,225 |
| `serverdb.rates.questOnly` | 2,582 |
| `serverdb.references.depthCapped` / `.missing` / `serverdb.zeroChance.ungrouped` | 0 / 0 / 0 |
| `provenance.sources.vmangos` (rows written) | 146,551 |
| `provenance.pairs` | 146,926 |
| `provenance.disagreeing` | 5,912 |
| `table.drop_rate_source` | 158,247 (shipped today: 11,696) |
| `item_npc_drop` rows with a `drop_pct` | 146,902 of 164,447 (146,458 headlined by `vmangos`) |
| output database size | 80.4 MB (shipped today: about 67 MB) |

The six unmatched creatures are all `[UNUSED]` ones: 725, 4439, 10402, 10403, 10404 and 11468. The meta table gets `serverdb_asset = db-sqlite-4641790.zip` and `serverdb_commit = 4641790816…`.

**Hogger (448) and Huge Gnoll Claw (1931).**

| Source | Value |
|---|---|
| VMaNGOS raw row | `(448, 1931, ChanceOrQuestChance -100.0, group 0)`, a quest-only drop |
| VMaNGOS rate | 100.0 |
| Questie Wowhead | 83.9965 |
| AtlasLoot | no rate |
| merged `item_npc_drop` | `drop_pct 100.0`, `pct_source vmangos`, `source_count 2`, `disagrees 1` |

How VMaNGOS compares with each other source, on the pairs they share:

| Row kind | Other source | Shared pairs | Average gap (points) | Over 2 points |
|---|---|---|---|---|
| ordinary | Questie Wowhead | 6,667 | 4.87 | 2,403 |
| quest-only | Questie Wowhead | 2,242 | 42.82 | 2,037 |
| via reference | Questie Wowhead | 246 | 4.29 | 129 |
| ordinary | AtlasLoot | 175 | 10.15 | 108 |
| via reference | AtlasLoot | 1,859 | 6.18 | 1,328 |
| quest-only | Questie corrections | 80 | 24.73 | 68 |

## TDD evidence

- **Java.** I wrote the tests and the `ServerDbFixture` builder first. The first run failed to compile: `ServerDbSource`, `serverDbDir()` and the nine-argument `ImportOptions` were missing. After the implementation, every test passed.
  - **Mutation check.** Setting the depth cap to 4 and breaking the equal share made `ServerDropRatesTest` fail 3 of 14: the equal-share, nested-cap and cycle tests. I then restored the code.
- **Python.** The `extract.py` tests were red first, with `ModuleNotFoundError`, then green. I wrote `fetch.py` before its tests, so those tests are test-after, not red-first.
- Tests added:
  - `ServerDropRatesTest`, 14 tests:
    - plain chance, and negative quest chance
    - `loot_id` and newest-patch mapping, and patch filtering
    - reference multiplication, and equal share inside a reference and in the creature's own group
    - a full group leaves nothing to share, and an ungrouped zero-chance row never drops
    - `maxcount` repeats, and two paths combining
    - the five-level nested cap, a cycle stopping at the cap, and a missing reference
    - unmatched NPCs
    - precedence against the correction, Wowhead, AtlasLoot and override sources
    - disagreement flagging, written through `SqliteWriter`: `2/1` and `drop_pct 40.0`, plus the report's `vmangos vs questie-wowhead: 1 disagreeing of 2 shared`
  - `ServerDbSourceTest`, 4 tests: absent file, reading rows and ids at a patch, read-only, missing table refused.
  - `ImportPipelineTest`:
    - With no server database: `serverdb.skipped = 1`, no `vmangos` rows, and no `serverdb.loot.rows` or `serverdb.unmatched.npcs`.
    - With the fixture server database:
      - 639/2874 is `60.0/questie-correction/5/1`, and 11520/14149 is `10.0/vmangos/2/1` in `item_npc_drop` and in `boss_loot`.
      - A VMaNGOS-only pair adds no row, and the meta keys are written.
      - `serverdb.loot.rows = 4`, `reference.rows = 1`, `unmatched.npcs = 1`, `rates = 3`, `provenance.sources.vmangos = 2`.
    - A missing directory: skipped with the note.
  - `ImportOptionsTest`: parsing, the default, and a missing value for `--server-db-dir`.
  - pytest:
    - `test_fetch.py`, 11 tests: first download pins both hashes and extracts only the world database, a later run verifies without downloading, archive and licence mismatches are refused, 5xx is retried while 404 fails, a missing member fails, an odd lock is refused, and the committed lock names the URLs.
    - `test_extract.py`, 4 tests.

## Verification

| Check | Result |
|---|---|
| `./mvnw -B -q verify`, shipped-data group included | exit 0 |
| lua-table | 61 tests, 0 failures |
| importer | 397 tests, 0 failures |
| app | 259 tests, 0 failures, `ShippedDataTest` 30 of them |
| `./mvnw -B -q -pl importer -am verify`, after the last doc edit | exit 0 |
| pytest `tools/serverdb` | 15 passed |
| pytest `tools/clientdata` | 80 passed, unchanged |

## Concerns

1. **The precedence wording does not match the existing order.** The ruling text says "Questie corrections, then VMaNGOS, then AtlasLoot, then Questie base". The existing code ranks Questie Wowhead (3) above the AtlasLoot drop table (4). Following "without reordering the existing ones", I kept that order and inserted VMaNGOS at 2. If AtlasLoot really should beat Questie Wowhead, that is a separate change.
2. **Quest-only VMaNGOS rates now headline the quest items.** A negative `ChanceOrQuestChance` is the chance while you are on the quest. Questie and Wowhead report the observed rate. On 2,242 shared pairs the two differ by about 43 points on average, and Hogger's claw reads 100 against 84. Decision 10 says VMaNGOS rates "may headline when Questie has none", which could be read as Questie winning when it has a rate.
   - **Option:** rank quest-only VMaNGOS rows below Questie Wowhead.
   - **Option:** keep them first and have plan 3B label them "quest drop".
   - I left the ruled precedence unchanged.
3. **Task 10's regeneration will be a much bigger change than the other tasks.**
   - The headline rate source changes for about 146 k drop rows.
   - `drop_rate_source` grows from 11,696 to 158,247 rows, about +13 MB.
   - The README "Known limitations" line "Drop percentages exist for about 11,000 of the 164,447 drop rows" will need updating to about 146,900.
4. **Pairs only VMaNGOS knows are not added**, as described above. If the owner wants VMaNGOS to add "Dropped by" entries for direct, non-reference rows, that is a follow-up.
5. **A missing directory only skips with a note.** `--server-db-dir` pointing at a directory without `mangos.sqlite` does not fail the import, so the Maven default works before the fetch has run. A typo is therefore silent apart from the report note. Task 10's shipped-data check on Hogger will catch a skipped regeneration.
6. **Memory.** About 1.59 M (npc, item) entries are held in `DropRates`, mostly world-drop pools. That was fine for the 7 s import with the default heap. If the Maven JVM ever runs short, filter the rates to the listed pairs before putting them.
7. **Not consumed yet.** `gameobject_loot_template`, because `item_object_drop` has no rate column, and `npc_vendor`, per decision 10's first-consumer rule. Loot `condition_id` rows (3,727) are included without evaluating their conditions.


## Follow-up: precedence fix and quest-only marking (commit `924c5ba`)

The controller ruled on concerns 1 and 2. VMaNGOS chances are a vanilla emulator's configuration and Forever's loot is reworked, so Questie's observed data outranks them. Quest-only rows rank last.

**Precedence now in `DropRateLoader`, lower wins:**

| Precedence | Source |
|---|---|
| 1 | `QUESTIE_CORRECTION` |
| 2 | `ATLASLOOT_OVERRIDE` |
| 3 | `QUESTIE_WOWHEAD` |
| 4 | `VMANGOS`, ordinary rows |
| 5 | `ATLASLOOT`, the drop table |
| 6 | `VMANGOS_QUEST_ONLY` |

**Interpretation of the ruling's list.** The ruling listed "1 Questie corrections, 2 Questie-Wowhead, 3 VMaNGOS, 4 AtlasLoot, 5 Questie base, 6 VMaNGOS quest-only".
- The importer has no separate "Questie base" rate source. Questie's only rate sources are the corrections and the Wowhead data, so I mapped "Questie base" to nothing.
- I kept the AtlasLoot item override above Questie Wowhead, as the v0.1 spec ruled.
- On real data the order between the override and Wowhead or VMaNGOS barely matters. Overrides share 4 pairs with Wowhead and 15 with VMaNGOS, and none of the 15 disagree. If the override should sit below Wowhead or VMaNGOS, it is a one-line constant swap.

**Quest-only marking.**
- `DropRate` gains a `questOnly` component. A two-argument constructor keeps every existing caller unchanged.
- A VMaNGOS rate is quest-only when every path to the item is quest-only. A path is quest-only when the item's own row, or a reference row on the way, has a negative `ChanceOrQuestChance`. An item with at least one ordinary path stays an ordinary rate at precedence 4.
- `drop_rate_source` gets the additive column `quest_only INTEGER NOT NULL DEFAULT 0`, set to 1 for those rows. It stays under schema version 3, like the Task 7 and 8 tables.
- The `disagrees` and `source_count` logic is unchanged: a quest-only row still counts as a source and may disagree.
- New counter `serverdb.rates.questOnlyPairs` counts pairs. `serverdb.rates.questOnly` still counts rows.

**Tests, written first.** The first run failed to compile because the three-argument `DropRate`, `questOnly()` and `VMANGOS_QUEST_ONLY` were missing. All are now green.
- `DropRatesTest.theSourcesRankInTheRuledOrder` checks all six ranks.
- `ServerDropRatesTest`:
  - The precedence test was rewritten: Wowhead beats VMaNGOS, VMaNGOS beats the AtlasLoot drop table, and the override beats VMaNGOS.
  - New `aQuestOnlyRateHeadlinesOnlyWhenNothingElseRatesThePair`: Hogger-like 100 % quest-only loses to Wowhead 84 % and to AtlasLoot, and headlines when alone.
  - New `aRateIsQuestOnlyWhenEveryPathToTheItemIs`: quest-only inside a reference is flagged, and a mixed path gives an ordinary 75 %.
  - The negative-chance test asserts the flag.
  - The `SqliteWriter` disagreement test now expects the Wowhead headline and `quest_only = 0`.
- `ImportPipelineTest`: the fixture's 639/2874 VMaNGOS row is now quest-only. It is written with `quest_only = 1` and does not change the correction headline (`5/1`).

**Re-run of the strict scratch import.** It exited 0 with empty stderr. The counters match the first run, plus `serverdb.rates.questOnlyPairs = 2,590`.

| Counter | Value |
|---|---|
| `serverdb.loot.rows` | 227,235 |
| `serverdb.unmatched.npcs` | 6 |
| `serverdb.rates` | 1,593,225 |
| `provenance.sources.vmangos` | 146,551 |
| `provenance.disagreeing` | 5,912 |

**Hogger (448) and Huge Gnoll Claw (1931):**

| Field | Value |
|---|---|
| `item_npc_drop` | `drop_pct 83.9965`, `pct_source questie-wowhead`, `source_count 2`, `disagrees 1` |
| `drop_rate_source` | `questie-wowhead 83.9965 quest_only 0`, and `vmangos 100.0 quest_only 1` |

**Headline sources of `item_npc_drop`, 164,447 rows:**

| Headline source | Rows |
|---|---|
| `vmangos` | 137,376, of which 208 quest-only rows headline because nothing else rates the pair |
| `questie-wowhead` | 9,334 (before the fix: 267) |
| `questie-correction` | 106 |
| `atlasloot` | 67 |
| `atlasloot-override` | 19 |
| no rate | 17,545 |

`drop_rate_source` holds 2,456 rows with `quest_only = 1`. 2,248 of them share their pair with another source.

**Verification.**

| Check | Result |
|---|---|
| `./mvnw -B -q verify` | exit 0 |
| lua-table | 61 tests, 0 failures |
| importer | 400 tests, 0 failures |
| app | 259 tests, 0 failures, `ShippedDataTest` 30 of them |
| pytest `tools/serverdb` | 15 passed |
| pytest `tools/clientdata` | 80 passed |

`tools/serverdb/README.md` now documents the new order and the quest-only rule.


## Fix round 1: loot rolls follow LootMgr.cpp (commit `cd2795e`)

**Rules now implemented in `ServerDropRates`.** They follow `LootStoreItem::IsValid`, `LootTemplate::AddEntry` and `Process`, and `LootGroup::Roll` in the copy of `src/game/LootMgr.cpp` at the pinned commit.
1. **Template split.** Groups are the rows with `groupid > 0` and `mincountOrRef > 0`. Every other row is an ungrouped entry: group-0 items, and every reference whatever its `groupid`.
2. **Ungrouped items.** Each rolls on its own with `|chance|`, capped at 100. A negative chance makes the item quest-only. A 0-chance ungrouped item is invalid and never drops (`serverdb.zeroChance.ungrouped`).
3. **References.**
   - A reference rolls its own chance. A negative chance there is a plain chance, so a reference is never quest-only itself.
   - A 0-chance reference is skipped and counted (`serverdb.references.zeroChance`).
   - On a hit the referenced template is processed `maxcount` times, as independent passes, giving `p * (1 - (1 - q)^maxcount)`.
   - With the reference's `groupid` g above 0, a pass rolls only group g of the referenced template. Its ungrouped items and nested references are not processed. With g = 0 the whole template is processed.
   - Nesting only happens through whole-template passes. The depth cap of 5 is unchanged.
   - The memo is keyed on (reference id, group selector, depth).
4. **Group roll.**
   - A group rolls once and yields at most one item.
   - Explicit-chance rows are taken in row order (by item id, the primary-key order) and are mutually exclusive. Each takes `min(chance, what is left)`, and a row of 100 % or more takes all that is left.
   - The 0-chance rows share `max(0, 100 - explicit) / n`.
   - An item listed twice in one group adds its two shares, p1 + p2.
   - Separate entries and groups combine as independent rolls.
5. **Quest-only** applies only to plain item rows with a negative chance, in creature or reference templates. A rate is quest-only when every way to the item ends in such a row.
   - **One deviation from your rule 5:** quest rows in a group still consume the group's roll. `LootGroup::Roll` subtracts every explicit row, quest rows included. `RawTotalChance` leaves quest rows out, but the code uses it only for `Verify` and `TotalChance`, not for the roll.
   - In this release it changes nothing: no group in either template table holds a quest row, so no group holds a quest row next to equal-chanced rows.
6. **Forbidden items, added as part of the same alignment.** The server's loader drops item rows whose item `forbidden_items` bans at the patch: `after_or_before = 0 AND patch <= 10`, or `after_or_before = 1 AND patch >= 10`. `ServerDbSource` now does the same and reports the count as `serverdb.loot.forbidden`, which is 3 in the real data.

**Minor items.**
- **Immutable open.**
  - Java opens `jdbc:sqlite:<file URI>?immutable=1` with `SQLiteOpenMode.OPEN_URI` plus read-only.
  - Python opens `Path.as_uri() + "?mode=ro&immutable=1"`, so the path is percent-encoded.
  - Both are tested on a path with spaces, and the Python test also uses a `#`. Both are tested while another connection holds `BEGIN EXCLUSIVE`: with immutable the read succeeds, and before this fix it failed on the lock.
- **Failed pins.** An archive or licence that fails its pin now also removes `mangos.sqlite` and the lock copy.
- **Download errors.** `download` catches `URLError` and `OSError` after `HTTPError`, removes the `.part` file and raises `FetchError` with the reason. `FetchError` now takes a status or a reason.
- **`ServerDbSource.connection()`** is package-private.
- **README.** The coverage sentence now reads "Of the (NPC, item) pairs Questie or AtlasLoot list as drops, the ones VMaNGOS also rates get a `drop_rate_source` row". The rules section is rewritten for the semantics above, and the over-long lines are re-wrapped.

**Tests, red first.**
- **Java.** 6 new `ServerDropRatesTest` cases failed against the previous code, and the immutable `ServerDbSourceTest` case errored on the writer's lock. All pass now.
  - The 6 cases that failed: group selector, duplicate in a group equals p1 + p2, explicit sum above 100 truncated in row order (including a 100 % row taking the rest), zero-chance reference skipped and counted, negative reference read as a plain chance, and `forbidden_items`.
  - Also new: a reference with groupid 1 stays out of the creature's group 1, and a reference listed twice rolls twice for 75 %. Both already held on the old code for their numbers, and they now guard the new structure.
  - `zeroChanceRowsShareWhatTheirGroupLeavesEqually` now puts its references outside groups. The group-2 items then share 100 / 3 each.
- **Python.** The 4 new or changed tests (immutable and encoded path, the two cleanup cases, and network or disk errors) fail against the previous `fetch.py` and `extract.py`, and pass now.

**The three SQL counts you asked for**, on `mangos.sqlite` at patch 10 (`patch_min <= 10 AND patch_max >= 10`):

| Query | Count |
|---|---|
| `creature_loot_template WHERE mincountOrRef < 0 AND groupid > 0` | 1,814 rows, over 53 distinct references |
| `reference_loot_template WHERE mincountOrRef < 0 AND groupid > 0` | 0 |
| negative-chance references (`mincountOrRef < 0 AND ChanceOrQuestChance < 0`) | 0 in `creature_loot_template`, 0 in `reference_loot_template` |

Related counts at patch 10, which are 0 in both tables unless noted:
- zero-chance references
- quest rows in groups
- groups with an explicit sum above 100
- duplicate items within one group
- zero-chance ungrouped items
- `mincountOrRef = 0`
- a group selector pointing past the referenced template's groups
- reference listed twice in one template: 17

**Effect on this release: none beyond the forbidden items.**
- None of the 1,814 grouped references point at a template with a row outside group g. Every referenced template is exactly that group, so the selector changes nothing.
- 26 creature groups mix a reference with items, and 7 hold several references. Their explicit sums stay at or below 100, so the old shared-group reading and the correct independent reading give the same per-item probability.
- Compared with the previous scratch import, no written `vmangos` value changed by more than 1e-6. 3 pairs disappeared (the forbidden items), and no `disagrees` flag flipped.
- The fix makes the rules right for later database releases. It does not move any of today's numbers.

**Re-run of the strict scratch import.** It exited 0 with empty stderr.

| Counter | Value |
|---|---|
| `serverdb.loot.rows` | 227,232 (3 removed by `forbidden_items`) |
| `serverdb.loot.forbidden` | 3 |
| `serverdb.reference.rows` | 9,976 |
| `serverdb.creatures` | 4,164 |
| `serverdb.unmatched.npcs` | 6 |
| `serverdb.rates` | 1,593,222 |
| `serverdb.rates.questOnlyPairs` | 2,590 |
| `serverdb.rates.questOnly` (rows) | 2,582 |
| `serverdb.references.zeroChance` / `.depthCapped` / `.missing` | 0 / 0 / 0 |
| `provenance.sources.vmangos` | 146,548 |
| `provenance.pairs` | 146,923 |
| `provenance.disagreeing` | 5,912 |
| `table.drop_rate_source` | 158,244 |

**Hogger (448) and Huge Gnoll Claw (1931):**

| Field | Value |
|---|---|
| `item_npc_drop` | `drop_pct 83.9965`, `pct_source questie-wowhead`, `source_count 2`, `disagrees 1` |
| `drop_rate_source` | `questie-wowhead 83.9965 quest_only 0`, and `vmangos 100.0 quest_only 1` |

**Headline sources of `item_npc_drop`, 164,447 rows:**

| Headline source | Rows |
|---|---|
| `vmangos` | 137,373, of which 208 quest-only rows headline alone |
| `questie-wowhead` | 9,334 |
| `questie-correction` | 106 |
| `atlasloot` | 67 |
| `atlasloot-override` | 19 |
| no rate | 17,548 |

`drop_rate_source` holds 2,456 `quest_only` rows, and 2,248 of them share their pair with another source.

**Verification.**

| Check | Result |
|---|---|
| `./mvnw -B -q verify` | exit 0 |
| lua-table | 61 tests, 0 failures |
| importer | 415 tests, 0 failures |
| app | 259 tests, 0 failures, `ShippedDataTest` 30 of them |
| pytest `tools/serverdb` | 16 passed |
| pytest `tools/clientdata` | 81 passed |
