# Selected CDN archive ranges

The Java reader now has a bounded archive fallback for an encoding key whose loose
CDN URL is unavailable. It reads only `archives` from the selected, MD5-verified CDN
configuration. It does not enumerate another build or use patch archives, local
CASC files, a third-party index, or a whole archive download.

## Format and trust chain

The supported index variant is v1, 4096-byte pages, full 16-byte EKeys, four-byte
big-endian size/offset fields, little-endian element count and eight-byte MD5
prefix checksums. Other variants are explicitly unsupported. Each archive ID pins
the MD5 of its 28-byte index footer. The reader verifies that identity, the footer
checksum with its checksum bytes zeroed, the TOC checksum and every page checksum.
Exact file length, ordered unique keys, TOC boundary keys, page padding and unsigned
entry bounds are checked before using any location.

This format was checked against primary implementation sources:

- [TACTSharp IndexInstance at 67fd53a](https://github.com/wowdev/TACTSharp/blob/67fd53ac852ca297e9bf95bb41d1a97da202eba4/TACTSharp/IndexInstance.cs)
- [TACTSharp GroupIndex checksum construction at 67fd53a](https://github.com/wowdev/TACTSharp/blob/67fd53ac852ca297e9bf95bb41d1a97da202eba4/TACTSharp/GroupIndex.cs)
- [CascLib footer validation at 38a3466](https://github.com/ladislav-zezula/CascLib/blob/38a34665624b8775bb875274b36191b21c38d97b/src/CascIndexFiles.cpp)

These are community implementations of the format, not Blizzard's specification.
No source code or game bytes from those projects is bundled as a runtime dependency.

The exact requested archive interval must return HTTP 206, an exact numeric
`Content-Range`, the expected byte count, and no content encoding. A server ignoring
Range is rejected; the body subscriber caps bytes even on an erroneous full response.
The range's BLTE EKey is checked (whole BLTE for headerless framing, header MD5 for
chunked framing), then existing BLTE chunk hashes, decoded content MD5, root membership
and encoding content size remain mandatory. `OFFICIAL_ONLY` does not use Wago.

## Bounds and verified reuse

The standard per-fetch budget is 64 **new** indexes and 16 MiB of new index metadata.
Explicit callers can choose a larger bounded budget, at most 2048 new indexes and
128 MiB. Total parsing is separately capped at 2048 indexes/128 MiB per fetch.
Cached indexes are reverified but do not consume the new-network budget, so a retry
can continue beyond the previous batch. A budget failure leaves availability unknown.

A successful EKey-to-archive hint is persisted under the selected CDN configuration.
It is only a hint: the archive must still occur in that configuration and its pinned
index must still prove exact EKey membership. Verified content cache reuse remains
zero-network. Cancellation and app-owned storage guards apply throughout.

The real Forever 70450 CDN configuration lists 1424 archives and about 126.6 MB of
index sizes. Its `archives-index-size` values do not match the observed current index
lengths: the first pinned archive `0017a402f556fbece46c38dc431a2c9b` declares 135988,
but its official index is 173068 bytes and its footer MD5 matches the pinned archive
ID. These size fields are advisory only; actual framing and cryptographic checks
remain strict. The pinned archive-group index URL returned 404, so this slice does
not assume a downloadable global index or fabricate one.

## Verification and shared runtime consent

33 focused standalone JUnit tests passed, including a complete synthetic configuration /
encoding / root / archive-index / range chain and a real loopback Java HTTP client.
The HTTP checks cover exact Range headers, rejected full-body responses, wrong
Content-Range and pre-request cancellation. Other checks cover damaged checksums,
cache reuse, retry budgets, verified location hints and protected storage.

The shared audio reader additionally consults `AssetDownloadPermissions` before each
network request and cache/proof publication. Before catalog resolution it is cache-only;
managed CDN permission does not grant Wago, and Wago permission does not grant CDN.
A verified content cache still works without network consent. Declared read-only game
roots are guarded even without `.build.info`. Explicit descriptor clients retain their
own transport policies and are not changed by this shared runtime context. Startup /
registry persistence is implemented separately by the library activation integration.

A real Java-only official request at 2026-10-18T05:10:35Z selected Forever 70450 from
fresh official discovery. It reused only independently rehashed encoding/root metadata;
requested asset 567490 was absent from the new cache. Both selected configurations
were obtained from official hosts. Loose URLs returned 403/404. The default lookup
verified 64 fresh archive indexes / 5,753,312 bytes and stopped at its explicit network
budget after 42 seconds. No Wago or whole archive was requested; asset availability
remained unknown. A separately bounded expanded metadata lookup is ongoing; its
result will be recorded separately, without claiming fresh asset success prematurely.
Raw DB2 decoding, local CASC extraction and full first-run ingestion are not completed
by this change.
