"""Cut a small, self-consistent subset of the cached client tables for test fixtures.

Given item ids (plus optional item sets and UiMaps) it keeps every row the importer will
follow from them, transitively:

  Item, ItemSparse                      the items (plus every piece of each referenced set)
  ItemSet, ItemSetSpell                 the sets named by --sets and by ItemSparse.ItemSet
  ItemXItemEffect -> ItemEffect         the items' effects
  Spell, SpellName, SpellEffect,        every spell reached from item effects and set bonuses,
  SpellMisc, SpellDuration              EffectTriggerSpell, enchantment spells and $<id> text refs
  SpellItemEnchantment                  enchantments named by enchanting spell effects
  RandPropPoints, ItemDamage*,          rows for every item level used (plus --extra-levels)
  ItemArmorTotal/Quality/Shield
  UiMap, UiMapXMapArt, UiMapArt,        the chain for --ui-maps
  UiMapArtStyleLayer, UiMapArtTile,
  WorldMapOverlay(+Tile), UiMapAssignment
  AreaTable                             --areas and all their descendants (ParentAreaID)
  Map                                   maps referenced by the kept AreaTable / UiMapAssignment rows
  small lookup tables                   whole (ArmorLocation, ItemClass, ItemSubClass, ...)

Rows are copied byte for byte (original quoting and line endings), in source order, under
the original header line. Every table in REQUIRED_TABLES is written, even when empty.
"""
import argparse
import csv
import io
import re
import sys
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Set, Tuple

from .tables import REQUIRED_TABLES

# Copied whole: small lookup tables (all well under WHOLE_TABLE_LIMIT in 1.60.1.70425).
WHOLE_TABLES = ("ArmorLocation", "ItemClass", "ItemSubClass", "ItemNameDescription",
                "ChrRaces", "ChrClasses", "SkillLine", "Faction")
WHOLE_TABLE_LIMIT = 200 * 1024

LEVEL_TABLES_BY_ID = ("RandPropPoints", "ItemArmorQuality")
LEVEL_TABLES_BY_COLUMN = ("ItemDamageOneHand", "ItemDamageOneHandCaster", "ItemDamageTwoHand",
                          "ItemDamageTwoHandCaster", "ItemDamageRanged", "ItemDamageThrown",
                          "ItemDamageWand", "ItemDamageAmmo", "ItemArmorTotal", "ItemArmorShield")

# SpellEffect.Effect values whose EffectMiscValue_0 is a SpellItemEnchantment id.
ENCHANT_EFFECTS = {53, 54, 92, 156}
# SpellItemEnchantment Effect_n types whose EffectArg_n is a spell id (proc, equip, use).
ENCHANT_SPELL_TYPES = {1, 3, 7}
SPELL_REF = re.compile(r"\$(?:@[a-z]+|\?[a-z])?(\d+)")
MAX_SPELL_ROUNDS = 4


class Table:
    """Header line plus raw records; fields parsed lazily with the csv module."""

    def __init__(self, name: str, header_raw: str, records: List[str]):
        self.name = name
        self.header_raw = header_raw
        self.columns = next(csv.reader(io.StringIO(header_raw)))
        self._index = {c: i for i, c in enumerate(self.columns)}
        self.records = records
        self._parsed = [next(csv.reader(io.StringIO(r))) for r in records]

    def rows(self) -> Iterable[Tuple[str, Dict[str, str]]]:
        for raw, fields in zip(self.records, self._parsed):
            yield raw, {c: (fields[i] if i < len(fields) else "") for c, i in self._index.items()}

    def has(self, column: str) -> bool:
        return column in self._index


def split_records(text: str) -> List[str]:
    """Split CSV text into raw records (terminator included), honouring quoted newlines."""
    out, start, quoted, i, n = [], 0, False, 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            quoted = not quoted  # a doubled quote toggles twice: net unchanged
        elif c == "\n" and not quoted:
            out.append(text[start:i + 1])
            start = i + 1
        i += 1
    if start < n:
        out.append(text[start:])
    return out


