# Read-only local WoW discovery for Vibemining

This backend implements bounded installation inspection for the main app's setup source picker. It finds and validates local installation metadata. It does **not** extract CASC archives, decode DB2 tables, determine complete asset availability, build SQLite or generate portraits.

## API

`LocalWowDiscovery.scan(rememberedPaths, includeCommonLocations, cancelled, progress)` runs on a worker and returns an immutable snapshot. Remembered paths precede conventional OS locations. `inspect(selectedFolder, cancelled, progress)` inspects a Browse selection. Both accept the actual install root, a product subfolder, or `.build.info`; at most two parent levels are checked. Duplicate canonical roots are inspected once. Missing roots produce no invented installation.

A result includes explicit directory, product, full version, build configuration hash, declared root/encoding identities, recorded locales, `.build.info` SHA-256, status and explanation. Statuses are:

- **SUPPORTED_METADATA:** the exact reviewed Forever 70450 configuration matches; extraction and complete assets are still unverified.
- **OTHER_EDITION:** genuine metadata points at another edition/channel. It cannot supply Forever data.
- **UNSUPPORTED_BUILD:** Forever beta metadata exists but its exact build/configuration lacks a reviewed profile.
- **INCOMPLETE:** no active product or local build configuration is available.
- **INVALID:** malformed, oversized, escaping or checksum-inconsistent metadata.
- **CHANGED:** installation identity changed while it was being inspected.

`stillMatches(installation, cancelled)` rechecks the captured `.build.info` SHA-256 and local configuration MD5 before/after future extraction. This is an identity guard, not permission to treat missing or unsupported assets as present.

## Validation and bounds

The parser supports typed `.build.info` headers, BOMs, active rows and multiple installed products. Product/build identity comes from the records and hashed build configuration, never from the folder's name. Configuration version and optional build-uid must agree, and declared root/encoding hashes must be valid. Only the reviewed product/build/configuration pin enables SUPPORTED_METADATA. Era data is never substituted for Forever.

Files are opened for reading only. There is no network request, external program, cache mutation or game-directory write. Canonical metadata paths must remain inside the selected installation; escaping symlinks are rejected.

Search is limited to 64 candidates, 128 metadata rows, 1 MiB per metadata file and a five-second cooperative loop budget, with cancellation around file reads and candidate/row iterations. Operating-system filesystem calls can themselves block; this is not a hard filesystem timeout. The UI must run the detector on its worker and discard stale results.

Conventional candidates cover common Windows and macOS locations plus known Linux Wine/Battle.net/Bottles paths. This first slice does not parse Battle.net installation records, enumerate arbitrary Wine prefixes or recursively search disks. An unrecognized location remains available through Browse or a remembered path.

## Verification

Ten focused JUnit tests passed using synthetic installation metadata. They cover typed headers, MD5/version/product consistency, locale extraction, duplicate root/subfolder choices, Era isolation, unreviewed configurations, missing/corrupt/oversized metadata, traversal via symlinks, changed identity, candidate bounds, cancellation, EDT refusal and unchanged game files. Test-only injected profile hashes are package-private; production accepts only the reviewed 70450 pin.

A Java-only conventional-location scan at **2026-10-18T03:03:12Z** inspected 12 candidates, found zero installations and stayed within the budget. Its proof is outside the app in the development review directory. No real installed-client extraction acceptance is claimed.

## Remaining work

The GUI must connect Detect/Browse and persist the chosen identity. Actual local extraction still requires Java CASC index/archive access, raw DB2 decoding and selected image conversion. Setup source selection must separately authorize network fallback; this detector never adds one. Curated Questie/AtlasLoot sources remain independent from the local WoW installation. Candidate building and atomic activation must happen in Vibecraft storage, never inside the game folder.
