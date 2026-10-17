# UI designer 3 memo — information architecture and clean-up (2026-10-17)

## Critique
Noise: the summary line lists negatives ("no vendor · no quests"); empty sections render; "≠" has no legend; three ways to browse the same thing (rail, kind chips, list pages); Home welcome text is dead space; the 300 px master list is a leftover once a page is open.
Missing: recent pages; a "best source" answer at the top; level/zone context in every header; counts in headings.
Wrong place: the drop-source filter in the View menu (belongs on the sections); "Dropped by" and "In instance loot" are one question split in two; "Data build" belongs on Home.

## Shared page skeleton
Header: 40 px icon, name (quality colour), kind word, chips (level, zone, faction/reaction, rank, group size); pin + back on the right. Facts strip: one line, ≤ 5 positive facts, each clickable to its section. Sections: fixed order per type, hidden when empty, heading "Name (count)", 8 rows then "Show all 34".
- Item (card in the header): Dropped by (merged with instance loot; instance as a chip; by rate) / Sold by / Quest reward / Contained in / Starts a quest.
- NPC: Drops (notable first: epics, quest items; then by category) / Sells / Quests (starts+ends merged with ! and ?) / Location (map).
- Quest: Objectives / Rewards / Given by / Turned in to / Quest chain / Related items.
- Zone: Map (pins, toggles) / Dungeons & Raids / Quests / NPCs (filters: rares, vendors, trainers) / Objects.
- Instance: Encounters (click filters Loot) / Loot (by boss default, by slot toggle) / Trash drops / Quests / Entrance (map).
Ctrl+1..n = nth visible section.

## Navigation model
Rail entries open list pages with remembered filters; Home = dashboard (big search, Pinned grid, Recent last 12, "What's new in build …" with per-kind counts, data build footer; featured item opt-in). Back/forward on Alt+arrows and mouse 4/5; breadcrumbs only for real hierarchy (Zone › Instance › Encounter). Kind chips only inside search results/dropdown. Master list shows while searching, auto-collapses when a result is opened, Ctrl+B pins it open; stays visible by default ≥ 1600 px.

## While-playing mode (compact 420×720)
Search on top, one page below, results as a dropdown, no master list; pinned pages as a tab strip (max 6, Ctrl+1..6 switch tabs); sections collapse to "Heading (count)" except the first; always-on-top toggle. Later: a ~300 px always-on-top mini tooltip card for one pinned item (tooltip + top 3 sources), never takes focus from the game.

## Empty and loading copy
No results: "No matches for 'xyz'. Try fewer letters." + [Search all kinds]. Item with no source: "No known source in this data build. It may be unobtainable or not yet datamined." Filter hides everything: "All 12 drops are hidden by the source filter." + [Show all]. Recent empty: "Pages you open appear here. Press Ctrl+F to search." Pinned empty: "Pin any page with Ctrl+D to keep it one click away." No map: "No map for this area." Startup: "Loading data build 1.60.1.70425… items 12,400 / 26,000". Page load: header immediately, "Loading drops…" per section only after 150 ms.

## Prioritised changes
1. Hide empty sections, drop negative facts, counts in headings (high value, trivial).
2. Back/forward on Alt+arrows and mouse 4/5 (already exists — verify).
3. Home = Pinned + Recent + What's new.
4. Merge "Dropped by" and "In instance loot" into one rate-sorted "Dropped by" with instance chips.
5. Auto-collapse the master list after opening a result; Ctrl+B keeps it.
6. Standard header chips and section order per page type.
7. Drop-source filter on the sections; "≠" hover legend.
8. Compact mode around search + pinned tabs + always-on-top; mini tooltip as a follow-up.
