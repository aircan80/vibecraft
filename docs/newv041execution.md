# Development through v0.4.1: acceptance ledger

Updated 2026-10-17. Authority: product backlog v5, the phase 3 design and approved source-data/map/collector contracts. The owner authorized implementation through v0.4.1. This ledger describes integrated source and candidate evidence; final verification/capture results appear below. Implementation completion does not close the external collector gate.

## Confirmed source and candidate evidence

| Scope | Evidence and accepted behavior |
|---|---|
| Existing quest/browser features | Existing quest objectives/maps, giver/turn-in navigation, chain semantics, patrols, merged drops, themed chrome and ATT Sleeping Bag relationships are retained. Native prerequisite evidence remains authored, never represented as direct server verification. |
| Source preservation | Capture and offline projection remain separate. Targeted foundation, Questie correction, hotfix, native completion, travel and Atlas workflows preserve original evidence and unrelated selected application facts. The unused composite CLI was removed. |
| Source comparison | Item and boss surfaces expose actual rate alternatives, selected versus provider values, addon claims, versions and evidence locations. Missing coverage and temporal updates remain distinct from genuine value conflicts. |
| Knowledge | Six field states and assertion history are integrated. 87 selected Known assertions were repaired. Missing tooltip lines alone never assert Hidden; Classic Era values stay separate from Forever assertions. |
| Native foundation/diffs | Candidate foundation contains 69 Forever and 27 Era tables, explicitly paired by snapshot/build. New/Changed comes from computed native row comparisons; unavailable or partial baseline coverage cannot establish novelty. |
| Native item pages | F4 metadata supplies 10,593 source-backed native IDs. A later completion filled 3,566 missing cards; the completion summary reports zero native sparse rows still without a card and zero foreign-key violations. |
| Hotfix precedence | Targeted hotfix projection preserves beta evidence and existing unrelated facts. Controls 869,273643, 286978 remain hotfix; 16806 remains Classic Era. Hidden versus revealed members are chosen from actual evidence, rather than forcing the stale all-Hidden Rotmender example. |
| World drill-down | Azeroth/continent/zone navigation retains explicit source geometry, keyboard/focus/history and Map/List behavior. Unplaced islands remain accessible without invented rectangles. Overlapping smaller city regions take precedence in hit testing. |
| Travel layers | Candidate inventory reports 76 public flight nodes and 295 connections, authentic instance entrances/exits and nested-instance context. Route fragments are clipped without false connecting lines. Westfall evidence includes Deadmines and Thor 523. |
| Coordinate acceptance | The declared 21-NPC corpus contains 20 comparisons agreeing within one map point, fulfilling the backlog's exact twenty-NPC criterion. The additional Hogger comparison differs by 1.406 points and remains a documented exception; no patrol waypoint is substituted for its spawn. |
| Atlas floors | Seven genuine maps, fifty legend rows and exact supported NPC links are integrated. Original source archives/artifacts and GPL text are retained. Captions credit Dan Gilbert, Lothaer, Arith Hsu and the Atlas Team. The app boss list remains authoritative; no invented image pins. |
| Talents | Native TraitSystem 10 browser/calculator supports nine real class-linked trees, 51-point cap, rank/cost/prerequisite rules, 11/16/21/31 milestones and hypothetical build strings. Native spell descriptions qualify unresolved formulas. Beta content above 30 remains explicitly unconfirmed. |
| Legacy | One 16-point allocation spans trees 1187–1189; 131 source-linked challenge records and Covenant 48 reward track are available. Ruleset 60/61 retain neutral source labels until their gameplay meaning is verified. |
| Races | Skyborne 95/96 each use native five-class allow-lists. Race pages include native racials, start geography and pinned Classic comparison; unplayable combinations are excluded. |
| Camping | 36 distinct source-backed camps, campfire tiers, named Blueprints and profession/tier matrix support a hypothetical party eligibility composer. Coverage does not assume recipe ownership, unverified buff values or stacking rules. |
| Stats and class spells | Named stat glossary and has-stat filter preserve hidden/Era exclusions. Class spell comparisons use explicit native/Era identities, actual source levels and honest unavailable-baseline handling. |
| Forever content navigation | Forever hub, New/Changed filters/context, dungeon/raid ladder, discovery and PvP/faction pages are integrated. Announced dates and sizes cite reviewed curated evidence, separately from computed diffs. Beta bands are not final launch bands. |
| Discovery confidence | Direct DungeonEncounter facts are Known; achievement-only boss evidence remains Reported. Hyjal 2981's Wild King is never upgraded merely from an achievement/name. Instance pages contain no name-matched item block. |
| Item content | Set membership/bonuses and pinned Classic comparison, situational trinkets, recipes by skill band, XP food, Comprehension lists and shared appearance are integrated. Restrictions use verified source relationships; unclassified situations remain explicit. Recipe graphs remain outside this slice. |
| Hypothetical input | Talent, Legacy and camping planners save no character progress or profile. Browsing stays offline and does not read the game installation. DBCache remains an explicit read-only fallback import, not a runtime feature. |