def read_table(csv_dir: Path, name: str) -> Optional[Table]:
    path = Path(csv_dir) / (name + ".csv")
    if not path.exists():
        return None
    text = path.read_bytes().decode("utf-8")
    bom = text.startswith("﻿")
    if bom:
        text = text[1:]
    records = split_records(text)
    if not records:
        return None
    return Table(name, records[0], [r for r in records[1:] if r.strip("\r\n")])


def _int(v: str) -> int:
    try:
        return int(v)
    except (TypeError, ValueError):
        try:
            return int(float(v))
        except (TypeError, ValueError):
            return 0


def _ids(values: Iterable[str]) -> Set[int]:
    return {x for x in (_int(v) for v in values) if x > 0}


class Subset:
    def __init__(self, csv_dir: Path):
        self.csv_dir = Path(csv_dir)
        self._tables: Dict[str, Optional[Table]] = {}
        self.keep: Dict[str, List[str]] = {}

    def table(self, name: str) -> Optional[Table]:
        if name not in self._tables:
            self._tables[name] = read_table(self.csv_dir, name)
        return self._tables[name]

    def select(self, name: str, pred: Callable[[Dict[str, str]], bool]) -> List[Dict[str, str]]:
        t = self.table(name)
        if t is None:
            self.keep[name] = []
            return []
        kept_raw, kept = [], []
        for raw, row in t.rows():
            if pred(row):
                kept_raw.append(raw)
                kept.append(row)
        self.keep[name] = kept_raw
        return kept

    def whole(self, name: str, referenced: Optional[Set[int]] = None) -> None:
        t = self.table(name)
        if t is None:
            self.keep[name] = []
            return
        path = self.csv_dir / (name + ".csv")
        if path.stat().st_size > WHOLE_TABLE_LIMIT and referenced is not None:
            self.select(name, lambda r: _int(r.get("ID")) in referenced)
        else:
            self.keep[name] = list(t.records)

    def write(self, out_csv: Path) -> Dict[str, int]:
        out_csv.mkdir(parents=True, exist_ok=True)
        counts = {}
        for name in REQUIRED_TABLES:
            t = self.table(name)
            if t is None:
                continue
            rows = self.keep.get(name, [])
            data = t.header_raw + "".join(rows)
            (out_csv / (name + ".csv")).write_bytes(data.encode("utf-8"))
            counts[name] = len(rows)
        return counts


