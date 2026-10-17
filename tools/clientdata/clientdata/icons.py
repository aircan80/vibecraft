"""BLP icon -> 56 px PNG."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ICON_SIZE = 56
# Client icons are 64x64 with a ~4 px decorative border baked in; it is cropped
# on every side (scaled proportionally for other source sizes).
ICON_BORDER_PX = 4
ICON_SOURCE_PX = 64


def convert_icon(blp_path, out_png, size=ICON_SIZE, crop_px=None) -> None:
    with Image.open(blp_path) as im:
        im.load()
        img = im.convert("RGBA")
    if crop_px is None:
        crop_px = round(ICON_BORDER_PX * img.width / ICON_SOURCE_PX)
    if crop_px > 0:
        img = img.crop((crop_px, crop_px, img.width - crop_px, img.height - crop_px))
    img = img.resize((size, size), Image.LANCZOS)
    _save_png(img, out_png)


def fallback_png(out_png, size=ICON_SIZE) -> None:
    img = Image.new("RGBA", (size, size), (32, 32, 36, 255))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.load_default(size=int(size * 0.7))
    except TypeError:  # Pillow < 10.1: fixed-size bitmap font
        font = ImageFont.load_default()
    draw.text((size / 2, size / 2), "?", fill=(200, 200, 200, 255), font=font, anchor="mm")
    _save_png(img, out_png)


def _save_png(img, out_png):
    out_png = Path(out_png)
    out_png.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_png, format="PNG")
