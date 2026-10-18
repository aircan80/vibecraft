# Compact runtime database

End-user releases should include a ready-to-use runtime database. Source-only GitHub preparation is a separate boundary; it does not mean asking end users to scrape or import data. Python is used only by this developer export tool, never by the shipped Java application.

A release does not need original downloaded addons, archived ZIPs/SQL/Lua/CSV bytes, or the intermediate `source_record` extraction payloads. It does need gameplay facts, maps/icons, edition/build identity, selected values, source versions/hashes, and conflicting assertions. Those remain intact.

## Measured current dataset

A private export on 2026-10-18 UTC, from build `40ca42c8e8c6442c0e2e633f76bf4f4638ebf23faf4029b568ee1ee038c207b7`:

| Artifact | Bytes | Approximate size |
| --- | ---: | ---: |
| Development database | 2,283,749,376 | 2.28 GB / 2.13 GiB |
| Runtime database | 1,050,083,328 | 1.05 GB / 1,001 MiB |
| Runtime database, `xz -T2 -3` | 58,793,808 | 58.8 MB / 56.1 MiB |
| Uncompressed reduction | 1,233,666,048 | 54.02% |

Runtime SHA-256: `ee10d3ec2680551c4aff232cad1cd7a5a0abc9d0549badbbce9979e5c65b0066`.

The xz result measures database compression only, not an entire application/JRE archive. A ZIP will have its own measured size. SQLite opens the uncompressed database; compressed download size does not represent installed disk usage.

The export removed 659,228 intermediate source records, 340,686,953 bytes of original archived files, and an approximately 205 MB assertion deduplication index. It retained all rows in 81 other tables, including FTS shadow tables. Full-row, order-independent SHA-256 multiset digests matched for every retained table. SQLite integrity, foreign keys, and all original metadata matched/passed. Five synthetic exporter tests and 20 focused importer tests passed; main application acceptance remains the integration owner's responsibility.

## Run the developer tool

```sh
python3 tools/release_database/export.py /path/to/development.sqlite /new/path/runtime.sqlite > export-report.json
python3 -m unittest discover -s tools/release_database -p 'test_*.py' -v
```

The source opens read-only under one consistent read transaction. SQLite backup creates a separate temporary copy, changes only that copy, then vacuums it. Existing output paths are refused; failed validation never publishes an output. Publication uses a non-replacing atomic hard link in the destination directory. Keep generated databases/reports outside tracked source paths. No installed or bundled database is changed by the exporter.

## Exact release schema and content differences

- `source_record`: schema retained, all rows omitted. The developer database remains the rebuild authority.
- `source_archive_blob`: replaced with `sha256 TEXT PRIMARY KEY, bytes BLOB CHECK(bytes IS NULL), original_size INTEGER NOT NULL, storage_state TEXT NOT NULL CHECK(storage_state='omitted-release')`. Each original SHA identity remains with its original byte length, `bytes=NULL`, and explicit omission status. This keeps `source_archive_file` foreign keys valid without pretending empty bytes hash as original content.
- `field_knowledge_assertion_identity`: omitted. This large UNIQUE index enforces ingestion deduplication; runtime reads do not use its name. All assertion rows, selected references, and retractions remain. The runtime artifact is not a writable authoring database.
- `meta`: five new keys: `release_dataset_profile=runtime-v1`, UTC creation time, omitted raw-record count, omitted original archive-byte count, and `release_rebuild_supported=false`. All original keys/build identity remain unchanged. Runtime schema version stays 4 because all runtime table contracts are preserved; the named release profile distinguishes the deliberate archive-table variant.
- Original indexes other than the ingestion-only identity index, views, domain data and lightweight source metadata remain. SQLite `VACUUM` repacks pages.

Importer archive initialization/capture/raw evidence reads and structured source extraction reject the release marker with a direct instruction to use the development database. Source catalog browsing remains available without accessing raw bytes; its build-history note explains their omission. Release datasets must not be used for authoring, migrations that ingest new sources, or rebuilding. They can be replaced with a newly built complete runtime dataset.

## Remaining size work

`client_table_row` and its index occupy about 535 MB in the development database, but are actively queried by cities, systems, races, native item/spell/recipe research, NPC appearances and comparison screens. `field_knowledge_assertion` occupies about 181 MB and supports selected facts and conflict visibility. Dropping either would silently remove working features. Further reduction requires typed runtime projections plus query migration and acceptance checks, not deleting every table whose name mentions sources. Keep Forever and explicitly labeled Era comparison data separate.

## Publication boundary

A runtime database still contains gameplay artwork (icons/maps), sourced textual facts and provenance. Compaction is not an artwork license review and does not make the database source-only or cleared for GitHub publication. The clean source export excludes database binaries and raw source archives. A separate reviewed end-user package can include the exact runtime database under an explicit hash/size manifest. No repository publication, history rewrite, public database upload, or asset deletion is performed by this tool.
