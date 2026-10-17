# One database, several WoW editions: architecture assessment

Research proposal, 17 October 2026. No production schema, imports, application behavior, or acquisition jobs changed by this assessment. Product names below describe requested catalog identities; live download-channel mappings must be verified before acquisition.

## Recommendation

Use one physical SQLite file with explicitly edition-scoped, typed canonical projections, immutable source snapshots, and shared content-addressed asset bytes. Add an immutable game session to the app. A selector can change that session without restarting the JVM. Keep interface choice (Classic interface versus the new interface) separate from game choice.

This is feasible in Java/Swing. Adding a selector alone is insufficient: the current database intentionally has one Forever projection and assumes globally unique entity IDs. Importing another edition into those same tables would overwrite data or attach the wrong loot, maps, tooltips, or provenance.

**User requirement: Forever uses genuine Forever data, including during beta. No implicit Classic Era fallback is allowed.** Missing Forever values remain unknown or retain their genuine Forever knowledge state. Era values appear only through an explicit **Compare Classic** action, clearly labelled with their own edition/build/source. Identical IDs or shared lineage do not authorize copying a Classic value or assertion into Forever. Source facts must carry a verified applicable-edition scope; transparent lineage may support explicit comparison, never silent cross-game promotion.

Start with Forever plus Classic Era as a vertical slice. Enable a game only when its own validated catalog exists. Retail and Anniversary TBC require source adapters and coverage acceptance, not just different selector labels. Do not describe the present Era reference snapshot as a complete second game.

## Evidence in this repository

| Current implementation | Consequence |
| --- | --- |
| `importer/src/main/resources/dev/wowforever/importer/schema.sql`: `item`, `npc`, `zone`, and other canonical tables use a single numeric `id`; item children join only `item_id`. | The same native ID in two editions cannot coexist safely today. Scope roots and every relationship. |
| `app/.../model/EntityRef.java` and `nav/ViewTarget.java` identify targets by kind/ID, with no edition. | Back/Forward, links, saved selection, and loaded-result equality need session context. |
| `db/SearchRepository.java` joins FTS results by kind/ID and collapses same-name hits. | Search and counts must filter the selected projection before ranking, limiting, joining, or collapsing. |
| `foundation/ClientDataset.java` already keys native rows by snapshot/table/row and records product/build/locale. | Preserve this reusable archive foundation. It does not provide edition-scoped canonical pages by itself. |
| `foundation/ForeverDiff.java` explicitly validates an Era/Forever snapshot pair and records missing/partial schema coverage. | Keep this useful comparison behavior; a comparison snapshot is not an imported Classic gameplay catalog. |
| `foundation/FieldKnowledge.java` selects knowledge by `(entity,id,field)`; hotfix workflows use global `meta` pins. | Knowledge selection and hotfix authority must stay within an edition/projection; otherwise another game's facts can win. |
| `db/SystemsRepository.java` selects build prefixes `1.60.%` and `1.15.%`; `ForeverContentRepository.java` checks `wow_classic_beta` and global comparison pins. | Replace implicit current-product discovery with explicit projection inputs and edition capabilities. |
| `db/AssetRepository.java` resolves icon bytes by numeric icon ID; Atlas floors/maps likewise contain native IDs. | Scope asset identity before resolving bytes; identical numeric IDs do not prove identical art. |
| Several catalog repositories cache by the `Database` object alone. | A single database containing multiple games needs projection/locale-aware cache keys. |
| `db/Database.java` serializes reads on one read-only connection; `Repositories.of(db)` builds the global repository set. | Existing worker-thread loading is reusable, but increasing task count will not parallelize queries on this connection. Measure contention before adding a bounded read pool. |
| `db/DatabaseInstaller.java` atomically replaces the complete bundled database by global build ID. | Preserve atomic verified installation. Future optional data-pack updates need composition/reinstallation rules rather than replacement that silently drops other games. |

Paths abbreviated `app/...` mean `app/src/main/java/dev/wowforever/app/`; importer class names live under `importer/src/main/java/dev/wowforever/importer/`.

## Identity model

Separate these concepts rather than making a download product string the game's primary key:

- **Edition:** stable app identity, e.g. `forever`, `classic-era`, `retail`, `anniversary-tbc`. This is what the user chooses.
- **Channel:** verified acquisition mapping, including source provider, product, release/beta/PTR channel, region where relevant, and validity period. A mutable upstream product mapping must not rename historical editions.
- **Snapshot:** immutable source capture with original bytes, product, build, locale, hashes, paths, capture time, and provider version.
- **Projection:** one reproducible canonical catalog, pinned to an edition, locale, transformation version, and exact input snapshots. Different sources can contribute to one projection; their builds need not be identical.
- **Entity identity:** `(edition_id, entity_kind, native_id)`. Its visible fields and relationships belong to a projection. Same numeric ID in another edition is a distinct entity unless an explicit comparison mapping says otherwise.
- **Comparison:** explicit left/right projection or native-snapshot pair with field/schema compatibility coverage. Missing coverage never means “new” or “removed.”

