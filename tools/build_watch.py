"""Build watch: is any upstream of the shipped database newer than what it was built from?

    tools/clientdata/.venv/Scripts/python.exe -I tools/build_watch.py [--lock L] [--sources S]

Compares, read-only, at build time:

- the Blizzard patch service versions of ``wow_classic_beta`` and ``wowf`` with the ``build.lock`` build
- wago.tools' ``/api/builds`` (the newest build of the lock's product) with the ``build.lock`` build
- the Raidbots hotfix cache: its header build (a 44-byte range GET) with the ``build.lock`` build, and its
  Last-Modified with the snapshot the lock records
- the QuestieDB master commit with ``sources.properties``' pin
- AtlasLoot: always "manual check" with the pinned version, since CurseForge refuses automated requests

It prints one line per upstream (``fresh``, ``STALE``, ``unknown`` or ``manual check``) and exits 1 when anything is
stale, else 2 when an upstream could not be read (``unknown`` is never ``fresh``), else 0. It never imports,
fetches into a cache or updates anything.
"""
import argparse
import json
import struct
import sys
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Callable, Dict, List, NamedTuple, Optional

_TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(_TOOLS / "clientdata"))  # -I leaves the script directory off sys.path

from clientdata.fetch import _split_url, user_agent  # noqa: E402
from clientdata.http import Client  # noqa: E402
from clientdata.hotfix import HEADER_SIZE, RAIDBOTS_URL  # noqa: E402

PATCH_SERVICE = "https://us.version.battle.net/v2/products/%s/versions"
WAGO_BUILDS = "https://wago.tools/api/builds"
QUESTIEDB_MASTER = "https://api.github.com/repos/%s/%s/commits/master"
PRODUCTS = ("wow_classic_beta", "wowf")
DEFAULT_LOCK = _TOOLS / "clientdata" / "build.lock"
DEFAULT_SOURCES = _TOOLS.parent / "importer" / "src" / "main" / "resources" / "dev" / "wowforever" / "importer" \
    / "sources.properties"

FRESH, STALE, UNKNOWN, MANUAL = "fresh", "STALE", "unknown", "manual check"


class Line(NamedTuple):
    upstream: str
    status: str
    detail: str


class Response(NamedTuple):
    status: int
    headers: Dict[str, str]
    body: bytes


# get(url, headers) -> Response; raises on a network failure
Getter = Callable[[str, Optional[Dict[str, str]]], Response]


def client_getter(contact=None) -> Getter:
    """GETs through the pipeline's Client: its User-Agent, throttle and 5xx retries; one client per host."""
    clients = {}

    def get(url, headers=None):
        host, path = _split_url(url)
        if host not in clients:
            clients[host] = Client(host, user_agent(contact))
        r = clients[host].get_response(path, headers=headers, ok=(200, 206))
        return Response(r.status_code, dict(r.headers), r.content)

    return get


def read_properties(path) -> Dict[str, str]:
    """A Java .properties file: ``key=value`` lines, ``#`` comments, a trailing backslash continues the line."""
    out, pending = {}, ""
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = pending + raw.strip()
        if line.endswith("\\"):
            pending = line[:-1]
            continue
        pending = ""
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def _version(build):
    return tuple(int(p) for p in build.split("."))


def _compare(name, upstream_build, lock_build, extra=""):
    if _version(upstream_build) > _version(lock_build):
        return Line(name, STALE, "%s is newer than build.lock %s%s" % (upstream_build, lock_build, extra))
    if _version(upstream_build) < _version(lock_build):
        return Line(name, FRESH, "%s, older than build.lock %s%s" % (upstream_build, lock_build, extra))
    return Line(name, FRESH, "%s = build.lock%s" % (upstream_build, extra))


def _unknown(name, why):
    return Line(name, UNKNOWN, why)


def _ok(resp: Response, name: str, accept=(200,)):
    if resp.status not in accept:
        raise LookupError("HTTP %d" % resp.status)
    return resp


def parse_versions(text: str) -> Optional[str]:
    """``VersionsName`` of the ``us`` row (else the first row) of a patch service ``versions`` answer (BPSV)."""
    lines = [ln for ln in text.splitlines() if ln.strip() and not ln.startswith("##")]
    if not lines:
        return None
    names = [h.split("!", 1)[0] for h in lines[0].split("|")]
    if "VersionsName" not in names:
        return None
    rows = [dict(zip(names, ln.split("|"))) for ln in lines[1:]]
    row = next((r for r in rows if r.get("Region") == "us"), rows[0] if rows else None)
    return row.get("VersionsName") if row else None


def check_patch_service(get: Getter, product: str, lock: dict) -> Line:
    name = "patch service %s" % product
    try:
        version = parse_versions(_ok(get(PATCH_SERVICE % product, None), name).body.decode("utf-8", "replace"))
    except Exception as e:  # noqa: BLE001 - any failure to read is "unknown"
        return _unknown(name, "unreachable (%s)" % e)
    if not version:
        return _unknown(name, "no VersionsName in the answer")
    note = "" if product == lock["product"] else " (the lock follows %s)" % lock["product"]
    return _compare(name, version, lock["build"], note)


