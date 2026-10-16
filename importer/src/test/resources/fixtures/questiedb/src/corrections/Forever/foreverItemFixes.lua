---@class ForeverItemFixes
local ForeverItemFixes = QuestieLoader:CreateModule("ForeverItemFixes")

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")

-- Static Corrections: shared by all characters and folded in during Generation.
function ForeverItemFixes:Load()
    local itemKeys = QuestieDB.itemKeys
    local itemClasses = QuestieDB.itemClasses

    return {
        [159] = { -- Refreshing Spring Water
            [itemKeys.npcDrops] = {},
            [itemKeys.objectDrops] = {}, -- only show vendors
        },
        [750] = { -- Tough Wolf Meat
            [itemKeys.npcDrops_remove] = {238425,238426,238427,247809},
        },
    }
end

-- Dynamic Corrections: selected from character/game facts such as faction, race or class.
-- These override legacy Dynamic Corrections and all Static Corrections at query time.
function ForeverItemFixes:LoadDynamic()
    return {}
end
