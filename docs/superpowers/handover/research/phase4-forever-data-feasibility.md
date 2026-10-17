# Phase 4: what build 1.60.1.70425 actually ships (datamining feasibility, 2026-10-17)

**Tags.** **[W]** verified on wago (live wago.tools `/db2/{Table}?build=1.60.1.70425` on 2026-10-17, or our local cache of wago's CSV export for this build). **[E]** documented elsewhere. **[N]** not found. *(reading)* marks my interpretation of [W] data.

**Method.** wago lists **617 tables** for 1.60.1.70425, against 422 for Era 1.15.9.70003 and 1,106 for mainline 12.1.5.70448 [W]. I diffed the lists, pulled row totals for about 60 tables on both builds, and joined small CSVs in the scratchpad.

## Headline findings

1. **Talents run on the mainline Trait system, not on Talent/TalentTab [W].** `Talent` (432) and `TalentTab` (27) are Era leftovers with `ClassID` zeroed in every row, so vestigial *(reading)*. The live talents are `TraitSystem` 10, with currency `TraitCurrency` 3820 (`SourcedMax` 51 = one point per level 10–60, from 56 level rows in `TraitCurrencySource`). Nine class trees of 50–55 nodes each are linked to classes through `SkillLineXTraitTree`. New talents resolve by name through `TraitDefinition`→`SpellName`, e.g. Missile Barrage, Lava Burst, Riptide, Penance, Mutilate, Shifting Power, Spearing Strike, Vanguard, Conflagrate, Summon Hawk and Flawless Execution [W].
2. **Legacy points are a second Trait system [W].** `TraitSystem` 45 owns trees 1187, 1188 and 1189 (9 nodes each), plus an empty tree 1118. Its currency, `TraitCurrency` 4225, has `SourcedMax` 16, which matches the 16-per-character cap. `TraitCost` 4845 is named "1 Legacy Point". Perks (Well Rested, Field Guide, Reagent Economy, Permanence) are `TraitDefinition` rows [W].
3. **Legacy challenges are Achievements [W].** `TraitCurrencySource` has 131 rows for currency 4225, each with an `AchievementID`, and all 131 resolve in `Achievement`. They split by `SuperDistrictSetID` 60/61 (66/65 rows), likely one set per ruleset family *(reading)*. `Achievement` grew from 34 rows in Era to 441 [W]. The reward track is `RenownRewards` with `CovenantID` 48: rank 25 Spectral Bear Cub, 40 Spectral Bear Tabard, 55 Reins of the Spectral Bear, plus `Faction` 2802 "Legacy Track" and `CurrencyTypes` 3485 "Renown - Legacy Rank" [W].
4. **Camping lives in Spell/SpellEffect plus items, not in GameObjects [W].** 36 named summon-object spells sit in 1307172–1307397, e.g. Basic/Journeyman/Expert Campfire, Camp Tent, Sharpening Wheel, Incense Candle, Tanning Rack (`SpellEffect.Effect` 76, `EffectMiscValue_0` = GameObject entry). The summoned entries (612xxx/651xxx) are **not** in `GameObjects` (1,526 rows, world spawns only), so object names and buff links beyond the spell are server-side [W]. 56 items place them; 29 "Blueprint: X" items carry `RequiredSkill`/`RequiredSkillRank` (Blueprint: Fermenter = Alchemy 140); 17 of the 56 are hidden [W]. `SkillLineAbility` has no camp-spell rows [W].
5. **Skyborne is fully in the client [W].** It ships as `ChrRaces` 95 "High Order Skyborne" and 96 "Windshaper Skyborne". `CharBaseInfo` gives 95 Warrior/Hunter/Rogue/Mage/Druid and 96 Warrior/Hunter/Rogue/Shaman/Druid. `ChrRaceRacialAbility` (new, 4 rows per playable race) names Walk on Air, Read Leyline, Skysight, Wind Blessed and Elemental Insight. `CharBaseInfo` also has the six new combos (Undead Paladin, Human Hunter, Dwarf Shaman, Gnome Priest, Troll Warlock, Orc Mage) [W]. Most of the 58 `ChrRaces` rows are unplayable mainline rows, so filter on `CharBaseInfo` *(reading)*.
6. **Dungeons: beta content present, launch content mostly absent, and there is no journal [W].** `JournalInstance`/`JournalEncounter` **do not exist** in this build or in Era [N]. `Map` has 15 new rows, e.g. City of Dalaran (2959), Ruins of Lordaeron (2999), Excavation Site: Wetlands (2998), The Hall of Thanes (3065), Zephras Isle, Hyjal Crater, Darkspear Islands. `LFGDungeons` adds only those four dungeons; `DungeonEncounter` adds 35 bosses (Dalaran 9, Ruins 7, Excavation 4, Thanes 4, map 3002 2, reworked Sunken Temple 8, Deadmines 1) [W]. Drowned City, Krol'dok, Alcaz, Blackmaw, Shaper's Terrace, Barrow Deeps and Hyjal Summit have **no** `Map`, `LFGDungeons` or `DungeonEncounter` rows [N]. They leak through **boss-kill statistics** in `Achievement` (category 14821), whose `Instance_ID` points to map IDs with no `Map` row: Hyjal Summit 2981 (The Wild King), Alcaz Prison 2994 (Blazeroar), Shaper's Terrace 3001 (Nanaya, Cinder, Bolt, Snowtalon) and Barrow Deeps 3052 (Sonya Darkhallow) [W].
7. **Other systems [W].** `TransmogSet`/`TransmogSetItem` are new (7/63 rows, Shen'dorei garbs). `ItemSet` adds 55 sets, including 2133 Rotmender's Raiment and 2135 Krol'dok Battlegear. `Faction` adds 44 (e.g. Darkspear Raiders, Guardians of Hyjal, Kirin Tor). `TaxiNodes` adds 13. `QuestV2` is ID-only (about 1,800 new IDs). `QuestLine`, `QuestLineXQuest` and `QuestPOI*` are new but tiny. `CreatureFamily` adds 319 Fox; `Creature` (180 rows) is vanity pets, not mobs.

## New stat types (merged hit/crit) [W]

Stats are `StatModifier_bonusStat` + `StatPercentEditor` budget shares (no flat amounts, damage or resistance columns), as the importer already assumes. WoWDBDefs' `ItemStatType` enum on wago names every id. **31 HIT_RATING (184 items) and 32 CRIT_RATING (521 items) are the merged hit and crit.** The split ids 16–21 appear 0 times. 37 EXPERTISE_RATING (8 items) is the likely "dodge/parry reduction" *(reading)*. Former equip spells became stat ids: 83–89 school damage, 90–105 weapon skills, 106–118 profession skills, 119–123 penetration, 124 resist-all, 125–140 power vs creature type (e.g. 128 on Gauntlets of Undead Slaying); 51–56 resistances. Era uses only 3–7 [W]. The importer's stat-name map needs ids 31–140.

## Obfuscated items: what they look like [W]

- **Rows are missing, not zeroed and not flagged.** `Item` has 31,852 rows and `ItemSparse` 19,234. **12,618 items have an `Item` row but no `ItemSparse` row** [W]. foreverchanges.pro reports the same 12,618 for 70425, 2,433 of them new, which matches my count of hidden items absent from Era [E][W]. The other 10,185 have an Era row in our `.era` fallback [W].
- **What survives:** the `Item` row (class, subclass, slot, mostly the icon), effects (2,395 hidden items), appearances (10,089) and set membership (2,246 of 3,069 `ItemSet` members are hidden) [W]. Name, quality, item level and stats are gone [W][E].
- **Samples.** Rotmender's Raiment (set 2133) has items 286978, 286980, 271207, 271214 and 286979. Each has an `Item` row (cloth, InventoryType 5/6/7/8/10, **IconFileDataID 0**) and no `ItemSparse` row on wago (ID=286978 returns 0) or in Era [W]. Worgenbane Talisman is very likely item 273643: a trinket (`InventoryType` 12, icon 133439) whose `ItemXItemEffect` points to spell 1292252 "Worgenbane" and has no `ItemSparse` row [W]; the match is *(reading)*, as the name is not in the client.
- **How items get revealed:** the server sends the row as a hotfix when someone sees the item. The client caches it in `DBCache.bin`, and Raidbots, foreverchanges and wowsims/gamedata aggregate those caches [E]. Separately, 66 items, 90 quests and 872 spells sit in encrypted sections [E].

## Implications for sizing

Data present: Legacy calculator, challenge checklist and reward track; talent calculator from Trait*; race/class grid; camp matrix (spells + Blueprint skill ranks); set browser; new-stat filters; beta dungeon boss lists. Partial: obfuscated items (slot, icon, set, effect spell), launch instances (names and a few bosses), quests (IDs only). Absent: boss loot, quest objectives, Encounter Journal. A hotfix/`DBCache.bin` ingest is the main lever for hidden items [E].

## Table-by-table matrix

| Area | Table | In 70425 | Rows 70425 / Era 1.15.9 | Key columns to read | Tag |
|---|---|---|---|---|---|
| Legacy | TraitSystem | yes (new) | 2 / n.a. | ID (10 class, 45 Legacy) | [W] |
| Legacy/Talents | TraitTree | yes | 17 / 1 | ID, TraitSystemID, FirstTraitNodeID | [W] |
| Legacy/Talents | TraitNode | yes | 558 / 9 | ID, TraitTreeID, PosX, PosY, Type, Flags | [W] |
| Legacy/Talents | TraitNodeXTraitNodeEntry | yes | 561 / – | TraitNodeID, TraitNodeEntryID, _Index | [W] |
| Legacy/Talents | TraitNodeEntry | yes | 659 / 9 | ID, TraitDefinitionID, MaxRanks, NodeEntryType | [W] |
| Legacy/Talents | TraitDefinition | yes | 659 / 9 | ID, SpellID, OverrideName/Description_lang, OverrideIcon | [W] |
| Legacy/Talents | TraitEdge | yes (new) | 99 / – | LeftTraitNodeID, RightTraitNodeID, Type | [W] |
| Legacy/Talents | TraitCurrency | yes (new) | 2 / – | ID (3820 talent, 4225 Legacy), SourcedMax | [W] |
| Legacy/Talents | TraitCurrencySource | yes (new) | 187 / – | TraitCurrencyID, AchievementID, PlayerLevel, SuperDistrictSetID, Requirement_lang | [W] |
| Legacy/Talents | TraitCost, TraitCond | yes (new) | 11, 172 / – | Amount, TraitCurrencyID; cond rules | [W] |
| Talents | SkillLineXTraitTree | yes (new) | 9 / – | SkillLineID, TraitTreeID | [W] |
| Talents | Talent / TalentTab | yes, vestigial | 432/432, 27/27 | (ClassID zeroed; do not use) | [W] |
| Legacy | Achievement | yes | 441 / 34 | ID, Title/Description/Reward_lang, Category, Instance_ID, Criteria_tree, IconFileID | [W] |
| Legacy | Criteria / CriteriaTree | yes | 3,297 / 215; 6,771 / 360 | Type, Asset; Parent, Amount | [W] |
| Legacy | Achievement_Category | yes | 56 | ID, Name_lang, Parent | [W] |
| Legacy | RenownRewards | yes (new) | 22 / – | CovenantID (48 Legacy), Level, ItemID, Name_lang | [W] |
| Legacy | CurrencyTypes | yes (new) | 9 / – | ID, Name_lang, MaxQty | [W] |
| Camping | Spell / SpellEffect | yes | 37,084 / 42,698 (both 70425) | SpellID, Effect=76, EffectMiscValue_0 | [W] |
| Camping | GameObjects | yes | 1,526 / 864 | spawns only; camp entries absent | [W] |
| Camping | GameObjectDisplayInfo | yes | 3,243 / 2,256 | ID, FileDataID | [W] |
| Camping | SkillLineAbility | yes | 7,847 / 6,143 | no camp-spell rows; use Blueprint RequiredSkillRank | [W] |
| Camping | CampsiteObject (or similar) | no | – | – | [N] |
| Race | ChrRaces | yes | 58 / 9 | ID 95/96, Name_lang, ClientFileString, FactionID | [W] |
| Race | CharBaseInfo | yes | 56 / 40 | RaceID, ClassID, OtherFactionRaceID | [W] |
| Race | ChrRaceRacialAbility | yes (new) | 40 / – | ChrRacesID, Name_lang, Description_lang, Icon | [W] |
| Race | ChrRaceXChrModel | yes | 116 / 18 | ChrRacesID, ChrModelID | [W] |
| Instances | Map | yes | 73 / 59 | ID, MapName_lang, InstanceType, MaxPlayers | [W] |
| Instances | LFGDungeons | yes | 71 / 67 | ID, Name_lang, MapID (0 for new four) | [W] |
| Instances | DungeonEncounter | yes | 342 / 307 | Name_lang, MapID, OrderIndex | [W] |
| Instances | AreaTable | yes | 1,375 / 1,212 | ID, AreaName_lang, ParentAreaID, ContinentID | [W] |
| Instances | JournalInstance / JournalEncounter | no | – | – | [N] |
| Items | ItemSet | yes | 536 / 481 | Name_lang, ItemID_0..16 | [W] |
| Items | ItemSetSpell | yes | 1,477 / 1,275 | ItemSetID, SpellID, Threshold | [W] |
| Items | ItemSparse | yes | 19,234 / 24,442 | StatModifier_bonusStat_n, StatPercentEditor_n, ItemLevel | [W] |
| Items | Item | yes | 31,852 / 24,973 | ClassID, SubclassID, InventoryType, IconFileDataID | [W] |
| Transmog | ItemModifiedAppearance | yes | 16,792 / 13,346 | ItemID, ItemAppearanceID, ItemAppearanceModifierID | [W] |
| Transmog | TransmogSet / TransmogSetItem | yes (new) | 7 / 63 | Name_lang, TransmogSetGroupID; ItemModifiedAppearanceID | [W] |
| Reputation | Faction | yes | 253 / 209 | ID, Name_lang, ReputationIndex | [W] |
| Reputation | FactionTemplate | yes | 453 / 367 | Faction, FactionGroup | [W] |
| Taxi | TaxiNodes / TaxiPath / TaxiPathNode | yes | 100/87, 328/294, 10,778/9,582 | Name_lang, ContinentID, Pos; FromTaxiNode, ToTaxiNode | [W] |
| Quests | QuestV2 | yes | 6,614 / 4,807 | ID only (names server-side) | [W] |
| Quests | QuestLine / QuestLineXQuest | yes (new) | 3 / 22 | QuestLineID, QuestID, OrderIndex | [W] |
| Quests | QuestPOIBlob / QuestPOIPoint | yes (new) | 57 / 121 | QuestID, MapID, X, Y | [W] |
| Quests | QuestSort / QuestInfo | yes | 39/36, 7/7 | SortName_lang (660 High Order, 666 Camping) | [W] |
| Pets | CreatureFamily | yes | 27 / 26 | ID 319 Fox, Name_lang | [W] |
| Pets | Creature | yes | 180 / 1 | vanity pets only, not mobs | [W] |
| Mounts | Mount | yes (new) | 141 / – | Name_lang, SourceSpellID | [W] |

## Forever-specific tables (not in Era 1.15.9) [W]

206 tables are not in Era; most are mainline imports (Trait*, Transmog*, Mount, Toy, CurrencyTypes, Profession, ItemBonus*, RenownRewards). **22 are in neither Era nor mainline 12.1.5:**

- **Realmless/ruleset:** SuperDistrictSet, SuperDistrictSetCollection, SuperDistrictSetXAvailableSD, SuperDistrictTransfer, AvailableSuperDistrict, Cfg_SuperDistrict and Cfg_DatacenterLocality. `AvailableSuperDistrict` holds the PvP, Normal and Roleplay rulesets.
- **Leveling and stats:** LevelExperience, RaceStat and PlayerExpectedStat.
- **Pets:** BattlePetXP and BattlePetTypeModifier.
- **Character models:** ChrModelAltVariant, ChrModelAltVariantChoice and ChrModelAltVariantOption.
- **Other:** AuctionHouseRedirect, TraitNodeGroupDisplayInfo, UiTextureAtlasSet, ModelProperties, FFTTile, LiquidTypeXFFTTile and LightDataGlobalVolumeFog.

Dropped from Era (11): ItemRandomProperties, ItemRandomSuffix, ScalingStatValues, CharStartOutfit, CreatureSpellData, Hotfixes, House, SpellAuraVisXTalentTab, SpellCraftUI, WorldLayerMapSet, WorldStateUI.

## Sources

- wago.tools DB2 browser and CSV export, build 1.60.1.70425: https://wago.tools/db2?build=1.60.1.70425 , e.g. https://wago.tools/db2/TraitTree?build=1.60.1.70425 , https://wago.tools/db2/ItemSparse?build=1.60.1.70425&filter[ID]=286978 (fetched 2026-10-17)
- wago.tools, Era 1.15.9.70003 and mainline 12.1.5.70448 table lists (same URL pattern)
- WoWDBDefs `ItemStatType` enum as served in wago's `dbdMeta` for ItemSparse
- Local cache `C:\Users\aircan80\.cache\wowforever-clientdata\1.60.1.70425\csv` (wago CSVs, fetched 2026-10-17T00:11Z; read only)
- https://foreverchanges.pro/hidden-items (12,618 hidden, 2,433 new, reveal via client cache)
- https://classicwowforever.com/guides/forever-beta-datamine-loot-limits/ (encrypted sections, build 69893)
- https://github.com/wowsims/gamedata/pull/12 (1.60.1 hotfix ingestion)
- https://warcraft.wiki.gg/wiki/Patch_1.60.1/API_changes/notes (no DB2 detail; checked)
- Icy Veins hidden-items article (403 to fetch; summary via search only)
