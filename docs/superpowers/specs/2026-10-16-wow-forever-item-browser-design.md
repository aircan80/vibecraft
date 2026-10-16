# WoW Forever Item & Drop Browser — Design

Date: 2026-10-16
Status: approved for planning (autonomous build; user delegated requirements gathering)

## 1. Intent and why

A desktop application that lets a player of **World of Warcraft: Forever** (Blizzard's
Classic+ branch, announced at BlizzCon 2026, launching 4 Nov 2026) browse every item in the
game and find out **where it comes from**: which NPCs drop it (with drop percentages where
known), which objects contain it (chests, herbs, veins), which containers hold it, which
vendors sell it, and which quests reward it. The inverse views matter just as much: pick an
NPC, a boss, a dungeon or a zone and see what it drops.

The user asked for exactly this: "browse all the drops/items in WoW-forever", sourced from
addon databases such as Questie and AtlasLoot, stored in a local SQLite database shipped with
the app, written in Java, running on Windows, macOS and Linux, with solid tests.

Why offline and local: the addon databases are the most complete community data for Forever,
but reading them in-game is clumsy and reading them out of game needs an app. Scraping
Wowhead-style sites is out of scope and against their terms.

## 2. Goals, non-goals, anti-goals

### Goals
1. Browse and search all **items**, **NPCs**, **game objects**, **quests**, **zones** and
   **dungeons/raids (with bosses)** known for Forever.
2. For any item show every source: NPC drops (with % where known), object drops, container
   drops, vendors, quest rewards, related quests, "starts quest".
3. For any NPC/object/boss/instance show what it drops or contains, where it is
   (zone + coordinates as text), and quest links. A zone shows the NPCs, objects, quests and
   dungeons in it (lists, not an aggregated item view).
4. Everything is a link: an NPC in an item's drop list opens that NPC; a zone opens its NPC
   list; a boss opens its loot. Back/forward history.
5. Fast: name search returns well under a second on ordinary hardware; startup in a few seconds.
6. One runnable artifact: `java -jar wow-forever-browser.jar` works on Windows, macOS and
   Linux with Java 21+. Native installers (jpackage) are a stretch goal.
7. Reproducible data build: one command regenerates the SQLite database from pinned upstream
   commits and prints an import report (counts, skipped constructs, warnings).
8. Solid automated tests at every layer, runnable with one command and in CI on all three OSes.

### Non-goals (v1)
- Item stats, tooltips, icons, quality colours. The chosen sources do not carry them. The
  schema leaves room (nullable columns / separate table) for a later source.
- Crafting recipes, reputation vendors by standing, PvP rank gear. AtlasLoot has modules for
  these (`_Crafting`, `_Factions`, `_PvP`, `_Collections`); they are explicitly deferred.
- Localisation. English only.
- Live data updates from the network at runtime. The app never touches the network.
- Map rendering. Coordinates are shown as numbers.
- Character/account integration or "collected" tracking.

### Anti-goals (would make it a failure even if it worked)
- Electron or any web-view wrapper. It must be a plain Java desktop app.
- Scraping Wowhead or any website at build or run time. Only published addon repositories.
- Requiring the user to install Maven, Gradle, or anything beyond a Java 21 runtime.
- Requiring Internet access to run the app.
- A database that silently loses data because a correction construct was not understood.
  Unsupported constructs must be counted and reported by the importer.

## 3. Data sources (researched 2026-10-16)

### Primary: QuestieDB — https://github.com/Questie/QuestieDB
Pinned commit for v1: `49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4` (2026-10-15).
The Questie addon moved its database into this repository. It has a dedicated **Forever**
flavour. Files the importer reads (paths relative to the repo root):

