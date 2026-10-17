# Task 4 report: client UI icon textures for the rail

Status: DONE_WITH_CONCERNS (minor, see Concerns)
Commit: 7ac1847d2bea24a69f3dc071b7c883a4fb822939 `feat(clientdata): client UI icon textures for the rail`
Branch: feature/phase3a-quest-data (main checkout H:\claude_java)

## Resolution path

The `ManifestInterfaceData` path was taken; no `ui_icons.json` was committed.
wago.tools serves `/db2/ManifestInterfaceData/csv?build=1.60.1.70425` (8.78 MB, columns `ID,FilePath,FileName`).
`FilePath` uses backslashes with a trailing separator (`Interface\ICONS\`), and case varies (`INV_MISC_RUNE_01.BLP`, `inv_misc_bag_08.blp`).
Matching normalises slashes and case and compares the whole path. `CampaignAvailableQuestIcon.blp` therefore does not satisfy `AvailableQuestIcon`, and a test covers that.

## Resolved icons (build 1.60.1.70425)

All six resolved to their first choice. No fallback was logged.

| Name | Path | FDID | Choice | Native size |
|---|---|---|---|---|
| rail.home | Interface/Icons/INV_Misc_Rune_01 | 134414 | first | 64x64 |
| rail.items | Interface/Icons/INV_Misc_Bag_08 | 133633 | first | 64x64 |
| rail.npcs | Interface/GossipFrame/GossipGossipIcon | 132053 | first | 16x16 |
| rail.quests | Interface/GossipFrame/AvailableQuestIcon | 132049 | first | 16x16 |
| rail.zones | Interface/Icons/INV_Misc_Map_01 | 134269 | first | 64x64 |
| rail.instances | Interface/Icons/INV_Misc_Head_Dragon_01 | 134153 | first | 64x64 |

The second choices also all exist in the manifest: 136010, 130716, 134166, 134327, 137176 and 134237.

Four of the six BLPs were already cached as item icons. Two were new downloads, so the cache's `file_count` went from 6311 to 6313.
All six decoded with Pillow, with `ui_icons_failed` at 0.
I checked a contact sheet visually. The 86 % crop removes the dark bevel that this build's Interface/Icons art carries. The two gossip glyphs sit centred at 16 px on the 24 px canvas.

## Real output produced

These two commands ran. The fetch ran only for the manifest table and the six files.

    fetch.py --product wow_classic_beta --build 1.60.1.70425 --base-url https://wago.tools \
        --cache ~/.cache/wowforever-clientdata --tables ManifestInterfaceData --ui-icons
      -> ui icons: 6 resolved, 0 unresolved; files cached: 6 of 6
    convert.py --cache ~/.cache/wowforever-clientdata --build 1.60.1.70425 \
        --out ~/.cache/wowforever-clientdata/out/current --ui-icons-only
      -> {"ui_icons": 6, "ui_icons_missing": 0, "ui_icons_failed": 0}

`~/.cache/wowforever-clientdata/out/current/ui_icons/` now holds 12 PNGs, `rail.<x>.png` and `rail.<x>@24.png`, about 48 KB.
`out/current/build.lock` was refreshed. It was copied to the committed `tools/clientdata/build.lock`, which gains `tables.ManifestInterfaceData` (sha 71b82f75...), the `ui_icons` list, `file_count` 6313 and a new `fetched_at`.
The rest of `out/current` (csv, icons, maps) is untouched. Task 5 can import it directly and should see `clientdata.uiAssets = 6`.

## What was built

Python, in `tools/clientdata`:
- **New `clientdata/ui_icons.py`.** It holds:
  - `UI_ICONS`, with each icon's name, first choice and second choice.
  - `load_manifest(csv)`, which builds a dict from the normalised path to the FDID.
  - `resolve(manifest)`, which returns `Resolution(resolved, unresolved, messages)`.
  - `crop_box`, `small_variant` and `convert_ui_icon`.
- **`tables.py`.** `ManifestInterfaceData` joins `REQUIRED_TABLES`, which grows from 44 to 45 tables.
- **`fetch.py`.** It gains `--ui-icons`, which reads the cached manifest. Without the manifest the run exits with code 2 and a clear message.
  - It logs a fallback to the second choice on stderr.
  - When neither choice exists, it logs `error: ... skipped` and counts the icon in the `ui icons: N resolved, M unresolved` line. The rest of the run continues.
  - It adds the resolved FDIDs to the fetch set.
  - `write_lock(..., ui_icons=...)` records the list. Later runs without `--ui-icons` keep the existing key, through the same merge logic as the fallback keys.
- **`convert.py`.** It writes `ui_icons/<name>.png` (native size, RGBA) and `ui_icons/<name>@24.png` for every lock entry whose BLP is cached.
  - It counts `ui_icons`, `ui_icons_missing` and `ui_icons_failed` in the JSON summary. None of them is fatal.
  - The new flag `--ui-icons-only` writes only `build.lock` and `ui_icons/`.
- **The 24 px variant.**
  - For Interface/Icons art, `crop_box` keeps the central 86 % (`round(64*0.86)=55`, offset `(64-55)//2=4`, giving the box (4,4,59,59)) and resizes it to 24 px with BICUBIC.
  - Other art is centred on a transparent 24 px canvas. It is downscaled with BICUBIC, keeping its aspect ratio, only when it is larger than 24 px.

Java, in the importer:
- **New `UiAssetLoader.load(source, report)`.** It returns a `Map<String, byte[]>` of every `ui_icons/*.png` by name, sorted.
  - It counts `clientdata.uiAssets` as the number of distinct base names, which is 6 when all six are present.
  - It notes `clientdata.uiAssets.missing: ...` for any of the six rail names that is absent.
  - It notes a base name that lacks one of its two variants.
- **`ClientDataSource.uiIcons()`.** It lists the directory and returns an empty result when the directory is absent.
- **`ClientDataBundle`.** It gains a new last component, `Map<String, byte[]> uiAssets`. Null becomes `Map.of()`, and `EMPTY` passes `Map.of()`.
- **`ClientDataStage`.** It calls the loader after `ZoneMapLoader`.
- **`SqliteWriter`.** The new `writeUiAssets` fills `ui_asset(name, png)` in name order.
- **`--skip-client-data`.** It returns `EMPTY`, so `ui_asset` has no rows and the counter is absent. `ImportPipelineTest` asserts both.

Docs:
- **`tools/clientdata/README.md`.**
  - A new "UI icons" section has the icon table, the resolution rules, the crop rules, this build's FDIDs and the two commands.
  - The flag list, the lock layout, the convert output tree and the table count (45) are updated.
  - A note says the fixture PNGs are synthetic.
- **Root `README.md`, "Client data".** The fetch command gains `--ui-icons` and there is a "UI icons" paragraph. The sizes row now reads 47 tables, 6,313 files and 218 MB.
- **`NOTICE.md`.** The two lines now name the rail's UI icons and UI textures. The old text covered "icons" only in the sense of item icons.
  - `app/src/main/resources/NOTICE.md` is synced. `MainFrameTest.noticeOnTheClasspathMatchesTheRootNotice` enforces that the two are equal, and the first verify failed on it until I copied the file.

## Files

New:
- tools/clientdata/clientdata/ui_icons.py
- tools/clientdata/tests/test_ui_icons.py
- importer/src/main/java/dev/wowforever/importer/clientdata/UiAssetLoader.java
- importer/src/test/java/dev/wowforever/importer/clientdata/UiAssetLoaderTest.java
- importer/src/test/resources/fixtures/clientdata/ui_icons/rail.quests.png and rail.quests@24.png. These are 16x16 and 24x24, drawn with Pillow: a yellow bar and dot on a transparent canvas. They are not game art.

Modified:
- tools/clientdata/clientdata/fetch.py, convert.py, tables.py
- tools/clientdata/tests/test_fetch.py
- tools/clientdata/README.md
- tools/clientdata/build.lock
- ClientDataSource.java, ClientDataBundle.java, ClientDataStage.java, SqliteWriter.java
- ImportPipelineTest.java, SqliteWriterClientDataTest.java, Fixtures.java (javadoc)
- README.md, NOTICE.md, app/src/main/resources/NOTICE.md

## TDD evidence

- **Python, red.** `test_ui_icons.py` failed to collect with `ImportError: cannot import name 'ui_icons'`. Three `test_fetch.py` tests failed:
  - the table count of 45;
  - `--ui-icons` as an unrecognised argument, which failed two tests.
- **Python, green.** 73 passed: the 56 existing tests plus 17 new ones (15 in `test_ui_icons.py` and 2 CLI tests in `test_fetch.py`).
  - They cover the crop maths (64 gives a 55 px box at (4,4,59,59); 56 gives 48), the icon-art and frame-art 24 px variants, and downscaling of frame art larger than 24 px.
  - They cover resolution against a fake manifest: first choice found case-insensitively, first missing so second used and logged, neither so skipped and counted, and whole-path rather than suffix matching.
  - They cover the lock's `ui_icons` merge, convert writing both variants with missing BLPs counted, and `--ui-icons-only`.
- **Java, red.** `UiAssetLoaderTest` failed to compile with "cannot find symbol" on `UiAssetLoader`.
- **Java, green.** `UiAssetLoaderTest` passes 4 of 4: fixture bytes and size, all six present giving 6 with no notes, a half-present base noted, and no directory giving an empty result with the counter at 0.
  - `ImportPipelineTest` covers end to end with `ui_asset` rows and `clientdata.uiAssets=1`, and the skip case with 0 rows and no counter.
  - `SqliteWriterClientDataTest` checks the `ui_asset` rows and bytes.

## Verification

- **`./mvnw -B -q verify`.** Exit 0. Tests: app 204, importer 364, lua-table 61. That is 629 tests with 0 failures and 0 errors.
- **`tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q`.** 73 passed.

## Concerns

1. **`ManifestInterfaceData` joins `REQUIRED_TABLES`, as the ruling said.**
   - A full `convert.py` run now copies its 8.8 MB CSV into `out/csv`. The importer never reads it.
   - `make_fixture.py` would write a header-only `ManifestInterfaceData.csv` into a regenerated fixture.
   - Both are harmless. If you prefer, the table could live in a separate "fetch-only" list instead.
2. **Stale files can stay behind.** `convert.py` never deletes stale files in `out/ui_icons/`, just as it treats `icons/`. If a future build drops a rail icon, its old PNGs would remain and be embedded. The icon set is fixed, so I left it.
3. **The gossip glyphs are small.** They are 16 px drawings, so their `@24` variants are a 16 px glyph with a 4 px transparent margin, as the spec says. The UI task may find them visually small next to the cropped 64 px icon art.
4. **The `fetched_at` value changes.** The committed `build.lock` gets a new `fetched_at` of 2026-10-17T04:53:16Z. Task 5's regenerated database will carry it as `client_fetched_at`, and no test pins the shipped value.
5. **The fetch used the placeholder User-Agent contact.** `WOWFOREVER_CONTACT` was unset, as in earlier runs. The run made four requests to wago.tools: `/api/builds`, the manifest table and the two BLPs that were not cached yet.
