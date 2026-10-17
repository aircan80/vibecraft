"""Zone maps: UiMap chain CSVs -> composited tile images.

Chain: UiMap -> UiMapXMapArt(UiMapID, UiMapArtID) -> UiMapArt(ID, UiMapArtStyleID)
 -> UiMapArtStyleLayer(UiMapArtStyleID, LayerIndex 0: LayerWidth/Height) and
    UiMapArtTile(UiMapArtID, RowIndex, ColIndex, LayerIndex, FileDataID);
 overlays: WorldMapOverlay(ID, UiMapArtID, OffsetX, OffsetY) ->
    WorldMapOverlayTile(WorldMapOverlayID, RowIndex, ColIndex, LayerIndex, FileDataID).
Only LayerIndex 0 is used (all UiMapArtTile / WorldMapOverlayTile rows of the
build are layer 0; the style table has higher layers only for 3840x2560 styles).
"""
import csv
from pathlib import Path
from typing import Dict, List, NamedTuple, Tuple

from PIL import Image

TILE_PX = 256


class Overlay(NamedTuple):
    offset_x: int
    offset_y: int
    rows: int
    cols: int
    tiles: Dict[Tuple[int, int], int]


class MapSpec(NamedTuple):
    ui_map_id: int
    art_id: int
    name: str
    rows: int
    cols: int
    base_tiles: Dict[Tuple[int, int], int]
    overlays: List[Overlay]
    layer_w: int
    layer_h: int


def _rows(csv_dir, table):
    path = Path(csv_dir) / (table + ".csv")
    if not path.exists():
        return []
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def read_specs(csv_dir) -> List[MapSpec]:
    """One MapSpec per UiMap that has art with tiles, sorted by ui_map_id."""
    names = {int(r["ID"]): r["Name_lang"] for r in _rows(csv_dir, "UiMap")}
    style_of = {int(r["ID"]): int(r["UiMapArtStyleID"]) for r in _rows(csv_dir, "UiMapArt")}
    layer = {}
    for r in _rows(csv_dir, "UiMapArtStyleLayer"):
        if int(r["LayerIndex"]) == 0:
            layer[int(r["UiMapArtStyleID"])] = (int(r["LayerWidth"]), int(r["LayerHeight"]))
    tiles: Dict[int, Dict[Tuple[int, int], int]] = {}
    for r in _rows(csv_dir, "UiMapArtTile"):
        if int(r["LayerIndex"]) == 0:
            tiles.setdefault(int(r["UiMapArtID"]), {})[(int(r["RowIndex"]), int(r["ColIndex"]))] = \
                int(r["FileDataID"])
    otiles: Dict[int, Dict[Tuple[int, int], int]] = {}
    for r in _rows(csv_dir, "WorldMapOverlayTile"):
        if int(r["LayerIndex"]) == 0:
            otiles.setdefault(int(r["WorldMapOverlayID"]), {})[(int(r["RowIndex"]), int(r["ColIndex"]))] = \
                int(r["FileDataID"])
    overlays: Dict[int, List[Overlay]] = {}
    for r in sorted(_rows(csv_dir, "WorldMapOverlay"), key=lambda r: int(r["ID"])):
        t = otiles.get(int(r["ID"]))
        if not t:
            continue
        overlays.setdefault(int(r["UiMapArtID"]), []).append(Overlay(
            int(r["OffsetX"]), int(r["OffsetY"]),
            max(k[0] for k in t) + 1, max(k[1] for k in t) + 1, t))

    specs = []
    seen = set()
    for r in sorted(_rows(csv_dir, "UiMapXMapArt"), key=lambda r: (int(r["UiMapID"]), int(r["PhaseID"]))):
        ui_id, art_id = int(r["UiMapID"]), int(r["UiMapArtID"])
        if ui_id in seen or ui_id not in names or art_id not in tiles:
            continue
        w_h = layer.get(style_of.get(art_id))
        if w_h is None:
            continue
        seen.add(ui_id)  # first phase (lowest PhaseID) wins
        base = tiles[art_id]
        specs.append(MapSpec(ui_id, art_id, names[ui_id],
                             max(k[0] for k in base) + 1, max(k[1] for k in base) + 1,
                             base, overlays.get(art_id, []), w_h[0], w_h[1]))
    specs.sort(key=lambda s: s.ui_map_id)
    return specs


def _load_tile(files_dir, fdid):
    path = Path(files_dir) / ("%d.blp" % fdid)
    try:
        with Image.open(path) as im:
            im.load()
            return im.convert("RGBA")
    except Exception:
        return None


def compose(spec: MapSpec, files_dir):
    """Return (RGBA image of layer size, number of missing/undecodable tiles)."""
    canvas = Image.new("RGBA", (max(spec.layer_w, spec.cols * TILE_PX),
                                max(spec.layer_h, spec.rows * TILE_PX)), (0, 0, 0, 0))
    missing = 0
    for (row, col), fdid in sorted(spec.base_tiles.items()):
        tile = _load_tile(files_dir, fdid)
        if tile is None:
            missing += 1
            continue
        canvas.paste(tile, (col * TILE_PX, row * TILE_PX))
    for ov in spec.overlays:
        for (row, col), fdid in sorted(ov.tiles.items()):
            tile = _load_tile(files_dir, fdid)
            if tile is None:
                missing += 1
                continue
            _alpha_clipped(canvas, tile, ov.offset_x + col * TILE_PX, ov.offset_y + row * TILE_PX)
    return canvas.crop((0, 0, spec.layer_w, spec.layer_h)), missing


def _alpha_clipped(canvas, tile, x, y):
    """alpha_composite that tolerates tiles hanging over the canvas edge."""
    left, top = max(0, -x), max(0, -y)
    right, bottom = min(tile.width, canvas.width - x), min(tile.height, canvas.height - y)
    if right <= left or bottom <= top:
        return
    canvas.alpha_composite(tile.crop((left, top, right, bottom)), (x + left, y + top))


def save_jpeg(img, path, quality=85) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    bg = Image.new("RGB", img.size, (0, 0, 0))
    bg.paste(img, mask=img.getchannel("A"))
    bg.save(path, format="JPEG", quality=quality, optimize=True)
