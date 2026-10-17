# Task 19 report: hotfix overlay, build watch, AtlasLoot Classic Forever (interim)

Status on 2026-10-17: the code is committed and green (02ae8bf). The fork swap, the 10-rate spot-check and the
single database regeneration wait for the AtlasLoot Classic Forever zip, which CurseForge refuses to serve to
automation. The controller ruled to build everything else first.

## What was built (commit 02ae8bf)

**tools/clientdata**
- `fetch.py --hotfix-cache [URL]` snapshots the Raidbots mirror of the beta client's `DBCache.bin`. The default URL is
  the `classic_beta` path. The header and every entry are parsed before the file is kept as
  `~/.cache/wowforever-clientdata/hotfix/<sha256>.bin`.
- An index lists every snapshot. `build.lock` records the newest snapshot under `hotfix`: header build, SHA-256,
  Last-Modified, fetch time and size. It also records all snapshots and the WoWDBDefs commit with the hash of each
  definition.
- `clientdata/dbd.py` parses WoWDBDefs, picks the layout of a build and decodes a record. `clientdata/hotfix.py` reads
  the cache, hashes table names with SStrHash, builds the overlay and writes CSVs in wago's format.
- `convert.py` (full run, or `--hotfix-only`) writes `csv/ItemSparse.hotfix.csv`, `Item.hotfix.csv`,
  `ItemEffect.hotfix.csv` and `hotfix.json`. It stops with the build-watch text on a build mismatch, and on any hash
  or column mismatch.
- Overlay rules follow the PM memo. Only status 1 rows apply. An invalidation removes only its own push's row.
  Snapshots accumulate and the newest wins. Push ids cannot order entries, because per-record replies carry
  0x1000000 plus the record id.
- `http.Client.get_response` gained per-request headers and accepted statuses, for the 206 range reply.

**tools/build_watch.py.** One line per upstream: patch service `wow_classic_beta` and `wowf`, wago `/api/builds`,
the Raidbots header build (44-byte range GET) and Last-Modified, QuestieDB master, and AtlasLoot as "manual check".
It exits 1 when anything is stale. Otherwise it exits 2 when an upstream is unknown, else 0. It never imports or
updates anything.

**Importer**
- Item precedence is hotfix, then Forever, then Era (`client_source` gains `hotfix`). An `Item.hotfix` row wins over
  Forever's Item row. `ItemEffect.hotfix` rows replace Forever's rows of the same id.
- Counters: `hotfix.itemsparse.valid`, `.applied`, `.invalidated`, `.notInItemTable`, `hotfix.fromForever`,
  `hotfix.fromEra`, `hotfix.fromNoClient`, `hotfix.item.applied`, `hotfix.itemeffect.applied`,
  `clientdata.items.matched.hotfix` and `clientdata.era.effectsOnHotfixItems`.
- `ClientDataSource` checks the overlay against the lock: the header build, the build, the snapshot SHA and each
  CSV's hash. A mismatched header build fails with "hotfix cache header build N differs from build.lock build B: the
  client moved on. Run tools/build_watch.py, ...".
- Footers are written by the importer as `source_note`. A hotfix item reads "Forever stats revealed by a Blizzard
  hotfix · beta 70450 · cache 17 Oct 2026". An Era item reads "Classic Era values. Forever stats are still hidden."
  A Forever item has none.
- Meta keys `client_hotfix_sha256` and `client_hotfix_header_build` are written.
- AtlasLoot, designed so the fork drops in through `sources.properties` only:
  - `SourceSpec` supports `atlasloot.kind=zip` with name, author, license, version, page, zipDir, zip and sha256.
    `SourceFetcher` never downloads it. It checks the SHA-256 and extracts only the pinned entries, refusing paths
    that point outside the cache.
  - `atlasloot.rateSource` names the rates, `atlasloot-forever` for the fork and `-override` for its overrides.
    Positions 2 and 5 are unchanged.
  - `atlasloot.baseline.*` pins Hoizame. `AtlasLootBaseline.merge` keeps a dropped boss row while Questie's rates,
    VMaNGOS' rates or Questie's `npcDrops` list the pair. It counts `atlasloot.forever.removedVsHoizame` (with
    `.kept`, `.dropped`, `.unplaced`) and `atlasloot.forever.additions`.
  - Meta records `atlasloot_name`, `atlasloot_version`, `atlasloot_sha256` and `atlasloot_baseline_commit`.

**App**
- An item without client data shows the muted header note "Stats hidden until this item drops on a realm". It uses
  the new `HeaderPanel.setNote`.
