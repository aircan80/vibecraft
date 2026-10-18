# Pinned ItemSparse hotfix event ledger

The Java decoder emits ordered evidence events. It does not resolve an overlay, produce importable CSVs, change SetupPlan or enable library generation. Every selected-table event retains its original snapshot entry position, region, push, status, payload offsets and record identity. Duplicate IDs remain separate events; removed, invalidated and non-public events retain their status and have no fabricated decoded values.

## Exact definition and provenance

The only production schema is ItemSparse at WoWDBDefs commit `b9a63096cdd99b26967f17f9515377ae62cadfd7`, definition SHA-256 `8664c3b5862dfe4c3a4322941b48b032c57b90412c070deb2028f3e448cf674a`, layout `6FCC3191`, and exact build `1.60.1.70450`. That block declares 69 fields and 104 expanded columns, including a noninline ID, signed/unsigned 8/16/32-bit integers, float32 values, UTF-8 NUL-terminated localized strings and arrays. No other build or layout is inferred. See the [pinned upstream definition](https://github.com/wowdev/WoWDBDefs/blob/b9a63096cdd99b26967f17f9515377ae62cadfd7/definitions/ItemSparse.dbd).

The caller supplies definition bytes; the decoder verifies the complete content hash before parsing. The upstream definition is not bundled here. WoWDBDefs authors retain attribution to their work; future acquisition/distribution must preserve applicable upstream notices and review its [license](https://github.com/wowdev/WoWDBDefs/blob/b9a63096cdd99b26967f17f9515377ae62cadfd7/LICENSE.md). This implementation is an independently authored parser, not a copy of the upstream project code.

Decoded evidence retains the Raidbots provider, exact requested product/build/locale, snapshot content hash and decoder-definition commit/hash/layout. The fixed provider URL identifies product and requested locale; XFTH provides only numeric build identity. This weaker provider-based evidence remains explicit, with no CASC authenticity claim. The raw snapshot hash and acquisition evidence are checked again before decoding.

## Why no overlay is resolved yet

Reference implementations do not establish a single validated precedence policy for our client snapshot. DBCD sorts records by push ID and includes a TODO questioning sequential application; it also has special-case invalidation behavior for a cached table. That is useful implementation evidence, not proof of all client semantics. See [DBCD HotfixReader at the inspected commit](https://github.com/Marlamin/DBCD/blob/3b79ca1b4f1392ee3d6cb93ecc47d0b9d434868d/DBFileReaderLib/HotfixReader.cs).

TrinityCore recognizes a distinct removed status and erases the corresponding database record. Its server database replay differs from a client snapshot parser and cannot alone settle snapshot ordering or invalidation rollback. See [TrinityCore DB2Stores at the inspected commit](https://github.com/TrinityCore/TrinityCore/blob/f7cac245da88185d632c682983bd8c33cac34089/src/server/game/DataStores/DB2Stores.cpp).

The existing local Python reference chooses file/snapshot order and counts but ignores removals. The Java ledger deliberately applies neither that unresolved removal behavior nor a guessed replacement.

## Real snapshot evidence

The pinned `70450` snapshot SHA `f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b` contains 4,911 ItemSparse events: 4,873 valid payloads and 38 invalidations. All valid payloads consume the pinned schema exactly and represent 4,864 distinct IDs; nine IDs repeat with identical decoded values. Seven duplicate pairs place a smaller push ID later in the file, so numeric and file ordering differ. Identical payloads cannot demonstrate which order is correct. There are no ItemSparse removal events in this snapshot, so it cannot validate deletion semantics. Region 70 is retained as an opaque observed value, not reinterpreted as a selected user region.

Next: establish and test explicit same-build snapshot ordering, removal/tombstone and push-specific invalidation/rollback rules against independent evidence; add pinned definition acquisition; then design a reviewed selected-source overlay contract. Until then, no database application, threshold reduction, Era fallback or Build enablement follows from successful decoding.

## Bounded policy candidate for this exact snapshot

A further read-only cohort audit found that its 38 invalidated ItemSparse IDs overlap neither the 4,864 valid overlay IDs nor any base ItemSparse ID. Thirteen invalidated IDs are community-named; all 38 have native Item identities. Excluding all invalidated IDs from both overlay and base details therefore leaves the potential coverage unchanged at 19,472/21,294 (91.44%), with 1,822 named records still lacking verified details.

This supports a narrower proposed path that does not choose between file or push precedence: accept only this reviewed snapshot/definition/build/locale-provider pin, require the observed single opaque region 70, reject any removal/non-public event, reject differing valid rows for a duplicate ID, and exclude every ID with any invalidation from native facts. Preserve that exclusion set and all events as provenance. The reader's region value is not a geographic or locale assertion. This proposed policy requires a separately reviewed implementation and real end-to-end validation; the current ledger does not apply it. Future snapshots or ambiguous transitions remain unsupported rather than silently inheriting this pin's policy.
