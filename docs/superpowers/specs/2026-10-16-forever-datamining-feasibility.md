# WoW Forever client data: feasibility report (2026-10-16)

**Verdict:** Feasible. The tables, icons and map tiles for the Forever beta are available two ways:
- from the local install, through wow.tools.local (recommended), or
- remotely, through wago.tools or Blizzard's CDN.

Everything below was fetched live on 2026-10-16. Samples are in this folder: `icons/`, `maps/`, `tt/`, `westfall/`, `dbd/`.

## 1. Build identity
- Blizzard's patch service (`https://us.version.battle.net/v2/products/{p}/versions`) lists two Forever products:

| Product | Build | Role |
|---|---|---|
| `wow_classic_beta` | 1.60.1.70425 | Beta; every 1.60.1 build since 69876 (16 Sep), including Questie's 69893 |
| `wowf` | 1.60.1.70245 | Launch product, first seen 13 Oct |

- KeyRing is empty for both, so no encryption keys are needed.
- `https://wago.tools/api/builds` lists the same builds.
- `wow_classic_forever` and `wow_camelot` do not exist. "Camelot" appears only in QuestieDB's TOC alias and in AreaTable names.
- The local `.build.info` shows the beta's build key `ea9c525e…`. That is the same build config as the CDN's 70425, so the local install and wago serve the same build.
- QuestieDB's DBC snapshots live in a private `Questie/dbc` release (`tools/dbc/README.md`), so we cannot reuse them.

## 2. DB2 tables
- **wago export URL:** `https://wago.tools/db2/{Table}/csv?build=1.60.1.70425`. It returns the full CSV, cached for 4 hours, and ignores Range requests.
- **ItemSparse** (19,234 rows, 5.3 MB) includes: `ID, Display_lang, Description_lang, ItemLevel, OverallQualityID, RequiredLevel, InventoryType, Bonding, SellPrice, ItemDelay, DmgVariance, DamageType, ItemSet, StartQuestID, AllowableClass, Flags_0..4, StatModifier_bonusStat_0..9, StatPercentEditor_0..9`.
- **Stats are budget shares, not flat values.** Thunderfury's stat IDs 3/7/51/55 with shares 2174/3478/3478/3913 match 5 Agi, 8 Sta, 8 Fire resistance and 9 Nature resistance. The real values come from the scaling tables:
  - Stats: `RandPropPoints`.
  - Damage: `ItemDamage{OneHand,TwoHand,Ranged,Wand,…}`.
  - Armor: `ItemArmorTotal`, `ArmorLocation`, `ItemArmorQuality`, `ItemArmorShield`.
  - Section 4 points to a reference implementation.
- **Item** (31,852 rows) has `ClassID, SubclassID, InventoryType, IconFileDataID`. Item 17204 maps to icon 134124.
- **All other requested tables exist in 70425.** Their headers were fetched:
  - Effects and spells: `ItemXItemEffect`, `ItemEffect`, `Spell`, `SpellName`, `SpellEffect`, `SpellMisc`, `SpellDuration`, `SpellItemEnchantment`.
  - Sets: `ItemSet`, `ItemSetSpell`.
  - Appearance: `ItemAppearance`, `ItemModifiedAppearance`, `ItemDisplayInfo`.
  - Maps and areas: `UiMap`, `UiMapArt`, `UiMapArtTile`, `UiMapArtStyleLayer`, `UiMapXMapArt`, `UiMapAssignment`, `WorldMapOverlay`, `WorldMapOverlayTile`, `AreaTable`, `Map`.
- **Hotfixes:** wago's CSVs appear to omit hotfixes, though this is unverified. 12,618 Item rows have no ItemSparse row.

## 3. Icons and map tiles
- **wago endpoint:** `https://wago.tools/api/casc/{FileDataID}?download&branch=wow_classic_beta`. Using `&build=1.60.1.70425` returns identical bytes.
- The endpoint returns raw BLP2 (DXT). There is no PNG route; `/png` returns 404.
- Without the branch parameter, icon 134124 came back with a different header byte, presumably from retail. Always pin the branch.
- **Verified samples:**
  - Icon 134124: a 3.9 KB BLP that converts with Pillow 12 to a 64×64 PNG of 4.4 KB.
  - Westfall: 12 base tiles plus 17 overlay tiles, each 256×256 and about 45 KB.
- **CDN without wago:** wowdev TACTSharp (MIT).
  - Fetch a file with `TACTTool -p wow_classic_beta -m fdid -i 134124`.
  - Add `-d <WoW folder>` to read the local install instead.
  - Not tested here; it needs .NET.

