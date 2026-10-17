"""Builds importer/src/test/resources/fixtures/clientdata/known-items.csv from Wowhead's Forever tooltips.

    python tools/clientdata/known_items.py --raw DIR [--fetch] --out importer/src/test/resources/fixtures/clientdata/known-items.csv

--fetch downloads https://nether.wowhead.com/forever/tooltip/item/{id} (the JSON the item page itself loads) for
every id in ITEMS into DIR/{id}.wh.json, one request every half second, skipping files already there; without it
the saved responses are read. See fixtures/clientdata/KnownItemsOracle.md for why Wowhead is the oracle.

Columns: item_id, name, expected_lines (the tooltip lines, '|'-separated, sell price left out), compare_kinds,
stats ("id:value;..." in tooltip order: plain "+N" lines, resistances, bonus armour as 50), damage
("min-max/delayMs/dps/school", "*-*/0/dps/0" for ammunition), armor ("armor/block", empty for none), lines
("kind=text|..." for the binding, slot, required level and Use/Equip/Chance on hit lines, in tooltip order).
"""
import argparse
import csv
import html
import json
import os
import re
import sys
import time
import urllib.request

ITEMS = [19019, 13262, 25, 2092, 12640, 19682, 19683, 19684, 11306, 4127, 18837, 5248, 15327, 2916, 19321, 1306,
         1282, 17783, 4549, 5079, 2902, 4836, 19316, 1485, 4134, 15855, 7919, 11863]
# kinds left out of a row, and why (KnownItemsOracle.md)
EXCLUDE = {
    19019: {"damage", "dps"},  # Wowhead shows the original 44 - 115; the tables and the Era row give 82 - 153
    # Forever caster weapon (Flags_4 0x100): damage unverified, not written; its spell power "Equip:" line has no
    # stat slot or ItemXItemEffect row in the client data
    4134: {"damage", "speed", "dps", "equip"},
}
# tooltip text kinds compared by KnownItemsTest (task 6); a kind absent from `lines` expects no such line
TEXT_KINDS = ["binding", "slot", "req_level", "use", "equip", "proc"]
BINDINGS = {"Binds when picked up", "Binds when equipped", "Binds when used", "Quest Item"}
EFFECT_PREFIX = {"Use: ": "use", "Equip: ": "equip", "Chance on hit: ": "proc"}
URL = "https://nether.wowhead.com/forever/tooltip/item/%d"
USER_AGENT = "wow-forever-browser-pipeline (known-items oracle)"
RES = {"Fire": 51, "Frost": 52, "Holy": 53, "Shadow": 54, "Nature": 55, "Arcane": 56}
SCHOOL = {"Holy": 1, "Fire": 2, "Nature": 3, "Frost": 4, "Shadow": 5, "Arcane": 6}


