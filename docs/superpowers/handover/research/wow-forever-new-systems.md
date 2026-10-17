# WoW: Forever — what is new vs Classic Era (research memo, 2026-10-17)

Tags: **[C]** confirmed by Blizzard (news, BlizzCon panel, dev interview). **[D]** datamined or fan-site reported. **[S]** speculation. Data-location notes are [S] and name the Classic Era or mainline DB2 tables to check first.

**Context [C].** Launch is 4 Nov 2026. The beta runs 17 Sep to about 22 Oct and stays capped at level 30. The level cap is 60 permanently. Play is realmless, with Normal, PvP and RP rulesets, plus Hardcore after launch.

**Two constraints that shape the whole app [C].** First, Blizzard is "obfuscating all items": stats stay hidden until an item drops on your server (Aggrend, MMORPG.com interview). Icy Veins counts about 40%, or 12,500+ items, without stats [D]. Second, there is no Dungeon Journal at launch, bosses stay secret until first encounter, and raids get no PTR. Addons follow Midnight-style restrictions. A client-datamining app will therefore miss many stats and bosses, so collected in-game data becomes the scarce asset.

## 1. Camping
**What [C].** The Cooking Fire becomes a Basic Campfire, a shared campsite. Each nearby player places one profession-crafted object, on a shared 1-hour cooldown. Players sit, become rested, and get 1-hour buffs that don't stack with class buffs, such as Sharpening Wheel (AP) or Incense Candle (Int). Tier-1 objects unlock at skill 20, and advanced ones come from Blueprints dropped by dungeon bosses. Cooks upgrade the fire to 5 or 10 slots (Blizzard deep-dive recap).
**Detail [D].** Method.gg lists 12 professions with objects at skills 20, 140 and 300, for example Camp Tent, Tanning Rack, Sewing Machine for Leatherworking. Campfire tiers need Cooking 1, 90 and 200.
**Data.** Spell and SpellEffect (summon-object effects), GameObjects, SkillLineAbility (skill thresholds), and Blueprint items in Item/ItemSparse. Which boss drops which Blueprint is server loot data that must be crowd-sourced [S].
**Ideas.** (1) A camp matrix: profession by tier to object, buff, skill and reagents. (2) A camp planner: pick party professions and show the best 3/5/10 objects and the buffs that overlap with class buffs. (3) A Blueprint tracker page per dungeon, with "source unknown" states.

## 2. Legacy points
**What [C].** Legacy Challenges award account-wide points that each character spends separately. Players can earn 65 at launch and spend at most 16 per character. The excess feeds a cosmetic Legacy Reward Track, for example the Spectral Bear Cub. The three trees are Adventure, Professions and Resourcefulness, with perks such as Well Rested and Reagent Economy. Hardcore progress flows out to other rulesets, not in.
**Detail [D].** The 65 challenges split into Classes 27 (levels 25/45/60), Tradeskills 18 (skill 150/225/300), PvP 12, Adventure 2, Dungeons 3 and Raids 3. There are 21 ranked perks, seven per tree, such as Field Guide and Permanence (Mobalytics, Method).
**Data.** Possibly mainline Trait* tables (TraitTree/TraitNode/TraitDefinition) or a new table [S]. Challenges may sit in Achievement/Criteria tables [S]. Check the build for new table names before assuming.
**Ideas.** (1) A Legacy tree calculator with a 16-point cap and shareable builds. (2) An account challenge checklist that shows which alt to level next for the most points. (3) A perk-effect explorer that links perks to the systems they change.

## 3. Talents and classes
**What [C].** The tree shape and row count are kept. A fourth milestone appears at 16 points, alongside 11, 21 and 31. Raid buffs such as Divine Spirit, Kings and Improved Mark of the Wild become baseline. Dual spec costs 10g at level 40 from trainers. Official deep dives cover all nine classes. Sample 16-point talents and headline additions:
- **Mage.** Missile Barrage, Ice Block, and the new spell Frostfire Bolt.
- **Shaman.** Call of Thunder, Stormstrike, Mana Tide, totem sets, Lava Burst and Riptide.
- **Priest.** Soul Warding, Binding Heal, Vampiric Embrace, Penance and Prayer of Mending.
- **Warrior.** Spearing Strike, Raging Blows and Vanguard.
- **Hunter.** Summon Hawk, Counterattack, a new fox pet family, and one unique ability per family.
- **Druid.** Shifting Power, Primal Bite, Swiftmend and Insect Swarm.
- **Rogue.** Cold Blood, Flawless Execution, Premeditation, Mutilate, and axes.
- **Warlock.** Curse of Exhaustion, Fel Domination, Conflagrate, and Banes as a new category.
- **Paladin.** Holy Strike, Seal of Fury, and Judgment that no longer consumes Seals.

Wowhead's talent calculators come from BlizzCon testing, not the client [D].
**Data.** Talent, TalentTab, Spell*, SkillLineAbility (baseline spells by level), and CreatureFamily (pets).
**Ideas.** (1) A Classic-vs-Forever talent diff view per tree, showing new, moved, baseline and removed talents. (2) A spell-by-level "what you learn when" page per class. (3) A dual-spec comparison with a printable build.

