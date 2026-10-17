"""The hotfix cache reader, the WoWDBDefs layouts and the overlay (Task 19), on synthetic caches."""
import hashlib
import json
import struct
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pytest

from clientdata import convert, dbd, fetch, hotfix

BUILD = "1.60.1.70450"

# A made-up definition in the .dbd syntax: two layouts, the second for the builds under test.
SPARSE_DBD = """COLUMNS
int ID
locstring Display_lang
float DmgVariance
int ItemLevel
int StatModifier_bonusStat
int AllowableRace
int Old

LAYOUT 00000001
BUILD 1.15.9.70003
$noninline,id$ID<32>
Display_lang
Old<32>

LAYOUT 00000002
BUILD 1.60.1.70100-1.60.1.70425, 1.60.1.70450
COMMENT table is sparse
$noninline,id$ID<32>
Display_lang
DmgVariance
ItemLevel<u16>
StatModifier_bonusStat<32>[2] // budget slots
AllowableRace<32>
"""
SPARSE_COLUMNS = ["ID", "Display_lang", "DmgVariance", "ItemLevel", "StatModifier_bonusStat_0",
                  "StatModifier_bonusStat_1", "AllowableRace"]
ITEM_DBD = """COLUMNS
int ID
int ClassID
int IconFileDataID

LAYOUT 00000003
BUILD 1.60.1.70100-1.60.1.70450
$noninline,id$ID<32>
ClassID<32>
IconFileDataID<32>
"""
EFFECT_DBD = """COLUMNS
int ID
int SpellID

LAYOUT 00000004
BUILD 1.60.1.70100-1.60.1.70450
$noninline,id$ID<32>
SpellID<32>
"""
DBDS = {"ItemSparse": SPARSE_DBD, "Item": ITEM_DBD, "ItemEffect": EFFECT_DBD}
HEADERS = {"ItemSparse": SPARSE_COLUMNS, "Item": ["ID", "ClassID", "IconFileDataID"],
           "ItemEffect": ["ID", "SpellID"]}
SPARSE = hotfix.table_hash("ItemSparse")
ITEM = hotfix.table_hash("Item")


def sparse_row(name, dmg=0.6, ilvl=23, stats=(-1, 7), race=-1):
    return (name.encode() + b"\0" + struct.pack("<f", dmg) + struct.pack("<H", ilvl) + struct.pack("<ii", *stats)
            + struct.pack("<i", race))


def entry(table, rid, data=b"", push=100, status=hotfix.VALID, unique=7):
    return struct.pack("<4siiIIIIB3x", b"XFTH", 70, push, unique, table, rid, len(data), status) + data


def cache_bytes(*entries, build=70450, version=9):
    return struct.pack("<4sII", b"XFTH", version, build) + b"\0" * 32 + b"".join(entries)


def layouts(build=BUILD):
    return {t: dbd.layout(text, build) for t, text in DBDS.items()}


# --- table hash, header, entries ---------------------------------------------------------------------------------

def test_table_hash_is_sstrhash_of_the_name():
    assert hotfix.table_hash("ItemSparse") == 0x919BE54E
    assert hotfix.table_hash("Item") == 0x50238EC2
    assert hotfix.table_hash("itemsparse") == 0x919BE54E


def test_header_and_entries_in_file_order():
    data = cache_bytes(entry(SPARSE, 5, sparse_row("A")), entry(ITEM, 5, b"\x01" * 12, push=9, status=4))
    assert hotfix.header(data) == (9, 70450)
    got = list(hotfix.entries(data))
    assert [(e.table_hash, e.record_id, e.push, e.status) for e in got] == [(SPARSE, 5, 100, 1), (ITEM, 5, 9, 4)]
    assert got[1].data == b"\x01" * 12


@pytest.mark.parametrize("data, message", [
    (b"XFTH" + b"\0" * 10, "shorter"),
    (struct.pack("<4sII", b"NOPE", 9, 1) + b"\0" * 32, "magic"),
    (cache_bytes(version=8), "version 8"),
    (cache_bytes(entry(SPARSE, 1, b"abc"))[:-1], "past the end"),
    (cache_bytes() + b"XFTH", "cut short"),
])
def test_bad_caches_are_refused(data, message):
    with pytest.raises(hotfix.HotfixError, match=message):
        list(hotfix.entries(data))


