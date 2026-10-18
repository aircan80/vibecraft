# Native map hierarchy adapter

The standalone `NativeMapHierarchyAdapter` adds user-visible map hierarchy facts to a protected copy of a validated core candidate. It is not yet called by the production preparation workflow. It uses only the selected exact Forever 1.60.1.70450/enUS UiMap and UiMapAssignment inputs already captured by the 51-table Wago bootstrap, plus the hash-pinned selected Questie Forever area overrides. It neither fetches another provider nor substitutes Era data.

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

Next: review this adapter, then explicitly integrate it between core generation and final runtime export. Complete map artwork, collector routes and other additive coverage remain issue #14 work.