- `RateSource` labels `atlasloot-forever` "AtlasLoot Forever" and `atlasloot-forever-override` "AtlasLoot Forever
  override". The hotfix and Era footers are tooltip lines, so `ItemCardPanel` shows them unchanged.

**Docs.** README "Rebuild the data" now starts with the build watch and covers the zip pin and the hotfix overlay.
The tools README gained "Hotfix overlay" and "build_watch.py" sections.

## Red to green

- `HotfixOverlayTest`: red, a compile failure (no `ClientDataSource.hotfix()` or `HotfixInfo`), then green with 7
  tests.
- `DropRateLoaderTest.atlasLootRatesCarryThePinnedRateSourceName`: red (unresolved method), then green.
- `AtlasLootBaselineTest`: red (no class), then green with 2 tests.
- `TooltipBuilderTest`: three tests went red on the new Era footer text and were updated to it.
- `ItemSparseLoaderTest.withoutTheEraTableItemOnlyRowsKeepClassAndIcon`: red, because `hotfix()` required a lock.
  Fixed by skipping the lock when no overlay exists.
- Written together with their code, without a red run first: `ZipSourceTest` (5), `ListedElsewhereTest`, the
  `SqliteWriterClientDataTest` hotfix test, the app `RateSourceTest` and `ItemPageTest` additions, and the Python
  `test_hotfix.py` (21) and `test_build_watch.py` (11).

## Hotfix snapshot

