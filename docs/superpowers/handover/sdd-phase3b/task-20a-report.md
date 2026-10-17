# Task 20a report: collector addon (addon half)

Status: done. Commit `73a96c3` on `feature/collector-addon` (worktree `H:\claude_java\.worktrees\collector-addon`).

## What was built

| File | Purpose |
| --- | --- |
| `tools/collector/WowForeverCollector/WowForeverCollector.toc` | `## Interface: 16001`, title, notes, version 1.0.0, `## SavedVariables: WowForeverCollectorDB` (account-wide only). |
| `tools/collector/WowForeverCollector/Core.lua` | The addon. Loot, encounters, vendors, trainers, quests, apiFailures, session; `/wfc` status, `/wfc failures`, `/wfc reset yes`. |
| `tools/collector/WowForeverCollector/README.md` | Owner steps: install path, enabling on the character screen, the run plan, where the file lands, how to share it; schema 1 reference for the importer. |
| `tools/collector/fixtures/WowForeverCollector.sample.lua` | Hand-written SavedVariables in the client's exact layout. |
| `lua-table/src/test/java/dev/wowforever/lua/CollectorSampleTest.java` | Parses and evaluates the sample with `LuaChunk` + `ChunkEvaluator`. |
| `.gitattributes` | `tools/collector/fixtures/** -text`, so the fixture keeps its CRLF bytes. |

### Schema decisions beyond the brief

- **Loot rows are per source**, not per window. Each row has `source = { type, id, spawnKey }` and its `items`. Area loot from two corpses becomes two rows with per-source quantities from `GetLootSourceInfo`.
- **`spawnKey`** is a one-way hash of the GUID: two 32-bit string hashes, 16 hex digits. The raw GUID is never stored. The hash lets the addon skip a corpse already recorded in an earlier session, and lets the importer dedupe "duplicate source GUIDs count once" across files. Without it, the brief's importer test has nothing to dedupe on. Flag this if the controller reads it as storing a GUID.
- **`encounterID` on loot** is set when a boss died in the same instance map within 300 seconds. This is the fallback that keeps the acceptance goal reachable if `GetLootSourceInfo` turns out to be secret inside instances.
- **`sourceHidden = true`** rows keep items whose source the client hid, at most one per loot window.
- **Vendors and trainers** keep one row per NPC id; a later visit replaces the row.
- **Trainer `raw`** keeps the plain `GetTrainerServiceInfo` returns at their positions. Polestar reads Forever's returns as `name, kind, _, _, rank`, which differs from Classic Era. The kind is found by value (`available` / `unavailable` / `used`).
- **Trainer filters:** the scan turns on all three service filters, reads, then restores them, so unavailable and known services are captured too.
- Both `LOOT_READY` and `LOOT_OPENED` are handled. The spawn key makes the second one a no-op for sources already read. QuestieTrace and Polestar both use `LOOT_READY` on this client.
- `QUEST_ACCEPTED` accepts both payload shapes: `(questID)` on the modern client and `(questLogIndex, questID)` on Classic Era.

### Fixture values

The fixture's NPC ids, item ids, names and timestamps are invented, apart from encounter 3303. The README and this report say so. The importer half should not treat them as facts.

## Interface version evidence

| Source | Finding |
| --- | --- |
| Owner's beta install, `_classic_beta_\Interface\AddOns` (read only) | Every Forever-only addon declares `## Interface: 16001`: Polestar and its five modules, DBM-Party-Forever, Questie_Camelot.toc, QuestieDB_Forever.toc, QuestieTrace-Camelot.toc and Plater_Camelot.toc. Multi-client addons such as DBM-Core, Auctionator, Baganator and Syndicator list 16001 among their comma-separated values. |
| Installed Polestar `Core/Compat.lua` | "Each function was checked against the Forever (1.60.1) API declarations." It uses `issecretvalue`, which confirms secret values on this client. |
| warcraft.wiki.gg "TOC format" | Forever Beta 16001, Forever Test 16001. Patch 1.60.1 (Beta) added the Camelot game type, with `_Camelot.toc` and `_Forever.toc` file suffixes. `_Mainline.toc` is the family suffix covering it, and the plain `.toc` is the fallback. Comma-separated Interface values have been supported since 10.2.7. |

