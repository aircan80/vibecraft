# Explicit Raidbots hotfix acquisition

This backend prepares an immutable raw snapshot in Vibecraft-owned job storage. It does not decode rows, merge a database, enable setup generation, or activate a library. No source-plan or UI integration is present.

The reviewed provider is `https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin`. `Permission.ALLOW_RAIDBOTS_HOTFIX` is required independently of Wago or Blizzard CDN permissions. The future source card should say **Raidbots hotfix mirror**, describe its third-party origin and incomplete coverage, default off, and show the exact selected build and requested locale. Selecting other providers never grants this permission.

The API accepts only Forever beta `wow_classic_beta`, a full `1.60.*.*` build, `enUS`, and an explicit expected SHA-256. The fixed provider path identifies product and requested locale; XFTH itself encodes only the numeric build, not product or locale. Evidence records that distinction and never claims CASC/root authentication. It records the captured content hash, source URL, timestamp, XFTH version/build, and counts for all four status values. No credentials or arbitrary source URLs are accepted.

Transport rejects redirects and non-200 responses, enforces byte/time/entry limits, polls cancellation during stalled downloads, checks exact content hashes, and validates every XFTH v9 entry boundary. Cache reuse rechecks bytes, identity evidence and framing without network. An interrupted or failed acquisition never publishes a complete snapshot; prior immutable snapshots remain. All paths use the existing protected staging boundary and never write into selected WoW installations or active library files.

## Measured motivation

A genuine Wago-only build of Forever 1.60.1.70450 had 15,672 native ItemSparse matches among 21,294 named community items (73%). Of 5,622 missing detail rows, 5,603 have native Item identities, primarily armor and weapons. A separately captured same-build hotfix export intersects 3,800 of those missing IDs: a possible 91.44% union before correct replay semantics. This is evidence for investigating native hotfix coverage, not permission to infer missing statistics or import Era data.

## Decoder and replay work still required

The existing Python reference is `tools/clientdata/clientdata/hotfix.py`. Its XFTH format is a 44-byte header followed by 32-byte entry headers and variable payloads. Valid, removed, invalidated and non-public statuses are retained by the Java structural reader, with sequence, push identity, record/table IDs and payload offsets.

Do not copy the Python overlay behavior unchanged: it counts status-2 removals but ignores them, leaving an unresolved product question. Before production merges, confirm removal semantics against authoritative client behavior; retain tombstones and reject unsupported status transitions. Invalidation must remove the exact record/push contribution and restore an earlier still-valid version where applicable. Snapshot and entry order, not numerical push IDs, determine precedence. A missing row in a later snapshot is not automatically a removal.

Next implement pinned DBD-layout acquisition and Java record decoding for the exact build, table hashes, field widths, signedness, arrays and strings. Validate full consumption and IDs; preserve the raw snapshot and decoder-definition hash. Only after replay tests for removals, invalidation, order and duplicates should SetupPlan allow explicitly selected, strictly same-build overlays. Never copy a legacy input directory wholesale: those caches can contain unselected Era files. Recompute actual coverage after replay; keep the 85% guard and generation disabled until the result meets the full readiness contract. Spell formula completeness, media, additive projections, compaction and activation remain independent gates.
