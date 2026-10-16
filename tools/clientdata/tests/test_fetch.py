import hashlib
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from clientdata import fetch
from clientdata import tables
from clientdata.http import Client, FetchError

UA = "wowforever-clientdata-test/1.0 (+test)"

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
                status, body = outer.routes.get(self.path, (404, b"nope"))
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
    assert len(tables.REQUIRED_TABLES) == 42
    assert set(out) == set(tables.REQUIRED_TABLES)
    assert out["Item"] == tmp_path / build / "csv" / "Item.csv"
    assert out["Item"].read_text() == "ID,Name\n1,Item\n"
    hits = server.hits
    assert hits == 42
    out2 = fetch.fetch_tables(c, build, tmp_path)
    assert server.hits == hits
    assert out2 == out


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
    out2 = fetch.fetch_files(c, "wow_classic_beta", [100], tmp_path, build="B")
    assert server.hits == hits and out2 == {100: out[100]}


def test_non_404_error_raises(server):
    server.routes["/boom"] = (500, "x")
    with pytest.raises(FetchError) as e:
        make_client(server).get_text("/boom")
    assert e.value.status == 500 and e.value.path == "/boom"


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
