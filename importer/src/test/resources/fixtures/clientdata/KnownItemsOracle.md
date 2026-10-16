# Known items oracle

`known-items.csv` is the secondary gate for item stats, damage and armour (spec 4.4, ruling 10) and for the tooltip
texts (task 6). `KnownItemsTest` compares the computed values and the rendered tooltip lines of each row in the
kinds listed in its `compare_kinds`. The primary gate is the Era
overlap (`EraOverlapOracleTest`): items present in both Forever's and the Era client's `ItemSparse`.

## Oracle used: Wowhead's Forever tooltips

Produced on 2026-10-16 from `https://nether.wowhead.com/forever/tooltip/item/{id}`, the JSON that the item page
`https://www.wowhead.com/forever/item={id}` loads to draw its tooltip. Reproduce with:

    python tools/clientdata/known_items.py --raw DIR --fetch --out importer/src/test/resources/fixtures/clientdata/known-items.csv

The item page itself renders the tooltip with JavaScript, so fetching the page returns no values.

## Why not wow.tools.local

The brief asked for wow.tools.local first. It ran against the local `wow_classic_beta` install, build
1.60.1.70425, and served `/dbc/tooltip/item/{id}`. Its computed values are not the game's:

- **Stats agree on 19 of 28 items, damage on 2 of 12.**
- **Stats use the retail budget slot groups.** For example, it puts a one-hand weapon in group 4 and ranged right
  in group 0. Thunderfury gets +3 Agility, +5 Stamina and +5/+5 resistances. Both the Era client row and Wowhead
  give +5 Agility, +8 Stamina, +8 Fire and +9 Nature, which is also the original item.
- **The damage maximum is floored.** Thunderfury gets 82 - 152, where the Era row has 82 - 153. Worn Shortsword
  gets 1 - 2, where the Era row and Wowhead have 1 - 3.
- **Ranged weapons and wands use the wrong tables.** High Warlord's Crossbow gets 179 - 269, where Wowhead has
  107 - 162. That 107 - 162 is both the Era row and the ranged table.

wow.tools.local is a third-party reimplementation of the retail tooltip. The Era rows are absolute values the
game ships, so they settle the disagreement in Wowhead's favour. wow.tools.local was stopped after the run, and
nothing under the WoW folder changed.

## What each column holds

- **`expected_lines`:** Wowhead's tooltip lines in order, without the sell price. These are kept for the tooltip
  task (6) and are not compared here.
- **`stats`:** `id:value` pairs, built from these Wowhead lines:
  - Plain `+N` lines.
  - Resistance lines, as ids 51 to 56.
  - "+N Armor", as 50.
  - "+N Block Value", as 48.
  - "+N Ranged Attack Power", as 39.

  Wowhead rewrites ratings and spell power as "Equip:" text, for example "Improves your chance to hit by 2.0%".
  Those stats are outside the gate. The test compares the ids in `KnownItemsTest.TOOLTIP_STATS` as values per id,
  not in order.
- **`damage`:** `min-max/delayMs/dps/school`. The dps is compared to the precision Wowhead prints it. Ammunition
  is `*-*/0/dps/0`, from "Adds 16.5 damage per second".
- **`armor`:** `base/block`. Empty means no armour line. Block is recorded but not compared.
- **`lines`:** the tooltip text kinds, `kind=text` entries separated by `|`, in tooltip order:
  - `binding`: "Binds when picked up", "Binds when equipped", "Binds when used" or "Quest Item".
  - `slot`: Wowhead's slot row, the slot and the subclass separated by a literal `	` (the tooltip line's tab);
    the subclass is left out when Wowhead's cell is empty (rings, trinkets) or missing (cloaks).
  - `req_level`: "Requires Level n".
  - `use`, `equip`, `proc`: the "Use:", "Equip:" and "Chance on hit:" lines, cooldown included.

  Every row compares all six text kinds unless excluded; a kind with no entry expects no such line. Texts are
  compared with runs of spaces and line breaks counted as one space, because Wowhead collapses the game's double
  spaces. A mismatch is reported with the expected and the rendered lists.

## Rows and exclusions

There are 28 Forever-sourced items. They cover:

- qualities 0 to 5
- every stat slot group
- one-hand, two-hand, main hand and off hand
- bow, gun, crossbow, wand, thrown and ammunition
- two shields
- cloth, leather, mail and plate
- neck, ring, trinket, back and held in off-hand
- a set piece (Bloodvine, set 421)
- proc weapons (Thunderfury, Ashbringer)
- a Use trinket (Cold Basilisk Eye)
- bonus armour (Ring of Protection, Heavy Mithril Gauntlet)
- block value (The Immovable Object)

Twenty of them were added to the fixture for this gate. See the fixture README's `--items` list.

Wowhead prints the rating and spell bonus stats as "Equip:" lines ("Improves your chance to hit by 2.0%."). The
tooltip renders them the same way from the computed stats (`StatText`): hit at 10 and critical strike at 14 rating
per 1 %, which Lionheart Helm and the three Bloodvine pieces confirm, and spell power as "Increases damage and
healing done by magical spells and effects by up to n.". Lionheart Helm's slots hold critical strike before hit,
but Wowhead prints hit first, so these lines are ordered by stat id.

Two rows drop kinds:

- **19019 Thunderfury: `damage` and `dps` dropped.** Wowhead shows the original 44 - 115 (41.84 dps). Forever's
  item level 80 one-hand table gives 82 - 153 (61.85 dps), and so does the Era client row. No client table
  produces 44 - 115. Speed, stats and resistances are compared.
- **4134 Nimboya's Mystical Staff: `damage`, `speed`, `dps` and `equip` dropped.** Forever marks caster weapons with
  `Flags_4` bit 0x100. Wowhead shows them with about 26 % (two-hand) to 34 % (one-hand) less DPS than the tables,
  plus an "Increases damage and healing done by magical spells" line. That line has no `ItemXItemEffect` row in
  the client data, and Forever's caster damage tables equal the plain ones. The rule cannot be derived from the
  client data, so caster weapon damage is unverified: none is written, and it counts as
  `clientdata.unverified.damage`. There are 99 such weapons in the build. For the same reason the tooltip cannot
  produce the staff's "Equip: Increases damage and healing done ... by up to 38." line, so `equip` is dropped too.

## Era-sourced items are not in the gate

Six fixture items take their `ItemSparse` row from the Era fallback (ruling 17) and have a Wowhead Forever page.
Five of them differ from Wowhead:

| Item | Wowhead (Forever) | Era row |
|---|---|---|
| Fight Club | +5 Str, +4 Sta | +6 Str, +6 Sta |
| Scarlet Belt | 136 armour, +12 Str, +10 Sta | 123 armour, +8 Str, +7 Sta |
| Crystalline Cuffs | 15 armour, +2 Sta, +3 Spi | 14 armour, +2 Spi, +1 Int |
| Subterranean Cape | 17 armour, +3 Str, +5 health regen | 16 armour, +2 Agi, +2 Str |
| Cursed Felblade | 19 - 37 | 16 - 31 |

Cruel Barb matches. Era fallback values are Era values, and the gate does not claim them for Forever.
