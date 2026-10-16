-- AUTO GENERATED FILE! DO NOT EDIT!

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB");

QuestieDB.objectKeys = {
    ['name'] = 1, -- string
    ['questStarts'] = 2, -- table {questID(int),...}
    ['questEnds'] = 3, -- table {questID(int),...}
    ['spawns'] = 4, -- table {[zoneID(int)] = {coordPair(floatVector2D),...},...}
    ['zoneID'] = 5, -- guess as to where this object is most common
    ['factionID'] = 6, -- faction restriction mask (same as spawndb factionid)
    ['waypoints'] = 7, -- waypoints for objects on ships/zeppelins/etc
}

QuestieDB.objectData = [[return {
[1560] = {"Storage Chest",nil,nil,{[12]={{44.3,65.82}}},12},
[2843] = {"Battered Chest",nil,nil,{[1]={{30.74,80.39},{29.24,81.16},{20.66,76.74},{29.29,81.05},{22.61,80.11},{22.62,80.51},{26.39,79.7},{26.19,79.6},{30.61,79.43},{29.23,80.64}},[85]={{34.22,64.3},{36.51,62.2},{23.6,58.28},{24.71,59.78},{31.32,62.16},{37.35,68.01},{26.11,60.35}},[12]={{57.59,48.45},{49.97,26.66},{49.31,29.13},{52.99,47.5},{52.36,51.89},{56.89,43.89},{49.13,28.23}},[141]={{55.71,27.19},{56.41,26.18},{58.25,27.11},{57.29,30.01},{56.04,46.24},{54.63,44.29},{54.1,39.66},{54.01,39.09}}},1},
}]]
