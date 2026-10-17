"""WoWDBDefs (.dbd) definitions: pick the layout of one build and decode a hotfix record with it.

A .dbd file starts with a ``COLUMNS`` section (``type Name``, the type ``int``, ``float``, ``string`` or ``locstring``,
optionally ``int<Foreign::ID>``) followed by blank-line separated layout blocks. A block lists ``LAYOUT``, ``BUILD``
(single builds or ``lo-hi`` ranges, comma separated) and ``COMMENT`` lines, then its fields in record order:
``$annotations$Name<size>[array]`` with an optional ``// comment``; ``<u32>`` is unsigned, ``[10]`` an array.

A hotfix record (DBCache.bin) holds the fields in layout order: integers little-endian at their size, floats as 4-byte
IEEE, strings NUL-terminated UTF-8, arrays element by element. The ``$noninline,id$`` field is not in the record (the
record header carries it); ``$noninline,relation$`` fields are refused, since no table read here has one.

The CSV columns are the field names with arrays expanded to ``Name_0..Name_{n-1}``, as wago.tools exports them.
"""
import re
import struct
from typing import Dict, List, NamedTuple, Optional

DBD_BASE_URL = "https://raw.githubusercontent.com"
DBD_REPO = "/wowdev/WoWDBDefs"
# The WoWDBDefs commit the definitions are read at (master on 2026-10-17; lists 1.60.1.69876 to 1.60.1.70450).
DBD_COMMIT = "b9a63096cdd99b26967f17f9515377ae62cadfd7"

_FIELD = re.compile(r"(?:\$([^$]*)\$)?([A-Za-z_][A-Za-z0-9_]*)(?:<(u?)(\d+)>)?(?:\[(\d+)\])?")
_INT_FORMATS = {8: "b", 16: "h", 32: "i", 64: "q"}


class DbdError(ValueError):
    """A definition that cannot be parsed, has no layout for the build, or does not fit a record."""


class Field(NamedTuple):
    name: str
    type: str  # int, float, string, locstring
    size: Optional[int]  # bits, ints only
    unsigned: bool
    array: Optional[int]
    annotations: tuple

    @property
    def inline(self) -> bool:
        return "noninline" not in self.annotations

    @property
    def is_id(self) -> bool:
        return "id" in self.annotations


def dbd_path(table: str, commit: str = DBD_COMMIT) -> str:
    """The raw.githubusercontent.com path of a table's definition at ``commit``."""
    return "%s/%s/definitions/%s.dbd" % (DBD_REPO, commit, table)


def _version(build: str):
    return tuple(int(p) for p in build.split("."))


def _matches(build_line: str, build: str) -> bool:
    want = _version(build)
    for part in build_line.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            lo, hi = part.split("-", 1)
            if _version(lo.strip()) <= want <= _version(hi.strip()):
                return True
        elif _version(part) == want:
            return True
    return False


def layout(text: str, build: str) -> List[Field]:
    """The fields of the layout block that lists ``build``. Raises DbdError when no block does."""
    lines = text.replace("\r\n", "\n").split("\n")
    if not lines or lines[0].strip() != "COLUMNS":
        raise DbdError("not a .dbd definition: the first line is not COLUMNS")
    types: Dict[str, str] = {}
    i = 1
    while i < len(lines) and lines[i].strip():
        decl = lines[i].split("//", 1)[0].strip()
        if decl:
            parts = decl.split(None, 1)
            if len(parts) != 2:
                raise DbdError("bad column line %r" % lines[i])
            types[parts[1].rstrip("?")] = re.sub(r"<.*>", "", parts[0])
        i += 1
    for block in "\n".join(lines[i:]).split("\n\n"):
        rows = [r for r in block.split("\n") if r.strip()]
        if not any(r.startswith("BUILD ") and _matches(r[len("BUILD "):], build) for r in rows):
            continue
        fields = []
        for r in rows:
            if r.startswith(("BUILD ", "LAYOUT ", "COMMENT")):
                continue
            decl = r.split("//", 1)[0].strip()
            m = _FIELD.fullmatch(decl)
            if m is None:
                raise DbdError("bad field line %r" % r)
            name = m.group(2)
            if name not in types:
                raise DbdError("field %s has no COLUMNS entry" % name)
            ann = tuple(a for a in (m.group(1) or "").split(",") if a)
            fields.append(Field(name, types[name], int(m.group(4)) if m.group(4) else None, m.group(3) == "u",
                                int(m.group(5)) if m.group(5) else None, ann))
        return fields
    raise DbdError("no layout lists build %s" % build)


def columns(fields: List[Field]) -> List[str]:
    """The CSV header: field names, arrays expanded to ``Name_0..``."""
    out = []
    for f in fields:
        if f.array:
            out.extend("%s_%d" % (f.name, k) for k in range(f.array))
        else:
            out.append(f.name)
    return out


def decode(fields: List[Field], data: bytes, record_id: int) -> Dict[str, object]:
    """One record's values by CSV column; the non-inline id is ``record_id``. DbdError when the bytes do not fit."""
    out: Dict[str, object] = {}
    off = 0
    try:
        for f in fields:
            if not f.inline:
                if f.is_id:
                    out[f.name] = record_id
                    continue
                raise DbdError("non-inline field %s is not supported" % f.name)
            values = []
            for _ in range(f.array or 1):
                if f.type in ("string", "locstring"):
                    end = data.index(b"\0", off)
                    values.append(data[off:end].decode("utf-8"))
                    off = end + 1
                elif f.type == "float":
                    values.append(struct.unpack_from("<f", data, off)[0])
                    off += 4
                else:
                    if f.size not in _INT_FORMATS:
                        raise DbdError("field %s has no integer size" % f.name)
                    fmt = _INT_FORMATS[f.size]
                    values.append(struct.unpack_from("<" + (fmt.upper() if f.unsigned else fmt), data, off)[0])
                    off += f.size // 8
            if f.array:
                for k, v in enumerate(values):
                    out["%s_%d" % (f.name, k)] = v
            else:
                out[f.name] = values[0]
    except (struct.error, ValueError, UnicodeDecodeError) as e:
        if isinstance(e, DbdError):
            raise
        raise DbdError("record %d does not fit the layout: %s" % (record_id, e)) from e
    if off != len(data):
        raise DbdError("record %d: layout reads %d bytes, record has %d" % (record_id, off, len(data)))
    return out


def format_value(v) -> str:
    """A CSV cell as wago.tools writes it: floats as float32 with 11 significant digits, ints and strings as is."""
    if isinstance(v, float):
        s = "%.11g" % v
        return "0" if s in ("0", "-0") else s
    return str(v)