**`## Interface-Classic` was not added.** The wiki documents no client-specific Interface directive key such as `## Interface-Classic` or `## Interface-Vanilla`. Hyphenated keys exist only for locales (`Title-frFR`). Client targeting is done by file suffix or by comma-separated values. The addon targets one client, so a single `WowForeverCollector.toc` with `16001` is enough. If a later beta build bumps the number, the README tells the owner to tick "Load out of date AddOns".

## How each handler guards against secret values

Shared machinery in `Core.lua`:

- `isSecret(v)` calls `issecretvalue` under pcall. A missing `issecretvalue` means nothing is secret; a throwing test counts as secret.
- `clean(v)` is the only way into the saved table. It passes non-secret strings, numbers and booleans and nothing else.
- `try(call, where, ...)` resolves the dotted API path. A missing function gives `missing`. The call runs under pcall, and a throw gives `error` with a 240-character message. Each secret return is replaced by nil and recorded once as `secret` with the return positions. `one()` adds `nil` when the first return is missing and was not secret.
- `apiFailures` keeps one row per call, where and reason, with `count`, `first`, `last` and `detail`. `where` is the event, with " (instance)" appended inside an instance, because Forever hides more there.
- Every event handler, timer callback and the slash command run in `guarded()`, which pcalls and records `error`. `RegisterEvent` is pcalled per event, and an unknown event is recorded as `missing` without stopping the others.

| Handler | Guards |
| --- | --- |
| `LOOT_READY` / `LOOT_OPENED` | Count, slot type, link, slot info and source info all go through `try` or `one`. A GUID is parsed only after `clean`, and `strsplit` runs under pcall. Player sources are dropped. A secret or nil source gives a `sourceHidden` row. Position and instance info are read through `try` as well. |
| `ENCOUNTER_START` / `ENCOUNTER_END` | Every payload argument is tested. A secret payload gives an `ENCOUNTER_* payload` / `secret` row, and values are stored only through `clean`. |
| `MERCHANT_SHOW` / `MERCHANT_UPDATE` | The NPC comes from `UnitGUID("npc")`, falling back to the target only when `UnitIsUnit` says it is the same unit. `C_MerchantFrame.GetItemInfo` is used when present, else `GetMerchantItemInfo`. Every table field is cleaned. The window is rescanned after 1 second and on updates, at most every 0.5 seconds, because links arrive late. |
| `TRAINER_SHOW` | Same NPC guard. Filters are saved, opened and restored even if the scan throws. Every service call goes through `try` or `one`. |
| `QUEST_DETAIL` / `QUEST_COMPLETE` / `QUEST_ACCEPTED` / `QUEST_TURNED_IN` | The giver or ender is captured at detail or complete. A player giver, such as a shared quest, is never stored. Rewards and objectives come through `try`. A secret XP is dropped and recorded. Objectives are re-read after 1.5 seconds if the log lags. |
| Privacy | The code never calls `UnitName("player")`, `GetRealmName` or `GetGuildInfo`. The player unit is asked only for its map position. |

## Test results

