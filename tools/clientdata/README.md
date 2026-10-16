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

    # also the Classic Era ItemSparse as a fallback for items Forever's ItemSparse lacks (ruling 17)
    ... --fallback-product wow_classic_era

Flags: `--product` (required), `--build` (default newest), `--base-url` (default
`http://localhost:8080`), `--cache` (required), `--tables A,B` (subset), `--icons-from ITEM_CSV`
(path, or file name inside `{cache}/{build}/csv`), `--maps`, `--fdids 1,2,3` (exactly these files),
`--retry-missing`, `--rate` (default 4 req/s), `--fallback-product PRODUCT` (also fetch that product's newest
`ItemSparse`, and nothing else from it), `--fallback-build BUILD` (pin the fallback build; needs
`--fallback-product`).

Tests: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q` (no network).

## Cache layout

    {cache}/{build}/csv/{Table}.csv     CSV export; skipped when present and non-empty
    {cache}/{build}/files/{fdid}.blp    BLP2 bytes; skipped when present
    {cache}/{build}/missing.txt         FileDataIDs that returned 404 (not fatal)
    {cache}/{build}/csv/ItemSparse.era.csv   the fallback product's ItemSparse (--fallback-product)
    {cache}/{build}/build.lock          {product, build, fetched_at, base_url, tables{name: sha256}, file_count, missing_count
                                         [, fallback_product, fallback_build]}; the fallback table is tables["ItemSparse.era"]

Re-running is idempotent: cached items and files already recorded in `missing.txt` cause no requests
(only `/api/builds` is read each run, once). Pass `--retry-missing` to deliberately re-request the known-404 files.
`ItemSparse.era.csv` is cached under the Forever build; it is fetched again when the last `build.lock` names a
different (or no) `fallback_build` than the one resolved now, so a newer Era build replaces it.
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

    OUT/csv/*.csv           copies of the cached tables (ItemSparse.era.csv included when cached)
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

## make_fixture.py: importer test fixture

Cuts a small, self-consistent subset of the cached tables: the given items (plus every piece of their sets)
and every row reached from them (effects, spells incl. trigger and `$<id>` text references, enchantments,
set bonuses, scaling rows for the item levels used), the UiMap chain of the given maps, the given areas with
their sub-zones, and the small lookup tables whole. Rows are copied byte for byte under the original header.
When the cache has `ItemSparse.era.csv`, the Era rows of every subset item are kept in `ItemSparse.era.csv`; for
items without a Forever ItemSparse row they also add their item levels (and references) to the closures, but
they never add items or sets.
`--list-fdids icons|tiles` prints the files the subset needs instead of writing.

The fixture `importer/src/test/resources/fixtures/clientdata` was built from `wow_classic_beta` 1.60.1.70425:

    B=1.60.1.70425; C=~/.cache/wowforever-clientdata
    ARGS="--cache $C --build $B --sets 203,421 --ui-maps 1436,1429 --areas 12,40       --items 25,730,2874,5191,14145,14148,14149,16800,19019,17204,19137,18422,13262,12640,16865,10329,2092,6948,117,159,4500,7736,190179,215129,11306,4127,18837,5248,15327,2916,19321,1306,1282,17783,4549,5079,2902,4836,19316,1485,4134,15855,7919,11863"
    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C         --fallback-product wow_classic_era --fallback-build 1.15.9.70003
    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C       --fdids "$(python tools/clientdata/make_fixture.py $ARGS --out x --list-fdids icons),$(python tools/clientdata/make_fixture.py $ARGS --out x --list-fdids tiles)"
    # a scratch mini cache holding only the subset, so convert.py emits exactly the fixture
    python tools/clientdata/make_fixture.py $ARGS --out MINI/$B
    cp $C/$B/build.lock MINI/$B/ && mkdir MINI/$B/files && cp $C/$B/files/*.blp MINI/$B/files/
    python tools/clientdata/convert.py --cache MINI --build $B --out importer/src/test/resources/fixtures/clientdata

The last 20 ids (from 11306) were added for the known-items gate (`known_items.py` below). They were cut offline
from the cache with the steps above minus the two `fetch.py` runs, so their 19 icons are not in the cache and the
fixture has no PNG for them (`icons/` is unchanged); every CSV change was additive.

The fixture tree is `-text` in `.gitattributes`: one quoted ItemSparse cell holds a real CRLF.

## known_items.py: the known-items oracle

Builds `importer/src/test/resources/fixtures/clientdata/known-items.csv` from Wowhead's Forever tooltips
(`--fetch` downloads `nether.wowhead.com/forever/tooltip/item/{id}` for its `ITEMS`, one request every half second;
without it the saved responses in `--raw` are read). Why Wowhead and not wow.tools.local, and which rows drop which
kinds, is in `fixtures/clientdata/KnownItemsOracle.md`.
The fixture's `ItemSparse.era.csv` came from Era build 1.15.9.70003 (`/db2/ItemSparse/csv?build=1.15.9.70003`,
sha256 `eef48b88d1af6ea23872155819dc59342204b2f34c0b613df0405ef06f7a5145`); the fixture `build.lock` predates the
fallback and has no `fallback_*` keys.
