"""Fetch the pinned VMaNGOS world database (the official SQLite release asset) into a local cache.

    python fetch.py [--lock serverdb.lock] [--cache ~/.cache/wowforever-serverdb]

The lock (``serverdb.lock``, committed) names the GitHub repository, release tag, commit and asset. The asset is
downloaded once into ``<cache>/<short commit>/``; on the first download its SHA-256 is written into the lock, and every
later run verifies the cached file against it. Only the world database member (``world_db`` in the lock) is extracted,
as ``mangos.sqlite``; nothing in the archive is executed. The repository's GPL-2.0 licence at the pinned commit is
saved beside it as ``LICENSE`` (pinned the same way), and a copy of the lock as ``serverdb.lock`` for the importer.

Standard library only. The retry policy (three retries of an HTTP 5xx answer after 2, 4 and 8 seconds, any other
failure raises) is copied from tools/clientdata/clientdata/http.py, which needs ``requests``.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

LOCK = Path(__file__).resolve().with_name("serverdb.lock")
DEFAULT_CACHE_ROOT = Path.home() / ".cache" / "wowforever-serverdb"
WORLD_DB = "mangos.sqlite"
LICENSE = "LICENSE"
LOCK_COPY = "serverdb.lock"
USER_AGENT = "wow-forever-browser-pipeline/serverdb-0.1"
# Seconds to wait before each retry of a request answered with HTTP 5xx: three retries, then FetchError.
RETRY_DELAYS = (2, 4, 8)
ASSET_PATTERN = re.compile(r"db-sqlite-([0-9a-f]{7,40})\.zip")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
CHUNK = 1 << 20


class FetchError(Exception):
    """A failed download (``status`` is the HTTP status, or None with a ``reason``) or an unusable archive."""

    def __init__(self, url, status=None, reason=None):
        if status is not None:
            message = "GET %s failed with HTTP %s" % (url, status)
        else:
            message = "%s: %s" % (url, reason)
        super().__init__(message)
        self.url = url
        self.status = status


class ChecksumError(Exception):
    """A downloaded file whose SHA-256 differs from the one pinned in the lock."""


class LockError(ValueError):
    """A lock whose fields would make unsafe or ambiguous cache paths."""


def load_lock(path):
    lock = json.loads(Path(path).read_text(encoding="utf-8"))
    for key in ("repo", "tag", "commit", "asset", "world_db", "license"):
        if key not in lock:
            raise LockError("%s lacks %r" % (path, key))
    if not COMMIT_PATTERN.fullmatch(lock["commit"]):
        raise LockError("commit %r is not a full 40-digit hash" % (lock["commit"],))
    m = ASSET_PATTERN.fullmatch(lock["asset"])
    if not m or not lock["commit"].startswith(m.group(1)):
        raise LockError("asset %r is not db-sqlite-<commit>.zip for commit %s" % (lock["asset"], lock["commit"]))
    return lock


def write_lock(path, lock):
    Path(path).write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8", newline="\n")


def short_commit(lock):
    return ASSET_PATTERN.fullmatch(lock["asset"]).group(1)


def asset_url(lock):
    return "https://github.com/%s/releases/download/%s/%s" % (lock["repo"], lock["tag"], lock["asset"])


def license_url(lock):
    return "https://raw.githubusercontent.com/%s/%s/%s" % (lock["repo"], lock["commit"], lock["license"]["path"])


def _remove(*paths):
    for path in paths:
        if Path(path).exists():
            Path(path).unlink()


def download(url, dest, opener=urllib.request.urlopen, sleep=time.sleep, retry_delays=None):
    """Streams ``url`` into ``dest`` (through ``dest.part``); an HTTP 5xx is retried after each delay.

    Any other HTTP status, a network error (``URLError``) or a local I/O error (``OSError``) removes the partial file
    and raises FetchError.
    """
    delays = iter(RETRY_DELAYS if retry_delays is None else retry_delays)
    dest = Path(dest)
    part = dest.with_name(dest.name + ".part")
    while True:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with opener(request, timeout=120) as resp, open(part, "wb") as out:
                shutil.copyfileobj(resp, out, CHUNK)
            os.replace(part, dest)
            return dest
        except urllib.error.HTTPError as e:
            delay = next(delays, None) if 500 <= e.code < 600 else None
            if delay is None:
                _remove(part)
                raise FetchError(url, e.code) from e
            sleep(delay)
        except (urllib.error.URLError, OSError) as e:
            _remove(part)
            raise FetchError(url, reason=getattr(e, "reason", None) or e) from e


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(CHUNK), b""):
            h.update(block)
    return h.hexdigest()


def verify_or_pin(entry, path):
    """Checks ``path`` against ``entry["sha256"]``; when the entry has none yet, pins it. True when it pinned."""
    digest = sha256_file(path)
    expected = entry.get("sha256")
    if not expected:
        entry["sha256"] = digest
        return True
    if expected != digest:
        raise ChecksumError("%s has SHA-256 %s, the lock pins %s" % (path, digest, expected))
    return False


def extract_member(archive, member, dest):
    """Copies one named member of ``archive`` to ``dest`` (through ``dest.part``); no other member is touched."""
    dest = Path(dest)
    part = dest.with_name(dest.name + ".part")
    with zipfile.ZipFile(archive) as z:
        try:
            info = z.getinfo(member)
        except KeyError:
            raise FetchError("%s!%s" % (archive, member), reason="the archive has no such member") from None
        with z.open(info) as src, open(part, "wb") as out:
            shutil.copyfileobj(src, out, CHUNK)
    os.replace(part, dest)
    return dest


def fetch(lock_path=LOCK, cache_root=DEFAULT_CACHE_ROOT, opener=urllib.request.urlopen, sleep=time.sleep,
          log=print):
    """Downloads (once), verifies and extracts the pinned world database; returns the cache directory."""
    lock = load_lock(lock_path)
    cache = Path(cache_root) / short_commit(lock)
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / lock["asset"]
    downloaded = False
    if not archive.exists():
        log("downloading %s" % asset_url(lock))
        download(asset_url(lock), archive, opener=opener, sleep=sleep)
        downloaded = True
    world = cache / WORLD_DB
    derived = (world, cache / LOCK_COPY)  # never left beside an archive or licence that failed its pin
    try:
        pinned = verify_or_pin(lock, archive)
    except ChecksumError:
        _remove(archive, *derived)
        raise
    if downloaded or not world.exists():
        log("extracting %s" % lock["world_db"])
        extract_member(archive, lock["world_db"], world)
    licence = cache / LICENSE
    if not licence.exists():
        download(license_url(lock), licence, opener=opener, sleep=sleep)
    try:
        pinned = verify_or_pin(lock["license"], licence) or pinned
    except ChecksumError:
        _remove(licence, *derived)
        raise
    if pinned:
        write_lock(lock_path, lock)
        log("pinned the SHA-256 in %s" % lock_path)
    write_lock(cache / LOCK_COPY, lock)
    log("%s ready in %s" % (WORLD_DB, cache))
    return cache


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--lock", type=Path, default=LOCK, help="the pin file (default: %(default)s)")
    p.add_argument("--cache", type=Path, default=DEFAULT_CACHE_ROOT,
                   help="cache root; the data lands in <cache>/<short commit> (default: %(default)s)")
    args = p.parse_args(argv)
    try:
        fetch(args.lock, args.cache)
    except (FetchError, ChecksumError, LockError) as e:
        print("error: %s" % e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
