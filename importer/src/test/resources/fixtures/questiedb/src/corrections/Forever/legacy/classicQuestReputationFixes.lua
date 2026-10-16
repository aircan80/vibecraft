-- AUTO GENERATED FILE! DO NOT EDIT!
---@class QuestieClassicQuestReputationFixes
local QuestieClassicQuestReputationFixes = QuestieLoader:CreateModule("QuestieClassicQuestReputationFixes")

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")

function QuestieClassicQuestReputationFixes:Load()
    local questKeys = QuestieDB.questKeys

    return {
        [2] = {
            [questKeys.reputationReward] = {{81,100}},
        },
        [136] = {
            [questKeys.reputationReward] = {},
        },
    }
end
