# Versioned source data and offline projection

Status: approved direction. The initial delivery contract below is narrower than the complete target; acceptance must state which parts are implemented.

## Purpose and boundaries

The importer will preserve acquired source evidence before applying application rules. A separate, offline process will derive the ordinary application tables from explicitly selected evidence. The source layer and ordinary tables belong to **one SQLite database**, as requested. The main application will expose source attribution and source URLs; a general source-data explorer is deferred.

“All source data” means the complete acquired artifacts and their available fields for the configured inputs. It does not mean fetching every upstream repository, every client asset, or every supported game build. Capturing additional upstream inputs is an explicit acquisition change. Unknown fields and unsupported syntax must survive in the original artifact even when structured extraction is incomplete.

This separation helps distinguish missing upstream data, parser omissions, and projection decisions. It does not turn absent evidence into a fact or make an older game edition authoritative for Forever.

## Processing contract

1. **Acquire and capture:** pin the input version, retain its exact bytes, record its origin URL and checksum, and extract source records without applying cross-source precedence or application corrections.
2. **Select evidence:** choose a complete, explicit set of immutable source snapshots. Missing optional providers are explicit, rather than implicitly reusing stale values.
3. **Project offline:** replay parsing and versioned transformation rules from retained evidence. Populate normal tables, provenance, and search indexes without network requests.
4. **Validate and publish:** produce a new database at a temporary path, validate it, and atomically replace the published file. The existing application database remains usable if any stage fails.

The two stages may share one executable and one convenience command, but capture and projection must also be independently callable. Changing a projection rule must not require downloading the evidence again. A deliberate parser upgrade may replay the original artifact into a new extraction generation; it must not rewrite the previous extraction silently.

## Initial delivery interoperability contract

The first implementation uses the static JDBC `SourceArchive` API: `initialize`, `capture`, `snapshots`, `snapshot`, `artifacts`, `diff`, and `extract`. Its data contracts are `Snapshot(source, version, product, build, locale)`, `Artifact(path, url, byte[])`, and `StoredSnapshot(id, metadata, capturedAt)`. Storage tables are `source_archive_snapshot`, `source_archive_blob`, and `source_archive_file`. Content-addressed blobs retain exact file bytes; snapshot/file associations retain paths and URLs.

The tooling exposes separate `ingest` and `project` operations. Projection replays archived files into a candidate database and publishes only a successful candidate. That candidate must preserve the archived snapshots and history in the same SQLite file. The existing default acquisition path may remain a convenience wrapper, but it must use the same evidence boundary.

The initial comparison is an artifact/file-history diff. Initial app presentation is a read-only **Data sources** view with honest dataset-level provenance and recorded URLs. Structured per-entity source tables, entity/field diffs, detailed fact lineage, and a source-data explorer are extensions; neither file retention nor dataset attribution may be described as completing those extensions. Original bytes preserve unknown fields now, while fully queryable structured coverage requires later parser work.

### Structured record delivery

The `archive.records.SourceRecords` package adds `source_record` and `source_record_parse`. Both include an explicit extraction generation independently of the immutable snapshot identity. Generic CSV extraction retains all ordered headers and parsed cell strings, including duplicate native IDs. Generic Lua extraction stores typed syntax-tree JSON for table entries, locals, functions, and other statements without evaluating or applying entity-field schemas; embedded Questie return tables are extracted too. Unknown nested keys survive. Unsupported/skipped syntax and malformed inputs have explicit coverage statuses; original artifacts remain authoritative for lexical details and failed parsing.

Record-level comparisons report additions and changes. Missing records are marked unresolved unless the replacement artifact has a complete extraction, preventing partial parses from claiming deletion. Lua syntax-record identities include statement ordinals; inserting earlier statements can churn identities. These are inspectable syntax records, not a claim of stable cross-version game-entity identity. Field-level semantic comparisons and fact lineage remain follow-up work.

## Immutable evidence and identity

Each snapshot identifies its provider, version or commit, game product/build where relevant, locale, retrieval time, artifact hashes, extraction version, and completeness. Local-directory inputs require content hashes; a nominal upstream commit alone does not identify locally modified files.

Each retained artifact has its original relative path, original URL when available, media/encoding information, exact bytes, and content hash. Archive bytes and selected extracted entries have distinct identities. Original binary hotfixes and original text inputs are evidence, while converted CSVs and decoded images are derived artifacts with their own tool version and parent-artifact references.

Each structured source record belongs to a snapshot and extraction generation. Its provider-native identity and complete supported payload are retained before corrections. Positional Lua keys, nested structures, unknown fields, and absent versus explicitly empty values must not be flattened into an ambiguous string. The artifact remains the fallback for constructs the parser cannot represent.

A source parse must report completeness and errors. Unsupported fields are never counted as successfully captured structured facts merely because the original file exists. Exact artifact retention and structured coverage are reported separately.

## Projection and lineage

A projection run records its explicit snapshot/extraction inputs, transformation version, schema version, success/failure, validation summary, and output identifier. Run timestamps identify executions; content hashes identify evidence and comparable outputs.

