# Task 6 report: spell description formatter, item effects, sets and tooltip lines

**Status:** DONE_WITH_CONCERNS
**Commit** (not pushed): `d017804 feat(importer): spell description formatting, item effects, sets and tooltip lines`

## Outcome

- **Every importer test passes, including the extended known-items gate.**
  - The gate now compares six text kinds for all 28 rows: binding, slot, required level, Use, Equip and Chance on hit.
  - Every row matches Wowhead's Forever tooltip, on the fixture and on the real cached build.
  - One documented exclusion: 4134's caster spell-power Equip line.
- **Builds.**
  - `./mvnw -B -q -pl importer test` passes 297 tests with 0 failures and 0 skips; the cache-gated tests ran.
  - `./mvnw -B -q verify` exits 0.
  - `pytest tools/clientdata` gives 34 passed.
- **Real data:** 14,875 Questie items rendered in 0.9 s.

  | Counter | Value |
  |---|---|
  | `clientdata.tooltip.items` | 14875 |
  | `clientdata.tooltip.lines` | 121559 |
  | `clientdata.effects` | 3100 |
  | `clientdata.effects.otherTrigger` | 0 |
  | `clientdata.sets` | 139 |
  | `clientdata.unformatted` | 96 spells |
  | `clientdata.tooltip.missingKind.durability` | 1 |

## What was implemented

All new classes are in `importer/src/main/java/dev/wowforever/importer/clientdata/`.

- **`SpellTables`.** Loads `Spell`, `SpellName`, `SpellEffect` (by spell and 0-based index), `SpellMisc` and `SpellDuration`.
  - Rows with `DifficultyID` 0 are preferred.
  - `SpellRadius` is used when present. It is not fetched today.
- **`SpellDescriptionFormatter`.** `format(spellId, template)` and `description(spellId)` return `Formatted(text, complete, unresolved)`.
  - **`$sN`:** value ranges come from `Variance` ("70 to 90"). Values print as magnitudes. With a sign right before the placeholder, a negative value turns that sign into `-`.
  - **`$m`, `$M`, `$b`:** base value, maximum, and points per combo point.
  - **`$oN`:** `base * duration / EffectAuraPeriod`. The regeneration auras 84 and 85 have no period in the data, so they tick every 5 s, as in the game. That gives "61 health over 18 sec".
  - **`$d`/`$D`:** "15 sec", "1.5 min", "30 min", "1 hour", "2 hrs", "1 day".
  - **Other placeholders:**
    - `$t`, `$x` and `$a` (radius, when `SpellRadius` exists).
    - `$/N;` and `$*N;`.
    - `${expr}`, with `+ - * /`, parentheses, values, `$pl` at level 60, and `$max $min $floor $ceil $abs $round`, plus a `.N` precision.
    - `$<id>…` cross-spell references; `$@spelldesc`, `$@spellname` and `$@spelltooltip`; `$@spellicon` prints nothing.
    - `$l…:…;` follows the preceding number; `$g…:…;` takes the first form.
  - **`$?cond[a]?cond[b][else]`:** spell, aura and class conditions are false, since no player is known. `$pl` comparisons are evaluated at 60.
  - **Markup:** `|cff……`, `|cn…:`, `|r`, `|T…|t` and `|H…|h` are stripped. CRLF, `|n` and a literal `\n` become line breaks.
  - **Unknown placeholders** stay raw and make the result incomplete. `report()` counts `clientdata.unformatted` and adds two notes:
    - the first 50 spells with their tokens;
    - `clientdata.unformatted.tokens`, a tally by token kind.
- **`ItemSpellEffect` and `ItemEffectLoader`.** The loader follows `ItemXItemEffect` to `ItemEffect` and formats `Spell.Description_lang`.
  - Triggers 0, 1 and 2 are kept, ordered by `LegacySlotIndex` then id.
  - The cooldown is `CoolDownMSec`, else `CategoryCoolDownMSec`. Charges are kept.
  - **Trigger 6 teaches a spell (recipes and formulae).** The empty "Learning" Use effect is replaced by one Use effect for the taught spell.
    - Its text is the item's own description, formatted for that spell. Formula 215129's description is itself a template.
    - That description is then not shown as flavour text.
  - Other triggers are skipped and counted as `clientdata.effects.otherTrigger`.
