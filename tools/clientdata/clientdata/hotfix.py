"""The client's hotfix cache (DBCache.bin) as mirrored by Raidbots, and the hotfix overlay built from it.

File layout (version 9, the Forever beta client):

    header   44 bytes: magic b"XFTH", uint32 version, uint32 build (e.g. 70450), 32 bytes hash
    entry    32 bytes: magic b"XFTH", int32 region, int32 push id, uint32 unique id, uint32 table hash,
             uint32 record id, uint32 data size, uint8 status, 3 bytes padding; then ``data size`` bytes of record

Status 1 is a valid row, 2 a removed record, 3 an invalidated push, 4 a row not made public. The table hash is
Blizzard's SStrHash of the table name (ItemSparse 0x919BE54E, Item 0x50238EC2).

The overlay (pm-data-sources.md, Task 19): only valid rows of the overlay tables are applied. Snapshots are read
oldest first and each file in its own order, so the newest snapshot, then the later entry, wins (push ids cannot
order them: the per-record replies carry 0x1000000 + record id, larger than real pushes). An invalidated entry
removes only the row its push supplied; an older push's row of the same record then applies again. A record a later
snapshot omits keeps its row: revealed items are never un-revealed. Removals (status 2) are counted and change
nothing (whether one should also undo the record's earlier rows is a PM question, Task 19 report).

Each snapshot's rows are decoded with the layout of its own header build and carried into the overlay by column
name, so a snapshot of an earlier layout keeps its rows as long as it has every column the overlay writes; one that
lacks a column is skipped and counted (``convert.py``).
"""
import struct
from typing import Dict, Iterable, Iterator, List, NamedTuple, Tuple, Union

from . import dbd

MAGIC = b"XFTH"
VERSION = 9
HEADER_SIZE = 44
ENTRY_SIZE = 32
RAIDBOTS_URL = "https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin"

VALID, REMOVED, INVALID, NOT_PUBLIC = 1, 2, 3, 4

# The tables the overlay writes, as csv/{Table}.hotfix.csv (Task 19).
OVERLAY_TABLES = ("ItemSparse", "Item", "ItemEffect")
SUFFIX = ".hotfix"

_HASH_TABLE = (0x486E26EE, 0xDCAA16B3, 0xE1918EEF, 0x202DAFDB, 0x341C7DC7, 0x1C365303, 0x40EF2D37, 0x65FD5E49,
               0xD6057177, 0x904ECE93, 0x1C38024F, 0x98FD323B, 0xE3061AE7, 0xA39B0FA1, 0x9797F25F, 0xE4444563)
_M = 0xFFFFFFFF


class HotfixError(ValueError):
    """A cache that is not a DBCache.bin of the known version, or that is cut short."""


class BuildMismatchError(ValueError):
    """The newest snapshot's header build is not the build.lock build."""


class Entry(NamedTuple):
    region: int
    push: int
    unique: int
    table_hash: int
    record_id: int
    status: int
    data: bytes


def overlay_name(table: str) -> str:
    """``ItemSparse`` -> ``ItemSparse.hotfix`` (the overlay CSV and lock name)."""
    return table + SUFFIX


def table_hash(name: str) -> int:
    """SStrHash of the upper-cased name, the DB2 table hash the cache keys rows by."""
    seed, shift = 0x7FED7FED, 0xEEEEEEEE
    for c in name.upper().replace("/", "\\").encode("ascii"):
        seed = ((_HASH_TABLE[c >> 4] - _HASH_TABLE[c & 0xF]) & _M) ^ ((shift + seed) & _M)
        shift = (c + seed + 33 * shift + 3) & _M
    return seed or 1


def header(data: bytes) -> Tuple[int, int]:
    """``(version, build)`` of a cache; HotfixError when the magic or version is not the known one."""
    if len(data) < HEADER_SIZE:
        raise HotfixError("cache is %d bytes, shorter than its %d-byte header" % (len(data), HEADER_SIZE))
    magic, version, build = struct.unpack_from("<4sII", data, 0)
    if magic != MAGIC:
        raise HotfixError("not a DBCache.bin: magic %r" % magic)
    if version != VERSION:
        raise HotfixError("DBCache.bin version %d, this reader knows version %d" % (version, VERSION))
    return version, build


