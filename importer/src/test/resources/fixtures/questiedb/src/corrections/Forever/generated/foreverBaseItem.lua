-- Generated delta-base. Apply after inherited Static Corrections, before authored Forever Static Corrections.
-- Import provenance and refresh policy: docs/forever-delta-base.md.
local ForeverBaseItem = QuestieLoader:CreateModule("ForeverBaseItem")
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
function ForeverBaseItem:Load()
    local itemKeys = QuestieDB.itemKeys
    return {
        [117] = { -- Tough Jerky : https://wowhead.com/forever/item=117/tough-jerky
            [itemKeys.vendors_add] = {254089, 255894, 255940, 257057, 259863, 261313, 265347, 271006},
        },
        [159] = { -- Refreshing Spring Water : https://wowhead.com/forever/item=159/refreshing-spring-water
            [itemKeys.relatedQuests] = {99050},
        },
        [1176] = { -- Smelling Salts : https://wowhead.com/forever/item=1176/smelling-salts
            [itemKeys.name] = "Smelling Salts",
            [itemKeys.itemLevel] = 8,
            [itemKeys.class] = 0,
            [itemKeys.subClass] = 8,
        },
    }
end
return ForeverBaseItem
