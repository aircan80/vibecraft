# PM memo: Zones world-map drill-down (2026-10-17)

Owner request: the Zones rail page opens on a map of Azeroth. Clicking a continent shows its zones, drawn and
labelled, and clicking a zone opens the zone page ("more intuitive than having to search").

## Placement: v0.3.1, first in the slice, ahead of the map layers
- **Not inside 3B.** v0.3.0 already carries one exception (the Forever dungeons), and this is a new page, not a fix.
- **Not v0.4.** The owner names it as a navigation pain, and the data is already fetched.
- **No dependency on the transform.** UiMapAssignment's UiMin/UiMax bounds are already in parent-map space, so the
  drill-down needs no world-coordinate transform. It can go first and land fast.
- **v0.3.1 becomes the "Maps" release.**
  - World map: 2 tasks.
  - Then the map layers memo (pm-map-layers.md): transform, entrances, flight paths, layers UI, 4 tasks.
- **Cut line.** If v0.3.1 runs long, the layers ship as v0.3.2 and the world map is never the part that slips.
- **Backlog v5** will add the row "World map drill-down · H · M · UiMap, UiMapArt, UiMapAssignment · 0.3.1 · In".

## Size: M (2 tasks)
| # | Task | Data / scope | Size |
|---|---|---|---|
| 1 | World map data | Art for UiMaps 947, 1414 and 1415, stitched from UiMapArt/UiMapArtTile like the 57 zone maps. A table `ui_map_child(parent_ui_map_id, child_ui_map_id, x0, y0, x1, y1)` from UiMapAssignment bounds for children of 947, 1414 and 1415, linked to `zone` through the existing `zone.ui_map_id`. "New in Forever" for a child map absent from the Era UiMap table: fetch Era UiMap through the 2A fallback route, using the same rule backlog F2 generalises later. Shipped tests: 1415 contains Westfall's UiMap; every `zone` with a UiMap under 1414/1415 has a rectangle | S |
| 2 | WorldMapPage | Three levels in one page (Azeroth › continent), with a breadcrumb because this is real hierarchy. Hover and focus highlight, labels, click to open the zone page, keyboard, compact mode, and a "Map / List" toggle that remembers its choice and keeps today's list as List | M |

## What each level shows
**Azeroth (947):**
- Kalimdor and Eastern Kingdoms as the two clickable regions, with names.
- Any other child map the client places under 947, for example a Forever island such as Zephras Isle (verify), shown
  the same way.
- No dungeons, quests or pins. This level is only for choosing a continent.

**Continent (1414 or 1415):**
- Every zone rectangle, with a gold hairline outline on hover and focus.
- Each name sits on a dark plate with a halo, so it measures at least 4.5:1 on the plate.
- A name is drawn when its region is at least 40 px wide at the current size. Smaller regions, such as Stormwind
  City, show it on hover and focus only.
- **Tooltip, facts only:**
  - "Westfall · Levels 10–20 · Alliance territory · 2 dungeons · 98 quests".
  - The level band comes from the 2B Task 14 fix. Territory comes from AreaTable flags (verify); it is omitted when
    unknown.
- **Forever zones** carry a "New in Forever" chip in the label and the tooltip.
- **A zone without map art** is drawn as an outlined rectangle. Its tooltip reads "No map art in this client build".
- **A zone not in our database** is not clickable. Its tooltip reads "Not in the database yet".
- **No entrance or flight-path pins here.** They live on zone maps (v0.3.1 layers), and the continent stays readable.

**Zone:** clicking opens the existing zone page. Back returns to the continent with the same zone focused.

## Keyboard, compact, list
- **Keyboard.** Tab and Shift+Tab move through regions in reading order. Enter opens. Backspace or Alt+Left goes up
  a level, and Esc clears the focus. Each region has an accessible name, for example "Westfall, zone, levels 10–20".
- **Compact (420 px).** The continent art is scaled to the width and labels appear on hover and focus only. The
  Map / List toggle stays in the header.
- **List.** Today's zone list, unchanged. The rail page opens on whichever view was used last, and on Map the first
  time.

## Research framing
- The map describes geography: names, level bands, territory and counts.
- It never colours zones by suitability, never highlights zones "for your level", and never draws "next zone" arrows
  or progress tints.
- Level bands are text, never a colour scale.
- There is no stored character (backlog decision 17), so the map cannot and does not say where you should go.

## v0.3.1 gates for this part
- Mouse path: Zones rail, then Eastern Kingdoms, then Westfall opens zone 40 in three clicks.
- The same path works keyboard-only.
- The continent page renders within the 300 ms p95 budget.
- Screenshots of Azeroth, Eastern Kingdoms and Kalimdor in dark, light and compact.
- At least one Forever zone appears with its chip, or the release notes state that the client has none under 947,
  1414 or 1415.
