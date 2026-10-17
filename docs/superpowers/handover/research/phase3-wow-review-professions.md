# WoW expert review 2 — professions and economy (2026-10-17)

## Questions a player asks Wowhead → where the answer lives
- Recipe page: source type first (Trainer name/cost/required skill; Vendor who/price/limited stock; Drop; Quest); skill strip orange/yellow/green/grey (learn 90, yellow 115, green 130, grey 145); what it teaches (crafted item with tooltip); reagents with counts, each linked to its sources (vendor-bought vs gathered vs dropped).
- Item page: "Created by" block (profession, skill, mats); "Reagent for" grouped by profession sorted by skill; vendor buy price with stack size, limited stock + restock time, vendor faction; disenchant results (skill + shard/dust/essence table) and "disenchanted from"; "Gathered from" (herb/vein/chest objects → zone map filtered to those nodes, gathering skill).

## Additions ranked
1. Crafting graph: Created by / Reagent for / recipe "Teaches" — SpellEffect (Effect 24 create item, item + quantity), SpellReagents (Reagent[8], ReagentCount[8]), ItemEffect (recipe fires spell 483 with the taught spell).
2. Skill thresholds + profession browser — SkillLineAbility (MinSkillLineRank, TrivialSkillLineRankLow/High, AcquireMethod), SkillLine, SkillRaceClassInfo.
3. Recipe source resolver (trainer/vendor/drop/quest) — VMaNGOS npc_trainer (spell, spellcost, reqskill, reqlevel); npc_vendor or Questie vendor lists.
4. Gathering nodes — VMaNGOS gameobject_loot_template (item → node object), Lock DB2 (skill required), object spawns already shipped.
5. Vendor economics — ItemSparse BuyPrice, VendorStackCount; VMaNGOS npc_vendor maxcount/incrtime; a "Sells" filter on the vendor finder.
6. Disenchanting — ItemSparse RequiredDisenchantSkill / ItemDisenchantLoot DB2; VMaNGOS disenchant_loot_template.
7. Fishing per zone — VMaNGOS fishing_loot_template + skill_fishing_base_level.
8. World-drop separation — VMaNGOS reference_loot_template ids → a "World drop (level X–Y)" bucket instead of listing under every mob.

## Farming-spot research
Needs density, level range/elite share, respawn, drop/gather chance, a route loop, competition. From spawn points alone: heat-map/grid counts and clusters ("12 Defias Pillagers around the Jangolode Mine"), cluster level/elite share, a nearest-neighbour loop, distance to nearest flight path/graveyard/vendor (NPC flags), competition proxy = number of quests needing those mobs/objects. Needs server data: respawn (spawntimesecs), kill-based rates, pooled nodes (Copper/Tin/Silver share points — show "possible spawn" with pool chance). Estimated yield per hour (density × respawn × rate), clearly labelled an estimate.

## Screenshot findings (→ 2B Task 14 polish where product bugs)
a) Hogger's Drops (25): the 11 recipes, Copper Ore, Shadowgem, Tigerseye, Silverleaf and food are the generic world-drop pool with "—" rates, burying his real loot (Huge Gnoll Claw 84 %); fold into a collapsed "World drops" group. "Item level" is meaningless for recipes/trade goods; show required skill for recipes.
b) Zone level "1–60" for Elwynn and Westfall (min/max of all NPCs incl. guards) → should be the zone band (1–10, 10–20); header repeats "Zone #12 · Zone".
c) Map pins identical white dots with no legend/label/hover hint; the Westfall page shows a blank block where the map should be (capture artefact?); the Recent dropdown stores half-typed fragments ("westfa", "west", "van"); Edwin VanCleef shows "Rare Elite" in the Deadmines list (should be elite boss) — rank mapping to verify.

## Build first
The crafting graph + skill strip (Created by, Reagent for, Teaches, orange/yellow/green/grey) — client DB2 only; turns every trade good from a dead end into "what is this worth to me".
