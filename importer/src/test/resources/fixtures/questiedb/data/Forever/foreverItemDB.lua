-- AUTO GENERATED FILE! DO NOT EDIT!

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB");

QuestieDB.itemKeys = {
    ['name'] = 1, -- string
    ['npcDrops'] = 2, -- table or nil, NPC IDs
    ['objectDrops'] = 3, -- table or nil, object IDs
    ['itemDrops'] = 4, -- table or nil, item IDs
    ['startQuest'] = 5, -- int or nil, ID of the quest started by this item
    ['questRewards'] = 6, -- table or nil, quest IDs
    ['flags'] = 7, -- int or nil, see: https://github.com/cmangos/issues/wiki/Item_template#flags
    ['foodType'] = 8, -- int or nil, see https://github.com/cmangos/issues/wiki/Item_template#foodtype
    ['itemLevel'] = 9, -- int, the level of this item
    ['requiredLevel'] = 10, -- int, the level required to equip/use this item
    ['ammoType'] = 11, -- int,
    ['class'] = 12, -- int,
    ['subClass'] = 13, -- int,
    ['vendors'] = 14, -- table or nil, NPC IDs
    ['relatedQuests'] = 15, -- table or nil, IDs of quests that are related to this item
}

QuestieDB.itemData = [[return {
[25] = {'Worn Shortsword',nil,nil,nil,nil,nil,nil,nil,2,1,0,2,7},
[730] = {'Murloc Eye',{46,126,127,171,285,391,422,456,458,513,515,517,519,520,544,545,548,578,645,732,735,1024,1025,1026,1027,1028,1029,1083,1259,1418,1767,1768,1908,1909,1957,1958,2201,2202,2203,2204,2205,2206,2207,2208,3654,3737,3739,3740,3742,4818,4819,4820,6243,7015,10323,10643,14270},nil,nil,nil,nil,nil,nil,11,0,0,7,0},
[2874] = {'An Unsent Letter',{639},nil,nil,nil,nil,2048,nil,16,16,0,12,0},
[5191] = {'Cruel Barb',{639},nil,nil,nil,nil,nil,nil,24,19,0,2,7},
[14145] = {'Cursed Felblade',{11520},nil,nil,nil,nil,nil,nil,18,13,0,2,7},
[14148] = {'Crystalline Cuffs',{11520},nil,nil,nil,nil,nil,nil,18,13,0,4,1},
[14149] = {'Subterranean Cape',{11520},nil,nil,nil,nil,nil,nil,18,13,0,4,1},
[16800] = {'Arcanist Boots',nil,nil,nil,nil,nil,nil,nil,66,60,0,4,1},
[19019] = {'Thunderfury, Blessed Blade of the Windseeker',nil,nil,nil,nil,{7787},nil,nil,80,60,0,2,7},
}]]
