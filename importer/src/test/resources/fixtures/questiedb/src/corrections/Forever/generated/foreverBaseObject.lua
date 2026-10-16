-- Generated delta-base. Apply after inherited Static Corrections, before authored Forever Static Corrections.
-- Import provenance and refresh policy: docs/forever-delta-base.md.
local ForeverBaseObject = QuestieLoader:CreateModule("ForeverBaseObject")
local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")
function ForeverBaseObject:Load()
    local objectKeys = QuestieDB.objectKeys
    local zoneIDs = ZoneDB.zoneIDs
    return {
        [175740] = { -- The Last Guardian : https://wowhead.com/forever/object=175740/the-last-guardian
            [objectKeys.name] = "The Last Guardian",
            [objectKeys.spawns] = {[38] = {{37.2, 47}}, [40] = {{52.4, 53.1}, {52.5, 53.1}}},
        },
        [375548] = { -- Unlit Torch : https://wowhead.com/forever/object=375548/unlit-torch
            [objectKeys.name] = "Unlit Torch",
            [objectKeys.zoneID] = zoneIDs.ASHENVALE,
        },
    }
end
return ForeverBaseObject
