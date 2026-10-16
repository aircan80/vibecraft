---@class QuestieNPCFixes
local QuestieNPCFixes = QuestieLoader:CreateModule("QuestieNPCFixes")
-------------------------
--Import modules.
-------------------------
---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
---@type Phasing
local Phasing = QuestieLoader:ImportModule("Phasing")

-- Further information on how to use this can be found at the wiki
-- https://github.com/Questie/Questie/wiki/Corrections

function QuestieNPCFixes:Load()
    local npcKeys = QuestieDB.npcKeys
    local zoneIDs = ZoneDB.zoneIDs
    local npcFlags = QuestieDB.npcFlags
    local waypointPresets = QuestieDB.waypointPresets
    local phases = Phasing.phases

    return {
        [300] = { -- Zzarc' Vul
            [npcKeys.spawns] = {[zoneIDs.DUSKWOOD] = {{35,80},{36.81,83.79},{38,80}}}, -- #1047
            [npcKeys.minLevel] = 30,
            [npcKeys.maxLevel] = 30,
        },
        [314] = { -- Eliza <Bride of the Embalmer>
            [npcKeys.spawns] = {[zoneIDs.DUSKWOOD] = {{28.82,30.91}}},
            [npcKeys.zoneID] = zoneIDs.DUSKWOOD,
        },
    }
end

-- some quest items are shared across factions but require different sources for each faction (not sure if there is a better way to implement this)
function QuestieNPCFixes:LoadFactionFixes()
    return {}
end