def build_subset(csv_dir, items: Iterable[int], sets: Iterable[int] = (), ui_maps: Iterable[int] = (),
                 areas: Iterable[int] = (), extra_levels: Iterable[int] = ()) -> Subset:
    s = Subset(csv_dir)
    item_ids = set(items)
    set_ids = set(sets)

    # Items and sets: an item's own set and a set's pieces, to a fixed point.
    sparse_t = s.table("ItemSparse")
    sparse_by_id = {}
    if sparse_t is not None:
        for _, r in sparse_t.rows():
            sparse_by_id[_int(r["ID"])] = r
    sets_t = s.table("ItemSet")
    set_rows = {_int(r["ID"]): r for _, r in sets_t.rows()} if sets_t else {}
    while True:
        before = (len(item_ids), len(set_ids))
        set_ids |= {_int(sparse_by_id[i].get("ItemSet")) for i in item_ids if i in sparse_by_id} - {0}
        for sid in set_ids:
            r = set_rows.get(sid)
            if r:
                item_ids |= _ids(v for k, v in r.items() if k.startswith("ItemID_"))
        if (len(item_ids), len(set_ids)) == before:
            break

    s.select("Item", lambda r: _int(r["ID"]) in item_ids)
    sparse = s.select("ItemSparse", lambda r: _int(r["ID"]) in item_ids)
    s.select("ItemSet", lambda r: _int(r["ID"]) in set_ids)
    set_spells = s.select("ItemSetSpell", lambda r: _int(r["ItemSetID"]) in set_ids)

    links = s.select("ItemXItemEffect", lambda r: _int(r["ItemID"]) in item_ids)
    effect_ids = _ids(r["ItemEffectID"] for r in links)
    effects = s.select("ItemEffect", lambda r: _int(r["ID"]) in effect_ids)

    spell_ids = _ids(r["SpellID"] for r in effects) | _ids(r["SpellID"] for r in set_spells)
    enchant_ids = _ids(r.get("Socket_match_enchantment_ID", "") for r in sparse)
    spell_ids, enchant_ids = _close_spells(s, spell_ids, enchant_ids)

    s.select("Spell", lambda r: _int(r["ID"]) in spell_ids)
    s.select("SpellName", lambda r: _int(r["ID"]) in spell_ids)
    s.select("SpellEffect", lambda r: _int(r["SpellID"]) in spell_ids)
    misc = s.select("SpellMisc", lambda r: _int(r["SpellID"]) in spell_ids)
    durations = _ids(r["DurationIndex"] for r in misc)
    s.select("SpellDuration", lambda r: _int(r["ID"]) in durations)
    s.select("SpellItemEnchantment", lambda r: _int(r["ID"]) in enchant_ids)

    levels = _ids(r["ItemLevel"] for r in sparse) | set(extra_levels)
    for name in LEVEL_TABLES_BY_ID:
        s.select(name, lambda r: _int(r["ID"]) in levels)
    for name in LEVEL_TABLES_BY_COLUMN:
        s.select(name, lambda r: _int(r["ItemLevel"]) in levels)

    refs = {
        "SkillLine": _ids(r.get("RequiredSkill", "") for r in sparse),
        "Faction": _ids(r.get("MinFactionID", "") for r in sparse),
        "ItemNameDescription": _ids(r.get("ItemNameDescriptionID", "") for r in sparse),
    }
    for name in WHOLE_TABLES:
        s.whole(name, refs.get(name))

    map_ids = _ui_map_chain(s, set(ui_maps))
    area_ids = _descendants(s, set(areas))
    kept_areas = s.select("AreaTable", lambda r: _int(r["ID"]) in area_ids)
    map_ids |= {_int(r["ContinentID"]) for r in kept_areas}
    s.select("Map", lambda r: _int(r["ID"]) in map_ids)
    return s


def _close_spells(s: Subset, spell_ids: Set[int], enchant_ids: Set[int]) -> Tuple[Set[int], Set[int]]:
    """Follow trigger spells, enchantments and $<id> description references a few rounds deep."""
    spell_t, effect_t, ench_t = s.table("Spell"), s.table("SpellEffect"), s.table("SpellItemEnchantment")
    text = {}
    if spell_t is not None:
        for _, r in spell_t.rows():
            text[_int(r["ID"])] = " ".join(r.get(c, "") for c in ("Description_lang", "AuraDescription_lang"))
    effects_of: Dict[int, List[Dict[str, str]]] = {}
    if effect_t is not None:
        for _, r in effect_t.rows():
            effects_of.setdefault(_int(r["SpellID"]), []).append(r)
    ench = {_int(r["ID"]): r for _, r in ench_t.rows()} if ench_t is not None else {}

    frontier = set(spell_ids)
    seen_ench: Set[int] = set()
    for _ in range(MAX_SPELL_ROUNDS):
        found = set()
        for sid in frontier:
            found |= {int(m) for m in SPELL_REF.findall(text.get(sid, ""))}
            for e in effects_of.get(sid, []):
                found |= _ids([e.get("EffectTriggerSpell", "")])
                if _int(e.get("Effect")) in ENCHANT_EFFECTS:
                    enchant_ids |= _ids([e.get("EffectMiscValue_0", "")])
        for eid in enchant_ids - seen_ench:
            seen_ench.add(eid)
            r = ench.get(eid)
            if r is None:
                continue
            for n in range(3):
                if _int(r.get("Effect_%d" % n)) in ENCHANT_SPELL_TYPES:
                    found |= _ids([r.get("EffectArg_%d" % n, "")])
        found = {f for f in found if f in text} - spell_ids
        if not found:
            break
        spell_ids |= found
        frontier = found
    return spell_ids, enchant_ids


