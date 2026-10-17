# PM memo: data-source adoptions in 3B (2026-10-17)

Input: `scratchpad/research/data-sources-review.md`. Controller ruling: adopt the Raidbots DBCache mirror, a
build-watch script and the AtlasLoot Classic Forever fork as one task, Task 19, placed after Task 17 (client 70450 +
Forever dungeons) and before Task 18 (Forever dungeon pages). Add an own collector addon (Task 20) and CDN/CASC
mining as a backlog v5 design item.

Measured by me today with the review's Raidbots download (`DBCache.bin`, header build 70450) against the shipped DB
(70425):
- 3,173 items we show with Classic Era stats have a valid hotfix ItemSparse row.
- 626 items with no client data at all have one.
- Revealed, so usable as acceptance ids: 286978 Rotmender's Garb, 273643 Worgenbane Talisman, and 869 Dazzling
  Longsword (Era today).
- Not in the cache: 16806 Felheart Belt and 19019 Thunderfury.

## Task 19: hidden-item overlay, build-watch, AtlasLoot Forever (M–L, one task, after 17, before 18)

### Sources and pins
| Source | Pin | Licence / NOTICE |
|---|---|---|
| Raidbots DBCache mirror, `https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin` | Snapshot to `~/.cache/wowforever-clientdata/hotfix/<sha256>.bin`. `build.lock` records the header build, the SHA-256, Last-Modified and the fetch time. The file is overwritten upstream, so we never re-read it by URL alone | The rows are Blizzard data, on the same footing as the client tables. NOTICE names Raidbots as the transport ("hotfix cache mirrored by Raidbots"); no Raidbots terms were found |
| AtlasLoot Classic Forever (sliccer), CurseForge zip | Downloaded by hand, because CurseForge answers 403 to automation, into a pinned cache. `sources.properties` replaces `atlasloot.*` (Hoizame 8e99341) with the zip's version, SHA-256 and the same three files plus LICENSE | GPL-2.0, with the author credited in NOTICE. The origin of its data is unstated (see Risks) |
| Build-watch upstreams | Read-only GETs at build time; never at app runtime | none |

### What each overlay may overwrite
**Item fields:** name, quality, item level, required level, stats, damage, armour, class and race restrictions,
bonding, prices, set id, description, icon. The value comes from the source with the lowest number:

| # | Source | `client_source` | Rule |
|---|---|---|---|
| 1 | Hotfix overlay | `hotfix` (new) | Applies only valid rows (status 1) for ItemSparse, Item and ItemEffect. Invalidated rows (38 today) undo only that push. The cache header build must equal the `build.lock` build, or the import fails with the build-watch message. Rows accumulate across snapshots, newest push wins: a later cache that omits a row does not un-reveal the item |
| 2 | Forever base ItemSparse | `forever` | as today |
| 3 | Classic Era fallback | `era` | Used only when 1 and 2 have no row |
| 4 | Questie | none | Name only; client names win and Questie's goes to `item_alt_value`, as today |

**Drop rates:**
- The six-source order is unchanged (lower wins): 1 Questie manual corrections, 2 AtlasLoot item overrides,
  3 Questie/Wowhead, 4 VMaNGOS, 5 AtlasLoot drop table, 6 VMaNGOS quest-only.
- Positions 2 and 5 now read the fork. Its rows carry the source label "AtlasLoot Forever".
- The fork never outranks Questie (3) and, for vanilla content, never outranks VMaNGOS (4). The hotfix overlay
  carries no rates.

**Loot membership** ("who drops it") stays a union, with these rules:
- A Classic boss row that the Hoizame pin had and the fork dropped is not deleted when Questie or VMaNGOS still list
  it. It is counted (`atlasloot.forever.removedVsHoizame`) and keeps the "Vanilla rate; Forever loot reworked" label.
- Fork rows flagged as Forever additions are counted (`atlasloot.forever.additions`).

### How provenance reads
Tooltip footers on the `source_note` line:

| Source | Footer |
|---|---|
| `hotfix` | "Forever stats revealed by a Blizzard hotfix · beta 70450 · cache 17 Oct 2026" |
| `era` | "Classic Era values. Forever stats are still hidden." This replaces "Stats from Classic Era data; Forever values may differ" and follows backlog decision 20 |
| no client data | "Stats hidden until this item drops on a realm" |
| `forever` | no footer, as today |

