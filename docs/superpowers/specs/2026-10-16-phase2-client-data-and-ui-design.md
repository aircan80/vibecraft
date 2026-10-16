# Phase 2: Client data (stats, icons, maps) and UI overhaul — Design

Date: 2026-10-16
Status: approved for planning
Builds on: v0.1 (tag `v0.1.0`), the v0.1 design
(`2026-10-16-wow-forever-item-browser-design.md`), the datamining feasibility report
(`2026-10-16-forever-datamining-feasibility.md`) and the UI design review
(`2026-10-16-ui-design-review.md`). Where this document and those disagree, this one wins.

## 1. Intent and why

v0.1 answers "what drops X and where" from addon databases. The owner wants it to feel like a
locally installed Wowhead: item icons, quality colours, full stats and tooltip text, zone maps
with spawn pins, and a UI built for a companion app used on a second monitor **and** alt-tabbed
on one screen. The owner has also asked that conflicting data from different sources be visible
rather than silently resolved.

Phase 2 is two sub-projects, planned and executed in order:

- **2A — Client data pipeline.** A build-time tool that turns Blizzard client tables and files
  for the Forever launch build into new SQLite tables and embedded images, plus provenance for
  drop rates. Delivers working software on its own: the database gains stats, quality, tooltip
  lines, icons and maps, visible through minimal additions to the existing UI (quality colour on
  names, an icon next to the item header, a map image on zone pages).
- **2B — UI overhaul.** The UI review's P1 and P2 recommendations, consuming 2A's data.

## 2. Decisions the owner made (2026-10-16)

1. **Ship Blizzard icons and map art inside the app.** The redistribution grey area is accepted.
2. **Target the launch product `wowf`** (1.60.1.70245 on 2026-10-16). The pipeline must accept
   `wow_classic_beta` as an override for testing before launch.
3. **Python with Pillow is allowed at build time** (never shipped to users). The app stays pure
   Java.
4. **Both usage setups count:** second monitor and single-screen alt-tab. Compact mode is in
   scope for 2B.

Calls made by the author on the owner's behalf (check these):

5. Provenance work from the roadmap (keep every competing drop-rate value, conflict report,
   disagreement marker) is folded into 2A because the drop-rate stage is being touched anyway.
   The UI source filter (roadmap B10) is deferred to 2B as a P2 item.
6. The datamining fetch goes through wago.tools once per build, throttled, with a contact
   User-Agent, and caches everything; TACTSharp against Blizzard's CDN is the documented
   fallback, not implemented in 2A. wago.tools' robots.txt disallows crawlers; a one-time,
   throttled fetch of ~30 CSVs and ~3,000 small files per build is judged acceptable use, and
   the project owner should say hello to the wago team on their Discord.
7. Assets are stored as BLOBs inside the shipped SQLite (one `icon` table and one `zone_map`
   table), so the app still ships one data file. Expected growth: 26 MB → roughly 55 MB.
   Git history grows by that much per regeneration; if it becomes a problem, move the file to
   Git LFS or a release asset (not in this phase).
8. Fully revealed maps (base art plus every "explored" overlay composited) are shipped. Dungeon
   interiors have no maps in the client; instance pages keep lists.
9. Stat and tooltip correctness is proven against a hand-collected fixture of about 25 known
   items (values read by a human from the game or Wowhead's Forever pages and typed into a test
   file; no scraping). Items whose computed tooltip does not match the fixture fail the build.

## 3. Goals, non-goals, anti-goals

### Goals
1. Every item with client data shows quality (colour), item level, binding, slot, armour or
   damage/speed/DPS, stats, resistances, durability, requirements, Use/Equip/Chance-on-hit
   text, set name and bonuses, flavour text, sell price, stack size, in the game's order.
2. Every such item has a 56 px icon; lists show it at 18–28 px; the question-mark icon is the
   fallback.
3. Every Questie zone that has a client world map shows it, fully revealed, with NPC and object
   spawn pins; NPC pages pin that NPC; zone pages pin bosses and rares by default with a toggle
   for all.
4. Drop rates keep every source's value; the UI shows the chosen value with its source and marks
   disagreements; the import report lists conflicts.
