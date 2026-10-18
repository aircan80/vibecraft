# Exact Forever archive retrieval and partial DB2 sections

The missing DB2 transports were located in official Blizzard CASC archive
`29608f9bfe8244a6f97a5b86dcf1f417`. These are exact Forever
`wow_classic_beta` / `1.60.1.70450` encoding objects, not Era replacements.

| Table | FileDataID | Expected complete content MD5 | Encoding/header MD5 | Erased decoded range |
|---|---:|---|---|---|
| NPCModelItemSlotDisplayInfo | 1340661 | `03503e9951abb4dc4e0acf24b86bb972` | `9972a6bed3deafc3fbed384544d94501` | offset 850346, length 108 |
| CreatureDisplayInfoOption | 3692043 | `e94a83ca2f54b5036c3ef2437a8f36f4` | `39b283108fb6f3fd480dfed2d647c4e1` | offset 1328099, length 147 |
| ItemDisplayInfo | 1266429 | `29120c29774d2bcdda084f1b6f2fa582` | `ddbaa5989bec1cc97e2bca024e51a1fd` | offset 426882, length 30 |

**These are partial DB2 artifacts, not complete content-MD5-verified files.**
One small encrypted section in each uses key name `057dc814574bd5b6`.
That name, and its byte-reversed form, were absent from both public key
registries checked. We did not guess a key or attempt to recover a nonpublic key.
Other public-key sections in ItemDisplayInfo were decoded using the pinned
wow.export Salsa20 implementation.

The verification chain is:

1. Existing NativeAssets preparation validates the exact build configuration,
   decoded encoding table, and decoded root content MD5.
2. Exact root FileDataID selects the content key; the authenticated encoding
   table selects the encoding key and expected decoded size.
3. The official CDN configuration is MD5 verified. Archive indexes supply
   transport locations; they do not establish build membership.
4. HTTP ranges must return exactly HTTP 206, the requested Content-Range and
   requested byte length. Each preserved BLTE header MD5 equals its encoding key.
5. Every encoded chunk checksum is checked against the authenticated header.
   Available N/Z chunks are decoded with length and framing checks. Public-key
   encrypted chunks use the pinned decoder. Missing-key chunks become explicit
   zero holes at their declared decoded offsets and lengths.
6. A section-aware WDC consumer must reject any erased overlap with metadata or
   a readable section, including records, strings, relationships and IDs. Only
   sections explicitly omitted as encrypted may contain the holes.

No partial file is inserted into the NativeAssets content cache. A partial file's
SHA-256 identifies the artifact containing placeholders; it is not the expected
complete content hash. The offline section audit passed for all three artifacts: readable section spans
and metadata avoid every erased range, and only explicitly skipped encrypted
sections contain holes. This yielded 53,120 gear rows (6 encrypted records
omitted), 87,919 option rows (9 omitted), and 42,541 item-display rows (2 omitted).
A valid appearance recipe is still required before any portrait can be authorized
from these partial tables.

## Reproduce

Run from the repository root. The metadata seed path and tools location can point
at the primary checkout. Requests use the existing two-starts-per-second transport
ceiling while searching indexes; downloaded indexes are retained.

```bash
python tools/npcmodels/humanoid/archive_assets.py \
  --native-tools tools/npcmodels \
  --metadata .local/npc-render-poc/attestation \
  --cache .local/humanoid-70450/assets \
  --cdn-config f065eade5276a5ac05606e60a4c5a1f7 \
  --archive 29608f9bfe8244a6f97a5b86dcf1f417 --encoded-only \
  1340661 3692043 1266429

python tools/npcmodels/humanoid/archive_partial.py \
  --native-tools tools/npcmodels \
  --metadata .local/npc-render-poc/attestation \
  --cache .local/humanoid-70450/assets \
  --keys .local/humanoid-70450/tact-keys.txt \
  --wow-export .local/npc-render-poc/wow-export \
  --node .local/npc-render-poc/toolchain/node-v22.15.0-linux-x64/bin/node \
  1340661 3692043 1266429

python -m unittest discover -s tools/npcmodels/humanoid -p 'archive*_test.py' -v
```

Omit `--archive` to search all archives in the verified CDN configuration.
The authenticated encoded bytes and transport manifests are retained beneath
`assets/encoded`. Partial DB2 files and `authenticated-partial-blte-v1` manifests
are separate, beneath `.local/humanoid-70450/partial`.

Transport and partial manifests are archived in `evidence/archive-70450/`.

Twelve focused tests cover index framing/padding, unsigned offsets, strict HTTP
ranges, authenticated headers and chunks, exact erasure placement, BLTE nonce/key
normalization, framing/length failures and trailing data.

## Sources and pinned decoder

- Official CDN configuration: `f065eade5276a5ac05606e60a4c5a1f7`, fetched from
  `https://us.cdn.blizzard.com/tpr/wow/config/f0/65/f065eade5276a5ac05606e60a4c5a1f7`.
- [wow.export](https://github.com/Kruithne/wow.export), commit
  `c2fd7bde36a712be78a5da896c995b84fbfa2545`; archive index framing and Salsa20
  behavior follow this pinned implementation.
- [Public TACTKeys registry](https://raw.githubusercontent.com/wowdev/TACTKeys/master/WoW.txt),
  fetched bytes SHA-256 `e5695072ed09e4e17462204da60cda7f22a71ef69d6cf2481f563b4d533b6d6a`.
- [wow.export public fallback registry](https://www.kruithne.net/wow.export/data/tact/wow),
  fetched bytes SHA-256 `3147300160a6091c824597493e8588025d172195e502854577f4aab17db7b91f`.