Rate tooltips:
- VMaNGOS reads "Vanilla rate (VMaNGOS)", unchanged.
- Fork rows read "AtlasLoot Forever".
- Task 18 shows fork loot on Forever dungeon bosses expanded. The section heading carries the chip "AtlasLoot
  Forever, unverified" (state Reported, backlog decision 15). A row loses that chip once Questie or the collector
  (Task 20) confirms it.

Never use the words "live" or "confirmed" for beta hotfix data.

### Acceptance checks (shipped-data tests)
1. **286978 Rotmender's Garb:** `client_source = hotfix`, with name, quality, item level and stats present and
   `item_set_id = 2133`. The page shows the hotfix footer and no "Stats hidden" line.
2. **869 Dazzling Longsword:** it was `era` and is now `hotfix`. No Era footer remains.
3. **16806 Felheart Belt** (negative control): it stays `era`, with the new "Classic Era values" footer.
4. **273643 Worgenbane Talisman:** `hotfix`, with its name equal to Questie's, or Questie's recorded as the alt value.
5. **Import report counters:** `hotfix.itemsparse.valid`, `.applied`, `.invalidated`, `.notInItemTable`,
   `hotfix.fromEra` (about 3,173 on today's data; record the actual figure after Task 17's 70450 refresh) and
   `hotfix.fromNoClient` (about 626).
6. **Build mismatch:** a fixture cache whose header says another build fails the import with the build-watch text.
7. **Fork diff:** the report counts fork additions and rows removed compared with Hoizame. Either City of Dalaran
   (2959) has at least one fork loot row, or the report states that the fork has none for the four beta dungeons;
   Task 18 then shows "No loot recorded yet". Both outcomes are honest. Choose the Task 18 screenshot example from
   this report.
8. **Build-watch, fixture runs:**
   - One stale upstream exits non-zero and names it.
   - An unreachable upstream reports "unknown", never "fresh".
   - The fork is reported as "manual check" with its pinned version.
9. NOTICE carries the Raidbots line and the fork's credit and licence. `./mvnw -B -q verify` is green, and the
   300 ms page budget is unchanged.

**Build-watch script** (`tools/build_watch.py`, Python, `-I`), compared against `build.lock` and `sources.properties`:
- the patch service versions for `wow_classic_beta` and `wowf`
- wago's `/api/builds`
- the Raidbots Last-Modified, plus the header build from a 44-byte range GET
- the QuestieDB master commit

It prints one line per upstream and exits 1 when anything is stale. README "Rebuild the data" runs it first. It never
imports or updates anything by itself, because auto-update is Out.

## Task 20: in-game collector addon (M; two halves)
**Placement.** The addon half starts now, in parallel and in a worktree, since it touches no Java. It must be ready
for the owner by 19 Oct, because the beta ends about 22 Oct. The importer half runs after Task 19 and before Task 16.

**What the addon records,** account-wide SavedVariables `WowForeverCollectorDB`, schema 1:
- **`session`:** build (GetBuildInfo), date, addon version. No character name, realm or guild anywhere in the file.
- **`loot`:** on LOOT_OPENED, each slot's item link and quantity, and GetLootSourceInfo's source GUIDs reduced to
  type and id (Creature or GameObject). Also instance name, mapID and difficulty (GetInstanceInfo), uiMapID and
  player x,y, de-duplicated per source GUID so a corpse counts once.
- **`encounters`:** ENCOUNTER_START and ENCOUNTER_END with encounterID, name and success. These confirm DungeonEncounter bosses.
- **`vendors`:** on MERCHANT_SHOW, the NPC id, then each item's link, price, stack, limited stock and extended cost.
- **`trainers`:** on TRAINER_SHOW, the NPC id, then each service's name, cost, required level and required skill.
- **`quests`:** on accept and turn-in, questID, giver or ender id and type, objectives text, rewards, uiMapID and x,y.
- **`apiFailures`:** every call that returned a secret or nil value where data was expected, with the call name and
  where it happened. Learning what the Forever API hides is half of this beta run.

**Importer half:**
- A new option `--collector-file <path>`, repeatable. The owner passes the path explicitly, as an opt-in import
  (backlog decision 23). It is parsed with the existing lua-table parser.