5. The UI follows the review's P1 list in full and its P2 list except where §6 says otherwise.
6. Build-time only network use; the app stays offline; one shipped data file.
7. Test discipline as in v0.1: unit, fixture-based integration, shipped-data sanity, CI on
   three OSes.

### Non-goals (this phase)
- 3D model renders (roadmap A4), crafting/PvP/faction data (A6), VMaNGOS drop rates (A5), the
  equivalence check (C11), "usable by my character" (needs a character profile), localisation.
- Dungeon interior maps (none exist in the client).
- A Java BLP decoder (Python does it at build time).

### Anti-goals
- Network access at runtime; a web view; scraping Wowhead.
- Silently wrong numbers: a stat formula that cannot be validated ships as "unavailable", never
  as a guess. A tooltip line with an unknown placeholder shows the raw text.
- Breaking v0.1 behaviour: every v0.1 test keeps passing; the importer still runs with
  `--skip-client-data` and produces a database the app can open (no icons, no stats).

## 4. Sub-project 2A — Client data pipeline

### 4.1 Sources (verified 2026-10-16, see the feasibility report)
- Build list: `https://wago.tools/api/builds` (products `wowf`, `wow_classic_beta`).
- DB2 CSV: `https://wago.tools/db2/{Table}/csv?build={build}`. Tables: `Item`, `ItemSparse`,
  `ItemClass`, `ItemSubClass`, `ItemNameDescription`, `ItemXItemEffect`, `ItemEffect`, `Spell`,
  `SpellName`, `SpellEffect`, `SpellMisc`, `SpellDuration`, `SpellItemEnchantment`, `ItemSet`,
  `ItemSetSpell`, `RandPropPoints`, `ItemDamageOneHand`, `ItemDamageOneHandCaster`,
  `ItemDamageTwoHand`, `ItemDamageTwoHandCaster`, `ItemDamageRanged`, `ItemDamageThrown`,
  `ItemDamageWand`, `ItemDamageAmmo`, `ItemArmorTotal`, `ItemArmorQuality`, `ItemArmorShield`,
  `ArmorLocation`, `UiMap`, `UiMapArt`, `UiMapArtTile`, `UiMapArtStyleLayer`, `UiMapXMapArt`,
  `UiMapAssignment`, `WorldMapOverlay`, `WorldMapOverlayTile`, `AreaTable`, `Map`,
  `ChrRaces`, `ChrClasses`, `SkillLine`, `Faction`. Exact column names come from the CSV
  headers at fetch time; the pipeline fails loudly if a required column is missing.
- Files: `https://wago.tools/api/casc/{FileDataID}?download&branch={product}` → BLP2.
- **Local alternative (recommended on the owner's machine):** wow.tools.local (MIT; wago.tools'
  own code as a localhost service) run against the local install with
  `-wowFolder "C:\Program Files (x86)\World of Warcraft" -wowProduct wow_classic_beta` (or
  `wowf` once installed) serves the same CSV and file endpoints, with hotfixes applied, and
  renders reference item tooltips at `/dbc/tooltip/item/{id}` that the stat formula can be
  checked against. The fetcher therefore takes a configurable base URL; one implementation
  serves both sources. Its spell-description parser is unlicensed: rewrite, never copy.
- Icon id: `Item.IconFileDataID`. Map chain: AreaID → `UiMapAssignment` → `UiMap` →
  `UiMapXMapArt` → `UiMapArt` → `UiMapArtTile` (base, Row/Col) plus `WorldMapOverlay` /
  `WorldMapOverlayTile` (OffsetX/Y, composited over the base), cropped to the style's
  1002×668 layer.

