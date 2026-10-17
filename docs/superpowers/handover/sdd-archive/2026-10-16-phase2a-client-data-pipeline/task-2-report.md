# Task 2 report: convert tool

Commit f6a8e84 "feat(clientdata): convert icons to PNG and compose zone maps".

## Implemented
- `clientdata/icons.py`: `convert_icon` (BLP -> RGBA, crop `ICON_BORDER_PX`=4 of 64, LANCZOS 56, PNG), `fallback_png` (dark square, "?" via default font).
- `clientdata/maps.py`: `Overlay`, `MapSpec` (adds `name`), `read_specs`, `compose` (returns `(image, missing_count)`), `save_jpeg` (q85, optimize).
- `clientdata/convert.py` + `convert.py` shim: CLI per brief; Item.csv-less fallback converts every non-tile BLP (documented in README).
- README section added.

## Real CSV chain columns
- UiMap: Name_lang, ID, ...
- UiMapXMapArt: ID, PhaseID, UiMapArtID, UiMapID (lowest PhaseID wins)
- UiMapArt: ID, HighlightFileDataID, HighlightAtlasID, UiMapArtStyleID
- UiMapArtStyleLayer: ID, LayerIndex, LayerWidth, LayerHeight, TileWidth, TileHeight, ..., UiMapArtStyleID
- UiMapArtTile: ID, RowIndex, ColIndex, LayerIndex, FileDataID, UiMapArtID
- WorldMapOverlay: ID, UiMapArtID, TextureWidth, TextureHeight, OffsetX, OffsetY, HitRect*, ...
- WorldMapOverlayTile: ID, RowIndex, ColIndex, LayerIndex, FileDataID, WorldMapOverlayID
- All tile rows in the build are LayerIndex 0.

## TDD
- RED: collection error `No module named 'convert'`. GREEN: 20 passed (6 new). One intermediate failure was Windows path separators in the test tree comparison; fixed with `as_posix()`.

## Fixtures (~300 KB)
icon.blp, 6 tiles (4 Westfall base from row 0, 2 tiles of overlay 5292), CSV excerpts for Westfall (UiMap 1436, art 2165, all 12 base tile rows, overlay 5292 only). The 8 other base tiles are deliberately absent so the missing-tile path is covered (missing=8).

## Real conversion
Fetched the 8 map tables from wago.tools into the cache (no BLP files cached, no Item.csv). Output: 60 maps, 1659 missing tiles (all tiles, since no files are cached), 0 icons plus 0.png, `maps.json` and 60 JPEGs written. Scratch output: C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\out2

## Concerns
- Real tiles not exercised end to end beyond fixtures; a full run needs `fetch.py --maps`.
- Transparent missing areas become black in the JPEG.
- Overlay tile BLPs may be non-256 sized (e.g. 320x256 overlay uses 256 + 64 wide tiles); pasted at native size, clipped at canvas edge.
