import hashlib
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from clientdata import fetch
from clientdata import http
from clientdata import tables
from clientdata.http import Client, FetchError

UA = "wowforever-clientdata-test/1.0 (+test)"
DEFAULT_RETRY_DELAYS = http.RETRY_DELAYS  # before no_retry_waits patches it

BUILDS = {
    "wow_classic_beta": [
        {"product": "wow_classic_beta", "version": "1.60.1.70100", "created_at": "2026-10-10 10:00:00"},
        {"product": "wow_classic_beta", "version": "1.60.1.70425", "created_at": "2026-10-16 00:50:05"},
    ],
    "wowf": [
        {"product": "wowf", "version": "1.60.1.70245", "created_at": "2026-10-13 21:18:10"},
    ],
}


class _Server:
    def __init__(self):
        self.routes = {}
        self.hits = 0
        self.paths = []
        self.headers = []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                outer.hits += 1
                outer.paths.append(self.path)
                outer.headers.append(dict(self.headers))
                route = outer.routes.get(self.path, (404, b"nope"))
                # a list of responses is served in order, its last one from then on
                status, body = (route.pop(0) if len(route) > 1 else route[0]) if isinstance(route, list) else route
                if isinstance(body, str):
                    body = body.encode()
                self.send_response(status)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        self.httpd = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = "http://127.0.0.1:%d" % self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def close(self):
        self.httpd.shutdown()
        self.httpd.server_close()


@pytest.fixture(autouse=True)
def no_retry_waits(monkeypatch):
    """Clients made by the tests (the CLI's too) retry 5xx without waiting."""
    monkeypatch.setattr(http, "RETRY_DELAYS", (0, 0, 0))


@pytest.fixture
def server():
    s = _Server()
    s.routes["/api/builds"] = (200, json.dumps(BUILDS))
    yield s
    s.close()


def make_client(server, rate=1000.0):
    return Client(server.url, UA, rate_per_second=rate)


def test_resolve_build_picks_newest_for_product(server):
    c = make_client(server)
    b = fetch.resolve_build(c, "wow_classic_beta")
    assert (b.product, b.build, b.date) == ("wow_classic_beta", "1.60.1.70425", "2026-10-16 00:50:05")
    assert fetch.resolve_build(c, "wowf").build == "1.60.1.70245"
    assert fetch.resolve_build(c, "wow_classic_beta", build="1.60.1.70100").build == "1.60.1.70100"


def test_resolve_build_accepts_flat_list(server):
    flat = BUILDS["wowf"] + BUILDS["wow_classic_beta"]
    server.routes["/api/builds"] = (200, json.dumps(flat))
    assert fetch.resolve_build(make_client(server), "wow_classic_beta").build == "1.60.1.70425"


def test_fetch_tables_skips_cached_files(server, tmp_path):
    build = "1.60.1.70425"
    for t in tables.REQUIRED_TABLES:
        server.routes["/db2/%s/csv?build=%s" % (t, build)] = (200, "ID,Name\n1,%s\n" % t)
    c = make_client(server)
    out = fetch.fetch_tables(c, build, tmp_path)
    assert len(tables.REQUIRED_TABLES) == 51
    assert set(out) == set(tables.REQUIRED_TABLES)
    assert out["Item"] == tmp_path / build / "csv" / "Item.csv"
    assert out["Item"].read_text() == "ID,Name\n1,Item\n"
    hits = server.hits
    assert hits == 51
    out2 = fetch.fetch_tables(c, build, tmp_path)
    assert server.hits == hits
    assert out2 == out


def test_required_tables_include_the_crafting_tables():
    # Task 8 (3A): reagents and skill ranks of the crafting spells; SkillRaceClassInfo exists in 1.60.1.70425
    assert {"SpellReagents", "SkillLineAbility", "SkillRaceClassInfo"} <= set(tables.REQUIRED_TABLES)