def entries(data: bytes) -> Iterator[Entry]:
    """Every entry in file order; HotfixError when an entry is cut short or out of step."""
    header(data)
    off = HEADER_SIZE
    while off < len(data):
        if off + ENTRY_SIZE > len(data):
            raise HotfixError("entry header at %d is cut short" % off)
        magic, region, push, unique, th, rid, size, status = struct.unpack_from("<4siiIIIIB", data, off)
        if magic != MAGIC:
            raise HotfixError("entry at %d has magic %r" % (off, magic))
        start = off + ENTRY_SIZE
        if start + size > len(data):
            raise HotfixError("entry at %d claims %d bytes past the end" % (off, size))
        yield Entry(region, push, unique, th, rid, status, data[start:start + size])
        off = start + size


class TableOverlay(NamedTuple):
    rows: Dict[int, Dict[str, object]]  # by record id, decoded
    valid: int  # distinct records with a valid row in some snapshot
    invalidated: int  # distinct (record, push) invalidations seen
    undone: int  # invalidations that removed a row a snapshot had supplied
    removed: int = 0  # distinct (record, push) removals (status 2) seen; they change nothing


Snapshot = Union[bytes, Tuple[bytes, Dict[str, List[dbd.Field]]]]


def build_overlay(snapshots: Iterable[Snapshot], layouts: Dict[str, List[dbd.Field]]) -> Dict[str, TableOverlay]:
    """The overlay of each table in ``layouts`` from ``snapshots`` (oldest first). See the module docstring.

    A snapshot is its bytes (decoded with ``layouts``) or ``(bytes, its own layouts)``; rows are carried into the
    overlay's columns (``layouts``) by name, so its own layouts must have every one of those columns.
    """
    hashes = {table_hash(t): t for t in layouts}
    # per table, per record: push -> (sequence, data, fields); the highest sequence left wins
    pushed: Dict[str, Dict[int, Dict[int, Tuple[int, bytes, list]]]] = {t: {} for t in layouts}
    seen_valid: Dict[str, set] = {t: set() for t in layouts}
    invalid: Dict[str, set] = {t: set() for t in layouts}
    removed: Dict[str, set] = {t: set() for t in layouts}
    undone = {t: 0 for t in layouts}
    seq = 0
    for snapshot in snapshots:
        data, own = snapshot if isinstance(snapshot, tuple) else (snapshot, layouts)
        for e in entries(data):
            table = hashes.get(e.table_hash)
            if table is None:
                continue
            seq += 1
            per_record = pushed[table].setdefault(e.record_id, {})
            if e.status == VALID:
                per_record[e.push] = (seq, e.data, own[table])
                seen_valid[table].add(e.record_id)
            elif e.status == REMOVED:
                removed[table].add((e.record_id, e.push))
            elif e.status == INVALID:
                invalid[table].add((e.record_id, e.push))
                if per_record.pop(e.push, None) is not None:
                    undone[table] += 1
    out = {}
    for table, fields in layouts.items():
        names = dbd.columns(fields)
        rows = {}
        for rid, by_push in pushed[table].items():
            if by_push:
                _, data, own_fields = max(by_push.values(), key=lambda v: v[0])
                row = dbd.decode(own_fields, data, rid)
                rows[rid] = {c: row[c] for c in names}
        out[table] = TableOverlay(dict(sorted(rows.items())), len(seen_valid[table]), len(invalid[table]),
                                  undone[table], len(removed[table]))
    return out


def check_build(header_build: int, lock_build: str):
    """BuildMismatchError with the build-watch text when the cache's build is not ``lock_build``'s last number."""
    if str(header_build) != lock_build.rsplit(".", 1)[-1]:
        raise BuildMismatchError(mismatch_message(header_build, lock_build))


def mismatch_message(header_build, lock_build) -> str:
    return ("hotfix cache header build %s differs from build.lock build %s: the client moved on. Run "
            "tools/build_watch.py, fetch the client tables of the new build (fetch.py --build ... --hotfix-cache) "
            "and convert again before importing" % (header_build, lock_build))


def write_csv(path, header_columns: List[str], rows: Dict[int, Dict[str, object]]):
    """The overlay CSV in wago.tools' format: LF lines, non-empty strings quoted, floats as float32."""
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(",".join(header_columns) + "\n")
        for rid in sorted(rows):
            row = rows[rid]
            cells = []
            for col in header_columns:
                v = row[col]
                if isinstance(v, str):
                    cells.append('"%s"' % v.replace('"', '""') if v else "")
                else:
                    cells.append(dbd.format_value(v))
            f.write(",".join(cells) + "\n")
