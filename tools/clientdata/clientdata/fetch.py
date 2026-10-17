"""Fetch Blizzard client tables (CSV) and files (BLP) with cache and lock file."""
import argparse
import csv
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, NamedTuple, Optional

from .http import Client, FetchError
from .tables import FALLBACK_TABLES, REQUIRED_TABLES, fallback_name

# wow.tools.local's default port.
DEFAULT_BASE_URL = "http://localhost:5000"
TOOL_VERSION = "0.2"
USER_AGENT_PREFIX = "wow-forever-browser-pipeline/" + TOOL_VERSION
CONTACT_ENV = "WOWFOREVER_CONTACT"
# Sent when no contact is configured: a neutral hint, never personal data.
PLACEHOLDER_CONTACT = "contact: set " + CONTACT_ENV
BUILD_PATTERN = re.compile(r"\d+(\.\d+){3}")


def user_agent(contact: Optional[str] = None) -> str:
    """``wow-forever-browser-pipeline/<version> (+<contact>)``.

    The contact is ``contact`` (the ``--user-agent`` flag) when not blank, else the WOWFOREVER_CONTACT environment
    variable when not blank, else PLACEHOLDER_CONTACT.
    """
    for c in (contact, os.environ.get(CONTACT_ENV)):
        if c is not None and c.strip():
            return "%s (+%s)" % (USER_AGENT_PREFIX, c.strip())
    return "%s (+%s)" % (USER_AGENT_PREFIX, PLACEHOLDER_CONTACT)


class Build(NamedTuple):
    product: str
    build: str
    date: str


class BadBuildError(ValueError):
    """A build string that is not four dot-separated numbers; builds become cache paths, so it is refused."""


def check_build(build) -> str:
    """``build`` when it matches ``^\\d+(\\.\\d+){3}$`` (e.g. 1.60.1.70425); else BadBuildError."""
    if not isinstance(build, str) or not BUILD_PATTERN.fullmatch(build):
        raise BadBuildError("build %r is not four dot-separated numbers (like 1.60.1.70425)" % (build,))
    return build


def load_builds(client):
    """The parsed /api/builds listing (read once per run, shared by every resolve_build call)."""
    return json.loads(client.get_text("/api/builds"))


def resolve_build(client, product, build=None, builds=None) -> Build:
    """The pinned ``build`` or the product's newest listed build; either is checked with check_build."""
    data = builds if builds is not None else load_builds(client)
    # wago.tools returns {product: [entries]}; accept a flat list as well.
    if isinstance(data, dict):
        entries = list(data.get(product, []))
    else:
        entries = [e for e in data if e.get("product") == product]
    if build is not None:
        check_build(build)
        for e in entries:
            if e.get("version") == build:
                return Build(product, build, e.get("created_at", ""))
        return Build(product, build, "")
    if not entries:
        raise LookupError("no builds listed for product %r" % product)
    newest = max(entries, key=lambda e: e.get("created_at", ""))
    return Build(product, check_build(newest.get("version")), newest.get("created_at", ""))


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


def fetch_fallback(client, fallback_build, cache_dir, build, previous_fallback_build=None) -> Dict[str, Path]:
    """Fetch the FALLBACK_TABLES of the fallback product's build into the Forever build's csv directory.

    Each lands in ``csv/{Table}.era.csv`` and is returned under ``{Table}.era``. A cached file is reused unless it is
    empty or the cache's tables were fetched for a different fallback build (``previous_fallback_build``, read from
    the last build.lock), so a newer fallback build replaces them all and a table missing from the cache is fetched
    alone.
    """
    out = {}
    for table in FALLBACK_TABLES:
        name = fallback_name(table)
        path = Path(cache_dir) / build / "csv" / (name + ".csv")
        cached = path.exists() and path.stat().st_size > 0
        if not cached or previous_fallback_build != fallback_build:
            _write_atomic(path, client.get_bytes("/db2/%s/csv?build=%s" % (table, fallback_build)))
        out[name] = path
    return out


def read_lock(cache_dir, build):
    """The build's existing build.lock as a dict, or {} when there is none or it is unreadable."""
    path = Path(cache_dir) / build / "build.lock"
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


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


def _cached_file_count(cache_dir, build):
    files = Path(cache_dir) / build / "files"
    return sum(1 for p in files.glob("*.blp") if p.stat().st_size > 0) if files.is_dir() else 0


