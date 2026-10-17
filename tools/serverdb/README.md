# serverdb: VMaNGOS world database for community drop rates

Build-time Python tooling that fetches the VMaNGOS world database, the importer's third drop-rate source
(`vmangos`, next to Questie and AtlasLoot). Standard library only; `pytest` is needed for the tests.

## Source and pin

[VMaNGOS](https://github.com/vmangos/core) (GPL-2.0) publishes its world database as the release `db_latest`. The
tool uses the SQLite asset `db-sqlite-<commit>.zip`, so no MySQL server and no dump parsing are needed.
`serverdb.lock` (committed) pins the repository, tag, full commit, asset name, the archive member holding the world
database (`sqlite-dump/mangos.sqlite`) and two SHA-256 hashes: the archive's and the repository licence's at that
commit. The tool never reads a moving `latest` without the pin.

## Commands

    python tools/serverdb/fetch.py      # download (once), verify, extract into ~/.cache/wowforever-serverdb/4641790/
    python tools/serverdb/extract.py    # print the row counts of the world database's loot and vendor tables

`fetch.py` downloads the asset from the GitHub release into `~/.cache/wowforever-serverdb/<short commit>/`
(`--cache <root>` moves the root). A lock without a hash gets it written on the first download; every later run
verifies the cached archive against it and refuses, deleting the file, when it differs. HTTP 5xx answers are retried
after 2, 4 and 8 seconds, the policy of `tools/clientdata/clientdata/http.py` (copied, since that client needs
`requests`). Only the world database member is extracted, as `mangos.sqlite`; nothing from the archive is executed.
The cache directory then holds:

| File | What |
|---|---|
| `db-sqlite-4641790.zip` | the release asset (42 MB) |
| `mangos.sqlite` | the world database (146 MB) |
| `LICENSE` | the VMaNGOS GPL-2.0 licence text at the pinned commit (the archive has none) |
| `serverdb.lock` | a copy of the lock; the importer writes its `asset` and `commit` as `meta.serverdb_*` |

None of these files are committed.

## How the importer uses it

The importer opens `mangos.sqlite` read-only and immutable and queries it directly (`--server-db-dir <dir>`, or
`-Dserverdb.dir` in the `import-data` Maven profile). Exporting CSV files first would add a step and a format
without a benefit, so `extract.py` only inspects the database. Without the option, or when the directory has no
`mangos.sqlite`, the import skips the community rates (`serverdb.skipped = 1`).

Rates are computed as the server's loot code (`src/game/LootMgr.cpp`) would roll them at patch 1.12 (VMaNGOS
patch index 10, rows with `patch_min <= 10 <= patch_max`, without the items `forbidden_items` bans at that patch):

- A creature reaches its `creature_loot_template` entry through `creature_template.loot_id`, read from its newest
  row at or before patch 10. Several creatures can share one table. Creature entries are the Classic NPC ids
  Questie uses.
- A template holds groups and ungrouped entries. A group is the rows with `groupid > 0` and `mincountOrRef > 0`.
  Every other row is an ungrouped entry: items of group 0 and every reference, whatever its `groupid`.
- An ungrouped item rolls on its own with `|ChanceOrQuestChance|` percent, capped at 100. A negative value marks a
  quest-only item. A 0-chance item outside a group never drops.
- A reference (`mincountOrRef < 0`) rolls its own chance. A negative chance there is a plain chance, and a 0-chance
  reference is skipped. On a hit, `reference_loot_template` entry `-mincountOrRef` is processed `maxcount` times.
  With the reference's `groupid` g above 0 only group g of that template rolls; with 0 the whole template does.
  References nest up to five levels deep.
- A group rolls once and yields at most one item. Explicit chances are taken in row order, each limited to what is
  left of 100 %, and a row of 100 % or more takes all that is left. The 0-chance rows share the rest equally. An
  item listed twice in one group adds its two shares.
- Separate entries and groups roll independently, so an item reached several ways combines as `1 - prod(1 - p)`.

VMaNGOS-only pairs add no drop rows, because its reference tables carry the world-drop pools and world drops are
never a mob's own loot. Of the (NPC, item) pairs Questie or AtlasLoot list as drops, the ones VMaNGOS also rates
get a `drop_rate_source` row with `source = 'vmangos'`.

VMaNGOS rates are a vanilla emulator's configured chances, and Forever's loot is reworked, so observed and curated
data outranks them. The merged `item_npc_drop.drop_pct` takes the first of:

1. Questie corrections
2. AtlasLoot item overrides
3. Questie's Wowhead data
4. VMaNGOS, ordinary rows
5. the AtlasLoot drop table
6. VMaNGOS quest-only rows

A quest-only rate is the chance while the looter is on the quest, not per kill. An item is quest-only when every way
to it ends in an item row with a negative chance; a reference never makes a rate quest-only. Such a rate headlines
only when no other source rates the pair, and its `drop_rate_source` row has `quest_only = 1`. It still counts in
`source_count` and may set `disagrees`.

Report counters: `serverdb.loot.rows`, `serverdb.reference.rows`, `serverdb.loot.forbidden`, `serverdb.creatures`,
`serverdb.unmatched.npcs` (creatures with loot that are not in the NPC table), `serverdb.rates`,
`serverdb.rates.questOnlyPairs`, `serverdb.rates.questOnly` (rows), `serverdb.zeroChance.ungrouped`,
`serverdb.references.zeroChance`, `serverdb.references.depthCapped`, `serverdb.references.missing` and
`serverdb.skipped`; `provenance.sources.vmangos` counts the rows written.

## Tests

    tools/clientdata/.venv/Scripts/python -m pytest tools/serverdb

That is the Windows path of the clientdata virtual environment; any Python with pytest works.

The tests build a fake archive and a tiny world database; they need no network.
