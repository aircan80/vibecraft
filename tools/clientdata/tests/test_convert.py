import json
import shutil
from pathlib import Path

import pytest
from PIL import Image

import convert as convert_cli
from clientdata import icons, maps

FIX = Path(__file__).parent / "fixtures"
BUILD = "1.60.1.70425"


@pytest.fixture
def csv_dir():
    return FIX / "csv"


@pytest.fixture
def files_dir(tmp_path):
    d = tmp_path / "files"
    d.mkdir()
    for p in (FIX / "tiles").glob("*.blp"):
        shutil.copy(p, d / p.name)
    return d


def test_icon_converts_to_56px_rgba(tmp_path):
    out = tmp_path / "i.png"
    icons.convert_icon(FIX / "icon.blp", out)
    with Image.open(out) as im:
        assert im.size == (56, 56)
        assert im.mode == "RGBA"
        assert im.getpixel((0, 0))[3] > 0
        assert im.getpixel((28, 28))[3] > 0


def test_fallback_icon_is_56px(tmp_path):
    out = tmp_path / "0.png"
    icons.fallback_png(out)
    with Image.open(out) as im:
        assert im.size == (56, 56)
        assert im.mode == "RGBA"
        pixels = set(im.get_flattened_data())
        assert len(pixels) >= 2  # background plus drawn question mark


def test_read_specs_builds_chain(csv_dir):
    specs = maps.read_specs(csv_dir)
    assert len(specs) == 1
    s = specs[0]
    assert (s.ui_map_id, s.art_id, s.name) == (1436, 2165, "Westfall")
    assert (s.rows, s.cols) == (3, 4)
    assert len(s.base_tiles) == 12
    assert s.base_tiles[(0, 0)] == 8067822
    assert s.base_tiles[(2, 3)] == 8067825
    assert (s.layer_w, s.layer_h) == (1002, 668)
    assert len(s.overlays) == 1
    o = s.overlays[0]
    assert (o.offset_x, o.offset_y) == (204, 260)
    assert (o.rows, o.cols) == (1, 2)
    assert o.tiles == {(0, 0): 8067794, (0, 1): 8067795}


def test_compose_stitches_and_crops(csv_dir, files_dir):
    spec = maps.read_specs(csv_dir)[0]
    img, missing = maps.compose(spec, files_dir)
    assert img.size == (1002, 668)
    # Only the first base row is available: 8 base tiles missing.
    assert missing == 8
    with Image.open(files_dir / "8067822.blp") as t:
        t.load()
        expect = t.convert("RGBA").getpixel((10, 10))
    assert img.getpixel((10, 10)) == expect
    # Overlay area differs from a base-only compose.
    base_only = maps.MapSpec(spec.ui_map_id, spec.art_id, spec.name, spec.rows, spec.cols,
                             spec.base_tiles, [], spec.layer_w, spec.layer_h)
    plain, _ = maps.compose(base_only, files_dir)
    diff = sum(1 for x in range(204, 460, 8) for y in range(260, 516, 8)
               if img.getpixel((x, y)) != plain.getpixel((x, y)))
    assert diff > 0


def test_missing_tile_is_recorded_not_fatal(csv_dir, tmp_path):
    empty = tmp_path / "none"
    empty.mkdir()
    spec = maps.read_specs(csv_dir)[0]
    img, missing = maps.compose(spec, empty)
    assert img.size == (1002, 668)
    assert missing == 14  # 12 base + 2 overlay tiles
    assert img.getpixel((5, 5))[3] == 0

    bad = tmp_path / "bad"
    bad.mkdir()
    (bad / "8067822.blp").write_bytes(b"not a blp")
    _, missing = maps.compose(spec, bad)
    assert missing == 14


def _make_cache(tmp_path):
    cache = tmp_path / "cache"
    b = cache / BUILD
    (b / "csv").mkdir(parents=True)
    (b / "files").mkdir()
    for p in (FIX / "csv").glob("*.csv"):
        shutil.copy(p, b / "csv" / p.name)
    (b / "csv" / "Item.csv").write_text(
        "ID,IconFileDataID\n17,134124\n18,999\n", encoding="utf-8")
    shutil.copy(FIX / "icon.blp", b / "files" / "134124.blp")
    for p in (FIX / "tiles").glob("*.blp"):
        shutil.copy(p, b / "files" / p.name)
    (b / "build.lock").write_text(json.dumps({"build": BUILD}), encoding="utf-8")
    return cache


def _tree_bytes(root):
    return {p.relative_to(root).as_posix(): p.read_bytes() for p in sorted(root.rglob("*")) if p.is_file()}


def test_convert_is_deterministic(tmp_path):
    cache = _make_cache(tmp_path)
    o1, o2 = tmp_path / "o1", tmp_path / "o2"
    assert convert_cli.main(["--cache", str(cache), "--build", BUILD, "--out", str(o1)]) == 0
    assert convert_cli.main(["--cache", str(cache), "--build", BUILD, "--out", str(o2)]) == 0
    t1, t2 = _tree_bytes(o1), _tree_bytes(o2)
    assert t1 == t2
    assert {"icons/134124.png", "icons/0.png", "maps/1436.jpg", "maps.json",
            "build.lock", "csv/Item.csv", "csv/UiMap.csv"} <= set(t1)
    assert "icons/999.png" not in t1  # referenced but not cached
    assert not any(k.startswith("icons/8067") for k in t1)  # map tiles are not icons
    data = json.loads(t1["maps.json"])
    assert data == [{"ui_map_id": 1436, "name": "Westfall", "width": 1002, "height": 668,
                     "tiles": 12, "overlays": 1, "missing_tiles": 8 + 0}]
