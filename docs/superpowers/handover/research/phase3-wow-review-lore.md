# WoW expert review 3 — lore, exploration and learning (2026-10-17)

## Missing for a curious player
Zone page: no intro text; sub-area list nearly empty (Elwynn shows only Northshire); no flight masters/innkeepers/"quests that start here"; no faction marker. Links: an NPC page does not say which quests send you to it or mention it; an item page not which quest needs it. Storylines: cannot follow an arc (Defias Brotherhood) as one piece. Factions: no pages. Item flavour text and readable letters/books missing. Hunter pet info (tameable, family) missing. Rare details: spawn-point count, placeholders; respawn timers not in any current source.

## Additions ranked
1. Zone gazetteer: sub-areas with levels, flight masters, innkeepers, quests started here, instances inside — AreaTable (ParentAreaID, ExplorationLevel, flags), TaxiNodes, Questie npcFlags/friendlyToFaction, quest zoneOrSort.
2. "Mentioned in" / "Involved in" on NPC, item and zone pages — a name index over Questie quest text + objectivesText, questStarts/Ends, creature/item objectives; no new data.
3. Storylines browser: linked quests grouped into named arcs with zones crossed — chain fields; name = last quest.
4. Faction pages — Faction.db2 (name, description, parent), FactionTemplate via Questie factionID, quest reputationReward.
5. Item flavour and readable text — ItemSparse.Description, PageText (if shipped), Questie item startQuest.
6. Hunter pet panel — Creature.db2 Type/Family + CreatureFamily (check the Classic client).
7. Flight-path layer on zone maps — TaxiNodes, TaxiPath.
8. Rare spawn details — spawn points/placeholders from Questie; respawn needs a server export (spawntimesecs), "unknown" until then.

## Home and search as a learning entry point
Tiles: Continents → zones by descriptive level band (no ordering); Storylines; Factions; Dungeons & raids by level; Rares of the world; Glossary (elite, rare elite, breadcrumb, BoP/BoE, placeholder, exclusive quest; also hover tooltips); "From the archives" (a random quest text or item quote each launch); Recently viewed. Search: "search inside quest text" mode; kind shown clearly ("Westfall (zone)" vs "Westfall (signpost object)"); results grouped by kind. Not a leveling guide: no XP rates, no "do these next", no optimal order.

## Screenshot findings (→ 2B Task 14 polish)
1. Zone level ranges "1–60" wrong; Westfall's only "sub-zone" is The Deadmines, already listed as a dungeon.
2. Westfall NPC table mixes Deadmines elites with open-world NPCs; Gryan Stoutmantle (friendly) listed as "Elite 35" among hostiles; no hostile/friendly or "inside instance" column; blank gap above the table (map area).
3. Labels: Molten Core loot "Slot" column shows AtlasLoot entry order (1…30), not an equipment slot; "Westfall Deed" reads "Quest · iLvl 8" like a quest (it is a quest item); rare pins are plain white dots with "2" badges, no names/legend.

## Build first
The zone gazetteer (correct level ranges and sub-areas from AreaTable): fixes the most visible wrong data and makes the zone page the hub for storylines, factions, flight paths and "mentioned in".
