# Recorded object sources: isolated v0.6 increment

`JournalRecordedObjectSources(itemId, repository, worker, navigator)` is an explicitly lazy, standalone panel. Construction and selection rendering execute no repository work until **Load recorded object sources** is activated. Supply the app's background executor. The panel is ready for a later item-page/tab integration; this change deliberately modifies no existing application files.

## Evidence audit

Read-only installed database `40ca42c8e8c6442c0e2e633f76bf4f4638ebf23faf4029b568ee1ee038c207b7`: item2770 / Elwynn Forest12 returns Copper Vein1731 with107 raw placement rows and Battered Chest106318 with41. Copper's distinct valid points number107. The view therefore uses **Recorded object sources**, never a mining-node count. There is no profession proficiency, required skill, phase, live activity, respawn, stock, yield or rate inference.

Importer `EntityWriter.items/objects` establishes Questie objectDrops and object spawns; collector projection can add the reported object/item relation. Dataset base and correction revisions are shown as archive context, explicitly not per-point Forever verification. Optional collector evidence applies only to item/object membership, not every archived placement. This schema lacks per-coordinate source IDs; the interface does not invent them.

## Bounds and performance

Zones and objects are paged100+one sentinel, exact-zone selected. A selected object's distinct valid coordinate points are limited250+one sentinel. Counts preserve all raw rows, valid rows (duplicates included), and displayed distinct points. Invalid/null coordinates stay counted but are omitted from the coordinates. Missing object/zone names retain exact IDs. An item-membership guard prevents an unrelated selected object being queried as its source. Open object/zone actions reuse normal entity navigation; changing selection only inspects.

Read-only Python SQLite probe (selection, not end-to-end UI): Copper zones27 in1.28ms, Elwynn objects2 in0.15ms, Copper points107 in0.17ms. EXPLAIN uses item-first `item_object_drop` primary key, `idx_object_spawn_object`, and zone/object primary keys. No whole-object catalog scan or schema/index migration is needed. Aggregate counts still inspect the selected item's recorded rows; response bounds do not claim a constant CPU bound for arbitrary future datasets.

The panel discards stale queued work and responses using generation checks. Already-running SQLite work is not interrupted. Back state retains offsets, selected zone and object. Removal invalidates pending work. No pin map or farming route is fabricated; Open object reaches the existing detailed object map.

## Validation

Repository tests cover distinct object identities, duplicate/invalid/missing coordinates, exact zones, unrelated-object guard, missing names/areas, object/point overflow and source labels. UI tests use a queued executor to establish lazy construction, explicit load, click-to-inspect versus Open, stale requests/removal and Back selection restoration. Desktop/compact visual integration remains part of the later root acceptance batch.
