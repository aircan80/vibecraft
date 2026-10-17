Needs fixes — spec ❌, quality ❌ (0 Critical, 3 Important, 6 Minor, 3 Nit)

# Task 19 code review: commit 02ae8bf (hotfix overlay, build watch, AtlasLoot zip pin)

Scope: everything that does not need the AtlasLoot Classic Forever zip. The fork swap, the 10-rate spot-check, the
regeneration and the shipped-data tests come with the data commit and get their own scoped re-review. The repository
was read at 02ae8bf only (git show). The snapshot and the converted CSVs were parsed read-only with the venv python
`-I` and scripts kept in the scratchpad. One read-only fetch of the WoWDBDefs `manifest.json` settled the table hashes.

## What holds (verified)

- **DBCache.bin format.** The header is 44 bytes: magic `XFTH`, version 9, build 70450 and a 32-byte hash. Each
  entry is 32 bytes: magic, region, push, unique id, table hash, record id, size, status and 3 padding bytes. A
  separate parser of mine walked the 3,380,951-byte snapshot to the exact end. Every padding triple was zero.
- **Statuses.** The reader uses the same values as DBCD: 1 valid, 2 record removed, 3 invalid, 4 not public. The
  snapshot holds 4,872 ItemSparse and 4,872 Item entries with status 1. It holds 38 of each with status 3.
- **Table hashes.** SStrHash matches the WoWDBDefs manifest for ItemSparse `919BE54E`, Item `50238EC2`,
  ItemEffect `4002A5B1`, ItemXItemEffect `00CB674F` and ItemSearchName `198E28B5`.
- **Layouts.** The definitions are pinned to a commit and their hashes are kept in the lock. The 1.60.1.70450
  ItemEffect layout is the non-inline id plus nine fields, 24 bytes per record. The decoder refuses any record whose
  size does not fit its layout. The overlay header must equal the base CSV header. The implementer's byte-identity
  check against wago's 70450 CSVs is strong evidence that the decoding is right.
- **Overlay rules.** Only status-1 rows apply. An invalidation removes only the row of its own push, after which an
  older push applies again. Snapshots accumulate oldest first, and the newest snapshot, then the later entry, wins.
  Precedence is hotfix, then Forever, then Era, both for ItemSparse and for the Item row.
- **Build mismatch.** It fails in `convert.py` with `check_build`. It also fails at import in
  `ClientDataSource.checkHotfix`, on both the `hotfix.json` header and the lock header. The text names
  `tools/build_watch.py`, and fixtures test both places.
- **Counters.** All of these exist with the memo's names: `hotfix.itemsparse.valid`, `.applied`, `.invalidated`,
  `.notInItemTable`, `hotfix.fromEra` and `hotfix.fromNoClient`. The scratch figures 3,173 and 626 equal the memo's.
- **build.lock.** It records the header build, SHA-256, Last-Modified, fetch time, size, every snapshot and the
  WoWDBDefs commit with each definition's hash.
- **Snapshots.** They are stored by SHA-256. `convert.py` reads them only by SHA from the cache and re-hashes each one,
  so nothing is ever re-read by URL. Nothing downloaded is executed: the BPSV, JSON, struct and dbd data are parsed only.
- **Retries.** The shared `Client` retries 5xx answers three times with throttling. A network failure in the watch
  reads "unknown".
- **build_watch.py.** It covers the four upstreams plus AtlasLoot and prints one line each. Stale exits 1, and an
  unreachable upstream reads "unknown". The fork reads "manual check" with its pin, and the script never writes. Exit
  2 for "unknown, nothing stale" (open choice 6) is a sound extension, and the README documents it.
- **Footers.** The hotfix and Era footers are exactly the memo's texts. Tests assert that the footer contains neither
  "live" nor "confirmed". The no-data note is the memo's text and lives in the item header (open choice 7: accepted).