def test_required_tables_include_the_dungeon_tables():
    # Task 17 (3B): the Forever dungeons come from Map, LFGDungeons (name and ContentTuningID), ContentTuning
    # (MinLevelSquish) and DungeonEncounter (the bosses); they exist in 1.60.1.70425 and 1.60.1.70450
    assert {"Map", "LFGDungeons", "ContentTuning", "DungeonEncounter"} <= set(tables.REQUIRED_TABLES)


def test_fetch_tables_refetches_empty_file(server, tmp_path):
    build = "B"
    server.routes["/db2/ItemClass/csv?build=B"] = (200, "ID\n1\n")
    p = tmp_path / build / "csv" / "ItemClass.csv"
    p.parent.mkdir(parents=True)
    p.write_text("")
    fetch.fetch_tables(make_client(server), build, tmp_path, only=["ItemClass"])
    assert p.read_text() == "ID\n1\n"


def test_fetch_files_records_404_as_missing(server, tmp_path):
    server.routes["/api/casc/100?download&branch=wow_classic_beta"] = (200, b"BLP2data")
    c = make_client(server)
    out = fetch.fetch_files(c, "wow_classic_beta", [100, 101], tmp_path, build="B")
    assert set(out) == {100}
    assert out[100] == tmp_path / "B" / "files" / "100.blp"
    assert out[100].read_bytes() == b"BLP2data"
    assert (tmp_path / "B" / "missing.txt").read_text().split() == ["101"]
    hits = server.hits
    out2 = fetch.fetch_files(c, "wow_classic_beta", [100, 101], tmp_path, build="B")
    assert server.hits == hits and out2 == {100: out[100]}
    # deliberate retry re-requests the known-missing file
    server.routes["/api/casc/101?download&branch=wow_classic_beta"] = (200, b"now")
    out3 = fetch.fetch_files(c, "wow_classic_beta", [100, 101], tmp_path, build="B", retry_missing=True)
    assert server.hits == hits + 1 and set(out3) == {100, 101}
    assert (tmp_path / "B" / "missing.txt").read_text() == ""


def test_missing_txt_kept_when_later_request_fails(server, tmp_path):
    server.routes["/api/casc/2?download&branch=p"] = (500, "x")
    with pytest.raises(FetchError) as e:
        fetch.fetch_files(make_client(server), "p", [1, 2], tmp_path, build="B")
    assert server.url in str(e.value)
    assert (tmp_path / "B" / "missing.txt").read_text().split() == ["1"]


def test_non_404_error_raises(server):
    server.routes["/boom"] = (500, "x")
    with pytest.raises(FetchError) as e:
        make_client(server).get_text("/boom")
    assert e.value.status == 500 and e.value.path == "/boom"


def test_retry_delays_default_to_2_4_8_seconds():
    assert DEFAULT_RETRY_DELAYS == (2, 4, 8)
    assert http.Client("http://x", UA)._retry_delays == (0, 0, 0)  # read when made: patched for the tests


def test_transient_5xx_is_retried_with_backoff(server):
    server.routes["/flaky"] = [(500, "x"), (503, "x"), (200, "ok")]
    waits = []
    c = Client(server.url, UA, rate_per_second=1000.0, retry_delays=(2, 4, 8), sleep=waits.append)
    assert c.get_text("/flaky") == "ok"
    assert server.hits == 3 and waits == [2, 4]


def test_persistent_5xx_raises_after_three_retries(server):
    server.routes["/down"] = (502, "x")
    waits = []
    c = Client(server.url, UA, rate_per_second=1000.0, retry_delays=(2, 4, 8), sleep=waits.append)
    with pytest.raises(FetchError) as e:
        c.get_bytes("/down")
    assert e.value.status == 502
    assert server.hits == 4 and waits == [2, 4, 8]


def test_404_and_other_4xx_are_not_retried(server):
    waits = []
    c = Client(server.url, UA, rate_per_second=1000.0, retry_delays=(2, 4, 8), sleep=waits.append)
    server.routes["/gone"] = (403, "x")
    for path, status in (("/nope", 404), ("/gone", 403)):
        with pytest.raises(FetchError) as e:
            c.get_text(path)
        assert e.value.status == status
    assert server.hits == 2 and waits == []