Realm, progression phase, difficulty, and observation provenance can change what loot or availability means inside an edition. Preserve them as source/fact qualifiers instead of silently creating one universal loot truth. They need not all become top-level game-selector choices.

## Preferred schema direction

Prefer typed tables with composite projection keys for a first migration. This keeps current SQL recognizable, supports native IDs in URLs and diagnostics, and allows SQLite foreign keys to reject cross-projection joins. Do not turn all gameplay data into a generic JSON/EAV database: the raw archive already serves that purpose.

Illustrative target, not an executable migration:

```sql
CREATE TABLE game_edition (
  edition_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL
);
CREATE TABLE catalog_projection (
  projection_id TEXT PRIMARY KEY,
  edition_id TEXT NOT NULL REFERENCES game_edition(edition_id),
  locale TEXT NOT NULL,
  transform_version TEXT NOT NULL,
  content_hash TEXT NOT NULL
);
CREATE TABLE projection_input (
  projection_id TEXT NOT NULL REFERENCES catalog_projection(projection_id),
  role TEXT NOT NULL,
  snapshot_id TEXT NOT NULL REFERENCES source_archive_snapshot(id),
  PRIMARY KEY (projection_id, role, snapshot_id)
);
CREATE TABLE catalog_head (
  edition_id TEXT NOT NULL REFERENCES game_edition(edition_id),
  locale TEXT NOT NULL,
  projection_id TEXT NOT NULL REFERENCES catalog_projection(projection_id),
  PRIMARY KEY (edition_id, locale)
);
CREATE TABLE item_v2 (
  projection_id TEXT NOT NULL REFERENCES catalog_projection(projection_id),
  id INTEGER NOT NULL,
  name TEXT,
  -- other typed item columns retained
  PRIMARY KEY (projection_id, id)
);
CREATE TABLE item_stat_v2 (
  projection_id TEXT NOT NULL,
  item_id INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  stat_id INTEGER NOT NULL,
  value INTEGER NOT NULL,
  PRIMARY KEY (projection_id, item_id, ordinal),
  FOREIGN KEY (projection_id,item_id) REFERENCES item_v2(projection_id,id)
);
```

Validate that `catalog_head` points to a projection of its own edition/locale in the writer and preferably through a matching composite foreign key. Scope quests/objectives, NPC/object spawns, boss memberships, loot facts, maps/floors/legends, travel routes, item sets, classification/stat labels, source comparisons, and selected knowledge the same way. Scope active metadata to projection; retain global schema/application metadata separately.

Assertions remain immutable. Give them an explicit edition identity plus exact source snapshot and qualifiers; scope selected knowledge and its selection inputs to the projection. A hotfix's authority applies only to compatible edition/channel/build inputs. Never compare dotted build strings lexicographically or let an unrelated larger build number win. Preserve existing assertion IDs and retractions during migration.

An integer surrogate entity key is a reasonable later optimization if measured composite-index size or relationship complexity warrants it. It adds a pervasive native-ID-to-surrogate mapping today and does not remove the need to scope projection/history/locale. Avoid packing edition bits into native numeric IDs: that creates opaque IDs and risks overflow or accidental decoding.

SQLite table-per-edition prefixes would duplicate every repository/importer and make comparisons harder. Attached databases could help temporary ingestion, but would not satisfy the requested single persistent database. Neither is the recommended catalog design.

## Search and assets

FTS should have an internal unique search-document row ID plus stored projection, entity kind, native ID, and localized name. Join the selected projection's document identity to the typed entity with the same projection key. Filtering only the outer page after a global `LIMIT` can hide valid results. Name collapsing must never cross projection boundaries. Benchmark FTS matching/filtering and name-prefix alternatives on realistic Retail-sized fixtures before choosing one FTS structure; global `bm25` corpus statistics can otherwise change ordering across games.

Store large immutable assets once by actual content hash. Add an asset mapping keyed by projection plus namespace/native ID/variant (and locale for localized art) pointing to that hash. FileDataID, icon ID, map ID, display ID, and model path are separate namespaces. Store model/render dependency manifests, decoder version, render recipe, camera, lighting, and source snapshot so future NPC portraits can be reproduced. Deduplicate exact bytes; do not deduplicate semantic identity solely because numbers or names match.

One SQLite file does not require copying every decoded full-resolution model or every redundant historical projection into it. Establish a measured storage budget and explicit retention policy before full Retail ingestion. Source coverage and missing art remain visible; “all data” needs a per-edition source/feature coverage matrix.

## Java and asynchronous switching