## 4. Tooltip text
- **Effect chain:** `ItemXItemEffect` → `ItemEffect` → `Spell.Description_lang`. TriggerType 0 means Use, 1 Equip, 2 Chance on hit. Placeholders are filled from `SpellEffect` by EffectIndex. Verified examples:
  - Thunderfury: spell 21992 uses `$s2` = 300 and `$s1` = |−25|.
  - Benediction: "Holy spells by $s1%", with $s1 = 2.
  - Set bonuses come from `ItemSetSpell` (Threshold, SpellID).
- **Reference implementation found.** wow.tools.local (MIT) has an item tooltip in `Controllers/DBC/TooltipController.cs`. It covers:
  - stats from RandPropPoints[ilvl] and StatPercentEditor, through `TooltipUtils.CalculateItemStat`;
  - damage as `ItemDamage*[ilvl].Quality[q] × delay × (1 ± DmgVariance/2)`, with Legendary treated as Epic;
  - a fallback to the ItemModifiedAppearance icon when the Item icon is 0.
- It formats spell text with Marlamin/WoWTools.SpellDescParser (C#). That repo has **no licence file**, so port it from scratch rather than copying it.
- Quality colours (warcraft.wiki.gg/wiki/Quality):

| Value | Quality | Colour |
|---|---|---|
| 0 | Poor | #9d9d9d |
| 1 | Common | #ffffff |
| 2 | Uncommon | #1eff00 |
| 3 | Rare | #0070dd |
| 4 | Epic | #a335ee |
| 5 | Legendary | #ff8000 |

## 5. Zone maps
- **Chain:** area ID → `UiMapAssignment.AreaID` → `UiMapID` → `UiMapXMapArt` → `UiMapArtTile` (Row, Col, FileDataID).
  - Westfall: area 40 → UiMap 1436 → art 2165 → 3×4 tiles.
  - The layer is 1002×668, cropped from 1024×768.
- **Base art is blank parchment.** The terrain is drawn by `WorldMapOverlay` and its tiles, which must be alpha-composited at OffsetX/Y. Compare `westfall/base.png` with `westfall/full.png`.
- **Totals in 70425:**
  - 60 UiMaps: 51 zones, 4 continents, 4 orphans and 1 world map.
  - 1,672 base tiles, mostly 12 per map.
  - 1,081 overlays, with 1,739 overlay tiles.
  - No dungeon maps.
- **Forever redrew zones.** 688 tile IDs are new compared with Era 1.15.9.70003, including Westfall. Era art and coordinates cannot be reused.
- **New maps:** each has 12 tiles and an area assignment.

| UiMap | Name | Area ID |
|---|---|---|
| 2482 | Mount Hyjal | 616 |
| 2521 | Zephras Isle | 16593 |
| 2548 | Riverglades | 16591 |
| 2652 | Shen'dralas | 16651 |
| 2524 | Darkspear Islands (battleground) | 16606 |

## 6. Licensing and terms
- `wago.tools/robots.txt` disallows every path except the home page.
- wago.tools has no terms page: /terms, /about and /tos all return 404. No throttling was hit in about 80 requests.
- No published rate limit was found for Blizzard's CDN (unverified).
- Icons and map art are Blizzard copyright, and shipping them has no explicit licence. Fan sites rely on tolerance. The EULA bars "data mining" of the platform. This is the owner's legal call.

## 7. Blizzard Game Data API
- Classic Era uses namespace `static-classic1x-{region}`, which has Item and Item Media endpoints.
- No Forever namespace was found in documentation or forums (untested; it needs a key).
- Keys are free with a Battle.net account.
- Reported terms forbid permanent mirroring and redistribution (second-hand, unverified). That makes the API unsuitable for data shipped inside the app.

## 8. Local install compared with CDN/wago.tools
- **Local install:** `C:\Program Files (x86)\World of Warcraft`. The product is `wow_classic_beta`, folder `_classic_beta_`, build 70425, sharing the `Data\` CASC storage.
- **Definitions:** WoWDBDefs already covers 1.60.1.69876 through 70425. ItemSparse, Item, SpellEffect, UiMapArtTile and UiMapAssignment were checked, and 70425 was merged at 2026-10-16 00:53Z. Local DB2 parsing therefore works today. Future builds will need a WoWDBDefs update, which usually lands within hours.
- **wago.tools** lists 70425, with DB2 CSV exports and files available.

| Tool | Reads local beta? | Fit for automation |
|---|---|---|
| **wow.tools.local** (MIT, v0.9.9, 29 Sep) | Yes, with `-wowFolder … -wowProduct wow_classic_beta` | **Best.** It runs wago.tools' code as a localhost HTTP service: `/dbc/export/csv?name=ItemSparse&build=…&useHotfixes=true`, `/casc/fdid?fileDataID=…` for raw BLP, and `/dbc/tooltip/item/{id}`. It fetches WoWDBDefs, the listfile and TACT keys itself. It applies hotfixes from the local DBCache. |
| **wow.export** (MIT, 0.2.19, June 2026) | Yes; its product list includes `wow_classic_beta` | **GUI only.** It has no CLI or remote control. It exports BLP to PNG and handles DB2. It does not list `wowf`, so the launch product may need an update (unverified). Good for manual spot checks. |
| **TACTSharp** | Yes, with `-d <folder> -p wow_classic_beta` | Scriptable, but extracts files only. DB2 would also need DBCD and WoWDBDefs. |
| **wago.tools / CDN** | Not applicable | Runs anywhere, including CI, with nothing installed. Subject to robots.txt and possibly without hotfixes. |

- **Local advantages:**
  - No load on a third-party site, which settles the robots.txt concern.
  - Hotfixes are included.
  - Works offline once the definitions are cached.
- **Local drawbacks:**
  - Only works on the maintainer's machine, not CI.
  - Battle.net auto-updates, so the build drifts. Pin by build key and record it.
  - Battle.net and WoW must be closed while extracting.
  - The `_classic_beta_\Cache\ADB\enUS\DBCache.bin` is dated 2 Oct, older than 70425. Log into the beta once to refresh the hotfixes.
- **Recommendation:** use wow.tools.local as the primary source and wago.tools as the fallback. Both expose the same endpoints, so one fetcher with a configurable base URL serves both.

## Recommended pipeline
1. **Pin the build.** Read `.build.info` locally, or the wago builds API, and record the build key and version.
2. **Start the source.** Run `wow.tools.local.exe -wowFolder "C:\Program Files (x86)\World of Warcraft" -wowProduct wow_classic_beta`, or set the base URL to wago.tools.
3. **Export about 30 CSVs** with hotfixes into `cache/{build}/`. That is about 15 MB.
4. **Compute items.** Build stats, damage, armor, effect and set lines, porting the WTL formulas. Write `item`, `item_stat`, `item_tooltip_line` and `item_set` tables into the shipped SQLite.
5. **Icons.** Fetch the BLPs, convert them to 64×64 PNG with Pillow, and store them as BLOBs keyed by FileDataID.
6. **Maps.** For each zone, stitch the base tiles and all overlays, crop to 1002×668, and save as JPEG q80.
7. **Validate.** Compare about 20 items with WTL's `/dbc/tooltip/item/{id}` output, and compare image hashes across builds.

## Estimated sizes

| Asset | Count | Size |
|---|---|---|
| Icons | 2,883 distinct | about 13 MB as PNG |
| Map tiles downloaded | about 3,400 BLP | about 150 MB per build |
| Shipped maps | 51 zones | about 175 KB each as JPEG q80, about 10 MB total |
| CSVs | about 30 tables | about 15 MB, build-time only |

## Risks
1. **Stat and damage formula drift.** Validate against WTL and known items.
2. **Blizzard art redistribution** has no licence.
3. **Build churn.** The beta changes every 2–3 days. `wowf` may diverge, and tool support for `wowf` is untested.
4. **wago.tools fallback** remains subject to its robots.txt.
5. **Spell-description syntax** has a long tail of cases, and the reference parser is unlicensed.

## Open questions for the project owner
1. Is shipping Blizzard icons and map art acceptable?
2. Should the pipeline target `wowf` at launch or `wow_classic_beta` now? Should the app support multiple builds?
3. Is it acceptable that the pipeline runs only on the machine with the WoW install, with wago as the CI fallback?
4. Is a .NET/Python build-time dependency acceptable?
5. Should maps be fully revealed?

## Sources
- https://wago.tools/api/builds
- https://wago.tools/db2/ItemSparse/csv?build=1.60.1.70425
- https://us.version.battle.net/v2/products/wowf/versions
- https://github.com/Marlamin/wow.tools.local
- https://github.com/Kruithne/wow.export/blob/main/src/js/constants.js
- https://github.com/wowdev/TACTSharp
- https://github.com/wowdev/WoWDBDefs
- https://github.com/Questie/QuestieDB/blob/master/docs/forever.md
- https://www.icy-veins.com/wow-forever/news/all-maps-and-minimaps-from-wow-forever-new-and-changed-zones-spoilers/
- https://gameinformer.com/blizzcon-2026/2026/09/12/blizzard-announces-world-of-warcraft-forever-expanding-vanilla-wow-with