def test_icon_download_survives_a_transient_500(server, tmp_path):
    server.routes["/api/casc/1?download&branch=p"] = [(500, "x"), (200, b"BLP2")]
    out = fetch.fetch_files(make_client(server), "p", [1], tmp_path, build="B")
    assert out[1].read_bytes() == b"BLP2"
    assert not (tmp_path / "B" / "missing.txt").exists()


def test_throttle_respects_rate(server):
    server.routes["/x"] = (200, "ok")
    c = make_client(server, rate=4.0)
    t0 = time.monotonic()
    for _ in range(8):
        c.get_text("/x")
    assert time.monotonic() - t0 >= 1.5


def test_lock_file_contents(tmp_path):
    csv_dir = tmp_path / "B" / "csv"
    csv_dir.mkdir(parents=True)
    f = csv_dir / "ItemClass.csv"
    f.write_bytes(b"ID\n1\n")
    blp = tmp_path / "B" / "files" / "7.blp"
    blp.parent.mkdir()
    blp.write_bytes(b"x")
    (tmp_path / "B" / "missing.txt").write_text("8\n9\n")
    p = fetch.write_lock(tmp_path, "B", {"ItemClass": f}, {7: blp},
                         product="wow_classic_beta", base_url="https://wago.tools")
    assert p == tmp_path / "B" / "build.lock"
    lock = json.loads(p.read_text())
    assert set(lock) == {"product", "build", "fetched_at", "base_url", "tables", "file_count", "missing_count"}
    assert lock["product"] == "wow_classic_beta" and lock["build"] == "B"
    assert lock["base_url"] == "https://wago.tools"
    assert lock["tables"] == {"ItemClass": hashlib.sha256(b"ID\n1\n").hexdigest()}
    assert lock["file_count"] == 1 and lock["missing_count"] == 2


def test_lock_merges_into_the_existing_lock(tmp_path):
    csv_dir = tmp_path / "B" / "csv"
    csv_dir.mkdir(parents=True)
    a, b, era = csv_dir / "ItemClass.csv", csv_dir / "Spell.csv", csv_dir / "ItemSparse.era.csv"
    a.write_bytes(b"ID\n1\n")
    b.write_bytes(b"ID\n2\n")
    era.write_bytes(b"ID\n3\n")
    fb = fetch.Build("wow_classic_era", "1.15.9.70003", "")
    fetch.write_lock(tmp_path, "B", {"ItemClass": a, tables.FALLBACK_NAME: era}, {},
                     product="wow_classic_beta", base_url="https://wago.tools", fallback=fb)
    files = tmp_path / "B" / "files"
    files.mkdir()
    for fdid in (7, 8, 9):
        (files / ("%d.blp" % fdid)).write_bytes(b"x")
    (tmp_path / "B" / "missing.txt").write_text("10\n")
    # a later run that fetched one other table and one file, without --fallback-product
    a.write_bytes(b"ID\n1\nchanged on disk, not re-fetched\n")
    p = fetch.write_lock(tmp_path, "B", {"Spell": b}, {9: files / "9.blp"},
                         product="wow_classic_beta", base_url="https://wago.tools")
    lock = json.loads(p.read_text())
    assert lock["tables"] == {
        "ItemClass": hashlib.sha256(b"ID\n1\n").hexdigest(),          # kept from the first run
        tables.FALLBACK_NAME: hashlib.sha256(b"ID\n3\n").hexdigest(),
        "Spell": hashlib.sha256(b"ID\n2\n").hexdigest(),
    }
    assert (lock["fallback_product"], lock["fallback_build"]) == ("wow_classic_era", "1.15.9.70003")
    assert lock["file_count"] == 3 and lock["missing_count"] == 1
    # a run that refreshes a table replaces its hash
    p = fetch.write_lock(tmp_path, "B", {"ItemClass": a}, {}, product="wow_classic_beta", base_url="u")
    assert json.loads(p.read_text())["tables"]["ItemClass"] == hashlib.sha256(a.read_bytes()).hexdigest()


