import sqlite3

import pytest

import extract

LOOT = ("(entry integer, item integer, ChanceOrQuestChance float, groupid integer, mincountOrRef integer,"
        " maxcount integer, condition_id integer, patch_min integer, patch_max integer)")


def world_db(path):
    c = sqlite3.connect(path)
    for table in ("creature_loot_template", "reference_loot_template", "gameobject_loot_template"):
        c.execute("CREATE TABLE %s %s" % (table, LOOT))
    c.execute("CREATE TABLE npc_vendor (entry integer, slot integer, item integer, maxcount integer,"
              " incrtime integer, itemflags integer, condition_id integer)")
    c.executemany("INSERT INTO creature_loot_template VALUES (?, ?, ?, 0, 1, 1, 0, ?, ?)",
                  [(448, 1931, -100, 0, 10), (448, 117, 6.1, 0, 10), (448, 118, 2, 0, 6)])
    c.execute("INSERT INTO reference_loot_template VALUES (30018, 1495, 0, 1, 1, 1, 0, 0, 10)")
    c.execute("INSERT INTO npc_vendor VALUES (1, 0, 159, 0, 0, 0, 0)")
    c.commit()
    c.close()
    return path


def test_counts_every_table_and_the_rows_valid_at_patch_10(tmp_path):
    counts = extract.row_counts(world_db(tmp_path / "mangos.sqlite"))
    assert counts == {
        "creature_loot_template": (3, 2),
        "reference_loot_template": (1, 1),
        "gameobject_loot_template": (0, 0),
        "npc_vendor": (1, None),
    }


def test_main_prints_one_line_per_table(tmp_path, capsys):
    db = world_db(tmp_path / "mangos.sqlite")
    assert extract.main(["--db", str(db)]) == 0
    out = capsys.readouterr().out.splitlines()
    assert out[0].startswith("table")
    assert "creature_loot_template" in out[1] and out[1].split()[1:] == ["3", "2"]
    assert out[4].split()[1:] == ["1", "-"]


def test_the_database_is_opened_read_only(tmp_path):
    db = world_db(tmp_path / "mangos.sqlite")
    with extract.connect(db) as c, pytest.raises(sqlite3.OperationalError):
        c.execute("DELETE FROM npc_vendor")


def test_a_missing_database_is_an_error(tmp_path, capsys):
    assert extract.main(["--db", str(tmp_path / "absent.sqlite")]) == 1
    assert "absent.sqlite" in capsys.readouterr().err