- **App.** The changes are a header label set in the page constructor plus label strings. There are no new threads,
  no I/O and no EDT-only calls, so the headless and EDT rules hold.
- **Zip pin plumbing.** The SHA-256 is checked before extraction, only pinned entries are extracted, and zip-slip is
  refused.
- **Tests.** The Python fixtures cover valid, removed, not-public, invalidated, accumulation, a skipped layout, the
  build mismatch, a changed definition, a stale overlay, a non-cache body and the fetch lock. The Java fixtures cover
  precedence, the counters, the build mismatch at the lock and at the stage, a stale snapshot, an edited CSV, an
  orphan CSV and the footers per source.

## Open choices: rulings

1. **Era effects on hotfix items: wrong, see I1.** It is not only a labelling question. Forever moved the Era Equip
   bonuses (hit, crit, attack power, spell power, haste, slaying) into ItemSparse stat ids. Era Equip lines on a
   hotfix item therefore repeat bonuses the hotfix stats already show.
2. **ItemEffect overlay: the parsing is right and the table is absent.** The WoWDBDefs manifest confirms `4002A5B1`
   for ItemEffect and `00CB674F` for ItemXItemEffect, and the snapshot has neither. The review's `DF2F53CF` is
   **TactKey**. Its 3,584 entries are 3,570 zero-length "not public" rows and 14 valid 16-byte key rows, which cannot
   be 24-byte ItemEffect records. The review's `5EE3B0B0` is in no manifest entry and not in the cache. The
   implementer's correction stands. A header-only `ItemEffect.hotfix.csv` is the honest output.
3. **Spell rows not read: accepted.** Revealed items keep Forever's spell texts.
4. **One footer date for every hotfix row: accepted.** The footer names the cache the overlay is current to. Revisit
   once a second snapshot exists (see M1).
5. **Unknown stat ids: they render safely but are not recorded, see M3.**
6. **Exit 2: accepted.**
7. **The no-data note in the header: accepted.**

## Important

**I1. Era Equip effects on hotfix items double-count their bonuses.**
- **Where:** `importer/src/main/java/dev/wowforever/importer/clientdata/ItemEffectLoader.java:95`, and the test that
  locks the behaviour in at `importer/src/test/java/dev/wowforever/importer/clientdata/HotfixOverlayTest.java:125-133`.
- **What is wrong:** A hotfix item without a Forever `ItemXItemEffect` link takes Era's `ItemEffect.era` rows. The
  converted data shows this is systematic. 803 overlay items take Era effects, 87 of them browser items. Every one of
  those Era effects is trigger 1 (Equip), 1,448 rows in all, and each one restates a bonus that the hotfix row carries
  as a stat:

  | Item | Hotfix stat ids | Era Equip effects |
  |---|---|---|
  | 227807 Dense Timbermaw Belt | 7, 38 (Attack Power) | Attack Power 64 |
  | 220854 Knight's Leather Armor | 7, 50, 32 (Crit), 31 (Hit), 38 (AP) | Increased Critical 1, Increased Hit Chance 01, Attack Power 18 |
  | 240141 Heretic Cowl | 7, 5, 6, 31, 32, 36 (Haste), 45 (Spell Power) | Hit 02, Critical 1, Spell Haste, Increase Spell Dam 62 |
  | 13044 Demonslayer | 127 (slaying), 38 | Demon Slaying 99 |
  | 13056 Frenzied Striker | 14 (Parry), 31, 101 | Increased Parry 1, Increased Hit Chance 02 |

- **Why it matters:** After the regeneration these tooltips would show each bonus twice: the Forever value as a stat
  line and the Era value as an Equip line, often with different amounts. All of it would sit under "Forever stats
  revealed by a Blizzard hotfix". That is wrong data under a provenance label that vouches for it. It breaks the rule
  that estimates are never presented as facts.
