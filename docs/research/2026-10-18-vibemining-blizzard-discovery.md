# Vibemining: official Blizzard build discovery

Implemented a Java-only backend for the first explicit **Discover builds** action inside Vibemining. This stage reads official metadata. It does not install a catalog, create SQLite, download game content or claim that a discovered build's tables can already be decoded.

## Integration API

`new BlizzardBuildDiscovery()` owns a Java HttpClient and is AutoCloseable. From a worker, call:

```java
Snapshot snapshot = discovery.discover(productId, cancelled, progressConsumer);
```

The call rejects Swing's event thread. Cancellation is checked before transfers, while waiting for asynchronous HTTP, after each transfer, and before returning. Progress callbacks can arrive on the worker or HTTP callback thread; the UI must marshal them to Swing. No external program runs. Closing the owner terminates its HTTP client.

Immutable result records retain product, region, complete version/build ID, build config MD5, CDN config MD5, optional product config MD5, and CURRENT/PRELOAD channel. CDN locations retain region, official path and declared hosts. Each source document retains its official URL, capture timestamp, byte count, SHA-256, publication status and raw text. This is evidence for the published pointer, **not root membership proof**; the next acquisition stage must download and hash the selected configurations, encoding and root.

`products()` supplies familiar WoW discovery channels, including beta, for the selector. Those labels do not enable corresponding gameplay editions. A beta channel is not permanently assumed to mean Forever; catalog adapters still require reviewed exact product/build identity.

## Transport and parsing

Only `https://us.version.battle.net/<validated-product>/{versions,cdns,bgdl}` is requested. There are no redirects, third-party mirrors or asset downloads. Documents are bounded to 1 MiB, UTF-8 is checked, and a transfer has a 90-second overall deadline plus request/connect timeouts. HTTP failure is reported rather than disguised as an empty list.

Typed column headers, BOMs, sequence comments, reordered columns and unknown additional columns are supported. Required columns, exact row width, duplicate headers, four-part version/build-number consistency, configuration hashes, CDN paths and host syntax are checked. Product and region identity remain explicit.

A preload is added only when the official bgdl table actually supplies a build row. A missing bgdl selector, HTTP 404/204 or empty response means NOT_PUBLISHED; a malformed table or server error remains an error. No historical completeness or preload availability is promised. Discovery does not follow changing pointers after returning its snapshot.

## Actual acceptance evidence

A Java 21 standalone smoke performed genuine HTTPS discovery at **2026-10-18T02:27:24Z**. It found CURRENT **1.60.1.70450**, build ID **70450**, across us/eu/kr/tw:

- Build config: `84bea4233331e390fda8724c6f91ffa5`
- CDN config: `f065eade5276a5ac05606e60a4c5a1f7`
- Product config: `fcfd1bc39031f5921d00aee2d869adb5`
- Official CDN data path: `tpr/wow`
- Preload: **NOT_PUBLISHED**; the official endpoint returned no matching selector.

| Document | Bytes | Captured SHA-256 |
|---|---:|---|
| versions | 630 | `ab8dab6f16863c5b8a6186e3d851c335e3d6981c12c154e0956a3147be9cec43` |
| cdns | 1,338 | `94faf4759ef5397cdd36356bce9a9fd6a3f6e6c0d0c9a269efaaf1336361d6be` |
| bgdl | 64 | `886c40a614a6fb217f52185504531bcf0af08278951c519dbbd037a4f8c9bd5f` |

Raw documents and timestamped proof remain in the external development review directory, not app resources or source-controlled game data. Nine focused JUnit tests passed, covering exact immutable pointers, real-versus-missing preload handling, failure preservation, typed/reordered metadata, invalid identities, unsafe CDN values, cancellation, EDT refusal, response bounds and malformed UTF-8. The smoke downloaded only 2,032 metadata bytes.

## Next stages

The GUI can present discovered build pointers now. Actual build acquisition still needs frozen configuration verification, official archive-index/range support, and Java table/image conversion. The current exact-70450 native audio client proves a narrower config/encoding/root/content path; its third-party transport fallback must not be used for a user-selected Blizzard-only operation. Optional community addon sources remain separate from Blizzard client data. No model rendering or portrait generation is added by this feature.