def _ui_map_chain(s: Subset, ui_maps: Set[int]) -> Set[int]:
    """Select the UiMap chain rows; return the Map ids named by the kept UiMapAssignment rows."""
    s.select("UiMap", lambda r: _int(r["ID"]) in ui_maps)
    xart = s.select("UiMapXMapArt", lambda r: _int(r["UiMapID"]) in ui_maps)
    arts = _ids(r["UiMapArtID"] for r in xart)
    art_rows = s.select("UiMapArt", lambda r: _int(r["ID"]) in arts)
    styles = _ids(r["UiMapArtStyleID"] for r in art_rows)
    s.select("UiMapArtStyleLayer", lambda r: _int(r["UiMapArtStyleID"]) in styles)
    s.select("UiMapArtTile", lambda r: _int(r["UiMapArtID"]) in arts)
    overlays = _ids(r["ID"] for r in s.select("WorldMapOverlay", lambda r: _int(r["UiMapArtID"]) in arts))
    s.select("WorldMapOverlayTile", lambda r: _int(r["WorldMapOverlayID"]) in overlays)
    assignments = s.select("UiMapAssignment", lambda r: _int(r["UiMapID"]) in ui_maps)
    return {_int(r["MapID"]) for r in assignments if _int(r["MapID"]) >= 0}


def _descendants(s: Subset, roots: Set[int]) -> Set[int]:
    t = s.table("AreaTable")
    if t is None or not roots:
        return set(roots)
    children: Dict[int, Set[int]] = {}
    for _, r in t.rows():
        children.setdefault(_int(r["ParentAreaID"]), set()).add(_int(r["ID"]))
    out, todo = set(), list(roots)
    while todo:
        a = todo.pop()
        if a in out:
            continue
        out.add(a)
        todo.extend(children.get(a, ()))
    return out


def icon_fdids(subset: Subset) -> Set[int]:
    t = subset.table("Item")
    kept = set(subset.keep.get("Item", []))
    return {_int(r["IconFileDataID"]) for raw, r in t.rows() if raw in kept} - {0} if t else set()


def tile_fdids(subset: Subset) -> Set[int]:
    out = set()
    for name in ("UiMapArtTile", "WorldMapOverlayTile"):
        t = subset.table(name)
        kept = set(subset.keep.get(name, []))
        if t is not None:
            out |= {_int(r["FileDataID"]) for raw, r in t.rows() if raw in kept} - {0}
    return out


def _id_list(text: str) -> List[int]:
    return [int(x) for x in text.split(",") if x.strip()] if text else []


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Write a fixture-sized subset of cached client tables.")
    ap.add_argument("--cache", required=True)
    ap.add_argument("--build", required=True)
    ap.add_argument("--out", required=True, help="output directory; tables go to OUT/csv")
    ap.add_argument("--items", required=True, help="comma-separated item ids")
    ap.add_argument("--sets", default="", help="comma-separated ItemSet ids (their pieces are added)")
    ap.add_argument("--ui-maps", default="", help="comma-separated UiMap ids")
    ap.add_argument("--areas", default="", help="comma-separated AreaTable ids (descendants included)")
    ap.add_argument("--extra-levels", default="60", help="item levels always kept in scaling tables")
    ap.add_argument("--list-fdids", choices=("icons", "tiles"),
                    help="print the icon or tile FileDataIDs of the subset (comma-separated) and write nothing")
    args = ap.parse_args(argv)

    csv_dir = Path(args.cache).expanduser() / args.build / "csv"
    if not csv_dir.is_dir():
        print("no cached csv for build %s under %s" % (args.build, args.cache), file=sys.stderr)
        return 2
    s = build_subset(csv_dir, _id_list(args.items), _id_list(args.sets), _id_list(args.ui_maps),
                     _id_list(args.areas), _id_list(args.extra_levels))
    if args.list_fdids:
        ids = icon_fdids(s) if args.list_fdids == "icons" else tile_fdids(s)
        print(",".join(str(i) for i in sorted(ids)))
        return 0
    counts = s.write(Path(args.out) / "csv")
    for name in sorted(counts):
        print("%-26s %6d" % (name, counts[name]))
    return 0