- **The fix:**
  - Use the Era effect fallback for `era` items only. A hotfix row is a Forever row, so its effects come from the hotfix
    overlay or Forever's links, never from Era.
  - Count the skipped Era rows, for example `clientdata.era.effectsSkippedOnHotfixItems`, with the 87 recorded in the
    report.
  - Invert the test: 16805 shows no Era effects and the counter reads 1.
  - With that change the item's footer is true as written. No mixed-provenance footer is needed.
  - If the PM later wants Era Use (trigger 0) lines on hotfix items, that needs a separate ruling and its own footer
    line. Today the set contains none.

**I2. The build watch reports Raidbots STALE forever when the mirror re-uploads identical bytes.**
- **Where:** `tools/clientdata/clientdata/fetch.py:290` together with `tools/build_watch.py:168`.
- **What is wrong:**
  - A fetch whose SHA is already in the index returns the first record, with the first Last-Modified. The lock then
    keeps that old date.
  - If Raidbots re-uploads unchanged content, the object's Last-Modified moves forward. The watch then compares it
    with the old snapshot date and prints STALE with exit 1.
  - Re-running `fetch.py --hotfix-cache` cannot clear it, because the SHA is known and the lock is unchanged.
- **The same path has a second defect.** A re-fetched known snapshot that is not the newest in the index becomes the
  lock's top-level `hotfix` record, which happens when the mirror reverts. But `convert.py` still orders snapshots by
  index, so the newest one wins. The lock's SHA and date then disagree with the rows the overlay actually prefers.
- **Why it matters:** A build watch that cries stale on nothing trains the owner to ignore it. The memo relies on it to
  detect a moved or changed mirror.
- **The fix:**
  - Record the latest Last-Modified seen for a known SHA, for example `last_seen_modified` and `last_checked_at` on the
    index entry and in the lock's top-level record. The watch compares against that value.
  - Keep the lock's top-level record as the index's newest snapshot. Either move a re-seen SHA to the end of the index
    or record it separately as `last_fetched`.
  - Add a fetch test: the same bytes with a newer Last-Modified, then the watch reads fresh.

**I3. The owner's contact goes to third-party hosts in the User-Agent.**
- **Where:**
  - `tools/build_watch.py:60-67` builds `Client(host, user_agent(contact))` for every host.
  - `tools/clientdata/clientdata/fetch.py:439` and `:441` reuse the wago client's User-Agent for the Raidbots and
    GitHub clients.
- **What is wrong:** `user_agent()` appends `--user-agent` or `WOWFOREVER_CONTACT`. The README tells the owner to set
  that variable before fetching from wago. With it set, the contact also goes to storage.googleapis.com,
  us.version.battle.net, api.github.com and raw.githubusercontent.com. The variable is not set on this machine today.
- **Why it matters:** The brief requires the plain pipeline User-Agent only. This task already had one incident of the
  owner's email reaching CurseForge in a User-Agent. The contact suffix is a courtesy to wago.tools, not something
  other hosts need.
- **The fix:**
  - Send `USER_AGENT_PREFIX` alone to every host except the wago or wow.tools base URL, both in the watch and in the
    hotfix and dbd clients.
  - Drop the watch's `--user-agent` option, or limit it to the wago line.
  - Add a test that sets `WOWFOREVER_CONTACT` and asserts that the Raidbots and dbd requests carry the bare prefix.
    The existing `_Server` already records User-Agents.

## Minor

**M1. A snapshot of another layout drops its rows from the import's view without a word.**
- **Where:** `tools/clientdata/clientdata/convert.py:146-151`. Also `HotfixInfo.read` at
  `importer/src/main/java/dev/wowforever/importer/clientdata/HotfixInfo.java:103` ignores `snapshots_skipped`.
- **What is wrong:** When ItemSparse's layout changes, for example at the 4 Nov `wowf` launch build, every older
  snapshot is skipped. Its revealed rows vanish from the overlay. The only trace is a number in `hotfix.json` that
  the import report never shows.
- **Why it matters:** The memo promises that revealed rows are never lost. A layout change is a likely event during
  this release window.