- **`ItemSetInfo` and `ItemSetLoader`.** Reads `ItemSet` (name, required skill, `ItemID_0..16`) and `ItemSetSpell` (bonuses by ascending threshold, formatted text). Only the requested sets are loaded.
- **`TooltipLine(ordinal, kind, text, colour)`.** Also holds the colour constants and `qualityColour`.
- **Small lookups:**
  - `BindingNames` covers bonding values 1 to 4.
  - `InventoryTypeNames` maps slots to names, with robe as "Chest" and shield as "Off Hand".
  - `ClassRaceNames` reads `ChrClasses` and `ChrRaces` and tests masks by bit.
  - `TooltipNames` covers subclass display names, `SkillLine`, `Faction` and item names.
- **`StatText`.** Phrases computed stats as Wowhead's Forever tooltips do (details under "Decisions" below).
- **`TooltipBuilder.build(item, stats, damage, armor, effects, set, names)`.** Kinds and colours follow spec 4.3 plus the rulings. `KINDS` is the published order.
- **`ItemTooltips.build(src, items, values, questieNames, report)`.** The orchestrator Task 9 should call. It returns effects, sets and lines, and emits the counters.
- **`ClientItem`.** Gains `Details details`: `MaxCount`, `MaxDurability` (Era table only), `ContainerSlots`, `MinFactionID`, `MinReputation`, `DurationInInventory` and `AllowableRace_1`.
  - `Absolute` gains `extraMinDamage` and `extraMaxDamage`, from `MinDamage_1` and `MaxDamage_1`.
  - `CsvTable.Row` gains `table()`.
- **`SqliteWriter.write(..., values, ItemTooltips)`.** A new 7-argument overload; the 6-argument one passes `ItemTooltips.empty()`.
  - Fills `item_effect` and `item_tooltip_line` for item rows.
  - Fills `item_set`, `item_set_item` and `item_set_bonus` for every set given.

## Five sample tooltips (real cached build 1.60.1.70425)

These come from `ItemTooltips.build` over all 14,889 Questie items. `<TAB>` is the slot line's tab.

**17204 Eye of Sulfuras (Forever)**
```
 0 name         #ff8000  Eye of Sulfuras
 1 binding      #ffffff  Binds when picked up
 2 use          #1eff00  Use: The Eye of Sulfuras can be combined with the Sulfuron Hammer to create Sulfuras, legendary hammer of Ragnaros.
 3 item_level   #9d9d9d  Item Level 60
 4 sell_price   #9d9d9d  Sell Price: 20g
```

**19019 Thunderfury (Forever row).** The Proc and resist lines are here. There is no `damage_extra`: Forever's row has no second range, and Wowhead Forever shows none.
```
 0 name         #ff8000  Thunderfury, Blessed Blade of the Windseeker
 1 binding      #ffffff  Binds when picked up
 2 unique       #ffffff  Unique
 3 slot         #ffffff  One-Hand <TAB> Sword
 4 damage       #ffffff  82 - 153 Damage
 5 speed        #ffffff  Speed 1.90
 6 dps          #ffffff  (61.84 damage per second)
 7 stat         #ffffff  +5 Agility
 8 stat         #ffffff  +8 Stamina
 9 resist       #ffffff  +8 Fire Resistance
10 resist       #ffffff  +9 Nature Resistance
11 req_level    #ffffff  Requires Level 60
12 proc         #1eff00  Chance on hit: Blasts your enemy with lightning, dealing 300 Nature damage and then jumping to additional nearby enemies.  Each jump reduces that victim's Nature resistance by 25. Affects 5 targets. Your primary target is also consumed by a cyclone, slowing its attack speed by 20% for 12 sec.
13 item_level   #9d9d9d  Item Level 80
14 sell_price   #9d9d9d  Sell Price: 25g 53s 55c
```

A `damage_extra` line does appear on Era rows with a second range, for example 1482 Shadowfang:
```
 3 damage       #ffffff  29 - 55 Damage
 4 damage_extra #ffffff  +4 - 8 Damage
 5 speed        #ffffff  Speed 2.70
```

