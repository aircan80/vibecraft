# WoW Forever GUI review and proposed refresh

17 October 2026 · Current app: `52e144e` (v0.4.1 development candidate).

This review combines six AI role-based perspectives: desktop navigation, visual design, accessibility, leveling/exploration, dungeon/loot planning, and prototype design. It is source inspection and screenshot-based task analysis, not a study with human WoW players. The production Swing app is unchanged.

## Start here

Open the [interactive mockups](mockups/index.html). They propose a coherent direction rather than a finished replacement GUI. The prototype's sample filters, selection, history and source disclosures demonstrate interaction; they do not query the app or save a character profile. Features absent from the current app are proposals.

The direction is an **explorer's journal**: keep the genuine maps, Slate/Parchment themes and Warcraft identity, then give every page a clear task and a consistent navigation model. Improve hierarchy before adding ornament.

[Rendered previews](mockups/previews/README.md) include desktop, compact and light-theme examples. A separate [NPC portrait/model feasibility review](../npc-render-research-2026-10-17/README.md) covers the requested native-data rendering investigation.

## Recommended priorities

| Order | Improvement | Why it matters | Proposed behavior |
| --- | --- | --- | --- |
| 1 | Label the sidebar and extend breadcrumbs | Seven icons currently require learning by hovering; deeper systems lose visible parent context. | Named destinations; optional collapsed rail; a compact navigation drawer; real parent breadcrumbs distinct from Back/Forward. |
| 2 | Replace the default Forever change feed with a player Home | More than 30,000 source-record changes compete with finding a dungeon, item or build. | Task shortcuts, recent pages and optional favorites; retain the complete diff browser under **What changed?**. |
| 3 | Unify dungeon browsing and preserve research context | Generic Instances and the Forever ladder are competing entrances; returning can lose filters. | One Dungeons & Raids index, meaningful filters, stable selection/scroll, and Overview / Bosses & loot / Maps / Quests. Keep the selected boss and loot together. |
| 4 | Put decisions first and evidence within reach | Repeated builds, paths and confidence panels push useful content down; distant chips are hard to associate with values. | Compact facts beside the item/encounter; persistent Reported/Hidden/Known labels; **Why this value?** for source conclusions, then optional technical detail. |
| 5 | Make planning look like planning | Talent IDs, raw Legacy fields and reference tables obscure the interactive work. | Source-supported talent tree plus accessible list; Legacy perks/rewards first; Camping composer before its reference catalog. |
| 6 | Connect quests to geography | Maps already provide the strongest visual experience. | Keep drill-down; pair zone quest lists with maps and explicit Start / Objectives / Turn-in modes; make route and entrance controls clear. |

All are high-value usability proposals. The reviewers' severity labels are their estimates, not measured failure rates. The recommended first implementation slice is **shell + Home + one dungeon/item journey**; validate that pattern before reskinning every page.

## Proposed navigation

- **Home**: start tasks and resume browsing.
- **World**: map hierarchy, zones and quest geography.
- **Dungeons & Raids**: the single dungeon index and boss research.
- **Gear**: items, sets, trinkets and stat browsing.
- **Build planner**: talents, Legacy, races and class spells.
- **Professions & Supplies**: camping, recipes, XP food and scrolls.
- **What changed?**: the full Forever/Era comparison browser.

Global search remains available everywhere. Search scope and local list filters have distinct labels. PvP can initially sit under World. A planning level/faction/class is explicitly hypothetical; it must never imply a detected character or saved account progress.

## Details to preserve

- Clickable Azeroth → continent → zone hierarchy, authentic map art and working history.
- Offline browsing and original per-source evidence.
- Reported addon loot is distinct from verified observation. An absent listing is missing coverage, not a contradictory drop claim.
- Hidden, Not yet discovered, Reported and Known stay distinct; numeric zero remains a real value.
- Hotfix history is not automatically a present-day conflict. Classic Era values remain clearly separate from Forever values.
- Unknown level restrictions, entrance positions or talent rules must not be guessed to make a mockup look complete.
- Planner calculations remain hypothetical, with no account progress read or saved.

## Scope and delivery

**First pass:** labelled navigation, shared headings/breadcrumbs, task Home, state restoration, compact facts and evidence disclosure. These establish patterns using current data. Favorites need their actual listing/restoration implementation; a mockup pin alone does not deliver persistence.

**Second pass:** one dungeon index and boss/loot workspace, map/quest pairing, filterable references. Proposed equipment/class filters require source coverage and explicit handling of hidden rows; they are not just cosmetic controls.

**Third pass:** a genuine topology-based talent view, Legacy organization and Camping planner layout. Keep an accessible list option. Validate constraints against native rules rather than drawing decorative prerequisite lines.

## Acceptance before production rollout

1. From Home, find a new dungeon, select a boss, open an item and go Back without losing filters, selected boss or scroll.
2. Drill Azeroth → Eastern Kingdoms → Westfall; a parent breadcrumb goes up the hierarchy, while Back retraces visit history.
3. Explain why a loot row is Reported and which source supplies an item value without reading a snapshot hash. Technical evidence remains copyable.
4. Allocate a hypothetical talent and identify its cost/prerequisite; unsupported rules have an explanation and cannot be bypassed.
5. At narrow width and 150–200% scale, keep navigation and primary actions reachable, avoid page-level horizontal scrolling and expose full truncated values.
6. Complete the same tasks by keyboard with visible focus and focus restored after closing a drawer.
7. Confirm with actual WoW players: observe findability and backtracking on the current app versus prototype. Do not treat this expert review as measured user validation.

## Individual reviews

- [Navigation and information architecture](navigation.md)
- [Visual direction and tokens](visual.md)
- [Leveling and exploration player perspective](leveling-player.md)
- [Dungeon, loot and build-planning player perspective](loot-player.md)
- [Keyboard and accessibility review](accessibility.md)

## Evidence limitations

The review uses current source and the v0.4.1 acceptance captures. Several are page-only captures. The native Westfall capture records a transient Loading state, not a settled blank zone. Missing table headers in headless images may be a Swing lifecycle artifact; they are not confirmed production defects. The earlier native X11 interaction checks cover maps and history, not every new planner interaction. Prototype previews are design evidence, not product acceptance tests.
