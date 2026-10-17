# WoW Forever Collector

An in-game addon for the WoW: Forever beta. It writes down what the game shows you:
- what dropped from which boss or chest
- boss encounters
- vendor stock and trainer services
- quests you accept and turn in

It also writes down every place where the client hid data from addons. The browser app imports the file later, and those rows show as "Observed in game".

The addon keeps one account-wide file. That file never holds a character name, realm or guild. Quest text that would show your character's name keeps the game's placeholder `$N` instead. NPCs and objects are kept as a type and an id. The game's raw unit IDs (GUIDs) are never stored.

## Install (19 October)

1. Close the game.
2. Copy the whole `WowForeverCollector` folder, the one that holds this README, into the beta's AddOns folder. The result must be:

   ```
   C:\Program Files (x86)\World of Warcraft\_classic_beta_\Interface\AddOns\WowForeverCollector\WowForeverCollector.toc
   ```

   Copy it yourself. The browser app never writes into the game folder.
3. Start the beta. On the character screen, click **AddOns** at the bottom left.
4. Tick **WoW Forever Collector**. If it shows as out of date, also tick **Load out of date AddOns** at the top of that list.
5. Enter the world and type `/wfc`. The addon answers with a line of counts, such as `loot 0, encounters 0, ...`. If nothing answers, the addon is not loaded; check step 2.

## Session 1 (about one hour)

1. If you have a character of the right level and a group, run one dungeon from start to end. Pick by level: The Hall of Thanes (Ironforge, level 13 and up), Ruins of Lordaeron (Tirisfal, 15 and up), Excavation Site (Wetlands, 26 and up) or City of Dalaran (Alterac, 28 and up). A partial run still records every boss you kill. Loot every boss corpse yourself, also when a group member could.
   - No dungeon is fine too. Play normally in the open world instead: everything you loot (quest mobs, chests, rare spawns), every vendor and trainer you open and every quest you accept or turn in is recorded, and so is the list of calls the client hid. A Forever-new zone or quest line is the most useful place to spend the hour.
   - Loot a corpse before you skin it, and do not pick-pocket bosses. The addon files pickpocket, skinning, mining, herb and fishing loot separately, but a corpse you never looted gives the browser nothing.
2. In a capital city, open one vendor and one trainer. Open the trainer for your profession or class, and leave each window open for two seconds.
   - The addon opens collapsed trainer sections and turns on every filter while it reads, then puts the window back as it was. The list may flicker once.
3. Accept one quest from an NPC and turn it in.
4. Optional: type `/wfc failures` to see what the client hid.
5. Type `/reload`, then log out. The game writes the file only on `/reload`, logout or exit. A crash or Alt+F4 loses everything since the last `/reload`.

## Where the file is

```
C:\Program Files (x86)\World of Warcraft\_classic_beta_\WTF\Account\<account>\SavedVariables\WowForeverCollector.lua
```

`<account>` is a folder named like `12345678#1`. If there are several, use the one for the game account you played on. Its `SavedVariables` folder has a fresh `WowForeverCollector.lua`. Ignore the `.lua.bak` next to it; that is the previous copy.

## Share the file

- **Played on this PC:** tell the controller the session is done. The controller reads the file where it is, read only, and imports it into a scratch database. You do not need to copy anything.
- **Played on another PC:** copy only `WowForeverCollector.lua` onto this PC, into your Downloads folder. Then tell the controller where it is.
- **Send only that one file.** Do not send the `WTF` folder, the `Account` folder or the whole `SavedVariables` folder. The folders next to it are named after your characters and hold other addons' data.

The file holds:
- item links and names
- NPC and object ids and names
- quest text, with `$N` where your name would be
- encounter names and server timestamps
- your own character's map position at the moment of each recorded event: a loot window, a vendor or trainer visit, a quest accept or turn-in

It does not track your movement in between, and it holds no character name, realm or guild.

## Session 2 (20 or 21 October, only if asked)

If the first file shows that the addon needs a fix, you get a new copy. Replace the folder, then run another dungeon. If a Blueprint drops anywhere, loot it yourself.

## Commands

| Command | What it does |
| --- | --- |
| `/wfc` | Prints how many rows each list holds. |
| `/wfc failures` | Lists the first 20 calls the client hid or refused. |
| `/wfc reset yes` | Clears everything recorded so far. |

## Acceptance

At least one loot source from the session, a Forever boss when a dungeon was run and otherwise any creature or chest, gets an item row "Observed in game" on its page in the browser, and the vendor and trainer stock import. If none does, the `apiFailures` list in the file says why.

## File format (schema 1)

The game writes a Lua file that sets `WowForeverCollectorDB`. The importer reads it with the project's `lua-table` reader. A sample is in `tools/collector/fixtures/WowForeverCollector.sample.lua`; its values are invented.

