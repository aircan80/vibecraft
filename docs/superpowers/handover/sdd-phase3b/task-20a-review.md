**Needs fixes** (spec ❌, quality ❌): 0 Critical, 3 Important, 7 Minor.

# Task 20a review: collector addon (addon half), 73a96c3

Reviewed in `H:\claude_java\.worktrees\collector-addon` against the brief, the PM memo "Task 20", the implementer's report and the diff. Signatures were checked on warcraft.wiki.gg. Conventions were checked against the Forever addons installed in the beta (Polestar, QuestieTrace), read only.

Nothing here would lose the single beta run outright. Each Important finding either breaks the absolute privacy rule or writes wrong facts that the importer would show as "Observed in game". Each fix is small enough to land before 19 October.

## Important

### I1. A character name can reach the file through quest text
- **Where.** `Core.lua:637` stores `GetObjectiveText()` as `objectivesText`. `Core.lua:664` stores each `C_QuestLog.GetQuestObjectives` `text`.
- **What is wrong.** The client substitutes `$N` (and `$C`/`$R`) in quest strings with the player's own name, class and race before returning them. A quest whose objective says "…, $N." puts the character name into the shared file.
- **Evidence.** QuestieTrace on this same beta treats exactly these calls as name-bearing. `QuestieTrace/Modules/Trackers/QuestDialog.lua:170-189` sanitises `GetObjectiveText` (and the other quest text calls). `QuestLog.lua:297-303,418` sanitises the objectives table. `Modules/Privacy.lua:122-136` replaces the player's and party members' names with `<name>`.
- **Why it matters.** Brief and PM memo: "never a character name, realm or guild anywhere". This is the one rule the owner was promised, and the README repeats it at lines 5 and 41.
- **Fix.**
  - Add `scrub(text)`. It reads `UnitName("player")` in memory only, through `try`, and skips the step when the name is secret. It then replaces every plain occurrence with `$N` using `gsub` with the name pattern-escaped.
  - Apply it to `objectivesText`, every objective `text` and `title`. Store the result only if it passes `clean`.
  - If the name cannot be read, drop the text and record `apiFailures` `UnitName(player)` / `secret`. Storing it unscrubbed is not an option.
  - Update the privacy comment at `Core.lua:7-9` and report line 59. Both say the player unit is asked only for its position.
  - Add a fixture objective with `$N` and a test that no string in the sample contains a test name.

### I2. Loot from pickpocketing, skinning, mining or herb-gathering a creature is recorded as that creature's drop, and can swallow its real loot
- **Where.** `Core.lua:322-391`, especially the dedupe at 341 and `placed` at 355.
- **What is wrong.** `GetLootSourceInfo` returns the same creature GUID for every way of looting that creature. The addon cannot tell a corpse loot from the other kinds.
  - **Pickpocket.** Pick Pocket on a living mob or boss opens a loot window whose source is that creature. The addon records the pickpocket loot as a creature drop and marks the spawn key seen. The real corpse loot after the kill is then skipped as a duplicate (`not seenSpawns[key]` at 341). For a rogue who pickpockets a boss, this loses the boss→item row the acceptance test needs and replaces it with junk or lockboxes.
  - **Skinning, mining, herbing.** Gathering from a corpse that the owner never looted, for example one a group member looted, records leather, ore or herbs as an NPC drop. The importer half would turn these into false `item_npc_drop` rows labelled "Observed in game".
  - **Fishing.** Fishing loot is attributed to whatever GUID the client gives, likely the bobber object.
- **Why it matters.** These are wrong facts with the highest provenance label. Pickpocketing can also cost the acceptance row in the single run.
- **Fix.**
  - Record a `lootKind` on each loot row: `corpse`, `pickpocket`, `gather` (skinning, mining, herbalism), `fishing` or `unknown`.
  - Detect fishing with `IsFishingLoot()` through `try`.
  - Detect the others from the player's last `UNIT_SPELLCAST_SUCCEEDED` for unit `player` within about 3 s before `LOOT_READY`. Pick Pocket is spell 921. Match the gathering spells by ID set or spell name. Record `secret` when the spell ID is hidden, and fall back to `unknown`.
  - Make the dedupe key `spawnKey .. "|" .. lootKind`, so a pickpocket never blocks the corpse.
  - Document `lootKind` in the README schema and tell the importer half to import only `corpse` (and `unknown` with a flag).
  - Until this lands, add one README line asking the owner not to pickpocket and to skin, mine or gather only after they have looted the corpse themselves.

