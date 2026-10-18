# Completed library staging cleanup (#16)

The original isolated helper acceptance below preceded application wiring. Production setup now invokes it only after durable registration; see [post-activation setup cleanup](setup-post-activation-cleanup.md) for that separate implementation and genuine service acceptance. A successful activation remains successful if cleanup is refused or fails, with a separate storage warning.

## Actual successful workflow storage

The genuine fresh `374f88e` production workflow, after the separately accepted compact activation at `a9732f8`, occupied 1,484,996,608 allocated bytes under its private application storage root. The catalogue job occupied 1,330,028,544 bytes: its full source archive and full candidate were each 642,269,184 bytes, and the staged runtime was 45,441,024 bytes. The reusable acquisition job cache occupied 109,506,560 bytes. The managed runtime was another 45,441,024 bytes. The catalogue job's own cache was empty; selected plans, receipts and diagnostic logs were small.

The initial inventory identified 1,284,538,368 disposable full-database bytes. The later explicitly reviewed external acceptance run removed exactly those two files; allocated storage fell to 200,458,240 bytes, dominated by reusable downloads and two verified runtime copies. The acquisition cache was retained.

## Helper contract

`CompletedLibraryJobCleanup.cleanupCompleted(JobContext, RuntimeLibraryExporter.Receipt, ArchiveIdentity, cancelled)` returns `Report(deletedBytes, deletedPaths, warnings, complete)`. `ArchiveIdentity(bytes, sha256)` must come from the actual closed source archive; it is never inferred from the candidate's identity. The synthetic success test deliberately gives the archive different bytes from the candidate.

Before any deletion, the helper:

- validates the canonical owned `jobs/<id>` context through `SetupStorage`, refusing active paths, game-root destinations, symlinks and aliases;
- acquires the existing library-build and source-archive locks and refuses SQLite WAL/SHM/journal sidecars;
- matches the caller's complete export receipt against the actual persisted preparation receipt and loads the persisted reviewed source plan;
- requires a durable format-2 managed selection for this exact runtime SHA/build with matching reviewed acquisition permissions;
- protects active and previous managed paths, including hard-link aliases;
- independently checks the supplied archive byte/hash identity twice and invokes `RuntimeLibraryExporter.verifyExport` against the full candidate and durable managed runtime;
- rechecks that the selection is unchanged and that staging file identities remain unchanged.

It deletes only `source-archive.sqlite` and `candidate.sqlite`. It never recursively removes a directory. It retains the staged runtime, active/previous managed runtimes, verified acquisition caches, plans, receipts, all diagnostic logs, and lock files. Failed or cancelled preparation without durable verified activation cannot pass preflight. Cancellation is honored throughout validation and immediately before the bounded two-delete commit; the two deletions then run without an intervening cancellation boundary.

If a delete fails, the returned report marks cleanup incomplete and warns that activation remains valid. The helper stops at that failure. It does not automatically retry a partial cleanup after a required proof file has gone: the remaining artifact requires a separately reviewed manual safe plan. Preflight exceptions also leave activation valid and must become cleanup warnings in any future caller. Existing lock protocols and a closed staging job are required; the helper is not a general-purpose garbage collector or a promise to identify arbitrary non-cooperating processes holding read-only file handles.

Eight synthetic direct Java/JUnit tests cover successful bounded deletion, cache/log/runtime retention, independent archive identity, no-activation/cancelled refusals, busy locks/live sidecars, wrong selection, symlink/game-root/active protections, previous-runtime hard links, missing proof and injected partial filesystem failure. These are direct compiled-source checks, not a Maven run or an end-user control. The separately reviewed real-data acceptance below exercises the helper only; application wiring remains pending.


## Actual external successful-job cleanup acceptance

After review of implementation checkpoints `4d1c06d` and `d010461`, the helper was invoked directly against only the already accepted external `374f88e` workflow's successful catalogue job. Both source archive and candidate were independently stream-hashed before any deletion; each actually measured 642,269,184 bytes with SHA-256 `957b87550d92012a37ae1de02180581935e97fcbc712ffc0a7660028adb611d9`. The helper received the genuine persisted export receipt and separately measured archive identity, then performed its own complete preflight before deletion.

The cleanup and subsequent checks passed in 12.28 seconds, excluding initial independent hash/inventory capture. Exactly `source-archive.sqlite` and `candidate.sqlite` were deleted, reclaiming 1,284,538,368 bytes. A before/after inventory of all **233 remaining files** matched exact size and SHA-256, including both job caches, plans, diagnostic logs, receipts, the unchanged selection pointer, staged runtime, and managed runtime. Allocated storage measured with `du -B1` decreased from 1,484,996,608 to **200,458,240 bytes**.

Managed and staged runtime hashes remained `01045bed76b333638d29189a899e7ee7a074a4d05601229cefd69968db22534c`. The old user database was held open read-only and retained `ee10d3ec2680551c4aff232cad1cd7a5a0abc9d0549badbbce9979e5c65b0066`. After cleanup, a fresh managed registry resolved the same selection, and the selected runtime opened read-only with unchanged counts: 21,543 items, 13,392 NPCs, 5,232 quests, 596 zones, 7,033 objects and 19 instances. The actual fresh registry had no previous selection and the reviewed production plan selected no local WoW installation; previous-runtime alias and game-root refusals are covered by synthetic tests, not claimed as additional real-installation runs.

The [sanitized result](completed-library-staging-cleanup.properties) records exact before/after hashes and counts. Private external evidence is under `vibecraft-work/review/completed-cleanup-check/`: `CleanupProductionAcceptance.java`, before/after properties, `retained-inventory-before.tsv`, and `production-cleanup.log`. The retained proof files survive removal of both disposable databases. Failed/partial cleanup still requires a separately reviewed safe manual plan; automatic partial retries remain deliberately absent. Production service wiring and its separate acceptance are documented in the linked post-activation report. This check changed no production preferences, active user database, verified download cache or game installation.