### 4.2 Architecture
```
tools/clientdata/                      Python 3.11+, Pillow; build-time only
  fetch.py      --product wowf [--build B] --cache DIR     builds list → CSVs → icon BLPs → map tiles
  convert.py    --cache DIR --out DIR                       BLP→PNG (icons 56×56 RGBA, border cropped), maps stitched+composited → JPEG q85
  requirements.txt, README.md, tests/ (pytest: tiny synthetic BLP/tiles fixtures)
importer/  (Java)
  clientdata/ClientDataOptions, CsvTable, ItemSparseLoader, ItemStatsCalculator,
             TooltipBuilder, SpellDescriptionFormatter, ItemSetLoader, IconLoader, ZoneMapLoader,
             ClientDataStage (orchestrates), plus DropRateProvenance in drops/
  sources: tools/clientdata output dir (`--client-data-dir`), or skip (`--skip-client-data`)
app/ (Java)
  minimal consumers in 2A: ItemRepository gains quality/stats/tooltip/icon; ZoneRepository gains map; UI shows quality colour on item names, the icon in the item header, the tooltip lines as a plain list, and the map image on zone pages (pins arrive in 2B).
```
The Java importer never touches the network for client data; it reads the converted output
directory. `fetch.py` pins the build in `tools/clientdata/build.lock` (product, build, date,
file count, sha256 of each CSV) and the importer records the client build in `meta`.

### 4.3 Data model additions (SQLite)
```sql
item ADD COLUMNS: quality INTEGER, inventory_type INTEGER, bonding INTEGER, sell_price INTEGER,
                  buy_price INTEGER, max_stack INTEGER, icon_id INTEGER, item_set_id INTEGER,
                  description TEXT, allowable_class INTEGER, allowable_race INTEGER,
                  required_skill INTEGER, required_skill_rank INTEGER, has_client_data INTEGER NOT NULL DEFAULT 0
item_stat(item_id, ordinal, stat_id, value)                  -- computed values (e.g. +8 Stamina), resistances included
item_damage(item_id, min, max, school, delay_ms, dps REAL)   -- one row per item with damage
item_armor(item_id, armor, block)
item_effect(item_id, ordinal, trigger INTEGER, spell_id, text)   -- trigger 0 Use, 1 Equip, 2 Chance on hit; text already formatted
item_set(id, name)
item_set_item(set_id, item_id)
item_set_bonus(set_id, threshold, spell_id, text)
item_tooltip_line(item_id, ordinal, kind TEXT, text TEXT, colour TEXT)   -- the rendered tooltip, in game order; kinds: name, binding, unique, begins_quest, slot, damage, speed, dps, armor, block, stat, resist, durability, req_class, req_race, req_level, req_skill, req_rep, use, equip, proc, set_name, set_item, set_bonus, charges, duration, flavour, item_level, sell_price
icon(id INTEGER PRIMARY KEY, png BLOB)                       -- 56×56 RGBA PNG; id 0 is the question-mark fallback
zone_map(zone_id INTEGER PRIMARY KEY, ui_map_id, width, height, jpeg BLOB)   -- 1002×668 fully revealed
drop_rate_source(npc_id, item_id, source TEXT, pct REAL, PRIMARY KEY(npc_id, item_id, source))
item_npc_drop ADD COLUMNS: disagrees INTEGER NOT NULL DEFAULT 0, source_count INTEGER NOT NULL DEFAULT 0
meta ADD KEYS: client_product, client_build, client_fetched_at, client_item_count, icon_count, zone_map_count
```
`item_tooltip_line` is the contract with the UI: 2B's card renders lines, it does not recompute.
The structured tables exist for filters and future features.

### 4.4 Stat, damage and armour computation
- Input: `ItemSparse.StatModifier_bonusStat_n` (stat id) and `StatPercentEditor_n` (budget share
  in ten-thousandths), `ItemLevel`, `OverallQualityID`, `InventoryType`, `ItemDelay`,
  `DmgVariance`, class/subclass.
