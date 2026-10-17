# Phase 3A — Quest data and client UI icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship quest objectives, quest relations (chains), provided/required items, quest flags and six client UI icon textures in the SQLite database so the phase 3B UI can show giver/turn-in maps, objective pins, chains and WoW-idiom rail icons.

**Architecture:** Extend the QuestieDB quest loader (lua-table → RawEntity) to read the remaining `questKeys` fields, write them through `EntityWriter` into new tables (schema v3), keep references to absent quests, and count data-quality problems in the import report. Fetch the icon textures through the existing client-data pipeline (`fetch.py --fdids`, `convert.py`) and embed them as PNG in `ui_asset`. Regenerate the shipped DB once.

**Tech Stack:** Java 21, Maven wrapper, sqlite-jdbc, JUnit 5/AssertJ; Python tool under tools/clientdata (build time only).

**Spec:** docs/superpowers/specs/2026-10-17-phase3-quest-companion-and-wow-look-design.md (§3 data, §5 icons, §8 acceptance quests).

## Global Constraints
- Branch `feature/phase3a-quest-data` from `main` (which carries phase 2A); the phase 2B branch is in flight in a worktree — do not touch `.worktrees/`. Full `./mvnw -B -q verify` before each commit; every existing test keeps passing.
- Schema changes are additive (new tables/columns only); `--skip-client-data` imports keep working; the app on `main` keeps opening the regenerated DB.
- Questie objectives have NO counts: never invent a count column.
- References to quests absent from the DB are kept in `quest_relation` and counted as `quest.relation.missing`; never dropped silently.
- Never modify the WoW installation; network only at build time (wago.tools) with the existing retry/UA rules.
- Commit after every task; trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus
1. A quest whose `preQuestGroup` and `preQuestSingle` both exist must produce both relation kinds (Task 2 test `bothPrerequisiteKindsAreKept`).
2. `killCreditObjective` groups must share one `credit_group` and keep the base creature as the representative row (Task 2 `killCreditGroupSharesOneGroup`).
3. `zoneOrSort < 0` must map to a sort label and never to a zone id (Task 3 `negativeZoneOrSortBecomesASortLabel`).
4. `triggerEnd` points in several zones must each land in `quest_objective_point` with their zone (Task 2 `explorePointsKeepTheirZone`).
5. A relation to a quest id not in the DB must survive the write and be counted (Task 3 `missingRelationTargetsAreKeptAndCounted`).

---

### Task 1: Schema v3 and the quest raw-field reader

**Files:**
- Modify: `importer/src/main/resources/dev/wowforever/importer/schema.sql` (add `quest_objective`, `quest_objective_point`, `quest_source_item`, `quest_relation`, `quest` columns `repeatable, required_skill_id, required_skill_value, required_min_rep_faction, required_min_rep_value, sort_label`, the four indexes from the spec, `ui_asset(name TEXT PRIMARY KEY, png BLOB NOT NULL)`), `importer/.../sqlite/SqliteWriter` (DDL version bump), `importer/.../questie/RawEntity` (+ accessors for nested tables), `importer/.../report/UiFields` (QUEST adds the new fields).
- Test: `SchemaTest.v3TablesExist`, `RawEntityTest.questNestedTables` on fixture quest 176.

**Interfaces:**
- Produces: `RawEntity.nested(String key, int... path) -> List<LuaValue>`; schema tables as named in the spec.

- [ ] Write failing tests; implement; run `./mvnw -B -q -pl importer -am test`; commit `feat(importer): schema v3 for quest objectives, relations, source items and ui assets`.

