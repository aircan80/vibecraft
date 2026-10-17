import json

import pytest
from PIL import Image

import convert as convert_cli
from clientdata import fetch, tables, ui_icons

BUILD = "1.60.1.70425"

# The manifest's own shape: FilePath with backslashes and a trailing separator, FileName with the extension, any case.
MANIFEST = (
    "ID,FilePath,FileName\n"
    "134414,Interface\\ICONS\\,INV_MISC_RUNE_01.BLP\n"
    "136010,Interface\\ICONS\\,Spell_Nature_AstralRecal.blp\n"
    "130716,Interface\\BUTTONS\\,Button-Backpack-Up.blp\n"
    "3532317,Interface\\GossipFrame\\,CampaignAvailableQuestIcon.blp\n"
    "134327,Interface\\ICONS\\,INV_Misc_Note_01.blp\n"
)

ICONS = (
    ("rail.home", "Interface/Icons/INV_Misc_Rune_01", "Interface/Icons/Spell_Nature_AstralRecal"),
    ("rail.items", "Interface/Icons/INV_Misc_Bag_08", "Interface/Buttons/Button-Backpack-Up"),
    ("rail.quests", "Interface/GossipFrame/AvailableQuestIcon", "Interface/Icons/INV_Misc_Note_01"),
    ("rail.zones", "Interface/Icons/INV_Misc_Map_01", "Interface/WorldMap/UI-World-Icon"),
)


@pytest.fixture
def manifest(tmp_path):
    p = tmp_path / "ManifestInterfaceData.csv"
    p.write_text(MANIFEST, encoding="utf-8")
    return ui_icons.load_manifest(p)


def test_the_rail_needs_six_icons_each_with_two_choices():
    assert [n for n, _, _ in ui_icons.UI_ICONS] == [
        "rail.home", "rail.items", "rail.npcs", "rail.quests", "rail.zones", "rail.instances"]
    assert dict((n, (a, b)) for n, a, b in ui_icons.UI_ICONS)["rail.quests"] == (
        "Interface/GossipFrame/AvailableQuestIcon", "Interface/Icons/INV_Misc_Note_01")


def test_manifest_table_is_fetched_with_the_others():
    assert ui_icons.MANIFEST_TABLE == "ManifestInterfaceData"
    assert ui_icons.MANIFEST_TABLE in tables.REQUIRED_TABLES


def test_manifest_keys_are_full_paths_without_case_or_separator_differences(manifest):
    assert manifest["interface/icons/inv_misc_rune_01.blp"] == 134414
    assert manifest["interface/buttons/button-backpack-up.blp"] == 130716


def test_first_choice_found_case_insensitively(manifest):
    r = ui_icons.resolve(manifest, ICONS[:1])
    assert r.resolved == [{"name": "rail.home", "path": "Interface/Icons/INV_Misc_Rune_01",
                           "fdid": 134414, "choice": "first"}]
    assert r.unresolved == [] and r.messages == []


def test_missing_first_choice_falls_back_to_the_second_and_logs_it(manifest):
    r = ui_icons.resolve(manifest, ICONS[1:2])
    assert r.resolved == [{"name": "rail.items", "path": "Interface/Buttons/Button-Backpack-Up",
                           "fdid": 130716, "choice": "second"}]
    assert len(r.messages) == 1
    assert "rail.items" in r.messages[0] and "INV_Misc_Bag_08" in r.messages[0] and "second" in r.messages[0]


def test_a_path_is_matched_whole_not_as_a_suffix(manifest):
    # CampaignAvailableQuestIcon must not satisfy AvailableQuestIcon
    r = ui_icons.resolve(manifest, ICONS[2:3])
    assert r.resolved[0]["fdid"] == 134327 and r.resolved[0]["choice"] == "second"


def test_neither_choice_present_skips_the_icon_and_counts_it(manifest):
    r = ui_icons.resolve(manifest, ICONS)
    assert [e["name"] for e in r.resolved] == ["rail.home", "rail.items", "rail.quests"]
    assert r.unresolved == ["rail.zones"]
    assert any("rail.zones" in m and "skipped" in m for m in r.messages)


def test_crop_box_is_the_central_86_percent():
    # 86 % of 64 is 55.04: a 55 px box, 4 px in from the top left
    assert ui_icons.crop_box(64, 64) == (4, 4, 59, 59)
    left, top, right, bottom = ui_icons.crop_box(64, 64)
    assert right - left == 55 and bottom - top == 55
    assert ui_icons.crop_box(56, 56) == (4, 4, 52, 52)  # 48.16 -> 48


def _image(w, h, colour=(200, 40, 40, 255)):
    return Image.new("RGBA", (w, h), colour)


def test_icon_art_is_cropped_then_resized_to_24():
    img = _image(64, 64, (0, 0, 255, 255))
    for x in range(64):  # a 3 px red border, outside the 55 px box
        for y in (0, 1, 2, 61, 62, 63):
            img.putpixel((x, y), (255, 0, 0, 255))
            img.putpixel((y, x), (255, 0, 0, 255))
    small = ui_icons.small_variant(img, "Interface/Icons/INV_Misc_Rune_01")
    assert small.size == (24, 24) and small.mode == "RGBA"
    assert small.getpixel((0, 0)) == (0, 0, 255, 255)  # the border was cropped away