- Budget: `RandPropPoints[ItemLevel]` column chosen by quality (Epic/Superior/Good) and slot
  group (per the wowdev wiki's slot → column mapping); `value = round(budget × share / 10000)`.
- Damage: `ItemDamage<Kind>[ItemLevel].Quality_<q>` gives DPS; `avg = dps × delay/1000`;
  `min = floor(avg × (1 − variance/2))`, `max = floor(avg × (1 + variance/2))`; kind from
  subclass and inventory type (one-hand, two-hand, ranged, thrown, wand, ammo; caster variants
  by flag).
- Armour: `ItemArmorTotal[ItemLevel]` by material/quality via `ItemArmorQuality` and
  `ArmorLocation`; shields from `ItemArmorShield`.
- **Validation gate:** `importer/src/test/resources/fixtures/clientdata/known-items.csv` with
  ~25 items spanning every slot group, both weapon hands, ranged, shield, cloth/leather/mail/
  plate, each quality, a set piece, a proc weapon and a Use item. Columns: item id, expected
  tooltip lines (one per column, in order). The owner or implementer fills it by reading values
  from the beta client or Wowhead's Forever item pages by hand. `KnownItemsTest` fails on any
  mismatch. If the formula cannot be made to match for a category, that category's structured
  values are written as NULL and the tooltip omits those lines; the import report counts
  `clientdata.unverified.<category>`.

### 4.5 Spell description formatter
Own implementation. Supported: `$sN`, `$oN`, `$d`/`$D`, `$aN`, `$xN`, `$tN`, `$/N;sN`,
`${expr}` with `+ - * /`, `$lsingular:plural;`, `$<spellId>sN` cross-references, `$g…:…;`
gender (first form), `|cff…|r` colour codes stripped, `\n` line breaks kept. Unknown
placeholder → raw text kept and `clientdata.unformatted` counted with the spell id. Tests:
a table of 30 real descriptions from the fixture spells with expected output.

### 4.6 Provenance
- `DropRateLoader` records every source's value in `drop_rate_source`; the winner (same
  precedence as v0.1) still fills `item_npc_drop.drop_pct/pct_source`; `disagrees = 1` when any
  two sources differ by more than 2 points; `source_count` = number of sources with a value.
- Import report gains `== Conflicts ==`: the 50 largest disagreements (npc, item, each
  source's value) and counts per source pair.

### 4.7 Pipeline behaviour and CLI
- `python tools/clientdata/fetch.py --product wowf --cache ~/.cache/wowforever-clientdata`:
  resolves the newest build (or `--build`), fetches CSVs and files not already cached, ≤ 4
  requests/second, `User-Agent: wow-forever-browser-pipeline (+repo URL)`, writes
  `build.lock`. Idempotent; re-running fetches nothing when the cache is complete.
- `python tools/clientdata/convert.py --cache … --out ~/.cache/wowforever-clientdata/out/<build>`:
  icons → `icons/<fdid>.png`, maps → `maps/<uiMapId>.jpg` plus `maps.json` (uiMapId, areaIds,
  width, height, tile counts), copies CSVs. Deterministic output (same input → same bytes).
- `java -jar importer.jar --out … --client-data-dir <out dir>` (or `--skip-client-data`)
  runs `ClientDataStage` after the v0.1 stages: joins items by id (Questie item ids are the
  same ids as `Item.ID`), writes the tables in §4.3, embeds icons used by at least one item
  plus the fallback, embeds a map for every Questie zone whose area id resolves. Counters:
  `clientdata.items.matched/unmatched`, `clientdata.icons`, `clientdata.maps`, plus the
  unverified/unformatted counters above. `--fail-on-unsupported` also fails when
  `clientdata.items.matched` is below 90 % of items with a `name`.
- Zone id mapping: Questie zone ids are AreaTable ids; `UiMapAssignment.AreaID` resolves them.
  Sub-zones without their own map inherit the parent's map (`zone_map` row per Questie zone id
  that resolves, directly or via parent) with the same image.

### 4.8 Tests (2A)
- Python: pytest for BLP decode on a 4×4 synthetic DXT1/DXT5 BLP, tile stitching + overlay
  compositing on a 2×2 synthetic set, idempotent fetch with a local HTTP stub, lock file.
- Java unit: `CsvTable` (quoting, BOM, large files), stat/damage/armour calculators with
  worked examples, `SpellDescriptionFormatter` table, `TooltipBuilder` order, `ZoneMapLoader`
  chain on a fixture subset, provenance table and `disagrees`.
