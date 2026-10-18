# Static NPC portraits

Production NPC pages asynchronously read ordinary, verified PNG images. They do not generate models or launch external processes. Generation, rotation, playback, and turntable controls have been removed. Developer research tools remain separate.

An exact product/build/NPC identity is required. The reviewed bundled/external-data portrait supports Mechanical Squirrel (2671), Forever `wow_classic_beta`, `1.60.1.70450`. The source attribution is World of Warcraft artwork © Blizzard Entertainment. The image and evidence manifest are pinned by SHA-256. External reviewed data uses `wowforever.data.dir`, through ReviewedDataFiles; an explicit directory is authoritative.

Existing local preview caches may contribute only their verified `portrait.png`; rotation frames are ignored. The manifest must match the exact product/build/NPC mapping, image hash, PNG signature and bounded dimensions. Cache reads never modify the cache. `wowforever.npcPreview.cache` optionally overrides its location. A missing portrait is a compact disclosure, not an invented image. NPC246 remains missing because an exact Forever appearance source has not been acquired.

Future local portrait packs can be selected with `wowforever.npcPortrait.pack=/directory`. Its manifest.json uses schema `npc-portraits-v1` and an `entries` array. Each entry requires `product`, `build`, positive `npcId`, relative `file`, `sha256`, `source`, and `evidence` (or `provenance`). Duplicate identities, traversal, symlink escapes, oversized files/images, and malformed or mismatched images fail verification. A local manifest is supplied provenance, not independent proof of the supplier's assertions or distribution rights.

The repository uses one daemon worker, a queue of 16, pending-request deduplication, and an eight-image decoded LRU. Decoding and file reads happen off the EDT. Detached UI pages ignore stale callbacks; revisiting can reuse decoded images. No renderer dependencies are required by the application.

Validation on 2026-10-17: all 11 provider/pack tests and four portrait UI tests passed in isolated compilation against the accepted application's dependencies. Actual owner-cache read-only probe returned NPC7380 AVAILABLE512x512, NPC7550 AVAILABLE512x512, and NPC246 MISSING. These images already existed; no renderer or UI generation was run. Final native screenshots and full integrated Maven acceptance are delegated to the parent acceptance agent against the final frozen artifact.
