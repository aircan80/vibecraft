-- AUTO GENERATED FILE! DO NOT EDIT!

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB");

QuestieDB.npcKeys = {
    ['name'] = 1, -- string
    ['minLevelHealth'] = 2, -- int
    ['maxLevelHealth'] = 3, -- int
    ['minLevel'] = 4, -- int
    ['maxLevel'] = 5, -- int
    ['rank'] = 6, -- int, see https://github.com/cmangos/issues/wiki/creature_template#rank
    ['spawns'] = 7, -- table {[zoneID(int)] = {coordPair(floatVector2D),...},...}
    ['waypoints'] = 8, -- table {[zoneID(int)] = {coordPair(floatVector2D),...},...}
    ['zoneID'] = 9, -- guess as to where this NPC is most common
    ['questStarts'] = 10, -- table {questID(int),...}
    ['questEnds'] = 11, -- table {questID(int),...}
    ['factionID'] = 12, -- int, see https://github.com/cmangos/issues/wiki/FactionTemplate.dbc
    ['friendlyToFaction'] = 13, -- string, Contains "A" and/or "H" depending on NPC being friendly towards those factions. nil if hostile to both.
    ['subName'] = 14, -- string, The title or function of the NPC, e.g. "Weapon Vendor"
    ['npcFlags'] = 15, -- int, Bitmask containing various flags about the NPCs function (Vendor, Trainer, Flight Master, etc.).
                       -- For flag values see https://github.com/cmangos/mangos-classic/blob/172c005b0a69e342e908f4589b24a6f18246c95e/src/game/Entities/Unit.h#L536
}

QuestieDB.npcData = [[return {
[448] = {'Hogger',666,666,11,11,1,{[12]={{25.02,92.9},{26.83,86.69},{26.43,93.65},{27.36,92.71},{25.78,89.82}}},nil,12,nil,nil,20,nil,nil,0},
[639] = {'Edwin VanCleef',4168,4168,21,21,2,{[1581]={{-1,-1}}},nil,1581,nil,nil,17,nil,"Defias Kingpin",0},
[1259] = {'Gobbler',573,573,22,22,0,{[11]={{18.07,39.84}}},nil,11,nil,nil,18,nil,nil,0},
[11502] = {'Ragnaros',1099230,1099230,63,63,3,nil,nil,0,nil,nil,1081,nil,nil,0},
[11518] = {'Jergosh the Invoker',1382,1382,16,16,1,{[2437]={{-1,-1}}},nil,2437,nil,nil,554,nil,nil,0},
[11520] = {'Taragaman the Hungerer',1869,1869,16,16,1,{[2437]={{-1,-1}}},nil,2437,nil,nil,554,nil,nil,0},
[12118] = {'Lucifron',351780,351780,63,63,3,{[2717]={{-1,-1}}},nil,2717,nil,nil,54,nil,nil,0},
}]]