def test_fdids_run_keeps_the_tables_and_fallback_of_earlier_runs(server, tmp_path):
    _era_server(server)
    server.routes["/api/casc/5?download&branch=wow_classic_beta"] = (200, b"BLP2")
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era", "--fallback-build", "1.15.9.70003") == 0
    first = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert fetch.main(["--product", "wow_classic_beta", "--base-url", server.url, "--cache", str(tmp_path),
                       "--tables", "ItemClass", "--fdids", "5", "--rate", "1000"]) == 0
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert lock["tables"] == first["tables"] and set(lock["tables"]) == {"ItemClass", *tables.FALLBACK_NAMES}
    assert lock["fallback_build"] == "1.15.9.70003" and lock["file_count"] == 1


def test_user_agent_header_sent(server):
    server.routes["/x"] = (200, "ok")
    make_client(server).get_text("/x")
    assert server.headers[0]["User-Agent"] == UA


def test_cli_end_to_end(server, tmp_path):
    server.routes["/db2/ItemClass/csv?build=1.60.1.70425"] = (200, "ID\n1\n")
    rc = fetch.main(["--product", "wow_classic_beta", "--base-url", server.url,
                     "--cache", str(tmp_path), "--tables", "ItemClass"])
    assert rc == 0
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert list(lock["tables"]) == ["ItemClass"]




def _cli(server, tmp_path, *extra):
    server.routes["/db2/ItemClass/csv?build=1.60.1.70425"] = (200, "ID\n1\n")
    return fetch.main(["--product", "wow_classic_beta", "--base-url", server.url,
                       "--cache", str(tmp_path), "--tables", "ItemClass", "--rate", "1000", *extra])


def _csv(tmp_path, name, text):
    p = tmp_path / "1.60.1.70425" / "csv" / (name + ".csv")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def _fetched_fdids(server):
    return sorted(int(x.split("?")[0].rsplit("/", 1)[1]) for x in server.paths if x.startswith("/api/casc/"))


def test_icons_from_fetches_distinct_positive_ids(server, tmp_path):
    _csv(tmp_path, "Item", 'ID,Name,IconFileDataID\n1,"A, quoted",100\n2,"B ""x""",100\n3,C,\n4,D,0\n5,E,200\n')
    assert _cli(server, tmp_path, "--icons-from", "Item.csv") == 0
    assert _fetched_fdids(server) == [100, 200]


def test_maps_unions_both_tile_tables(server, tmp_path):
    _csv(tmp_path, "UiMapArtTile", "ID,FileDataID\n1,10\n2,20\n3,0\n")
    _csv(tmp_path, "WorldMapOverlayTile", "ID,FileDataID\n1,20\n2,30\n")
    assert _cli(server, tmp_path, "--maps") == 0
    assert _fetched_fdids(server) == [10, 20, 30]


def test_maps_with_missing_csv_fails_clearly(server, tmp_path, capsys):
    _csv(tmp_path, "UiMapArtTile", "ID,FileDataID\n1,10\n")
    assert _cli(server, tmp_path, "--maps") == 2
    err = capsys.readouterr().err
    assert "WorldMapOverlayTile" in err and "Traceback" not in err
    assert _fetched_fdids(server) == []


def test_fdids_fetches_exactly_the_listed_ids(server, tmp_path):
    assert _cli(server, tmp_path, "--fdids", "300,100,100") == 0
    assert _fetched_fdids(server) == [100, 300]


def test_fdids_rejects_non_numbers(server, tmp_path, capsys):
    assert _cli(server, tmp_path, "--fdids", "1,x") == 2
    assert "--fdids" in capsys.readouterr().err


