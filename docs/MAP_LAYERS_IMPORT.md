Map layer evidence and replay
=============================

`MapLayersArchiveWorkflow` captures five authentic client CSV exports and two derived
Vanilla query exports. It never selects the bundled database implicitly. Use an offline
candidate copied from the current app database:

```sh
java -cp importer/target/importer.jar dev.wowforever.importer.MapLayersArchiveWorkflow \
  capture candidate.sqlite /path/to/1.60.1.70450 /path/to/mangos.sqlite tools/serverdb/serverdb.lock
java -cp importer/target/importer.jar dev.wowforever.importer.MapLayersArchiveWorkflow \
  project candidate.sqlite SNAPSHOT_ID
```

The client directory contains `csv/{UiMapAssignment,TaxiNodes,TaxiPath,TaxiPathNode,AreaTrigger}.csv`
and `acquisition-manifest.json`. Capture verifies build1.60.1.70450 and each file's manifest
SHA256, preserves the exact CSV/manifest bytes and acquisition URLs, and commits the archive
before projection. VM exports preserve the lock, the SHA256 of the original SQLite database,
exact SQL queries and release URL. The derived CSVs are clearly marked as query exports;
they are not represented as unmodified upstream files. The existing serverdb fetch tool
verifies the pinned release ZIP checksum before extraction.

Offline projection reads captured artifacts only, validates a temporary SQLite backup, then
atomically publishes it. Failures preserve both committed evidence and prior runtime tables.
The shared additive projection history retains active source inputs and updates the runtime
build ID deterministically. Repeating a snapshot yields the same runtime identity.

Flight nodes require player visibility bits1/2 (Alliance/Horde; both means neutral).
Flightmasters match only VM NPCs flagged with npc_flags&8, within three map points, and
present in the app's NPC catalogue. Connections require both public endpoints. Route segments
are clipped to0..100 against actual zone assignments; separate clipped fragments retain a
segment_index so a renderer never joins across an excursion outside the zone.

Instance targets come from Vanilla VMaNGOS teleport relations, with matching client trigger
positions where available. Exits use actual teleport landing coordinates. An exit within two
map points shares its entrance marker. Instance-to-instance entrances retain their parent
context instead of creating an outdoor pin. No Forever-only destination is inferred from a
trigger ID or position. Missing custom-instance entrances remain unrecorded.

The pure `WorldMapTransform` chooses the smallest containing rectangular assignment, with
stable tie ordering, and supports explicit known-zone projection. Rectangles overlap, so this
is not proof of terrain AreaID. An explicit fixed21-NPC corpus retains all outcomes:20 real VM-spawn/Questie
comparisons agree within one point, including the Defias Messenger. It is not a
representative global sample; tools/maplayers/comparison_corpus.py reproduces every row. The required Hogger spawn comparison currently differs by1.406 map points
and remains an explicit release gate; a closer patrol waypoint is not relabelled as a spawn.