| Field | Value |
|---|---|
| URL | https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin |
| Header | magic XFTH, version 9, build 70450 |
| SHA-256 | 6decce9f13b485f5c920ce2f0601a61fc4175724de88cdf959a3050cd334b1cf (byte-identical to the review's download) |
| Last-Modified | Sat, 17 Oct 2026 02:57:21 GMT |
| Fetched | 2026-10-17T17:49:24Z, 3,380,951 bytes |
| WoWDBDefs commit | b9a63096cdd99b26967f17f9515377ae62cadfd7 (lists 1.60.1.69876 to 70450) |
| ItemSparse | 4,863 valid records (4,872 entries; 9 records twice, byte-identical), 38 invalidated (all push 112709, none undoes a row), 0 undone |
| Item | 4,863 valid rows |
| ItemEffect, ItemXItemEffect | 0 rows in the cache |

**Decoder check against wago's 70450 CSVs.**
- 87 hotfix ItemSparse rows overlap the base table. 70 have equal values, and 69 of those lines are byte-identical
  (one differs in string quoting only). The 17 that differ change ItemLevel, SheatheType or StatPercentEditor, which
  is what a hotfix changes.
- All 4,863 Item rows overlap the base table: 4,857 are equal, all byte-identical, and 6 differ.

**Correction to the review.** Its table hashes for ItemEffect (0xDF2F53CF) and ItemXItemEffect (0x5EE3B0B0) are
wrong. SStrHash gives 0x4002A5B1 and 0x00CB674F, and the cache has no rows under either.

## Scratch import (not shipped)

The scratch import used the shipped command with the overlay converted into `out/current`. It wrote to the scratchpad
and left `app/src/main/resources` untouched.

| Item | client_source | Notes |
|---|---|---|
| 286978 Rotmender's Garb | hotfix | quality 3, item level 23, set 2133, 3 stats, hotfix footer |
| 869 Dazzling Longsword | hotfix (was era) | hotfix footer, no Era footer |
| 16806 Felheart Belt | era | new Era footer |
| 273643 Worgenbane Talisman | hotfix | same name as Questie's, no alt value |
| 19019 Thunderfury | forever | no footer |

**Counter deltas, shipped report to scratch report:**

| Counter | Old | New |
|---|---|---|
| clientdata.items.matched | 20,492 | 21,118 |
| clientdata.items.matched.forever | 15,679 | 15,604 |
| clientdata.items.matched.era | 4,813 | 1,640 |
| clientdata.items.matched.hotfix | absent | 3,874 |
| clientdata.items.iconOnly | 791 | 165 |
| clientdata.coverage.pct | 96 | 99 |
| hotfix.itemsparse.valid / applied / invalidated / notInItemTable | absent | 4,863 / 3,874 / 38 / 989 |
| hotfix.fromEra / fromNoClient / fromForever | absent | 3,173 / 626 / 75 |
| hotfix.item.applied / itemeffect.applied | absent | 3,874 / 0 |
| clientdata.era.effectsOnHotfixItems | absent | 87 |
| clientdata.stats.items | 7,067 | 7,643 |
| clientdata.stats.unknownId | 8 | 28 (new ids 101, 125, 126, 129, 134, 139) |
| clientdata.damage.items / armor.items | 2,359 / 7,603 | 2,455 / 7,944 |
| clientdata.effects | 6,734 | 6,737 |
| clientdata.sets | 200 | 222 |
| clientdata.unverified.block / damage | 186 / 114 | 349 / 184 |
| clientdata.damage.secondaryDropped | 11 | 3 |
| clientdata.unformatted | 171 | 172 |
| clientdata.alt.name / item_level / required_level / class_id / subclass_id | 172 / 510 / 590 / 426 / 967 | 205 / 285 / 493 / 427 / 968 |
| table.item_tooltip_line | 161,815 | 166,366 |
| table.item_stat | 18,471 | 19,843 |
| table.item_set / item_set_item / item_set_bonus | 200 / 1,119 / 571 | 222 / 1,239 / 652 |
| table.item_alt_value | 2,665 | 2,378 |
| table.meta | 16 | 18 |

- **Unchanged.** Items (21,543), icons and every non-item table.
- **Matches the PM.** `hotfix.fromEra` 3,173 and `hotfix.fromNoClient` 626 equal the memo's measurement.

**App suite against the scratch database.** It ran with the database copied temporarily into the app resources and
restored with git: 882 tests, 0 failures. That count includes the drafted `ShippedHotfixDataTest` (6 tests, acceptance
checks 1 to 5) and two `ShippedDataTest` updates. `clientSourceIsHotfixForeverOrEraAndForeverDominates` now allows
`hotfix`, and the icon-only example moves from 212347 Illari's Key, now revealed, to 249791 Shiny Green Apple. The
draft is saved as
`C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\t19\shipped-tests.patch`
and lands with the data commit.

## Build watch, live run (2026-10-17 ~17:45 UTC)

```
patch service wow_classic_beta  fresh         1.60.1.70450 = build.lock
patch service wowf              fresh         1.60.1.70245, older than build.lock 1.60.1.70450 (the lock follows wow_classic_beta)
wago.tools builds               fresh         1.60.1.70450 = build.lock (wow_classic_beta)
Raidbots hotfix cache           STALE         header build 70450, Last-Modified Sat, 17 Oct 2026 02:57:21 GMT; build.lock has no snapshot
QuestieDB master                STALE         master e9a67d1a2753, pinned 49e7dbca1f18
AtlasLoot                       manual check  pinned Hoizame/AtlasLootClassic 8e99341; the AtlasLoot Classic Forever fork is not pinned yet
```

The Raidbots line turns fresh once the regenerated lock is committed. Re-pinning QuestieDB is outside Task 19; that
is the weekly re-pin row of backlog v5.

## Verify (code commit)

- `./mvnw -B verify`: lua-table 61, importer 448, app 876 tests, 0 failures, BUILD SUCCESS.
- Tools: `tools/clientdata` pytest, 116 passed.

## Pending the zip

- The owner places the zip in `C:\Users\aircan80\.cache\wowforever-importer\atlasloot-forever-zip\`.
- Then: set the `atlasloot.*` zip pin in `sources.properties` (version, file, SHA-256, `rateSource=atlasloot-forever`,
  `baseline.*` = Hoizame 8e99341), and add the NOTICE and app-copy lines (Raidbots as the transport of Blizzard
  hotfix rows, and sliccer's GPL-2.0 fork).
- Also: diff the fork against Hoizame, read its Forever-addition flags (the format is unknown until the files are
  seen), and run the 10-rate Wowhead spot-check.
- Finally: the single regeneration, apply the shipped-test patch, full verify, and the data commit.

## Open choices

1. **Era effects on hotfix items.**
   - The facts: 87 browser items have a hotfix ItemSparse row but no Forever `ItemXItemEffect` link, and the cache
     holds no effect rows.
   - The choice: read per table, the precedence gives them the Era effects, used only when neither the hotfix nor
     Forever has an effect row. So they show Era Use and Equip lines under the hotfix footer.
   - The alternative is to drop them.
2. **ItemEffect overlay.** It is header-only on this cache. Effect links stay Forever's `ItemXItemEffect`, and no
   `ItemXItemEffect` overlay exists, since the cache has none.
3. **Spell rows.** The cache's spell rows (`Spell`, `SpellName`, `SpellEffect`, `SpellMisc`) are not read.
4. **Footer date.** The footer shows the newest snapshot's header build and Last-Modified date for every hotfix item,
   not the date of the snapshot that supplied each row. Today these are the same.
5. **Unknown stat ids.** Six new stat ids appear on revealed items (101, 125, 126, 129, 134, 139). They are counted,
   not named.
6. **Build-watch exit code 2.** It marks "unknown, nothing stale", a code beyond the brief's exit 1, so an offline run
   never reads as fresh.
7. **No-data note.** It lives in the item page header, since items without client data have no card.

## Incident

My first CurseForge probe sent the owner's email address in the User-Agent header of three requests, all refused
with HTTP 403. No later request carried it, and no file contains it.

## Fix round 1 (review of 02ae8bf): commit 807185b

The round was prepared during the hold in a private clone at 02ae8bf (commit 79a0f3b), without touching the main tree.
It was cherry-picked cleanly onto 22ce22d (Task 6) as 807185b.

| Finding | Fix |
|---|---|
| I1: Era effects on hotfix items | `ItemEffectLoader` gives Era fallback effects to `era` items only. A hotfix item with Era effects and no Forever link is counted: `clientdata.era.effectsSkippedOnHotfixItems` (items) and `clientdata.era.effectRowsSkippedOnHotfixItems` (rows). The test is inverted: 16805 shows none, 16806 (Era) keeps them, and the counter reads 1. No mixed footer. |
| I2: STALE forever on a re-upload | Each index entry gains `last_seen_modified` and `last_checked_at`, which move whenever its bytes are served again. The lock's top-level `hotfix` record is always the index's newest snapshot, plus `last_seen_sha256`, `last_seen_modified` and `last_checked_at`. The build watch compares with `last_seen_modified`. When the mirror reverts to an older known snapshot, the lock still names the newest one and fetch warns. Tests cover both: the same bytes with a newer date, after which the watch reads fresh, and a revert. |
| I3: contact to third parties | `fetch.bare_user_agent()` (the pipeline name only) for the Raidbots and WoWDBDefs clients. `build_watch.agent_for` sends the contact to wago.tools only. Tested with `WOWFOREVER_CONTACT` set, through a local server's recorded User-Agents, and with a unit test of `agent_for`. |
| M1: other-layout snapshots | Each snapshot is decoded with its own build's layout and carried over by column name when its columns are a superset of the overlay's. Otherwise it is skipped, listed in `hotfix.json` (`skipped`), warned in convert and reported as `hotfix.snapshotsSkipped` with a note. The tools README states the rule. |
| M2: removals | Status-2 entries are counted (`removed` in `hotfix.json`, `hotfix.itemsparse.removed` in the report) and change nothing. **PM question:** should a removal also undo the record's earlier rows? |
| M3: new stat ids | Recorded in backlog row F3 and in the table below. |
| M4: NOTICE | Lands in the data commit, in both copies (scripted): Raidbots as the transport, the WoWDBDefs credit, the fork's author, version, SHA and GPL-2.0, and Hoizame as the comparison pin. |
| M5 | The schema comment reads `client_source: hotfix | forever | era`. |
| M6 | `stage(problems, "atlasloot.baseline")`. |
| N1 | Imports instead of inline qualified names (`ImportPipeline`, `ClientDataSource`, `HotfixFixture`). |
| N2 | The `ItemCardPanelTest` fixture uses the new Era footer text. |
| N3 | Noted for the PM: the no-data note also appears on Questie ids that exist in neither client. The text is unchanged. |

**New stat ids on revealed items (M3).**

| Stat id | Overlay items | Examples |
|---|---|---|
| 101 | 1 | 13056 Frenzied Striker |
| 125 | 11 | 7683 Bloody Brass Knuckles, 13019 Harpyclaw Short Bow, 273033 Gravespike Repeater |
| 126 | 3 | 5444 Miner's Cape, 9391 The Shoveler, 279836 Thorn Protecting Girdle |
| 129 | 1 | 282778 Mark of the Red Flight |
| 134 | 2 | 2169 Buzzer Blade, 279835 Vine Pruner's Cloak |
| 139 | 1 | 4771 Harvest Cloak |

**Red to green.**
- Python: the 6 new tests failed against the 02ae8bf code and passed with the fix. They cover removals, the earlier
  layout, the re-seen snapshot, the mirror revert, the contact for fetch and the contact for the watch.
- Java: `HotfixOverlayTest.hotfixItemsNeverTakeEraEffectsAndHotfixEffectRowsWin` and
  `.skippedSnapshotsAndRemovalsReachTheReport` failed, then passed.

**Verify at 807185b:** lua-table 61, importer 449, app 897, 0 failures, BUILD SUCCESS. Tools pytest: 122 passed.

**A flake from Task 6.** The first full verify at 807185b failed once, in
`NpcPageTest.columnsEmptyOrUniformForEveryRowAreHidden`: a "Type" column was expected hidden. The test passed alone at
807185b and at 22ce22d, the full suite passed at 22ce22d in the clone, and the full verify at 807185b passed on rerun.
It is an order or timing flake in Task 6's code, not caused by this round, which touches no NPC page code.
