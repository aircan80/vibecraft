# Java native audio acquisition proof

This Java implementation is wired into both city music and UI sound acquisition. It removes the Python asset worker and external process lifecycle from both services. It does not implement model rendering or rotations, which are outside the current product scope.

`dev.wowforever.app.assets.NativeAssetClient` uses only JDK 21 APIs. Construct with the cache root, `wow_classic_beta` and `1.60.1.70450`. Call `fetch(fileDataId, cancellationSupplier)` from a worker and close the client when its owner shuts down. The returned immutable `Asset` contains the verified content path, file ID, MD5, SHA-256, byte count, product, build and root identity. No subprocess is launched. UI calls fail before waiting for the client lock; queued workers can cancel while waiting.

## Trust chain

1. The previously reviewed official 70450 build configuration hash is pinned in source: `84bea4233331e390fda8724c6f91ffa5`. Other product/build combinations fail closed.
2. Download or cached configuration must hash to that MD5 and identify the requested build.
3. Configuration identifies encoding content and its encoding key. BLTE N/Z decoding validates framing, chunk hashes and sizes, then the decoded encoding content MD5.
4. Authenticated encoding identifies the root content encoding and size. Decoded root MD5 must match the configuration.
5. The exact root supplies the requested file ID's first neutral/enUS, non-LowViolence content hash in native root order. Encoding supplies its byte size and encodings.
6. Official loose CDN content is attempted first. Wago may transport decoded bytes only if they hash to that exact root content hash and match its encoding size. It never supplies build identity.
7. Cached content is rehashed. Successfully acquired content receives a proof JSON including SHA-256 and exact root membership.

Encrypted/unsupported BLTE and legacy root formats fail closed. HTTP redirects are disabled. Response/cache/decoded content are bounded to 512 MiB; configuration to 1 MiB. Request starts share a 2/s ceiling; acquisition has a 120-second body deadline and cooperative cancellation. Cache publication uses temporary files and atomic replacement. MD5 is the CASC format's native content-address mechanism; SHA-256 is recorded for subsequent artifact evidence, not substituted for the publisher's identity chain.

## Actual verification

A clean audio cache with only authenticated configuration/encoding/root metadata seeded fetched file **567490** using Java HttpClient. No encoded or decoded audio was preseeded.

- Decoded asset: **6,914 bytes**
- Native content MD5: `a3bbfecd2f7ac627be224a4c76fd5309`
- SHA-256: `c1266a5866182b8185537530705ed5e075288fa49de39f7a84da5515678c60e8`
- Exact root: `8a0ca94be2f6ace93991c04ca6e19b57`
- Existing Java `ShortVorbis` decoded **14,208 PCM frames**, mono 22,050 Hz, 16 bit; WAV **28,460 bytes**.
- Six focused JUnit tests passed: unsupported product/build; cancellation before network; EDT refusal; corrupt configuration; bounded/framed compressed and native BLTE; wrong locale/LowViolence and malformed metadata.

The metadata seed accelerates this proof but is not trusted: every content hash is checked back to the pinned official configuration. A completely empty cache was not exercised in this proof; code has the download path for all metadata.

## Remaining integration and footprint

Full empty-cache acceptance and packaged-app GUI regression remain before claiming end-user acceptance. Existing controlled playback/lifecycle tests pass. Both services share one lazy Java client and authenticated metadata cache; service closure cancels its pending worker without shutting down the other service. UI assets are bounded to 2 MiB and music to 64 MiB before downloading their payload, using the authenticated encoding size. The exact encoding table is about 135 MiB decoded, plus the root; a first installation currently needs that metadata download and corresponding temporary memory. This is developer source code only: no game audio or metadata blob is added to the repository. A reviewed compact per-file manifest could reduce metadata cost later, but would introduce a separate manifest trust model and must not be described as runtime root parsing.


## Audio service integration verification

After integrating the Java client into both services, **19 focused JUnit tests passed** (native client, city music lifecycle, interface feedback). A separate actual-service acquisition smoke called the production acquisition methods:

- UI sound 567490 produced the 14,208-frame PCM WAV above using Java only.
- Undercity music 8181003 downloaded into the fresh audio cache, verified exact root content `e0b5b10b5844443b940d00d37d393158`, and decoded its first 8,192 PCM bytes as stereo 44,100 Hz / 16 bit through Java Sound.
- No Python, Node, Blender or subprocess is used by either audio service. The developer render tooling remains separate.

The proof deliberately stops short of claiming human listening, full track playback or a fresh packaged-app GUI launch.

## Portable external data

The source-only portable app sets `-Dwowforever.data.dir=<app>/data`. `ReviewedDataFiles.open(relativePath, reviewedSha256, maxBytes)` loads an explicitly selected external data directory authoritatively, checks size and the compiled reviewed SHA-256, and rejects traversal or symlinks outside that directory. It falls back to `/data/` classpath resources only when the property is absent. A missing or altered explicit catalog never borrows a bundled catalog.

City music requires only `city-music/catalog.json`, SHA-256 `f652c48b94f0efa6eedcc175002847270b1aa49717024335dd40261c8410c466`. Its compressed source tables are evidence inputs, not runtime dependencies. UI sound file IDs and their exact product/build are compiled constants; `ui-sounds/catalog.json` is provenance documentation, not a runtime requirement. Native metadata and audio content remain acquired by the authenticated Java client.

Four additional tests verify reviewed external files, bounds and tampering; authoritative missing-data behavior; identical six-city bundled/external catalogs; traversal and escaping symlinks. Combined native/audio/data suite: **23 tests passed**.
