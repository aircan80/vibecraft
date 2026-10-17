-- AUTO GENERATED FILE! DO NOT EDIT!

---@type QuestieDB
local QuestieDB = QuestieLoader:ImportModule("QuestieDB");

QuestieDB.questKeys = {
    ['name'] = 1, -- string
    ['startedBy'] = 2, -- table
        --['creatureStart'] = 1, -- table {creature(int),...}
        --['objectStart'] = 2, -- table {object(int),...}
        --['itemStart'] = 3, -- table {item(int),...}
    ['finishedBy'] = 3, -- table
        --['creatureEnd'] = 1, -- table {creature(int),...}
        --['objectEnd'] = 2, -- table {object(int),...}
    ['requiredLevel'] = 4, -- int
    ['questLevel'] = 5, -- int
    ['requiredRaces'] = 6, -- bitmask
    ['requiredClasses'] = 7, -- bitmask
    ['objectivesText'] = 8, -- table: {string,...}, Description of the quest. Auto-complete if nil.
    ['triggerEnd'] = 9, -- table: {text, {[zoneID] = {coordPair,...},...}}
    ['objectives'] = 10, -- table
        --['creatureObjective'] = 1, -- table {{creature(int), text(string), iconFile},...}, If text is nil the default "<Name> slain x/y" is used
        --['objectObjective'] = 2, -- table {{object(int), text(string), iconFile},...}
        --['itemObjective'] = 3, -- table {{item(int), text(string), iconFile},...}
        --['reputationObjective'] = 4, -- table: {faction(int), value(int)}
        --['killCreditObjective'] = 5, -- table: {{{creature(int), ...}, baseCreatureID, baseCreatureText, iconFile}, ...}
        --['spellObjective'] = 6, -- table: {{spell(int), text(string), item(int)},...}
    ['sourceItemId'] = 11, -- int, item provided by quest starter
    ['preQuestGroup'] = 12, -- table: {quest(int)}
    ['preQuestSingle'] = 13, -- table: {quest(int)}
    ['childQuests'] = 14, -- table: {quest(int)}
    ['inGroupWith'] = 15, -- table: {quest(int)}
    ['exclusiveTo'] = 16, -- table: {quest(int)}
    ['zoneOrSort'] = 17, -- int, >0: AreaTable.dbc ID; <0: QuestSort.dbc ID
    ['requiredSkill'] = 18, -- table: {skill(int), value(int)}
    ['requiredMinRep'] = 19, -- table: {faction(int), value(int)}
    ['requiredMaxRep'] = 20, -- table: {faction(int), value(int)}
    ['requiredSourceItems'] = 21, -- table: {item(int), ...} Items that are not an objective but still needed for the quest.
    ['nextQuestInChain'] = 22, -- int: if this quest is active/finished, the current quest is not available anymore
    ['questFlags'] = 23, -- bitmask: see https://github.com/cmangos/issues/wiki/Quest_template#questflags
    ['specialFlags'] = 24, -- bitmask: 1 = Repeatable, 2 = Needs event, 4 = Monthly reset (req. 1). See https://github.com/cmangos/issues/wiki/Quest_template#specialflags
    ['parentQuest'] = 25, -- int, the ID of the parent quest that needs to be active for the current one to be available. See also 'childQuests' (field 14)
    ['reputationReward'] = 26, -- table: {{faction(int), value(int)},...}, a list of reputation rewarded upon quest completion
    ['breadcrumbForQuestId'] = 27, -- int: quest ID for the quest this optional breadcrumb quest leads to
    ['breadcrumbs'] = 28, -- table: {questID(int), ...} quest IDs of the breadcrumbs that lead to this quest
    ['extraObjectives'] = 29, -- table: {{spawnlist, iconFile, text, objectiveIndex (optional), {{dbReferenceType, id}, ...} (optional)},...}, a list of hidden special objectives for a quest. Similar to requiredSourceItems
    ['requiredSpell'] = 30, -- int: quest is only available if character has this spellID
    ['requiredSpecialization'] = 31, -- int: quest is only available if character meets the spec requirements. Use QuestieProfessions.specializationKeys for having a spec, or QuestieProfessions.professionKeys to indicate having the profession with no spec. See QuestieProfessions.lua for more info.
    ['requiredMaxLevel'] = 32, -- int: the maximum level at which the quest is still available
    ['availableUntilCompleted'] = 33, -- int: the current quest is available until this quest is turned in
    ['availableStartingWith'] = 34, -- int: the ID of the quest that needs to be in quest log OR turned in for the current one to be available.
    ['requiredRanks'] = 35, -- table: {{skill(int), value(int)}}. Table of professions and ranks to be checked with OR logic
    ['disabledByQuest'] = 36, -- int: quest that, if in player's quest log, makes current quest unavailable for the duration
}

