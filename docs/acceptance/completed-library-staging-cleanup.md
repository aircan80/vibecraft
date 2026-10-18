# Completed library staging cleanup (#16)

This is an isolated, unwired backend helper. No Sources & Updates service, activation code, or startup behavior invokes it yet. A successful activation must remain successful if cleanup is refused or fails; future integration must display cleanup warnings separately.

## Actual successful workflow storage

The genuine fresh `374f88e` production workflow, after the separately accepted compact activation at `a9732f8`, occupied 1,484,996,608 allocated bytes under its private application storage root. The catalogue job occupied 1,330,028,544 bytes: its full source archive and full candidate were each 642,269,184 bytes, and the staged runtime was 45,441,024 bytes. The reusable acquisition job cache occupied 109,506,560 bytes. The managed runtime was another 45,441,024 bytes. The catalogue job's own cache was empty; selected plans, receipts and diagnostic logs were small.

Removing only the two full staging databases would reclaim 1,284,538,368 file bytes, leaving approximately 200 MB of allocated storage, dominated by reusable downloads and two verified runtime copies. This is a measured inventory and a proposed removal amount, not evidence that cleanup has run. No database or cache was deleted to obtain this measurement.

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

Eight synthetic direct Java/JUnit tests cover successful bounded deletion, cache/log/runtime retention, independent archive identity, no-activation/cancelled refusals, busy locks/live sidecars, wrong selection, symlink/game-root/active protections, previous-runtime hard links, missing proof and injected partial filesystem failure. These are direct compiled-source checks, not Maven/full application acceptance. No production cleanup or end-user control is claimed by this checkpoint.