Introduce records conceptually equivalent to `CatalogContext(editionId, projectionId, locale)` and `ScopedTarget(context, viewTarget)`. Build `Repositories.of(db, context)`; repository methods bind the scope internally so callers cannot accidentally omit it. A global mutable `current_game` SQL table or mutable parameter shared by worker tasks is unsafe.

On selection, create a new immutable session and increment its generation. Load its Home/catalog summary off the Swing EDT. Apply a result only if its context and generation still match the active request. Best-effort cancellation improves responsiveness, but stale-result rejection is mandatory even when cancellation cannot interrupt JDBC. Keep the selector, progress state, and window responsive throughout.

History must store game context. Prefer separate per-edition navigation stacks for ordinary switching, with a visible game-labelled destination when an explicit cross-game comparison link opens another edition. Preserve filters, scroll, and map position under `(edition, projection, target)`. Cache parsed content under `(projection_id, locale, catalog kind)` and thumbnails under asset hash plus render variant. Never retain the present DB-only static cache unchanged.

Initial implementation can reuse serialized read-only `Database.read`; query work already happens on workers. Use bounded tasks and short indexed/page queries. Long background scans and eager model decoding must not block interactive queries behind that single connection. If profiling demonstrates contention, introduce a small read-only connection pool with each query's context bound explicitly. Each connection retains its own locking rules; result DTOs remain immutable and contain no live JDBC resources.

Both old and new interfaces should consume the same session/repository contract. Forever-specific features such as Legacy or Camping are capability-gated, not copied into another game's menus. Retail-specific systems need dedicated adapters/pages; common item/NPC/quest/map/instance journeys can share components.

## Incremental migration and acceptance

1. Add the context API in Forever-only mode. Keep defaults explicitly pinned to Forever for current callers; do not change shipped data yet. Convert repositories and caches with tests before enabling a second selector option.
2. Build a candidate database beside the existing one. Preserve all original native IDs, raw snapshots, assertions, history, archived blobs, and source paths. **Do not bulk-label the existing mixed canonical projection as Forever.** Current `client_source='era'` fields and any independently traced Era-derived tooltip/effect/stat fields must be excluded from active Forever values and retained as explicitly Era historical/comparison evidence. The current schema has mixed field provenance, so `client_source` alone is not a complete classifier. Reproject genuine Forever facts from pinned inputs; retain unclassified legacy values in an audit area, not as verified Forever. Existing Era reference snapshots stay marked reference/comparison inputs and do not automatically become a complete Classic catalog.
3. Compare current pages, counts, loot/rates, map relationships, knowledge selections, tooltips, search, and archive hashes against the old database. Require no unexplained archival loss, and produce an explicit field-level report for intended removal of implicit Era fallbacks from Forever views. Unknown Forever values must not be refilled by an Era assertion, tooltip fragment, set bonus, formula input, or asset mapping. Run foreign-key/integrity checks and atomic failure/recovery tests. Both GUI variants must obey the same edition-isolation policy when consuming the new catalog; retaining the old visual interface does not authorize retaining incorrect cross-game fallback behavior. Keep the original database as a rollback artifact; do not force an older binary to open an incompatible schema.
4. Add a deliberately tiny second-edition fixture containing identical item/NPC/map IDs with different names/stats/loot/art. Verify no cross-edition links, cached content, search counts, fallback icons, knowledge selections, or navigation state leaks.
5. Import a separately validated Classic catalog, recording missing source coverage. Enable it only after the complete item → NPC loot → map → quest journey passes. Native Era tables already archived reduce acquisition work but do not replace missing world/loot/quest projections.
6. Research and validate Anniversary TBC and Retail adapters independently, including item-stat/tooltip formulas, map transforms, class/talent systems, encounter difficulty, and source availability. Enable incremental coverage honestly rather than manufacturing parity from Forever fallbacks.
7. Build/rebuild candidate projections offline, validate them, then atomically publish the selected catalog head or complete composed database. Do not run destructive global `DELETE`/`DROP` importer paths against other editions. Keep the running read-only app on its verified generation until installation switches safely.

Required tests include same-native-ID collisions; compound foreign-key rejection; source/hotfix authority isolation; comparison coverage; locale isolation; exact asset-hash reuse with distinct ID mappings; old Forever preservation; migration rollback; installer preserving installed edition packs; Back/Forward across edition switches; rapid A→B→A selection with delayed A/B workers; no SQL/model decode on EDT; bounded memory; and selected-edition cold/warm load/search p95 under a declared realistic dataset and hardware budget. A small mock selector alone cannot substantiate performance or multi-game readiness.

## Decisions to settle before acquisition

Confirm what “WoW Classic” means in the selector (proposed default: Era, separately labelled from Anniversary TBC); verify exact live product/channel/build mappings; define locale and historical-retention scope; inventory legal/technical availability and provenance for world/quest/loot data per edition; and set coverage/storage/performance budgets. These are research outcomes to document, not reasons to block the current new-UI implementation.
