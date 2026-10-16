---@class ForeverNpcFixes
local ForeverNpcFixes = QuestieLoader:CreateModule("ForeverNpcFixes")

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
---@type Phasing
local Phasing = QuestieLoader:ImportModule("Phasing")

-- Static Corrections: shared by all characters and folded in during Generation.
function ForeverNpcFixes:Load()
    local npcKeys = QuestieDB.npcKeys
    local zoneIDs = ZoneDB.zoneIDs
    local npcFlags = QuestieDB.npcFlags
    local waypointPresets = QuestieDB.waypointPresets
    local phases = Phasing.phases

    return {
        [2155] = { -- Sentinel Shayla Nightbreeze
            [npcKeys.questEnds_add] = {490},
            [npcKeys.questStarts_add] = {490},
            [npcKeys.spawns] = {[zoneIDs.TELDRASSIL] = {{40.46,50.73}}},
            [npcKeys.waypoints] = {},
        },
        [2756] = { -- Grund Drokda
            [npcKeys.name] = "Grund Drokda",
            [npcKeys.spawns] = {[zoneIDs.DUN_MOROGH] = {{28.67,67.52}}},
            [npcKeys.zoneID] = zoneIDs.DUN_MOROGH,
        },
    }
end

-- Dynamic Corrections: selected from character/game facts such as faction, race or class.
-- These override legacy Dynamic Corrections and all Static Corrections at query time.
function ForeverNpcFixes:LoadDynamic()
    return {}
end