### I3. The trainer filter restore passes `0`, which Lua and the client treat as true
- **Where.** `Core.lua:561` sets each filter with `1`. `Core.lua:578` restores with `saved[f] and 1 or 0`.
- **What is wrong.** On current clients the second argument is a boolean: `SetTrainerServiceTypeFilter(type, enable [, exclusive])` (https://warcraft.wiki.gg/wiki/API_SetTrainerServiceTypeFilter). `GetTrainerServiceTypeFilter` has returned a boolean since 6.0.2 (https://warcraft.wiki.gg/wiki/API_GetTrainerServiceTypeFilter). Forever 1.60.1 is a modern client. A boolean-reading argument converts `0` to true, so a filter the owner had off stays on after the scan. The mock harness evidently accepted `0` as off. The real client is unlikely to.
- **Second gap.** If `GetTrainerServiceTypeFilter` fails for a filter, `saved[f]` is nil at 560, but the filter is still forced on at 561 and never restored at 578.
- **Why it matters.** The review checklist and the code comment at 550-551 promise the trainer window's filters come back as they were. With this bug they do not. The run loses no data, but the owner's trainer window is left changed.
- **Fix.**
  - Pass booleans: `try("SetTrainerServiceTypeFilter", where, f, true)`, and on restore `saved[f] == true`.
  - Call Set only when Get succeeded and returned a non-secret boolean. Otherwise leave that filter alone and record the failure.
  - Keep the restore loop outside the scan's pcall, as now.

## Minor

### M1. Collapsed trainer headers hide their services
- **Where.** `Core.lua:567-573`.
- **What.** Indices count only visible lines, and a collapsed header's fourth return is nil (https://warcraft.wiki.gg/wiki/API_GetTrainerServiceInfo). If the owner had collapsed a section, its services are silently missing.
- **Fix.** Count headers whose `expanded` return is nil and store the count as `row.collapsedHeaders`, or record it in `apiFailures`. Add "expand every section of the trainer window" to README step 2. Do not expand the headers automatically, because that changes state the addon cannot cleanly restore.

### M2. Several reads where data is expected do not record nil
- **Where.** `(try(...) or {})[1]` at `Core.lua:451` (`GetMerchantItemID`), 542 (`GetTrainerServiceLevelReq`), 615-618 (`GetRewardMoney`, `GetRewardXP`, `GetNumQuestRewards`, `GetNumQuestChoices`) and 762 (`GetLocale`).
- **What.** The brief asks that `apiFailures` record every call that returned nil where data was expected. These calls always return a number or string when they work, so a nil from them is exactly the signal the run is meant to catch.
- **Fix.** Use `one(...)` for these calls.

### M3. `encounterID` on loot rows is a proximity hint, but the README presents it as a link to the boss
- **Where.** `Core.lua:368-372`, README line 69.
- **What.** Any loot in the same instance map within 300 s of a kill gets the boss's ID. Trash looted after a boss therefore carries it too.
- **Fix.** In the README, say "a hint: the last boss killed in this instance within five minutes". Tell the importer half to use it only for `sourceHidden` rows, or where the source id is that encounter's creature.

### M4. `shortError` can cut a multi-byte character, and the importer reads strict UTF-8
- **Where.** `Core.lua:79` truncates by bytes. `LuaChunk.java:31` reads the file with `Files.readString(..., UTF_8)`, which throws on malformed input.
- **What.** One bad error detail would make the whole shared file unreadable. English error text is almost always ASCII, so the risk is low.
- **Fix.** Back off the cut while the next byte is a continuation byte (0x80-0xBF). Also note for the importer half that it should decode leniently, so one broken byte never costs the whole file.

### M5. The fixture does not exercise what the client really writes
- **Where.** `tools/collector/fixtures/WowForeverCollector.sample.lua`.
- **What.** The sample has no escaped string, no non-ASCII text, no sparse numeric keys and no array-index comments.
  - **Escaped strings.** Objective text often contains a newline, which the client writes as `\n`. Escaped quotes (`\"`) and backslashes are also missing.
  - **Non-ASCII.** No string tests UTF-8 bytes.
  - **Sparse keys.** Trainer `raw` with a hole is what `Core.lua:536` produces whenever a return is nil or secret. The client writes the later keys as `[3] = ...`.
  - **Array-index comments.** To my knowledge the client appends `-- [n]` after array elements. I did not verify this on 1.60.1. The lexer skips comments, so this is coverage, not breakage.
- **Fix.** Add one quest row with a `\n` and a `\"` in `objectivesText`, one `raw` with a hole, one non-ASCII name, and `-- [n]` comments on one list. Assert each in `CollectorSampleTest`.

### M6. The README is mostly clear for a non-developer, with four gaps
- **Where.** `README.md:26-27, 39-41`.
- **Step order.** Step 5 ("`/wfc failures` before logging out") comes after step 4, which already logs out. Move it before step 4.
- **How to share.** "Send … as it is" does not say how or where. Name the channel, or a folder the controller can read.
- **Send only one file.** Warn the owner not to send the whole `WTF` folder or the `SavedVariables` folder. The neighbouring per-character folders are named after characters.
- **Positions.** Say that "map positions" are your own character's position at the moment of each event. A reader may otherwise wonder whether it tracks movement.
- **What is right.** The install path is correct. The beta folder is `_classic_beta_`, and a plain `.toc` loads there, as Polestar's does. The WTF location, the `.bak` note, the reload and crash warning, and "loot every boss yourself" are all correct and useful.

### M7. `numAvailable` of -1 for unlimited is only guaranteed on the old API
- **Where.** README line 71, `Core.lua:452-460`.
- **What.** `C_MerchantFrame.GetItemInfo` exists on Forever and documents `numAvailable` with a default of 0 (https://warcraft.wiki.gg/wiki/API_C_MerchantFrame.GetItemInfo). On that path, "unlimited" may arrive as 0 or -1.
- **Fix.** Hedge the README line, and tell the importer half to treat `<= 0` as unlimited.

## Checked and correct

- **Schema 1 coverage.**
  - **`session`:** build from `GetBuildInfo`, the date and the addon version.
  - **`loot`:** link, quantity, type and id, instance name, mapID and difficulty, uiMapID and x,y. Dedupe is per source with a hashed spawn key, per the controller's ruling.
  - **`encounters`:** encounterID, name and success.
  - **`vendors`:** NPC id, link, price, stack, stock and extended cost.
  - **`trainers`:** NPC id, name, cost, level requirement and skill requirement.
  - **`quests`:** accept and turn-in, questID, giver or ender with type, objectives text, rewards and position.
  - **`apiFailures`:** call, where and reason, with a count.
- **No realm, guild or GUID.** The file stores no realm, guild or raw GUID. Player sources, player quest givers and pets are dropped. The only name risk is I1.
- **Event names and payloads.**
  - `LOOT_READY` exists on Forever (https://warcraft.wiki.gg/wiki/LOOT_READY).
  - `QUEST_ACCEPTED` sends `[questLogIndex,] questId`, and both shapes are handled (https://warcraft.wiki.gg/wiki/QUEST_ACCEPTED).
  - The `QUEST_TURNED_IN` and `ENCOUNTER_START`/`ENCOUNTER_END` payloads match.
- **API signatures.**
  - `GetLootSourceInfo` returns guid and quantity pairs (https://warcraft.wiki.gg/wiki/API_GetLootSourceInfo).
  - `GetLootSlotInfo` positions 2, 3, 4, 5 and 7 match (https://warcraft.wiki.gg/wiki/API_GetLootSlotInfo).
  - `GetInstanceInfo` returns 8 (instanceID) and 10 (lfgDungeonID) match (https://warcraft.wiki.gg/wiki/API_GetInstanceInfo).
  - `C_QuestLog.GetTitleForQuestID` exists on Forever (https://warcraft.wiki.gg/wiki/API_C_QuestLog.GetTitleForQuestID).
  - The fields read from `C_MerchantFrame.GetItemInfo` match.
  - `GetMerchantItemInfo` positions 1, 3, 4, 5 and 8 match.
  - The GUID's sixth field is the id.
- **Secret values.** `v ~= nil` before `issecretvalue` is the same pattern Polestar uses on this client (`Polestar/Core/Compat.lua:42-44`). Every client call goes through `try`/`one`. Every handler, timer and slash command runs in `guarded`. Nothing prints except `/wfc`, so neither an error nor a secret value can spam chat.
- **SavedVariables.**
  - The file holds only strings, numbers, booleans and plain tables. There are no functions, userdata or cycles.
  - Shared `instance` tables within one loot window are written as copies, which is harmless.
  - Growth is bounded at 20,000 rows per list and 500 failure rows.
- **Loading.** `ADDON_LOADED` checks the addon name and unregisters itself. No `PLAYER_LOGOUT` handler is needed, because rows are written straight into the saved table.
- **Globals and taint.** The only globals are `WowForeverCollectorDB` and the required `SLASH_WOWFORECOLLECTOR1`/`SlashCmdList` entry. No protected function is called.
- **TOC.** The fields are valid for Interface 16001: `## Interface: 16001`, Title, Notes, Author, Version and `## SavedVariables` only. The file name matches the folder, and the header matches Polestar's on the same beta.
- **Fixture test.** Line endings are kept byte-exact by `.gitattributes`. The test checks keys, but not string values. I1 adds a value check.
