# PM memo: Forever dungeons pull-forward and client refresh (2026-10-17)

Trigger: the owner asked why the new WoW: Forever dungeons are missing and whether our data is current.
Answer for the owner: the dungeon list comes only from AtlasLootClassic, which knows the 33 vanilla instances. The
client build already holds four Forever dungeons, so this is our gap, not missing data.

## Extra facts checked (wago.tools CSV, builds 70425 and 70450, 2026-10-17)
- **Bosses do exist client-side.** `DungeonEncounter` names them for the four beta dungeons, in order. There is still
  no loot and no Dungeon Journal.
  - City of Dalaran (2959), 9 bosses: Arcane Anomaly … Shade of the Archmage, Lyn the Ignored, Atrexis the Grave Knight, Mana Wraith.
  - Ruins of Lordaeron (2999), 7 bosses: Witherfang, The Abandoned, The Butcher, Rath'mael, Lordaeron Captain, Viktor the Vile, Bjork.
  - Excavation Site: Wetlands (2998), 4 bosses: Saltspine, Shadetooth, Highland Horror, Relic Guardian.
  - The Hall of Thanes (3065), 4 bosses: Faldrim Anvilmar, Infurnus, Plunder, Durgen Dirgehammer.
  - Map 3002 has 2 bosses (Deathulus, Crushfist Bloodbreaker) but no name; leave it out.
  - wago served identical `DungeonEncounter` files for both builds.
- **Level bands do not resolve.** `LFGDungeons` 3271–3274 → `ContentTuning` gives one level, `MinLevelSquish`:
  Dalaran 28, Ruins 15, Thanes 13, Excavation 26. Each equals the low end of the band Blizzard announced, or of the
  beta band for Excavation (26–31). `LfgMin/MaxLevel` is 0 or inconsistent (Ruins 27), so ignore it. Show "Level 28+",
  not a band.
- **Map links need the name.** `LFGDungeons.MapID` is 0 for the four, so link to `Map` by name.

## (a) Refresh client data to 70450: yes, now, once
- **Why now.** The owner asked whether the data is current. 70450 is the installed build and wago serves it.
- **One regeneration.** Do it in the same regeneration as the dungeon data in (b), so 3B regenerates only once.
- **Where in 3B.** Insert it as a data task right after the task in flight (Task 5), before Task 6. Tasks 6–15
  then pin their shipped-data tests and screenshots against final data.
- **Tests.** Shipped tests should pin facts by id: names, relations, sources. Totals such as client_item_count
  20,492 become floors. The refresh commit records the old and new counts in the import report.
- **Repeatable.** The live build (4 Nov) forces another regeneration, so the refresh must stay one command plus a
  build.lock bump.

## (b) Thin "Forever dungeons" slice: yes, in v0.3.0
Two tasks, about M in total. It is a recorded exception to decision 7 (scope frozen), because the owner reported it
as a bug. Backlog v5 will log it, and v0.4.1's instance discovery pages and F1 states later generalise it. If 3B has
already reached its final review when this is approved, ship it as v0.3.1 instead.

1. **Data (S–M, with the refresh).**
   - `instance` gains `source` (`atlasloot` or `client`), `tuned_min_level`, `bosses_state` and `loot_state`.
   - Four new rows come from `Map` (InstanceType 1) and `LFGDungeons`, joined by name, plus `ContentTuning`.
   - `boss` rows come from `DungeonEncounter`, ordered by `OrderIndex`, with no loot.
   - Shipped tests: 2959 has 9 bosses with level 28, and 3065 starts with Faldrim Anvilmar.
2. **UI (S, before Task 16 so the screenshots include it).**
   - A "New in Forever" chip, and "Level 28+" in the dungeons list, sorted among the Classic dungeons.
   - A boss list with no loot table.
   - Empty-state copy and the coverage line below.
   - One cited "Announced, not yet in this client" line on the Dungeons & Raids list.
   - Screenshot example: City of Dalaran.

Deliberately not in the slice:
- AreaTable-only names (Blackmaw Hold, The Shaper's Terrace, Krol'dok Stronghold, Alcaz Island) get no pages.
  Calling an area a dungeon is a guess, and the page would be empty.
- No name-matched items (decision 18).
- Darkspear Islands is a battleground, so it stays with the v0.4.1 PvP pages.

## (c) How to present dungeons whose bosses or loot are unknown
Describe what the data knows, name its source, and say what is not recorded yet. Never imply player progress or a
recommendation. Copy:
- **Header chips:** `Dungeon` · `New in Forever` · `Level 28+` · `5 players`. The level chip hover reads "Tuning
  level from the game client".
- **Bosses (9):** names in client order, each with the muted note "Loot not recorded yet". Under the heading: "Boss
  names come from the game client. WoW: Forever has no Dungeon Journal, so abilities and loot are recorded only after
  players meet these bosses."
- **Loot:** "No loot recorded yet for this dungeon. Drops appear here when a data update records them."
- **Coverage line,** which describes our data and not the player: "Bosses: 9 named in client data · Loot: 0
  confirmed items · Client build 1.60.1.70450".
- **Dungeons & Raids list footnote:** "Announced by Blizzard, not yet in this client build: Drowned City, Krol'dok
  Stronghold, Alcaz Island Prison, Blackmaw Hold, The Shaper's Terrace; raids Barrow Deeps and Hyjal Summit (open
  9 Dec 2026)." It carries one source link and follows decision 24 and open question 5's default.
- **"Is the data current":** the status bar and the Home footer already show the build. After the refresh they read
  "Forever beta 1.60.1.70450". No other copy is needed.
- **Never shown:** "recommended for you", completion ticks, "run this next", or guessed loot.
