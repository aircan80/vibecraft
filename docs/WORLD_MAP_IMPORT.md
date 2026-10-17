World-map archive and projection
===============================

Build the importer jar, then operate on an offline copy of the app database:

```sh
java -cp importer/target/importer.jar dev.wowforever.importer.WorldMapArchiveWorkflow capture \
  /absolute/path/candidate.sqlite \
  /cache/UiMap.1.60.1.70374.csv \
  /cache/UiMapAssignment.1.60.1.70374.csv \
  a8c2fce5e8e52af3925352f5e61cbf7520ae3148
```

The source files come from ATTWoWAddon/AllTheThings at that commit, under
`.contrib/.db/forever/.config/.wago/`. This map source is build 1.60.1.70374;
it does not establish the build of the main client dataset. The importer requires
the expected filenames and a full commit SHA. It archives exact bytes and acquisition
URLs with SourceArchive and commits the capture to the authoritative database. Capture prints
a snapshot ID. Project it separately, including after deleting the source CSVs:

```sh
java -cp importer/target/importer.jar dev.wowforever.importer.WorldMapArchiveWorkflow \
  project /absolute/path/candidate.sqlite SNAPSHOT_ID
```

Projection parses archived bytes only. A failed projection leaves its capture available for
diagnosis and replay and preserves the previous runtime tables. Repeated identical imports
reuse the same snapshot and map-projection history record.

An adjacent lock protects the offline candidate. SQLite `VACUUM INTO` copies all existing
tables and committed data to a temporary candidate; validation precedes atomic publication.
Do not run against a database open in another process. Existing core inputs, source archives and projection history survive. A composite active
ingestion includes the map snapshot, and a deterministic new build ID triggers installation
of the changed runtime data. Dedicated `world_map_projection`
history and `world_map_snapshot_id`, `world_map_source_build`, `world_map_unplaced_count`
metadata describe this additive projection. The bundled database is never selected implicitly.

`ui_map` retains Azeroth 947 and direct children of 947, 1414 and 1415, including maps without
art or matching zones. `ui_map_child` stores normalized bounds or four NULLs when placement
is unavailable. Zone geometry projects world Region extents through the parent assignment
sharing MapID, swapping and reversing the world axes. Root continent buttons use the authored
Azeroth assignment UiMin/UiMax rectangles. Selection is deterministic by OrderIndex then ID.
No matching parent assignment means no invented rectangle (notably Zephras and Darkspear).

Map and collector overlays share a content-based build identity rooted in the original app build. Replaying the same effective inputs in either order retains the same build ID and all original source roles. Core rebuilds refuse applied overlays until their explicit replay is supported, preserving the existing database.
