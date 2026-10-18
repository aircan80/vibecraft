# Pinned community input acquisition for library setup

Backend slice for [#1](https://github.com/aircan80/vibecraft/issues/1). `CommunitySourceMaterializer` prepares selected source files for the existing Java library importer. It does not create, validate or activate a runtime database, and adds no explorer or separate tool UI.

## Selection and exact revisions

`materialize(plan, job, cancelled, progress)` accepts the immutable `SetupPlan` and protected job context. It resolves only selected Questie, AtlasLoot and AllTheThings sources from the application's reviewed file manifests. The plan pin must equal the registered full 40-character Git commit before any acquisition directory or network request is created. Changed pins, floating refs, unsupported manual ZIP sources or baseline-dependent adapters fail explicitly; there is no CurseForge or hidden source fallback.

The implementation downloads required files directly from `https://raw.githubusercontent.com/<owner>/<repository>/<commit>/<path>`. It does not download or unpack repository archives. ZIP members, symlink entries and archive expansion therefore never enter this path. Manifest paths reject absolute paths, backslashes, drive syntax, parent traversal, duplicate/case-colliding files and excessive depth. Original importer filenames are preserved.

Unchecked AtlasLoot or ATT sources are neither downloaded nor returned from a previously populated cache. The full selected ATT manifest, including retained auxiliary evidence and license, is materialized; acquiring a file does not execute it or project every retained auxiliary table as native game data.

## Bounded publication and evidence

Default bounds are 64 MiB per file, 1 GiB total input, 2,048 files, a 120-second request deadline and 250 ms between request starts. HTTP redirects are not followed. Downloads stream to uniquely named partial files; cancellation is checked during transfer, hashing and file iteration. Progress reports source/path/file counts and bytes when available. The caller runs this blocking API on its setup worker.

Every write passes the job's `SetupStorage` boundary, protecting selected read-only WoW folders and the active database. Only fresh job-cache staging is written. A complete input set receives a receipt with exact source commits, URLs, timestamps, sizes and **recorded download SHA-256s**. These hashes detect subsequent cache changes. They are not independently reviewed expected file hashes and do not authenticate their own initial download; initial origin comes from HTTPS GitHub URLs at reviewed commit refs.

An atomic pointer publishes the complete materialized set only after every required file succeeds. Failed/cancelled staging is removed, while previous complete files and their pointer remain intact. Complete-cache reuse checks the receipt hash, exact manifest/source/URL identity, every file's size/hash, absence of extra input files and storage aliases. Corrupt/incomplete inputs are not silently reused. Partial-download resumption is not yet implemented.

The immutable result exposes Questie, optional AtlasLoot/ATT roots, per-file evidence and a bridge to `SetupPlan.MaterializedInputs`. Native client data remains a separate explicit acquisition result. The existing capture/validation/activation gates still apply.

## Verification

Fourteen focused tests passed with Java 21 both standalone and through the importer Maven reactor (zero failures, errors or skips). They cover:

- Exact source consent/pins and unsupported/floating source refusal before downloads.
- Complete selected optional inputs, including all production registered manifest paths using synthetic transport.
- Complete cache reuse, corruption detection and preservation of the last complete input on failure.
- Unsafe/case-colliding paths, byte/file bounds and game-folder symlink redirection.
- Cancellation without partial publication.
- Actual loopback HTTP success, redirect refusal and cancellation during streaming.

Loopback/synthetic checks are not live GitHub acquisition acceptance. No real source archive, downloaded game asset or generated database is committed. Full main-app setup, native table coverage, candidate projection and activation remain separate integration work.

## Actual selected Questie acceptance

A separate Java-only production acquisition completed on **2026-10-18 at 04:32:12 UTC**, using a fresh protected external setup job and a persisted **Questie-only** plan. All **42 required raw GitHub files** at commit `e9a67d1a2753a0e64c4c96bf7864788fac7b765f` were downloaded: **14,352,135 bytes** in approximately **12.61 seconds**. AtlasLoot and ATT were not selected or requested.

A second materialization used a new instance whose transport throws if any network request is attempted. It revalidated and reused the complete input set with **zero network attempts and zero downloaded bytes**. Existing complete inputs remained in place.

- Selection/file-manifest ID: `9d577b99c1b8c158f08ab77b25d0ce942574ba588335fd004235eb71501906bf`
- Captured acquisition receipt SHA-256: `e7a3fb61b92676519a34bcebb6c933f382a27eb7d838a72554f401b912bb5346`

The original files, persisted plan and timestamped acceptance receipt remain in external development review storage. Per-file hashes remain recorded download integrity evidence, not independently reviewed expected hashes. The materialized Questie root is ready as an input for a later real setup job; this acceptance did not project, validate or activate a database.