ERA_BUILDS = [
    {"product": "wow_classic_era", "version": "1.15.8.65000", "created_at": "2026-09-01 10:00:00"},
    {"product": "wow_classic_era", "version": "1.15.9.70003", "created_at": "2026-10-14 08:00:00"},
]
ERA_SPARSE = "ID,Display_lang,ItemLevel\n16800,Arcanist Boots,66\n"
# Era 1.15.9 has no ItemXItemEffect table: its ItemEffect links each effect to its item by ParentItemID.
ERA_TABLES = ("ItemSparse", "ItemEffect")


def _era_body(table, version):
    return "ID,Table,Build\n1,%s,%s\n" % (table, version)


def _era_server(server, builds=ERA_BUILDS):
    data = dict(BUILDS)
    data["wow_classic_era"] = builds
    server.routes["/api/builds"] = (200, json.dumps(data))
    for b in builds:
        server.routes["/db2/ItemSparse/csv?build=%s" % b["version"]] = (200, ERA_SPARSE.replace("66", b["version"][-2:]))
        for t in ERA_TABLES[1:]:
            server.routes["/db2/%s/csv?build=%s" % (t, b["version"])] = (200, _era_body(t, b["version"]))


def _era_requests(server):
    """Requests for a fallback (Era, 1.15.x) table, in order."""
    return [p for p in server.paths if p.startswith("/db2/") and "?build=1.15." in p]


def test_fallback_tables_are_item_sparse_and_the_item_effect_tables():
    assert tables.FALLBACK_TABLES == ERA_TABLES
    assert tables.FALLBACK_NAMES == ("ItemSparse.era", "ItemEffect.era")
    assert tables.FALLBACK_NAME == "ItemSparse.era"


def test_fallback_product_fetches_the_era_tables_beside_the_forever_tables(server, tmp_path):
    _era_server(server)
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 0
    csv_dir = tmp_path / "1.60.1.70425" / "csv"
    assert (csv_dir / "ItemSparse.era.csv").read_text() == ERA_SPARSE.replace("66", "03")
    for t in ERA_TABLES[1:]:
        assert (csv_dir / (t + ".era.csv")).read_text() == _era_body(t, "1.15.9.70003")
    assert _era_requests(server) == ["/db2/%s/csv?build=1.15.9.70003" % t for t in ERA_TABLES]
    assert [p for p in server.paths if p.startswith("/db2/")][0] == "/db2/ItemClass/csv?build=1.60.1.70425"
    assert server.paths.count("/api/builds") == 1
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert lock["product"] == "wow_classic_beta" and lock["build"] == "1.60.1.70425"
    assert lock["fallback_product"] == "wow_classic_era" and lock["fallback_build"] == "1.15.9.70003"
    for t in ERA_TABLES:
        era = csv_dir / (t + ".era.csv")
        assert lock["tables"][t + ".era"] == hashlib.sha256(era.read_bytes()).hexdigest()
    assert set(lock["tables"]) == {"ItemClass", "ItemSparse.era", "ItemEffect.era"}
    assert not any("ItemXItemEffect" in p for p in server.paths)


def test_fallback_is_cached_until_the_fallback_build_changes(server, tmp_path):
    _era_server(server)
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 0
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 0
    assert len(_era_requests(server)) == len(ERA_TABLES)
    newer = ERA_BUILDS + [{"product": "wow_classic_era", "version": "1.15.9.71111",
                           "created_at": "2026-10-15 08:00:00"}]
    _era_server(server, newer)
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 0
    assert _era_requests(server)[len(ERA_TABLES):] == ["/db2/%s/csv?build=1.15.9.71111" % t for t in ERA_TABLES]
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert lock["fallback_build"] == "1.15.9.71111"
    csv_dir = tmp_path / "1.60.1.70425" / "csv"
    assert (csv_dir / "ItemEffect.era.csv").read_text() == _era_body("ItemEffect", "1.15.9.71111")


