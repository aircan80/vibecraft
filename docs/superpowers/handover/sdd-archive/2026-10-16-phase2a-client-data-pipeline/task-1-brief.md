### Task 1: Python fetch tool with cache, throttle and lock file

**Files:**
- Create: `tools/clientdata/README.md`, `tools/clientdata/requirements.txt` (`Pillow>=11`, `requests>=2.32`, `pytest>=8`), `tools/clientdata/clientdata/__init__.py`, `tools/clientdata/clientdata/fetch.py`, `tools/clientdata/clientdata/http.py`, `tools/clientdata/clientdata/tables.py`, `tools/clientdata/fetch.py` (CLI shim), `tools/clientdata/tests/test_fetch.py`, `tools/clientdata/pytest.ini`, `.gitignore` entries (`tools/clientdata/.venv/`, `tools/clientdata/.pytest_cache/`)
- Create: `tools/clientdata/build.lock` is generated, not committed until Task 11.

**Interfaces:**
- `tables.REQUIRED_TABLES`: the spec §4.1 table list (46 names) as a tuple.
- `http.Client(base_url, user_agent, rate_per_second=4.0, session=None)`: `get_text(path) -> str`, `get_bytes(path) -> bytes`; raises `FetchError(path, status)` on non-200; sleeps so that at most `rate_per_second` requests start per second.
- `fetch.resolve_build(client, product, build=None) -> Build(product, build, date)`: reads `/api/builds` (JSON list of `{product, version, created_at …}`; confirm the exact field names against the live response and against wow.tools.local's `/api/builds` and document both in README) and returns the newest for `product` unless `build` is given.
- `fetch.fetch_tables(client, build, cache_dir) -> dict[str, Path]`: for each required table GET `/db2/{Table}/csv?build={build}` into `cache_dir/{build}/csv/{Table}.csv` unless present and non-empty.
- `fetch.fetch_files(client, product, fdids: Iterable[int], cache_dir) -> dict[int, Path]`: GET `/api/casc/{fdid}?download&branch={product}` into `cache_dir/{build}/files/{fdid}.blp` unless present; a 404 is recorded in `missing.txt`, not fatal.
- `fetch.write_lock(cache_dir, build, tables, files) -> Path`: writes `build.lock` JSON `{product, build, fetched_at, base_url, tables: {name: sha256}, file_count, missing_count}`.
- CLI: `python fetch.py --product wow_classic_beta [--build B] [--base-url URL] --cache DIR [--icons-from ITEM_CSV] [--maps]`: fetches tables; with `--icons-from` reads `Item.csv` from the cache and fetches every distinct `IconFileDataID`; with `--maps` fetches every `UiMapArtTile.FileDataID` and `WorldMapOverlayTile.FileDataID`.

- [ ] **Step 1: Create the venv and write the failing tests**

`python -m venv tools/clientdata/.venv && tools/clientdata/.venv/Scripts/pip install -r tools/clientdata/requirements.txt` (Windows path; README documents the POSIX form).

```python
# tests/test_fetch.py — uses http.server in a thread serving canned responses from a dict
def test_resolve_build_picks_newest_for_product(server): ...   # two wow_classic_beta entries, one wowf → newest beta
def test_fetch_tables_skips_cached_files(server, tmp_path): ... # second call makes zero requests (server.hits unchanged)
def test_fetch_files_records_404_as_missing(server, tmp_path): ...
def test_throttle_respects_rate(server): ...                     # 8 requests at rate 4/s take ≥ 1.5 s
def test_lock_file_contents(tmp_path): ...                       # keys present, sha256 matches file
def test_user_agent_header_sent(server): ...
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q`. Expected: import errors.
- [ ] **Step 3: Implement `http.py`, `tables.py`, `fetch.py`**
- [ ] **Step 4: Run tests to verify they pass** — same command. Expected: 6 passed.
- [ ] **Step 5: Smoke against wago.tools for one table** — Run: `tools/clientdata/.venv/Scripts/python tools/clientdata/fetch.py --product wow_classic_beta --base-url https://wago.tools --cache ~/.cache/wowforever-clientdata --tables ItemClass` (add a `--tables` filter for this). Expected: `ItemClass.csv` cached (small), lock written. Record the `/api/builds` field names in README.
- [ ] **Step 6: Commit** — `git add tools/clientdata .gitignore && git commit -m "feat(clientdata): fetch tool with cache, throttle and lock file"`

---

