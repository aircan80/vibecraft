# Bounded Java BLP image preparation

Tracking: [#17](https://github.com/aircan80/vibecraft/issues/17), under [#14](https://github.com/aircan80/vibecraft/issues/14).

`BlpImageDecoder` converts the top mip of a supported BLP into a straight-alpha `BufferedImage`. It runs on a worker thread, honors cancellation, and performs no downloads or cache writes. Its byte-array and path entry points require no additional runtime, native codec, or game executable. This backend is not yet connected to an icon/map UI pipeline.

Supported BLP2 formats are indexed palettes with 0/1/4/8-bit alpha, DXT1, DXT3, DXT5, and raw BGRA. BLP1 support is deliberately narrower: compression 1, exact flags 0/8, picture type 5 palette alpha or types 3/4 with a separate 8-bit alpha plane, and subtype 0/1. Unsupported combinations fail explicitly. JPEG BLP is rejected: the historical four-channel convention and alpha handling need separate verification before support is appropriate.

Defaults cap encoded input at 128 MiB, dimensions at 8192, and decoded pixels at 16,777,216. Callers can lower these limits. Every declared mip must have a complete, nonoverlapping range with its exact expected encoded size; only the top mip is decoded. Non-power-of-two edge blocks are clipped to the image bounds. Missing top mips, unsigned offset overflow attempts, truncated palettes/blocks, and ambiguous format flags fail with `IOException`. Input arrays must remain unchanged during decoding. Returned images and input ownership remain with the caller.

## Verification and limits

Seventeen authored tests cover channel ordering, packed alpha bit order, both DXT5 endpoint modes, DXT1 transparency, DXT3 four-color behavior, edge blocks, BLP1 variants, path limits, lower-mip validation, cancellation, EDT refusal, and a deterministic 2,000-case malformed-input sweep. Standalone JUnit acceptance passed 17/17 on Java 21. These fixtures are generated from format rules; no Blizzard image payload is committed. The later live acceptance below covers one authenticated sample; end-to-end UI acceptance and broad icon/map coverage remain separate work.

## Primary format references

The implementation was independently written against these pinned references; it does not embed their code:

- [wow.export BLP2 reader](https://github.com/Kruithne/wow.export/blob/c2fd7bde36a712be78a5da896c995b84fbfa2545/src/js/casc/blp.js): BLP2 header, palette packing, BGRA and block modes.
- [Pillow BLP reader](https://github.com/python-pillow/Pillow/blob/6085656229c7babd093b2b065141e9936fd3c261/src/PIL/BlpImagePlugin.py): independent palette/block decoding reference.
- [Magos BLP1 format notes](https://github.com/Kanma/BLPConverter/blob/38fc3229fd95040f5030e735e00c16a39ada7f94/doc/MagosBlpFormat.txt) and [BLPConverter reader](https://github.com/Kanma/BLPConverter/blob/38fc3229fd95040f5030e735e00c16a39ada7f94/blp.cpp): narrow BLP1 palette variants and the reason JPEG needs additional care.
- [Microsoft block-compression documentation](https://learn.microsoft.com/en-us/windows/win32/direct3d11/texture-block-compression-in-direct3d-11): BC1/BC2/BC3 block organization.

## Authenticated live acceptance (2026-10-18)

A dedicated external Java harness fetched FDID 134400 with `NativeAssetClient`, exact pinned Forever `wow_classic_beta` 1.60.1.70450, enUS, `OFFICIAL_ONLY`. Root `8a0ca94be2f6ace93991c04ca6e19b57` and encoding `c441a5427cb86b23bbfb6ed9c6cf0114` were explicitly seeded from the previously authenticated external cache; this was not a cold metadata bootstrap or a new live discovery check. The harness supplied metadata-only routing hints through its package-private/reflection test setup, without modifying the production client. Hints came from rehashed metadata and 770 already cached authenticated indexes; production still authenticated its selected index and payload independently.

The fresh payload cache fetched one 362,588-byte index and a 1,385-byte encoded archive interval at offset 51,651,272 in archive `1918c90d70d27694ba7738c258d907e1`. No full archive or broad game download occurred. The decoded 3,916-byte BLP has content MD5 `0b2976326e4a3ddd13e3f33005c15f2f`, SHA256 `4fe1c9232ac7a5ad2ced3be9789384ebe13a906813f7df9a660322b5009e0a02`. It is BLP2 DXT1, one-bit alpha, 64×64. Its 1,886-byte PNG has SHA256 `6f3d9a0edbf3b970627ecfd452862acf807a9e00381e4e15720e79a81df978c1`; visual inspection showed an intact red question mark with transparent surroundings. No filename or item association was assumed. Payloads, PNG and detailed receipt remain outside Git in `vibecraft-work/review/blp-live-fresh`.

The real file exposed an overly strict initial assumption: header byte 11 was `0x11`, not a boolean. Like the primary BLP2 reader, the decoder now derives mip presence from validated range tables rather than trusting that advisory byte. A dedicated authored regression test brings the suite to 18 passing tests. This proves one native icon format only, not broad map-texture coverage.

## Minimal next integration

Prepare icons only from FDIDs carried by actual selected-library native item/spell records and their exact build identity. A bounded worker queue should fetch through that library's explicitly consented client, decode off the EDT, and atomically cache PNGs under the application cache keyed by build/root/content hash and decoder version. Cache reads should verify their manifest/hash; game directories stay read-only. A small memory LRU and in-flight deduplication can deliver only visible-row icons, with cancellation when a page changes. Existing placeholders remain while missing, unsupported or unconsented assets resolve. This does not require a generic asset viewer, eager full-game preparation, or automatic CDN consent.
