# clientdata: client data fetch tool

Build-time Python tooling for the Forever build. This first stage fetches Blizzard client tables
(DB2 CSV exports) and files (BLP icons and map tiles) into a local cache. Later stages convert and import them.

## Setup (once)

Windows:

    python -m venv tools/clientdata/.venv
    tools/clientdata/.venv/Scripts/python -m pip install -r tools/clientdata/requirements.txt

POSIX:

    python3 -m venv tools/clientdata/.venv
    tools/clientdata/.venv/bin/python -m pip install -r tools/clientdata/requirements.txt

Never install the requirements globally.

## Commands

    # all 42 required tables, newest build of the product, local wow.tools.local (default base URL)
    python tools/clientdata/fetch.py --product wow_classic_beta --cache ~/.cache/wowforever-clientdata

    # against wago.tools, one table only (smoke test)
    python tools/clientdata/fetch.py --product wow_classic_beta --base-url https://wago.tools \
        --cache ~/.cache/wowforever-clientdata --tables ItemClass

    # also icons (needs Item.csv in the cache) and map tiles
    ... --icons-from Item.csv --maps

Flags: `--product` (required), `--build` (default newest), `--base-url` (default
`http://localhost:8080`), `--cache` (required), `--tables A,B` (subset), `--icons-from ITEM_CSV`
(path, or file name inside `{cache}/{build}/csv`), `--maps`, `--retry-missing`, `--rate` (default 4 req/s).

Tests: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q` (no network).

## Cache layout

    {cache}/{build}/csv/{Table}.csv     CSV export; skipped when present and non-empty
    {cache}/{build}/files/{fdid}.blp    BLP2 bytes; skipped when present
    {cache}/{build}/missing.txt         FileDataIDs that returned 404 (not fatal)
    {cache}/{build}/build.lock          {product, build, fetched_at, base_url, tables{name: sha256}, file_count, missing_count}

Re-running is idempotent: cached items and files already recorded in `missing.txt` cause no requests
(only `/api/builds` is read each run). Pass `--retry-missing` to deliberately re-request the known-404 files.
`build.lock` is generated; it is committed only in a later task.

## Endpoints used

| Purpose | Path |
|---|---|
| Builds | `/api/builds` |
| Table | `/db2/{Table}/csv?build={build}` |
| File | `/api/casc/{fdid}?download&branch={product}` |

### `/api/builds` response (wago.tools, verified 2026-10-16)

A JSON object keyed by product, each value a list of builds (not sorted by date):

    {"wow_classic_beta": [{"product": "wow_classic_beta", "version": "1.60.1.70425",
       "created_at": "2026-10-16 00:50:05", "build_config": "...", "product_config": "...",
       "cdn_config": "...", "is_bgdl": false}, ...], "wowf": [...], ...}

`resolve_build` uses `version` as the build and picks the entry with the greatest `created_at`.
A flat list of the same entries is also accepted. wow.tools.local's `/api/builds` shape is
**unverified**; so are its CSV and file paths. If they differ, that is a later task's problem.

## Politeness

wago.tools' robots.txt disallows crawling and states no terms, so the tool throttles to at most
4 requests per second, sends a descriptive User-Agent, caches everything, and the live smoke test
fetches a single small table. Prefer a local wow.tools.local instance for full runs.

## convert.py: icons and zone maps

    python tools/clientdata/convert.py --cache ~/.cache/wowforever-clientdata --build 1.60.1.70425 --out OUT

Reads `{cache}/{build}` and writes a deterministic (no timestamps) import tree:

    OUT/csv/*.csv           copies of the cached tables
    OUT/build.lock          copy of the fetch lock
    OUT/icons/{fdid}.png    56x56 RGBA, one per cached BLP referenced by Item.csv IconFileDataID
    OUT/icons/0.png         fallback "?" icon (drawn, no external asset)
    OUT/maps/{uiMapId}.jpg  composited zone map, 1002x668, JPEG quality 85
    OUT/maps.json           [{ui_map_id, name, width, height, tiles, overlays, missing_tiles}] by ui_map_id

- Icons: the 64 px source has a 4 px decorative border (`ICON_BORDER_PX`), cropped on every side, then LANCZOS to 56 px.
  Without `Item.csv` in the cache, every cached BLP that is not a map tile is converted.
- Maps: chain `UiMap` -> `UiMapXMapArt` -> `UiMapArt` -> `UiMapArtStyleLayer` (size) + `UiMapArtTile`, plus
  `WorldMapOverlay` -> `WorldMapOverlayTile`. Only `LayerIndex` 0 is used (every tile row in the build is layer 0).
  Base tiles go at `(col*256, row*256)`, overlay tiles alpha-composited at `(OffsetX + col*256, OffsetY + row*256)`,
  then cropped to the layer size. Maps with several phases use the lowest `PhaseID`.
- A missing or undecodable tile leaves a transparent area (black in the JPEG) and counts in `missing_tiles`; never fatal.
