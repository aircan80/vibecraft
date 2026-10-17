# PM memo: dungeon floor maps on the instance page (2026-10-17)

Owner request: instance and dungeon floor maps on the dungeon page.

## Facts added to the controller's
- **Atlas already draws Forever.** The map module `arithmandar/Atlas-Kalimdor-and-Eastern-Kingdoms` (TOC
  `Atlas_ClassicWoW`) lists Interface 16001, the Forever client. It was pushed 2026-10-16 and carries 98 BLP
  images.
  - Forever images included: `CF_CityofDalaranA/B`, `CF_ExcavationSiteWetlands`, `CF_HallofThanes`,
    `CF_RuinsofLordaeron`, plus `CF_Gnomeregan` and `CF_RagefireChasm`.
  - It is on GitHub, so it can be pinned by commit, with no CurseForge step.
- **Atlas licence.**
  - The module's `Docs/Readme-enUS.txt` says "Atlas is released under the GNU General Public License (GPL)", pointing
    to gpl-v2. The text ships in the core repo's `Docs/`; the module repo has no LICENSE file.
  - Copyright: Dan Gilbert 2005–2010, Lothaer 2010, Arith Hsu and the Atlas Team 2011–2026. TOC credits:
    nanderson11, Dynaletik, dubcat.
  - It asks anyone including its graphics to email the team (atlas.addon at gmail dot com), credit the team
    members, and distribute free of charge.
- **Atlas art is derived from Blizzard art.** Its readme says the maps "are based on data used by the minimap". They
  are community-made, but derived from Blizzard minimap art.
- **Our project is GPL-2.0** (LICENSE), so the licence is compatible.
- **Collector positions are probably empty inside instances.** With no dungeon-type UiMaps in this client line, the
  player's map position most likely returns nothing inside dungeons. Task 20's `apiFailures` will confirm this in the
  beta run. Do not plan features on boss positions until it does.

## Verdict per option
| Option | Licence verdict | Product verdict |
|---|---|---|
| (a) Atlas floor maps | GPL-2.0, compatible with our GPL-2.0 project. Conditions: ship the GPL text, credit the Atlas team in NOTICE and in the map caption, and send the courtesy email. The email is outward-facing, so the owner sends it or approves our draft. Because the art derives from Blizzard minimap art, it goes beyond the owner's "client art for icons only" rule: **owner call needed** | **In, v0.3.2, if the owner says yes.** It covers all Classic dungeons and four Forever dungeons today, and it is maintained |
| (b) Retail client floor art | Blizzard art beyond icons: owner call needed | **Out.** The layouts are wrong for dungeons reworked in Cataclysm and later (The Deadmines, Shadowfang Keep, Scholomance, Scarlet Monastery). There is no art for Forever's new dungeons. It needs a new retail-client pipeline |
| (c) Encounter-order list, ours | No third-party licence | **In now** as part of Task 18: numbered badges on the boss list in client encounter order. A positional diagram is **Later** and depends on (a) plus confirmed positions |

## Placement and size
- **(c), size S, inside Task 18 (no new data).**
  - The boss list gets numbered badges 1…n in DungeonEncounter `OrderIndex` order, or AtlasLoot order for Classic
    dungeons without client rows.
  - The caption reads "Encounter order in the game client". It never says "kill order", because some bosses are
    optional.
  - The badge component is the one already used for objective numbers.
- **(a), size M (2 tasks), as v0.3.2 "Dungeon maps"**, right after v0.3.1 "Maps". It reuses that release's map viewer
  work, and nothing in 3B waits on it.
  1. **Data (S–M).**
     - `tools/atlasmaps` pins the module commit and the core's gpl-v2 text by SHA-256.
     - The existing BLP→PNG converter turns the images into JPEG at 1024 px. Expect about 10 MB; it is reported in
       the import report.
     - Images map to instances, with floors A/B and the "Ent" entrance-area images, from the module's `Data`.
     - The Atlas legend (number → name) goes into `instance_map(instance_id, floor, label, jpeg)` and
       `instance_map_legend(instance_id, floor, number, name, npc_id null)`.
     - The NPC id is filled only on an exact name match with our NPC table; it is never guessed.
  2. **UI (M).**
     - An "Atlas map" section on the instance page, after Encounters, with floor tabs, zoom and pan in the existing
       map viewer.
     - The legend list sits beside the map (below it in compact). Legend names link to our NPC pages when the id
       matched.
     - In compact mode the section is collapsed with a count.
- **Gate to start (a):** the owner's yes on the art question, and the courtesy email sent.

## Presentation rules for (a)
- **Caption, always visible under the map:** "Map: Atlas (GPL-2.0) by Dan Gilbert, Lothaer, Arith Hsu and the Atlas
  Team". Forever dungeons add "drawn for the Forever beta".
- **Authority.** Our boss list stays the authority. Where the Atlas legend and DungeonEncounter differ in names or
  order, both show, and the legend is labelled as Atlas's.
- **No pins of our own on Atlas images.** They are not projected through a UiMap, so we have no coordinate system to
  place anything honestly.
- **No routes, pull paths or "recommended" order.** Those are guide features (backlog decision 8).
- **A dungeon without an Atlas map** (for example Drowned City or Krol'dok Stronghold until Atlas draws them) shows
  "No floor map for this dungeon yet." and the boss list from (c).

## Backlog v5 rows
| Feature | Value | Effort | Data | Rel. | Decision |
|---|---|---|---|---|---|
| Encounter-order badges on boss lists | M | S | DungeonEncounter, AtlasLoot | 0.3.0 (T18) | In |
| Dungeon floor maps (Atlas) | H | M | Atlas map module, GPL-2.0 | 0.3.2 | In, pending the owner's art call |
| Retail floor art | — | — | retail client | — | Out (wrong layouts, no Forever coverage) |
| Boss positions from the collector | M | M | collector | — | Later; probably blocked inside instances (verify in the Task 20 beta run) |

## Owner question to add
May the app ship Atlas's dungeon maps? They are GPL community maps derived from Blizzard minimap art, which is more
than icons. **Default:** yes, with Atlas credited and emailed; the Forever beta images included.