## Genuine external collector gate — open

No genuine owner beta SavedVariables capture has been supplied for final acceptance. Handwritten fixture projection and synthetic page captures cannot establish observed server facts. The gate requires at least one genuine Forever boss/item presence observation, or genuine API failure evidence explaining why capture failed.

Collector tooling retains direct source evidence, raw noncorpse/unknown/failure observations and snapshot lineage. Existing rates remain unchanged. A recent-kill encounter hint is not boss attribution. Addon reports, beta hotfix data and observed presence keep separate provenance. No synthetic collector observations may be shipped as real evidence.

## Explicit data and review limits

- Native card coverage is complete for the selected visible ItemSparse rows; complete coverage does not establish every derived stat/damage formula. Eleven unresolved test/monster/deprecated/QA rows remain: 4996,5410,6896,13608,19122,21101,21102,21516,21518,22391,274683. Nine have unresolved damage and two unresolved stats. Exact details remain in `.local/v041-hotfix-acquisition/native-unverified-details.txt`.
- Classic talent comparison is an exact spell-ID/rank comparison, not a claim of complete semantic spell-text equivalence. Every comparison names both retained builds.
- Missing custom-instance entrances, unplaced islands, unclassified trinket conditions, unavailable Blueprint names and unknown PvP vendor stock stay explicit; none is filled by name guessing.
- The twenty passing coordinate examples satisfy the numbered criterion. Hogger's additional outlier remains relevant to global transform accuracy; the corpus is not a representative proof for every spawn.
- Native desktop input acceptance under Wayland has historical limits. Headless bounds/Enter checks and image capture do not independently prove every native mouse/keyboard interaction.
- All dates/build labels describe retained evidence. Beta 70450 is never called live; no future live snapshot or external app release deadline is invented.

## Reproduction and evidence locations

README documents targeted capture/project commands, including `complete-native-client-items`. Travel is documented in `docs/MAP_LAYERS_IMPORT.md`, hierarchy in `docs/WORLD_MAP_IMPORT.md`, Atlas preparation/candidate import in `tools/atlasmaps/README.md`. Keep exact selected snapshot IDs and transformation versions with the final publication record. Candidate publication must validate integrity, archive retention, unrelated ordinary-table preservation and build identity before atomic replacement.

Read-only completion evidence: `.local/v041-hotfix-acquisition/native-completion-summary.json`, `.local/v041-hotfix-acquisition/native-unverified-details.txt`, `.local/v041-integration/knowledge-repair.log`. Older per-agent focused passes support development; the final integrated verification below supersedes them for the selected source revision.

## Final integrated verification and visual acceptance

Full verification after the final Classic Era bonus-provenance correction passed: **1,841 tests, six skipped, zero failures/errors**. Modules: Lua 71(one skipped), importer 582(five skipped), app 1,188(none skipped). Log: `.local/v041-final-verify.log`. Separate Python suites passed 122 clientdata and 16 serverdb tests from their respective working directories.

Candidate identity: `40ca42c8e8c6442c0e2e633f76bf4f4638ebf23faf4029b568ee1ee038c207b7`. Frozen review JAR SHA256: `a2c6379e6d67e3b0ff8b44f2e2a0dc4e40628216973b7d710d04ee1b981f7a9f`.

Final captures passed against the frozen review JAR: **299 images** (294 headless and five native). Standard pages: 94 panels/22 cases, zero geometry failures, maximum warm-load p95 106.968 ms. Catalogs: 76 panels/19 targets, zero geometry failures; cached repository load maximum 0.005715 ms (this is not a separate catalog render benchmark). World maps: 12 panels, query/decode/construct/render p95 31.03 ms. Travel: 16 panels, p95 48.95/27.92 ms. Source evidence: 96 captures without horizontal overflow; database unchanged. Five real JFrame/Robot screenshots under X11 accompany eight passing interaction checks covering focus, Tab, Enter, hover, coordinates, click, Alt+Left twice and Alt+Right. No Wayland interaction claim is made.

The coordinator visually reviewed hidden 287012, hotfix 286978, Era 16806 comparison, hub, race 95 and race grid, compact camping, full talents, compact Legacy, Reported Hyjal and light-theme set 2133. Genuine owner collector beta-capture acceptance remains the external open gate. Unresolved source formulas and the eleven native test/monster/QA stat/damage rows remain documented data limits, not invented facts.

The development verification and reviewed captures above are complete for this candidate/JAR. The genuine collector gate remains open independently; no synthetic observation is promoted to close it.

Catalog worker loading plus actual EDT panel construction also passed for all 19 targets: 30 measurements after three warmups, maximum combined warm p95 **98.53 ms**. Cold loads are separate: maximum 10.40 seconds, with asynchronous loading UI. Evidence: `.local/v041-acceptance/catalog-performance.json`. The verified JAR is running as the local `wowforever-browser.service`; installed database identity matches this candidate. Machine-readable results are in `docs/verification/v0.4.1.json`.