def write_lock(cache_dir, build, tables, files=None, product="", base_url="", missing_count=None, fallback=None):
    """Writes {cache_dir}/{build}/build.lock, merged into the build's existing lock.

    ``tables`` maps the names this run fetched (or verified) to their CSV paths; their sha256 replaces the
    existing lock's entries and every other existing table entry is kept, so a ``--tables`` or ``--fdids``
    run never drops what earlier runs recorded. ``fallback`` is the fallback Build (its product and build
    are recorded, and its tables' shas are expected in ``tables`` under FALLBACK_NAMES); without one the
    existing fallback keys are kept. ``file_count`` is the number of BLP files in the cache's files
    directory and ``missing_count`` the number of ids in missing.txt, whatever this run fetched; ``files``
    is accepted for compatibility and not counted.
    """
    previous = read_lock(cache_dir, build)
    if previous.get("build") not in (None, build):
        previous = {}
    if missing_count is None:
        missing_count = len(_read_missing(cache_dir, build))
    merged_tables = dict(previous.get("tables") or {})
    merged_tables.update({name: _sha256(p) for name, p in tables.items()})
    lock = {
        "product": product or previous.get("product", ""),
        "build": build,
        "fetched_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "base_url": base_url or previous.get("base_url", ""),
        "tables": dict(sorted(merged_tables.items())),
        "file_count": _cached_file_count(cache_dir, build),
        "missing_count": missing_count,
    }
    if fallback is not None:
        lock["fallback_product"] = fallback.product
        lock["fallback_build"] = fallback.build
    elif previous.get("fallback_product") and previous.get("fallback_build"):
        lock["fallback_product"] = previous["fallback_product"]
        lock["fallback_build"] = previous["fallback_build"]
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
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL,
                    help="wow.tools.local (default %s) or https://wago.tools" % DEFAULT_BASE_URL)
    ap.add_argument("--user-agent", metavar="CONTACT",
                    help="the contact part of the User-Agent, after the fixed '%s' prefix (default: the %s "
                         "environment variable, else a placeholder)" % (USER_AGENT_PREFIX, CONTACT_ENV))
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
    ap.add_argument("--fallback-product", metavar="PRODUCT",
                    help="also fetch %s of this product's build (e.g. wow_classic_era) into csv/{Table}.era.csv, "
                         "for items absent from the main ItemSparse" % ", ".join(FALLBACK_TABLES))
    ap.add_argument("--fallback-build", metavar="BUILD",
                    help="pin the fallback product's build (default newest)")
    args = ap.parse_args(argv)
    if args.fallback_build and not args.fallback_product:
        print("error: --fallback-build needs --fallback-product", file=sys.stderr)
        return 2

    cache = Path(args.cache).expanduser()
    client = Client(args.base_url, user_agent(args.user_agent), rate_per_second=args.rate)
    builds = load_builds(client)
    try:
        b = resolve_build(client, args.product, args.build, builds=builds)
    except (LookupError, BadBuildError) as e:
        print("error: --product: %s" % e, file=sys.stderr)
        return 2
    print("build: %s %s (%s)" % (b.product, b.build, b.date or "date unknown"))
    fb = None
    if args.fallback_product:
        try:
            fb = resolve_build(client, args.fallback_product, args.fallback_build, builds=builds)
        except (LookupError, BadBuildError) as e:
            print("error: --fallback-product: %s" % e, file=sys.stderr)
            return 2
        print("fallback build: %s %s (%s)" % (fb.product, fb.build, fb.date or "date unknown"))

    only = [t.strip() for t in args.tables.split(",") if t.strip()] if args.tables else None
    previous_lock = read_lock(cache, b.build)
    tables = fetch_tables(client, b.build, cache, only=only)
    print("tables cached: %d" % len(tables))
    if fb is not None:
        tables.update(fetch_fallback(client, fb.build, cache, b.build, previous_lock.get("fallback_build")))
        print("fallback %s cached from %s %s" % (", ".join(FALLBACK_TABLES), fb.product, fb.build))

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

    lock = write_lock(cache, b.build, tables, files, product=b.product, base_url=client.base_url, fallback=fb)
    print("lock: %s" % lock)
    return 0


if __name__ == "__main__":
    sys.exit(main())
