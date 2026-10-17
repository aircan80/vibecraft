"""tools/build_watch.py on canned answers (no network)."""
import json
import struct

import pytest

import build_watch as bw

LOCK = {"product": "wow_classic_beta", "build": "1.60.1.70450",
        "hotfix": {"url": "https://example.test/DBCache.bin", "sha256": "ab" * 32, "header_build": 70450,
                   "last_modified": "Sat, 17 Oct 2026 02:57:00 GMT"}}
SOURCES = {"questiedb.owner": "Questie", "questiedb.repo": "QuestieDB", "questiedb.commit": "c" * 40,
           "atlasloot.owner": "Hoizame", "atlasloot.repo": "AtlasLootClassic", "atlasloot.commit": "8e99341e4e77"}
FORK = {**SOURCES, "atlasloot.kind": "zip", "atlasloot.name": "AtlasLoot Classic Forever",
        "atlasloot.version": "v1.2.3", "atlasloot.sha256": "f" * 64,
        "atlasloot.page": "https://www.curseforge.com/wow/addons/atlasloot-forever/files"}


def versions(build):
    return ("Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|KeyRing!HEX:16|BuildId!DEC:4|VersionsName!String:0"
            "|ProductConfig!HEX:16\n## seqn = 1\n"
            "eu|a|b||%s|%s|c\nus|a|b||%s|%s|c\n" % (build.split(".")[-1], "9.9.9.1", build.split(".")[-1], build))


def answers(beta="1.60.1.70450", wowf="1.60.1.70245", wago="1.60.1.70450", header=70450,
            modified="Sat, 17 Oct 2026 02:57:00 GMT", master="c" * 40):
    return {
        bw.PATCH_SERVICE % "wow_classic_beta": bw.Response(200, {}, versions(beta).encode()),
        bw.PATCH_SERVICE % "wowf": bw.Response(200, {}, versions(wowf).encode()),
        bw.WAGO_BUILDS: bw.Response(200, {}, json.dumps({"wow_classic_beta": [
            {"version": "1.60.1.70425", "created_at": "2026-10-16 00:50:05"},
            {"version": wago, "created_at": "2026-10-16 22:29:00"}]}).encode()),
        LOCK["hotfix"]["url"]: bw.Response(206, {"Last-Modified": modified},
                                           struct.pack("<4sII", b"XFTH", 9, header) + b"\0" * 32),
        bw.QUESTIEDB_MASTER % ("Questie", "QuestieDB"): bw.Response(200, {}, json.dumps({"sha": master}).encode()),
    }


def getter(table, down=()):
    calls = []

    def get(url, headers=None):
        calls.append((url, headers))
        if url in down:
            raise ConnectionError("no route to host")
        return table[url]

    get.calls = calls
    return get


def by_name(lines):
    return {ln.upstream: ln for ln in lines}


def test_everything_fresh_exits_zero_and_prints_one_line_per_upstream(capsys, tmp_path):
    lock = tmp_path / "build.lock"
    lock.write_text(json.dumps(LOCK))
    props = tmp_path / "sources.properties"
    props.write_text("# pins\nquestiedb.owner=Questie\nquestiedb.repo=QuestieDB\nquestiedb.commit=%s\n"
                     "questiedb.paths=\\\n  a.lua,\\\n  b.lua\natlasloot.owner=Hoizame\natlasloot.repo=AtlasLootClassic\n"
                     "atlasloot.commit=8e99341e4e779328460bf7684c0d5b22ce50ddf1\n" % ("c" * 40))
    assert bw.read_properties(props)["questiedb.paths"] == "a.lua,b.lua"
    get = getter(answers())
    assert bw.main(["--lock", str(lock), "--sources", str(props)], get=get) == 0
    out = capsys.readouterr().out.splitlines()
    assert len(out) == 7 and out[-1] == "all fresh"
    assert [ln[:30].rstrip() for ln in out[:6]] == [
        "patch service wow_classic_beta", "patch service wowf", "wago.tools builds", "Raidbots hotfix cache",
        "QuestieDB master", "AtlasLoot"]
    assert all(ln[32:].split("  ")[0].strip() == "fresh" for ln in out[:5])
    assert "manual check" in out[5] and "Hoizame/AtlasLootClassic 8e99341" in out[5]
    # the hotfix cache is read with a 44-byte range request, the header only
    assert (LOCK["hotfix"]["url"], {"Range": "bytes=0-43"}) in get.calls


