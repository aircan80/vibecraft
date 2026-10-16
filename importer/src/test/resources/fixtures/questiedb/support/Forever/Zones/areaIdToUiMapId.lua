---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")

--? Areas whose spawns Questie places on a specific UiMap. 0 means no map.
---@type table<AreaId, UiMapId>
ZoneDB.private.areaIdToUiMapIdOverride = [[return {
    [40] = 1436, -- Westfall
    [1] = 0, -- Dun Morogh (no map)
    [38] = 9999, -- Loch Modan (map without art)
}]]
