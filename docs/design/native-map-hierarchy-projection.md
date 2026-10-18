# Native map hierarchy adapter

The standalone `NativeMapHierarchyAdapter` adds user-visible map hierarchy facts to a protected copy of a validated core candidate. The production preparation workflow runs it after core generation and before compact runtime export. It uses only the selected exact Forever 1.60.1.70450/enUS UiMap and UiMapAssignment inputs already captured by the 51-table Wago bootstrap, plus the hash-pinned selected Questie Forever area overrides. It neither fetches another provider nor substitutes Era data.

`apply(plan, destinationJob, coreCandidate, cancellation, progress)` revalidates the original, verifies its SHA, reads bounded immutable source archive artifacts, validates parent relationships, copies the original into a fresh protected job, captures a separately identified native map source snapshot and projects app facts. It returns the new candidate hash, snapshot, counts, receipt and unchanged core validation result. Cancellation removes only the owned unfinished copy. The original and active library are never written.

The app tables are `ui_map`, `ui_map_child` and zone map IDs, with a small `native_map_zone_link` provenance relation and `world_map_projection` receipt. No generic client rows, artwork, hidden-item claims, ATT attribution or legacy 70374 context are introduced. Missing or ambiguous geometry stays NULL; a valid zone link can exist without geometry or an image. Conflicting area mappings are omitted, and an existing conflicting zone map assignment rejects the copy. Parent cycles, duplicate IDs and incompatible source contexts fail closed.

Core `source_ingestion_id` and `build_id` remain unchanged. Separate map metadata records the exact native build, locale, transform, source snapshot and parent core identity; the resulting file SHA covers all additions. Independent map provenance is not a replacement for core ingestion proof: both describe their own projections. The unchanged whole-candidate validator still checks selected hotfix policy and all core gates. Its existing warning that additive replay was not performed remains core-scoped; production integration must make combined coverage wording accurate rather than silently suppress that warning.

## Verification

Fifteen authored Java tests passed, including cycles, duplicate maps/assignments, ambiguous area links/bounds, repeated identical area links, missing artwork/geometry, source/build rejection, cancellation, SQL identity preservation and rollback on conflicting existing zone links.

A real standalone copy acceptance used the preserved full selected-plan candidate under external `review/selected-hotfix-realjob/storage/jobs/fresh-selected-001`:

- Original SHA: `711aee8655f213ab35f76795a9ac1365bacfc298a2deb4a0f4c93593ae3d6f82`, unchanged before/after.
- New SHA: `04cc7f025d21d8a3b133b5a2bff00f774e546e1d645cf50ac4408c9e746a4203`.
- Map snapshot: `b55625d65f19deac76832dd739fa3e9afe9da051ee405d6d88f16a643de4a2a5`.
- 57 maps; 56 child links, 51 placed and 5 explicitly unplaced; 54 native zone links; no artwork added.
- Whole-candidate validation passed unchanged: 91% client coverage, zero UI skips/unsupported fields, exact selected hotfix proof retained.
- Successful application took 3.84 seconds after initial external harness validation. Cancellation during copy published no candidate or partial and preserved the original SHA.

Durable external reports are `review/build-verification/native-map-focused.log` and `native-map-live-acceptance.log`. An earlier smoke harness attempted metadata inspection at an intentionally cleaned staging path and created an empty SQLite file before failing. Only that confirmed zero-byte file was removed; the corrected harness checks existence and expected SHA before opening SQLite read-only. That harness failure is separate from the adapter acceptance above.

Complete map artwork, collector routes and other additive coverage remain issue #14 work.

## Workflow publication and replay

`StagedNativeMapPublication` creates a protected sibling job for the adapter. It verifies both closed candidate hashes, validates the mapped candidate with unchanged core gates, checks map receipt identity/counts, stages the final-path receipt and checks cancellation before a single `ATOMIC_MOVE` replacement of this attempt's original catalogue candidate. There is no delete-first or non-atomic fallback. Failed/cancelled precommit attempts preserve the core candidate and remove only the owned disposable map output/partial; tiny sibling proofs and lock files remain diagnostic evidence. The successful move consumes the extra full copy, so the workflow keeps one catalogue candidate at its original expected path.

Atomic replacement is the commit boundary. Cancellation is not polled during the following receipt finalization. An exceptional receipt finalization failure preserves the complete mapped staging candidate and fails preparation; it never activates a library or attempts to reconstruct the replaced core file. Every retry still starts a new catalogue attempt.

The final preparation receipt contains the mapped candidate path/hash, map snapshot, transform, counts and final map receipt path. Prebuild and final summaries distinguish imported hierarchy/zone links from missing pictures, routes, floors and collector observations. Unchanged validator warnings are explicitly labeled core-stage scope.

The source archive remains unchanged by mapping. Offline semantic replay is the two-stage contract: `ArchiveWorkflow.project` reconstructs the selected core from its archived sources; the map adapter then reads the exact captured CSV/override inputs and produces hierarchy, bounds and zone links again. Core replay alone does not apply the map transform. Source snapshot/input hashes, transform and projected facts are reproducible; timestamps and whole SQLite file hashes are not promised byte-for-byte identical.

The new `MAPS` workflow phase requires app service progress mapping to its existing build phase; that small service switch integration is owned separately by the root agent.

### Integrated publication and runtime acceptance

The final integration focused suite passed **38 tests, zero failures/errors/skips**: 15 orchestration checks, 16 native-map checks (including immutable-input semantic replay), and 7 atomic publication checks. Durable report: external `review/build-verification/native-map-workflow-focused.log`.

A Java-only live acceptance replayed a fresh protected **copy** of the preserved selected-plan archive, validated its rebuilt core, atomically published the map copy back to that catalogue job's expected `candidate.sqlite`, exported the compact runtime and verified every retained table against the full candidate. This is an archived-source acceptance, not a claim of fresh network acquisition or app activation. All map nodes, child bounds, zone IDs/links and assignment evidence matched the earlier independent native-map acceptance. Original candidate/archive hashes and the replay archive's post-core hash were unchanged by mapping/export.

- External job: `review/selected-hotfix-realjob/storage/jobs/map-integrated-replay-ba842da7-c663-4eb5-b548-02f2c096f4b9`.
- Final candidate SHA: `023f7574bf856a0b4a057cfd7d1a3cf7e90bdf5aac81abed1235953bbcfa809c`.
- Native map snapshot: `bffe2188e28f8203d343d0168334d8901c1bd9df1c70cfff9d1c328d45628e7c`.
- Runtime: **45,481,984 bytes**, SHA `362b19b8aa8052f108262176c67b86c25898b9c6ef1852e78ede8a6c27c067ed`; **57 retained table digests** verified.
- Hierarchy/link counts remained 57/54, with 51 placed and 5 unplaced child maps. Core coverage remained 91%, zero unsupported/skipped UI fields; all selected hotfix proof gates passed.
- Elapsed time: 60.97 seconds including core replay, full-file checks, mapping, compact export and verification.

The rebuilt core uses the current projector's build identity, rather than copying the older candidate's identity; map publication preserves that rebuilt core identity. The map snapshot also captures it. The successful publication consumes the temporary full-size map output; only its small diagnostic receipt/lock remain in the sibling job.

An initial live harness was interrupted by a concurrent isolated recompilation deleting a lazily loaded class. Its staged core remained intact and its map partial was removed. The successful acceptance used an immutable external compiled classpath; no compilation or cleanup ran against those classes during the process. Durable successful report: external `review/build-verification/native-map-workflow-live.log`.
