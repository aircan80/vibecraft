# Product backlog and decisions, v1

Date: 2026-10-17. Owner: product manager. Inputs: phase 3 design spec (incl. addenda §10–11), ROADMAP, memos from the WoW
expert, BA and UI designers 1–3, plans 2B and 3A. The three research reviews (loot/raids, professions/economy,
lore/exploration) are pending and will produce v2.

## 1. Product principles
1. A **research and learning tool** in the spirit of Wowhead: it describes the world (what exists, where, who, how
   often, what leads to what). It never prescribes what *you* should do next and never tracks progress.
2. **Offline and self-contained**: ships its own database; no network at runtime, nothing reads the game client.
3. **Trust through provenance**: every rate names its source; disagreements are shown, never hidden; unknown says unknown.
4. **Glance while playing**: the answer is above the fold at 1280×800 and works in compact mode (420 px).
5. **WoW idiom, readability first**: game cues players recognise, no themed-toy gimmicks, nothing under 4.5:1.
6. **Quality bar**: no feature ships without headless tests, shipped-data tests on named ids, review, and screenshots.

## 2. Release train
Quality gates for every release: `./mvnw -B -q verify` green; per-task spec + code review and a final branch review;
screenshots of the release's acceptance examples in dark, light and compact; warm page load ≤ 300 ms p95 on the
shipped DB; small text ≥ 4.5:1 in both themes; import report has no new unsupported/missing counters unexplained.

| Release | Theme | Scope | Must be true to ship |
|---|---|---|---|
| **v0.2.0** (phase 2B, finishing) | Companion-app foundation | Dark theme, detail-first layout + rail, search ranking, rebuilt pages, type-ahead + paste, list pages, keyboard map, compact mode, tooltip card + icons + quality colours, zone maps with pins, provenance markers + source filter, zone "!" giver pins | Gates above; README screenshots; Task 15 pins open quests (one → page, several → pick list) |
| **v0.3.0** (3A data + 3B UI) | Quests on the map, the WoW look | Quest page redesign, quest chain, objective pins, giver/turn-in pins, patrol routes, client-art rail icons, WoW chrome + Marcellus, Parchment theme, IA clean-up, merged Dropped by, master-list auto-collapse, community drop rates, coordinates + copy-to-game | BA scan test passes on the five acceptance quests; chain tests cover AND/OR, exclusive, breadcrumb, cycle, missing id; icons verified against the listfile; VMaNGOS licence in NOTICE |
| **v0.4.0** | Research depth | Crafting (created by / reagent for / teaches), dungeon prep tab, vendor/trainer finder, vendor stock, set browser, Home dashboard, zone rares filter | Folded-in findings of the three research reviews; shipped-data tests for each acceptance example |
| **v0.5.0** | Glance and compare | Compact pinned tabs + always-on-top, item comparison, "what changed in this build" | Compact screenshots at 420×720; build diff verified on two real builds |

## 3. Prioritised backlog
Value = to a researching player. Effort S ≤ 1 task, M 2–3, L 4+.

