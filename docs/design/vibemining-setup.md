# Vibecraft first-run data setup and source management

Status: the current development source binds the Java wizard and Sources & Updates to the production preparation/export/activation service. Real backend preparation and managed activation have passed acceptance. The complete native GUI build journey is still being checked; no GUI completion claim or public release is implied. Older portable preview archives remain unchanged.

## Current user flow

**Vibecraft owns setup inside the main app.** Without a selected compatible database, it opens Welcome → Games → Sources → Game files → Review. Existing installed/bundled libraries still open Journal directly, and first-run setup retains an existing-database escape. Settings → Sources & Updates uses the same source preferences and engine.

The supported bootstrap is exactly **WoW Forever beta 1.60.1.70450 / enUS**. Select QuestieDB, Blizzard native data, explicit Wago exports and the separately selected Raidbots hotfix details source. Raidbots defaults off; neither its mirror nor its pinned GitHub/WoWDBDefs schema dependency is silently enabled. The current Wago-only detail coverage does not pass the required gate without the selected same-build hotfix details. The review explains the blocker instead of adding a provider, reducing validation or borrowing Era data.

Select **Check requirements**, then **Build** once the current immutable plan passes. First-run success offers **Open library**. A management build registers the checked compact version for the next startup and reports **Ready for next startup**; the current Journal and its open database remain unchanged. Editing choices invalidates their previous review.

The app is Java only. End users do not launch an importer, Python, Node, Blender or a database server. The source-only JAR needs Java 21; portable packages bundle Java. The existing preview packages predate this flow and have not been republished.

## What this first catalog includes

The fresh bootstrap provides a core/client reference catalog. It does **not** provide full feature parity with the older development library: map pictures, flight routes, instance floors, collector imports, expanded source-comparison projections and artwork integration remain work in [issue #14](https://github.com/aircan80/vibecraft/issues/14). Unselected AtlasLoot means its boss-loot lists are absent. Missing media stays explicit; static portraits remain the image scope, with interactive 3D excluded.

A measured fresh run selected Questie, native/Wago and Raidbots, without AtlasLoot or All The Things. It produced a verified compact runtime of **45,441,024 bytes (45.4 MB)** from a 642,269,184-byte full candidate. This is one selected core-library measurement, not a promised size for all providers, artwork or future complete libraries. See [the preparation acceptance record](../verification/2026-10-18-fresh-java-library-preparation.md) for source pins, scope and timings.

## Explicit source and transport choices

Source cards explain **Adds**, **Needs** and **If not selected**. Each game's saved preferences are separate. Unsupported selected games/providers block review; they are not silently omitted.

| Choice | Current behavior |
| --- | --- |
| QuestieDB | Required by the current core projector; exact reviewed source pin. |
| Blizzard native data | Exact Forever70450/enUS identity and supported native-table set required. |
| Wago.tools exports | Explicit delivery-provider consent required for this catalog path; provider-reported table provenance is distinct from CASC authentication. |
| Raidbots hotfix details | Optional/default-off third-party choice; the current supported bootstrap needs it to satisfy the unchanged native-detail gate. Requires native + Wago selection, the pinned same-build snapshot and disclosed GitHub/WoWDBDefs schema. Deselecting dependencies clears this draft choice with an explanation. |
| AtlasLoot | Optional; offered for building only when the actual pinned adapter passes preflight. Omission excludes its data rather than substituting another addon or rates. |
| All The Things | Optional under its actual adapter's compatibility checks; unsupported conditions remain qualified. |
| Other games, comparison providers, collector imports | Preferences/design do not establish a supported production job. Unhandled choices remain gated. |

An unchecked provider is not downloaded or imported merely because its files exist in cache. No Era fallback, hidden substitute, automatic consent or fabricated source lineage is permitted. Source-specific rights and notices remain separate from code licensing; attribution alone does not establish redistribution permission.

## Read-only WoW folders and optional CDN permission

The Game files step can inspect remembered/common installation paths or a chosen folder on a background worker. Metadata recognition is not proof of complete archives, supported extraction or usable encrypted content. Full catalog construction currently uses **selected Wago exports**, not local CASC or direct Blizzard CDN table extraction.

A chosen WoW folder and the optional missing-content CDN permission are preserved in the reviewed plan. They do not have to be turned off to use Wago. An unavailable remembered folder is permitted and protected when actual catalog acquisition is Wago; an actual local acquisition still requires its files to exist. A clear-folder action remains available. CDN permission never grants Wago consent, and Wago consent never grants CDN permission.

All downloads, partial files, cache, source archives, staging databases and managed runtime versions stay in Vibecraft-owned storage. Selected game folders and active-permission roots form the protected read-only boundary. Reject app write paths inside those roots, including aliases. The active database is also protected. No job populates or patches the Blizzard installation.

Folder detection currently checks bounded common/remembered paths and metadata. Broader platform installation-record discovery and complete local catalog extraction remain separate work. Compatibility checks cover the supported setup target; there is no standalone raw-data/build explorer inside Vibecraft.

## Jobs, cancellation and publication

One bounded worker owns dynamic capability checks, pinned-plan review, acquisition, projection, compact export and publication. No source parsing or SQL runs on the EDT. The service factory captures the established app-data path and an immutable active-database/permission snapshot without I/O.

The user-facing phases are:

`Download selected sources → Prepare library → Build catalogue → Check data → Trim download size → Open library`

Report actual source/file/byte/table counts only when supplied. Projection, SQL checks and other unmeasured work remain indeterminate; there is no fabricated overall percentage. Internal validation can occur both before and after trimming.

Cancellation requests **Stopping after the current step** until a safe boundary returns. Verified acquisitions may be reused by the same preparation session on retry, while each catalog attempt starts fresh and is checked again. Do not describe this as resuming a partial SQL projection or guarantee restart recovery. Late/closed callbacks cannot open a library or switch the current page. A confirmed atomic publication wins a cancellation arriving after its commit; the UI must describe the registered result honestly rather than claim rollback.

Prepare a full candidate and source archive first. Independently validate the compact runtime and its source/export receipts, then publish a versioned copy and atomically register it for next startup. The old open database is not replaced. Full source evidence, logs and checked acquisition cache are currently retained; automatic cleanup is not part of this flow. Cleanup needs a separate reviewed policy for known staged paths after verified publication.

## Remaining work and release gates

The app now uses importer libraries in production; the earlier test-only dependency limitation is resolved. The core preparation path and managed activation have real-data evidence. They are separate from the native GUI journey, which is still under acceptance, and from packaged Windows/macOS launch acceptance and a public release.

Remaining work includes the full first-run capability inventory and missing extended projections/media in [#14](https://github.com/aircan80/vibecraft/issues/14), broader supported editions/providers, local/direct-CDN catalog extraction, durable restart/recovery, estimates, storage selection/cleanup, and cross-platform packaging checks. Explicit conflicts, missing fields, unsupported formats, encrypted files and intentionally omitted sources remain distinct states.

**vibeMiner is a separate planned application** for Blizzard CDN exploration, build comparisons and datamining, tracked in [#10](https://github.com/aircan80/vibecraft/issues/10). Vibecraft setup does not require it. This document retains its historical filename for existing links; it does not specify an embedded mining workspace.
