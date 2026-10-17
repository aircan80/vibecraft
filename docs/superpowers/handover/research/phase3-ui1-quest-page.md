# UI designer 1 memo — quest page design + WoW-like addendum (2026-10-17)

Principle: the page answers, in order, where do I pick it up, what do I do and where, where do I hand it in, what comes next. Objectives and map are one unit: selecting a row filters the pins.

## Section order
1. Header: name, "Lv 12 (req. 9)", zone, faction chip; below it the chain stepper "‹ Previous · This quest · Next ›  Step 2 of 5".
2. Objectives (left) + Map (right), side by side.
3. "Given by" / "Turned in to": LinkTables with NPC/object/item, zone, coordinates.
4. "Quest chain": full vertical tree.
5. "Rewards", then "Related items".

## Objectives
Row = numbered pin badge, verb, count ("×10", leading, bold; no "0/10"), target, "where" line:
- "① Kill ×10 Kobold Vermin" / "Echo Ridge Mine · 18 spawns"
- "② Collect ×8 Gold Dust" / "Drops from Kobold Laborer 34%, Kobold Tunneler 28%, +3 more"
- "③ Use ×1 Mine Cart" / "Fargodeep Mine · 2 locations"
- One line under the rows: "You carry: Sealed Letter (given at start)." (no pins)
Clicking a row shows only that objective's pins; giver/turn-in at 50 % opacity; chip "Showing objective 2 ✕" clears. Collect objectives pin the top 5 sources by rate, "Show all 9 sources" lifts the cap; pin tooltips give NPC + rate.

## Map: one view, zone tabs
Single MapView; zone chips above it list every zone involved with the glyphs it contains: "[Elwynn Forest ! ① ②] [Stormwind City ?]"; opens on the giver's zone; an edge banner "Turn-in is in Stormwind City ›" switches tabs. Dense spawns use the existing clusters drawn in the objective's shape with a count.

## Pins (same art in both themes)
1.5 px #0C1424 outline + 1 px #F4EBD0 halo. Shape + glyph carry meaning:
Start = gold #D6B25E disc, dark "!"; Turn-in = gold disc, dark "?"; Kill = red #E5705C circle with the objective number; Collect = green #7CC46B diamond with number; Interact = blue #6FA8DC rounded square with number. Legend chips under the map double as layer toggles.

## Chain
Header stepper shows previous / this / next only. "Quest chain" section = vertical tree, rows "Lv 10  Name — Zone", current = 3 px gold left bar + bold. Connectors: "Requires all of:", "Requires one of:", "Choose one (exclusive):" joined by "or", "Unlocks:" indented. Every row a link; long chains show 2 steps either side + "Show 6 earlier steps".

## Compact (420 px)
Single column; stepper "‹ Prev · 2/5 · Next ›"; map full width 400×267; zone chips → dropdown; legend wraps; objectives open; Given by / Turned in to one line each; Quest chain, Rewards, Related items collapsed.

## Keys
G Given by, T Turned in to, O Objectives, M Map, C Chain, R Rewards; 1–9 filter the map to objective n, Esc clears; [ ] previous/next quest in the chain; Z cycles zone tabs.

## Empty states (exact copy)
No map: "No map for Deadwind Pass yet." + coordinate list "Kobold Miner — 42.1, 57.8 (+11)". No source: "Source unknown — not in the quest database yet." Instance objective: "Inside The Deadmines — open dungeon page ›". No chain: "Standalone quest — no prerequisites or follow-ups." No giver: "Starts automatically or from an unknown source." Item start: "Starts from item: Bloodstained Note — drops from ›" + source pins.

## Wireframes
Full window: header + stepper; left Objectives column (①②③ + "You carry"), right map 540×360 with zone chips and legend chips; then Given by, Turned in to, Quest chain, Rewards · Related items.
Compact: header; stepper line; "Zone: [Elwynn Forest ▾]"; map 400×267; legend; Objectives; Given by / Turn in lines; collapsed Quest chain (4), Rewards (3), Related items.

## Addendum — making the whole app more WoW-like (top five)
1. Rail icons in WoW's idiom, original monochrome SVG (FlatSVGIcon), text.muted at rest, accent.gold selected: Home = hearthstone (rounded stone with a rune), Items = drawstring pouch, NPCs = face in profile, Quests = "!" in a disc, Zones = folded map, Dungeons = stone archway. Labels under icons in full mode; tooltip in compact.
2. Panels framed like in-game windows without textures: bg.surface cards with a 1 px accent.gold at 30 % border, 2 px dark inner line, faint gold underline on heading rows.
3. One tooltip style everywhere (the existing card) for item, NPC, map pin and chain-step hovers.
4. Disciplined colour: quality colours on every item name; dark-mode rare/epic lightened (#4A9EFF, #B866F5); light-mode darkened variants at 4.5:1; gold reserved for headings, selection and quest markers.
5. Typography: bundle an OFL display serif (e.g. Cinzel) for page titles and section headings in caps with slight letter-spacing; body/tables stay system sans.
Fit: the "!" rail icon = the giver pin glyph; objective number badges are one component shared by pins and rows.
