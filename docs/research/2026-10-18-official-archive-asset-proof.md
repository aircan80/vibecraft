# Official CDN archive asset acceptance

**A fresh requested asset was fetched successfully using Java and OFFICIAL_ONLY.**
This is the UI open-window sound used by `UiSoundService.OPEN`: FileDataID 567490,
SoundKit 850, Ogg Vorbis. It is a single audio proof, not a full import, DB2/local
CASC proof, or an empty-metadata-cache setup acceptance.

## Exact identity and interval

| Field | Verified value |
| --- | --- |
| Product / build / locale | wow_classic_beta / 1.60.1.70450 / enUS |
| Build configuration MD5 | 84bea4233331e390fda8724c6f91ffa5 |
| CDN configuration MD5 | f065eade5276a5ac05606e60a4c5a1f7 |
| Root content MD5 | 8a0ca94be2f6ace93991c04ca6e19b57 |
| Archive identity | 87b7255c070103bb6f355d6b27d5dd63 |
| Exact archive interval | bytes 262903734–262910684 (6951 encoded bytes) |
| Decoded content MD5 | a3bbfecd2f7ac627be224a4c76fd5309 |
| Decoded SHA-256 | c1266a5866182b8185537530705ed5e075288fa49de39f7a84da5515678c60e8 |
| Decoded size | 6914 bytes |

The selected official configuration, authenticated index footer/TOC/pages, exact
206 range response, encoded BLTE identity, BLTE chunk checksum, decoded content
identity, root membership and encoding size were checked. No Wago request or whole
archive download was used. No external runtime program performed acquisition or
audio conversion.

## What was fresh and what was reused

Fresh official discovery selected the exact build and both configurations. The
requested asset was absent from the new cache. Only previously verified decoded
encoding/root metadata was seeded, and both were independently rehashed against
the selected build chain. Those seed content MD5s were
`c441a5427cb86b23bbfb6ed9c6cf0114` and
`8a0ca94be2f6ace93991c04ca6e19b57`; no requested encoded/decoded audio was seeded.

Loose asset URLs still returned HTTP 403/404. At 05:10:35Z the standard 64-new-index
attempt began; it stopped at its budget after 42 seconds, having verified 5,753,312
bytes of new index metadata. It did not fetch the asset. **The default cold one-shot
request is therefore not sufficient evidence of a successful first-use experience.**

An explicit expanded budget (2048 indexes /128 MiB maximum) resumed at
**2026-10-18T05:11:48.872Z** and succeeded at **05:17:58.685Z**, taking
**369.813 seconds**. It reused the first 64 verified indexes and downloaded another
706 indexes /61,094,648 bytes. The total searched cache contained 770 verified
indexes /66,847,960 bytes. The final game-asset transfer was only the 6951-byte
interval above. The larger budget was an explicit acceptance caller choice; normal
callers still use the standard budget.

At **05:19:34Z**, a new Java reader revalidated the requested asset with an injected
asset transport that throws if called: **zero asset transport attempts and bytes**.
Discovery metadata was refreshed separately and is not included in that zero-network
claim. At **05:19:49Z**, Java `ShortVorbis` decoded the fresh Ogg into 14,208 mono PCM
frames, 22,050 Hz /16-bit, producing a 28,460-byte WAV. Human listening was not tested.

The immutable fresh receipt and logs stay outside Git under the development review
`cdn-archive-research` directory. Fresh receipt SHA-256:
`45c6b6c707332b44ff7d9e118cf38bf53d235fd816ad3d011c799b63b889eb5a`.
No game bytes are committed.

## Remaining runtime work

A follow-up pins the same reviewed Forever 70450 CDN configuration on the shared
compatibility descriptor. At 05:22:28Z, the actual shared descriptor revalidated this
asset through its selected configuration with fail-if-called asset transport and
CACHE_ONLY consent. Its authenticated CDN configuration supplied 1424 archives and
included the successful archive above. The descriptor remains exactly Forever 70450;
this does not discover or substitute another build.

Optional archive metadata is deferred for shared cache-only/Wago-only users when it
is absent, preserving existing verified root/content cache reuse without granting
CDN access. Explicit selected-descriptor clients retain their strict configuration
checks. A later permitted archive miss can load the matching pinned CDN metadata.
34 focused standalone tests pass, including actual shared-profile identity and
cache-only/Wago-only reuse with the optional CDN-config cache file absent.

Cold index discovery also needs an explicit permission-aware background workflow:
serialize shared index work, reuse verified metadata across assets, publish bounded
progress, retain progress after cancellation, and resume in bounded batches rather
than asking for repeated city visits. A retry must not restart at the first 64
indexes. Cache hits should remain immediate and no whole archive should be prefetched.
This workflow is tracked in [#15](https://github.com/aircan80/vibecraft/issues/15)
and is not implemented by this proof or silently enabled here.
