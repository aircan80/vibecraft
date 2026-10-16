---@class QuestieItemFixes
local QuestieItemFixes = QuestieLoader:CreateModule("QuestieItemFixes")
-------------------------
--Import modules.
-------------------------
---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB");

-- Further information on how to use this can be found at the wiki
-- https://github.com/Questie/Questie/wiki/Corrections

function QuestieItemFixes:Load()
    local itemKeys = QuestieDB.itemKeys
    local itemClasses = QuestieDB.itemClasses

    return {
        [730] = { -- Murloc Eye
            [itemKeys.npcDrops] = {1259, 1418, 127, 2206, 2207, 517, 2203, 456, 1958, 2202, 2205, 1027, 513, 2208, 2204, 2201, 126, 515, 458, 1028, 1029, 171, 1767, 1025, 3739, 1024, 3737, 1026, 3742, 3740, 422, 578, 545, 548, 1083, 544},
        },
        [1262] = { -- Keg of Thunderbrew
            [itemKeys.relatedQuests] = {116, 117},
            [itemKeys.vendors] = {239},
            [itemKeys.class] = itemClasses.QUEST,
            [itemKeys.name] = "Keg of Thunderbrew",
        },
    }
end

-- some quest items are shared across factions but require different sources for each faction
function QuestieItemFixes:LoadFactionFixes()
    return {}
end