- `./mvnw -B -q -pl lua-table test`: exit 0. All nine test classes pass, 66 tests with 0 failures. `CollectorSampleTest` has 5 tests: it evaluates with no problems, reads the session build "1.60.1" / "70450" / interface 16001, reads loot row 1 as a Creature with id 239512, encounterID 3303 and its first item, reads the `sourceHidden` row, reads apiFailures row 2 as `GetLootSourceInfo` / `LOOT_READY` / `secret`, and checks that no character, realm, guild or GUID key exists anywhere.
- **Mock-client run, not committed.** Core.lua was loaded in real Lua 5.1 through Python `lupa` 2.8, against a mocked WoW API with secret values that throw on compare, index, concat and arithmetic. The harness is at `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\harness\`. Its scenario asserts:
  - The boss and an add become 2 rows; money and player sources are skipped.
  - A reopened corpse adds nothing, also after a simulated reload with the saved table.
  - A secret source gives one `sourceHidden` row per window.
  - A thrown API is recorded as `error`.
  - A secret encounter payload is recorded.
  - A missing `GetMerchantItemID` is recorded as `missing`.
  - An unknown event registration is recorded.
  - A second vendor visit replaces its row, and the late link is filled by the rescan.
  - Trainer filters are restored, and all 3 non-header services are read.
  - A player-shared quest stores no giver.
  - The serialized table contains no player name, realm, guild or GUID string, and no secret value.

## Not verifiable without the game

- Whether the client loads the addon at all, and the exact behaviour of `issecretvalue` on 1.60.1.70450.
- Which calls Forever actually hides. Likely candidates are `GetLootSourceInfo` and `UnitGUID("npc")` in instances, and `C_Map.GetPlayerMapPosition` in instances, which is nil by design on modern clients. Finding this out is what the run is for.
- The real return order of `GetTrainerServiceInfo`, `GetLootSlotInfo` and `GetInstanceInfo` on Forever. They are stored defensively, with trainer `raw` kept by position.
- Whether `MERCHANT_UPDATE` exists on this client. A missing event is recorded, and the 1-second rescan still runs.
- Whether flipping trainer filters disturbs Blizzard's trainer window. It is restored right away and is not a protected call.
- SavedVariables key order: the client writes keys in hash order. The fixture's order is hand-picked, and the parser does not depend on it.

## Fix round 1 (review `task-20a-review.md`)

Commit `c720b3b` on top of `73a96c3`. Addon version 1.1.0, still schema 1. The new key is additive: `lootKind`, plus `lootSpellID` and `collapsedHeaders`.

| Finding | What changed |
| --- | --- |
| **I1** name in quest text | `scrub()` reads `UnitName("player")` through `try`, in memory only. It replaces every plain occurrence with `$N` in quest `title`, `objectivesText`, each objective `text`, and reward names without a link. If the name is secret or nil, the text is dropped and `UnitName(player)` / `secret` or `nil` is recorded with detail "quest text dropped". The header comment now says the player unit is also asked for its name, in memory only. The fixture has `$N` in `objectivesText`. A test asserts it, and asserts that no string holds a GUID or the harness's test name. |
| **I2** loot kinds | Each loot row carries `lootKind`: `corpse`, `pickpocket`, `gather`, `fishing` or `unknown`. Fishing comes from `IsFishingLoot`. The others come from the player's `UNIT_SPELLCAST_SUCCEEDED` within 3 s, registered as a unit event for `player` when the client has `RegisterUnitEvent`. Matching is by ID (Pick Pocket 921; the Classic skinning, mining and herb-gathering ranks) and by spell name. A secret spell ID gives `unknown` plus a `secret` row. The cast is consumed on `LOOT_CLOSED`. Dedupe is now `spawnKey .. "|" .. lootKind`, rebuilt the same way on load, so a pickpocket never blocks the corpse. The README documents the kinds, tells the importer to import only `corpse` (and `unknown` with a flag), and asks the owner to loot before skinning and not to pickpocket bosses. The fixture has a pickpocket row that shares the boss's spawn key, and a test asserts it. |
| **I3** filter restore | A filter's state is changed only when `GetTrainerServiceTypeFilter` returned a real, non-secret boolean. It is set with `true` and restored with `false`, and only those filters are touched. A nil read is recorded and that filter is left alone; a secret read is recorded once by `try`. The restore stays outside the scan's pcall. |
| **M1** collapsed headers | You asked for expand-then-restore; the review said do not expand. I followed your instruction, with a safeguard. A header counts as collapsed when, with all filters on, the next line is a header or the list ends. This avoids relying on a return position that differs on Forever. Collapsed sections are expanded from the end with `ExpandTrainerSkillLine`, read, then collapsed again by name from the end with `CollapseTrainerSkillLine`. This happens only when all three filters were read. Otherwise nothing is expanded and `collapsedHeaders` is left out, because the shape test would be unreliable. The count is stored as `collapsedHeaders`. A missing Expand is recorded as `missing` with the number of sections not read. |
| **M2** nil reads | `GetMerchantItemID`, `GetTrainerServiceLevelReq`, `GetRewardMoney`, `GetRewardXP`, `GetNumQuestRewards`, `GetNumQuestChoices` and `GetLocale` now go through `one()`. |
| **M3** encounterID | The README and a code comment call it a hint: the last boss killed in this instance within five minutes. The importer should use it only for `sourceHidden` rows, or where the source is that encounter's creature. |
| **M4** UTF-8 | `shortError` backs the cut off past continuation bytes. The README tells the importer to decode leniently. The harness puts a two-byte character exactly at the cut, then decodes the output as strict UTF-8. |
| **M5** fixture | Now has an escaped `\n` and `\"` in quest text, raw UTF-8 (`Thérum`), and a trainer `raw` with a hole written as `[5] = "Rank 1"` after the sequential values. That is how this client writes holes: QuestieTrace's saved `GetLootSlotInfo` tables on this beta use the same form. **No `-- [n]` comments were added.** No SavedVariables file on this beta has one, account-wide or per character, so the client does not write them. An inline test shows the reader skips them anyway. |
| **M6** README gaps | `/wfc failures` now comes before `/reload` and logout. Sharing says the controller reads the file in place, because the beta is installed on the same PC as the app. From another PC, copy only that file into Downloads. The README warns not to send the `WTF`, `Account` or `SavedVariables` folders, which hold folders named after characters. It says map positions are the character's own position at each recorded event, not a movement track. |
| **M7** numAvailable | README: treat `numAvailable <= 0` as unlimited. Code comment on the `C_MerchantFrame` path. |