def test_a_stale_upstream_exits_one_and_is_named():
    lines = by_name(bw.watch(getter(answers(beta="1.60.1.70500")), LOCK, SOURCES))
    assert lines["patch service wow_classic_beta"].status == bw.STALE
    assert "1.60.1.70500 is newer than build.lock 1.60.1.70450" in lines["patch service wow_classic_beta"].detail
    assert bw.exit_code(list(lines.values())) == 1


@pytest.mark.parametrize("kwargs, upstream, detail", [
    ({"wowf": "1.60.1.70600"}, "patch service wowf", "the lock follows wow_classic_beta"),
    ({"wago": "1.60.1.70500"}, "wago.tools builds", "1.60.1.70500 is newer"),
    ({"header": 70500}, "Raidbots hotfix cache", "header build 70500, build.lock 1.60.1.70450"),
    ({"modified": "Sun, 18 Oct 2026 06:00:00 GMT"}, "Raidbots hotfix cache", "snapshot abababababab"),
    ({"master": "d" * 40}, "QuestieDB master", "master dddddddddddd, pinned cccccccccccc"),
])
def test_each_upstream_can_be_stale(kwargs, upstream, detail):
    lines = by_name(bw.watch(getter(answers(**kwargs)), LOCK, SOURCES))
    assert lines[upstream].status == bw.STALE
    assert detail in lines[upstream].detail
    assert [ln.status for ln in lines.values()].count(bw.STALE) == 1


def test_an_unreachable_upstream_is_unknown_never_fresh():
    table = answers()
    lines = bw.watch(getter(table, down={bw.WAGO_BUILDS, LOCK["hotfix"]["url"]}), LOCK, SOURCES)
    named = by_name(lines)
    assert named["wago.tools builds"].status == bw.UNKNOWN
    assert named["Raidbots hotfix cache"].status == bw.UNKNOWN
    assert "no route to host" in named["wago.tools builds"].detail
    assert bw.exit_code(lines) == 2
    # an HTTP error or an unreadable answer is unknown too
    table[bw.QUESTIEDB_MASTER % ("Questie", "QuestieDB")] = bw.Response(403, {}, b"rate limited")
    table[bw.PATCH_SERVICE % "wowf"] = bw.Response(200, {}, b"<html>maintenance</html>")
    named = by_name(bw.watch(getter(table), LOCK, SOURCES))
    assert named["QuestieDB master"].status == bw.UNKNOWN
    assert named["patch service wowf"].status == bw.UNKNOWN
    # a stale upstream still wins over an unknown one
    lines = bw.watch(getter(answers(master="d" * 40), down={bw.WAGO_BUILDS}), LOCK, SOURCES)
    assert bw.exit_code(lines) == 1


def test_a_lock_without_a_snapshot_is_stale_for_raidbots():
    lock = {k: v for k, v in LOCK.items() if k != "hotfix"}
    table = answers()
    table[bw.RAIDBOTS_URL] = table.pop(LOCK["hotfix"]["url"])
    line = by_name(bw.watch(getter(table), lock, SOURCES))["Raidbots hotfix cache"]
    assert line.status == bw.STALE and "no snapshot" in line.detail


def test_the_fork_is_a_manual_check_with_its_pinned_version():
    line = bw.check_atlasloot(FORK)
    assert line.status == bw.MANUAL
    assert "AtlasLoot Classic Forever v1.2.3" in line.detail and "curseforge.com" in line.detail


def test_parse_versions_reads_the_us_row():
    assert bw.parse_versions(versions("1.60.1.70450")) == "1.60.1.70450"
    assert bw.parse_versions("Region!STRING:0|BuildId!DEC:4\nus|1\n") is None
    assert bw.parse_versions("") is None