## 4. Races
**What [C].** The Skyborne (shen'dorei) are Highborne exiles from Skywall who choose a faction. Horde Windshapers can be Druid, Hunter, Rogue, Shaman or Warrior. Alliance High Order swap Shaman for Mage. They start on Zephras Isle (1–12). Their racials are Walk on Air, Wind Blessed (+1% haste), Elemental Insight, and Skysight or Read Ley Line by faction. The race needs a paid pack [D, Zockify]. Every race now has 2 active and 2 passive racials. Six combos are new: Gnome Priest, Human Hunter, Dwarf Shaman, Orc Mage, Troll Warlock and Undead Paladin.
**Data.** ChrRaces, ChrClasses, CharBaseInfo (race/class matrix), racial spells via SkillLineAbility, and the starting map in AreaTable/UiMap.
**Ideas.** (1) A race/class availability grid that highlights new combos. (2) A racial comparison sheet showing Classic vs Forever per race. (3) A Zephras Isle zone page.

## 5. Zones
**What.**
- **Zephras Isle (1–12) [C].**
- **Riverglades/"Riverlands" [C].** An Eastern Kingdoms east-coast zone at about 30–45 with 150+ quests.
- **Mount Hyjal, renewed [C].** A post-Archimonde endgame zone with unnamed reputations, entered via Darkwhisper Gorge.
- **Shen'Dralas [D].** Between Desolace and Mulgore, levels unknown.
- **Dalaran [C].** Returns as a city, added to the beta on 8 Oct.
- **More [C].** New areas inside existing zones, and another zone or area in spring 2027.

**Data.** AreaTable (subzones and level ranges), UiMap/UiMapAssignment (map coords), Map, WorldMapOverlay, and AreaPOI.
**Ideas.** (1) A "new in Forever" map overlay that flags new subzones on Classic maps. (2) Zone pages with level range, quest count and dungeon entrances. (3) A diff of AreaTable between Classic Era and Forever.

## 6. Dungeons and raids
**Dungeons [C], nine in total.**

| Dungeon | Location | Levels |
|---|---|---|
| Hall of Thanes | Ironforge | 13–18 |
| Ruins of Lordaeron | Tirisfal | 15–20 |
| Excavation Site | Wetlands | 24–29; beta lists 26–31 |
| City of Dalaran | Alterac | 28–33 |
| Drowned City | Stranglethorn Vale | 35–40 |
| Krol'dok Stronghold | Riverglades | 40–45 |
| Alcaz Island Prison | | 48–53 |
| Blackmaw Hold | Azshara | 55–60 |
| Shaper's Terrace | Un'Goro | 58–60 |

Bosses are not announced.

**Raids [C].** Barrow Deeps (10-player) and Hyjal Summit (20-player) unlock with Onyxia's Lair on 9 Dec 2026. Two more new raids come in spring 2027, and a revamped "iconic" raid plus another new raid in summer 2027.
**Bosses [D].** Third-party sites list 13 Hyjal Summit bosses from client data, such as The Wild King. This list is low confidence and unverified.
**Data.** Map, LFGDungeons, AreaTable, and JournalInstance/JournalEncounter, which are likely empty at launch [S]. Boss loot is server-side.
**Ideas.** (1) Instance pages with a "discovered" state that fill in as bosses and loot are confirmed. (2) A dungeon-by-level ladder that merges Classic and Forever dungeons. (3) An unlock calendar for the roadmap.

## 7. Gear and items
**What.**
- **[C]** All dungeon loot was reworked, with hundreds of new dungeon and rare-mob drops. Unique boss items become blue.
- **[C]** Trinkets gain situational effects by environment or creature type, for example Worgenbane Talisman.
- **[C]** Hit and crit are merged across melee, ranged and spell. Parry and dodge reduction are new stats.
- **[D]** About 4,800 new items, 26 reworked Classic sets, and 48 new sets such as Rotmender's Raiment and Krol'dok Battlegear.
- **Legendaries [C].** A legendary questline is promised for spring 2027 with no details.

**Data.** Item, ItemSparse (stats are possibly absent or zeroed for obfuscated items [S]), ItemSet, ItemSetSpell, ItemModifiedAppearance, and ItemEffect/Spell for situational procs.
**Ideas.** (1) A set-bonus browser, Classic vs Forever. (2) An "obfuscated item" flag with "stats unknown" UI and user-confirmed stat entry. (3) A situational-trinket finder by creature type or environment.

## 8. Professions and recipes
**What [C].** There are 600+ new recipes, and crafted food gives a small XP bonus plus stats. Mages get Comprehension, a minor tradeskill for reading dropped scrolls. No new primary professions are announced.
**Data.** SkillLine, SkillLineAbility, Spell reagents, and recipe Items. Trainer and vendor lists are server-side.
**Ideas.** (1) A "new recipe" filter per profession and skill band. (2) A food-with-XP table. (3) A reagent reverse lookup, showing what needs this material.

## 9. Quests
**What [C].** There are 1,000+ new quests, with 150+ in Riverglades. Old chains gain rewards, such as a caster ring from the Mor'ladim quest. Questie lacks most Forever quests and depends on user traces [D].
**Data.** QuestV2, QuestLineXQuest, QuestPOIs and QuestSort. Starters, enders and objectives are server-side and must be crowd-sourced, as Questie does.
**Ideas.** (1) A quest diff: new quests and changed rewards for old ones. (2) Questline graphs. (3) A per-zone completion matrix with level bands.

## 10. Other systems
- **Transmog [C].** Done at city transmogrifiers and can be disabled, and "Classic Mode" turns it off while appearances still collect. Dungeon blues grant the look to all eligible looters, raid epics only to the binder. Data: ItemModifiedAppearance, TransmogSet. Idea: an appearance checklist per instance.
- **Mounts and collections [C].** Mounts are TBC-style and come with riding training. Transmog, mounts and companions are account-wide, with cross-version scope unclear [D].
- **PvP [C].** Darkspear Islands is a 15v15 battleground for levels 30–60, with four nodes and a flag. Its reps are the Darkspear Raiders and the Theramore Expeditionary Force, with a tabard at Exalted. Honor becomes a currency and Rank is capped weekly [D]. Idea: a PvP vendor and rep browser.
- **Hardcore [C].** Arrives in winter 2026/27 as an enclosed ruleset.

## 11. Data-structure changes to plan for
- **[C]** The client uses the mainline UI and API, with Forever-specific changes in build 70291 (Warcraft Wiki).
- **[C]** Merged hit and crit and the new stats need a new stat-type mapping.
- **[S]** Expect new Legacy and camping tables. Verify names against build 70425's DB2 list.
- **[C]** Item stats are hidden, so treat absent stats as unknown, not zero.

## Nothing public yet
No dungeon bosses are announced, and no raid bosses are official. Hyjal rep factions, legendary details and Shen'Dralas levels are unknown. The full list of 16-point talents for every spec is still in flux.

## Sources
- https://www.wowhead.com/forever/blue-tracker/news/eu/24303313 (Blizzard deep-dive panel recap)
- https://www.warcrafttavern.com/forever/news/warcraft-forever-classic-announced-at-blizzcon-2026/
- https://news.blizzard.com/en-us/article/24302071 (Skyborne)
- https://worldofwarcraft.blizzard.com/news/24310968 (Rogue/Warlock deep dive)
- https://worldofwarcraft.blizzard.com/news/24307308 (Darkspear Islands)
- https://blizzardwatch.com/2026/09/13/know-world-warcraft-forevers-new-zones-quests-dungeons-raids/
- https://www.warcrafttavern.com/forever/news/all-new-zones-areas-in-wow-forever-revealed-at-blizzcon/
- https://www.warcrafttavern.com/forever/news/scrolls-totems-mage-shaman-deep-dive-for-wow-forever/
- https://www.warcrafttavern.com/forever/news/rage-will-benefit-from-crit-priest-warrior-deep-dive-for-wow-forever/
- https://www.warcrafttavern.com/forever/news/powershifting-is-back-sort-of-hunter-druid-deep-dive-for-wow-forever/
- https://www.warcrafttavern.com/forever/news/item-stats-will-not-be-known-in-advance-for-wow-forever/
- https://www.warcrafttavern.com/forever/news/level-30-content-is-now-available-in-the-wow-forever-beta/
- https://www.method.gg/wow-forever/camping-in-wow-forever-how-it-all-works-all-profession-buffs
- https://www.method.gg/wow-forever/no-ptrs-and-new-datamining-restrictions-coming-to-world-of-warcraft-forever
- https://guildorder.com/games/wow_forever/wiki/professions-camping-and-blueprints
- https://mobalytics.gg/wow-forever/guides/legacy-system-guide
- https://www.icy-veins.com/wow-forever/news/wow-forevers-new-legacy-system-rewards-you-for-starting-the-journey-all-over-again/
- https://www.icy-veins.com/wow-forever/news/wow-forever-hid-over-12500-items-from-dataminers-heres-whats-still-secret/
- https://www.icy-veins.com/wow-forever/news/addons-in-wow-forever-blizzard-devs-just-addressed-the-big-question/
- https://warcraft.wiki.gg/wiki/1.60.1
- https://warcraft.wiki.gg/wiki/Patch_1.60.1/API_changes/notes
- https://www.wowhead.com/forever/talent-calc
- https://www.wowhead.com/forever/news/new-and-updated-gear-set-bonuses-in-wow-forever-382958
- https://seramate.com/news/forever/2026/09/25/rotmenders-raiment-datamined-1f1bced4
- https://guided.news/en/news/wow-forever-datamining-three-gear-sets-defias-dalaran-kroldok/
- https://kami-labs.fr/en/wow-forever/fin-de-la-beta-wow-forever/
- https://kami-labs.fr/en/wow-forever/addons-leveling-wow-forever/
- https://www.zockify.com/forever/skyborne/
- https://hostedgg.com/blog/wow-forever-talent-changes
- https://lfcarry.com/guides/wow-forever-raids (Hyjal boss list, low confidence)
