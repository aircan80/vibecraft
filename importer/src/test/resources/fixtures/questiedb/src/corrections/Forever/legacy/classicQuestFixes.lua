---@class QuestieQuestFixes
local QuestieQuestFixes = QuestieLoader:CreateModule("QuestieQuestFixes")
-------------------------
--Import modules.
-------------------------
---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
---@type QuestieProfessions
local QuestieProfessions = QuestieLoader:ImportModule("QuestieProfessions")
---@type QuestieCorrections
local QuestieCorrections = QuestieLoader:ImportModule("QuestieCorrections")
---@type l10n
local l10n = QuestieLoader:ImportModule("l10n")

QuestieCorrections.itemObjectiveFirst[503] = true
QuestieCorrections.itemObjectiveFirst[5088] = true

-- Further information on how to use this can be found at the wiki
-- https://github.com/Questie/Questie/wiki/Corrections

function QuestieQuestFixes:Load()
    local questKeys = QuestieDB.questKeys
    local zoneIDs = ZoneDB.zoneIDs
    local raceIDs = QuestieDB.raceKeys
    local classIDs = QuestieDB.classKeys
    local sortKeys = QuestieDB.sortKeys
    local specialFlags = QuestieDB.specialFlags
    local profKeys = QuestieProfessions.professionKeys
    local specKeys = QuestieProfessions.specializationKeys
    local factionIDs = QuestieDB.factionIDs
    local rankKeys = QuestieProfessions.rankNames

    return {
        [960] = { -- Onu is meditating
            [questKeys.name] = "Onu is meditating",
            [questKeys.startedBy] = {{3616}},
            [questKeys.finishedBy] = {{3616}},
            [questKeys.requiredLevel] = 1,
            [questKeys.questLevel] = 1,
            [questKeys.preQuestSingle] = {944},
            [questKeys.availableUntilCompleted] = 949,
            [questKeys.requiredRaces] = raceIDs.ALL_ALLIANCE,
            [questKeys.requiredClasses] = classIDs.NONE,
            [questKeys.zoneOrSort] = zoneIDs.DARKSHORE,
            [questKeys.nextQuestInChain] = 0,
            [questKeys.questFlags] = 8,
            [questKeys.specialFlags] = specialFlags.REPEATABLE,
        },
        [9422] = { -- Scouring the Desert
            [questKeys.preQuestSingle] = {},
            [questKeys.requiredLevel] = 1,
            [questKeys.disabledByQuest] = 9416,
            [questKeys.objectives] = {{{18199, nil, Questie.ICON_TYPE_EVENT}}},
            [questKeys.extraObjectives] = {{nil, Questie.ICON_TYPE_OBJECT, l10n("Silithyst"), 0, {{"object", 181597}, {"object", 181598}}}},
        },
    }
end

function QuestieQuestFixes:LoadFactionFixes()
    return {}
end
