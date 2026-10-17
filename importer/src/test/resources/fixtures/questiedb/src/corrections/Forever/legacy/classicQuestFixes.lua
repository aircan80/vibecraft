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
        [452] = { -- Pyrewood Ambush
            [questKeys.objectives] = {nil, nil, nil, nil, {{{2060, 2061, 2062, 2063, 2064, 2065, 2066, 2067, 2068}, 2060}}},
        },
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
        [1136] = { -- Frostmaw
            [questKeys.extraObjectives] = {{nil, Questie.ICON_TYPE_OBJECT, l10n("Use a Fresh Carcass at the Flame of Uzel"), 0, {{"object", 1770}}}},
        },
        [5561] = { -- Kodo Roundup
            [questKeys.objectives] = {nil, nil, nil, nil, {{{4700, 4701, 4702}, 4700, nil, Questie.ICON_TYPE_INTERACT}}},
            [questKeys.extraObjectives] = {{{[zoneIDs.DESOLACE] = {{60.58, 62}}}, Questie.ICON_TYPE_EVENT, l10n("Lure the Kodos to Smeed Scrabblescrew.")}},
        },
        [8121] = { -- Take Four Bases
            [questKeys.requiredMinRep] = {510, 3000},
            [questKeys.triggerEnd] = {"Hold Four Bases.", {
                [zoneIDs.THUNDER_BLUFF] = {{40.4, 51.57}},
                [zoneIDs.ARATHI_HIGHLANDS] = {{73.72, 29.52}},
                [zoneIDs.ORGRIMMAR] = {{50.1, 69.03}},
                [zoneIDs.SILVERPINE_FOREST] = {{39.68, 17.75}}
            },
            },
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
