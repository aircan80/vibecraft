"""The icon FileDataIDs the browser needs: Item.csv IconFileDataID of every item in a browser database.

    python tools/clientdata/needed_icons.py --db app/src/main/resources/data/wowforever.sqlite \\
        --cache ~/.cache/wowforever-clientdata --build 1.60.1.70425

prints the distinct ids greater than 0, ascending and comma-separated, for fetch.py --fdids. The importer embeds
only the icons of items it imports, so this fetches far fewer files than --icons-from Item.csv. The item ids come
from the database (opened read-only); the first import of a new item set can be a --skip-client-data import, which
writes the same item table.
"""
import argparse
import csv
import sqlite3
import sys
from pathlib import Path
from typing import Iterable, Set


def item_ids(db_path) -> Set[int]:
    uri = Path(db_path).resolve().as_uri() + "?mode=ro"
    con = sqlite3.connect(uri, uri=True)
    try:
        return {row[0] for row in con.execute("select id from item")}
    finally:
        con.close()


def icon_fdids(item_csv, ids: Iterable[int]) -> Set[int]:
    wanted = set(ids)
    out = set()
    with open(item_csv, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            item = (row.get("ID") or "").strip()
            icon = (row.get("IconFileDataID") or "").strip()
            if item.isdigit() and int(item) in wanted and icon.isdigit() and int(icon) > 0:
                out.add(int(icon))
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Print the icon FileDataIDs of the items in a browser database.")
    ap.add_argument("--db", required=True, help="the browser SQLite database")
    ap.add_argument("--cache", required=True, help="the fetch cache")
    ap.add_argument("--build", required=True)
    args = ap.parse_args(argv)
    db = Path(args.db).expanduser()
    item_csv = Path(args.cache).expanduser() / args.build / "csv" / "Item.csv"
    for p, what in ((db, "database"), (item_csv, "Item.csv (fetch the tables first)")):
        if not p.is_file():
            print("error: %s not found: %s" % (what, p), file=sys.stderr)
            return 2
    print(",".join(str(x) for x in sorted(icon_fdids(item_csv, item_ids(db)))))
    return 0