def test_only_the_missing_era_tables_download_beside_a_cached_era_item_sparse(server, tmp_path):
    """The upgrade path: a cache from before the effect tables joined the fallback list."""
    _era_server(server)
    csv_dir = tmp_path / "1.60.1.70425" / "csv"
    csv_dir.mkdir(parents=True)
    (csv_dir / "ItemSparse.era.csv").write_bytes(b"ID\ncached\n")
    (tmp_path / "1.60.1.70425" / "build.lock").write_text(json.dumps({
        "product": "wow_classic_beta", "build": "1.60.1.70425", "tables": {"ItemSparse.era": "x"},
        "fallback_product": "wow_classic_era", "fallback_build": "1.15.9.70003"}))
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 0
    assert _era_requests(server) == ["/db2/ItemEffect/csv?build=1.15.9.70003"]
    assert (csv_dir / "ItemSparse.era.csv").read_bytes() == b"ID\ncached\n"
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert lock["tables"]["ItemSparse.era"] == hashlib.sha256(b"ID\ncached\n").hexdigest()
    assert "ItemEffect.era" in lock["tables"]


def test_fallback_build_pins_the_fallback(server, tmp_path):
    _era_server(server)
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era", "--fallback-build", "1.15.8.65000") == 0
    assert _era_requests(server) == ["/db2/%s/csv?build=1.15.8.65000" % t for t in ERA_TABLES]


def test_fallback_build_without_product_is_rejected(server, tmp_path, capsys):
    assert _cli(server, tmp_path, "--fallback-build", "1.15.8.65000") == 2
    assert "--fallback-product" in capsys.readouterr().err


def test_unknown_fallback_product_fails_clearly(server, tmp_path, capsys):
    assert _cli(server, tmp_path, "--fallback-product", "wow_nope") == 2
    err = capsys.readouterr().err
    assert "wow_nope" in err and "Traceback" not in err
    assert _era_requests(server) == []


def test_lock_without_fallback_has_no_fallback_keys(server, tmp_path):
    assert _cli(server, tmp_path) == 0
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert "fallback_product" not in lock and "fallback_build" not in lock


# --- User-Agent (M3) --------------------------------------------------------------------------------------------

def test_user_agent_defaults_to_the_pipeline_prefix_and_a_placeholder_contact(monkeypatch):
    monkeypatch.delenv("WOWFOREVER_CONTACT", raising=False)
    ua = fetch.user_agent()
    assert ua == "wow-forever-browser-pipeline/%s (+contact: set WOWFOREVER_CONTACT)" % fetch.TOOL_VERSION
    assert "@" not in ua


def test_user_agent_contact_from_the_environment_or_the_flag(monkeypatch):
    monkeypatch.setenv("WOWFOREVER_CONTACT", "https://example.org/repo")
    assert fetch.user_agent() == "wow-forever-browser-pipeline/%s (+https://example.org/repo)" % fetch.TOOL_VERSION
    # the flag wins over the environment; blank values fall back
    assert fetch.user_agent("ops@example.org").endswith("(+ops@example.org)")
    assert fetch.user_agent("  ").endswith("(+https://example.org/repo)")
    monkeypatch.setenv("WOWFOREVER_CONTACT", " ")
    assert fetch.user_agent().endswith("(+contact: set WOWFOREVER_CONTACT)")


def test_cli_sends_the_pipeline_user_agent(server, tmp_path, monkeypatch):
    monkeypatch.delenv("WOWFOREVER_CONTACT", raising=False)
    assert _cli(server, tmp_path) == 0
    assert {h["User-Agent"] for h in server.headers} == {fetch.user_agent()}
    assert _cli(server, tmp_path, "--user-agent", "https://example.org/x") == 0
    assert server.headers[-1]["User-Agent"] == "wow-forever-browser-pipeline/%s (+https://example.org/x)" \
        % fetch.TOOL_VERSION


# --- default base URL (M4) --------------------------------------------------------------------------------------

def test_default_base_url_is_wow_tools_local():
    assert fetch.DEFAULT_BASE_URL == "http://localhost:5000"


# --- build strings (M5) -----------------------------------------------------------------------------------------

