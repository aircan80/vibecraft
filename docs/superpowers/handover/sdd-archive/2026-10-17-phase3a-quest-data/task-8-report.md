# 3A Task 8 report: crafting data from the client

Status: DONE. Commit `9459aba` on `feature/phase3a-quest-data` (one commit, `feat(clientdata): crafting recipes and
reagents`, trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`). The shipped database was not
regenerated and no shipped-data assertion was added (Task 10).

## What was built

- **tools/clientdata**
  - `REQUIRED_TABLES` gains `SpellReagents`, `SkillLineAbility` and `SkillRaceClassInfo` (48 tables). SkillRaceClassInfo
    exists for 1.60.1.70425 on wago.tools (186 rows, 5 KB). The fetch tool pulls it, the importer does not read it yet.
  - `convert.py --tables A,B` copies only the named cached CSVs plus `build.lock` into OUT and leaves the rest alone.
    It is mutually exclusive with `--ui-icons-only`. A table that is not cached exits 2 before writing anything.
  - `make_fixture.py --crafts 1,2,3` adds crafting spells to the spell closure. The `SpellReagents` rows (by
    `SpellID`) and `SkillLineAbility` rows (by `Spell`) of every kept spell are kept. Their skill lines join the
    `SkillLine` references, and `SkillRaceClassInfo` is copied whole.
  - The README documents all three, plus the fixture provenance.
- **Fetch and convert, real cache.** These ran for build 1.60.1.70425, product wow_classic_beta, against wago.tools:
  `fetch.py --tables SpellReagents,SkillLineAbility`, then `--tables SkillRaceClassInfo`, then
  `convert.py --tables SpellReagents,SkillLineAbility,SkillRaceClassInfo` into `~/.cache/wowforever-clientdata/out/current`.
  The lock merge kept every other entry. The committed `tools/clientdata/build.lock` differs from the previous one only
  by the three new table shas and `fetched_at`:
  - SpellReagents `47a239be…846d`
  - SkillLineAbility `9880b28c…8d02`
  - SkillRaceClassInfo `2193908c…a322`
- **Importer**
  - The records `Craft` and `CraftReagent` use exactly the names from the brief. `Crafts` is the container, with
    `crafts`, `reagents`, `reagentsOf(spell)` and `EMPTY`.
  - `CraftLoader.load(source, report)` builds them.
  - `ClientDataBundle` gains a `crafts` component, plus an 8-argument constructor without crafts so existing callers
    are unchanged.
  - `ClientDataStage` runs CraftLoader last.
  - `SqliteWriter.writeCrafts` fills the two tables, reagents in slot order.
  - The schema adds tables `craft` and `craft_reagent`, with the brief's columns and primary keys.
  - It adds indexes `idx_craft_item`, `idx_craft_recipe_item` and `idx_craft_reagent_item`.
  - SCHEMA_VERSION stays 3.
- **App**
  - The models are `app/model/Craft` and `app/model/CraftReagent`.
  - `Craft` carries the craft fields plus the created item's name, quality and icon, the recipe item's name, and the
    reagents. Its helpers are `item()`, `recipe()` and `quantityText()`, which gives "2" or "1-3".
  - `CraftReagent` carries `itemId, count, name, quality, iconId`, with `ref()` and `displayName()`.
  - `ItemRepository` gains `createdBy(itemId)`, `reagentFor(itemId)` and `taughtBy(recipeItemId)`.
  - They order crafts by skill name with no-skill crafts last, then by spell name.
  - Reagents keep rowid (slot) order, the same idiom as the existing spawn reader. Names, quality and icon come from a
    left join on the item table.
  - All three return an empty list when `craft` or `craft_reagent` is missing, using the `Sql.hasTable` idiom from
    Task 7.

## Column mapping (CSV header names, build 1.60.1.70425)

| Field | Source |
|---|---|
| craft spell | `SpellEffect` row with `Effect` = 24 and `EffectItemType` > 0; per spell the `DifficultyID` 0 row with the lowest `EffectIndex` (all 3,336 create rows are difficulty 0; one spell, 423212, has two) |
| `item_id` | `SpellEffect.EffectItemType` |
| `quantity_min/max` | `round(EffectBasePointsF * (1 -/+ Variance/2))`, each at least 1, max ≥ min |
| `spell_name` | `SpellName.Name_lang` by `ID` |
| `skill_line_id` | `SkillLineAbility.SkillLine`, row by `SkillLineAbility.Spell` (first in file order) |
| `skill_name` | `SkillLine.DisplayName_lang` by `ID` |
| `min_skill`, `trivial_low`, `trivial_high` | `MinSkillLineRank`, `TrivialSkillLineRankLow`, `TrivialSkillLineRankHigh` |
| reagents | `SpellReagents` by `SpellID`: `Reagent_0..7` / `ReagentCount_0..7`, slots with item ≤ 0 or count ≤ 0 skipped, a repeated item summed |
| `recipe_item_id` | `ItemXItemEffect(ItemEffectID -> ItemID)` joined to `ItemEffect(ID, TriggerType, SpellID)`: trigger 6 whose `SpellID` is the craft, or trigger 0/6 whose spell has a `SpellEffect` `Effect` 36 row with `EffectTriggerSpell` = the craft; lowest item id wins |

**Deviations from the brief, with the reasons.**

- **Quantity is not `EffectBasePoints + 1`.** The build has no integer `EffectBasePoints` and no `EffectDieSides`,
  only `EffectBasePointsF`, and that already holds the yield. Heavy Leather is 1, Crafted Heavy Shot is 200, and
  Seasoned Wolf Kabob is 3. The old DBC die sides survive as `Variance`: Coarse Dynamite is 2 with variance 1, which
  gives 1 to 3. A mutation test with "+1" fails four CraftLoaderTest cases. 537 create rows store 0, mostly quest
  "Filling" spells, but Transmute: Air to Fire stores 0 too. A Create Item effect makes at least one item, so 0
  becomes 1.
- **The recipe link usually skips the learn spell.** The brief describes a trigger "learn on use" pointing to a spell
  whose Effect 36 teaches the craft. In this build, trigger 6 `SpellID` is the craft spell itself, in 1,912 rows.
  Trigger 6 via Effect 36 finds 0 rows, and trigger 0 via Effect 36 finds 3. Both paths are implemented and tested;
  the second is tested on a synthetic table.

## Fixture ids (all real, from the 1.60.1.70425 CSVs)

| Spell | Craft | Notes |
|---|---|---|
| 20649 | Heavy Leather | Leatherworking 165, 125/135, item 4234 ×1, reagent 2319 ×5 |
| 21161 | Sulfuron Hammer | Blacksmithing 164, 325/350, item 17193, seven reagents |
| 21160 | Eye of Sulfuras | creates Sulfuras 17182 from 17193 + 17204; no SkillLineAbility row (noSkill); was already in the fixture |
| 3931 | Coarse Dynamite | Engineering 202, item 4365, 1-3 |
| 17559 | Transmute: Air to Fire | Alchemy 171, base points 0 → 1, taught by Recipe 13482 (already in the fixture) |
| 1225758 | Prowler Steak | Cooking 185, 260/280, item 238637 ×2, taught by Recipe: Prowler Steak 238645 and 250394 (lowest wins) |
| 435481 | Enchant Weapon - Dismantle | reagents and a skill row but no Create Item effect: skipped |

- **The cut.** It used the README's `ARGS` plus `--items ...,238645,250394 --crafts 20649,21161,3931`, from the real
  cache into the scratchpad.
- **Additive check.** The baseline cut without the new ids reproduces every committed CSV byte for byte, except the
  hand-extended `UiMapAssignment.csv`. Each changed CSV holds its committed rows as an ordered subsequence. The new
  rows are 2 Item, 4 ItemEffect, 1 ItemSparse and 1 ItemSparse.era (238645 also has an Era row), 4 ItemXItemEffect,
  4 Spell, 2 SpellAuraOptions, 5 SpellEffect, 4 SpellMisc and 4 SpellName.
- **New tables.** `SpellReagents.csv` has 8 rows and `SkillLineAbility.csv` has 6.
- **Left unchanged.** `UiMapAssignment.csv`, the fixture `build.lock` (following the SpellRadius precedent), and no
  `SkillRaceClassInfo.csv`.

## Real-data counters (strict scratch import)

    java -jar importer/target/importer.jar --out <scratchpad>/realimport/wowforever.sqlite --fail-on-unsupported \
        --client-data-dir ~/.cache/wowforever-clientdata/out/current      -> exit 0

| Counter | Value |
|---|---|
| clientdata.crafts | 3,335 |
| clientdata.crafts.noSkill | 967 |
| clientdata.crafts.reagents | 8,707 |
| clientdata.crafts.recipes | 1,869 |

**Supporting numbers.**

- Crafts that have a skill: 2,368.
- Crafts whose created item has an item row: 2,797.
- Reagent rows: 8,377 of 8,707 resolve to an item row, covering 864 of 998 distinct items.
- Crafts without reagents: 634.
- Crafts with a yield range: 13. Examples are Coarse Dynamite 1-3, Iron Grenade 2-4 and Heavy Dynamite 1-5.
- Medium Leather (2319) is a reagent of 91 crafts.
- Recipe items absent from the item table: 347.
- 40 crafts are taught by several items, which the report notes. One example is 1225758, taught by 238645 and 250394.
- Database size: the scratch DB is 69.1 MB against 67.1 MB for the shipped DB. The difference also includes the
  Task 7 waypoints.

## TDD evidence

- **Python.** The new tests went red first: 7 failed and 73 passed. The failures were the table count (45 to 48),
  `--tables` copying and refusal, `--crafts` closure, the SkillLine reference and the CLI. After the implementation,
  80 pass.
- **Java loader.** The tests were written before the implementation but first run after it, so I proved they
  discriminate by mutation. Each mutation below was applied to CraftLoader and reverted afterwards:
  - quantity +1: 4 tests fail;
  - keeping zero-count reagents: survived at first, so a count-0 slot was added to the synthetic test, which then
    fails;
  - no learn-spell path: 1 test fails;
  - keeping spells that create nothing: 1 test fails.
- **App readers.** `CraftReaderTest` was red first, with compile errors because `createdBy`, `reagentFor` and
  `taughtBy` did not exist. It was green after the implementation.
- **Tests added.**
  - `CraftLoaderTest` (10 tests): created item and quantity, ranges, zero base points, reagents in slot order, null
    skill fields, the recipe link with the lowest id winning, the skipped non-craft, the learn-spell path with summed
    and zero-count reagents, and a missing table.
  - `SchemaTest`: craft columns, primary keys and indexes.
  - `SqliteWriterClientDataTest`: crafts written, with nulls and slot order.
  - `ImportPipelineTest`: craft tables non-empty, the counters on the fixture (crafts 6, noSkill 1, reagents 15 =
    table.craft_reagent, recipes 2) and spot rows. The skip-client-data run leaves the craft tables empty with no
    craft counters.
  - `CraftReaderTest` (6 tests): fixture readers, ordering and rowid order on a schema database, and the fail-soft
    case with both tables dropped.

## Verify

`./mvnw -B -q verify` from H:\claude_java exits 0, run on the final tree just before the commit. The surefire reports
show:

| Module | Tests | Failures |
|---|---|---|
| lua-table | 61 | 0 |
| importer | 376 | 0 |
| app | 259 | 0 |

ShippedDataTest ran and passed on the committed database, which has no craft tables. Pytest from the venv gives
80 passed.

## Concerns

1. **min_skill is almost always 1.** This client's `MinSkillLineRank` is 1 for 2,352 of the 2,368 crafts with a
   skill; Heavy Leather is 1, not the classic 100. The learn rank most players expect lives in trainer data or the
   recipe item's `ItemSparse.RequiredSkillRank`, not here. Plan 3B should show trivial low and high, the yellow and
   grey thresholds, and either hide min_skill when it is 1 or take the recipe's RequiredSkillRank.
2. **Only one recipe item per craft is stored.** `recipe_item_id` is a single column, so for the 40 crafts taught by
   several items only the lowest id is kept. `taughtBy(250394)` is empty for Prowler Steak's second recipe. A
   `craft_recipe` link table would fix it if the owner wants every recipe item.
3. **Half-unit ranges are rounded half up.** High-Yield Radiation Bomb has base 3 and variance 1, so it reads 2-5. A
   true 1-5 cannot be told apart, and only two spells are affected.
4. **The craft set is broad.** It includes quest and conjure spells with no skill: "Filling", Conjure Water, item-use
   creates. They make up the 967 noSkill crafts. The UI may want to show only crafts with a skill, or with a recipe.
5. **Cross-build data is skipped.** Era-only recipe items (`ItemEffect.era`) are not read as recipe links, and
   `SkillRaceClassInfo` is fetched but unused.
6. **The main README is not updated.** Its schema paragraph and its Sizes table ("47 tables") do not mention crafting
   yet; I left that to the Task 10 regeneration docs.
7. **Environment.** Mid-task the session's working directory was switched to a `.worktrees` checkout. Following the
   brief, I ignored it and worked only in H:\claude_java on `feature/phase3a-quest-data`.

## Follow-up: every recipe item is kept (commit e63a49e)

Ruling on concern 4 was implemented in `fix(clientdata): a craft keeps every recipe item that teaches it`.

- **Schema.** The new table `craft_recipe_item(spell_id INTEGER NOT NULL, item_id INTEGER NOT NULL, PRIMARY KEY
  (spell_id, item_id))` has index `idx_craft_recipe_item_item` on item_id. It is additive to v3.
- **The convenience column.** `craft.recipe_item_id` stays, as the lowest id. On real data it equals the lowest join
  row for all 1,869 crafts that have one.
- **Importer.** The new record `CraftRecipeItem(spellId, itemId)` is held in `Crafts.recipeItems()`, ordered by
  spell and then item id; the 2-argument `Crafts` constructor is kept. `SqliteWriter` fills the table. The old
  "lowest item id is kept" note is removed, since nothing is dropped any more.
- **Counters.** `clientdata.crafts.recipes` still counts crafts with at least one recipe item. The new
  `clientdata.crafts.recipeItems` counts the join rows.
- **App.**
  - `Craft` gains `recipeItemIds`, every recipe item in id order, empty when there is none. `recipeItemId` and
    `recipeItemName` stay as the lowest one.
  - `taughtBy(itemId)` now reads `craft_recipe_item`, so either recipe item finds the craft.
  - All three readers return empty when any of `craft`, `craft_reagent` or `craft_recipe_item` is missing.
- **Fixture.** It already held Prowler Steak's second recipe item, 250394; it was cut in the first commit. No CSV
  changed.
- **Tests.**
  - The test changes went first and were red: compile errors on `CraftRecipeItem`, `recipeItems()` and
    `RECIPE_ITEMS`.
  - `CraftLoaderTest`: the three recipe pairs on the fixture, the recipeItems counter of 3, and no note. The synthetic
    learn-spell case gives pair (700, 900).
  - `SchemaTest`: the new table's columns, primary key, NOT NULL and index.
  - `SqliteWriterClientDataTest`: two recipe rows for one craft.
  - `ImportPipelineTest`: recipeItems of 3 equals `table.craft_recipe_item`.
  - `CraftReaderTest`: `taughtBy` on both 238645 and 250394 returns Prowler Steak with `recipeItemIds`
    [238645, 250394]. `createdBy(238637)` carries both. On the schema database, rows inserted out of order come back
    in id order and `taughtBy` works for the second id. The fail-soft case drops all three tables.
- **Verify and pytest.** `./mvnw -B -q verify` exits 0:

  | Module | Tests | Failures |
  |---|---|---|
  | lua-table | 61 | 0 |
  | importer | 376 | 0 |
  | app | 259 | 0 |

  Pytest gives 80 passed.

**Real data** (strict scratch import, exit 0):

| Counter | Value |
|---|---|
| clientdata.crafts | 3,335 |
| clientdata.crafts.noSkill | 967 |
| clientdata.crafts.reagents | 8,707 |
| clientdata.crafts.recipes | 1,869 |
| clientdata.crafts.recipeItems | 1,917 |

`table.craft_recipe_item` is 1,917, and 40 crafts have more than one recipe item.

## Fix round 1 (commit 6d97b06)

The commit is `fix(clientdata): crafting tables are optional client data; counters for dropped rows`, on top of
HEAD 924c5ba.

1. **Optional tables (Important).** `SkillLineAbility` and `SpellReagents` are read through `optionalTable`, like
   SpellRadius and SpellAuraOptions in SpellTables.
   - **My call: skip crafting entirely.** When either table is missing, the loader returns `Crafts.EMPTY`, without
     reading even SpellEffect. Loading crafts without skill ranks and reagents would make every craft look like a
     no-skill craft with nothing consumed, which reads as complete when it is not.
   - **Counted and noted.** The skip counts `clientdata.crafts.skipped = 1` and `clientdata.crafts = 0`. A note names
     the missing tables and the `--tables` value that fixes them.
   - **Present tables stay strict.** A table that is present must still have every column read.
   - **SkillRaceClassInfo** is still not read.
   - **Loader tests.** One copies the fixture CSVs without both tables: the result is EMPTY, skipped is 1, and the note
     names both. Another copies it without only SpellReagents: it is skipped and the note names SpellReagents alone.
     The SpellEffect-missing test now creates the two optional tables, so it still fails naming SpellEffect.
   - **End to end.** A strict import of a copy of `out/current` without the two CSVs exits 0. It reports crafts 0 and
     skipped 1, and all three craft tables are empty.
2. **A second Create Item row.**
   - **Counter.** The new `clientdata.crafts.extraCreates` counts the Create Item rows beyond the one used per spell.
     A note names up to 10 spells with their row counts.
   - **Test.** A synthetic spell has three create rows: index 1, a difficulty 1 row at index 0, and a difficulty 0 row
     at index 0. The difficulty 0, index 0 row wins, extraCreates is 2, and the note names that spell only.
   - **Real data.** The count is 1, spell 423212 with 2 rows.
3. **Rows without a spell.**
   - **Counter.** The new `clientdata.crafts.rowsWithoutSpell` counts rows with an empty spell id: `SpellID` in
     SpellEffect and SpellReagents, `Spell` in SkillLineAbility. A note gives the count per table.
   - **Test.** One such row in each table gives 3, and the craft and reagent with a spell id are still loaded.
   - **Real data.** The count is 0.
4. **New tests.**
   - An item (901) with two trigger 6 effects teaches crafts 700 and 701.
   - A trigger 6 effect of item 902 names learn spell 801, which is not a craft; its Effect 36 teaches craft 702.
   - This gives recipeItems (700, 901), (701, 901) and (702, 902), with recipes 3 and recipeItems 3.
5. **SkillRaceClassInfo fixture.** It is out of `WHOLE_TABLES`, since the importer does not read it, and the cutter
   now writes it header-only. That header-only CSV is committed in the fixture. A new pytest checks the header-only
   output and fails on the old cutter. A re-cut with the README's arguments reproduces every committed fixture CSV
   byte for byte, with two exceptions:
   - `UiMapAssignment.csv`, hand-extended and documented.
   - `ManifestInterfaceData.csv`, which the cutter writes header-only but the fixture has never had since Task 4.
     The importer does not read it, and I left it alone. The README says so.
6. **The recipe join comment.** A comment above the `ItemXItemEffect` to `ItemEffect` join explains why it does not
   use ItemEffectLoader:
   - that loader reads only the effects of imported items, and 347 recipe items are not in the item table;
   - it folds the trigger 6 and learn-spell rows into one Use effect.

**Also fixed.** The README's two crafting fetch and convert commands had lost their backslash line continuations; they
are restored.

**TDD.** The new Java tests went red first, with compile errors on `SKIPPED`, `EXTRA_CREATES` and
`ROWS_WITHOUT_SPELL`. After the implementation one test still failed: the skip note's `--tables` hint named both tables
when one was missing. The hint now lists only the missing tables. The new pytest is red on the old cutter and green on
the new one. `CraftLoaderTest` now has 16 tests.

**Verify.** `./mvnw -B -q verify` exits 0:

| Module | Tests | Failures |
|---|---|---|
| lua-table | 61 | 0 |
| importer | 406 | 0 |
| app | 259 | 0 |

Pytest gives 81 passed.

**Real data** (strict scratch import, exit 0):

| Counter | Value |
|---|---|
| clientdata.crafts | 3,335 |
| clientdata.crafts.noSkill | 967 |
| clientdata.crafts.reagents | 8,707 |
| clientdata.crafts.recipes | 1,869 |
| clientdata.crafts.recipeItems | 1,917 |
| clientdata.crafts.extraCreates | 1 |
| clientdata.crafts.rowsWithoutSpell | 0 |
| clientdata.crafts.skipped | absent |