New evidence from the owner's beta (read only, no names copied): QuestieTrace's saved data shows that `GetLootSourceInfo` returned a plain `Creature-…-250873-…` GUID with the npc id in field six. It also shows `GetLootSlotInfo` returning icon, name, quantity, nil, quality, false, false, …, which matches the positions used.

### Verification

- `./mvnw -B -q -pl lua-table test`: exit 0. 71 tests, 0 failures, 0 errors, 0 skipped. `CollectorSampleTest` now has 10 tests:
  - Loot kinds, and the pickpocket row sharing the boss's spawn key.
  - Escaped quest text with `$N`.
  - The non-ASCII name and the sparse `raw`.
  - Index comments skipped.
  - No GUID or name in any string.
- **Mocked client, Lua 5.1 via lupa, exit 0.** The harness is at `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\harness\`. It treats any non-false value, including 0, as "on" for a filter, as the real client does, so it would have caught I3. Its new assertions:
  - A pickpocket before the kill does not block the boss corpse row, which keeps `encounterID` 3303.
  - Skinning gives `gather`, a secret spell ID gives `unknown`, and a stale cast does not tag a later corpse.
  - Fishing gives `fishing`.
  - Filters come back as true/false/false, the collapsed section is collapsed again, the open one stays open, and all 4 services are read.
  - A secret filter leaves that filter and the sections untouched, and no `collapsedHeaders` is stored.
  - Quest text stores `$N` for the name. A secret name drops the text and title.
  - After a reload, the same corpse is still not counted twice.
  - The output holds no player name, realm, guild or GUID, and decodes as strict UTF-8.

### Could not do or verify

- Whether Forever uses the Classic spell IDs for skinning, mining and herb gathering. Name matching covers enUS, but other locales fall back to `corpse` for gathering. Gathering on a corpse the owner already looted is still kept apart, because its kind differs from that corpse's row.
- Whether `ExpandTrainerSkillLine` and `CollapseTrainerSkillLine` act synchronously on Forever. If they do not, the read misses the collapsed services, and the restore may find nothing to collapse.
- Whether pickpocketing on Forever opens a loot window at all. Modern clients may send it straight to the bags, in which case no row is written, which is harmless.
- Name scrubbing is a plain substring replace. A short character name can blank part of a longer word in quest text. This is deliberate and documented.