def test_resolve_build_rejects_a_listed_build_that_is_not_four_numbers(server):
    hostile = dict(BUILDS)
    hostile["wow_classic_beta"] = BUILDS["wow_classic_beta"] + [
        {"product": "wow_classic_beta", "version": "../../evil", "created_at": "2099-01-01 00:00:00"}]
    server.routes["/api/builds"] = (200, json.dumps(hostile))
    with pytest.raises(fetch.BadBuildError) as e:
        fetch.resolve_build(make_client(server), "wow_classic_beta")
    assert "../../evil" in str(e.value)
    for bad in ("1.60.1", "1.60.1.70425.1", "1.60.1.x", "1.60.1.70425\n", "C:\\x", ""):
        with pytest.raises(fetch.BadBuildError):
            fetch.check_build(bad)
    assert fetch.check_build("1.15.9.70003") == "1.15.9.70003"


def test_cli_refuses_a_hostile_build_before_writing_anything(server, tmp_path, capsys):
    hostile = dict(BUILDS)
    hostile["wow_classic_beta"] = [{"product": "wow_classic_beta", "version": "../escape", "created_at": "x"}]
    server.routes["/api/builds"] = (200, json.dumps(hostile))
    cache = tmp_path / "cache"
    assert fetch.main(["--product", "wow_classic_beta", "--base-url", server.url, "--cache", str(cache),
                       "--tables", "ItemClass", "--rate", "1000"]) == 2
    err = capsys.readouterr().err
    assert "../escape" in err and "Traceback" not in err
    assert not cache.exists() and not (tmp_path / "escape").exists()
    assert [p for p in server.paths if p.startswith("/db2/")] == []


def test_cli_refuses_a_hostile_fallback_or_pinned_build(server, tmp_path, capsys):
    data = dict(BUILDS)
    data["wow_classic_era"] = [{"product": "wow_classic_era", "version": "1.15.9.7/../../x", "created_at": "x"}]
    server.routes["/api/builds"] = (200, json.dumps(data))
    assert _cli(server, tmp_path, "--fallback-product", "wow_classic_era") == 2
    assert "1.15.9.7/../../x" in capsys.readouterr().err
    assert fetch.main(["--product", "wow_classic_beta", "--build", "..\\up", "--base-url", server.url,
                       "--cache", str(tmp_path), "--rate", "1000"]) == 2
    assert "not four dot-separated numbers" in capsys.readouterr().err
    assert [p for p in server.paths if p.startswith("/db2/")] == []


UI_MANIFEST = ("ID,FilePath,FileName\n"
               "134414,Interface\\ICONS\\,INV_Misc_Rune_01.blp\n"
               "130716,Interface\\BUTTONS\\,Button-Backpack-Up.blp\n")


def test_ui_icons_fetches_the_resolved_fdids_and_locks_them(server, tmp_path, capsys):
    server.routes["/db2/ManifestInterfaceData/csv?build=1.60.1.70425"] = (200, UI_MANIFEST)
    assert fetch.main(["--product", "wow_classic_beta", "--base-url", server.url, "--cache", str(tmp_path),
                       "--tables", "ManifestInterfaceData", "--ui-icons", "--rate", "1000"]) == 0
    assert _fetched_fdids(server) == [130716, 134414]
    lock = json.loads((tmp_path / "1.60.1.70425" / "build.lock").read_text())
    assert lock["ui_icons"] == [
        {"name": "rail.home", "path": "Interface/Icons/INV_Misc_Rune_01", "fdid": 134414, "choice": "first"},
        {"name": "rail.items", "path": "Interface/Buttons/Button-Backpack-Up", "fdid": 130716, "choice": "second"},
    ]
    out = capsys.readouterr()
    assert "rail.items" in out.err and "second choice" in out.err
    assert "ui icons: 2 resolved, 4 unresolved" in out.out


def test_ui_icons_without_the_manifest_fails_clearly(server, tmp_path, capsys):
    assert _cli(server, tmp_path, "--ui-icons") == 2
    err = capsys.readouterr().err
    assert "ManifestInterfaceData" in err and "Traceback" not in err
    assert _fetched_fdids(server) == []
