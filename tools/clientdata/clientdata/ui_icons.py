"""Client UI icon textures for the browser's navigation rail (spec section 5; client art is allowed for icons only).

Each rail icon names a first and a second choice of client texture. The names are resolved to FileDataIDs with the
client's own ``ManifestInterfaceData`` table (``ID, FilePath, FileName``), matched case-insensitively on the whole
path ``FilePath + FileName``. Two PNG variants are written per icon: ``<name>.png`` at native size and
``<name>@24.png``, 24 px (see ``small_variant``).
"""
import csv
from pathlib import Path
from typing import Dict, Iterable, List, NamedTuple, Tuple

from PIL import Image

MANIFEST_TABLE = "ManifestInterfaceData"

# (name, first choice, second choice), paths without the .blp extension.
UI_ICONS: Tuple[Tuple[str, str, str], ...] = (
    ("rail.home", "Interface/Icons/INV_Misc_Rune_01", "Interface/Icons/Spell_Nature_AstralRecal"),
    ("rail.items", "Interface/Icons/INV_Misc_Bag_08", "Interface/Buttons/Button-Backpack-Up"),
    ("rail.npcs", "Interface/GossipFrame/GossipGossipIcon", "Interface/Icons/INV_Misc_Head_Human_01"),
    ("rail.quests", "Interface/GossipFrame/AvailableQuestIcon", "Interface/Icons/INV_Misc_Note_01"),
    ("rail.zones", "Interface/Icons/INV_Misc_Map_01", "Interface/WorldMap/UI-World-Icon"),
    ("rail.instances", "Interface/Icons/INV_Misc_Head_Dragon_01", "Interface/Icons/INV_Misc_Key_03"),
)

SMALL_PX = 24
SMALL_SUFFIX = "@24"
# Interface/Icons art has a decorative border; the 24 px variant keeps the central 86 % (55 px of 64).
CROP_FRACTION = 0.86
ICON_ART_PREFIX = "interface/icons/"


def normalise(path: str) -> str:
    """Forward slashes, lower case, no leading slash: the key both the manifest and the choices are matched on."""
    return path.replace("\\", "/").lower().lstrip("/")


def load_manifest(csv_path) -> Dict[str, int]:
    """``ManifestInterfaceData.csv`` as {normalised FilePath + FileName: FileDataID}."""
    out = {}
    with open(csv_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            fdid = (row.get("ID") or "").strip()
            name = (row.get("FileName") or "").strip()
            if not fdid.isdigit() or not name:
                continue
            folder = (row.get("FilePath") or "").strip().replace("\\", "/").rstrip("/")
            out[normalise(folder + "/" + name if folder else name)] = int(fdid)
    return out


class Resolution(NamedTuple):
    resolved: List[dict]  # {name, path, fdid, choice: "first" | "second"}, in UI_ICONS order
    unresolved: List[str]  # names neither of whose choices is in the manifest
    messages: List[str]  # one line per fallback and per unresolved icon


def resolve(manifest: Dict[str, int], icons: Iterable[Tuple[str, str, str]] = UI_ICONS) -> Resolution:
    resolved, unresolved, messages = [], [], []
    for name, first, second in icons:
        f = manifest.get(normalise(first + ".blp"))
        if f is not None:
            resolved.append({"name": name, "path": first, "fdid": f, "choice": "first"})
            continue
        s = manifest.get(normalise(second + ".blp"))
        if s is not None:
            resolved.append({"name": name, "path": second, "fdid": s, "choice": "second"})
            messages.append("ui icon %s: first choice %s not in %s, using the second choice %s (%d)"
                            % (name, first, MANIFEST_TABLE, second, s))
            continue
        unresolved.append(name)
        messages.append("error: ui icon %s: neither %s nor %s is in %s; skipped"
                        % (name, first, second, MANIFEST_TABLE))
    return Resolution(resolved, unresolved, messages)


def is_icon_art(path: str) -> bool:
    return normalise(path).startswith(ICON_ART_PREFIX)


def crop_box(width: int, height: int, fraction: float = CROP_FRACTION) -> Tuple[int, int, int, int]:
    """The central ``fraction`` of a width x height image as a Pillow box: 64 x 64 gives (4, 4, 59, 59), 55 px."""
    w, h = round(width * fraction), round(height * fraction)
    left, top = (width - w) // 2, (height - h) // 2
    return left, top, left + w, top + h


def small_variant(img: Image.Image, path: str, size: int = SMALL_PX) -> Image.Image:
    """The 24 px RGBA variant.

    Interface/Icons art: the central 86 % (``crop_box``), resized to ``size`` bicubic. Any other art (GossipFrame,
    Buttons, WorldMap): centred at native size on a transparent ``size`` canvas, downscaled bicubic keeping its
    aspect only when larger than ``size``.
    """
    img = img.convert("RGBA")
    if is_icon_art(path):
        return img.crop(crop_box(img.width, img.height)).resize((size, size), Image.BICUBIC)
    if img.width > size or img.height > size:
        scale = size / max(img.width, img.height)
        img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.BICUBIC)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(img, ((size - img.width) // 2, (size - img.height) // 2))
    return canvas


def convert_ui_icon(blp_path, out_dir, name: str, path: str) -> None:
    """Writes ``out_dir/<name>.png`` (native size, RGBA) and ``out_dir/<name>@24.png``."""
    with Image.open(blp_path) as im:
        im.load()
        img = im.convert("RGBA")
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    img.save(out_dir / (name + ".png"), format="PNG")
    small_variant(img, path).save(out_dir / (name + SMALL_SUFFIX + ".png"), format="PNG")
