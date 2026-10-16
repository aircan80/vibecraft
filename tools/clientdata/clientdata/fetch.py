"""Fetch Blizzard client tables (CSV) and files (BLP) with cache and lock file."""
import argparse
import csv
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, NamedTuple, Optional

from .http import Client, FetchError
from .tables import REQUIRED_TABLES

DEFAULT_BASE_URL = "http://localhost:8080"
USER_AGENT = ("wowforever-item-browser-clientdata/0.1 "
              "(build-time fetch tool; everything cached, max 4 req/s)")


class Build(NamedTuple):
    product: str
    build: str
    date: str


def resolve_build(client, product, build=None) -> Build:
    data = json.loads(client.get_text("/api/builds"))
    # wago.tools returns {product: [entries]}; accept a flat list as well.
    if isinstance(data, dict):
        entries = list(data.get(product, []))
    else:
        entries = [e for e in data if e.get("product") == product]
    if build is not None:
        for e in entries:
            if e.get("version") == build:
                return Build(product, build, e.get("created_at", ""))
        return Build(product, build, "")
    if not entries:
        raise LookupError("no builds listed for product %r" % product)
    newest = max(entries, key=lambda e: e.get("created_at", ""))
    return Build(product, newest["version"], newest.get("created_at", ""))


def _write_atomic(path: Path, data: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".part")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def fetch_tables(client, build, cache_dir, only: Optional[Iterable[str]] = None):
    names = list(only) if only is not None else list(REQUIRED_TABLES)
    out = {}
    for name in names:
        path = Path(cache_dir) / build / "csv" / (name + ".csv")
        if not (path.exists() and path.stat().st_size > 0):
            _write_atomic(path, client.get_bytes("/db2/%s/csv?build=%s" % (name, build)))
        out[name] = path
    return out


def _missing_path(cache_dir, build):
    return Path(cache_dir) / build / "missing.txt"


def _read_missing(cache_dir, build):
    p = _missing_path(cache_dir, build)
    return set(p.read_text().split()) if p.exists() else set()


def fetch_files(client, product, fdids: Iterable[int], cache_dir, build=None, retry_missing=False):
    """Files land in cache_dir/{build}/files (cache_dir/files when build is None).

    FDIDs already recorded in missing.txt are skipped unless retry_missing is set.
    missing.txt is written even if a later request fails.
    """
    base = Path(cache_dir) / build if build else Path(cache_dir)
    out = {}
    missing = set(_read_missing(cache_dir, build)) if build else set()
    dirty = False
    try:
        for fdid in sorted(set(fdids)):
            path = base / "files" / ("%d.blp" % fdid)
            if path.exists() and path.stat().st_size > 0:
                out[fdid] = path
                continue
            if str(fdid) in missing and not retry_missing:
                continue
            try:
                data = client.get_bytes("/api/casc/%d?download&branch=%s" % (fdid, product))
            except FetchError as e:
                if e.status != 404:
                    raise
                if str(fdid) not in missing:
                    missing.add(str(fdid))
                    dirty = True
                continue
            _write_atomic(path, data)
            out[fdid] = path
            if str(fdid) in missing:
                missing.discard(str(fdid))
                dirty = True
    finally:
        if dirty:
            mp = base / "missing.txt"
            mp.parent.mkdir(parents=True, exist_ok=True)
            mp.write_text("".join(m + "\n" for m in sorted(missing, key=int)))
    return out


def _sha256(path: Path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_lock(cache_dir, build, tables, files, product="", base_url="", missing_count=None):
    if missing_count is None:
        missing_count = len(_read_missing(cache_dir, build))
    lock = {
        "product": product,
        "build": build,
        "fetched_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "base_url": base_url,
        "tables": {name: _sha256(p) for name, p in sorted(tables.items())},
        "file_count": len(files),
        "missing_count": missing_count,
    }
    path = Path(cache_dir) / build / "build.lock"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    return path


class MissingInputError(Exception):
    pass


def _column_ids(csv_path: Path, column: str, table: str = ""):
    ids = set()
    if not csv_path.exists():
        raise MissingInputError("%s not found at %s (fetch it first, do not filter it out with --tables)"
                                % (table or csv_path.name, csv_path))
    with open(csv_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            v = (row.get(column) or "").strip()
            if v.isdigit() and int(v) > 0:
                ids.add(int(v))
    return ids


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Fetch WoW client tables and files into a cache.")
    ap.add_argument("--product", required=True)
    ap.add_argument("--build")
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--tables", help="comma-separated subset of tables (default: all required)")
    ap.add_argument("--icons-from", metavar="ITEM_CSV",
                    help="Item.csv (path or name inside the cache); fetch every distinct IconFileDataID")
    ap.add_argument("--maps", action="store_true",
                    help="fetch UiMapArtTile and WorldMapOverlayTile FileDataIDs")
    ap.add_argument("--fdids", help="comma-separated FileDataIDs to fetch (e.g. a fixture's icons)")
    ap.add_argument("--retry-missing", action="store_true",
                    help="re-request files previously recorded as 404 in missing.txt")
    ap.add_argument("--rate", type=float, default=4.0, help="max requests per second (default 4)")
    args = ap.parse_args(argv)

    cache = Path(args.cache).expanduser()
    client = Client(args.base_url, USER_AGENT, rate_per_second=args.rate)
    b = resolve_build(client, args.product, args.build)
    print("build: %s %s (%s)" % (b.product, b.build, b.date or "date unknown"))

    only = [t.strip() for t in args.tables.split(",") if t.strip()] if args.tables else None
    tables = fetch_tables(client, b.build, cache, only=only)
    print("tables cached: %d" % len(tables))

    fdids = set()
    if args.fdids:
        parts = [x.strip() for x in args.fdids.split(",") if x.strip()]
        if not all(x.isdigit() for x in parts):
            print("error: --fdids takes comma-separated numbers, got %r" % args.fdids, file=sys.stderr)
            return 2
        fdids |= {int(x) for x in parts if int(x) > 0}
    try:
        if args.icons_from:
            p = Path(args.icons_from)
            if not p.exists():
                p = cache / b.build / "csv" / args.icons_from
            fdids |= _column_ids(p, "IconFileDataID", "Item")
        if args.maps:
            for name in ("UiMapArtTile", "WorldMapOverlayTile"):
                p = tables.get(name) or (cache / b.build / "csv" / (name + ".csv"))
                fdids |= _column_ids(p, "FileDataID", name)
    except MissingInputError as e:
        print("error: %s" % e, file=sys.stderr)
        return 2
    files = (fetch_files(client, b.product, fdids, cache, build=b.build, retry_missing=args.retry_missing)
             if fdids else {})
    if fdids:
        print("files cached: %d of %d" % (len(files), len(fdids)))

    lock = write_lock(cache, b.build, tables, files, product=b.product, base_url=client.base_url)
    print("lock: %s" % lock)
    return 0


if __name__ == "__main__":
    sys.exit(main())
