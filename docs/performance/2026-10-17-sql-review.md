# SQLite and table-load review — 2026-10-17

Independent review requested by the user. This is a review of the current working tree, including the in-progress JSON decoder and item-catalog optimizations, rather than an audit of the previously running JAR. No production database or application code was changed by this reviewer.

## What explains the delay

SQLite is local, but several pages reconstruct large application models from raw archival JSON before showing a comparatively small screen. The shared `Database.read` monitor serializes queries **and Java mapping/JSON decoding**. A heavy catalog load therefore delays unrelated later loads even when they run on worker threads. Async prevents Swing event-thread blocking; it does not make an excessive query or a queue of obsolete loads disappear.

The relevant candidate has 544,296 archived native rows, 130,755 Forever/Era diff rows, 32,136 canonical items, 25,033 item stats, 13,392 NPCs and 5,232 quests. Combined ItemSparse payloads alone occupy 130,440,121 text bytes. Those archives should remain lossless, but they should not be the interactive page model.

## Measurements and limitations

Measurements below use Python's SQLite read-only connection against `.local/v041-integration/candidate.sqlite`, fetching the entire result. They measure query execution and result transfer, **not Java decoding, Swing rendering, or first-open disk-cold performance**. The OS cache was already warm; “first” means the first execution in this process. JVM measurements supplied by the implementing agents are listed separately. Index experiments used `.local/sql-review/index-experiment.sqlite`, containing copied tables only. Raw scripts/results are in `.local/sql-review/` and are intentionally ignored local evidence.

| Current operation | Rows returned | First / subsequent execution |
|---|---:|---:|
| Actual `ForeverContentRepository.loadContext` SQL, including native name joins and sort | 130,755 | 746 / 746–752 ms |
| Same context query restricted to existing Map ID 0 | 1 | 8.73 / 0.01–0.06 ms |
| `SystemsRepository.readTables`, Forever snapshot | 69,737 | 115 / 119–124 ms |
| Same systems result with finite table-name IN guard | 69,737 | 88 / 87–89 ms |
| Native Forever ItemSparse payload read | 19,257 | 48 / 35–45 ms |
| `NativeItemMetadataRepository` ItemSet membership scan | 0–few | 1.4–1.6 ms |
| Native ItemXItemEffect scan for item 869 | 1 | 4.4–4.6 ms |

These explain why a JDBC query may appear cheap while its complete repository call is expensive: wide JSON payloads must subsequently become many maps, strings and domain objects.

## Prioritized changes

### P1: Stop loading the entire diff catalog for one zone or instance

`ForeverContentRepository.loadContext` reads and names all 130,755 diffs. `instanceContext` and `zoneContext` call `context()` and filter the whole result in Java. `hub()` also loads this entire context. Its EXPLAIN plan is a full `forever_diff` index scan, two native PK lookups per result, canonical joins and a temporary sort B-tree. This remains a meaningful cold-load problem after the decoder fix.

For an instance, query the active pair with `kind='Map' AND id=?` directly. For a zone, obtain descendant IDs and query `kind='AreaTable' AND id IN (...)` in bounded batches. The current diff primary key already supports these predicates: EXPLAIN shows `SEARCH ... (kind=? AND id=? AND era_snapshot_id=? AND forever_snapshot_id=?)`. The full hub should fetch aggregate counts and its first visible page; its change browser should filter, search and paginate in SQL rather than load everything and show 40 rows.

Preserve active snapshot IDs and build provenance in every result. Retain raw diff evidence, absent-table coverage and explicit Era comparison semantics.

### P1: Discard obsolete queued page work before it reads the database

`AppController.showCatalog` and `showJournalCatalog` check generation only in the completion callback. `Async.run` always executes `work.get()`. Consequently rapid navigation can queue multiple expensive loads that will never be shown, and they still acquire the shared database monitor.

Use a safely published request token checked before work, and again after acquiring the database read lease when necessary. Coalesce repeated requests for the same immutable catalog. A callback generation guard is still needed. Do not assume `Future.cancel(true)` safely interrupts JDBC or closes an active shared connection. This is a scheduler improvement, not a reason to weaken source handling.

### P2: Bound systems scans and divide the catalog by actual use

`SystemsRepository.readTables` currently uses `snapshot_id=? AND (table_name LIKE 'Trait%' OR table_name IN (...) OR ...)`. EXPLAIN uses only the snapshot prefix, traversing all 309,722 Forever rows to return 69,737. Adding a finite `table_name IN (...)` guard (Trait names discovered from the manifest plus the explicit names and Spell) changes the plan to `SEARCH ... (snapshot_id=? AND table_name=?)`, with the same result count, and reduced measured SQL/fetch from 119–124 to 87–89 ms.

A larger gain requires less data: load one class/tree and referenced spell IDs; fetch Legacy and class-spell catalogs separately. All SpellName and SkillLineAbility rows should not be prerequisites for a single talent screen. Replace integer-cast row-ID membership with exact canonical text ID lookups where identity formatting has been validated. The current shared immutable cache and post-lock cache recheck already prevent repeated completed catalog scans and duplicate concurrent cold builds; keep them.

### P2: Build small native UI projections at ingestion

