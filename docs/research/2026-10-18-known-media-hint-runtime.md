# Known-media hint runtime and fresh official control

Implements the reviewed tiny routing optimization for [#7](https://github.com/aircan80/vibecraft/issues/7)
and [#15](https://github.com/aircan80/vibecraft/issues/15). The independent coordinator
remains unwired; the existing shared fetch lock is unchanged.

The packaged resource `dev/wowforever/app/assets/forever-70450-media-hints.tsv` is
**3665 bytes of metadata only**, SHA-256
`6448f55405deffd404eb006a1b40ff3abea81db58bb6320e726a8caebe6111f0`.
It contains 31 FDID/content-MD5/EKey/archive records, no offsets or game asset bytes.
The loader checks its reviewed SHA, strict framing/bounds and exact product/build /
build-config/CDN-config/root-content/encoding-content identity. Its package path is
outside `/data`, preserving source-only JAR compatibility.

Normal runtime root and encoding resolution still supplies the requested content
MD5 and full EKey. A hint selects one archive index only when every identity agrees.
That archive must occur in the authenticated selected CDN configuration. The reader
verifies the complete official index and exact matching EKey record, then derives
the actual offset and size from that record. HTTP range, BLTE EKey/chunks, decoded
content MD5, root membership and encoding size checks remain mandatory.

Unavailable, mismatched or stale routing metadata does not grant trust, a different
build or another provider. It retains the existing bounded selected-source fallback.
An authenticated index with no matching record may be cached as index metadata, but
cannot supply an asset range. Verified EKey-location hints publish only after an
exact matching range passes encoded identity verification. Shared runtime network still obeys
separate CDN/Wago consent; no CDN consent means no CDN downloads.

## Actual external verification

At **2026-10-18T05:38:44Z**, a Java-only proof rehashed the real root/encoding and
selected CDN configuration, resolved all 31 packaged FDIDs to their exact keys, then
read only the three relevant cached indexes. **31/31 root/encoding identities and
31/31 exact authenticated EKey records passed**, using three index files totaling
**284364 bytes** and zero network requests. No assets or index binaries are in Git.

A fresh official control began at **05:37:49.641Z** and finished at **05:38:01.655Z**:
**12001 ms**, including fresh official discovery/configuration and metadata verification.
It used the normal default 64-index budget. Requested audio, encoded audio, indexes
and persisted location hints were absent. Only the previously verified decoded
root/encoding files were seeded and independently rehashed; those initial metadata
downloads are therefore excluded from this control.

The requested UI OPEN sound, FDID 567490, downloaded exactly **one 243108-byte index**
and the authenticated **6951-byte interval** from archive
`87b7255c070103bb6f355d6b27d5dd63`, offset 262903734. Decoded size was 6914 bytes,
content MD5 `a3bbfecd2f7ac627be224a4c76fd5309`, SHA-256
`c1266a5866182b8185537530705ed5e075288fa49de39f7a84da5515678c60e8`.
OFFICIAL_ONLY was enforced; no Wago or whole archive download occurred.
The previous expanded control needed 770 indexes and 369.813 seconds; these are
separate measured runs, not a universal latency guarantee.

At **05:38:50Z**, a new reader with fail-if-called asset transport revalidated the
cached result with **zero asset network attempts/bytes**. Discovery metadata was
refreshed separately. The immutable fresh receipt SHA-256 is
`8591ff77de9ed5417d1fee6abf1286fa79f277ea4854dacb769b2b786eb3305c`;
logs/receipts/game bytes stay outside Git in the development review directory.

Seven new unit tests cover all 31 records/three archive identities, metadata tamper
and extra-offset rejection, every pinned identity component, root/encoding/content /
EKey mismatch, one-index range authentication, stale-hint bounded retry and zero
unselected CDN requests. **52 combined reader/coordinator checks pass.**

## Remaining limits

Initial root/encoding authentication still needs substantial metadata when genuinely
absent (roughly 49 MiB decoded root and 136 MiB decoded encoding in this build).
Hints eliminate index search, not those downloads. This is not an empty-metadata-cache,
full first-run import, human-listening or packaged native UI acceptance. Unknown/new
assets retain bounded fallback; broader automatic resume/progress remains #15 work.
