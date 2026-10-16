-- Generated delta-base. Apply after inherited Static Corrections, before authored Forever Static Corrections.
-- Import provenance and refresh policy: docs/forever-delta-base.md.
local ForeverBaseNpc = QuestieLoader:CreateModule("ForeverBaseNpc")
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
function ForeverBaseNpc:Load()
    local npcKeys = QuestieDB.npcKeys
    local zoneIDs = ZoneDB.zoneIDs
    return {
        [20735] = { -- Archmage Lan'dalock : https://wowhead.com/forever/npc=20735/archmage-landalock
            [npcKeys.name] = "Archmage Lan'dalock",
            [npcKeys.spawns] = {[36] = {{15.4, 74}, {15.6, 73.6}}},
            [npcKeys.zoneID] = zoneIDs.ALTERAC_MOUNTAINS,
        },
        [28686] = { -- Caliel Brightwillow : https://wowhead.com/forever/npc=28686/caliel-brightwillow
            [npcKeys.name] = "Caliel Brightwillow",
            [npcKeys.minLevel] = 35,
            [npcKeys.maxLevel] = 35,
            [npcKeys.spawns] = {[36] = {{11.2, 66}}},
            [npcKeys.zoneID] = zoneIDs.ALTERAC_MOUNTAINS,
            [npcKeys.subName] = "Assistant Innkeeper",
        },
    }
end
return ForeverBaseNpc