- Java integration: full importer on the v0.1 fixtures plus a small client-data fixture
  (≈ 30 items' CSV rows, 5 icons, 1 synthetic map) → every §4.3 table populated; `--skip-client-data`
  still produces a valid DB.
- `KnownItemsTest` (§4.4 gate) runs against the real converted data when present (cache-gated
  like the v0.1 real-source tests) and against the shipped DB in `ShippedDataTest`.
- Shipped-data: ≥ 90 % items with client data, icon for every such item, map for ≥ 45 zones,
  `drop_rate_source` rows ≥ `item_npc_drop` rows with a rate, conflicts counted.

## 5. Sub-project 2B — UI overhaul

Implements the UI review. The review document is the detailed reference; this section fixes
scope and the few places where the design deviates.

### 5.1 In scope
- **P1 (all):** dark theme default with the token set; master list + detail layout with the
  icon rail; single-click links, hover affordance, mouse buttons 4/5, SVG icons; search
  ranking, merged zone/instance hits, collapsed duplicates, worded subtitles; item page as one
  scrolling sources page with rate bars, "Rate unknown" group, origin chips only when not
  Questie-only, boss-loot rows named by instance › list; NPC/boss loot grouped by category
  with rate-first sorting and hidden empty columns, rank chips, "Copy /way"; window state
  persistence; empty/unknown/not-found states.
- **P2:** type-ahead dropdown with kind chips, recent searches and paste parsing (item links,
  Wowhead URLs, ids); home launch pad (Recent, Pinned, Dungeons & Raids by level band); instance
  page (numbered encounters, loot by slot type); zone page (level range, bosses and rares first,
  map at top); category rail with filterable list pages (items by class/subclass/level/
  slot/quality, NPCs by zone/rank/level, quests by zone/level); keyboard map and Help →
  Keyboard shortcuts; tooltip card, quality colours everywhere, icons in rows and results, map
  with pins and clustering; provenance marker and source filter; compact mode (F10, 420×720,
  always-on-top option, separate bounds); accessibility (contrast variants, text size
  90–150 %, focus ring, accessible names).

### 5.2 Deviations from the review
- Pinned items persist in `Preferences` as a list of entity refs (no new DB table).
- The "merge a raid zone with its instance" rule: a search hit for a zone that is the
  `zone_id` of exactly one instance opens the instance page; the zone page remains reachable
  from the instance header. Data stays two entities.
- Icons in rows are drawn from the 56 px PNG with bicubic downscaling and cached per size; no
  36 px variant is shipped.
- P3 items are out of scope.

### 5.3 Architecture notes
- `ui/theme/` (FlatLaf properties for dark and light, token constants, quality colours with
  text variants), `ui/widgets/` (RateBar, Chip, IconCache, LinkLabel, SearchDropdown,
  MapView), `ui/pages/` replacing `ui/detail/` (ItemPage, NpcPage, ObjectPage, QuestPage,
  ZonePage, InstancePage, ListPage, HomePage, CompactShell), `ui/MasterList`, `ui/Rail`.
- `NavigationHistory.replaceCurrent(target)` for previews; `Preferences`-backed `UiState`.
- Every page is built from records the repositories already return plus the new 2A records
  (`ItemCard` with tooltip lines, `IconBytes`, `ZoneMap`, `DropSource.disagrees/sources`).
- Headless tests for every widget and page; `AppController` tests for ranking/merging/
  preview; a visual check by the controller with scripted screenshots as in v0.1.

## 6. Order and sizing
1. **Plan 2A** (pipeline + schema + minimal UI consumers): roughly 10–12 tasks.
2. **Plan 2B** (UI overhaul): roughly 12–16 tasks.
Each gets its own plan file and runs through the same subagent-driven process as v0.1, on a
branch from `main`, merged when its final review is clean.

## 7. Left to the builders
- Exact RandPropPoints/ItemDamage column selection per slot and quality (derive from wowdev
  wiki and validate with the fixture).
- Icon border crop amount; JPEG quality; map crop constants.
- Python project layout; whether to vendor a tiny HTTP throttle or use `time.sleep`.
- Swing specifics beyond the review's notes.

## 8. Open questions for the owner (answer when convenient; defaults in brackets)
1. Does the Forever client have more than one loot difficulty? [Assume no; the UI shows a
   difficulty control only if the data has > 1 value.]
2. Which ~25 items should the known-items fixture use? [Builder picks a spread; owner can
   replace with favourites.]
