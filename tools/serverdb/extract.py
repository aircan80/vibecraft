"""Inspect the VMaNGOS world database that fetch.py extracted: row counts of the tables the browser reads or may read.

    python extract.py [--db ~/.cache/wowforever-serverdb/4641790/mangos.sqlite]

The importer reads the SQLite file directly (``--server-db-dir``), so nothing is exported here; this script only
shows what the release holds. For each table it prints the total row count and, for the loot tables, the rows valid
at patch 1.12 (VMaNGOS patch index 10, ``patch_min <= 10 <= patch_max``), which are the rows the importer uses.
Standard library only; the file is opened read-only.
"""
import argparse
import contextlib
import sqlite3
import sys
from pathlib import Path

# fetch.py sits beside this script; import it from there even under python -I (no script directory on sys.path).
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch  # noqa: E402

PATCH = 10
LOOT_TABLES = ("creature_loot_template", "reference_loot_template", "gameobject_loot_template")
TABLES = LOOT_TABLES + ("npc_vendor",)


def default_db():
    lock = fetch.load_lock(fetch.LOCK)
    return fetch.DEFAULT_CACHE_ROOT / fetch.short_commit(lock) / fetch.WORLD_DB


def connect(path):
    return sqlite3.connect("file:%s?mode=ro" % Path(path).resolve().as_posix(), uri=True)


def row_counts(path):
    """table -> (rows, rows valid at patch 10, or None for a table without patch columns)."""
    counts = {}
    with contextlib.closing(connect(path)) as c:
        for table in TABLES:
            total = c.execute("SELECT count(*) FROM %s" % table).fetchone()[0]
            at_patch = None
            if table in LOOT_TABLES:
                at_patch = c.execute("SELECT count(*) FROM %s WHERE patch_min <= ? AND patch_max >= ?" % table,
                                     (PATCH, PATCH)).fetchone()[0]
            counts[table] = (total, at_patch)
    return counts


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--db", type=Path, default=None, help="the world database (default: the pinned cache path)")
    args = p.parse_args(argv)
    db = args.db or default_db()
    if not db.is_file():
        print("error: %s does not exist; run fetch.py first" % db, file=sys.stderr)
        return 1
    print("%-26s %10s %10s" % ("table", "rows", "patch-10"))
    for table, (total, at_patch) in row_counts(db).items():
        print("%-26s %10d %10s" % (table, total, "-" if at_patch is None else at_patch))
    return 0


if __name__ == "__main__":
    sys.exit(main())