**10329 Scarlet Belt (Era-sourced): slot, armour, stats, requirements, set block and source note**
```
 0 name         #1eff00  Scarlet Belt
 1 binding      #ffffff  Binds when equipped
 2 slot         #ffffff  Waist <TAB> Mail
 3 armor        #ffffff  123 Armor
 4 stat         #ffffff  +8 Strength
 5 stat         #ffffff  +7 Stamina
 6 durability   #ffffff  Durability 35 / 35
 7 req_level    #ffffff  Requires Level 32
 8 set_name     #ffd100  Chain of the Scarlet Crusade (0/6)
 9 set_item     #808080  Scarlet Belt
10 set_item     #808080  Scarlet Boots
11 set_item     #808080  Scarlet Chestpiece
12 set_item     #808080  Scarlet Gauntlets
13 set_item     #808080  Scarlet Leggings
14 set_item     #808080  Scarlet Wristguards
15 set_bonus    #808080  (2) Set: +10 Shadow Resistance.
16 set_bonus    #808080  (3) Set: +30 Attack Power against Undead.
17 set_bonus    #808080  (4) Set: Taking damage has a $h% chance to grant Enraging Light for 10 sec, causing your melee attacks to deal 20 Holy damage. Deals 3 times as much damage to Undead.
18 set_bonus    #808080  (5) Set: Improves your chance to hit by 1.0%.
19 set_bonus    #808080  (6) Set: Taking damage while at or below 20% health grants you a shield, absorbing $1293733s2 damage for 10 sec. This effect can only trigger once every ${$proccooldown/60} min.
20 item_level   #9d9d9d  Item Level 37
21 sell_price   #9d9d9d  Sell Price: 25s 7c
22 source_note  #9d9d9d  Stats from Classic Era data; Forever values may differ
```

- The armour and stats are the Era values (Task 5, concern 1). Wowhead Forever shows 136 armour, +12 Str and +10 Sta.
- Set bonuses 2, 3 and 5 equal Wowhead's text.
- **Bonus 4** needs `$h`, the proc chance (`SpellAuraOptions`).
- **Bonus 6** has two gaps:
  - `$1293733s2` refers to an effect index 1 that spell 1293733 does not have in this build. It has only index 0, value 480, which Wowhead shows.
  - `$proccooldown` has no table.

**19682 Bloodvine Vest (Forever): set piece, Equip lines from rating stats, the set's own skill requirement**
```
 0 name         #0070dd  Bloodvine Vest
 1 binding      #ffffff  Binds when equipped
 2 slot         #ffffff  Chest <TAB> Cloth
 3 armor        #ffffff  92 Armor
 4 stat         #ffffff  +13 Intellect
 5 req_level    #ffffff  Requires Level 60
 6 equip        #1eff00  Equip: Improves your chance to hit by 2.0%.
 7 equip        #1eff00  Equip: Increases damage and healing done by magical spells and effects by up to 27.
 8 set_name     #ffd100  Bloodvine Garb (0/3)
 9 req_skill    #ffffff  Requires Tailoring (300)
10 set_item     #808080  Bloodvine Vest
11 set_item     #808080  Bloodvine Leggings
12 set_item     #808080  Bloodvine Boots
13 set_bonus    #808080  (3) Set: Improves your chance to get a critical strike with spells by 2.0%.
14 item_level   #9d9d9d  Item Level 65
15 sell_price   #9d9d9d  Sell Price: 2g 50s 35c
```

**16806 Felheart Belt (Era-sourced, class-restricted, 8-piece set)**
```
 0 name         #a335ee  Felheart Belt
 1 binding      #ffffff  Binds when equipped
 2 slot         #ffffff  Waist <TAB> Cloth
 3 armor        #ffffff  57 Armor
 4 stat         #ffffff  +15 Intellect
 5 stat         #ffffff  +8 Spirit
 6 stat         #ffffff  +18 Stamina
 7 resist       #ffffff  +7 Fire Resistance
 8 durability   #ffffff  Durability 35 / 35
 9 req_class    #ffffff  Classes: Warlock
10 req_level    #ffffff  Requires Level 60
11 set_name     #ffd100  Felheart Raiment (0/8)
12-19 set_item  #808080  Felheart Belt, Bracers, Gloves, Pants, Robes, Shoulder Pads, Horns, Slippers
20 set_bonus    #808080  (3) Set: Health or Mana gained from Drain Life and Drain Mana increased by 15%.
21 set_bonus    #808080  (5) Set: Your pet gains 15 stamina and 100 spell resistance against all schools of magic.
22 set_bonus    #808080  (8) Set: Mana cost of Shadow spells reduced by 15%.
23 item_level   #9d9d9d  Item Level 66
24 sell_price   #9d9d9d  Sell Price: 1g 76s 49c
25 source_note  #9d9d9d  Stats from Classic Era data; Forever values may differ
```

