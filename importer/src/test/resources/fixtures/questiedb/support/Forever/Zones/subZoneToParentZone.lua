---@type ZoneDB
local ZoneDB = QuestieLoader:ImportModule("ZoneDB")

--? If you want to add something in subZoneToParentZone overwrite the data in this table
---@type table<AreaId, AreaId> table<SubAreaId, ParentAreaId>
ZoneDB.private.subZoneToParentZoneOverride = [[return {
    [133] = 1, -- New Tinkertown -> Dun Morogh
    [135] = 1, -- Frostmane Hold -> Dun Morogh
}]]

--! Generated table, add something manually here and i'll kill you //Logon
---@type table<AreaId, AreaId> table<SubAreaId, ParentAreaId>
ZoneDB.private.subZoneToParentZone = [[return {
  [2] = 40, -- Longshore -> Westfall
  [9] = 12, -- Northshire Valley -> Elwynn Forest
  [20] = 40, -- Moonbrook -> Westfall
  [133] = 1, -- Gnomeregan -> Dun Morogh
  [135] = 1, -- Frostmane Hold -> Dun Morogh
}]]
