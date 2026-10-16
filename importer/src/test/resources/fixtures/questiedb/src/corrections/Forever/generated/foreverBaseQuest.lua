-- Generated delta-base. Apply after inherited Static Corrections, before authored Forever Static Corrections.
-- Import provenance and refresh policy: docs/forever-delta-base.md.
local ForeverBaseQuest = QuestieLoader:CreateModule("ForeverBaseQuest")
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
function ForeverBaseQuest:Load()
    local questKeys = QuestieDB.questKeys
    local raceIDs = QuestieDB.raceKeys
    local classIDs = QuestieDB.classKeys
    local zoneIDs = ZoneDB.zoneIDs
    local sortKeys = QuestieDB.sortKeys
    return {
        [5691] = { -- <NYI> <TXT> In the Dark it was Created : https://wowhead.com/forever/quest=5691/nyi-txt-in-the-dark-it-was-created
            [questKeys.name] = "<NYI> <TXT> In the Dark it was Created",
            [questKeys.requiredLevel] = 15,
            [questKeys.questLevel] = 15,
            [questKeys.requiredRaces] = raceIDs.UNDEAD,
            [questKeys.requiredClasses] = classIDs.PRIEST,
            [questKeys.zoneOrSort] = sortKeys.PRIEST,
        },
        [5692] = { -- <NYI> In the Dark It was Created : https://wowhead.com/forever/quest=5692/nyi-in-the-dark-it-was-created
            [questKeys.name] = "<NYI> In the Dark It was Created",
            [questKeys.requiredLevel] = 15,
            [questKeys.questLevel] = 15,
            [questKeys.requiredRaces] = raceIDs.UNDEAD,
            [questKeys.requiredClasses] = classIDs.PRIEST,
            [questKeys.objectivesText] = {"Speak to Aelthalyste in the Undercity."},
            [questKeys.zoneOrSort] = sortKeys.PRIEST,
        },
    }
end
return ForeverBaseQuest
