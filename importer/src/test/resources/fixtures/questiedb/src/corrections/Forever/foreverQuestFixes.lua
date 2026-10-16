---@class ForeverQuestFixes
local ForeverQuestFixes = QuestieLoader:CreateModule("ForeverQuestFixes")

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

QuestieCorrections.itemObjectiveFirst[92682] = true
QuestieCorrections.killCreditObjectiveFirst[94489] = true
QuestieCorrections.itemObjectiveFirst[97277] = true
QuestieCorrections.itemObjectiveFirst[97485] = true

-- Static Corrections: shared by all characters and folded in during Generation.
function ForeverQuestFixes:Load()
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
        [76] = { -- The Jasperlode Mine
            [questKeys.triggerEnd] = {"Scout through the Jasperlode Mine", {[zoneIDs.ELWYNN_FOREST] = {{61.93,53.16}}}},
        },
        [490] = { -- Bounty: Gnarlpine Furbolg
            [questKeys.name] = "Bounty: Gnarlpine Furbolg",
            [questKeys.startedBy] = {{2155}},
            [questKeys.finishedBy] = {{2155}},
            [questKeys.requiredLevel] = 5,
            [questKeys.questLevel] = 8,
            [questKeys.requiredRaces] = raceIDs.ALL_ALLIANCE,
            [questKeys.requiredClasses] = raceIDs.NONE,
            [questKeys.objectivesText] = {"Shayla Nightbreeze on the road outside of Darnassus wants you to bring her 15 Gnarlpine Fangs."},
            [questKeys.objectives] = {nil,nil,{{5220}}},
            [questKeys.zoneOrSort] = zoneIDs.TELDRASSIL,
            [questKeys.questFlags] = 8,
            [questKeys.reputationReward] = {{factionIDs.DARNASSUS, 100}},
        },
    }
end

-- Dynamic Corrections: selected from character/game facts such as faction, race or class.
-- These override legacy Dynamic Corrections and all Static Corrections at query time.
function ForeverQuestFixes:LoadDynamic()
    return {}
end