Other real-data checks:

- **5079 Cold Basilisk Eye:** "Unique-Equipped", and "Use: … (7 Min Cooldown)".
- **13446 Major Healing Potion:** "Use: Restores 1050 to 1750 health. (2 Min Cooldown)".
- **117 Tough Jerky:** "Restores 61 health over 18 sec."
- **159 Refreshing Spring Water:** "Restores 151 mana over 18 sec.", with no cooldown suffix.
- **13510 Flask of the Titans:** "… for 2 hrs."
- **4500 Traveler's Backpack:** "16 Slot Bag".
- **5613 Staff of the Purifier:** "3 Charges".
- **19316 Ice Threaded Arrow:** "Ammo <TAB> Arrow" and "Adds 16.5 damage per second".
- **2411 Black Stallion Bridle:** "Races: Human, Dwarf, Night Elf, Gnome, High Order Skyborne", and the recipe-style Use from its description.
- **6948 Hearthstone:** "Use: Returns you to $z. …". `$z` has no client table and stays raw.

## Formatter coverage

**Placeholders that occur in the real `Spell` table** (17,760 non-empty descriptions), by the number of descriptions using them:

| Placeholder | Descriptions |
|---|---|
| `$s` | 8379 |
| `$d` | 4136 |
| `$@spelldesc` | 1369 |
| `$<id>s` | 1365 |
| `$<id>d` | 866 |
| `${…}` | 674 |
| `$m` | 657 |
| `$t` | 652 |
| `$@spellicon` | 567 |
| `$o` | 481 |
| `$a` | 199 |
| `$l…;` | 191 |
| `$/N;` | 157 |
| `$h` | 154 |
| `$<id>a` | 141 |
| `$x` | 135 |
| `$M` | 131 |
| `$q` | 130 |
| `$?` | 127 |
| `$<id>m` | 114 |
| `$<id>o` | 87 |
| `$<id>u` | 86 |
| `$<id>t` | 84 |

**Completeness:**

| Scope | Complete |
|---|---|
| Whole `Spell` table | 16110 / 17760 (90.7 %) |
| Item effects the pipeline writes (with text) | 2927 / 3003 (97.5 %) |
| Set bonuses (with text) | 359 / 385 (93.2 %) |

**Unformatted item and set spells: 96.** By token kind, counted in spells:

| Token | Spells |
|---|---|
| `$h` | 48 |
| `$aN` | 14 |
| `$<id>u` | 11 |
| `$proccooldown` | 11 |
| `$<id>aN` | 7 |
| `${…}` (`$h` inside an expression) | 6 |
| `$<id>qN` | 5 |
| `$ecN` | 4 |
| `$ecim` | 4 |
| `$<id>r` | 2 |
| `$<id>sN` (missing effect) | 2 |
| `$H` | 1 |
| `$i` | 1 |
| `$n` | 1 |
| `$sN` | 1 |
| `$u` | 1 |
| `$z` | 1 |

The first ids from the note:
```
2823 ($h $2818u), 2824 ($h $2819u), 2833 ($ec2 $ec1 $ecim), 3408 ($h), 5761 ($h), 6296 ($6297a1), 7617 ($h),
7619 ($h), 8679 ($h), 8686 ($h), 8688 ($h), 8690 ($z), 8693 ($h), 10344 ($ec2 $ec1 $ecim), 11202 ($h), 11338 ($h),
11339 ($h), 11340 ($h), 11350 ($11351a1), 11355 ($h $11353u), 11356 ($h $11354u), 11399 ($h), 11790 ($a1),
12022 ($a1), 12766 ($a1), 13219 ($h $13218u), … 21951 ($s2)
```

**For Task 11:**