- **The fix:** Surface `hotfix.snapshotsSkipped` as an import-report counter, and fail or warn when it is above 0. The
  better fix is to decode each snapshot with its own layout and carry the rows over by column name when the overlay's
  columns are a subset. Note the rule in the tools README.

**M2. Status 2 (record removed) is ignored and not counted.**
- **Where:** `tools/clientdata/clientdata/hotfix.py:124-130`.
- **What is wrong:** A removal leaves an older push's valid row in force, and nothing records that it happened.
  Today's snapshot has no ItemSparse or Item removals.
- **The fix:** Count removals per table in `TableOverlay` and `hotfix.json`, and carry them into the report. Ask the
  PM whether a removal should also undo the record's earlier rows.

**M3. The new stat ids render as "Stat #n" and are not recorded for the stat-id mapping.**
- **Where:** `StatNames.java:89` renders them, and they are counted as `clientdata.stats.unknownId` with a note.
  Nothing crashes.
- **What is wrong:** The ids are not written down anywhere the mapping task will find them. They are on revealed items
  in the overlay:

  | Stat id | Overlay items | Examples |
  |---|---|---|
  | 101 | 1 | 13056 Frenzied Striker |
  | 125 | 11 | 7683 Bloody Brass Knuckles, 13019 Harpyclaw Short Bow, 273033 Gravespike Repeater |
  | 126 | 3 | 5444 Miner's Cape, 9391 The Shoveler, 279836 Thorn Protecting Girdle |
  | 129 | 1 | 282778 Mark of the Red Flight |
  | 134 | 2 | 2169 Buzzer Blade, 279835 Vine Pruner's Cloak |
  | 139 | 1 | 4771 Harvest Cloak |

- **Why it matters:** Backlog F3's acceptance check is "No Stat #n in the shipped DB". After the regeneration about 20
  more tooltip lines will carry "Stat #n" under a "revealed" footer. Ids 125 to 139 look like the situational bonuses
  Era expressed as Equip spells, such as attack power against a creature type. Backlog row 5 already points there.
- **The fix:** Record the six ids with these examples in the Task 19 report and in backlog row F3.

**M4. NOTICE has no Raidbots line at 02ae8bf.**
- **What is wrong:** `NOTICE.md` and the app copy do not mention Raidbots. This line does not depend on the fork.
- **The fix:** It must land in the data commit together with the first shipped hotfix row, independently of the zip.
  The data re-review gates on it.

**M5. The schema comment still lists `client_source: forever | era`.**
- **Where:** `importer/src/main/resources/dev/wowforever/importer/schema.sql:23`.
- **The fix:** Add `hotfix`, since that comment is the column's contract.

**M6. Problems in the AtlasLoot baseline are discarded.**
- **Where:** `importer/src/main/java/dev/wowforever/importer/ImportPipeline.java:124` passes `new ProblemCollector()`.
- **What is wrong:** A parse problem in the Hoizame baseline vanishes. The removed-row and kept-row counters would then
  silently undercount.
- **The fix:** Use `stage(problems, "atlasloot.baseline")`.

## Nit

- **N1. Inline fully qualified names.** They appear at `ImportPipeline.java:166` (`ServerDropRates.SOURCE`) and in
  `ClientDataSource.java:151`, which spells out `HexFormat` and `MessageDigest`. Import them.
- **N2. A fixture with the old Era text.** `app/src/test/java/dev/wowforever/app/ui/widgets/ItemCardPanelTest.java:116`
  still uses "Stats from Classic Era data". It is only fixture text, but update it to the new footer so a search for
  the old wording comes up empty.
- **N3. The no-data note reaches more than hidden items.** `ItemPage.java:93` shows "Stats hidden until this item
  drops on a realm" for every item without a card. That includes Questie ids that exist in neither client, not only
  hidden ones. The text is the PM's, so this is noted for the PM rather than to be changed here.