# --- definitions -------------------------------------------------------------------------------------------------

def test_layout_picks_the_block_listing_the_build_and_expands_arrays():
    fields = dbd.layout(SPARSE_DBD, BUILD)
    assert dbd.columns(fields) == SPARSE_COLUMNS
    assert fields[0].is_id and not fields[0].inline
    assert dbd.columns(dbd.layout(SPARSE_DBD, "1.60.1.70200")) == SPARSE_COLUMNS  # inside the range
    assert dbd.columns(dbd.layout(SPARSE_DBD, "1.15.9.70003")) == ["ID", "Display_lang", "Old"]
    with pytest.raises(dbd.DbdError, match="no layout lists build 1.60.1.70451"):
        dbd.layout(SPARSE_DBD, "1.60.1.70451")


def test_decode_reads_fields_in_layout_order():
    row = dbd.decode(dbd.layout(SPARSE_DBD, BUILD), sparse_row("Rotmender's Garb", ilvl=60000, race=-2), 286978)
    assert row == {"ID": 286978, "Display_lang": "Rotmender's Garb", "DmgVariance": pytest.approx(0.6),
                   "ItemLevel": 60000, "StatModifier_bonusStat_0": -1, "StatModifier_bonusStat_1": 7,
                   "AllowableRace": -2}
    assert dbd.format_value(row["DmgVariance"]) == "0.60000002384"
    assert dbd.format_value(1.0) == "1" and dbd.format_value(-0.0) == "0"


def test_decode_refuses_a_record_of_another_size():
    fields = dbd.layout(SPARSE_DBD, BUILD)
    with pytest.raises(dbd.DbdError, match="reads 20 bytes, record has 21"):
        dbd.decode(fields, sparse_row("A") + b"\0", 1)
    with pytest.raises(dbd.DbdError, match="does not fit"):
        dbd.decode(fields, sparse_row("A")[:-3], 1)


# --- overlay -----------------------------------------------------------------------------------------------------

def test_overlay_applies_valid_rows_only_and_the_later_entry_wins():
    snap = cache_bytes(
        entry(SPARSE, 10, sparse_row("Old name"), push=200),
        entry(SPARSE, 10, sparse_row("New name"), push=50),  # later in the file, smaller push id
        entry(SPARSE, 11, b"", push=60, status=hotfix.NOT_PUBLIC),
        entry(SPARSE, 12, b"", push=61, status=hotfix.REMOVED),
        entry(ITEM, 10, struct.pack("<ii", 4, 132000), push=200),
        entry(hotfix.table_hash("Spell"), 10, b"\0\0\0", push=200))
    o = hotfix.build_overlay([snap], layouts())
    assert set(o["ItemSparse"].rows) == {10}
    assert o["ItemSparse"].rows[10]["Display_lang"] == "New name"
    assert o["ItemSparse"].valid == 1
    assert o["Item"].rows[10] == {"ID": 10, "ClassID": 4, "IconFileDataID": 132000}
    assert o["ItemEffect"].rows == {}


def test_an_invalidation_undoes_only_its_own_push():
    snap = cache_bytes(
        entry(SPARSE, 10, sparse_row("Push 1"), push=1),
        entry(SPARSE, 10, sparse_row("Push 2"), push=2),
        entry(SPARSE, 10, push=2, status=hotfix.INVALID),
        entry(SPARSE, 20, push=3, status=hotfix.INVALID))  # nothing of push 3 to undo
    o = hotfix.build_overlay([snap], layouts())["ItemSparse"]
    assert o.rows[10]["Display_lang"] == "Push 1"
    assert (o.valid, o.invalidated, o.undone) == (1, 2, 1)
    assert 20 not in o.rows


