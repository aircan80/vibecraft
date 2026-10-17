from clientdata import fixture

TABLES = {
    "Item": "ID,ClassID,IconFileDataID\n1,2,100\n2,4,200\n3,4,300\n9,4,900\n",
    "ItemSparse": ('ID,Description_lang,Display_lang,ItemLevel,ItemSet,RequiredSkill,MinFactionID,'
                   'ItemNameDescriptionID,Socket_match_enchantment_ID\n'
                   '1,"Line one\nline ""two"", with comma",Sword,10,0,0,0,0,0\n'
                   '2,,Helm,20,7,0,0,0,0\n'
                   '9,,Other,99,0,0,0,0,0\n'),
    "ItemSet": "ID,Name_lang,ItemID_0,ItemID_1\n7,Set,2,3\n8,Other,9,0\n",
    "ItemSetSpell": "ID,SpellID,Threshold,ItemSetID\n1,500,2,7\n2,900,2,8\n",
    "ItemXItemEffect": "ID,ItemEffectID,ItemID\n1,40,1\n2,49,9\n",
    "ItemEffect": "ID,SpellID\n40,400\n49,499\n",
    "Spell": ('ID,NameSubtext_lang,Description_lang,AuraDescription_lang\n'
              '400,,"Deals $s1 and $401s1, see $@spelldesc402",\n401,,,\n402,,"Uses $?s403[x][y]",\n'
              '403,,,\n404,,,\n405,,,\n499,,,\n500,,,\n900,,,\n'),
    "SpellName": "ID,Name_lang\n400,A\n401,B\n404,T\n499,X\n",
    "SpellEffect": ("ID,Effect,EffectTriggerSpell,EffectMiscValue_0,EffectRadiusIndex_0,EffectRadiusIndex_1,SpellID\n"
                    "1,6,404,0,8,0,400\n2,53,0,77,0,0,500\n3,6,0,0,9,0,499\n4,6,0,0,0,10,401\n"),
    "SpellAuraOptions": "ID,DifficultyID,ProcChance,SpellID\n1,0,5,400\n2,0,10,499\n",
    "SpellRadius": "ID,Radius\n8,10\n9,20\n10,30\n11,40\n",
    "SpellMisc": "ID,DurationIndex,SpellID\n1,3,400\n2,5,499\n",
    "SpellDuration": "ID,Duration\n3,1000\n5,2000\n",
    "SpellItemEnchantment": "ID,Name_lang,Effect_0,EffectArg_0,Effect_1,EffectArg_1,Effect_2,EffectArg_2\n77,E,1,405,0,0,0,0\n78,F,0,0,0,0,0,0\n",
    "RandPropPoints": "ID,Epic_0\n10,1\n20,2\n60,6\n99,9\n",
    "ItemDamageOneHand": "ID,ItemLevel,Quality_0\n1,10,1.5\n2,11,2.5\n3,60,6.5\n",
    "ItemClass": "ID,ClassName_lang\n2,Weapon\n4,Armor\n",
    "AreaTable": "ID,ZoneName,ContinentID,ParentAreaID\n12,Elwynn,0,0\n87,Goldshire,0,12\n88,Inn,0,87\n40,Westfall,0,0\n1,Other,1,0\n",
    "Map": "ID,Directory\n0,Azeroth\n1,Kalimdor\n",
    "UiMap": "Name_lang,ID,ParentUiMapID\nElwynn,1429,0\nOther,1,0\n",
    "UiMapXMapArt": "ID,PhaseID,UiMapArtID,UiMapID\n1,0,50,1429\n2,0,51,1\n",
    "UiMapArt": "ID,HighlightFileDataID,HighlightAtlasID,UiMapArtStyleID\n50,0,0,1\n51,0,0,2\n",
    "UiMapArtStyleLayer": "ID,LayerIndex,LayerWidth,LayerHeight,UiMapArtStyleID\n1,0,1002,668,1\n2,0,1,1,2\n",
    "UiMapArtTile": "ID,RowIndex,ColIndex,LayerIndex,FileDataID,UiMapArtID\n1,0,0,0,7000,50\n2,0,0,0,7100,51\n",
    "WorldMapOverlay": "ID,UiMapArtID,OffsetX,OffsetY\n5,50,1,1\n6,51,0,0\n",
    "WorldMapOverlayTile": "ID,RowIndex,ColIndex,LayerIndex,FileDataID,WorldMapOverlayID\n1,0,0,0,8000,5\n2,0,0,0,8100,6\n",
    "UiMapAssignment": "ID,UiMapID,MapID,AreaID\n1,1429,0,12\n2,1,1,0\n",
}


