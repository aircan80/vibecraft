# Known-media archive hints

This assessment was followed by the [runtime implementation and measured control](2026-10-18-known-media-hint-runtime.md).

Recommendation for [#7](https://github.com/aircan80/vibecraft/issues/7) and
[#15](https://github.com/aircan80/vibecraft/issues/15): implement small reviewed
exact-build archive hints before wiring the background coordinator into the shared
reader. Current app media does not need a global-fetch-lock refactor to avoid its
cold archive search.

## Measured coverage, without new downloads

The current catalog has five UI cues and 26 unique city-music FileDataIDs across six
cities: **31 selected files**. An external Java tool rehashed the previously verified
Forever 70450 root/encoding metadata and selected CDN configuration, resolved those
31 FDIDs to exact content/encoding keys, then authenticated the 770 already-cached
archive indexes. It requested no network or additional game assets.

**All 31 files were found in only three indexes:**

| Selected files | Pinned archive/index identity | Index bytes |
| --- | --- | ---: |
| All five UI cues | 87b7255c070103bb6f355d6b27d5dd63 | 243108 |
| 25 city tracks | 3405a8dbe3154b5801900aad8ebf7af9 | 8268 |
| Remaining city track, FDID 53194 | 5b5e32951bded7e54260f5ae4c1cf2af | 32988 |
| Total | Three unique indexes | **284364** |

A metadata-only candidate manifest is **3665 bytes**, including provenance comments.
It records FDID, content MD5, full EKey and archive identity, with no offsets, audio,
textures or other game bytes. It remains external, pending review and implementation.
SHA-256: `6448f55405deffd404eb006a1b40ff3abea81db58bb6320e726a8caebe6111f0`.

## Proposed bounded runtime behavior

1. Bundle the reviewed tiny metadata resource under the Java package resource path,
   outside `/data`, so source-only packaging retains it. Bind it exactly to product,
   build, build configuration, CDN configuration, root content MD5 and encoding content
   MD5. Reject another build/edition; do not infer a substitute.
2. Retain normal runtime FDID-to-content-key-to-EKey resolution through authenticated
   root and encoding. A candidate hint is usable only when that exact EKey agrees.
3. Require the hinted archive in the selected, authenticated CDN configuration. Fetch
   only its `.index`, authenticate footer/TOC/pages/full EKey record, and derive offset
   and size from that record. Never trust a bundled offset or location as asset proof.
4. Request the exact range and retain all existing HTTP/BLTE/EKey/content/root/size
   checks. Independent CDN/Wago consent and game-folder write guards remain unchanged.
   A verified location can use the existing content-addressed index/hint cache.
5. Preserve bounded selected-source fallback for files without a reviewed hint; do not
   silently start an unlimited scan. Missing/stale hints must not grant another provider
   or edition. The general coordinator remains useful for future/unknown assets.

The three indexes cover current media, so the existing shared lock is sufficient for
this slice. Prewarming five UI sounds reuses the same 243 KB index; city tracks mostly
share a single 8 KB index. This replaces the measured 770-index /66.8 MB lookup with a
single relevant index per cold asset (three unique indexes for all current media),
without downloading full archives. These are coverage/size measurements, not yet a
new end-to-end runtime timing claim.

## Separate metadata cold-start cost

Hints eliminate index search, not authentication of initial metadata. The prior fresh
asset proof seeded and rehashed a roughly 49 MiB decoded root and 136 MiB decoded
encoding table. A genuinely empty-cache installation still needs that authenticated
chain (or a separately reviewed, honestly scoped trust design). A tiny mapping alone
cannot prove partial membership in a flat MD5-authenticated root/encoding file.
Do not call the hint optimization a complete empty-cache or full first-run proof.

Next acceptance should use a fresh asset/index cache with only the explicitly stated
root/encoding seed, measure one hinted official index plus exact range, test hash/build/
EKey mismatch and provider refusal, then separately validate truly empty metadata cache.
No application code or bundled hint manifest was changed by this assessment.