def test_rows_accumulate_across_snapshots_and_the_newest_wins():
    older = cache_bytes(entry(SPARSE, 10, sparse_row("Older"), push=5), entry(SPARSE, 30, sparse_row("Kept"), push=6))
    newer = cache_bytes(entry(SPARSE, 10, sparse_row("Newer"), push=5))  # omits 30
    o = hotfix.build_overlay([older, newer], layouts())["ItemSparse"]
    assert o.rows[10]["Display_lang"] == "Newer"
    assert o.rows[30]["Display_lang"] == "Kept"
    assert o.valid == 2


def test_check_build_names_the_build_watch():
    hotfix.check_build(70450, BUILD)
    with pytest.raises(hotfix.BuildMismatchError, match="70451 differs from build.lock build 1.60.1.70450.*"
                                                        "tools/build_watch.py"):
        hotfix.check_build(70451, BUILD)


def test_write_csv_in_wago_format(tmp_path):
    rows = hotfix.build_overlay([cache_bytes(entry(SPARSE, 7, sparse_row('Say "hi", friend')),
                                             entry(SPARSE, 3, sparse_row("", dmg=1.0)))], layouts())
    path = tmp_path / "ItemSparse.hotfix.csv"
    hotfix.write_csv(path, SPARSE_COLUMNS, rows["ItemSparse"].rows)
    assert path.read_bytes().decode() == (
        ",".join(SPARSE_COLUMNS) + "\n"
        + "3,,1,23,-1,7,-1\n"
        + '7,"Say ""hi"", friend",0.60000002384,23,-1,7,-1\n')


# --- convert -----------------------------------------------------------------------------------------------------

def _sha(b):
    return hashlib.sha256(b).hexdigest()


def make_cache(root: Path, snapshots, build=BUILD, header_builds=None):
    """A cache with the base CSV headers, the definitions, the snapshots and a lock naming them."""
    base = root / build
    (base / "csv").mkdir(parents=True)
    for t, cols in HEADERS.items():
        (base / "csv" / (t + ".csv")).write_text(",".join(cols) + "\n", encoding="utf-8")
    commit = "a" * 40
    dbd_dir = root / "wowdbdefs" / commit
    dbd_dir.mkdir(parents=True)
    for t, text in DBDS.items():
        (dbd_dir / (t + ".dbd")).write_text(text, encoding="utf-8", newline="\n")
    (root / "hotfix").mkdir()
    snaps = []
    for i, data in enumerate(snapshots):
        (root / "hotfix" / (_sha(data) + ".bin")).write_bytes(data)
        snaps.append({"url": "u", "sha256": _sha(data), "header_build": hotfix.header(data)[1],
                      "last_modified": "Sat, 17 Oct 2026 02:57:00 GMT", "fetched_at": "2026-10-17T1%d:00:00Z" % i,
                      "size": len(data)})
    entry_ = {**snaps[-1], "snapshots": snaps, "wowdbdefs_commit": commit,
              "dbd": {t: _sha((dbd_dir / (t + ".dbd")).read_bytes()) for t in DBDS}}
    (base / "build.lock").write_text(json.dumps({"product": "wow_classic_beta", "build": build, "fetched_at": "x",
                                                 "base_url": "b", "tables": {}, "hotfix": entry_}), encoding="utf-8")
    return root


def test_convert_writes_the_overlay_and_its_summary(tmp_path):
    snap = cache_bytes(entry(SPARSE, 286978, sparse_row("Rotmender's Garb")),
                       entry(SPARSE, 9479, push=112709, status=hotfix.INVALID),
                       entry(ITEM, 286978, struct.pack("<ii", 4, 135000)))
    cache = make_cache(tmp_path / "cache", [snap])
    out = tmp_path / "out"
    assert convert.main(["--cache", str(cache), "--build", BUILD, "--out", str(out), "--hotfix-only"]) == 0
    sparse = (out / "csv" / "ItemSparse.hotfix.csv").read_text(encoding="utf-8").splitlines()
    assert sparse == [",".join(SPARSE_COLUMNS), "286978,\"Rotmender's Garb\",0.60000002384,23,-1,7,-1"]
    assert (out / "csv" / "Item.hotfix.csv").read_text().splitlines()[1] == "286978,4,135000"
    assert (out / "csv" / "ItemEffect.hotfix.csv").read_text() == "ID,SpellID\n"
    info = json.loads((out / "hotfix.json").read_text())
    assert info["header_build"] == 70450 and info["build"] == BUILD and info["sha256"] == _sha(snap)
    assert info["last_modified"] == "Sat, 17 Oct 2026 02:57:00 GMT"
    assert info["tables"]["ItemSparse"]["valid"] == 1
    assert info["tables"]["ItemSparse"]["invalidated"] == 1
    assert info["tables"]["ItemSparse"]["sha256"] == _sha((out / "csv" / "ItemSparse.hotfix.csv").read_bytes())
    assert json.loads((out / "build.lock").read_text())["hotfix"]["sha256"] == _sha(snap)


