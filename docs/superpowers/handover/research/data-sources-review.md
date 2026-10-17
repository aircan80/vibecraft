# WoW: Forever data sources: review and recommendations (2026-10-17)

**Tags.** **[C]** confirmed from a cited source, or measured by me today against a cited URL (marked *measured*). **[D]** derived from confirmed facts. **[S]** speculative. **[N]** looked for and not found. Earlier memos are not repeated: `phase4-forever-data-feasibility.md` (what build 70425 ships), `wow-forever-new-systems.md` (what Forever adds) and `forever-datamining-report.md` (tooling for local and wago extraction).

**Scope.** This covers every source that could feed the shipped SQLite at build time. Nothing here changes the runtime rule that the app never reads the game client and never touches the WoW install.

## Headline findings

1. **The biggest single gain is the hotfix cache, and it is one HTTP GET away [C, measured].** Raidbots publishes the Forever beta client's `DBCache.bin` at a public URL. Today's file is build 70450, 3.4 MB, last modified 2026-10-17 02:57 UTC. It holds 4,863 valid `ItemSparse` rows. **4,776 of them are items that our build-70425 ItemSparse lacks, out of 12,618 hidden.** They split into 678 of the 2,433 Forever-new hidden items and 4,098 items we currently show with Classic Era stats. Both sample items from the backlog are in it: Rotmender's Garb (286978) and Worgenbane Talisman (273643). These counts equal ForeverChanges' published "4,775 of 12,618 revealed, 678 of 2,433 new", so that site is a view of the same cache.
2. **wago.tools is not behind; we are [C, measured].** The patch service lists `wow_classic_beta` 1.60.1.70450 now. wago's build list has it from 2026-10-16 22:29, and WoWDBDefs merged its definitions at 22:31. Our shipped data and `build.lock` are 70425, and the owner's install is already on 70450. A direct CDN pipeline would not be fresher than wago; a build-watch would.
3. **wago's table CSVs omit hotfixes, but wago keeps them [C, measured].** `ItemSparse` ID 286978 is absent from wago's table view for 70450. wago's hotfix log holds that row (push 17064194, build 70425, "Rotmender's Garb", item set 2133). Build 70450 adds only 23 ItemSparse rows over 70425, so revealed items are not baked into new builds. Hotfix ingestion is the only route to them.
4. **Our AtlasLoot pin is a dead branch for Forever [C].** Hoizame/AtlasLootClassic was last pushed 2024-06-05. A Forever fork, "AtlasLoot Classic Forever" by sliccer, is GPL-2.0 and was updated 2026-10-12. A GPL-3.0 Forever addon (Dungeon Crawler Forever) already extracts its boss loot, Forever-addition flags and drop rates as facts.
5. **Our Questie source is already the Forever database, not vanilla [C].** `sources.properties` reads `data/Forever/*` plus the generated, trace and manual Forever correction layers. QuestieDB gets several commits a day. One day after the pin was set, it was 18 commits behind master, with 150 changed lines in `foreverQuestFixes.lua`. The gap is the age of the pin, not the source.
6. **Nothing official covers Forever beyond the client [C/N].** No Game Data API namespace for Forever was found. Warcraft Logs and Raider.IO announce no Forever support. Blizzard confirmed there is no Dungeon Journal and no raid PTR.

## Scored comparison

Scores run from 1 (poor) to 5 (excellent). **Gap fit** is how much of our known gaps the source closes: hidden stats, Forever boss loot, journal, drop rates, new quests and build lag. **Licence** is whether the derived data may go into the shipped SQLite.