QuestieDB.questData = [[return {
[2] = {"Sharptalon's Claw",{nil,nil,{16305}},{{12696}},20,30,8589934770,nil,{"Bring Sharptalon's Claw to Senani Thunderheart at Splintertree Post, Ashenvale."},nil,nil,16305,nil,{6383},nil,{23,24},nil,331,nil,nil,nil,nil,nil,nil,nil,nil,{{81,250}}},
[136] = {"Captain Sander's Hidden Treasure",{nil,nil,{1357}},{nil,{35}},10,16,0,nil,{"Find Captain Sanders' footlocker and search it for the next clue."},nil,nil,nil,nil,nil,nil,nil,nil,40,nil,nil,nil,nil,138},
[176] = {"Wanted:  \"Hogger\"",{nil,{68,156561}},{{240}},5,11,4294967373,nil,{"Slay the gnoll Hogger and bring his Huge Gnoll Claw to Marshal Dughan."},nil,{nil,nil,{{1931}}},nil,nil,nil,nil,nil,nil,12,nil,nil,nil,nil,nil,8,nil,nil,{{72,250}}},
[247] = {"The Hunt Completed",{{12696}},{{12696}},20,30,8589934770,nil,nil,nil,nil,nil,{2,23,24},nil,nil,nil,nil,331,nil,nil,nil,nil,nil,8,nil,nil,{{81,350}}},
[452] = {"Pyrewood Ambush",{{2058}},{{2058}},12,15,8589934770,nil,{"Help Deathstalker Faerleia kill the Pyrewood Council."},nil,nil,nil,nil,nil,nil,nil,nil,130,nil,nil,nil,nil,nil,2,2,nil,{{68,350}}},
[532] = {"Battle of Hillsbrad",{{2215}},{{2215}},19,26,8589934770,nil,{"Kill Magistrate Burnside and 4 Hillsbrad Councilmen. Destroy the Hillsbrad Proclamation. Steal the Hillsbrad Town Registry. Report back to Darthalia in Tarren Mill afterwards."},nil,{{{2335},{2387}},{{1761,"Hillsbrad Proclamation destroyed"}},{{3657}}},nil,nil,{529},nil,nil,nil,267,nil,nil,nil,nil,539,8,nil,nil,{{68,250}}},
[595] = {"The Bloodsail Buccaneers",{{2490}},{nil,{2083}},37,41,0,nil,{"First Mate Crazz wants you to check the cove north of Booty Bay."},nil,nil,nil,nil,nil,nil,nil,nil,33,nil,nil,nil,nil,597,8,nil,nil,{{21,150},{87,-150}}},
[597] = {"The Bloodsail Buccaneers",{nil,{2083}},{{2490}},37,41,0,nil,{"Report your findings to First Mate Crazz in Booty Bay."},nil,nil,nil,nil,{595},nil,nil,nil,33,nil,nil,nil,nil,599,8,nil,nil,{{21,150},{87,-150}}},
[599] = {"The Bloodsail Buccaneers",{{2490}},{{2487}},37,41,0,nil,{"Report the planned Bloodsail attack to Fleet Master Seahorn."},nil,nil,nil,nil,{597},nil,nil,nil,33,nil,nil,nil,nil,604,8,nil,nil,{{21,25},{87,-25}}},
[1136] = {"Frostmaw",{{3441}},{{3441}},26,37,8589934770,nil,{"Bring Frostmaw's Mane to Melor Stonehoof in Thunder Bluff."},nil,{nil,nil,{{5811}}},5838,nil,{1131},nil,nil,nil,1638,nil,nil,nil,{5810},nil,nil,nil,nil,{{81,500}}},
[3644] = {"Membership Card Renewal",{{8126}},{{8126}},30,47,0,nil,nil,nil,nil,nil,nil,{3639},nil,nil,nil,-201,{202,200},nil,nil,nil,nil,8,1},
[5142] = {"Little Pamela",{{10927}},{{10926}},50,55,0,nil,{"Find Pamela Redpath."},nil,nil,nil,nil,nil,nil,nil,{5601},139,nil,nil,nil,nil,5149,8},
[5149] = {"Pamela's Doll",{{10926}},{{10926}},50,55,0,nil,{"Find Pamela's doll."},nil,{nil,nil,{{12885}}},nil,nil,{5142,5601},nil,nil,nil,139,nil,nil,nil,{12886,12887,12888},nil,8},
[5561] = {"Kodo Roundup",{{11596}},{{11596}},30,34,0,nil,{"Bring five Tamed Kodos and the Kodo Kombobulator back to Smeed Scrabblescrew at Scrabblescrew's Camp."},nil,{{{11627,"Kodos Tamed"}}},13892,nil,nil,nil,nil,nil,405,nil,nil,nil,nil,nil,nil,nil,nil,{{470,150}}},
[5601] = {"Sister Pamela",{{11629}},{{10926}},50,55,0,nil,{"Find Pamela Redpath in Darrowshire."},nil,nil,nil,nil,nil,nil,nil,{5142},139,nil,nil,nil,nil,5149,8},
[8121] = {"Take Four Bases",{{15021}},{{15021}},60,60,8589934770,nil,{"Hold four bases at the same time in Arathi Basin, and then return to Deathmaster Dwire in Hammerfall."},nil,nil,nil,nil,nil,nil,nil,nil,3358,nil,{510,3000},nil,nil,nil,10,2,nil,{{510,1000}}},
[8485] = {"The Brokering of Peace",{{11555}},{{4949}},45,60,8589934770,nil,{"Bring the Timbermaw Offering of Peace to Thrall in Orgrimmar."},nil,{nil,nil,nil,{576,42000}},21155,nil,nil,nil,nil,nil,1769,nil,{576,42000}},
[9422] = {"Scouring the Desert",{{17079}},{{17079}},55,60,8589934770,nil,{"Find and deliver Silithyst to the Silithyst Extractor then speak with General Kirika at the Horde encampment near Hive'Regal."},nil,{{{18199,"Deliver Silithyst"}}},nil,nil,nil,nil,nil,nil,1377,nil,nil,nil,nil,nil,72,nil,nil,{{609,250}}},
}]]
