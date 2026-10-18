# Publication asset audit — 2026-10-17

The current repository is **not an asset-free source distribution**. This is a technical inventory, not permission to redistribute any asset. Code licensing does not establish the rights to game artwork, music, models, screenshots, or third-party addon bundles.

Run from the repository root:

```sh
python3 tools/publishing/audit_assets.py --output .local/publication-audit.json
```

The default command exits 1 while tracked or historically reachable binary candidates remain. `--allow-findings` produces an inventory without failing. It reads tracked working-tree paths, all locally reachable Git object history, declared SQLite BLOB columns, media signatures, and media candidates inside embedded ZIPs. It does not mutate Git, SQLite, or assets. A clean result would still not establish legal clearance: text data, untyped SQLite columns, nested archives, remote-only/unreachable objects, and ownership require separate review.

## Current findings

| Location | Count | Bytes | Meaning |
| --- | ---: | ---: | --- |
| Standalone tracked PNG files | 67 | 10,039,569 | Includes rendered NPC images, screenshots, mockups, and test fixtures; provenance varies |
| Standalone tracked JPEG files | 4 | 695,445 | Map/mockup and test map assets |
| Standalone tracked BLP fixtures | 7 | 295,076 | Native texture fixtures |
| Bundled SQLite database | 1 | 2,283,749,376 | Contains embedded images and third-party archives, in addition to structured data |
| Database `icon.png` | 2,925 | 12,740,939 | PNG signatures confirmed |
| Database `ui_asset.png` | 12 | 26,919 | PNG signatures confirmed |
| Database `zone_map.jpeg` | 57 | 11,330,675 | JPEG signatures confirmed |
| Database `instance_map.jpeg` | 7 | 403,450 | JPEG signatures confirmed |
| Database `source_archive_blob.bytes` | 455 | 340,686,953 | Includes 7 BLPs, 7 JPEGs, and 4 ZIP archives |

The source archive ZIPs also contain media: AtlasLoot Forever ZIP (43 BLP, 20 TGA); AtlasLoot Continued ZIP (43 BLP); `core.zip` (53 BLP, 22 PNG); `module.zip` (96 BLP, 2 PNG, 4 JPEG). These are separate archive contents and may duplicate other images; counts are not unique artwork totals. No M2 or audio entry was found in these four archives by filename extension.

The directly bundled model-derived assets include `app/src/main/resources/data/npc-portraits/mechanical-squirrel.png` and 12 files under `npc-turntables/mechanical-squirrel/`, plus a documentation render. Removing only raw models or music would leave these derived images and the embedded database artwork.

The scan found **98 historical candidate blob objects** reachable from local refs, including older versions of current binary paths. Ordinary deletion or a new `.gitignore` cannot remove those earlier blobs from Git history. No history rewrite, untracking, deletion, push, or publication was performed by this audit.

No tracked standalone raw M2 models, MP3/OGG/WAV audio, ZIP/JAR packages, or DB2/DBC tables were found. Runtime native model/music caches are local rather than tracked. `.local/` is currently excluded by `.git/info/exclude`, which is local to this checkout; it is not a shared repository `.gitignore` guarantee. User cache directories outside the repository are not uploaded by a normal Git push. Any manually attached release JAR must also be audited independently: the application build currently bundles the SQLite database and packaged images.

The JSON inventory is stored in ignored `.local/`; the command and this report contain no extracted native assets. A publication plan must account for existing Git history, release artifacts, tests, documentation previews, archived addon assets, and the SQLite image tables rather than reviewing only the music cache.