`ItemContentRepository.appearance` still loads all ItemModifiedAppearance rows for each previously unseen item, scans them twice, then performs one name query per related item. Build indexes/maps once per DB snapshot, or preferably project `(snapshot_id,item_id,appearance_id)` and query both directions. Batch name reads with an IN list. `NativeItemMetadataRepository` similarly rescans 12,643 ItemXItemEffect JSON records and 536 ItemSet records per item. Its present ~6 ms SQL-only overhead is modest but avoidable across many item openings.

Suitable purpose-built projections include native item-to-effect, item-to-set membership, spell effects/references, item required skill and appearance membership. Keys must include source snapshot/product/build, and raw source path/line must remain reachable. They are acceleration tables over **Forever** evidence, not a substitution of Era values. Unknown/null/empty/zero must remain distinct.

### P3: A small, justified stat index

The current `item_stat(item_id)` index serves item tooltips but cannot seek by stat ID. Proposed importer-built index:

```sql
CREATE INDEX idx_item_stat_stat_item_value
ON item_stat(stat_id, item_id, value);
```

Copied-table experiment: rare stat 125 (11 rows) improved median 0.4814 ms to 0.0069 ms, changed SCAN to covering-index SEARCH, and added 364,544 bytes. Common stat 7 (6,642 rows) improved only 2.47 to 2.11 ms because transferring the rows dominates. This is useful supporting work, **not the cure for multi-second page loads**. Add during candidate build, never by mutating the running read-only database.

## Indexes to avoid adding blindly

- `client_table_row` already has the correct `(snapshot_id, table_name, row_id)` primary key. Exact-ID reads seek efficiently. Another identical index adds size without benefit.
- `forever_diff` already supports exact kind/ID/build-pair lookup. An experimental `(era_snapshot_id,forever_snapshot_id,kind,id)` index added 21,233,664 bytes and did not improve the exact-row lookup or full sorted catalog. Reduce requested rows first.
- An experimental masked-name expression index added 946,176 bytes but the actual TEMP view lookup still chose SCAN + temporary ORDER BY. SQLite expression matching through this CASE-based TEMP view is not a safe assumption; require an EXPLAIN-confirmed query shape before adopting such an index. On 32k items the measured simple first-100 sort was ~3 ms, so it is low priority anyway.
- Existing NPC-zone, quest-zone, zone-parent, drop reverse lookup, quest objective/giver, craft and tooltip indexes cover the principal entity paths. No evidence that these small canonical tables are the present seconds-long bottleneck.

## Other inspected paths

- `SearchRepository` uses FTS5, bounded output and source-valid item checks. It still materializes and joins all FTS matches before its final LIMIT, and performs a separate full grouped count. Benchmark broad one-letter prefixes before changing ranking; debounce and suppress stale searches first. Do not move LIMIT before ranking without proving the same exact-match/kind ordering and duplicate collapsing.
- `ItemRepository.list(ItemFilter)` explicitly requests unlimited results. NPC/quest filtered list paths similarly allow broad lists. Existing per-page master-list fetch is the better model; avoid building every entity solely to populate the visible first page. Paginate large lists and preserve selected filters/history. Source-count correlated queries are already computed **after** item page limiting, which is good.
- `SourceDifferenceRepository` has per-field current-value queries and per-addon-fact canonical checks. Batch these if profiling shows a problem. Its current tiny per-item groups do not justify doing this before the measured large catalog scans.
- `WorldMapRepository` caches its small catalog; per-zone count queries are an N+1 pattern but run on a small bounded set and existing indexes. `TravelMapRepository` destination reads and `InstanceMapRepository` per-floor legend reads are also bounded. No measured evidence that they explain current stalls.
- `AssetRepository` reads blobs only by exact key; callers should retain decoded-image caches and perform loads off the event thread. Archival blobs should never enter ordinary catalog SELECTs.

## Work already fixed in this development pass

Do not report these as unresolved recommendations:

- `ClientTableRows` now reads one payload row and decodes it once with Gson, replacing a JSON-cell-expanded JDBC stream and wide temporary sort.
- `SystemsRepository` uses the shared decoder and rechecks its weak DB-scoped immutable cache after acquiring the database lock.
- Item catalogs now select relevant IDs/columns before decoding, rather than decoding all ItemSparse/SpellEffect rows for every screen. Implementing-agent before/after timings: sets 4,131→10 ms; trinkets 9,280→216 ms; XP food 6,414→208 ms; comprehension 2,797→85 ms; recipes 5,985→415 ms. Result snapshots matched. Reader ItemSparse 3,213→389 ms; full systems catalog 1,457→550 ms for the full 119,069-row two-build result. These Java timings are separate from this review's SQLite-only measurements.

## Validation required for follow-up changes

Keep both original and optimized result fingerprints, including ordered IDs, all source/version metadata, null/empty/zero values and knowledge states. Measure real cold first navigation, warm navigation, rapid navigation away from a cold catalog and concurrent unrelated search. Record lock wait, SQL execution, payload bytes/rows, decoding and EDT render times separately. Keep game/source identity in any future cache key. A connection pool is a later option for independent reads; it will not fix broad scans and increases memory and TEMP-view initialization complexity.