def test_convert_accumulates_snapshots_and_skips_one_of_another_layout(tmp_path):
    era_layout = cache_bytes(entry(SPARSE, 1, b"x\0" + b"\0" * 4), build=70003)
    older = cache_bytes(entry(SPARSE, 30, sparse_row("Kept")), build=70425)
    newer = cache_bytes(entry(SPARSE, 10, sparse_row("New")))
    cache = make_cache(tmp_path / "cache", [era_layout, older, newer])
    info_out = tmp_path / "out"
    convert.convert_hotfix(cache, BUILD, info_out)
    ids = [line.split(",")[0] for line in (info_out / "csv" / "ItemSparse.hotfix.csv").read_text().splitlines()[1:]]
    assert ids == ["10", "30"]
    info = json.loads((info_out / "hotfix.json").read_text())
    assert (info["snapshots"], info["snapshots_skipped"]) == (3, 1)


def test_convert_fails_on_a_build_mismatch(tmp_path, capsys):
    cache = make_cache(tmp_path / "cache", [cache_bytes(build=70451)])
    assert convert.main(["--cache", str(cache), "--build", BUILD, "--out", str(tmp_path / "o"), "--hotfix-only"]) == 2
    assert "tools/build_watch.py" in capsys.readouterr().err
    assert not (tmp_path / "o" / "hotfix.json").exists()


def test_convert_refuses_a_definition_that_is_not_the_pinned_one(tmp_path, capsys):
    cache = make_cache(tmp_path / "cache", [cache_bytes()])
    (cache / "wowdbdefs" / ("a" * 40) / "Item.dbd").write_text(ITEM_DBD + "\n// edited\n")
    assert convert.main(["--cache", str(cache), "--build", BUILD, "--out", str(tmp_path / "o"), "--hotfix-only"]) == 2
    assert "not the one build.lock pins" in capsys.readouterr().err


def test_convert_without_a_snapshot_removes_a_stale_overlay(tmp_path):
    cache = make_cache(tmp_path / "cache", [cache_bytes()])
    lock_path = cache / BUILD / "build.lock"
    out = tmp_path / "out"
    convert.convert_hotfix(cache, BUILD, out)
    assert (out / "hotfix.json").exists()
    lock = json.loads(lock_path.read_text())
    del lock["hotfix"]
    lock_path.write_text(json.dumps(lock))
    assert convert.convert_hotfix(cache, BUILD, out) == {}
    assert not (out / "hotfix.json").exists()
    assert not list((out / "csv").glob("*.hotfix.csv"))


# --- fetch -------------------------------------------------------------------------------------------------------

class _Server:
    """Serves ``routes[path] = (status, body, headers)``; records request paths and User-Agents."""

    def __init__(self):
        self.routes, self.paths, self.agents = {}, [], []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                outer.paths.append(self.path)
                outer.agents.append(self.headers.get("User-Agent"))
                status, body, headers = outer.routes.get(self.path, (404, b"nope", {}))
                self.send_response(status)
                for k, v in headers.items():
                    self.send_header(k, v)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        self.httpd = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = "http://127.0.0.1:%d" % self.httpd.server_address[1]
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def close(self):
        self.httpd.shutdown()