What this client writes:
- CRLF line endings and no indentation.
- Text as raw UTF-8, with `\n` and `\"` escapes.
- Arrays as plain values, then any later index after a hole as `[5] = ...`.
- No `-- [n]` comments.
- Keys in no fixed order.

Decode the file leniently, so one bad byte never costs the whole file.

Coordinates `x` and `y` are percent of the `uiMapID` map with two decimals. They are missing where the client hides the position, which it does in most instances. `t` is the server time in epoch seconds.

| Key | Contents |
| --- | --- |
| `schema` | `1`. |
| `session` | `build` and `buildNumber` from `GetBuildInfo`, plus `buildDate`, `interface`, `builds` (every build seen), `addonVersion`, `locale`, `firstDate`, `lastDate`, `logins`. |
| `loot` | One row per loot source and loot kind. See the loot notes below. |
| `encounters` | `ENCOUNTER_START` and `ENCOUNTER_END`: `event` (`start` or `end`), `encounterID`, `name`, `difficultyID`, `groupSize`, `success` on end, and `instance`. |
| `vendors` | One row per vendor NPC; a later visit replaces it. `npc` is `{ type, id, name }`. `items` holds `link`, `itemID`, `name`, `price` in copper, `stack`, `numAvailable`, `extendedCost`, and `costs` as `{ value, link, currency }`. Treat `numAvailable <= 0` as unlimited: the old API gives -1, and `C_MerchantFrame.GetItemInfo` may give 0. |
| `trainers` | One row per trainer NPC. `npc`, `tradeskill`, and `collapsedHeaders`: how many sections were collapsed when the window opened. Those sections are expanded for the read and collapsed again. `services` holds `name`, `kind` (`available`, `unavailable` or `used`), `cost`, `levelReq`, `skill` as `{ name, rank, met }`, and `raw`: the plain `GetTrainerServiceInfo` returns at their positions. |
| `quests` | `event` (`accept` or `turnin`), `questID`, `title`, `npc` (giver or ender; missing for a quest shared by a player), `objectivesText`, `objectives` as `{ text, type, numRequired }`, `startItemID` for an item-started quest, and `rewards` as `{ money, xp, items, choices }`. The character name in text is replaced by `$N`. When the name could not be read, the text is left out and `apiFailures` has a `UnitName(player)` row. |
| `apiFailures` | One row per call, place and reason: `call`, `where` (the event, with " (instance)" inside one), `reason`, `count`, `first`, `last`, and sometimes `detail`. `reason` is `secret` (the client returned a secret value), `nil` (no data where data was expected), `error` (the call or a handler threw), or `missing` (the client lacks that function or event). |

### Loot rows

- **`source`** is `{ type, id, spawnKey }`. `type` is `Creature` or `GameObject` with the NPC or object id; other kinds keep only the type.
- **`spawnKey`** is a one-way hash of the GUID. The same corpse counts once per loot kind, across reloads and files.
- **`lootKind`** says how the window was opened. The addon takes it from `IsFishingLoot` and from the player's own spell cast within 3 seconds before the window. The game gives a creature the same GUID for its corpse, a pickpocket and a skinning.

  | `lootKind` | Meaning |
  | --- | --- |
  | `corpse` | The creature's or object's own loot. |
  | `pickpocket` | Spell 921. |
  | `gather` | Skinning, mining or herb gathering. |
  | `fishing` | Fishing loot. |
  | `unknown` | The client hid the spell just cast. |

  **For the importer:** import only `corpse` rows as drops. Import `unknown` with a flag. Never import `pickpocket`, `gather` or `fishing` rows as an NPC or object drop.
- **`lootSpellID`** is the spell behind a non-corpse kind.
- **`items`** holds `link`, `itemID`, `name`, `quantity`, `quality` and, when set, `questItem` and `currencyID`.
- **`instance`** holds `name`, `instanceType`, `difficultyID`, `difficultyName`, `mapID` and sometimes `lfgDungeonID`.
- **`encounterID`** is a hint, not a link. It names the last boss killed in this instance within five minutes, so trash looted after a boss carries it too. **For the importer:** use it only for `sourceHidden` rows, or where the source id is that encounter's own creature.
- **`sourceHidden = true`**, with no `source`, marks items whose source the client hid.
- **`event`** is the event that read the window.

## Notes for maintainers

- The TOC declares `## Interface: 16001`, the number the Forever beta (1.60.1) reports. Every Forever addon installed on the owner's beta declares the same number.
- Every client call goes through `try` or `one` in `Core.lua`. Those pcall the call, test each return with `issecretvalue`, and record failures. Only values that pass `clean` are stored.
- The player's name is read only to replace it with `$N` in quest text, and never stored. Every plain occurrence is replaced, also inside a longer word, so a short name can blank part of a word. That is preferred to leaking it.
