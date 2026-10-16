---@class QuestieObjectFixes
local QuestieObjectFixes = QuestieLoader:CreateModule("QuestieObjectFixes")
-------------------------
--Import modules.
-------------------------
---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")

-- Further information on how to use this can be found at the wiki
-- https://github.com/Questie/Questie/wiki/Corrections

function QuestieObjectFixes:Load()
    local objectKeys = QuestieDB.objectKeys
    local zoneIDs = ZoneDB.zoneIDs

    return {
        [1738] = { -- Syndicate Documents
            [objectKeys.questStarts] = {510,511},
        },
        [2082] = { -- Uther the Lightbringer
            [objectKeys.name] = "Uther the Lightbringer",
            [objectKeys.spawns] = {[zoneIDs.WESTERN_PLAGUELANDS] = {{52.08,83.28}}},
        },
    }
end

-- some objects are shared across factions but require different sources for each faction
function QuestieObjectFixes:LoadFactionFixes()
    return {}
end