@pytest.fixture
def server():
    s = _Server()
    yield s
    s.close()


def _serve_basics(server, cache_data, last_modified="Sat, 17 Oct 2026 02:57:00 GMT"):
    server.routes["/api/builds"] = (200, json.dumps({"wow_classic_beta": [
        {"version": BUILD, "created_at": "2026-10-16 22:29:00"}]}).encode(), {})
    server.routes["/db2/ItemClass/csv?build=%s" % BUILD] = (200, b"ID\n1\n", {})
    server.routes["/raidbots/DBCache.bin"] = (200, cache_data, {"Last-Modified": last_modified})
    for t, text in DBDS.items():
        server.routes[dbd.dbd_path(t, "b" * 40)] = (200, text.encode(), {})


def _run_fetch(server, cache):
    return fetch.main(["--product", "wow_classic_beta", "--base-url", server.url, "--cache", str(cache),
                       "--tables", "ItemClass", "--hotfix-cache", server.url + "/raidbots/DBCache.bin",
                       "--dbd-commit", "b" * 40, "--dbd-base-url", server.url])


def test_fetch_snapshots_the_cache_and_records_it_in_the_lock(server, tmp_path):
    data = cache_bytes(entry(SPARSE, 1, sparse_row("A")))
    _serve_basics(server, data)
    assert _run_fetch(server, tmp_path) == 0
    sha = _sha(data)
    assert (tmp_path / "hotfix" / (sha + ".bin")).read_bytes() == data
    lock = json.loads((tmp_path / BUILD / "build.lock").read_text())
    h = lock["hotfix"]
    assert (h["sha256"], h["header_build"], h["last_modified"]) == (sha, 70450, "Sat, 17 Oct 2026 02:57:00 GMT")
    assert h["fetched_at"].endswith("Z") and h["size"] == len(data)
    assert h["url"] == server.url + "/raidbots/DBCache.bin"
    assert h["wowdbdefs_commit"] == "b" * 40
    assert set(h["dbd"]) == {"ItemSparse", "Item", "ItemEffect"}
    assert [s["sha256"] for s in h["snapshots"]] == [sha]
    assert all(a.startswith("wow-forever-browser-pipeline/") for a in server.agents)

    # the same file again: known, not appended; a changed file: a second snapshot
    assert _run_fetch(server, tmp_path) == 0
    assert len(json.loads((tmp_path / BUILD / "build.lock").read_text())["hotfix"]["snapshots"]) == 1
    data2 = cache_bytes(entry(SPARSE, 2, sparse_row("B")))
    server.routes["/raidbots/DBCache.bin"] = (200, data2, {"Last-Modified": "Sun, 18 Oct 2026 02:57:00 GMT"})
    assert _run_fetch(server, tmp_path) == 0
    h = json.loads((tmp_path / BUILD / "build.lock").read_text())["hotfix"]
    assert [s["sha256"] for s in h["snapshots"]] == [sha, _sha(data2)]
    assert h["sha256"] == _sha(data2) and h["last_modified"] == "Sun, 18 Oct 2026 02:57:00 GMT"

    # and a later run without --hotfix-cache keeps the entry
    assert fetch.main(["--product", "wow_classic_beta", "--base-url", server.url, "--cache", str(tmp_path),
                       "--tables", "ItemClass"]) == 0
    assert json.loads((tmp_path / BUILD / "build.lock").read_text())["hotfix"]["sha256"] == _sha(data2)


def test_fetch_refuses_a_file_that_is_not_a_hotfix_cache(server, tmp_path, capsys):
    _serve_basics(server, b"<html>gone</html>" + b"\0" * 40)
    assert _run_fetch(server, tmp_path) == 2
    assert "magic" in capsys.readouterr().err
    assert not (tmp_path / "hotfix").exists()


def test_fetch_warns_when_the_cache_is_of_another_build(server, tmp_path, capsys):
    _serve_basics(server, cache_bytes(build=70451))
    assert _run_fetch(server, tmp_path) == 0
    assert "70451 differs from build.lock build 1.60.1.70450" in capsys.readouterr().err