Projection rules choose the application value while preserving alternatives. Rules include Questie correction ordering, hotfix/Forever/Era precedence, loot-source reconciliation, relationship interpretation, and exclusions. Manual fixes are versioned correction evidence rather than edits to raw or generated tables.

Application facts must be explainable through their provider, selected snapshot, record or artifact location, and transformation rule. Entity-level attribution is sufficient only when it correctly describes all displayed facts; mixed-source fields and relationships need more specific lineage. Derived facts may cite several inputs. Runtime source links use recorded valid provider URLs and open only on user action; loading a page remains offline.

Game product/build, locale, observation status, and relation semantics remain part of the evidence. A prerequisite, breadcrumb, chain continuation, and mutually exclusive group are different relationships. An observed item presence is not an estimated drop percentage. Older-version fallback values retain their fallback labels.

## Retention and comparison

Capturing a new version appends evidence; it does not overwrite the prior snapshot. Identical artifact bytes may be deduplicated by checksum while preserving each snapshot's references. The initial implementation retains all captured snapshots; pruning is a later explicit operation with referential checks and a documented policy. Projection runs must never reference evidence that has been pruned.

A snapshot comparison reports added, removed, changed, and unchanged native records, with field-level changes where the payload supports them. Removal is scoped to complete comparable inputs: a partial extraction cannot prove that a fact was removed upstream. Cross-build or cross-locale comparisons label the changed scope. Conflicting sources remain separate evidence and do not cancel each other out.

Source tables are not included in the application's ordinary search index. The published database retains captured history, artifacts, current app tables, and provenance. Size and build-time measurements must be reported before release; do not silently switch to a separate source database to reduce package size.

## Migration and delivery

Start with the existing bundled database and preserve its ordinary-table behavior. Existing records without retained historical evidence are explicitly identified as legacy/untraceable; do not fabricate source snapshots from the final merged values. A fresh import captures actual pinned inputs and projects a fully traceable replacement.

Implement in verifiable slices:

1. Add the evidence/run schema, artifact capture, immutable snapshot identity, and compatibility with existing databases.
2. Separate replayable offline projection from acquisition, preserving current application tables and existing precedence rules.
3. Add structured source retention and comparison for the acquired providers, beginning with Questie quests and correction layers, then covering the remaining configured inputs. Artifact retention alone does not complete this slice.
4. Populate lineage and expose useful provider names and source URLs in the main application. Keep explorer controls and general-purpose source browsing out of this change.
5. Rebuild from actual retained inputs, compare ordinary tables and known-data gates, document the package-size change, and publish only when validation passes.

## Acceptance criteria for the initial delivery

- The same single SQLite file contains immutable evidence and the derived app tables.
- Two captures of identical inputs have stable content identity; changed bytes at the same upstream version cannot be mistaken for the original capture.
- A later snapshot leaves earlier artifact bytes and source records unchanged.
- The complete original acquired artifact survives unsupported fields or parser failures; reports distinguish archived-file coverage from supported structured extraction.
- Questie original file bytes survive correction application; phases and fields unused by the UI remain recoverable from those bytes.
- Offline projection works with networking disabled and does not consult mutable external caches.
- Reprojecting a pinned evidence set with the same rules yields equivalent ordinary rows, relationships, labels, and search results, excluding execution metadata.
- Different rules can be replayed against the same evidence without acquisition.
- A failed capture, failed projection, or failed validation leaves the previous published database usable.
- Missing optional inputs and partial snapshots cannot silently inherit stale facts or produce false removal diffs.
- Existing app behavior and supported older database loading remain covered by regression checks.
- Archived Questie inputs can be extracted and inspected for the Sleeping Bag investigation without fetching again; automatic per-quest comparison and diagnostic lineage remain follow-up work.
- Dataset-level provenance and URLs match actual archived evidence; existing mixed-source and fallback labels remain truthful. The UI does not imply field-level lineage where it has none. External pages open only when clicked.
- File-level snapshot diffs correctly identify additions, removals, and changes for complete comparable inputs; entity/field diffs are explicitly deferred.
- Release notes report retained snapshot coverage, remaining structured-data limitations, database size, and build duration.

## Follow-up acceptance for the complete target

Structured source records must retain all supported fields before corrections. Entity/field comparisons must handle partial extraction and scope changes honestly. Fact-level lineage must explain accepted, rejected, mixed-source, and derived values through evidence and versioned rules. These requirements complete the original target beyond the initial artifact archive and dataset attribution; the explorer remains a separate UI feature.

## Operational constraints

Preserve attribution and review redistribution terms before bundling additional original artifacts. Inputs already fetched for development are not automatically approved for unrestricted redistribution. Record the actual source locations rather than claiming a mirror is the original publisher.

The desktop application does not mutate source records or run acquisition while browsing. Data refresh remains a deliberate tooling operation. Collector observations retain their own evidence semantics and do not overwrite existing rates or claim verified boss attribution without evidence.
