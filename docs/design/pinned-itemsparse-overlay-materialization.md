# Exact-pin conservative native detail materialization

`PinnedItemSparseOverlayMaterializer` prepares a new immutable client input directory. It does not activate a database or bypass selected-source setup checks. Independent `ALLOW_RAIDBOTS_HOTFIX` permission is mandatory before any output is created. Its typed result has no conversion to the older generic `MaterializedClient` contract.

The supported combination is deliberately narrow: Forever beta `wow_classic_beta`, build `1.60.1.70450`, requested locale `enUS`, Raidbots snapshot SHA `f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b`, and the decoder's reviewed WoWDBDefs definition/layout. The 51 native base tables also have an exact reviewed aggregate SHA: `cfb3e7b90156dd5b226e0da2329721484747302abbc2f310079350d7d29f884f`, calculated from sorted `table=sha256` lines with LF terminators. Original inputs and locks remain untouched. Wago and Raidbots remain distinct providers; this procedure grants no implicit CDN, Era, Item or ItemEffect fallback.

The policy accepts only opaque region value 70, rejects any ItemSparse removal/non-public event, and accepts duplicate valid IDs only when every decoded value agrees. Every invalidated ID is retained as excluded/unknown. An invalidated ID overlapping either a valid overlay row or any base ItemSparse row causes failure: no retraction semantics are invented. The exact snapshot has 4,864 valid IDs, 38 disjoint invalidated IDs and no removal/non-public ItemSparse events. Requested locale/product rely on the fixed third-party provider paths; region 70 is not labeled as geography or locale and no CASC authenticity is claimed.

The output contains:

- `csv/`: unchanged 51 base CSVs plus `ItemSparse.hotfix.csv` only. Float32 values are widened exactly before decimal rendering.
- `build.lock` and `hotfix.json`: exact base/overlay hashes, selected snapshot/header and policy metadata, compatible with existing `ClientDataSource` integrity checks.
- `overlay-policy.properties`: policy ID, snapshot/definition/base-manifest pins, observed region, overlay hash and sorted excluded IDs.
- `evidence/`: original raw `DBCache.bin`, pinned `ItemSparse.dbd`, Raidbots/Wago acquisition proofs, original base lock and `event-receipt.json` retaining event identity/order/status plus exclusions.

The policy ID is `forever-70450-raidbots-f6fae4-sparse-v1`. A new protected staging directory is atomically published only after all copied inputs, generated output and preserved evidence validate again. Failed/cancelled work removes only its own unfinished output and does not replace previous complete inputs or the active database.

## Read-only validation contract

`validate(Path, cancel)` uses the same validation as `validate(Input, cancel)`. `Input.paths()` returns relative slash-separated **file** names; `Input.read(relative, maximumBytes)` must bound its stream before allocating the byte array. An archive database adapter can implement this using bounded JDBC BLOB streams, without temporary extraction or writable game/TMPDIR paths. Returned arrays are checked against the requested bound again. Both the decoder and raw structural reader share identical byte/path validation cores.

Validation reconstructs the ledger from preserved raw bytes and definition, verifies every base table hash and original provider proof, recomputes the conservative rows/exclusions, and checks the generated CSV, lock, metadata and receipts. It returns `Validation(overlaySha256, excludedIds, overlayIds, rows)` for comparing projected database facts. A mutable receipt or caller-created result object is never sufficient proof. Validation is read-only and does not itself grant source consent.

Next integrate the explicit optional source into SetupPlan, freeze all evidence in the source archive, compare projected hotfix IDs and excluded IDs in candidate validation, and run a real staged build. This slice alone does not establish a complete library or enable the setup Build action. Compact runtime export must separately retain an appropriate verified activation receipt if raw evidence is intentionally omitted.