def test_frame_art_is_centred_at_native_size_on_a_transparent_canvas():
    small = ui_icons.small_variant(_image(16, 16), "Interface/GossipFrame/AvailableQuestIcon")
    assert small.size == (24, 24)
    assert small.getpixel((0, 0))[3] == 0 and small.getpixel((3, 3))[3] == 0
    assert small.getpixel((4, 4)) == (200, 40, 40, 255) and small.getpixel((19, 19)) == (200, 40, 40, 255)
    assert small.getpixel((20, 20))[3] == 0


def test_frame_art_larger_than_24_is_downscaled_keeping_its_aspect():
    small = ui_icons.small_variant(_image(64, 32), "Interface/Buttons/Button-Backpack-Up")
    assert small.size == (24, 24)
    assert small.getpixel((12, 0))[3] == 0 and small.getpixel((12, 23))[3] == 0  # 24x12, centred vertically
    assert small.getpixel((0, 6))[3] == 255 and small.getpixel((23, 17))[3] == 255


def test_icon_art_is_recognised_by_path_case_insensitively():
    assert ui_icons.is_icon_art("Interface/Icons/INV_Misc_Map_01")
    assert ui_icons.is_icon_art("interface\\ICONS\\x")
    assert not ui_icons.is_icon_art("Interface/WorldMap/UI-World-Icon")


def test_lock_records_the_ui_icons_and_later_runs_keep_them(tmp_path):
    csv_dir = tmp_path / "B" / "csv"
    csv_dir.mkdir(parents=True)
    a = csv_dir / "ItemClass.csv"
    a.write_bytes(b"ID\n1\n")
    entries = [{"name": "rail.home", "path": "Interface/Icons/INV_Misc_Rune_01", "fdid": 134414, "choice": "first"}]
    fetch.write_lock(tmp_path, "B", {"ItemClass": a}, product="p", base_url="u", ui_icons=entries)
    assert json.loads((tmp_path / "B" / "build.lock").read_text())["ui_icons"] == entries
    fetch.write_lock(tmp_path, "B", {"ItemClass": a}, product="p", base_url="u")
    assert json.loads((tmp_path / "B" / "build.lock").read_text())["ui_icons"] == entries


def _write_blp_stand_in(path, size):
    # Pillow cannot write BLP; the converter opens files with Image.open, which reads a PNG under any name.
    path.parent.mkdir(parents=True, exist_ok=True)
    _image(*size).save(path, format="PNG")


def _ui_cache(tmp_path):
    b = tmp_path / "cache" / BUILD
    (b / "csv").mkdir(parents=True)
    (b / "csv" / "Item.csv").write_text("ID,IconFileDataID\n1,134414\n", encoding="utf-8")
    _write_blp_stand_in(b / "files" / "134414.blp", (64, 64))
    _write_blp_stand_in(b / "files" / "132049.blp", (16, 16))
    lock = {"build": BUILD, "tables": {}, "ui_icons": [
        {"name": "rail.home", "path": "Interface/Icons/INV_Misc_Rune_01", "fdid": 134414, "choice": "first"},
        {"name": "rail.quests", "path": "Interface/GossipFrame/AvailableQuestIcon", "fdid": 132049,
         "choice": "first"},
        {"name": "rail.zones", "path": "Interface/Icons/INV_Misc_Map_01", "fdid": 134269, "choice": "first"},
    ]}
    (b / "build.lock").write_text(json.dumps(lock), encoding="utf-8")
    return tmp_path / "cache"


def test_convert_writes_both_variants_of_every_cached_ui_icon(tmp_path, capsys):
    out = tmp_path / "o"
    assert convert_cli.main(["--cache", str(_ui_cache(tmp_path)), "--build", BUILD, "--out", str(out)]) == 0
    summary = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert summary["ui_icons"] == 2 and summary["ui_icons_missing"] == 1
    names = sorted(p.name for p in (out / "ui_icons").iterdir())
    assert names == ["rail.home.png", "rail.home@24.png", "rail.quests.png", "rail.quests@24.png"]
    with Image.open(out / "ui_icons" / "rail.home.png") as im:
        assert im.size == (64, 64) and im.mode == "RGBA"
    with Image.open(out / "ui_icons" / "rail.quests.png") as im:
        assert im.size == (16, 16)
    for n in ("rail.home@24.png", "rail.quests@24.png"):
        with Image.open(out / "ui_icons" / n) as im:
            assert im.size == (24, 24) and im.mode == "RGBA"


def test_convert_ui_icons_only_writes_nothing_else(tmp_path):
    out = tmp_path / "o"
    assert convert_cli.main(["--cache", str(_ui_cache(tmp_path)), "--build", BUILD, "--out", str(out),
                             "--ui-icons-only"]) == 0
    assert sorted(p.name for p in out.iterdir()) == ["build.lock", "ui_icons"]
    assert len(list((out / "ui_icons").iterdir())) == 4