### Task 2: Quest objectives, points and source items

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/sqlite/QuestObjectiveWriter.java` (reads `objectives` [1..6], `triggerEnd`, `sourceItemId`, `requiredSourceItems`, `extraObjectives` text-only; writes `quest_objective`, `quest_objective_point`, `quest_source_item`).
- Modify: `EntityWriter` (calls it per quest), the fixture `foreverQuestDB.lua` (add 176 Wanted: Hogger with creature + item objectives, a quest with a killCredit group, one with triggerEnd in two zones, one with sourceItemId and requiredSourceItems).
- Test: `QuestObjectiveWriterTest` (`creatureAndItemObjectivesInOrder`, `killCreditGroupSharesOneGroup`, `explorePointsKeepTheirZone`, `providedAndRequiredItems`, `reputationObjectiveKeepsFactionAndValue`), `ImportPipelineTest` counts `table.quest_objective > 0`.

**Interfaces:**
- Produces: rows as the spec's tables; report counters `quest.objective.kinds.<kind>`, `quest.objective.unknownKind`.

- [ ] Tests red → green; commit `feat(importer): quest objectives, exploration points and source items`.

### Task 3: Quest relations, flags and sort labels

**Files:**
- Create: `importer/.../sqlite/QuestRelationWriter.java` (preQuestGroup→pre_group, preQuestSingle→pre_single, nextQuestInChain→next, childQuests→child, parentQuest→parent, exclusiveTo→exclusive, inGroupWith→group, breadcrumbForQuestId→breadcrumb_for, breadcrumbs→breadcrumb; dedupe; keep missing targets; count `quest.relation.missing`), `importer/.../questie/QuestSort.java` (QuestSort id → label table for Classic: Epic, Class names, professions, Darkmoon Faire, Seasonal, Battlegrounds, …; unknown → "Sort #id").
- Modify: `EntityWriter` (quest columns: repeatable = specialFlags & 1, requiredSkill, requiredMinRep, sort_label), fixture quests (a chain of three with a missing fourth, an exclusive pair, a breadcrumb, a negative zoneOrSort).
- Test: `QuestRelationWriterTest` (`bothPrerequisiteKindsAreKept`, `missingRelationTargetsAreKeptAndCounted`, `duplicatesCollapse`, `breadcrumbsBothWays`), `QuestSortTest.negativeZoneOrSortBecomesASortLabel`, `EntityWriterTest.questFlagsAndRequirements`.

- [ ] Tests red → green; commit `feat(importer): quest relations, repeatable flag, requirements and sort labels`.

### Task 4: Client UI icon textures

**Files:**
- Modify: `tools/clientdata/clientdata/icons.py`/`fetch.py` (`--ui-icons` fetching a fixed list of (name, fdid) pairs resolved from the client listfile names in the spec §5 — resolve names → fdids with the listfile endpoint or a committed `ui_icons.json`; verify each exists, log the fallback choice when the first choice is missing), `convert.py` (writes `ui_icons/<name>.png` at native size, plus a 24 px cropped variant `<name>@24.png` using the central 86 % crop for Interface/Icons art and native centring for GossipFrame art), `importer/.../clientdata/UiAssetLoader.java` (embeds both variants into `ui_asset` as `<name>` and `<name>@24`), `ClientDataStage` wiring, README + NOTICE.
- Test: `UiAssetLoaderTest` on a fixture with two PNGs; Python tests for the crop maths and the name→fdid resolution with a fake listfile.

**Interfaces:**
- Produces: `ui_asset` rows `rail.home`, `rail.items`, `rail.npcs`, `rail.quests`, `rail.zones`, `rail.instances` (+ `@24`), `report: clientdata.uiAssets = 6`.

- [ ] Tests red → green; commit `feat(clientdata): client UI icon textures for the rail`.

### Task 5: Regenerate the shipped database and shipped-data tests

**Files:**
- Modify: `app/src/main/resources/data/*` (regenerated with the real client cache: fetch the six icons, convert, import with `--fail-on-unsupported`), `tools/clientdata/build.lock`, `app/src/test/java/dev/wowforever/app/ShippedDataTest.java` (+ `importer` `KnownQuestsTest`): quest 176 has exactly one objective, the item objective Huge Gnoll Claw 1931 (Questie records no creature objective for Hogger), two object starts (Wanted Posters 68 and 156561) and the NPC end 240; 7787 has pre relations to 7785/7786; 6502 has ≥ 10 relation rows and a chain reachable to 6602 via `exclusive` or a shared ancestor (verify and record); a repeatable quest has `repeatable = 1`; `quest.relation.missing` is reported; `ui_asset` has the six icons (+@24). Record the sanity numbers in the report (objective rows, relation rows by kind, missing targets, sort labels).
- Docs: README "Rebuild the data" and NOTICE updated.

- [ ] Run, test, commit `data: quest objectives, relations and UI icons (schema v3)` + `docs: …`.

### Task 6: Minimal app readers (no UI yet)

**Files:**
- Create: `app/.../model/QuestObjective`, `QuestRelation`, `QuestSourceItem`, `QuestChain` (records); `app/.../db/QuestRepository` gains `objectives(questId)`, `relations(questId)`, `sourceItems(questId)`, `chain(questId)` (walks pre/next/child/exclusive/breadcrumb edges both ways with the depth cap 50 and cycle stop, returns an ordered step list with labels per the spec §4), `uiIcon(name) -> Optional<byte[]>` on `AssetRepository`.
- Test: repository tests on the fixture DB (`FixtureDb` builds with the quest fixture), `QuestChainTest` (linear chain, all-of/one-of, exclusive branch, breadcrumb, cycle, missing target, depth cap) on an in-memory DB.

- [ ] Tests red → green; commit `feat(app): quest objective, relation and chain readers`.

### Task 7: NPC patrol routes (owner request, 2026-10-17)

Owner's words: "for npcs that patrol/move (like the defias messenger) can we mark on the map their spawn point (we
already do) and the route they take? (and if they disappear where that happens, like a despawn point)". Questie's
`npcKeys.waypoints` (index 8) holds `{[zoneId] = {{{x,y},...}, ...}}` per NPC (e.g. Defias Messenger 550 in Westfall);
Questie records no despawn points, so the UI will mark the route's END as "route ends here" when it is not a loop
(last point farther than 2 % from the first).

**Files:**
- Modify: `schema.sql` (+ `npc_waypoint(npc_id INTEGER NOT NULL, zone_id INTEGER NOT NULL, route INTEGER NOT NULL,
  ordinal INTEGER NOT NULL, x REAL, y REAL, PRIMARY KEY (npc_id, zone_id, route, ordinal))`), `EntityWriter`
  (writes every waypoint list; counter `npc.waypoints.npcs`, `npc.waypoints.points`), the NPC fixture (+550 with its
  route), `app/.../db/NpcRepository.routes(npcId) -> List<Route(zoneId, List<Point>, boolean loop)>` and
  `ZoneRepository.routesInZone(zoneId)`.
- Test: `EntityWriterTest.waypointsAreWrittenInOrder`, `NpcRepositoryTest.routesOfTheDefiasMessenger` (ordinal order,
  loop detection false), `ShippedDataTest` (550 has ≥ 30 points in zone 40; `npc.waypoints.npcs` reported).
- [ ] Tests red → green; commit `feat(importer): NPC patrol waypoints`.

### Task 8: Crafting data from the client (owner request)

**Files:** tools/clientdata `REQUIRED_TABLES` + `SpellReagents`, `SkillLineAbility` (and `SkillRaceClassInfo` if present); importer `clientdata/CraftLoader` (SpellEffect Effect = 24 "Create Item" → `EffectItemType`, `EffectBasePoints`+1 = quantity; SkillLineAbility → skill id, min/trivial ranks; SpellReagents → reagents; recipe items: `ItemEffect` with a "learn spell" trigger whose taught spell creates an item → `source recipe_item_id`); schema `craft`, `craft_reagent` (+ indexes on item_id); `ClientDataStage` wiring; report counters `clientdata.crafts`, `clientdata.crafts.noSkill`; fixture CSVs cut for a few crafts (e.g. Heavy Leather, a Blacksmithing weapon, a cooking recipe); shipped-data checks (3+ crafts for a known item, reagent lists non-empty).
**Tests:** `CraftLoaderTest` (created item + quantity, reagents, skill ranks, recipe item link, a spell that creates nothing is skipped), `ImportPipelineTest` counts.
- [ ] Commit `feat(clientdata): crafting recipes and reagents`.

### Task 9: Community drop rates from a server database (owner request)

**Source (verified 2026-10-17 via the GitHub API):** `github.com/vmangos/core` (GPL-2.0) publishes release tag `db_latest` (published 2026-09-28) with assets `db-4641790.zip` (MySQL dump, 30.5 MB) and `db-sqlite-4641790.zip` (SQLite build, 42.5 MB). Use the SQLite asset: no MySQL and no dump parsing. Pin the asset name (its commit hash `4641790`) and a sha256 in a committed `tools/serverdb/serverdb.lock`; never read a moving `latest` without the pin.
**Files:** `tools/serverdb/fetch.py` (downloads the pinned `db-sqlite-<hash>.zip` into `~/.cache/wowforever-serverdb/<hash>/`, verifies sha256, unzips the SQLite file; keeps the GPL-2.0 licence text beside it), `tools/serverdb/extract.py` (reads the SQLite world DB with the stdlib `sqlite3` module and writes `creature_loot_template`, `reference_loot_template`, `gameobject_loot_template`, `npc_vendor` as CSV; the importer could also attach the SQLite file directly — builder's choice, say which), importer `serverdb/ServerDbSource` + `ServerDropRates` (resolves reference loot groups; converts ChanceOrQuestChance to a percentage, negative = quest-only drop; adds `drop_rate_source(source='vmangos')` rows; feeds `DropRates` with precedence after questie corrections and before atlasloot, per the spec ruling); `--server-db-dir` option (optional; skipped when absent); NOTICE (VMaNGOS data licence); report counters `serverdb.loot.rows`, `serverdb.unmatched.npcs`.
**Tests:** Python tests on a tiny dump fixture; `ServerDropRatesTest` (reference loot resolution, negative chance, precedence, disagreement flagging), shipped-data check that `drop_rate_source` has `vmangos` rows for Hogger's Huge Gnoll Claw.
- [ ] Commit `feat(importer): community drop rates from the VMaNGOS world database`.
