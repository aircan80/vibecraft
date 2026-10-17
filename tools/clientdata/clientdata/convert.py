"""Convert cached client data into the import-ready output tree.

out/csv/*.csv, out/build.lock, out/icons/{fdid}.png, out/icons/0.png,
out/maps/{ui_map_id}.jpg, out/maps.json
"""
import argparse
import csv
import json
import shutil
import sys
from pathlib import Path

from . import icons, maps


def _icon_fdids(csv_dir):
    path = Path(csv_dir) / "Item.csv"
    if not path.exists():
        return None
    with open(path, newline="", encoding="utf-8") as f:
        return {int(r["IconFileDataID"]) for r in csv.DictReader(f)
                if r.get("IconFileDataID", "").strip().isdigit()}


def convert(cache, build, out):
    src = Path(cache) / build
    out = Path(out)
    csv_dir, files_dir = src / "csv", src / "files"
    (out / "csv").mkdir(parents=True, exist_ok=True)
    for p in sorted(csv_dir.glob("*.csv")):
        shutil.copyfile(p, out / "csv" / p.name)
    if (src / "build.lock").exists():
        shutil.copyfile(src / "build.lock", out / "build.lock")

    specs = maps.read_specs(csv_dir)
    tile_ids = set()
    for s in specs:
        tile_ids.update(s.base_tiles.values())
        for o in s.overlays:
            tile_ids.update(o.tiles.values())

    # Icons: every cached BLP referenced by Item.IconFileDataID. Without Item.csv,
    # every cached BLP that is not a map tile is converted.
    wanted = _icon_fdids(csv_dir)
    n_icons = n_bad = 0
    for blp in sorted(files_dir.glob("*.blp")):
        if not blp.stem.isdigit():
            continue
        fdid = int(blp.stem)
        if wanted is None:
            if fdid in tile_ids:
                continue
        elif fdid not in wanted:
            continue
        try:
            icons.convert_icon(blp, out / "icons" / ("%d.png" % fdid))
            n_icons += 1
        except Exception as e:
            n_bad += 1
            print("icon %d undecodable: %s" % (fdid, e), file=sys.stderr)
    icons.fallback_png(out / "icons" / "0.png")

    index = []
    for s in specs:
        img, missing = maps.compose(s, files_dir)
        maps.save_jpeg(img, out / "maps" / ("%d.jpg" % s.ui_map_id))
        index.append({"ui_map_id": s.ui_map_id, "name": s.name, "width": s.layer_w,
                      "height": s.layer_h, "tiles": len(s.base_tiles),
                      "overlays": len(s.overlays), "missing_tiles": missing})
    index.sort(key=lambda e: e["ui_map_id"])
    (out / "maps.json").write_text(json.dumps(index, indent=1) + "\n", encoding="utf-8")
    return {"icons": n_icons, "icons_failed": n_bad, "maps": len(index),
            "missing_tiles": sum(e["missing_tiles"] for e in index)}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Convert cached client data (BLP, CSV) for import.")
    ap.add_argument("--cache", required=True)
    ap.add_argument("--build", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    cache = Path(args.cache).expanduser()
    if not (cache / args.build / "csv").is_dir():
        print("no cached csv for build %s under %s" % (args.build, cache), file=sys.stderr)
        return 2
    print(json.dumps(convert(cache, args.build, args.out)))
    return 0
