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

    # all 51 required tables, newest build of the product, local wow.tools.local (default base URL)
    python tools/clientdata/fetch.py --product wow_classic_beta --cache ~/.cache/wowforever-clientdata

    # against wago.tools, one table only (smoke test)
    python tools/clientdata/fetch.py --product wow_classic_beta --base-url https://wago.tools \
        --cache ~/.cache/wowforever-clientdata --tables ItemClass

    # also icons (needs Item.csv in the cache) and map tiles
    ... --icons-from Item.csv --maps

    # only the icons of the items in a browser database (what the shipped build used, see below)
    ... --fdids "$(python tools/clientdata/needed_icons.py --db app/src/main/resources/data/wowforever.sqlite \
        --cache ~/.cache/wowforever-clientdata --build 1.60.1.70450)"

    # the navigation rail's UI icons (needs ManifestInterfaceData.csv in the cache, see "UI icons" below)
    ... --ui-icons

    # also the Classic Era ItemSparse and ItemEffect as a fallback for items Forever's ItemSparse lacks (ruling 17)
    ... --fallback-product wow_classic_era

Flags: `--product` (required), `--build` (default newest), `--base-url` (default
`http://localhost:5000`, wow.tools.local's port), `--cache` (required), `--tables A,B` (subset), `--icons-from ITEM_CSV`
(path, or file name inside `{cache}/{build}/csv`), `--maps`, `--fdids 1,2,3` (exactly these files), `--ui-icons`,
`--retry-missing`, `--rate` (default 4 req/s), `--fallback-product PRODUCT` (also fetch that product's newest
`ItemSparse` and `ItemEffect`, and nothing else from it), `--fallback-build BUILD` (pin the fallback build; needs
`--fallback-product`), `--user-agent CONTACT` (the contact part of the User-Agent, see "Politeness").

Every build string, whether resolved from `/api/builds` or given with `--build` or `--fallback-build`, must be four
dot-separated numbers (`^\d+(\.\d+){3}$`, e.g. `1.60.1.70425`) before it becomes a cache path; anything else
stops the run with exit code 2.

Tests: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q` (no network).

## Cache layout

    {cache}/{build}/csv/{Table}.csv     CSV export; skipped when present and non-empty
    {cache}/{build}/files/{fdid}.blp    BLP2 bytes; skipped when present
    {cache}/{build}/missing.txt         FileDataIDs that returned 404 (not fatal)
    {cache}/{build}/csv/{Table}.era.csv   the fallback product's ItemSparse and ItemEffect (--fallback-product)
    {cache}/{build}/build.lock          {product, build, fetched_at, base_url, tables{name: sha256}, file_count, missing_count
                                         [, fallback_product, fallback_build][, ui_icons]}; the fallback tables are
                                         tables["ItemSparse.era"] and tables["ItemEffect.era"]; ui_icons is
                                         [{name, path, fdid, choice}] (fetch.py --ui-icons)

Re-running is idempotent: cached items and files already recorded in `missing.txt` cause no requests
(only `/api/builds` is read each run, once). Pass `--retry-missing` to deliberately re-request the known-404 files.
A request answered with HTTP 5xx is retried three times, after 2, 4 and 8 seconds, before the run fails; a 404 is
never retried.
Each run merges into the build's existing `build.lock`: it replaces the hashes of the tables it fetched (or found
cached) and keeps every other table entry and the `fallback_*` keys of earlier runs, so a `--tables` or `--fdids`
run never drops what an earlier run recorded; the `ui_icons` key is kept the same way until a `--ui-icons` run
replaces it. `file_count` counts the BLP files in `files/` and `missing_count`
the ids in `missing.txt`, whatever the run fetched.
The fallback tables (`ItemSparse.era.csv`, `ItemEffect.era.csv`) are cached under the Forever build. They are
fetched again when the last `build.lock` names a different (or no) `fallback_build` than the one resolved now, so a
newer Era build replaces them all; a fallback table missing from the cache is fetched on its own. Classic Era
(1.15.9) has no `ItemXItemEffect` table: its `ItemEffect` names each effect's item in `ParentItemID`.
`build.lock` is generated. The lock of the build the shipped database was imported from is committed as `tools/clientdata/build.lock`.

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

The User-Agent is `wow-forever-browser-pipeline/<version> (+<contact>)`. The contact is the `--user-agent` value,
else the `WOWFOREVER_CONTACT` environment variable, else the placeholder `contact: set WOWFOREVER_CONTACT`. Set one
of them to a URL or address where the operator can be reached before a full run against a public server; the tool
never fills in personal data by itself.

    WOWFOREVER_CONTACT=https://github.com/<owner>/wow-forever-browser python tools/clientdata/fetch.py ...

## needed_icons.py: the icons of the browser's items

    python tools/clientdata/needed_icons.py --db app/src/main/resources/data/wowforever.sqlite \
        --cache ~/.cache/wowforever-clientdata --build 1.60.1.70450

Prints, comma-separated, the distinct `Item.csv` `IconFileDataID`s greater than 0 of every item in the database
(opened read-only), for `fetch.py --fdids`. The importer embeds only the icons of the items it imports, so this
fetches 2,925 files for the shipped item set where `--icons-from Item.csv` fetches every icon in the build. The item
ids come from a browser database, which needs an import first: a `--skip-client-data` import writes the same item
table, so a new item set is imported once without client data, then its icons are fetched, converted and imported.

## convert.py: icons and zone maps

    python tools/clientdata/convert.py --cache ~/.cache/wowforever-clientdata --build 1.60.1.70450 --out OUT

Reads `{cache}/{build}` and writes a deterministic (no timestamps) import tree:

    OUT/csv/*.csv           copies of the cached tables (the *.era.csv fallback tables included when cached)
    OUT/build.lock          copy of the fetch lock
    OUT/icons/{fdid}.png    56x56 RGBA, one per cached BLP referenced by Item.csv IconFileDataID
    OUT/icons/0.png         fallback "?" icon (drawn, no external asset)
    OUT/maps/{uiMapId}.jpg  composited zone map, 1002x668, JPEG quality 85
    OUT/maps.json           [{ui_map_id, name, width, height, tiles, overlays, missing_tiles}] by ui_map_id
    OUT/ui_icons/{name}.png      the lock's ui_icons at native size, RGBA
    OUT/ui_icons/{name}@24.png   the same at 24 px

- Icons: the 64 px source has a 4 px decorative border (`ICON_BORDER_PX`), cropped on every side, then LANCZOS to 56 px.
  Without `Item.csv` in the cache, every cached BLP that is not a map tile is converted.
- Maps: chain `UiMap` -> `UiMapXMapArt` -> `UiMapArt` -> `UiMapArtStyleLayer` (size) + `UiMapArtTile`, plus
  `WorldMapOverlay` -> `WorldMapOverlayTile`. Only `LayerIndex` 0 is used (every tile row in the build is layer 0).
  Base tiles go at `(col*256, row*256)`, overlay tiles alpha-composited at `(OffsetX + col*256, OffsetY + row*256)`,
  then cropped to the layer size. Maps with several phases use the lowest `PhaseID`.
- A missing or undecodable tile leaves a transparent area (black in the JPEG) and counts in `missing_tiles`; never fatal.
- An undecodable icon is skipped and counted (`icons_failed`); the importer then counts it in `clientdata.icons.missing`
  and the browser shows the fallback icon. Known limitation: Pillow cannot read BLP encoding 3 (uncompressed
  BGRA). In builds 1.60.1.70425 and 1.60.1.70450 that is icon 4559256, used by Rough Wolf Pelt (247877), Savannah Lion Hide (269715)
  and Tanning Rack (279941).

`--ui-icons-only` writes only `OUT/build.lock` and `OUT/ui_icons/` and leaves the rest of `OUT` as it is.
`--tables A,B` copies only those cached tables into `OUT/csv/` (plus `OUT/build.lock`) and leaves the rest of `OUT` as
it is; a table that is not cached stops the run with exit code 2 before anything is written. The two flags exclude
each other.

The crafting tables (`SpellReagents`, `SkillLineAbility` and `SkillRaceClassInfo`, 3A Task 8) joined
`REQUIRED_TABLES` after the full run and were fetched and converted alone:

    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C \
        --tables SpellReagents,SkillLineAbility,SkillRaceClassInfo
    python tools/clientdata/convert.py --cache $C --build $B --out $C/out/current \
        --tables SpellReagents,SkillLineAbility,SkillRaceClassInfo

The importer reads `SpellReagents` and `SkillLineAbility`; `SkillRaceClassInfo` (which races and classes may learn a
skill) is fetched for completeness and not read yet. Both tables the importer reads are optional: a converted
directory without them (one made before Task 8) imports without crafts, counted as `clientdata.crafts.skipped` with a
note naming the missing tables.

## Dungeon tables

`LFGDungeons`, `ContentTuning` and `DungeonEncounter` (3B Task 17) joined `REQUIRED_TABLES` with the client build
1.60.1.70450, together with `Map`, which was already fetched. The importer's `ClientInstanceLoader` reads them for the
WoW: Forever dungeons that AtlasLoot lacks:

- a `Map` row of `InstanceType` 1 and `ExpansionID` 0 is a dungeon; one whose id is the `InstanceID` of an AtlasLoot
  instance (36 The Deadmines, 389 Ragefire Chasm, ...) stays AtlasLoot's;
- the others are imported when their `MapName_lang` equals an `LFGDungeons.Name_lang`, both lower-cased and without a
  leading "the " (Map "The Hall of Thanes" is LFGDungeons "Hall of Thanes");
- their level is the `ContentTuning.MinLevelSquish` of that LFGDungeons row's `ContentTuningID`, their bosses the
  `DungeonEncounter` rows of the map with `DifficultyID` 0, by `OrderIndex`. The client has no loot tables.

Build 1.60.1.70450 yields four: City of Dalaran (2959, level 28, 9 bosses), Excavation Site: Wetlands (2998, 26, 4),
Ruins of Lordaeron (2999, 15, 7) and The Hall of Thanes (3065, 13, 4). The three tables are optional for the
importer: a converted directory without one of them imports without client dungeons and reports
`clientdata.instances.skipped = 1`.

## UI icons

The browser's navigation rail shows six client textures (spec section 5; the owner allows client art for icons only),
listed with a first and a second choice in `clientdata/ui_icons.py`:

| Name | First choice | Second choice |
|---|---|---|
| rail.home | Interface/Icons/INV_Misc_Rune_01 | Interface/Icons/Spell_Nature_AstralRecal |
| rail.items | Interface/Icons/INV_Misc_Bag_08 | Interface/Buttons/Button-Backpack-Up |
| rail.npcs | Interface/GossipFrame/GossipGossipIcon | Interface/Icons/INV_Misc_Head_Human_01 |
| rail.quests | Interface/GossipFrame/AvailableQuestIcon | Interface/Icons/INV_Misc_Note_01 |
| rail.zones | Interface/Icons/INV_Misc_Map_01 | Interface/WorldMap/UI-World-Icon |
| rail.instances | Interface/Icons/INV_Misc_Head_Dragon_01 | Interface/Icons/INV_Misc_Key_03 |

`fetch.py --ui-icons` resolves the paths to FileDataIDs with the client's own `ManifestInterfaceData` table (one of
the required tables; columns `ID`, `FilePath`, `FileName`), matching `FilePath + FileName` whole and ignoring case and
slash direction (`FileName` carries the `.blp`). A missing first choice is logged and the second used; when neither
exists the icon is logged as an error, skipped and counted in the run's `ui icons: N resolved, M unresolved` line. The
resolved `{name, path, fdid, choice}` entries go into `build.lock` under `ui_icons` and their files are fetched.
`convert.py` then writes `ui_icons/{name}.png` at native size and `ui_icons/{name}@24.png`: Interface/Icons art is
cropped to its central 86 % (a 55 px box of 64, which removes the bevel) and resized to 24 px bicubic; GossipFrame,
Buttons and WorldMap art is centred at native size on a transparent 24 px canvas (downscaled first only when larger).
A cached BLP missing or undecodable counts in `ui_icons_missing` or `ui_icons_failed` of the summary; neither is
fatal. The importer embeds both variants in `ui_asset` as `{name}` and `{name}@24`.

Builds 1.60.1.70425 and 1.60.1.70450 resolve all six to their first choice: 134414, 133633, 132053, 132049, 134269 and 134153 (the
two GossipFrame textures are 16 x 16, the rest 64 x 64). They were fetched alone, without a full run:

    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C \
        --tables ManifestInterfaceData --ui-icons
    python tools/clientdata/convert.py --cache $C --build $B --out $C/out/current --ui-icons-only

## make_fixture.py: importer test fixture

Cuts a small, self-consistent subset of the cached tables: the given items (plus every piece of their sets)
and every row reached from them (effects, spells incl. trigger and `$<id>` text references, enchantments,
set bonuses, scaling rows for the item levels used), the UiMap chain of the given maps, the given areas with
their sub-zones, and the small lookup tables whole. Rows are copied byte for byte under the original header.
`--crafts 1,2,3` adds crafting spells to the spell closure, and the `SpellReagents` (by `SpellID`) and
`SkillLineAbility` (by `Spell`) rows of every kept spell are kept, their skill lines joining the `SkillLine` references.
When the cache has `ItemSparse.era.csv`, the Era rows of every subset item are kept in `ItemSparse.era.csv`; for
items without a Forever ItemSparse row they also add their item levels (and references) to the closures, but
they never add items or sets. When it has `ItemEffect.era.csv`, the Era effects (by `ParentItemID`) of those
Era-sourced items are kept, and their spells join the spell closure (from Forever's spell tables).
`--list-fdids icons|tiles` prints the files the subset needs instead of writing.

The fixture `importer/src/test/resources/fixtures/clientdata` was built from `wow_classic_beta` 1.60.1.70425:

    B=1.60.1.70425; C=~/.cache/wowforever-clientdata
    ARGS="--cache $C --build $B --sets 203,421,163 --ui-maps 1436,1429 --areas 12,40       --items 25,730,2874,5191,14145,14148,14149,16800,19019,17204,19137,18422,13262,12640,16865,10329,2092,6948,117,159,4500,7736,190179,215129,11306,4127,18837,5248,15327,2916,19321,1306,1282,17783,4549,5079,2902,4836,19316,1485,4134,15855,7919,11863,118,13446,3827,810,6738,7959,6898,14134,7375,7953,8345,10030,1131,12802,14154,5613,11567,211957,217495,248682,13724,4984,13452,13510,5768,13482,15109,5411,1307,6900,212347"
    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C         --fallback-product wow_classic_era --fallback-build 1.15.9.70003
    python tools/clientdata/fetch.py --product wow_classic_beta --build $B --base-url https://wago.tools --cache $C       --fdids "$(python tools/clientdata/make_fixture.py $ARGS --out x --list-fdids icons),$(python tools/clientdata/make_fixture.py $ARGS --out x --list-fdids tiles)"
    # a scratch mini cache holding only the subset, so convert.py emits exactly the fixture
    python tools/clientdata/make_fixture.py $ARGS --out MINI/$B
    cp $C/$B/build.lock MINI/$B/ && mkdir MINI/$B/files && cp $C/$B/files/*.blp MINI/$B/files/
    python tools/clientdata/convert.py --cache MINI --build $B --out importer/src/test/resources/fixtures/clientdata

The last 20 ids (from 11306) were added for the known-items gate (`known_items.py` below). They were cut offline
from the cache with the steps above minus the two `fetch.py` runs, so their 19 icons are not in the cache and the
fixture has no PNG for them (`icons/` is unchanged); every CSV change was additive.

The 30 ids after 11863 and set 163 were added the same way for the tooltip task (6): items whose Use, Equip and
Chance on hit spells cover the description placeholders (potions with value ranges, food and drink, damage over
time, periods, `${}` expressions, `$@spelldesc`, `$?` conditions, plurals, colour codes), plus a bag, a timed item,
a quest starter, class, race, skill and reputation requirements, charges and a recipe. Set 163 (Chain of the
Scarlet Crusade) is named only by Era rows, which never add sets, so it is listed in `--sets`. Again every CSV
change was additive and `icons/` is unchanged.

`SpellAuraOptions.csv` (the `$h` proc chance) and `SpellRadius.csv` (the `$aN` radius) were added for the real data
run (task 11), when both tables joined `REQUIRED_TABLES`. They were cut offline with the same `ARGS` into a scratch
directory and only those two files were copied; every other CSV the cut wrote equals the fixture's, except
`UiMapAssignment.csv`, which the fixture extends by hand. The fixture `build.lock` was not changed and lists neither
table. The importer reads both tables only when present.

The 2A fix wave added two things the same way, offline and additively. Illari's Key (212347), a real item with an
`Item` row but no ItemSparse row in either build, stands for the icon-only items; it is also in the QuestieDB
fixture, and its icon `134236.png` was converted from the cached BLP. `ItemEffect.era.csv` holds the Era effects of
the fixture's Era-sourced items (Onslaught Girdle, Arcanist Boots, the Felheart pieces, ...), with their Forever
spell rows; the fixture `build.lock` gained its `tables["ItemEffect.era"]` sha (of the full table, like the others).

3A Task 8 (crafting) added, the same way and additively, `SpellReagents.csv` and `SkillLineAbility.csv`, the recipe
items Recipe: Prowler Steak (238645) and its second recipe 250394, and the crafts Heavy Leather (20649,
Leatherworking), Sulfuron Hammer (21161, Blacksmithing) and Coarse Dynamite (3931, Engineering, a yield range of 1 to
3): `ARGS` above plus `--items ...,238645,250394 --crafts 20649,21161,3931`. Spells already in the fixture became
crafts too: Eye of Sulfuras (21160, no SkillLineAbility row), Transmute: Air to Fire (17559, taught by 13482) and
Prowler Steak (1225758, Cooking); Enchant Weapon - Dismantle (435481) has reagents and a skill row but creates
nothing. `SkillRaceClassInfo.csv` is header-only: the importer does not read it, so the cutter keeps no rows of it.
The cut reproduces every committed CSV byte for byte except the hand-extended `UiMapAssignment.csv`, which was left
alone; it also writes a header-only `ManifestInterfaceData.csv` that the fixture lacks (the importer does not read
that table either). The fixture `build.lock` was not changed.

The fixture tree is `-text` in `.gitattributes`: one quoted ItemSparse cell holds a real CRLF.

3B Task 17 (dungeons) added, additively and without a cut, rows copied byte for byte from the 1.60.1.70450 cache
(byte for byte the same files in 1.60.1.70425): the `Map` rows of 36 Deadmines, 2959 City of Dalaran, 2998 Excavation
Site: Wetlands, 2999 Ruins of Lordaeron and 3065 The Hall of Thanes appended to `Map.csv`; `LFGDungeons.csv` rows 1
Wailing Caverns (whose map is not in the fixture), 5 Deadmines and 3271 to 3274; `ContentTuning.csv` rows 4512, 5240,
5254, 5256, 6874 and 7000; and every `DungeonEncounter.csv` row of maps 36, 2959, 2998, 2999, 3002 and 3065 (3002 has
no `Map` row in either build). `AreaTable.csv` gained row 16544 City of Dalaran from the 1.60.1.70425 cache. A cut with
`make_fixture.py` writes these three tables header-only and does not keep these `Map` rows, so they are hand-extended
like `UiMapAssignment.csv`. The fixture `build.lock` was not changed.

The fixture's `ui_icons/rail.quests.png` (16 x 16) and `rail.quests@24.png` (24 x 24) are not client art: they were
drawn with a few lines of Pillow (a yellow bar and dot on a transparent canvas) for `UiAssetLoaderTest`.

## known_items.py: the known-items oracle

Builds `importer/src/test/resources/fixtures/clientdata/known-items.csv` from Wowhead's Forever tooltips
(`--fetch` downloads `nether.wowhead.com/forever/tooltip/item/{id}` for its `ITEMS`, one request every half second;
without it the saved responses in `--raw` are read). Why Wowhead and not wow.tools.local, and which rows drop which
kinds, is in `fixtures/clientdata/KnownItemsOracle.md`.
The fixture's `ItemSparse.era.csv` came from Era build 1.15.9.70003 (`/db2/ItemSparse/csv?build=1.15.9.70003`,
sha256 `eef48b88d1af6ea23872155819dc59342204b2f34c0b613df0405ef06f7a5145`); its `ItemEffect.era.csv` from the same
build (`/db2/ItemEffect/csv?build=1.15.9.70003`). The fixture `build.lock` records both and the `fallback_*` keys.
