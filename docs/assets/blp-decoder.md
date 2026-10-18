# Bounded Java BLP image preparation

Tracking: [#17](https://github.com/aircan80/vibecraft/issues/17), under [#14](https://github.com/aircan80/vibecraft/issues/14).

`BlpImageDecoder` converts the top mip of a supported BLP into a straight-alpha `BufferedImage`. It runs on a worker thread, honors cancellation, and performs no downloads or cache writes. Its byte-array and path entry points require no additional runtime, native codec, or game executable. This backend is not yet connected to an icon/map UI pipeline.

Supported BLP2 formats are indexed palettes with 0/1/4/8-bit alpha, DXT1, DXT3, DXT5, and raw BGRA. BLP1 support is deliberately narrower: compression 1, exact flags 0/8, picture type 5 palette alpha or types 3/4 with a separate 8-bit alpha plane, and subtype 0/1. Unsupported combinations fail explicitly. JPEG BLP is rejected: the historical four-channel convention and alpha handling need separate verification before support is appropriate.

Defaults cap encoded input at 128 MiB, dimensions at 8192, and decoded pixels at 16,777,216. Callers can lower these limits. Every declared mip must have a complete, nonoverlapping range with its exact expected encoded size; only the top mip is decoded. Non-power-of-two edge blocks are clipped to the image bounds. Missing top mips, unsigned offset overflow attempts, truncated palettes/blocks, and ambiguous format flags fail with `IOException`. Input arrays must remain unchanged during decoding. Returned images and input ownership remain with the caller.

## Verification and limits

Seventeen authored tests cover channel ordering, packed alpha bit order, both DXT5 endpoint modes, DXT1 transparency, DXT3 four-color behavior, edge blocks, BLP1 variants, path limits, lower-mip validation, cancellation, EDT refusal, and a deterministic 2,000-case malformed-input sweep. Standalone JUnit acceptance passed 17/17 on Java 21. These fixtures are generated from format rules; no Blizzard image payload is committed. An authenticated live CDN sample and end-to-end UI acceptance remain separate work, so this does not claim production icon/map coverage.

## Primary format references

The implementation was independently written against these pinned references; it does not embed their code:

- [wow.export BLP2 reader](https://github.com/Kruithne/wow.export/blob/c2fd7bde36a712be78a5da896c995b84fbfa2545/src/js/casc/blp.js): BLP2 header, palette packing, BGRA and block modes.
- [Pillow BLP reader](https://github.com/python-pillow/Pillow/blob/6085656229c7babd093b2b065141e9936fd3c261/src/PIL/BlpImagePlugin.py): independent palette/block decoding reference.
- [Magos BLP1 format notes](https://github.com/Kanma/BLPConverter/blob/38fc3229fd95040f5030e735e00c16a39ada7f94/doc/MagosBlpFormat.txt) and [BLPConverter reader](https://github.com/Kanma/BLPConverter/blob/38fc3229fd95040f5030e735e00c16a39ada7f94/blp.cpp): narrow BLP1 palette variants and the reason JPEG needs additional care.
- [Microsoft block-compression documentation](https://learn.microsoft.com/en-us/windows/win32/direct3d11/texture-block-compression-in-direct3d-11): BC1/BC2/BC3 block organization.