- **`SpellAuraOptions`** would resolve `$h`, `$H`, `$u` and `$n`: 61 of the 96 spells. Those are `ProcChance`, `CumulativeAura` and `ProcCharges`.
- **`SpellRadius`** would resolve `$a`: 21 spells. `SpellTables` already reads it when present.
- **`SpellCooldowns`** would give cooldowns for effects whose `ItemEffect` cooldown is -1, such as Hearthstone's hour. No line needs it to be complete, so it is optional.
- Nothing needs code changes, only the fetch list.

## TDD evidence

- **Tests first:**
  - `SpellDescriptionFormatterTest`: a parameterized table of 39 real fixture descriptions with hand-derived expectations, working in comments, plus 7 focused tests including `unknownPlaceholderKeepsRawText`.
  - `ItemEffectLoaderTest`, `ItemSetLoaderTest`, `ClassRaceNamesTest` and `TooltipBuilderTest`.
  - The writer tests in `SqliteWriterClientDataTest`, the `KnownItemsTest` extension, and `ItemSparseLoaderTest.tooltipDetailsFromTheirColumns`.
- **The fixture was grown first** so the table could reach 30 or more real descriptions.
  - 30 items and set 163 were added with `make_fixture.py` and `convert.py` offline.
  - The CSV changes are purely additive: 513 lines added, 0 removed. `icons/` is unchanged.
  - Importer tests stayed green after the fixture change, before any code.
- **Red:** compilation failed with "cannot find symbol" for `ItemSpellEffect`, `SpellDescriptionFormatter`, `TooltipLine`, `SpellTables`, `ItemSetLoader`, `ItemEffectLoader` and `ItemSetInfo`.
- **First green run:** 295 tests with 5 failures. All five were fixed:
  - **Three were miscounts in my own expectations:**
    - Five effects, not six, because Worn Shortsword has none.
    - The formatter strips trailing whitespace.
    - Bloodvine's ordinals were off by one, because it has no Unique line.
  - **One was a gap in the oracle parser.** Wowhead's cloak slot row has no `<th>` cell, so `known_items.py` now makes it optional.
  - **One was the gate catching the parser gap above,** on both the fixture and the real data.
- **Final:** 297 tests, 0 failures. Verify and pytest are green.

## Decisions and deviations (please confirm)

1. **The level requirement is shown only when `RequiredLevel > 1`,** not `> 0`. Wowhead and the game omit "Requires Level 1" (Worn Shortsword); the gate checks this.
2. **Standings follow the game's enum:** 0 Hated, 1 Hostile, 2 Unfriendly, 3 Neutral, 4 Friendly, 5 Honored, 6 Revered, 7 Exalted.
   - The ruling's "4 Neutral" is off by one.
   - Recipe: Transmute Air to Fire has `MinReputation` 5, which is "Argent Dawn - Honored" as in the game.
3. **`damage_extra` has no school name.**
   - The Era `ItemSparse` has a single `DamageType` column for the first range. It is 0 for all 22 two-range rows, and there is no `DamageType_1`.
   - So the line is "+4 - 8 Damage"; the game shows "+4 - 8 Shadow Damage".
   - Forever rows have no second range at all.
4. **Durability shows for Era rows only.** Forever's `ItemSparse` has no `MaxDurability`, so `clientdata.tooltip.missingKind.durability` = 1.
5. **Cooldowns print only above 3 s.** The 1 to 3 s shared category cooldowns of food, drink, elixirs and scrolls are not printed by the game.
   - Example: Refreshing Spring Water has a category cooldown of 1000 ms.
   - Item cooldowns print as "(7 Min Cooldown)", "(1 Min 30 Sec Cooldown)" or "(1 Hour Cooldown)".
6. **`$o` on regeneration auras uses a 5 s tick.** Auras 84 and 85 have period 0 in the data, and the game's tick is 5 s. Any other `$o` without a period stays raw.
7. **Placeholders beyond the brief's list:** `$m`, `$M`, `$b`, `$@…` and `$?…`.
   - Conditions assume a level-60 character with no particular spells or auras.
   - `Formatted` gains a third component, `unresolved`, which feeds the note.