def check_wago(get: Getter, lock: dict) -> Line:
    name = "wago.tools builds"
    try:
        data = json.loads(_ok(get(WAGO_BUILDS, None), name).body)
        entries = data.get(lock["product"], []) if isinstance(data, dict) else \
            [e for e in data if e.get("product") == lock["product"]]
        newest = max(entries, key=lambda e: e.get("created_at", ""))["version"]
    except Exception as e:  # noqa: BLE001
        return _unknown(name, "unreadable (%s)" % e)
    return _compare(name, newest, lock["build"], " (%s)" % lock["product"])


def check_raidbots(get: Getter, lock: dict) -> Line:
    name = "Raidbots hotfix cache"
    entry = lock.get("hotfix") or {}
    url = entry.get("url") or RAIDBOTS_URL
    try:
        resp = _ok(get(url, {"Range": "bytes=0-%d" % (HEADER_SIZE - 1)}), name, accept=(200, 206))
        magic, _version_, build = struct.unpack_from("<4sII", resp.body, 0)
        if magic != b"XFTH":
            raise LookupError("not a hotfix cache (magic %r)" % magic)
    except Exception as e:  # noqa: BLE001
        return _unknown(name, "unreachable or unreadable (%s)" % e)
    modified = {k.lower(): v for k, v in resp.headers.items()}.get("last-modified", "")
    lock_build = lock["build"].rsplit(".", 1)[-1]
    if str(build) != lock_build:
        return Line(name, STALE, "header build %d, build.lock %s" % (build, lock["build"]))
    if not entry:
        return Line(name, STALE, "header build %d, Last-Modified %s; build.lock has no snapshot"
                    % (build, modified or "none"))
    try:
        newer = parsedate_to_datetime(modified) > parsedate_to_datetime(entry.get("last_modified", ""))
    except (TypeError, ValueError):
        return _unknown(name, "header build %d; cannot compare Last-Modified %r with the snapshot's %r"
                        % (build, modified, entry.get("last_modified")))
    if newer:
        return Line(name, STALE, "Last-Modified %s, snapshot %s %s" % (modified, entry["sha256"][:12],
                                                                      entry["last_modified"]))
    return Line(name, FRESH, "header build %d, Last-Modified %s = snapshot %s" % (build, modified,
                                                                                entry["sha256"][:12]))


def check_questiedb(get: Getter, sources: Dict[str, str]) -> Line:
    name = "QuestieDB master"
    pinned = sources.get("questiedb.commit", "")
    try:
        url = QUESTIEDB_MASTER % (sources["questiedb.owner"], sources["questiedb.repo"])
        sha = json.loads(_ok(get(url, {"Accept": "application/vnd.github+json"}), name).body)["sha"]
    except Exception as e:  # noqa: BLE001
        return _unknown(name, "unreachable (%s)" % e)
    if sha != pinned:
        return Line(name, STALE, "master %s, pinned %s" % (sha[:12], pinned[:12]))
    return Line(name, FRESH, "master %s = pinned" % sha[:12])


def check_atlasloot(sources: Dict[str, str]) -> Line:
    name = "AtlasLoot"
    if sources.get("atlasloot.kind") == "zip":
        return Line(name, MANUAL, "pinned %s %s (%s); CurseForge refuses automated checks, compare the files page "
                    "by hand: %s" % (sources.get("atlasloot.name", "AtlasLoot"), sources.get("atlasloot.version", "?"),
                                     sources.get("atlasloot.sha256", "?")[:12], sources.get("atlasloot.page", "?")))
    return Line(name, MANUAL, "pinned %s/%s %s; the AtlasLoot Classic Forever fork is not pinned yet"
                % (sources.get("atlasloot.owner", "?"), sources.get("atlasloot.repo", "?"),
                   sources.get("atlasloot.commit", "?")[:7]))


def watch(get: Getter, lock: dict, sources: Dict[str, str]) -> List[Line]:
    lines = [check_patch_service(get, p, lock) for p in PRODUCTS]
    lines += [check_wago(get, lock), check_raidbots(get, lock), check_questiedb(get, sources),
              check_atlasloot(sources)]
    return lines


def exit_code(lines: List[Line]) -> int:
    if any(ln.status == STALE for ln in lines):
        return 1
    if any(ln.status == UNKNOWN for ln in lines):
        return 2
    return 0


def main(argv=None, get: Optional[Getter] = None) -> int:
    ap = argparse.ArgumentParser(description="Report which upstreams moved past build.lock and sources.properties.")
    ap.add_argument("--lock", default=str(DEFAULT_LOCK), help="build.lock (default: the committed one)")
    ap.add_argument("--sources", default=str(DEFAULT_SOURCES), help="the importer's sources.properties")
    ap.add_argument("--user-agent", metavar="CONTACT", help="the contact part of the User-Agent (as fetch.py)")
    args = ap.parse_args(argv)
    lock = json.loads(Path(args.lock).read_text(encoding="utf-8"))
    sources = read_properties(args.sources)
    lines = watch(get or client_getter(args.user_agent), lock, sources)
    width = max(len(ln.upstream) for ln in lines)
    for ln in lines:
        print("%-*s  %-12s  %s" % (width, ln.upstream, ln.status, ln.detail))
    code = exit_code(lines)
    print({0: "all fresh", 1: "stale: rebuild the data (README, Rebuilding the database)",
           2: "nothing stale, but some upstreams are unknown"}[code])
    return code


if __name__ == "__main__":
    sys.exit(main())