def _cache(tmp_path):
    d = tmp_path / "cache" / "B" / "csv"
    d.mkdir(parents=True)
    for name, text in TABLES.items():
        (d / (name + ".csv")).write_bytes(text.encode("utf-8"))
    return d


def _out(tmp_path, name):
    return (tmp_path / "out" / "csv" / (name + ".csv")).read_bytes().decode("utf-8")


def test_split_records_keeps_quoted_newlines():
    text = 'a,b\n1,"x\ny ""z"""\n2,c\r\n'
    assert fixture.split_records(text) == ["a,b\n", '1,"x\ny ""z"""\n', "2,c\r\n"]


def test_subset_follows_references_and_copies_rows_verbatim(tmp_path):
    csv_dir = _cache(tmp_path)
    s = fixture.build_subset(csv_dir, items=[1], sets=[7], ui_maps=[1429], areas=[12], extra_levels=[60])
    counts = s.write(tmp_path / "out" / "csv")

    # items: 1 plus set 7's pieces 2 and 3 (3 has no ItemSparse row); 9 is untouched
    assert _out(tmp_path, "Item") == "ID,ClassID,IconFileDataID\n1,2,100\n2,4,200\n3,4,300\n"
    assert '1,"Line one\nline ""two"", with comma",Sword,10,0,0,0,0,0\n' in _out(tmp_path, "ItemSparse")
    assert counts["ItemSparse"] == 2 and counts["ItemSet"] == 1 and counts["ItemSetSpell"] == 1
    # spells: effect 400, its $401 / $@spelldesc402 refs, 402's $?s403, trigger 404, set bonus 500,
    # enchantment 77 named by spell 500 and its proc spell 405
    spells = {int(l.split(",")[0]) for l in _out(tmp_path, "Spell").splitlines()[1:] if l[:1].isdigit()}
    assert spells == {400, 401, 402, 403, 404, 405, 500}
    assert _out(tmp_path, "SpellItemEnchantment").splitlines()[1:] == ["77,E,1,405,0,0,0,0"]
    assert _out(tmp_path, "SpellDuration") == "ID,Duration\n3,1000\n"
    # proc chances of the kept spells; radii named by either radius index of their effects (400: 8, 401: 10)
    assert _out(tmp_path, "SpellAuraOptions") == "ID,DifficultyID,ProcChance,SpellID\n1,0,5,400\n"
    assert _out(tmp_path, "SpellRadius") == "ID,Radius\n8,10\n10,30\n"
    # scaling rows for item levels 10 and 20 (sparse) plus 60
    assert _out(tmp_path, "RandPropPoints") == "ID,Epic_0\n10,1\n20,2\n60,6\n"
    assert _out(tmp_path, "ItemDamageOneHand") == "ID,ItemLevel,Quality_0\n1,10,1.5\n3,60,6.5\n"
    # whole lookup table; areas 12 and descendants; maps referenced
    assert _out(tmp_path, "ItemClass") == TABLES["ItemClass"]
    assert counts["AreaTable"] == 3 and counts["Map"] == 1
    # UiMap chain for 1429 only
    assert counts["UiMap"] == 1 and counts["UiMapArtTile"] == 1 and counts["WorldMapOverlayTile"] == 1
    assert fixture.icon_fdids(s) == {100, 200, 300}
    assert fixture.tile_fdids(s) == {7000, 8000}


