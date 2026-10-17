# PM memo: map layers for instance entrances and flight paths (2026-10-17)

Owner requests: on zone maps, show (1) dungeon and raid entrances and exits, and (2) flight paths.

## Placement: one "map layers" slice as v0.3.1, right after v0.3.0. Not inside 3B.
- **3B is already stretched.** It carries one exception, the Forever dungeons. The world-to-map transform is new
  infrastructure with its own maths and failure modes, such as several UiMaps per continent, sub-zone maps and
  instance maps, and it deserves its own review rather than a slot before Task 16.
- **Task 8 decides the shape.** It is reworking layers and the legend now, so the new layers should plug into the
  model it lands rather than race it.
- **One carry-over into the Task 8 brief, with no data work.** Layers are registered, not hard-coded. Each layer
  declares an id, a legend chip, a default state and its preset membership. Adding a layer must not edit the legend
  or the presets. This costs Task 8 almost nothing and makes v0.3.1 additive.
- **Backlog v5 will record two moves**, both owner requests:
  - The flight-path layer moves from v0.7.0 to v0.3.1.
  - Entrance pins move from the v0.4.1 ladder and instance pages to v0.3.1.

## Size: about 4 tasks, M–L
| # | Task | Data | Size |
|---|---|---|---|
| 1 | World-to-map transform | UiMapAssignment (UiMapID, MapID, Region min/max) → zone map percentage; pick the most specific assignment that contains the point | M |
| 2 | Entrances and exits | Instance entrance triggers: VMaNGOS `areatrigger_teleport` gives the target instance map. The trigger's own world position comes from the client AreaTrigger table if VMaNGOS lacks it (verify). Exit = landing point of the instance's exit trigger. Forever's four dungeons: client AreaTrigger rows (verify); if absent, "Entrance not yet recorded" | S |
| 3 | Flight paths | TaxiNodes (Pos, ContinentID, Flags; faction from MountCreatureID_0/1, verify which is Horde/Alliance), TaxiPath (From/To), TaxiPathNode (lines) | S |
| 4 | Two layers in the UI, plus an entrance section on instance pages | Task 8's layer registry, existing pins and tooltip card | M |

Transform acceptance:
- 20 NPCs with both a VMaNGOS world spawn and a Questie map percentage agree within 1 point after the transform.
- Hogger and the Defias Messenger are among them.
- The transform is pure and headless-tested, including one point outside every assignment, which returns "no map".

## Presentation rules
**Entrances layer** (default on):
- **Pin.** A navy plate with the Dungeons & Raids rail icon at 16 px, a gold border for dungeons and a red border for
  raids. The shape keeps it distinct from quest and objective pins, and colour never carries meaning alone: the
  tooltip names the type.
- **Label.** The instance name sits beside the pin at every zoom. There are few entrances per zone, so labels fit.
- **Tooltip.** "The Deadmines · Dungeon · Levels 10–17", or "Level 28+ · New in Forever" for a Forever dungeon.
- **Click.** Opens the instance page.
- **Wings and multiple entrances** (Scarlet Monastery, Dire Maul): one pin per wing, labelled by wing.
- **Exits.** Shown only when the landing point is more than 2 map points from the entrance. The marker is a hollow
  plate, "Exit of Blackrock Depths". Otherwise the entrance pin stands for both, and its tooltip says "Exit lands here".
- **Instances entered from another instance** (Molten Core via Blackrock Depths): no zone pin. The instance page
  says "Entered from Blackrock Depths".
- **Instance pages** gain an "Entrance" section with a small map and a "/way" copy.

**Flight paths layer** (default on for nodes; routes off):
- **Nodes.** A circle with a wing glyph. The border is blue for Alliance, red for Horde and gold for neutral, and a
  letter A, H or N is printed so colour is not the only cue.
- **Tooltip.** "Sentinel Hill · Alliance flight path · Flies to: Stormwind, Lakeshire, Ironforge …" from TaxiPath.
- **Click.** Opens the flight master NPC when one matches by flight-master flag within 3 map points (Thor 523 at
  Sentinel Hill). Otherwise there is no click, only the tooltip.
- **Routes.** A legend sub-toggle "Routes" draws thin dashed lines from TaxiPathNode, clipped to the zone, with the
  destination name where a line leaves the map. Off by default, because lines across a zone map bury the quest and
  spawn pins. The tooltip's "Flies to" list answers the same question without clutter.
- **Forever nodes.** The 13 new ones show like any other node. The "New in Forever" chip arrives with v0.4's diff,
  not here.
- **Presets.** Both layers join a "Travel" preset; quest-focused presets turn them off.

Never shown: travel-time estimates, "fastest route" or "recommended flight path". Those are guide features
(backlog decision 8).

## v0.3.1 ship gates (in addition to the standard gates)
- The transform test above passes.
- Westfall shows The Deadmines entrance near Moonbrook, and Sentinel Hill linked to Thor 523.
- Stormwind shows its flight path. Elwynn shows no entrance pins.
- Screenshots of Westfall and Burning Steppes, the second with both layers.
- The zone map's p95 load stays within the 300 ms budget with both layers on.
