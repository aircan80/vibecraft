---@class ForeverObjectFixes
local ForeverObjectFixes = QuestieLoader:CreateModule("ForeverObjectFixes")

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")

-- Static Corrections: shared by all characters and folded in during Generation.
function ForeverObjectFixes:Load()
    local objectKeys = QuestieDB.objectKeys
    local zoneIDs = ZoneDB.zoneIDs

    return {
        [386759] = { -- Library Book
            [objectKeys.name] = "Library Book",
            [objectKeys.spawns] = {[zoneIDs.ELWYNN_FOREST] = {{65.4,70.1}}},
            [objectKeys.zoneID] = zoneIDs.ELWYNN_FOREST,
            [objectKeys.questStarts] = {79092},
        },
        [403041] = { -- Blasting Supplies
            [objectKeys.name] = "Blasting Supplies",
            [objectKeys.spawns] = {[zoneIDs.MULGORE] = {{57.4,48.8},{57.5,48.8},{58.4,47.8},{59.2,46.9},{59.5,49.0},{59.9,46.1},{60.2,50.0}}},
            [objectKeys.zoneID] = zoneIDs.MULGORE,
        },
    }
end

-- Dynamic Corrections: selected from character/game facts such as faction, race or class.
-- These override legacy Dynamic Corrections and all Static Corrections at query time.
function ForeverObjectFixes:LoadDynamic()
    return {}
end