8. **Rating and spell stats become green `equip` lines worded like the oracle.**
   - Hit uses 10 and critical strike 14 rating per 1 %. Lionheart Helm and the three Bloodvine pieces confirm both.
   - Healing 41 with spell damage 42 becomes the combined "Increases healing done by up to H and damage done by up to D …" line.
   - Attack power 38 is "+N Attack Power."; school spell damage 84 to 89 is "Increases damage done by Fire spells …".
   - Mana and health regeneration stay white "Restores +N mana per 5 sec." lines.
   - Equip lines from stats are ordered by stat id, then come effect Equip lines.
   - The ids are confirmed from item names: 84 holy (Shining), 85 fire (Inferno), 86 nature, 87 frost (Azure Silk), 88 shadow, 89 arcane.
   - The other ratings (defense, dodge, parry, block, resilience, haste, expertise, spell penetration) use TBC wording that no oracle item verifies.
   - Ids 83 and 90 to 136 stay "+N Stat #id".
9. **Recipes:** the Use line is the item description (see the loader above). Its `item_effect` row is trigger 0 with `spell_id` = the taught spell.
10. **A set's own skill requirement** ("Requires Tailoring (300)") is a `req_skill` line right under `set_name`, as Wowhead and the game show it. This is the one place a kind appears out of the spec order. `TooltipBuilderTest` allows exactly that.
11. **Smaller choices:**
    - **Unique:** "Unique-Equipped" comes from `Flags_0` bit 0x80000; Cold Basilisk Eye confirms it.
    - **Charges:** printed when `abs(Charges)` is above 1.
    - **Bags:** the slot line is "n Slot <subclass display name>".
    - **Name:** the item name falls back to the Questie name, then "Item #id".
    - **Which items:** tooltips are built for items with an `ItemSparse` row only.
    - **Use before Equip:** spec order puts all Use lines before Equip lines. Wowhead interleaves Benediction's by effect order.
12. **Sets for Era items:** `--sets 163` was added to the fixture command. Era-only items never pull their set into the fixture, and the self-review needed an Era set piece. The README is updated.

## Files changed

- **Main, new (`importer/src/main/java/dev/wowforever/importer/clientdata/`):** `SpellTables`, `SpellDescriptionFormatter`, `ItemSpellEffect`, `ItemEffectLoader`, `ItemSetInfo`, `ItemSetLoader`, `TooltipLine`, `TooltipBuilder`, `ItemTooltips`, `StatText`, `BindingNames`, `InventoryTypeNames`, `ClassRaceNames`, `TooltipNames`.
- **Main, modified:**
  - `clientdata/ClientItem.java` (`Details`, extra damage)
  - `clientdata/ItemSparseLoader.java`
  - `clientdata/CsvTable.java` (`Row.table()`)
  - `sqlite/SqliteWriter.java`
- **Tests, new:** `SpellDescriptionFormatterTest`, `ItemEffectLoaderTest`, `ItemSetLoaderTest`, `ClassRaceNamesTest`, `TooltipBuilderTest`.
- **Tests, modified:** `KnownItemsTest`, `ItemSparseLoaderTest`, `ClientItems`, `ItemStatsCalculatorTest`, `sqlite/SqliteWriterClientDataTest`.
- **Fixtures:**
  - 24 CSVs under `importer/src/test/resources/fixtures/clientdata/csv/` (rows added)
  - `known-items.csv`: a `lines` column was added and `compare_kinds` extended; the old columns are byte-identical.
  - `KnownItemsOracle.md`
- **Tools:** `tools/clientdata/known_items.py` (the `lines` column), `tools/clientdata/README.md`.

## Concerns

1. **Four rendering details are verified only by a few oracle items:**
   - the hit and critical strike conversion (10 and 14 rating per 1 %);
   - the TBC wording of the other ratings;
   - "1 hour" against "2 hrs";
   - the 3 s cooldown threshold.
   If the owner reads a different value in game, each is a one-line change.
2. **Some real items carry raw placeholders.**
   - 96 item and set spells keep raw tokens, mainly `$h`. Hearthstone shows `$z`.
   - The UI will display these as written until Task 11 fetches `SpellAuraOptions` and `SpellRadius`.
3. **Era-sourced tooltips show Era values** (Task 5, concern 1). The new `source_note` line flags them.
4. **`ItemTooltips` is not yet wired into `ImportPipeline`.** Task 9's `ClientDataStage` should call `ItemTooltips.build(...)` and pass it to the 7-argument `SqliteWriter.write`.