| Path | Content |
| --- | --- |
| `data/Forever/foreverItemDB.lua` | `QuestieDB.itemData = [[return { [id] = {name, npcDrops, objectDrops, itemDrops, startQuest, questRewards, flags, foodType, itemLevel, requiredLevel, ammoType, class, subClass, vendors, relatedQuests, teachesSpell}, ... }]]` |
| `data/Forever/foreverNpcDB.lua` | `QuestieDB.npcData` rows: `{name, minLevelHealth, maxLevelHealth, minLevel, maxLevel, rank, spawns{[zoneId]={{x,y[,phase]},...}}, waypoints, zoneID, questStarts, questEnds, factionID, friendlyToFaction, subName, npcFlags}` |
| `data/Forever/foreverObjectDB.lua` | `QuestieDB.objectData` rows: `{name, questStarts, questEnds, spawns, zoneID, factionID, waypoints}` |
| `data/Forever/foreverQuestDB.lua` | `QuestieDB.questData` rows: 36 positional fields, see `src/meta/questMeta.lua` |
| `src/meta/{item,npc,object,quest}Meta.lua` | Authoritative field index to name/type/shape tables. The importer parses these rather than hard-coding indices. |
| `src/corrections/Forever/legacy/*.lua` | Inherited Classic static corrections: `classicItemFixes`, `classicNPCFixes`, `classicObjectFixes`, `classicQuestFixes`, `classicQuestReputationFixes`, `itemStartFixes` |
| `src/corrections/Forever/generated/foreverBase{Item,Npc,Object,Quest}.lua` | Generated Forever delta-base (new Forever entities, `_add`/`_remove` operations) |
| `src/corrections/Forever/traces/forever{Item,Npc,Object,Quest}Traces.lua` | Player-trace-derived corrections |
| `src/corrections/Forever/forever{Item,NPC,Object,Quest}Fixes.lua` | Hand-authored Forever corrections. Only `Load()` is used; `LoadDynamic()` is per-character and ignored |
| `src/corrections/enum/*.lua` | Symbol tables the corrections reference: `zoneIDs`, `factionIDs`, `itemClasses`, `sortKeys`, `questFlags`, `specialFlags`, `professionKeys`, `specializationKeys`, `rankNames`, `iconTypes`, `phases`, `waypointPresets`, `raceMaskById`, `byExpansion.{Classic,Forever}.{raceKeys,classKeys,npcFlags}` |
| `support/Forever/DropTables/classicItemDrops.lua` | `wowheadData = [[return { [itemId] = { [npcId] = percent } }]]` |
| `support/Forever/DropTables/itemDropCorrections.lua` | Manual drop-rate overrides and `WOWHEAD` reference markers |
| `support/Forever/Zones/dungeons.lua` | `[areaId] = {name, altAreaIds, parentZoneId, {{zone,x,y},...}}`: dungeon names, parents, entrances |
| `support/Forever/Zones/subZoneToParentZone.lua` | sub-zone to parent zone map |
| `src/corrections/enum/zones.lua` | `zoneIDs.NAME = id`, the only zone-name source. Names are derived by title-casing the symbol with a small override map for apostrophes and known oddities (builder's call on the exact list) |

Static correction order (from `docs/forever.md`): **raw base, then legacy, then generated
delta-base, then traces, then authored Forever fixes**. Only static `Load()` corrections apply.

Correction semantics the importer must implement (from `docs/api.md`):
- `[entityId] = { [key] = value }` replaces the field. A **new** entity id creates the entity.
- `[key] = {}` deletes the field (reads as nil).
- `[key_add] = operand` / `[key_remove] = operand` for table fields. Shapes supported in v1:
  `idarray` (append absent ids / remove exact ids), `questgivers` (`{[1]=npcIds,[2]=objectIds,[3]=itemIds}`, per-group add/remove), `spawnlist` (`{[zoneId]={{x,y[,phase]}}}`, per-zone add/remove of coordinate tuples), `pairs`, `stringarray`.
  Any other shape, or any value the evaluator cannot reduce to a literal, is **counted as
  skipped and reported with file and line**; it never aborts the import.
- A replacement and an operation on the same field in one row is an upstream error; the
  importer applies the replacement, then the operation, and logs a warning.
- **Acceptance bar for the shipped database:** zero skipped corrections on the fields the UI
  shows (entity names and sub-names, item `npcDrops`/`objectDrops`/`itemDrops`/`vendors`/
  `questRewards`/`relatedQuests`/`startQuest`/`class`/`subClass`/levels, NPC and object
  levels/rank/`zoneID`/`spawns`/`questStarts`/`questEnds`/`npcFlags`, quest `name`/
  `startedBy`/`finishedBy`/levels/`zoneOrSort`/`objectivesText`/flags). Skips on other fields
  (for example quest `objectives`, `preQuestGroup`, `extraObjectives`) are acceptable but
  must appear in the report. `--fail-on-unsupported` enforces the bar for the UI fields only.

Questie/QuestieDB publish no license file. The app's NOTICE must credit Questie and link the
repository; the user should confirm they are comfortable redistributing this data (see §9).

### Secondary: AtlasLootClassic — https://github.com/Hoizame/AtlasLootClassic (GPL-2.0)
Pinned commit: `8e99341e4e779328460bf7684c0d5b22ce50ddf1` (2024-05-13). Vanilla data only
(`data.lua`; not `data-tbc.lua` or `data-wrath.lua`).

| Path | Content |
| --- | --- |
| `AtlasLootClassic_DungeonsAndRaids/data.lua` | `data["Ragefire"] = { MapID, InstanceID, ContentType, LevelRange = GetForVersion({10,13,18},{8,13,16}), items = { { name = AL["Taragaman the Hungerer"], npcID = 11520, Level = 16, specialType = "rare"?, ExtraList = true?, ObjectID = ?, [NORMAL_DIFF] = { {slot, itemId}, {slot, "icon", nil, AL["Header"], nil}, ... } }, ... } }`. 33 instances including `WorldBosses` |
| `AtlasLootClassic_DungeonsAndRaids/droprate.lua` | `DropRateData = { [npcId] = { [itemId] = percent } }` |
| `AtlasLootClassic_DungeonsAndRaids/droprate_override.lua` | same shape, overrides |

Evaluation rules for AtlasLoot's Lua: `AL["x"]` evaluates to `"x"`; `format(AL["Tier %s Sets"], "1")`
does simple `%s` substitution; `GetForVersion(a, b, ...)` returns the first argument
(Classic); the `*_DIFF`, `*_CONTENT`, `*_ITTYPE` locals resolve from a fixed symbol map
(`NORMAL_DIFF` to `"NORMAL"`, `RAID20_DIFF` to `"RAID20"`, `RAID40_DIFF` to `"RAID40"`,
`ALLIANCE_DIFF`/`HORDE_DIFF` to `"ALLIANCE"`/`"HORDE"`, `DUNGEON_CONTENT` to `"DUNGEON"`, and
so on). Loot rows whose second element is a string (section headers / icons) are skipped.
Only top-level `data["..."] = {...}` statements are instances; the `KEYS`, `T1_SET` style
locals are not. Boss entries with `ExtraList = true` (Trash Mobs, Keys) are kept as non-boss
"extra" lists within the instance.

AtlasLoot boss loot **is an item source**: every (boss `npcID`, item) pair is merged into
`item_npc_drop` with `origin` = `questie`, `atlasloot` or `both`, so an item's "Dropped by" list
shows raid bosses even when Questie's `npcDrops` lacks the pair. `boss_loot` is kept as well
for the instance/boss view (slot order, difficulty, extra lists).

Drop-percentage precedence for an (npc, item) pair: `itemDropCorrections` explicit value, then
`droprate_override`, then Questie Wowhead data, then AtlasLoot `droprate`, else unknown (NULL).
The chosen source is stored alongside the value.

## 4. Constraints and platform choices (decided)

- **Language/runtime:** Java 21 (LTS). No preview features.
- **Build:** Maven 3.9 with the Maven Wrapper (`mvnw`, `mvnw.cmd`) committed, so only a JDK is
  required. Multi-module reactor.
- **UI toolkit:** Swing with FlatLaf (`com.formdev:flatlaf`) for a modern look and dark/light
  themes. Chosen over JavaFX because Swing ships inside every JDK, which keeps one fat JAR
  working on all three OSes without per-platform native artifacts. Not Electron, not a web view.
- **Database:** SQLite via `org.xerial:sqlite-jdbc` (bundles natives for Windows, macOS and
  Linux on x64 and arm64). FTS5 for name search. The database file is a **build artifact
  committed to the repo** at `app/src/main/resources/data/wowforever.sqlite` so the app builds
  offline; the importer regenerates it.
- **Runtime DB handling:** SQLite cannot open a file inside a JAR. On start the app copies the
  resource to the per-user data dir (`%LOCALAPPDATA%\WoWForeverBrowser` on Windows,
  `~/Library/Application Support/WoWForeverBrowser` on macOS, `$XDG_DATA_HOME/wowforever-browser`
  or `~/.local/share/wowforever-browser` on Linux) when missing or when the embedded
  `meta.build_id` differs from the extracted copy's. Opened read-only.
- **Logging:** SLF4J with the simple binding, or `java.util.logging`. Builder's call; keep it light.
- **Licensing:** the project is licensed **GPL-2.0-or-later** so that AtlasLoot-derived data
  (GPL-2.0) can be redistributed inside it. `NOTICE.md` credits QuestieDB (no license file
  upstream) and AtlasLootClassic with links and pinned commits.
- **No network at runtime.** The importer is the only component that downloads, and only the
  files listed above, from `raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>`, into a
  cache directory. It also accepts local checkout directories instead.
- **Tests must run headless** (CI). Swing panels and models are tested; top-level frames are
  not instantiated in tests.

## 5. Architecture

Maven reactor `wow-forever-browser` with three modules:

```
wow-forever-browser/
  pom.xml                      parent: Java 21, dependency management, plugin versions
  mvnw, mvnw.cmd, .mvn/        Maven wrapper
  lua-table/                   Lua table-literal parser + restricted evaluator (no deps)
  importer/                    CLI: upstream files -> SQLite (depends on lua-table, sqlite-jdbc)
  app/                         Swing application (depends on sqlite-jdbc, flatlaf); contains the shipped DB
  docs/superpowers/specs/      this document; plans go in docs/superpowers/plans/
  .github/workflows/ci.yml     mvnw verify on ubuntu/windows/macos
  LICENSE, NOTICE.md, README.md
```

### 5.1 `lua-table`: parser and evaluator
- Hand-written lexer and recursive-descent parser for the Lua **expression** subset found in
  these files: `nil`, `true`, `false`, numbers (integer and float, negative), strings (`'...'`,
  `"..."`, `[[...]]`, with Lua escapes `\'`, `\"`, `\\`, `\n`, `\ddd`), table constructors with
  positional entries, `[expr] = expr` and `name = expr` entries, trailing commas and `;`,
  comments (`--` and `--[[ ]]`), identifiers and dotted or indexed names (`itemKeys.npcDrops`,
  `ZoneDB.zoneIDs.DUSKWOOD`, `AL["x"]`), function calls `f(args)`, binary `+`/`-`/`*`, unary `-`.
- The evaluator takes a `Scope` (name to value, nested tables allowed) and a `CallHandler`
  (function name plus evaluated args to a value, or "unsupported"). Values: `LuaNil`,
  `LuaNumber` (long or double), `LuaString`, `LuaBoolean`, `LuaTable` (ordered: array part
  plus hash part, preserving `nil` holes in positional lists because Questie rows depend on
  position).
- A `LuaFile` utility extracts a target without evaluating the rest of the file: "the
  `return {...}` inside the long string assigned to `QuestieDB.itemData`", "the table returned
  by `function X:Load()`", or "every top-level statement `data["..."] = {...}`". Local alias
  statements inside `Load()` (`local itemKeys = QuestieDB.itemKeys`) are recorded as scope
  aliases.
- Unsupported constructs produce an `UnsupportedConstruct(file, line, snippet)` that callers
  collect; they never throw past the entry they belong to.

### 5.2 `importer`: build the SQLite
Pipeline (each stage a class, each unit-testable on fixture snippets):
1. `SourceFetcher`: resolves the file manifest for both repos from `sources.properties`
   (owner, repo, commit, paths), downloads missing files into the cache, or uses
   `--questiedb-dir` / `--atlasloot-dir`.
2. `MetaLoader`: parses `*Meta.lua` into `EntitySchema` (field index to name, type, shape).
3. `RawEntityLoader`: parses the four `data/Forever/*DB.lua` into `Map<Long, EntityRow>`
   (row = positional `LuaValue[]` keyed by schema).
4. `EnumLoader`: parses `src/corrections/enum/*.lua` into the correction `Scope`
   (`zoneIDs`, `factionIDs`, `itemClasses`, `sortKeys`, `specialFlags`, `questFlags`,
   `professionKeys`, `specializationKeys`, `rankNames`, `iconTypes`, `phases`,
   `waypointPresets`, Forever `raceKeys`/`classKeys`/`npcFlags`). Aliases used by the
   correction files (`raceIDs`, `classIDs`, `profKeys`, `specKeys`, `rankKeys`, `factionIDs`,
   `zoneIDs`, `npcFlags`, `itemClasses`, `questKeys`/`npcKeys`/`itemKeys`/`objectKeys`
   including the `_add`/`_remove` virtual keys) are pre-bound.
5. `CorrectionApplier`: applies the correction files in the documented order with the
   semantics in §3, collecting an `ImportReport` of applied, created and skipped counts plus
   unsupported constructs.
6. `DropRateLoader`: Questie Wowhead table plus corrections; AtlasLoot droprate plus override.
7. `ZoneLoader`: `zoneIDs` names, `dungeons.lua`, `subZoneToParentZone.lua`.
8. `AtlasLootLoader`: instances, bosses, loot rows.
9. `SqliteWriter`: creates the schema (§6), bulk-inserts in a single transaction with
   prepared statements, builds the FTS index, writes `meta`, runs `VACUUM` and `ANALYZE`.
10. `ImportReport` printed to stdout and written next to the DB as `import-report.txt`.

CLI: `java -jar importer.jar --out app/src/main/resources/data/wowforever.sqlite [--cache DIR]
[--questiedb-dir DIR] [--atlasloot-dir DIR] [--fail-on-unsupported]`. Also wired as a Maven
profile `-Pimport-data` on the importer module (exec plugin) for convenience.

### 5.3 `app`: Swing browser
Layers (packages):
- `db`: `Database` (extracts the resource, opens a read-only connection), repositories with
  plain JDBC SQL: `ItemRepository`, `NpcRepository`, `ObjectRepository`, `QuestRepository`,
  `ZoneRepository`, `InstanceRepository`, `SearchRepository` (FTS). They return immutable records.
- `model`: records such as `ItemSummary`, `ItemDetail`, `DropSource`, `NpcSummary`,
  `NpcDetail`, `Spawn`, `Instance`, `Boss`, `LootRow`, `Zone`, `QuestSummary`, `SearchHit`,
  `EntityRef(kind, id)`.
- `nav`: `NavigationHistory` (back/forward stack of `EntityRef` or list-view targets) and the
  `Navigator` interface the UI implements.
- `ui`: `MainFrame`, `SearchBar` (debounced type-ahead), `BrowseTree` (left), `ResultsTable`
  (centre; sortable `JTable` with per-kind column models), detail panels (right):
  `ItemDetailPanel`, `NpcDetailPanel`, `ObjectDetailPanel`, `QuestDetailPanel`,
  `ZoneDetailPanel`, `InstanceDetailPanel`, `BossLootPanel`. Linked cells render in link
  colour; double-click or Enter navigates. `Ctrl+F` focuses search; `Alt+Left`/`Alt+Right`
  move through history. FlatLaf light by default with a View menu dark-theme toggle persisted
  in `java.util.prefs.Preferences`.
- `Main`: sets the look-and-feel, opens the DB on a background thread with a small splash
  label, shows `MainFrame`. All DB access happens off the EDT via `SwingWorker`.

Layout sketch:
```
+----------------------------------------------------------------------+
| [<] [>]   Search: [ thunderfury_______________ ] (All v)   View v     |
+------------+-------------------------------+-------------------------+
| Browse     | Results (1,204)               | Thunderfury, Blessed... |
| > Items    | Name          iLvl Req Class  | Item 19019 . Sword . 80 |
|   > Weapon | Thunderfury...  80   60 Sword | [Dropped by][Contained].|
|   > Armor  | ...                           | NPC      Lvl Zone    %  |
| > NPCs     |                               | Ragnaros  63 MC      -  |
| > Objects  |                               |                         |
| > Zones    |                               |                         |
| > Dungeons |                               |                         |
| > Quests   |                               |                         |
+------------+-------------------------------+-------------------------+
```

## 6. Database schema (SQLite)

```sql
meta(key TEXT PRIMARY KEY, value TEXT)
  -- build_id, built_at, questiedb_commit, atlasloot_commit, importer_version
zone(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER NULL, kind TEXT)
  -- kind: zone | dungeon | raid | city | continent | subzone
item(id PK, name, item_level, required_level, class_id, subclass_id, flags, food_type,
     ammo_type, start_quest_id, teaches_spell_id)
item_class(id PK, name)                       -- static cmangos names
item_subclass(class_id, id, name, PRIMARY KEY(class_id, id))
npc(id PK, name, sub_name, min_level, max_level, rank, zone_id, faction_id, friendly_to, npc_flags)
game_object(id PK, name, zone_id, faction_id)
quest(id PK, name, required_level, quest_level, zone_or_sort, required_races, required_classes,
      objectives_text, quest_flags, special_flags)
quest_giver(quest_id, kind TEXT, entity_id, role TEXT)   -- kind npc|object|item, role start|end
item_npc_drop(item_id, npc_id, drop_pct REAL NULL, pct_source TEXT NULL, origin TEXT,
              PRIMARY KEY(item_id, npc_id))   -- origin: questie | atlasloot | both
item_object_drop(item_id, object_id, PRIMARY KEY(item_id, object_id))
item_container_drop(item_id, container_item_id, PRIMARY KEY(item_id, container_item_id))
item_vendor(item_id, npc_id, PRIMARY KEY(item_id, npc_id))
quest_reward_item(quest_id, item_id, PRIMARY KEY(quest_id, item_id))
item_related_quest(item_id, quest_id, PRIMARY KEY(item_id, quest_id))
npc_spawn(npc_id, zone_id, x REAL, y REAL)
object_spawn(object_id, zone_id, x REAL, y REAL)
instance(id PK, key TEXT, name TEXT, map_id, instance_id, content_type TEXT, level_min, level_max, zone_id NULL)
boss(id PK, instance_id, name, npc_id NULL, object_id NULL, level, special_type TEXT NULL,
     is_extra_list INTEGER, sort_order)
boss_loot(boss_id, item_id, difficulty TEXT, slot INTEGER, drop_pct REAL NULL,
          PRIMARY KEY(boss_id, item_id, difficulty))
search_index   -- FTS5(kind, id UNINDEXED, name) over item, npc, game_object, quest, zone, instance names
```
Plus indexes on every foreign-key column used in reverse lookups (`item_npc_drop(npc_id)`,
`npc_spawn(zone_id)`, `npc(zone_id)`, and so on). During import: `PRAGMA journal_mode=OFF`,
`synchronous=OFF`, `page_size=4096`. References to unknown ids (for example a drop pointing at
an NPC id with no row) are kept so nothing is silently dropped; the UI shows `Unknown NPC #id`.

## 7. Testing strategy

- **Unit (lua-table):** lexer tokens including escapes and long strings; parser on every
  construct in §5.1; evaluator with scopes, nil holes and calls; unsupported constructs
  reported with line numbers; a round-trip test over generated tables.
- **Unit (importer):** each loader on small fixture `.lua` files copied verbatim from upstream
  (a few entities each); `CorrectionApplier` tests for replace, create, delete-with-`{}`,
  `_add`/`_remove` on each supported shape, precedence across layers, and reporting of
  unsupported operations; drop-rate precedence; AtlasLoot header-row skipping and `GetForVersion`.
- **Integration (importer):** run the full pipeline on the fixture set into a temp SQLite and
  assert row counts and specific facts (item 14149 "Subterranean Cape" dropped by npc 11520;
  Taragaman's loot contains 14149; VanCleef 639 drops Cruel Barb 2874 at 56.07 %).
- **Unit (app):** repositories against the fixture SQLite produced by the importer fixtures
  (built once per test run); `NavigationHistory`; search ranking; table models. Detail panels
  constructed headless and populated from fixture records (no `JFrame`).
- **Shipped-data sanity (app, tagged `shipped-data`):** opens the real committed DB and checks
  minimum counts (items > 15,000; npcs > 8,000; instances = 33), that referential-integrity
  queries return zero orphans in the primary tables, and the facts above.
- **CI:** GitHub Actions matrix ubuntu/windows/macos running `./mvnw -B verify`, headless
  (`-Djava.awt.headless=true`).

## 8. Left to the builder
- Exact Swing component choices, column widths, icons, keyboard details beyond those listed.
- Zone-name override list and how continents, cities and sub-zones are classified.
- Whether to store `waypoints`, `phase` and structured `objectives` beyond `objectives_text`
  (not required for v1; may be skipped with a report entry).
- The static item class and subclass name table (use cmangos/wowdev names).
- Logging framework; test fixture size; exact FTS tokenizer settings.
- jpackage native installers: only after the fat JAR and tests are complete.

## 9. Calls made on the user's behalf (please check)
1. **Target data is the Forever flavour** of QuestieDB, not Classic Era. If the user actually
   plays the unofficial "ForeverWoW" 3.3.5 private server this is wrong; everything else still
   holds and the importer could be pointed at the `Wotlk` flavour.
2. **Swing + FlatLaf** instead of JavaFX, for packaging simplicity.
3. **GPL-2.0-or-later** project license, because AtlasLoot data is GPL-2.0. QuestieDB has no
   license file; its data is credited in `NOTICE.md`. The user should confirm they are happy
   with redistribution.
4. The generated SQLite is **committed to the repository** (tens of MB) so builds are offline
   and reproducible; regeneration is a separate command.
5. Dynamic (per-character) corrections and non-English locales are ignored.