| Feature | Value | Effort | Data | Release | Decision | Rationale |
|---|---|---|---|---|---|---|
| Quest page redesign | H | L | 3A objectives, relations | 0.3 | In | Answers pick-up, do, hand-in, next without scrolling |
| Quest chain (stepper + tree) | H | M | quest_relation | 0.3 | In | AND/OR prerequisites are the hardest thing to find in-game |
| Objective pins | H | M | objectives, spawns, drops | 0.3 | In | "Where do I do this" is the top reason to open a quest |
| Giver/turn-in pins (quest + zone "?") | H | S | quest_giver | 0.3 | In | Zone "!" ships in 0.2; "?" completes the pair |
| Patrol routes | M | S | npc_waypoint (3A T7) | 0.3 | In | Owner request; finds roaming named mobs |
| Client-art rail icons | M | S | ui_asset (3A T4) | 0.3 | In | Owner request; instant recognition |
| WoW chrome + Marcellus headings | M | M | none | 0.3 | In | Token-driven, cheap, unifies pages |
| Parchment light theme | M | S | none | 0.3 | In | Contrast-checked tokens already specified |
| IA clean-up (skeleton, hide empty, positive facts, counts) | H | M | none | 0.3 | In | Removes noise on every page |
| Merged Dropped by + section filter + "≠" legend | H | S | drop_rate_source | 0.3 | In | One question, one answer, sorted by rate |
| Master-list auto-collapse | M | S | none | 0.3 | In | Gives the page 300 px back |
| Community drop rates | H | M | VMaNGOS (3A T9) | 0.3 | In | Replaces "rate unknown" on most drops; existing provenance UI |
| Coordinates + /way + in-game links | H | S | none | 0.3 | In | Bridges research to the game; tiny |
| Crafting created-by / reagent-for | H | M | craft tables (3A T8) | 0.4 | In | Data lands now; UI waits for the professions review |
| Dungeon prep tab | H | M | relations, objectives | 0.4 | In | Lists quests tied to an instance; waits for the loot/raids review |
| Vendor/trainer finder | H | S | npc.npc_flags | 0.4 | In | Data shipped; Role filter + Services map toggle |
| Vendor stock completeness | M | S | VMaNGOS npc_vendor | 0.4 | In | Fills "Sold by" gaps; provenance applies |
| Set browser | M | S | item_set (shipped) | 0.4 | In | Classic research staple; data already shipped |
| Home dashboard | M | S | build counts | 0.4 | In | Pinned + Recent + build facts; random featured item Out |
| Zone rares filter | M | S | npc flags, spawns | 0.4 | In | Research half of a "rare tracker" |
| Compact pinned tabs + always-on-top | H | M | none | 0.5 | In | The "glance while playing" mode |
| Item comparison | M | M | item stats | 0.5 | In | Side-by-side stats; no character needed |
| What changed in this build | M | M | previous build DB | 0.5 | In | Forever is a moving beta; diffs are research gold |
| Mini tooltip window | M | M | none | — | Later | After pinned tabs prove themselves |
| Notes / favourites | L | S | local prefs | — | Later | Pinned already covers favourites |
| Upgrade finder | M | L | stat weights | — | Later | Needs a character and opinion weights; list filters cover the research half |
| Localisation | L | L | locale tables | — | Later | Owner and data are English-first |
| 3D models | L | L | asset pack | — | Later | Size and art redistribution; ROADMAP A4 |
| Rare spawn timers | L | M | live data | — | Out | Needs network; a tracker, not research |
| Farming planner (routes, gold/hour) | L | L | — | — | Out | Prescriptive guide; Dropped by + spawn counts answer the research question |
| Auto-update | L | M | network | — | Out | Breaks offline; releases carry the data |
| Quest progress ("3/10"), next-quest advice, level routes | — | — | — | — | Out | Leveling guide (decision 8) |

## 4. Decisions log
1. **Objectives + map above Given by / Turned in to** (UI-1, WoW expert). The BA's concern is met by the stepper and
   the "!"/"?" pins visible above the fold; the scan test is the arbiter.
2. **No objective counts.** Questie has none; inventing them breaks principle 3.
3. **Rail icons from client art on navy plates**, SVG only as fallback for an unverifiable texture. Owner ruling.
4. **Marcellus SC headings, Inter body.** Cinzel is the fallback; Friz Quadrata never ships (commercial).
5. **Parchment replaces the current light theme**; dark "Slate" stays the default.
6. **Home dashboard moves to v0.4.0**, not v0.3.0, because v0.3.0 is already the largest release and Home is low-risk polish.
7. **Merged Dropped by**: instance loot joins NPC drops as chips; the source filter applies to NPC rows only and a
   footnote states how many rows it hides.
8. **What "leveling guide" means here**: a feature is Out when it needs your character, your progress or a
   recommended order. Describing quests by level, zone and chain is research.
9. **Community drop rates ship in v0.3.0** because the data arrives in 3A and the provenance UI already exists.
10. **Crafting and dungeon UI wait for v0.4.0** so the professions and loot/raids reviews shape them; their data still lands in 3A.

## 5. Open questions for the owner
1. Should the app know a character (level, faction, class) to grey out low-level "!" or other-faction quests?
   **Default:** no; faction chips and level text only.
2. May a VMaNGOS rate, which reflects vanilla, be the headline number where Questie has none? **Default:** yes,
   labelled "community (VMaNGOS)" in the tooltip; Questie wins when both exist.
3. Is replacing the current light theme with Parchment acceptable? **Default:** yes.
4. May later features ship more client art, such as item-set or profession icons? **Default:** icons only, never frames or textures.
5. Should v0.3.0 ship as one release or as quest page first, then chrome? **Default:** one release.

## 6. How I work
- New expert input produces v(n+1) of this file with a changelog entry below.
- A feature enters a plan only from this backlog with decision In and a release.
- Every feature carries BA-format acceptance criteria (numbered, testable, with edge cases) and a named acceptance
  example for the visual check: quests 176, 6502/6602, 7787, 7791, 166 (Defias Brotherhood chain); items 1931
  (Huge Gnoll Claw), 19019 (Thunderfury), 4234 (Heavy Leather); NPCs 448 (Hogger), 550 (Defias Messenger); The
  Deadmines; Elwynn Forest.
- A release ships only when every gate in §2 is met; a failed gate moves the feature, never the bar.

## Changelog
- v1 (2026-10-17): first backlog from the phase 3 spec, ROADMAP and five panel memos.
