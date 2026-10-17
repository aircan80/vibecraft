import hashlib
import io
import json
import urllib.error
import zipfile

import pytest

import fetch

COMMIT = "464179081673cfd240f7ffe7f0daf96bca0b5a70"
WORLD = b"SQLite format 3\x00 a fake world database"
LICENCE = b"GNU GENERAL PUBLIC LICENSE\nVersion 2, June 1991\n"


def fake_zip(members=None):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("sqlite-dump/", b"")
        for name, data in (members or {"sqlite-dump/mangos.sqlite": WORLD, "sqlite-dump/logs.sqlite": b"x"}).items():
            z.writestr(name, data)
    return buf.getvalue()


def write_lock(path, sha=None, licence_sha=None, asset="db-sqlite-4641790.zip", commit=COMMIT):
    path.write_text(json.dumps({
        "repo": "vmangos/core", "tag": "db_latest", "commit": commit, "asset": asset, "sha256": sha,
        "world_db": "sqlite-dump/mangos.sqlite", "license": {"path": "LICENSE", "sha256": licence_sha},
    }), encoding="utf-8")
    return path


class FakeWeb:
    """An ``urlopen`` stand-in: url -> list of answers (bytes, or an int HTTP status), consumed in order."""

    def __init__(self, routes):
        self.routes = {url: list(answers) for url, answers in routes.items()}
        self.requests = []

    def __call__(self, request, timeout=None):
        url = request.full_url
        self.requests.append((url, request.get_header("User-agent")))
        answers = self.routes.get(url)
        if not answers:
            raise AssertionError("unexpected request " + url)
        answer = answers.pop(0) if len(answers) > 1 else answers[0]
        if isinstance(answer, int):
            raise urllib.error.HTTPError(url, answer, "status %d" % answer, {}, None)
        return io.BytesIO(answer)


ASSET_URL = "https://github.com/vmangos/core/releases/download/db_latest/db-sqlite-4641790.zip"
LICENCE_URL = "https://raw.githubusercontent.com/vmangos/core/%s/LICENSE" % COMMIT


def web(archive=None):
    return FakeWeb({ASSET_URL: [archive or fake_zip()], LICENCE_URL: [LICENCE]})


def no_web(request, timeout=None):
    raise AssertionError("no download expected, got " + request.full_url)


def test_first_download_pins_both_hashes_and_extracts_only_the_world_db(tmp_path):
    lock = write_lock(tmp_path / "serverdb.lock")
    archive = fake_zip()
    w = web(archive)
    cache = fetch.fetch(lock, tmp_path / "cache", opener=w, log=lambda m: None)
    assert cache == tmp_path / "cache" / "4641790"
    assert (cache / "mangos.sqlite").read_bytes() == WORLD
    assert (cache / "LICENSE").read_bytes() == LICENCE
    assert sorted(p.name for p in cache.iterdir()) == ["LICENSE", "db-sqlite-4641790.zip", "mangos.sqlite",
                                                       "serverdb.lock"]
    pinned = json.loads(lock.read_text(encoding="utf-8"))
    assert pinned["sha256"] == hashlib.sha256(archive).hexdigest()
    assert pinned["license"]["sha256"] == hashlib.sha256(LICENCE).hexdigest()
    assert json.loads((cache / "serverdb.lock").read_text(encoding="utf-8")) == pinned
    assert [u for u, _ in w.requests] == [ASSET_URL, LICENCE_URL]
    assert all(ua.startswith("wow-forever-browser-pipeline/") for _, ua in w.requests)


def test_a_later_run_verifies_the_cache_against_the_lock_without_downloading(tmp_path):
    lock = write_lock(tmp_path / "serverdb.lock")
    fetch.fetch(lock, tmp_path / "cache", opener=web(), log=lambda m: None)
    before = lock.read_text(encoding="utf-8")
    (tmp_path / "cache" / "4641790" / "mangos.sqlite").unlink()
    cache = fetch.fetch(lock, tmp_path / "cache", opener=no_web, log=lambda m: None)
    assert (cache / "mangos.sqlite").read_bytes() == WORLD, "re-extracted from the cached archive"
    assert lock.read_text(encoding="utf-8") == before


def test_an_archive_that_differs_from_the_pin_is_refused_and_removed(tmp_path):
    lock = write_lock(tmp_path / "serverdb.lock", sha="0" * 64)
    with pytest.raises(fetch.ChecksumError, match="the lock pins 0000"):
        fetch.fetch(lock, tmp_path / "cache", opener=web(), log=lambda m: None)
    cache = tmp_path / "cache" / "4641790"
    assert not (cache / "db-sqlite-4641790.zip").exists()
    assert not (cache / "mangos.sqlite").exists()
    assert json.loads(lock.read_text(encoding="utf-8"))["sha256"] == "0" * 64


def test_a_licence_that_differs_from_the_pin_is_refused(tmp_path):
    lock = write_lock(tmp_path / "serverdb.lock", licence_sha="f" * 64)
    with pytest.raises(fetch.ChecksumError):
        fetch.fetch(lock, tmp_path / "cache", opener=web(), log=lambda m: None)
    assert not (tmp_path / "cache" / "4641790" / "LICENSE").exists()


def test_server_errors_are_retried_and_other_errors_fail(tmp_path):
    waits = []
    w = FakeWeb({"https://x/a": [503, 502, b"ok"]})
    fetch.download("https://x/a", tmp_path / "a", opener=w, sleep=waits.append)
    assert (tmp_path / "a").read_bytes() == b"ok"
    assert waits == [2, 4]
    with pytest.raises(fetch.FetchError, match="HTTP 503"):
        fetch.download("https://x/b", tmp_path / "b", opener=FakeWeb({"https://x/b": [503]}), sleep=waits.append)
    with pytest.raises(fetch.FetchError, match="HTTP 404"):
        fetch.download("https://x/c", tmp_path / "c", opener=FakeWeb({"https://x/c": [404]}), sleep=waits.append)
    assert waits == [2, 4, 2, 4, 8]
    assert not (tmp_path / "c").exists() and not (tmp_path / "c.part").exists()


def test_an_archive_without_the_world_db_fails(tmp_path):
    lock = write_lock(tmp_path / "serverdb.lock")
    with pytest.raises(fetch.FetchError, match="mangos.sqlite"):
        fetch.fetch(lock, tmp_path / "cache", opener=web(fake_zip({"other.sqlite": b"x"})), log=lambda m: None)


@pytest.mark.parametrize("asset,commit", [
    ("../db-sqlite-4641790.zip", COMMIT),
    ("db-sqlite-1234567.zip", COMMIT),
    ("db-4641790.zip", COMMIT),
    ("db-sqlite-4641790.zip", "4641790"),
])
def test_a_lock_that_would_make_odd_paths_is_refused(tmp_path, asset, commit):
    lock = write_lock(tmp_path / "serverdb.lock", asset=asset, commit=commit)
    with pytest.raises(fetch.LockError):
        fetch.fetch(lock, tmp_path / "cache", opener=no_web, log=lambda m: None)


def test_the_committed_lock_names_the_pinned_release():
    lock = fetch.load_lock(fetch.LOCK)
    assert lock["asset"] == "db-sqlite-4641790.zip"
    assert fetch.asset_url(lock) == ASSET_URL
    assert fetch.license_url(lock) == LICENCE_URL
