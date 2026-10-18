# Vibemining and first-run data setup

Status: consolidated product/design decision and implementation plan. The complete workflow below is not yet shipped. Existing Java importers and initial asset/runtime services provide parts of the engine; they do not yet implement all source choices, local-client decoding or resumable database creation.

## Product experience

**Vibemining is an area inside the main Vibecraft application**, not another application or an end-user command-line tool. Journal remains focused on reference browsing. Vibemining contains build discovery, sources, download plans, jobs, extraction inspection, provenance/conflicts, and candidate-database validation/activation.

First-run setup uses the same engine. With a compatible installed or bundled runtime database, start Journal directly. Without one, show the source picker and setup progress, then open Journal after a validated database is installed. Choosing Blizzard acquisition links into the Vibemining workspace. No end-user Python, Node, Blender, manually launched importer or terminal commands are part of the intended workflow.

Support both a small application that builds local data and an optional reviewed compact-database bundle. Neither requires a populated database in Git. Existing local data and the active app stay available while a replacement is prepared. Static NPC images take priority; a 3D viewer is outside this scope.

## Sources and acquisition methods

The picker has checkboxes for sources, and separate controls for how selected data is acquired. Every source card shows its capabilities, exact revision/build, required/recommended/optional status, dependencies, omission impact, acquisition availability, license/rights information, and estimated download/disk size. Show measured pinned-manifest or server size estimates; say “not measured yet” when unavailable. Downloads already verified in cache reduce the remaining estimate.

Persist the exact selection. An unchecked source is neither downloaded nor imported from cache. In particular, omitting AtlasLoot also omits its baseline, Continued variants and rate files unless the user explicitly selects them separately. No Era fallback, hidden substitute, or implicit import of everything found in a cache.

Candidate source capabilities:

| Source/capability | Selection behavior | Missing or omitted behavior |
| --- | --- | --- |
| Forever core identity/schema | Required for a Forever catalog; identity must be genuine | Cannot activate a catalog with invented native metadata |
| Questie entities/quests | Recommended; current pipeline structurally requires it until refactored | Explain absent entity/quest coverage; do not fabricate rows |
| AtlasLoot loot/rates | Optional; current mandatory importer dependency must be removed | Loot sections explain that this source was not selected |
| AllTheThings connections | Optional, with quest-detail dependencies shown | Connection coverage unavailable; no inferred prerequisite chains |
| Native items/spells/recipes | Selected pinned client tables; dependency closure visible | Dependent detail panels report missing table coverage |
| Maps/icons/static portraits | Optional selected artwork capabilities | Text/reference browsing continues with honest placeholders |
| Music/UI sounds | Optional separate audio capabilities | Quiet playback; no hidden download |
| Community drop-rate database | Optional separate source | Do not substitute community percentages as native evidence |
| Collector observations | Optional actual local capture | Capture-required status; observations cannot be invented/downloaded |

If a capability needs another selected source/table, show that dependency and require a visible choice. Do not silently add downloads. License information is per selected source and revision: the code's GPL license does not license Blizzard assets, and attribution alone does not establish permission for sources with no declared license. Preserve the necessary notices and provenance.

The current importer requires Questie plus AtlasLoot and runs a broad native-table loader. Making these optional is substantive adapter/model work. A two-table native sample or `--skip-client-data` does not currently create the full supported catalog: `EditionRegistry` requires actual Forever product/build metadata. Use a real pinned native input set or explicitly design a distinct honest community-only context; never fake metadata to bypass the gate.

## Autodetect a local WoW installation first

Before asking the user to Browse, check remembered locations, supported Battle.net installation records, and bounded platform-specific locations: Windows installation records and common program directories, macOS applications, and known Linux Wine/Bottles/Lutris prefixes. Run discovery in the background with cancellation, time/candidate/file-size limits and stale-result guards. Do not recursively crawl disks.

Deduplicate canonical paths and validate typed `.build.info` records, active product, build, locale and expected CASC metadata. Multiple compatible results produce a selection list; one can be preselected; no compatible result offers Browse. Show incompatible Era, Retail or Anniversary installations clearly instead of treating them as Forever. Finding a directory proves neither complete files nor decoder support.

