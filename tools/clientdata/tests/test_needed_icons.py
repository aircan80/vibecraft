import sqlite3

from clientdata import needed_icons


def _setup(tmp_path):
    db = tmp_path / "browser.sqlite"
    con = sqlite3.connect(db)
    con.execute("create table item (id integer primary key, name text)")
    con.executemany("insert into item values (?, ?)", [(1, "a"), (2, "b"), (3, "c"), (5, None)])
    con.commit()
    con.close()
    csv_dir = tmp_path / "cache" / "B" / "csv"
    csv_dir.mkdir(parents=True)
    # 1 and 3 share icon 300; 2 has none; 4 is not in the database; 5 (nameless) still counts
    (csv_dir / "Item.csv").write_bytes(
        b"\xef\xbb\xbfID,ClassID,IconFileDataID\n1,2,300\n2,4,0\n3,4,300\n4,4,400\n5,0,500\n")
    return db


def test_prints_distinct_icons_of_items_in_the_database(tmp_path, capsys):
    db = _setup(tmp_path)
    assert needed_icons.main(["--db", str(db), "--cache", str(tmp_path / "cache"), "--build", "B"]) == 0
    assert capsys.readouterr().out.strip() == "300,500"


def test_missing_inputs_fail_clearly(tmp_path, capsys):
    db = _setup(tmp_path)
    assert needed_icons.main(["--db", str(tmp_path / "nope.sqlite"), "--cache", str(tmp_path / "cache"),
                              "--build", "B"]) == 2
    assert needed_icons.main(["--db", str(db), "--cache", str(tmp_path / "cache"), "--build", "X"]) == 2
    err = capsys.readouterr().err
    assert "nope.sqlite" in err and "Item.csv" in err and "Traceback" not in err


def test_database_is_opened_read_only(tmp_path):
    db = _setup(tmp_path)
    before = db.read_bytes()
    assert needed_icons.item_ids(db) == {1, 2, 3, 5}
    assert db.read_bytes() == before
