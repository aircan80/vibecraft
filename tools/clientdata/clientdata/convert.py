"""Convert cached client data into the import-ready output tree.

out/csv/*.csv, out/build.lock, out/icons/{fdid}.png, out/icons/0.png,
out/maps/{ui_map_id}.jpg, out/maps.json, out/ui_icons/{name}.png and out/ui_icons/{name}@24.png, and with a
hotfix snapshot in the lock out/csv/{Table}.hotfix.csv and out/hotfix.json;
--ui-icons-only, --tables A,B and --hotfix-only write only a part of it.
"""
import argparse
import csv
import hashlib
import json
import shutil
import sys
from pathlib import Path

from . import dbd, hotfix, icons, maps, ui_icons

HOTFIX_JSON = "hotfix.json"


def _icon_fdids(csv_dir):
    path = Path(csv_dir) / "Item.csv"
    if not path.exists():
        return None
    with open(path, newline="", encoding="utf-8") as f:
        return {int(r["IconFileDataID"]) for r in csv.DictReader(f)
                if r.get("IconFileDataID", "").strip().isdigit()}


def _lock_ui_icons(src):
    """The ``ui_icons`` entries of the cache's build.lock (written by fetch.py --ui-icons), or []."""
    try:
        lock = json.loads((src / "build.lock").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    return [e for e in lock.get("ui_icons") or [] if isinstance(e, dict)]


def convert_ui_icons(src, out):
    """``out/ui_icons/{name}.png`` and ``{name}@24.png`` for every ui_icons entry of the lock whose BLP is cached.

    A BLP that is not cached counts in ``ui_icons_missing``, one that does not decode in ``ui_icons_failed``; neither
    is fatal (the importer then has fewer rail icons and the browser falls back to its own glyphs).
    """
    n = n_missing = n_bad = 0
    for e in _lock_ui_icons(src):
        blp = src / "files" / ("%d.blp" % int(e["fdid"]))
        if not blp.exists():
            n_missing += 1
            print("ui icon %s: %s (%s) not cached" % (e["name"], e["path"], e["fdid"]), file=sys.stderr)
            continue
        try:
            ui_icons.convert_ui_icon(blp, out / "ui_icons", e["name"], e["path"])
            n += 1
        except Exception as ex:
            n_bad += 1
            print("ui icon %s: %d undecodable: %s" % (e["name"], int(e["fdid"]), ex), file=sys.stderr)
    return {"ui_icons": n, "ui_icons_missing": n_missing, "ui_icons_failed": n_bad}


def convert_tables(cache, build, out, tables):
    """Copies only ``csv/{Table}.csv`` of each of ``tables`` and the build.lock into ``out``; leaves the rest as it is.

    For tables fetched alone (``fetch.py --tables``) into a cache whose full conversion is already in ``out``.
    Raises FileNotFoundError naming the tables that are not cached, before writing anything.
    """
    src = Path(cache) / build
    out = Path(out)
    missing = [t for t in tables if not (src / "csv" / (t + ".csv")).is_file()]
    if missing:
        raise FileNotFoundError("not cached under %s: %s" % (src / "csv", ", ".join(missing)))
    (out / "csv").mkdir(parents=True, exist_ok=True)
    if (src / "build.lock").exists():
        shutil.copyfile(src / "build.lock", out / "build.lock")
    for t in tables:
        shutil.copyfile(src / "csv" / (t + ".csv"), out / "csv" / (t + ".csv"))
    return {"tables": len(tables)}


def _read_lock(src):
    try:
        return json.loads((Path(src) / "build.lock").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _sha256_file(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _csv_header(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return next(csv.reader(f))


def remove_hotfix(out):
    """Deletes the overlay CSVs and hotfix.json from ``out`` (a lock without a hotfix snapshot)."""
    out = Path(out)
    for t in hotfix.OVERLAY_TABLES:
        (out / "csv" / (hotfix.overlay_name(t) + ".csv")).unlink(missing_ok=True)
    (out / HOTFIX_JSON).unlink(missing_ok=True)


def convert_hotfix(cache, build, out):
    """The hotfix overlay of the lock's snapshots: ``out/csv/{Table}.hotfix.csv`` and ``out/hotfix.json``.

    Without a ``hotfix`` entry in the lock, stale overlay files are removed and ``{}`` is returned. The newest
    snapshot's header build must be ``build``'s (BuildMismatchError otherwise). Every snapshot and definition is
    checked against its recorded SHA-256 (ValueError). Each snapshot is decoded with the layout WoWDBDefs gives its
    own header build (same major version as ``build``); one whose layout differs from the current build's is skipped and counted in ``snapshots_skipped``. The overlay CSVs carry the base table's header, which must equal
    the layout's columns.
    """
    cache, out = Path(cache).expanduser(), Path(out)
    src = cache / build
    lock = _read_lock(src)
    entry = lock.get("hotfix")
    if not entry:
        remove_hotfix(out)
        return {}
    hotfix.check_build(entry["header_build"], build)
    prefix = build.rsplit(".", 1)[0]
    commit = entry["wowdbdefs_commit"]
    texts = {}
    for t in hotfix.OVERLAY_TABLES:
        p = cache / "wowdbdefs" / commit / ("%s.dbd" % t)
        if not p.is_file() or _sha256_file(p) != entry["dbd"].get(t):
            raise ValueError("definition %s missing or not the one build.lock pins (%s)" % (p, entry["dbd"].get(t)))
        texts[t] = p.read_text(encoding="utf-8")
    layouts, headers = {}, {}
    for t in hotfix.OVERLAY_TABLES:
        layouts[t] = dbd.layout(texts[t], build)
        base = src / "csv" / (t + ".csv")
        if not base.is_file():
            raise ValueError("base table %s is not cached; the overlay copies its header" % base)
        headers[t] = _csv_header(base)
        if dbd.columns(layouts[t]) != headers[t]:
            raise ValueError("WoWDBDefs %s layout for %s does not match the columns of %s" % (t, build, base))
    snapshots = []  # the bytes of each usable snapshot, oldest first
    skipped = 0
    for snap in entry.get("snapshots") or [entry]:
        p = cache / "hotfix" / ("%s.bin" % snap["sha256"])
        if not p.is_file() or _sha256_file(p) != snap["sha256"]:
            raise ValueError("hotfix snapshot %s missing or changed" % p)
        snap_build = "%s.%s" % (prefix, snap["header_build"])
        try:
            snap_layouts = {t: dbd.layout(texts[t], snap_build) for t in hotfix.OVERLAY_TABLES}
        except dbd.DbdError:
            skipped += 1
            continue
        if snap_layouts != layouts:
            skipped += 1
            continue
        snapshots.append(p.read_bytes())
    overlay = hotfix.build_overlay(snapshots, layouts)
    (out / "csv").mkdir(parents=True, exist_ok=True)
    tables = {}
    for t in hotfix.OVERLAY_TABLES:
        target = out / "csv" / (hotfix.overlay_name(t) + ".csv")
        hotfix.write_csv(target, headers[t], overlay[t].rows)
        o = overlay[t]
        tables[t] = {"rows": len(o.rows), "valid": o.valid, "invalidated": o.invalidated, "undone": o.undone,
                     "sha256": _sha256_file(target)}
    info = {k: entry[k] for k in ("url", "sha256", "header_build", "last_modified", "fetched_at") if k in entry}
    info.update({"build": build, "snapshots": len(entry.get("snapshots") or [entry]), "snapshots_skipped": skipped,
                 "wowdbdefs_commit": commit, "tables": tables})
    (out / HOTFIX_JSON).write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    return {"hotfix_" + t: tables[t]["rows"] for t in hotfix.OVERLAY_TABLES}


def convert(cache, build, out, ui_icons_only=False):
    src = Path(cache) / build
    out = Path(out)
    csv_dir, files_dir = src / "csv", src / "files"
    out.mkdir(parents=True, exist_ok=True)
    if (src / "build.lock").exists():
        shutil.copyfile(src / "build.lock", out / "build.lock")
    ui_summary = convert_ui_icons(src, out)
    if ui_icons_only:
        return ui_summary
    (out / "csv").mkdir(parents=True, exist_ok=True)
    for p in sorted(csv_dir.glob("*.csv")):
        shutil.copyfile(p, out / "csv" / p.name)
    hotfix_summary = convert_hotfix(cache, build, out)

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
            "missing_tiles": sum(e["missing_tiles"] for e in index), **ui_summary, **hotfix_summary}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Convert cached client data (BLP, CSV) for import.")
    ap.add_argument("--cache", required=True)
    ap.add_argument("--build", required=True)
    ap.add_argument("--out", required=True)
    only = ap.add_mutually_exclusive_group()
    only.add_argument("--ui-icons-only", action="store_true",
                      help="write only build.lock and ui_icons/ (the rail icons), leave the rest of OUT as it is")
    only.add_argument("--hotfix-only", action="store_true",
                      help="write only build.lock, the hotfix overlay (csv/{Table}.hotfix.csv) and hotfix.json, leave "
                           "the rest of OUT as it is")
    only.add_argument("--tables", metavar="A,B",
                      help="copy only these cached tables (csv/{Table}.csv) and build.lock, leave the rest of OUT as "
                           "it is")
    args = ap.parse_args(argv)
    cache = Path(args.cache).expanduser()
    if not (cache / args.build / "csv").is_dir():
        print("no cached csv for build %s under %s" % (args.build, cache), file=sys.stderr)
        return 2
    if args.hotfix_only:
        src = cache / args.build
        Path(args.out).mkdir(parents=True, exist_ok=True)
        try:
            summary = convert_hotfix(cache, args.build, args.out)
        except (hotfix.BuildMismatchError, ValueError) as e:
            print("error: %s" % e, file=sys.stderr)
            return 2
        if (src / "build.lock").exists():
            shutil.copyfile(src / "build.lock", Path(args.out) / "build.lock")
        print(json.dumps(summary))
        return 0
    if args.tables is not None:
        tables = [t.strip() for t in args.tables.split(",") if t.strip()]
        try:
            print(json.dumps(convert_tables(cache, args.build, args.out, tables)))
        except FileNotFoundError as e:
            print("error: %s" % e, file=sys.stderr)
            return 2
        return 0
    try:
        print(json.dumps(convert(cache, args.build, args.out, ui_icons_only=args.ui_icons_only)))
    except (hotfix.BuildMismatchError, ValueError) as e:
        print("error: %s" % e, file=sys.stderr)
        return 2
    return 0
