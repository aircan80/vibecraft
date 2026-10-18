# Selected-build Java native reader

Implements a bounded backend slice for [#7](https://github.com/aircan80/vibecraft/issues/7), supporting the main setup flow in [#1](https://github.com/aircan80/vibecraft/issues/1). This does not complete raw DB2 decoding, local CASC extraction, official archive-range acquisition or first-run SQLite creation.

## Frozen selection and explicit transport

`BlizzardBuildDescriptor.fromDiscovery(snapshot, selectedBuild, locale)` freezes a selection from the captured official documents. It verifies that the selected product/build/configuration row actually occurs in the official versions or preload document, that the region's CDN location occurs in its captured cdns document, and that document URL/byte count/SHA-256 agree. Lists and maps are immutable. Supported locale masks control the root lookup; the first matching neutral or selected-locale non-LowViolence entry is retained.

The descriptor pins product, full build, region, locale, build configuration, CDN configuration, declared CDN bases, discovery time and metadata hashes. Discovery evidence identifies the published pointers; actual content is authenticated separately.

Use the explicit constructor for new setup work:

```java
var descriptor = BlizzardBuildDescriptor.fromDiscovery(snapshot, build, "enUS");
try (var reader = new NativeAssetClient(cache, descriptor,
        NativeAssetClient.TransportPolicy.OFFICIAL_ONLY)) {
    var asset = reader.fetch(fileDataId, cancelled, maximumAssetBytes);
}
```

The reader checks both selected configuration MD5s, build-name and declared build-uid identity, decoded encoding MD5, root membership/size/hash, selected file size/content MD5, and records SHA-256 evidence. It uses the selected official CDN hosts rather than a fixed build URL. Unsupported root/BLTE formats fail closed.

- `OFFICIAL_ONLY` never requests Wago.
- `ALLOW_ROOT_VERIFIED_WAGO` is a separate opt-in: downloaded bytes still have to match the exact selected root content hash and encoding size.
- Previously verified content-addressed cache entries are rehashed before reuse. A transport permission controls new network requests; it is not a claim about the transport that originally populated a verified native-content cache.
- Existing three-argument legacy audio construction preserves its previously reviewed Forever 70450 profile and existing root-verified fallback behavior. The new policy is **not yet wired app-wide to setup checkboxes**. Runtime callers must consume the persisted setup plan before that can be claimed.

Network and cache operations remain off Swing's event thread, bounded and cancellation-aware. Before publication, canonical writable paths are checked for a WoW installation ancestor, including symlink redirection. No cache directory, partial file or proof is created inside a detected game installation. The setup storage layer separately guards all explicitly selected read-only game roots and the active database.

## Verification

**31 focused JUnit checks passed** with Java 21: 7 existing reader checks, 11 selected-reader checks, 4 descriptor checks and 9 discovery checks. New fixtures contain complete synthetic configuration/encoding/root/asset chains and use the actual BLTE/EN/TSFM parsers. They test a different selected build, exact proof identity, verified cache reuse and corruption, official-only refusal, opt-in fallback and wrong fallback bytes, configuration/product/build mismatches, locale selection, cancellation and game-cache write prevention. No game asset fixture was added to Git.

A real Java-only acceptance run at **2026-10-18T03:38:05Z** selected official Forever **1.60.1.70450** metadata. Both build configuration `84bea4233331e390fda8724c6f91ffa5` and CDN configuration `f065eade5276a5ac05606e60a4c5a1f7` were downloaded from selected official hosts and hash-checked. Existing decoded encoding/root metadata was seeded and independently rehashed against that chain.

A fresh request for UI sound **567490** then encountered HTTP 403/404 for its loose encoding URLs. **OFFICIAL_ONLY refused rather than selecting Wago.** This is verified permission enforcement, not a successful fresh official-CDN asset download. Archive-index/range acquisition remains necessary for this loose-file miss; the failure alone does not establish whether the bytes are available in an archive.

A subsequent run supplied only that previously verified cached native asset and successfully revalidated it through the selected chain:

- Content MD5: `a3bbfecd2f7ac627be224a4c76fd5309`
- Root MD5: `8a0ca94be2f6ace93991c04ca6e19b57`
- SHA-256: `c1266a5866182b8185537530705ed5e075288fa49de39f7a84da5515678c60e8`
- Size: **6,914 bytes**

Timestamped proof and the strict refusal log remain in the external development review directory. No full empty-cache, full import, human listening or packaged GUI acceptance is implied.