def fetch(raw):
    os.makedirs(raw, exist_ok=True)
    for i in ITEMS:
        path = os.path.join(raw, "%d.wh.json" % i)
        if os.path.exists(path):
            continue
        req = urllib.request.Request(URL % i, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read().decode("utf-8")
        with open(path, "w", encoding="utf-8") as f:
            f.write(body)
        time.sleep(0.5)


def lines_of(tooltip):
    t = re.sub(r"<!--.*?-->", "", tooltip)
    t = re.sub(r"<br\s*/?>|</?(table|tr|td|th|div)[^>]*>", "\n", t)
    t = re.sub(r"<[^>]+>", "", t)
    out = []
    for line in t.split("\n"):
        line = re.sub(r"\s+", " ", html.unescape(line).replace("\xa0", " ")).strip()
        if line and not line.startswith("Sell Price"):
            out.append(line)
    return out


def parse(item_id, w):
    tip = w["tooltip"]
    lines = lines_of(tip)
    stats = [(int(m.group(1)), int(m.group(2))) for m in re.finditer(r"<!--stat(\d+)-->([+-]\d+) ", tip)]
    for m in re.finditer(r"(Equip: )?([+-])<!--rtg(\d+)-->(\d+) ", tip):
        if not m.group(1):  # ratings Wowhead rewrites as "Equip:" text are not stat lines
            stats.append((int(m.group(3)), int(m.group(2) + m.group(4))))
    armor = None
    for line in lines:
        m = re.fullmatch(r"([+-]\d+) (Fire|Frost|Holy|Shadow|Nature|Arcane) Resistance", line)
        if m:
            stats.append((RES[m.group(2)], int(m.group(1))))
        m = re.fullmatch(r"\+(\d+) Armor", line)
        if m and not any(a == 50 for a, _ in stats):
            stats.append((50, int(m.group(1))))
        m = re.fullmatch(r"(\d+) Armor", line)
        if m:
            armor = int(m.group(1))
    block = next((int(m.group(1)) for line in lines for m in [re.fullmatch(r"(\d+) Block", line)] if m), 0)
    text = "\n".join(lines)
    damage, kinds = "", []
    dmg = re.search(r"^(\d+) - (\d+) (?:(\w+) )?Damage$", text, re.M)
    adds = re.search(r"^Adds ([\d.]+) damage per second$", text, re.M)
    if dmg:
        speed = re.search(r"^Speed ([\d.]+)$", text, re.M).group(1)
        dps = re.search(r"^\(([\d.]+) damage per second\)$", text, re.M).group(1)
        damage = "%s-%s/%d/%s/%d" % (dmg.group(1), dmg.group(2), round(float(speed) * 1000), dps,
                                     SCHOOL.get(dmg.group(3) or "", 0))
        kinds += ["damage", "speed", "dps"]
    elif adds:
        damage = "*-*/0/%s/0" % adds.group(1)
        kinds += ["dps"]
    kinds += ["armor", "stat", "resist"] + TEXT_KINDS
    kinds = [k for k in kinds if k not in EXCLUDE.get(item_id, set())]
    return {
        "item_id": item_id, "name": w["name"], "expected_lines": "|".join(lines), "compare_kinds": ",".join(kinds),
        "stats": ";".join("%d:%d" % s for s in stats), "damage": damage,
        "armor": "" if armor is None else "%d/%d" % (armor, block),
        "lines": "|".join("%s=%s" % kv for kv in text_lines(tip, lines)),
    }


def text_lines(tip, lines):
    """(kind, text) for the TEXT_KINDS lines in tooltip order; the slot row is "Slot\\tSubclass" (a literal
    backslash-t, the subclass left out when Wowhead's cell is empty)."""
    out = []
    for line in lines:
        if line in BINDINGS:
            out.append(("binding", line))
            break
    # the slot row; a cloak's has no subclass cell at all
    slot = re.search(r'<table width="100%"><tr><td>(.*?)</td>(?:<th>(.*?)</th>)?</tr></table>', tip, re.S)
    if slot:
        left = html.unescape(re.sub(r"<[^>]+>", "", slot.group(1))).strip()
        right = html.unescape(re.sub(r"<[^>]+>", "", slot.group(2) or "")).strip()
        out.append(("slot", left + ("\\t" + right if right else "")))
    out += [("req_level", line) for line in lines if line.startswith("Requires Level ")]
    for line in lines:
        for prefix, kind in EFFECT_PREFIX.items():
            if line.startswith(prefix):
                out.append((kind, line))
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Build known-items.csv from Wowhead Forever tooltips.")
    ap.add_argument("--raw", required=True, help="directory of saved {id}.wh.json responses")
    ap.add_argument("--fetch", action="store_true", help="download missing responses first")
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    if args.fetch:
        fetch(args.raw)
    rows = []
    for i in ITEMS:
        with open(os.path.join(args.raw, "%d.wh.json" % i), encoding="utf-8") as f:
            rows.append(parse(i, json.load(f)))
    with open(args.out, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print("%d rows -> %s" % (len(rows), args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
