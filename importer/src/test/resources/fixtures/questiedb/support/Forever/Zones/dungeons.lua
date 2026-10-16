---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
---@type Expansions
local Expansions = QuestieLoader:ImportModule("Expansions")
local isHorde = UnitFactionGroup("Player") == "Horde"

---@class DungeonZoneEntry
---@field [1] string name
---@field [2] AreaId[]? alternativeAreaIds
---@field [3] AreaId parentZone
---@field [4] table<AreaId, AreaCoordinate> dungeonLocations

-- Era-framed entrances in changed zones are stored in Forever percentages, not converted at load.
-- DBC 1.15.9.69722 -> 1.60.1.69893; see docs/forever-coordinate-audit.md for source points and exclusions.
---@type table<AreaId, DungeonZoneEntry>
local dungeons = {
    [209] = {"Shadowfang Keep",{10014,10015,10016,10017,10018,10019},130,{{130, 44.8, 67.8}}}, -- removed 236, not used by anything
    [718] = {"Wailing Caverns",nil,17,{{17, 46, 36.5}}},
    [1581] = {"The Deadmines",{10029},40,{{40, 42.5, 71.7}}},
    [2437] = {"Ragefire Chasm",nil,1637,{{1637, 52.6, 49}}},
    [2717] = {"Molten Core",nil,46,{{51, 34.8, 85.3}, {46, 29.4, 38.3}}},
}

if Expansions.Current >= Expansions.Wotlk then
    dungeons[717][4] = {{1519, 52.4, 70}} -- The Stockade
    dungeons[2257][4] = {{1519, 72, 28}, {1537, 84.1, 53.1}} -- Deeprun Tram
    dungeons[3456][4] = {{65, 87.4, 51.1}} -- Naxxramas
end

if Expansions.Current >= Expansions.Cata then
    dungeons[491][3] = 4709 -- Utgarde Keep
    dungeons[491][4] = {{4709, 41, 94.6}} -- Utgarde Keep
    dungeons[718][4] = {{17, 38.9, 69.1}} -- Wailing Caverns
    dungeons[719][4] = {{331, 14.2, 13.9}} -- Blackfathom Deeps
    dungeons[721][4] = {{1, 31.1, 37.9}} -- Gnomeregan
    dungeons[722][3] = 400 -- Razorfen Downs
    dungeons[722][4] = {{400, 41.5, 29.4}} -- Razorfen Downs
    dungeons[796][4] = {{85, 82.5, 33.3}} -- Scarlet Monastery
    dungeons[1176][4] = {{440, 39.2, 21.3}} -- Zul'Farrak
    dungeons[1337][4] = {{3, 41.7, 11.6}, {3, 58.6, 37}} -- Uldaman
    dungeons[1477][4] = {{8, 69.7, 53.5}} -- The Temple of Atal'Hakkar
    dungeons[1581][4] = {{40, 42.6, 71.8}} -- The Deadmines
    dungeons[1583][4] = {{51, 34.9, 83.9}, {46, 21, 37.9}} -- Blackrock Spire
    dungeons[1584][4] = {{51, 34.9, 83.9}, {46, 21, 37.9}} -- Blackrock Depths
    dungeons[1977][4] = {{33, 72.1, 32.9}} -- Zul'Gurub
    dungeons[2017][4] = {{139, 27.7, 11.5}} -- Stratholme
    dungeons[2100][4] = {{405, 29.1, 62.6}} -- Maraudon
    dungeons[2257][4] = {{1519, 69.49, 31.2}, {1537, 84.1, 53.1}} -- Deeprun Tram
    dungeons[2366][4] = {{440, 64.8, 50}} -- The Black Morass
    dungeons[2367][4] = {{440, 64.8, 50}} -- Old Hillsbrad Foothills
    dungeons[2437][4] = {{1637, 55.2, 51.2}} -- Ragefire Chasm
    dungeons[2557][4] = {{357, 62.1, 30.2}} -- Dire Maul
    dungeons[2597][4] = {(isHorde and {267, 58.4, 33.3}) or {267, 44.5, 46}} -- Alterac Valley
    dungeons[2677][4] = {{51, 34.9, 83.9}, {46, 21, 37.9}} -- Blackwing Lair
    dungeons[2717][4] = {{51, 34.9, 83.9}, {46, 21, 37.9}} -- Molten Core
    dungeons[3277][4] = {(isHorde and {17, 39.91,20.26}) or {331, 61.66,84.52}} -- Warsong Gulch
    dungeons[3428][4] = {{1377, 24.4, 87.5}} -- Temple of Ahn'Qiraj
    dungeons[3429][4] = {{1377, 36.5, 93.9}} -- Ruins of Ahn'Qiraj
    dungeons[3606][4] = {{440, 64.8, 50}} -- Hyjal Summit
    dungeons[4100][4] = {{440, 64.8, 50}} -- The Culling of Stratholme
    -- dungeons[7307][4] = {{51, 34.9, 83.9}, {46, 21, 37.9}} -- Upper Blackrock Spire -- only added in WoD
    dungeons[10023][4] = {{357, 60.3,31.3}} -- West
    dungeons[10024][4] = {{357, 60.3,31.3}} -- West
    dungeons[10025][4] = {{357, 60.3,31.3}} -- West
end

ZoneDB.private.dungeons = dungeons
