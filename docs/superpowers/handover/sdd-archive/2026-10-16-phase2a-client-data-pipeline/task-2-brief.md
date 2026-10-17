### Task 2: Python convert tool: icons and maps

**Files:**
- Create: `tools/clientdata/clientdata/convert.py`, `clientdata/icons.py`, `clientdata/maps.py`, `tools/clientdata/convert.py` (CLI shim), `tools/clientdata/tests/test_convert.py`, fixtures `tools/clientdata/tests/fixtures/icon.blp` (the real 134124 icon from `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\research\icons\134124.beta.blp`, 3.9 KB), `tiles/*.blp` (four real Westfall tiles from `…\research\westfall\`, ~45 KB each) and small CSV excerpts for `UiMapArt`, `UiMapArtTile`, `UiMapArtStyleLayer`, `UiMapXMapArt`, `UiMap`, `WorldMapOverlay`, `WorldMapOverlayTile` covering one map.

**Interfaces:**
- `icons.convert_icon(blp_path, out_png, size=56, crop_px=None) -> None`: Pillow opens the BLP, converts to RGBA, crops the in-game border (default crop 4 px of a 64 px source; constant documented), resizes with LANCZOS to `size`, saves PNG. `icons.fallback_png(out_png)`: a 56×56 question-mark PNG drawn with Pillow (no external asset).
- `maps.MapSpec(ui_map_id, art_id, rows, cols, base_tiles: dict[(row,col)] -> fdid, overlays: list[Overlay(offset_x, offset_y, rows, cols, tiles)], layer_w, layer_h)`; `maps.read_specs(csv_dir) -> list[MapSpec]` from the UiMap* and WorldMapOverlay* CSVs (chain in spec §4.1); `maps.compose(spec, files_dir) -> Image` stitches base tiles at `(col*256, row*256)`, alpha-composites every overlay tile at `(offset_x + col*256, offset_y + row*256)`, crops to `(layer_w, layer_h)` (1002×668 for style 1); `maps.save_jpeg(img, path, quality=85)`.
- CLI: `python convert.py --cache DIR --build B --out DIR`: writes `out/csv/*.csv` (copies), `out/icons/{fdid}.png` for every cached BLP referenced by `Item.IconFileDataID`, `out/icons/0.png` fallback, `out/maps/{ui_map_id}.jpg`, `out/maps.json` (`[{ui_map_id, name, width, height, tiles, overlays, missing_tiles}]`), and copies `build.lock`. Deterministic: PNG/JPEG written without timestamps.

- [ ] **Step 1: Write the failing tests**
```python
def test_icon_converts_to_56px_rgba(tmp_path): ...     # size (56,56), mode RGBA, corner pixel alpha > 0
def test_fallback_icon_is_56px(tmp_path): ...
def test_read_specs_builds_chain(fixture_csv_dir): ...  # one MapSpec: 3×4 base tiles, N overlays, layer 1002×668
def test_compose_stitches_and_crops(fixture): ...       # output size (1002,668); pixel at an overlay location differs from base-only compose
def test_missing_tile_is_recorded_not_fatal(fixture): ...
def test_convert_is_deterministic(tmp_path): ...        # two runs → identical bytes
```
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Implement** — Pillow's `BlpImagePlugin` reads BLP2 DXT1/3/5; if a tile fails to decode, record it in `missing_tiles` and leave the area transparent.
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit** — `git commit -m "feat(clientdata): convert icons to PNG and compose zone maps"`

---