def test_cli_writes_csv_dir_and_lists_fdids(tmp_path, capsys):
    _cache(tmp_path)
    base = ["--cache", str(tmp_path / "cache"), "--build", "B", "--items", "1", "--ui-maps", "1429"]
    assert fixture.main(base + ["--out", str(tmp_path / "x"), "--list-fdids", "tiles"]) == 0
    assert capsys.readouterr().out.strip() == "7000,8000"
    assert not (tmp_path / "x").exists()
    assert fixture.main(base + ["--out", str(tmp_path / "out")]) == 0
    assert (tmp_path / "out" / "csv" / "Item.csv").exists()
    assert fixture.main(["--cache", str(tmp_path), "--build", "nope", "--out", "o", "--items", "1"]) == 2


ERA = ("ID,Description_lang,Display_lang,ItemLevel,ItemSet,RequiredSkill,MinFactionID,"
       "ItemNameDescriptionID,Socket_match_enchantment_ID,Extra\n"
       "1,,Sword (era),11,0,0,0,0,0,x\n"
       "3,,Pants,30,8,0,0,0,0,y\n"
       "9,,Other,99,0,0,0,0,0,z\n")


def test_era_rows_are_kept_for_subset_items_and_feed_the_level_closure(tmp_path):
    csv_dir = _cache(tmp_path)
    (csv_dir / "ItemSparse.era.csv").write_bytes(ERA.encode("utf-8"))
    (csv_dir / "RandPropPoints.csv").write_bytes(b"ID,Epic_0\n10,1\n11,1\n20,2\n30,3\n60,6\n99,9\n")
    s = fixture.build_subset(csv_dir, items=[1], sets=[7], extra_levels=[60])
    counts = s.write(tmp_path / "out" / "csv")

    # every subset item the Era table has (1 overlaps Forever, 3 is Item-only); 9 is not in the subset
    assert _out(tmp_path, "ItemSparse.era") == ERA.splitlines(keepends=True)[0] + "1,,Sword (era),11,0,0,0,0,0,x\n" \
        + "3,,Pants,30,8,0,0,0,0,y\n"
    assert counts["ItemSparse.era"] == 2
    # level 30 comes from the Era row of item 3; item 1's Era level 11 is ignored (Forever wins)
    assert _out(tmp_path, "RandPropPoints") == "ID,Epic_0\n10,1\n20,2\n30,3\n60,6\n"
    # Era rows never widen the subset: set 8 (and its piece 9) stay out
    assert counts["ItemSet"] == 1 and counts["Item"] == 3


def test_no_era_table_in_the_cache_writes_none(tmp_path):
    csv_dir = _cache(tmp_path)
    counts = fixture.build_subset(csv_dir, items=[1]).write(tmp_path / "out" / "csv")
    assert "ItemSparse.era" not in counts
    assert not (tmp_path / "out" / "csv" / "ItemSparse.era.csv").exists()


# the Era layout: no ItemXItemEffect, each effect names its item in ParentItemID
ERA_EFFECTS = "ID,TriggerType,SpellID,ParentItemID\n41,1,401,1\n43,1,900,3\n44,1,950,3\n49,1,499,9\n"


def test_era_effects_are_kept_for_era_sourced_items_and_feed_the_spell_closure(tmp_path):
    csv_dir = _cache(tmp_path)
    (csv_dir / "ItemSparse.era.csv").write_bytes(ERA.encode("utf-8"))
    (csv_dir / "ItemEffect.era.csv").write_bytes(ERA_EFFECTS.encode("utf-8"))
    s = fixture.build_subset(csv_dir, items=[1], sets=[7], extra_levels=[60])
    counts = s.write(tmp_path / "out" / "csv")

    # only item 3 is Era-sourced (1 has a Forever row, 9 is outside the subset)
    assert _out(tmp_path, "ItemEffect.era") == "ID,TriggerType,SpellID,ParentItemID\n43,1,900,3\n44,1,950,3\n"
    assert counts["ItemEffect.era"] == 2
    # Era effect spells come from Forever's spell tables: 900 is kept, 950 has no Forever row (nothing to keep)
    spells = {int(l.split(",")[0]) for l in _out(tmp_path, "Spell").splitlines()[1:] if l[:1].isdigit()}
    assert 900 in spells and 950 not in spells