- The new provenance source `collector` reads "Observed in game".
- First scope: loot sources become presence rows on `item_npc_drop` and `item_object_drop`, with no rate, plus
  encounters. Vendors go into `item_vendor`. Trainers and quests are kept raw in `collector_observation` for later
  features.
- An observation upgrades a Reported fork row to Known.

**Tests:**
- A fixture SavedVariables file imports deterministically.
- The schema has no name, realm or guild fields.
- Duplicate source GUIDs count once.

**The owner's run plan:**
1. 19 Oct: the owner copies the addon folder into the beta's `Interface/AddOns`. That is the owner's own action on
   their install; the app never writes there.
2. Session 1, about 1 hour:
   - One run of City of Dalaran (level 28+) or Excavation Site (26+).
   - One vendor and one trainer in a city.
   - Accept and turn in one quest.
   - `/reload`, then log out to flush.
3. The owner shares `WTF/Account/<account>/SavedVariables/WowForeverCollector.lua`. The controller imports it into a
   scratch DB and reports which APIs worked.
4. 20–21 Oct, session 2, only if fixes were needed: another dungeon, and one Blueprint drop if seen.
5. Acceptance: at least one Forever boss → item row "Observed in game" shows on the Task 18 page for that dungeon, or
   `apiFailures` explains why not.

## Backlog v5 rows
| Feature | Value | Effort | Data | Rel. | Decision |
|---|---|---|---|---|---|
| Hidden-item overlay (Raidbots DBCache) | H | M | Raidbots mirror | 0.3.0 (T19) | In |
| Build-watch script | H | S | patch service, wago, Raidbots, QuestieDB | 0.3.0 (T19) | In |
| AtlasLoot Classic Forever replaces Hoizame | H | S–M | fork, GPL-2.0 | 0.3.0 (T19) | In |
| Collector addon + import | H | M | own observations | 0.3.0 (T20) | In |
| QuestieDB re-pin weekly and at launch | H | S | QuestieDB | process | In |
| CDN/CASC mining (port the wowsims db2tool design) | M | L | patch service, CDN | — | Later: design page now; build only if wago fails or changes terms |
| Rates from aggregated collector data | M | M | collector files | — | Later, after launch |
| CMaNGOS disagreement report | L | M | GPL-3.0 | — | Later; never in the shipped file |
| Wowhead, fan sites, Blizzard Game Data API as shipped sources | — | — | — | — | Out: test oracle or cross-check only |

Other backlog updates:
- v4's "DBCache import spike" (v0.4.0 item 7) shrinks to a fallback note. The aggregated cache covers what one
  machine's cache would.
- Parking-lot paths (c) and (d) gain "adopted: Raidbots overlay, collector".
- Open question 2 (aggregators) is now settled for the Raidbots cache by the controller's ruling. It is reported to
  the owner as a decision.

## Product risks
- **Raidbots is a single, unstated dependency.**
  - The file has no terms, is overwritten in place, and has no known path for the launch product `wowf` (404 today).
  - Mitigations:
    - Snapshots are hashed and kept.
    - Revealed rows accumulate, so an outage freezes our data but never loses it.
    - The build-watch flags a missing or changed path.
    - Fallbacks are the owner's own cache through the opt-in import and the CDN design.
  - At launch (4 Nov) we must find the `wowf` path, or the overlay stops growing.
- **The fork's data origin is unstated.**
  - If its rates or loot were scraped from Wowhead, GPL-2.0 cannot license them.
  - Mitigations:
    - Rows are labelled "AtlasLoot Forever" and "unverified" on Forever bosses.
    - They never outrank Questie.
    - Task 19 spot-checks 10 fork rates against the Wowhead tooltip test oracle and records the result. If they
      match Wowhead to the decimal across the board, the controller tells the owner before release.
- **Legal footing.** Shipping hotfix rows is the same act as shipping client tables, which is the owner's standing
  call (ROADMAP caveat). It is new only in that it reveals what Blizzard hid until first drop. The footer says so
  plainly.
- **The beta deadline.** If the addon slips past 19 Oct, the beta run is lost and the collector waits for launch. If
  Forever's API hides loot sources in new dungeons, `apiFailures` documents it and the collector scope shrinks to
  vendors, trainers and quests.
- **Trust.** Hotfix data is beta data. The footer names the beta build until the live re-import on 4 Nov.