Local WoW files are read-only. Put caches, extracted images, staging files and databases in Vibecraft user storage. Revalidate identity around reads to detect the client being patched. Missing, encrypted or unsupported files are explicit capability gaps. Prefer verified local files when that acquisition method is selected, but never fall back to the network unless the corresponding network source is enabled.

Local installations supply client data; they do not supply Questie/AtlasLoot curated datasets. Pure Java local-CASC index/archive access and raw DB2 decoding remain separate implementation work.

## Direct Blizzard builds/CDN, including beta availability

Offer explicit product/branch, region, available build and locale selection. Capture official discovery metadata, URL, timestamp and content hash, then pin build/CDN configuration, encoding and root identities for the job. Verify each configuration/content key through the acquisition chain. Supported available builds are not a promise that all historical builds can be fetched.

A beta/preload advertised by metadata is not necessarily readable. Show missing, unpublished or encrypted required data as unavailable. An incomplete new build may keep its verified download cache while the current installed catalog remains active. It must not be activated or silently replaced with another build.

Download selected tables/assets rather than the entire game by default. Start with resumption at verified file boundaries; range resumption requires separate identity and Content-Range validation. Direct-Blizzard-only mode excludes implicit Wago transport fallback even when bytes could match. A separately selected table-export service has its own service-reported build/hash provenance and must not be described as CASC-root verification.

## Job engine, resume and activation

One bounded setup worker coordinates immutable plans and reports structured progress:

`Inspect → Plan → Fetch → Normalize → Project → Compact → Validate → Install → Ready`

Additional states: waiting for input, paused, failed and cancelled. Show source/file/byte progress when lengths are known and stage progress otherwise. Retry the failed source or stage without discarding verified independent inputs. Persist job-schema/app version, selected edition/build/locale, source pins and hashes, acquisition permissions, completed checkpoints and reports outside Git. Changes to source selections or build identity create a new plan; never mutate an active job implicitly.

Cancellation propagates through HTTP requests, file loops, parsing and SQL stages. Interrupts alone are insufficient. Use cooperative cancellation and transaction rollback, with JDBC cancellation where supported. Resume at verified file/stage boundaries rather than pretending a partly completed SQL transaction is resumable.

Build into a staging database. Keep raw snapshots in a separate archive/cache store and produce a compact runtime projection that retains the provenance/conflict evidence needed by the UI. Validate integrity, schema, identity, core query behavior, source counts and capability coverage before activation. Close handles and publish the validated candidate atomically; retain the previous database for recovery. Journal must never attach to a half-built candidate.

Adding sources later uses this same staged rebuild/update flow. Coverage states distinguish **not selected**, **download failed**, **selected but missing source row**, **unsupported format**, **encrypted**, and **conflicting evidence**. Source precedence is explicit and reviewable; an alternative value is evidence to inspect, not an excuse to silently combine game editions.

## Reuse, implementation slices and acceptance

Reuse Java `SourceFetcher`, `ArchiveWorkflow` ingestion/projection, existing table loaders and pinned asset-verification code as libraries behind the main app. The application currently uses the importer only in test scope, so production reuse requires a deliberate module/dependency change. No separate end-user importer is implied.

Implementation slices:

1. Source capability/selection model, persisted plans, structured progress and first-run/Vibemining shell.
2. Exact selected community acquisition, optional AtlasLoot refactor, worker cancellation and atomic candidate lifecycle.
3. Direct pinned native table acquisition; satisfy the real loader dependency set and catalog identity before claiming a usable fresh build.
4. Local WoW autodetection plus verified Java CASC/DB2 extraction; optional BLP image conversion and other selected asset capabilities.
5. Resume/recovery, conflict inspection, compact runtime export, add-source rebuilds and cross-platform packaged acceptance.

Each slice needs deterministic synthetic tests; network-format checks and actual local-client acceptance are separate evidence. Test cancellation, invalid input, corrupted cache, unavailable selected sources, no hidden transport fallback, patch races, disk-full failure and recovery without replacing the working database. Do not claim complete first-run setup from a file chooser, metadata discovery alone or an importer CLI demonstration.