def test_no_era_effect_table_in_the_cache_writes_none(tmp_path):
    csv_dir = _cache(tmp_path)
    (csv_dir / "ItemSparse.era.csv").write_bytes(ERA.encode("utf-8"))
    counts = fixture.build_subset(csv_dir, items=[1], sets=[7]).write(tmp_path / "out" / "csv")
    assert "ItemSparse.era" in counts
    assert "ItemEffect.era" not in counts


def _append(csv_dir, name, text):
    with open(csv_dir / (name + ".csv"), "ab") as f:
        f.write(text.encode("utf-8"))


def _craft_cache(tmp_path):
    csv_dir = _cache(tmp_path)
    # spell 700 creates item 50 (a craft named by --crafts), 701 is a craft outside the subset
    _append(csv_dir, "Spell", "700,,,\n701,,,\n")
    _append(csv_dir, "SpellName", "700,Craft\n701,Other craft\n")
    _append(csv_dir, "SpellEffect", "5,24,0,0,0,0,700\n6,24,0,0,0,0,701\n")
    (csv_dir / "SpellReagents.csv").write_bytes(
        b"ID,SpellID,Reagent_0,ReagentCount_0\n1,700,60,5\n2,701,61,1\n3,400,62,2\n")
    (csv_dir / "SkillLineAbility.csv").write_bytes(
        b"ID,SkillLine,Spell,MinSkillLineRank\n10,165,700,1\n11,164,701,1\n12,185,400,1\n")
    (csv_dir / "SkillLine.csv").write_bytes(b"DisplayName_lang,ID\nBlacksmithing,164\nLeatherworking,165\n"
                                            b"Cooking,185\nTailoring,197\n")
    return csv_dir


def test_crafts_join_the_spell_closure_and_keep_their_reagents_and_skill_rows(tmp_path):
    csv_dir = _craft_cache(tmp_path)
    s = fixture.build_subset(csv_dir, items=[1], crafts=[700])
    counts = s.write(tmp_path / "out" / "csv")

    spells = {int(l.split(",")[0]) for l in _out(tmp_path, "Spell").splitlines()[1:] if l[:1].isdigit()}
    assert 700 in spells and 701 not in spells
    assert "5,24,0,0,0,0,700" in _out(tmp_path, "SpellEffect").splitlines()
    # reagents and skill rows of every kept spell: the craft 700 and the item's spell 400, not 701
    assert _out(tmp_path, "SpellReagents") == "ID,SpellID,Reagent_0,ReagentCount_0\n1,700,60,5\n3,400,62,2\n"
    assert _out(tmp_path, "SkillLineAbility") == "ID,SkillLine,Spell,MinSkillLineRank\n10,165,700,1\n12,185,400,1\n"
    assert counts["SpellReagents"] == 2 and counts["SkillLineAbility"] == 2


def test_skill_race_class_info_is_written_header_only(tmp_path):
    # the importer does not read it, so the cut keeps no rows (the committed fixture CSV is header-only)
    csv_dir = _craft_cache(tmp_path)
    (csv_dir / "SkillRaceClassInfo.csv").write_bytes(b"ID,SkillID,ClassMask\n1,165,0\n")
    counts = fixture.build_subset(csv_dir, items=[1], crafts=[700]).write(tmp_path / "out" / "csv")
    assert _out(tmp_path, "SkillRaceClassInfo") == "ID,SkillID,ClassMask\n"
    assert counts["SkillRaceClassInfo"] == 0


def test_a_large_skill_line_table_keeps_the_kept_crafts_skill_lines(tmp_path, monkeypatch):
    csv_dir = _craft_cache(tmp_path)
    monkeypatch.setattr(fixture, "WHOLE_TABLE_LIMIT", 0)
    fixture.build_subset(csv_dir, items=[1], crafts=[700]).write(tmp_path / "out" / "csv")
    assert _out(tmp_path, "SkillLine") == "DisplayName_lang,ID\nLeatherworking,165\nCooking,185\n"


def test_cli_takes_crafts(tmp_path):
    _craft_cache(tmp_path)
    assert fixture.main(["--cache", str(tmp_path / "cache"), "--build", "B", "--items", "1", "--crafts", "700",
                         "--out", str(tmp_path / "out")]) == 0
    assert "1,700,60,5" in _out(tmp_path, "SpellReagents")