| # | Source | Forever value | Gap fit | Freshness | Licence for shipping | Effort | Risk | Verdict |
|---|---|---|---|---|---|---|---|---|
| 1 | Raidbots DBCache mirror (hotfix rows) | 5 | 5 (hidden stats) | 4 (hours) | 3 (Blizzard data, same footing as client tables; no Raidbots terms found) | M | Medium | **Adopt now** |
| 2 | wago.tools DB2 CSV + files | 5 | 3 | 5 (same day) | 3 (Blizzard data; wago states no terms) | Done | Medium | **Keep, add build-watch** |
| 3 | wago.tools hotfix log | 4 | 4 | 5 | 3 | M | High (robots.txt disallows, no API) | Cross-check only |
| 4 | Direct CDN (TACT/CASC) mining | 5 | 3 | 5 | 3 | L | Medium | Adopt later as fallback |
| 5 | Local install CASC + DBCache, read-only | 5 | 4 | 5 | 3 | M | Medium (owner's machine only) | Adopt later, on owner's yes |
| 6 | QuestieDB Forever data | 5 | 4 (quests, NPCs) | 5 (daily) | 2 (no licence file) | Done | Low | **Keep, re-pin often** |
| 7 | AtlasLoot Classic Forever (sliccer) | 4 | 4 (Forever boss loot) | 4 | 4 (GPL-2.0) | S-M | Medium (CurseForge only, data origin unstated) | **Adopt** |
| 8 | AtlasLootClassic (Hoizame, current pin) | 1 | 1 | 1 (stale since 2024) | 4 | Done | Low | Replace with 7 |
| 9 | VMaNGOS world DB | 2 | 2 (vanilla rates) | 3 | 4 (GPL-2.0) | Done | Low | **Keep** |
| 10 | CMaNGOS classic-db | 2 | 2 | 3 | 3 (GPL-3.0) | M | Low | Later, cross-check only |
| 11 | TrinityCore family | 0 | 0 | n/a | 4 | n/a | n/a | Reject (no 1.x branch) |
| 12 | pfQuest / ClassicCodex | 0 | 0 | 1 | 3 | M | Low | Reject (repackaged VMaNGOS) |
| 13 | GatherMate2_Data | 1 | 1 | 2 | 1 (Wowhead-derived) | M | Medium | Reject |
| 14 | Blizzard Game Data API (Classic) | 1 | 1 | ? | 1 (no permanent mirror) | M | High | Reject for shipping |
| 15 | Warcraft Logs / Archon | 1 | 1 | ? | 1 | M | High | Reject now, re-check after the 9 Dec raids |
| 16 | Raider.IO | 0 | 0 | n/a | 1 | n/a | n/a | Reject |
| 17 | Wowhead Forever | 5 | 5 | 5 | 0 (no scraping) | n/a | High | Test oracle only, as today |
| 18 | ForeverChanges, foreverdb.net, wowtbc.gg and similar fan sites | 4 | 4 | 4 | 1 (terms unknown) | n/a | High | Cross-check only; use their primary sources |
| 19 | wowsims Forever (ElliotWood/Forever) and wowsims/gamedata | 3 | 2 | 5 (3-hourly) | 2 (MIT code; data is Blizzard's; origin of `sources` unknown) | S | Medium | Reference and diff feed only |
| 20 | Other Forever addons with collectors (DungeonQuestAtlas, Forever Buddy, NeedRoll) | 3 | 3 | 4 | 1 to 3 (All Rights Reserved to GPL-3.0) | M | Medium | Cross-check only, after a licence check |
| 21 | Own in-game collector addon | 5 after launch | 5 (loot sources, vendors, trainers) | live | 5 (own observations) | M | Medium (API restrictions) | **Adopt for launch** |
| 22 | SimulationCraft data | 0 | 0 | n/a | 3 | n/a | n/a | Reject (retail only); its CASC code is a reference |
| 23 | Guide sites (Icy Veins, Method) | 2 | 1 | 4 | 0 | S (manual) | Low | Cited curated facts only |
| 24 | Warcraft Wiki API docs | 1 | 0 | 4 | CC BY-SA | S | Low | Developer reference |

## 1. Direct Battle.net CDN / CASC mining

**What it is [C].** The patch service answers `https://us.version.battle.net/v2/products/{product}/versions` and `/cdns`. Today `wow_classic_beta` is 1.60.1.70450 with build config `84bea4233331e390fda8724c6f91ffa5`, and `wowf`, the launch product, is still 1.60.1.70245 *(measured)*. The KeyRing column is empty for both *(measured)*. The build config names the root, install, download, encoding and VFS files, and its `build-name` is `WOW-70450patch1.60.1_ForeverBeta` *(measured)*. The encoding file alone is about 141 MB *(measured from `encoding-size`)*. The wowsims Forever tool, which runs this path in production, reports "a few hundred MB per build" for configs, archive indexes, encoding, root and DB2 range reads.

**Tooling [C].**
- **wowsims Forever `db2tool`** (Go, MIT, ElliotWood/Forever) has local-CASC, CDN and offline modes. It applies `DBCache.bin` hotfixes. It decrypts encrypted DB2 sections with keys from `TactKey.db2`, from TACTKEY hotfix records or from wowdev/TACTKeys. It is the best current reference design.
- **SimulationCraft `casc_extract.py`** (Python, GPL-3.0) has a `--cdn` mode, a `--product` option, Salsa20 and a key file. Its companion `dbc_extract.py` takes `--hotfix DBCache.bin`, but it uses simc's own format files rather than WoWDBDefs.
- **TACTSharp, wow.tools.local and wow.export** (MIT), **CascLib** (MIT, C) and **TACT.Net** (GPL-3.0, last push 2024-10) are .NET or native code. keg (Python, MIT) was last pushed in 2020.
- **WoWDBDefs** definitions are CC BY-SA 4.0 and its code is BSD-3. It covers 1.60.1.69876 to 70450. **wowdev/wow-listfile** publishes a release almost daily.

**What it gives beyond wago [D].**
- Independence from a single volunteer site.
- Control over the hotfix overlay and TACT-key decryption. The 66 items, 90 quests and 872 spells in encrypted sections become readable the moment a key surfaces.
- Every table and file, including M2 models and full BLP sets.
- **No freshness gain.** wago, WoWDBDefs and the patch service all moved within minutes of each other on 16 Oct.

**Risks.**
- **Terms [C].** The Blizzard EULA §1.C.vi bars "any unauthorized process or software that intercepts, collects, reads, or 'mines' information", and §1.C.i bars derivative works. Blizzard's 2018 statement, quoted on Warcraft Wiki, tolerates datamining for content creation and draws the line at emulated servers. Reading the public CDN is the same act wago performs. The legal exposure is shipping derived data, which we already do, so this stays the owner's call.
- **Encryption [C].** Unknown keys leave sections zero-filled. Tools must report the rows still locked, as `db2tool` does.
- **Maintenance [D].** A Python implementation means BLTE, archive indexes, encoding, root/TVFS and WDC5 parsing. That is an L task with a long tail. Porting `db2tool`'s design is cheaper than writing from the format docs.
- **Bandwidth [C].** Hundreds of MB per build, against about 15 MB of CSV from wago.
- **Local install variant [D].** Reading local CASC read-only gives the same data, offline, with the owner's own hotfix cache. It needs Battle.net and WoW closed during extraction and works only on that machine. The backlog defers any reading of the install until the owner says yes.

**Verdict.** Adopt later, as the fallback if wago disappears or its terms change. Adopt the cheap part now: the versions endpoint as a build-watch.

## 2. wago.tools

**What it is [C].** wago.tools is a community DB2 and file browser. It offers CSV export (`/db2/{Table}/csv?build=`), file download (`/api/casc/{fdid}`), a build list (`/api/builds`), build and table diffs, and a hotfix log (`/hotfixes`). Its route table also lists `journal`, `maps` and a webhook *(measured)*. Its `/apis` page is empty, and no terms page exists *(measured today and on 2026-10-16)*.

**Freshness [C, measured].** Same day: 70450 was listed at 2026-10-16 22:29, a few minutes after the patch service.

**Hotfixes [C, measured].** Table views and CSVs show only the build's base rows. Hotfix rows, including revealed ItemSparse rows, live in the separate hotfix log. That log is in page data with no documented API, and robots.txt disallows crawling.

**Licence and risk.** wago states no terms, and the data is Blizzard's [C]. It is run by volunteers, so outages and policy changes are the main risk [S].

**Verdict.** Keep it as the primary table and file source, and add a build-watch. Use the hotfix log only for manual spot checks.

## 3. Blizzard Game Data API

**What it is [C].** Classic namespaces are `static-classic-{region}` for progression realms and `static-classic1x-{region}` for Era. They expose item, item media, item class, creature, creature family and display media, playable race and class, and power type. Classic has no journal or quest endpoints. The developer portal moved to `community.developer.battle.net` and renders client-side, so its docs could not be read directly.

**Forever [N].** No Forever namespace was found in docs, forums or search. Whether one will exist is unknown [S]. If it did, it would serve server-side item data and might expose hidden items, which Blizzard would likely prevent [S].

**Terms [S, second-hand].** Third-party summaries say Blizzard-sourced data must be refreshed or deleted within 30 days and must not be permanently mirrored. That rules out a shipped SQLite.

**Verdict.** Reject for shipped data. Re-check for a Forever namespace after launch, for validation only.

## 4. Community addon databases

- **QuestieDB Forever [C].** It is already our source. It has its own `data/Forever` inputs, a generated delta base, machine-generated `traces/` from aggregated player traces, and manual fixes. Its own doc says it is "not complete new Forever content". GitHub reports no licence file. Commits land daily. **Verdict: keep, and re-pin at least weekly and at launch.**
- **AtlasLoot Classic Forever (sliccer) [C].** A GPL-2.0 fork, updated 2026-10-12, advertising "Detailed loot data from current and upcoming raids" with boss lists per phase. A downstream GPL-3.0 project extracts boss loot item IDs, Forever-addition flags and drop rates from its `data.lua` and `droprate.lua`, the same files our importer parses. The origin of its loot data is not stated. It is distributed only through CurseForge, which answered 403 to my automated request. **Verdict: adopt, replacing Hoizame/AtlasLootClassic.**
- **pfQuest [C].** Its vanilla database is generated from VMaNGOS and its TBC data from CMaNGOS. ClassicCodex is a Classic port of the same idea [S]. **Verdict: reject; it holds nothing we lack.**
- **GatherMate2_Data [C].** It imports Wowhead data and targets retail. **Verdict: reject.** Gathering nodes can come from Questie objects or VMaNGOS spawns.
- **WeakAuras [C].** It ended support with the Midnight API, which Forever shares, and holds no data. **Verdict: reject.**
- **Forever-specific addons [C].** NeedRoll is All Rights Reserved, with data from AtlasLootClassic and CMaNGOS. DungeonQuestAtlas is All Rights Reserved, with client data via ForeverChanges plus Wowhead, and has an in-game collect and export mode. Forever Buddy is GPL-3.0 and has an opt-in collector of quests, givers, trainer lists and loot to SavedVariables. WoW-Spyglass is an MIT loot browser. **Verdict: cross-check only, after a licence check per source.** Their collectors show that loot, quest-giver and trainer data is readable in the Forever addon API [D].

## 5. Server-emulation databases

| Project | Version target | Licence | Format | Notes |
|---|---|---|---|---|
| VMaNGOS | 1.2 to 1.12, every row tagged by patch | GPL-2.0 | `db_latest` release with a SQLite asset, last published 2026-09-28 | Our current source; its stated goal is accuracy with patch-correct progression [C] |
| CMaNGOS classic-db | 1.12 only | GPL-3.0; the LICENSE must ship with redistributions | One SQL dump plus update files, MySQL | Used by NeedRoll and Dungeon Crawler Forever [C] |
| TrinityCore | Branches 3.3.5, cata_classic, wotlk_classic, master | GPL-2.0 | TDB releases | **No 1.x branch** [C] |

**Accuracy [N].** I found no published head-to-head comparison of loot accuracy. Both projects derive vanilla rates from historical community data, and neither knows Forever's reworked loot [C]. VMaNGOS's per-patch tagging is what lets our importer compute patch-1.12 rates [D].

**Licence note [D].** Combining GPL-3.0 data into our GPL-2.0-or-later project makes that distribution GPL-3.0. Keep CMaNGOS out of the shipped file unless the owner accepts that.

**Verdict.** Keep VMaNGOS. Add CMaNGOS later as a build-time disagreement report only.

## 6. Community-collected live data

- **Raidbots DBCache mirror [C, measured].** The URL is `https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin`.
  - The cache header says version 9, build 70450. It has 35,990 entries from 4,867 pushes. Its ItemSparse rows are 4,863 valid and 38 invalidated.
  - The same URL pattern answers 404 for `wowf`, `forever` and `classic_era` today, so the path for the launch product is not known yet [C].
  - ForeverChanges says Raidbots aggregates many players' caches. wowsims/gamedata and ElliotWood/Forever read this exact URL in scheduled workflows [C].
  - No Raidbots terms for this file were found, because the developer page renders client-side [N].
  - The rows are Blizzard's own data, broadcast to every client once anyone sees the item, per Icy Veins [C]. They are therefore on the same footing as the client tables we already ship [D].
  - The file is overwritten in place, so we must snapshot and hash it, as we do with VMaNGOS [D].
- **ForeverChanges [C].** A hidden-item tracker built on the Raidbots dataset and refreshed every two hours. Its dungeon pages "fill up as they get run". Its terms could not be read. **Verdict: use its primary source, and cite the site as a cross-check.**
- **wowsims/gamedata [C].** It publishes text diffs of every Forever build and hotfix push, every three hours, from the CDN plus Raidbots' cache. It has no licence file. **Verdict: use it as a free changelog and history of past caches; do not ship from it.**
- **Wowhead Forever [C].** It has full tooltips and "dropped by" data from its Looter client. robots.txt disallows `/forever/list` and random pages. The ToS page renders client-side and could not be read. We already use `nether.wowhead.com/forever/tooltip/item/{id}` as a small test oracle. **Verdict: keep it as a test oracle only; never ship from it.**
- **Warcraft Logs / Archon [C/N].** Classic subdomains exist (classic, fresh, sod, vanilla), but no Forever site or announcement was found, and the API docs answered 403. Logs would give encounter IDs and kill confirmation, which DungeonEncounter already gives, and no loot. **Verdict: reject now; re-check after the 9 December raids.**
- **Raider.IO [N].** No Forever support was found, and it has no item data in any case. **Verdict: reject.**
- **Reddit and Discord dumps [S].** They are unversioned, unlicensed and unverifiable. **Verdict: leads only.**
- **Blizzard announcements [C].** These are already planned as cited curated facts (backlog decision 24).

## 7. In-game collection (the owner's client)

**What an addon can record [D, with C evidence].** The collector modes in DungeonQuestAtlas and Forever Buddy show that quest givers, quest text, trainer lists and loot are readable in the Forever client. A collector addon would record:
- **Loot sources.** On `LOOT_OPENED`, the slot link and the source GUIDs (NPC or object ID). These give "drops from" facts for new dungeons, Blueprints and rares.
- **Vendors and trainers.** Merchant and trainer windows give stock, prices and skill requirements.
- **Quests.** Giver, ender, objectives and rewards, with the player's map position.
- **Positions.** The player's map position when targeting or looting, as an approximate NPC position.

**Limits [C/S].** Midnight-style "secret values" hide health, names, casts, auras, cooldowns and threat in restricted content [C]. Whether loot-source APIs are affected inside new dungeons is unknown [S]. One player's loot gives presence, not rates. Item stats need no addon, because the client's own `DBCache.bin` already holds every revealed row [D].

**Export [D].** SavedVariables Lua, which our `lua-table` parser already reads. Facts we observe ourselves carry no third-party licence.

**Verdict.** Adopt as the post-launch scarce asset. Test it on the beta before the beta ends on about 22 October.

## 8. Anything else

- **SimulationCraft [C].** Its sim data is retail-only. Its `casc_extract.py` is a Python CDN reference (GPL-3.0). Reject as a data source.
- **wowsims Forever item DB [C, measured].** 10,771 items, 2,862 of them with a `sources` field (zone, boss or trash). The origin of the sources is not documented. Use it for diffs only.
- **Icy Veins, Method and Mobalytics guides.** They are editorial and copyrighted. Facts such as level bands may be entered by hand with citations (decision 24). Icy Veins answered 403 to automated fetches.
- **Warcraft Wiki API pages (CC BY-SA).** The reference for writing the collector addon.

## Recommendations, in priority order

1. **Ingest revealed hidden items from Raidbots' `DBCache.bin`.** It closes 4,776 of the 12,618 hidden items today. 678 of them gain stats they have nowhere else, and 4,098 swap Classic Era values for Forever's. Label them with the source "hotfix". *First step:* add fetching of `https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin` to `tools/clientdata`, recording the header build, SHA-256 and Last-Modified in `build.lock`. Then write a WoWDBDefs-driven parser that turns the valid ItemSparse, Item and ItemEffect rows into overlay CSVs for the matching base build (70450 today).
2. **Add a build-watch, and re-fetch 70450 now.** wago is same-day; our data lags because nothing tells us that a build moved. *First step:* write a `build_watch.py` that compares several upstreams with `build.lock` and `sources.properties`, and exits non-zero when any is stale. The upstreams are the patch service's `wow_classic_beta` and `wowf` versions, wago's `/api/builds`, the Raidbots cache's Last-Modified and header build, and the QuestieDB master commit. Run it before every import, and switch the product to `wowf` at launch.
3. **Replace AtlasLootClassic with AtlasLoot Classic Forever (GPL-2.0).** It gives Forever boss loot and Forever-addition flags, in the file format our importer already parses. *First step:* download the current CurseForge zip by hand into a pinned cache, and record its SHA-256 and licence. Then diff its `data.lua` against the Hoizame pin and count the new Forever boss-loot rows before wiring it in.
4. **Build a collector addon, and test it on the beta before about 22 October.** Loot sources for new dungeons, Blueprints, vendors and trainers exist only on the server. *First step:* write a minimal addon that saves `LOOT_OPENED` slot links and source GUIDs, plus merchant and trainer contents, to SavedVariables. Run one beta dungeon and import the file with the existing Lua parser.
5. **Keep direct CDN mining as the documented fallback, not the default.** It adds independence and decryption, not freshness. *First step:* write a one-page design that ports the wowsims Forever `db2tool` flow to Python, and park it in the backlog.

## Sources

- Patch service, measured 2026-10-17: https://us.version.battle.net/v2/products/wow_classic_beta/versions , https://us.version.battle.net/v2/products/wowf/versions , https://us.version.battle.net/v2/products/wow_classic_beta/cdns
- Build config 70450: http://us.cdn.blizzard.com/tpr/wow/config/84/be/84bea4233331e390fda8724c6f91ffa5
- Local `C:\Program Files (x86)\World of Warcraft\.build.info` (read only): the beta is at 1.60.1.70450
- wago.tools: https://wago.tools/api/builds , https://wago.tools/db2/ItemSparse?build=1.60.1.70450&filter[ID]=286978 , https://wago.tools/hotfixes?search=286978 , https://wago.tools/journal , https://wago.tools/apis
- Raidbots cache: https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin ; developer page https://mimiron.raidbots.com/developers (client-rendered)
- wowsims Forever db2tool: https://github.com/ElliotWood/Forever/blob/master/tools/db2tool/README.md , https://github.com/ElliotWood/Forever/blob/master/docs/commands.md , https://github.com/ElliotWood/Forever/blob/master/assets/database/db.json
- wowsims/gamedata: https://github.com/wowsims/gamedata , https://github.com/wowsims/gamedata/pull/12
- SimulationCraft CASC tools: https://github.com/simulationcraft/simc/wiki/Using-CASC-Extract-and-DBC-Extract , https://github.com/simulationcraft/simc/tree/master/casc_extract
- WoWDBDefs: https://github.com/wowdev/WoWDBDefs (LICENSE.md, definitions/ItemSparse.dbd) ; TACT keys: https://github.com/wowdev/TACTKeys ; listfile: https://github.com/wowdev/wow-listfile
- Other tooling: https://github.com/Marlamin/wow.tools.local , https://github.com/wowdev/TACTSharp , https://github.com/ladislav-zezula/CascLib , https://github.com/wowdev/TACT.Net , https://github.com/Kruithne/wow.export , https://github.com/0xf4b1/keg
- Blizzard EULA: https://www.blizzard.com/en-us/legal/fba4d00f-c7e4-4883-b8b9-1b4500a402ea/blizzard-end-user-license-agreement
- Datamining stance: https://warcraft.wiki.gg/wiki/Data_mining
- Forever restrictions: https://www.method.gg/wow-forever/no-ptrs-and-new-datamining-restrictions-coming-to-world-of-warcraft-forever , https://www.icy-veins.com/wow-forever/news/wow-forever-hid-over-12500-items-from-dataminers-heres-whats-still-secret/
- Game Data API: https://us.forums.blizzard.com/en/blizzard/t/world-of-warcraft-classic-api-endpoints/346 , https://us.forums.blizzard.com/en/blizzard/t/several-api-calls-to-classic-api-result-in-404-not-found/40911 , https://gist.github.com/branneman/684767197dc50b932e6f4a6ba38b3bf2 , https://project.pages.drupalcode.org/wow/development/api-compliance/ (second-hand terms)
- QuestieDB: https://github.com/Questie/QuestieDB/blob/master/docs/forever.md , https://api.github.com/repos/Questie/QuestieDB/compare/49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4...master
- AtlasLoot: https://github.com/Hoizame/AtlasLootClassic , https://www.curseforge.com/wow/addons/atlasloot-forever , https://github.com/perweum/dungeon-crawler-forever/blob/HEAD/data/SOURCES.md
- Forever addons: https://www.curseforge.com/wow/addons/needroll , https://www.curseforge.com/wow/addons/forever-buddy , https://github.com/Leemo94/ForeverBuddy-public , https://github.com/Pirson-s-Addons/DungeonQuestAtlas , https://github.com/Karl-HeinzSchneider/WoW-Spyglass
- pfQuest: https://github.com/shagu/pfQuest ; GatherMate2 data: https://www.wowace.com/addons/gathermate2_data
- Emulators: https://github.com/vmangos/core , https://github.com/vmangos/core/releases/tag/db_latest , https://github.com/cmangos/classic-db , https://github.com/TrinityCore/TrinityCore (branch list via the GitHub API)
- ForeverChanges: https://foreverchanges.pro/hidden-items , https://foreverchanges.pro/dungeons
- Wowhead: https://www.wowhead.com/robots.txt , https://www.wowhead.com/tos (client-rendered)
- Warcraft Logs and Raider.IO: https://www.archon.gg/wow/articles/news/reflecting-on-a-decade-of-warcraft-logs , https://seramate.com/news/2026/06/03/warcraft-logs-uploader-archon-transition-1f15f9c2 , https://raider.io/news/553-raiderio-classic-is-here
- Addon restrictions: https://www.curseforge.com/wow/addons/whyhidden , https://www.icy-veins.com/wow/news/weakauras-to-end-support-in-midnight/

## Downloads (untrusted data, read with `python -I` only)

Everything is under `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\research\datasources-dl\`: `raidbots\DBCache.bin`, `wago\*.html`, `wowsims\db.json` and `builds.json`. The parsing scripts are in `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\research\ds-scripts\`. Nothing from a download was executed. Under the WoW folder, only `.build.info` was read.
