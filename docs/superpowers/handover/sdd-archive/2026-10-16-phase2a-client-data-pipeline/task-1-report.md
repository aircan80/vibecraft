# Task 1 report: fetch tool

Status: DONE_WITH_CONCERNS

## Implemented
`tools/clientdata/` with `clientdata/{__init__,http,tables,fetch}.py`, CLI shim `fetch.py`, tests, pytest.ini,
requirements.txt, README, venv (ignored). .gitignore entries added.
Interfaces as in the brief. Signature additions (keyword-only defaults, compatible): `fetch_tables(..., only=None)`,
`fetch_files(..., build=None)` (needed for the `{build}/files` path), `write_lock(..., product="", base_url="", missing_count=None)`.
CLI adds `--tables` and `--rate`.

## TDD
RED: `pytest` -> ImportError (cannot import name fetch). GREEN: 10 passed (6 required + flat-list builds, empty-file refetch,
non-404 raises, CLI end to end). First GREEN run failed one test on 46 vs 42, see concerns.

## Smoke (wago.tools, one table)
build: wow_classic_beta 1.60.1.70425 (2026-10-16 00:50:05); ItemClass.csv 455 bytes cached
(header `ID,ClassName_lang,ClassID,PriceModifier,Flags`).
build.lock: product wow_classic_beta, build 1.60.1.70425, base_url https://wago.tools, tables {ItemClass: f1fc0772...b1fb}, file_count 0, missing_count 0.
/api/builds is an object keyed by product (not a list); entry fields product, version, created_at, build_config,
product_config, cdn_config, is_bgdl; unsorted, so newest is chosen by max created_at.

## Concerns
- Brief and task text say 46 tables, but spec 4.1 lists 42 names; REQUIRED_TABLES follows the spec (42). Tests assert 42.
- User-Agent deliberately contains no email/contact address (user's address must not go to external services).
- wow.tools.local /api/builds shape and paths unverified. Column names `IconFileDataID` / `FileDataID` for --icons-from / --maps
  are assumed from DB2 naming and not yet verified against live CSV headers.
- Files cached under ~/.cache/wowforever-clientdata/1.60.1.70425 (outside repo).

## Fix round 1
Changes:
- `clientdata/fetch.py` `fetch_files`: skips fdids listed in missing.txt unless `retry_missing=True`; missing.txt is written in a `finally` (kept on mid-run failure) and a retried fdid that now succeeds is removed from it. CLI flag `--retry-missing` added; README documents it and the idempotency wording.
- `clientdata/fetch.py` `_column_ids`/`main`: a missing Item/UiMapArtTile/WorldMapOverlayTile CSV raises `MissingInputError`, printed to stderr naming the table, exit code 2.
- `clientdata/http.py`: `FetchError` carries `url` and its message uses the full URL.
Tests added/extended in `tests/test_fetch.py`: 404 test re-runs with [100, 101] (hits unchanged) and retry; missing.txt kept after a 500; icons-from (quoted fields, duplicates, empty, 0 -> distinct positive ids); maps unions both tables; maps with missing CSV fails clearly.
Command: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q` -> `14 passed in 9.16s`.
