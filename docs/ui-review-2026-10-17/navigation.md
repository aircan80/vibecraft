# Navigation and information architecture review

Simulated expert review, 17 October 2026. This is a source and screenshot inspection by an AI acting as a senior desktop UX/information architect; it is not a human usability study. No production code changed.

## Evidence inspected

- Full application shell: `.local/v041-acceptance/native-final/02-eastern-kingdoms.png` and `04-westfall-zone.png`.
- Page captures: `catalog-final/hub-0-dark-1216.png`, `hub-0-dark-420.png`, `ladder-0-dark-1216.png`, `talents-0-dark-1216.png`, `standard-final/instance-34-slate-full.png`, and `item-287012-slate-full.png`, all under `.local/v041-acceptance/`.
- Source: `ui/MainFrame.java`, `ui/SearchBar.java`, `ui/AppController.java`, `ui/widgets/Rail.java`, `RailTarget.java`, `nav/ViewTarget.java`, and `ui/pages/ForeverHubPage.java`.

Page captures do not include the application shell. The Westfall native screenshot catches an asynchronous loading state; it is not evidence that the finished zone page is blank. Findings below distinguish observations from interaction risks inferred from code.

## Prioritized findings

| Priority | Finding and evidence | Proposed improvement |
| --- | --- | --- |
| P1 | Primary destinations are seven small, unlabeled icons. `Rail.WIDTH=48`; names exist only as tooltips/accessibility labels. The native Eastern Kingdoms screenshot makes Items, Forever and Home hard to distinguish without hovering. | Default to an expandable 184–208 px labeled sidebar with familiar icons. Keep a deliberately selected icon-only mode. Group destinations under Explore and Plan; visibly label the current section. |
| P1 | The Forever start page immediately exposes a 30,932-result technical change feed. At 420 px, counts, 13 links and filters consume about 490 px before the first result. Native row differences are truthful, but they are a poor default answer to “what can I do here?” | Make the landing view a task hub: Explore dungeons, Find gear, Plan a build, Explore the world. Put snapshot differences behind a named **What changed?** destination, keeping full coverage and raw table access available there. |
| P1 | Two competing dungeon entry points: rail Instances opens `ViewTarget.List(INSTANCE)`, while Forever **Dungeons & raids** opens `Forever("ladder")`. The ladder includes repeated indistinguishable Scarlet Monastery rows. | Use one Dungeons & Raids index with shared source-preserving filters: All / New in Forever, level, dungeon/raid. Display wing/context where actually known; otherwise expose distinct recorded level bands and IDs in details rather than invent wing names. Route both entry points into this one index. |
| P1 | Item research catalogs are discovered through Forever quick links, but `AppController` selects the Items rail when one opens. The user’s apparent section changes without an equivalent visible subsection path. Talent/Legacy pages have no obvious local parent in the page captures. | Add stable section navigation and breadcrumbs: **Gear > Item sets > Set name**, **Build planner > Talents**. Use location breadcrumbs for parent navigation and Back/Forward for visit history. Preserve existing geographic breadcrumbs. |
| P1 | Forever hub category, query, result page and status are local constructor variables. `ViewTarget.Forever` only records section/id; rebuilding the page restores defaults rather than the same filtered result set. Back after opening a result is at risk of losing research context. | Store catalog query/filter/page in navigation view state, plus selected row and scroll. Back should return to the same item and result position. Keep a sidebar section click as an explicit route to the section’s default index. |
| P1 | Talent planner is presented as a flat table of names, rank zeros and raw IDs; the inspected capture has no visible table headings. Allocation requires selecting a row then moving to wide + Rank/− Rank controls. This hides relationships that matter to WoW players. | Present an icon tree with visible tiers, prerequisite lines, rank badges and contextual point allocation. Retain an accessible labeled list/table view. Keep unresolved formulas and beta limitations visible in the selected talent details; do not invent numeric effects. |
| P2 | Wide dungeon detail combines an encounter scroll pane, a separate right-hand scroll area and an Atlas disclosure above them. The Dalaran capture shows Quests competing with the selected boss’s loot beneath extensive introductory text. | Use dungeon-local **Overview / Bosses & loot / Maps / Quests** views. Keep the boss selector and loot together, with a clear **3 reported items** summary. Select a boss without resetting map/floor or overall page context. Preserve source badges next to loot and source inspection on demand. |
| P2 | Full-window search hides its entity-kind filter in the dropdown. Enter opens the selected/top result; Shift+Enter or Show all runs a results search. These behaviors are implemented, but the distinction is easy to miss. | Add a visible scope control and persistent hint **Enter: open · Shift+Enter: all results** while suggestions are shown. Separate global search from local **Filter this list** controls with distinct labels. Retain supported pasted item links/URLs. |
| P2 | Unknown item pages repeat a large amount of uncertainty labeling, and knowledge-state chips sit far from field labels at wide widths. The source-state distinction is valuable, but it competes with the item’s few known attributes. | Begin with a compact summary: **Name hidden · Hands · Plate · Set: Krol’dok Battlegear**. Group missing fields in a compact two-column fact card. Keep exact Hidden/Undiscovered/Reported differences and their explanations; do not collapse them to a generic Unknown. |
| P2 | Technical metadata and qualification text dominate several headings: raw Spell IDs, native paths and snapshot counts appear before the player can identify their next action. | Present task content first and a concise, persistent evidence badge with build/scope. Keep complete paths, raw fields, formulas and temporal provenance under **Data & sources**. Important limitations such as reported loot or an unverified entrance remain adjacent to the relevant content. |
| P3 | The native map screenshot clips several zone labels, and a large area at right remains unused. Breadcrumbs and direct map interaction are already strong. | Keep the current map identity and hierarchy. Improve label placement/hover readability, expose Fit/Zoom and a compact layer legend, and use available width for optional zone/dungeon context rather than stretching the source map. |

## Proposed information architecture

Primary sidebar (labels shown by default):

- **Home** — task shortcuts, recent pages and optional locally stored favorites.
- **World** — continent/zone hierarchy, flight routes and entrances.
- **Dungeons & Raids** — one canonical index, New in Forever and level filters, consistent detail views.
- **Gear** — Items, Item sets, Situational trinkets, Stats and appearances.
- **Build planner** — Talents, Legacy, Races and class spells. Builds remain explicitly hypothetical and transient until persistence is intentionally implemented.
- **Professions & Supplies** — Camping, Recipes, XP food and Comprehension scrolls.
- **What changed?** — the full evidence-based Forever vs Era browser with coverage disclosures and advanced raw-table mode.

Global search stays available across all destinations. Data/source inspection is a secondary action on every applicable entity, not a replacement for browsing. PvP can sit under World initially, or become its own destination if its content grows.

## Flows to prototype first

1. **Find a new dungeon and its loot:** Dungeons & Raids → New in Forever → select Hall of Thanes → Bosses & loot → boss → item → Back returns to that boss and preserves dungeon filters.
2. **Explore an area:** World → Azeroth → Eastern Kingdoms → Westfall → a recorded dungeon entrance → dungeon Maps. Geographic breadcrumb goes up the map hierarchy; Back returns to the exact map view.
3. **Build a character:** Build planner → class → talent tree → assign points → inspect Legacy → return to talents without losing the hypothetical allocation. Unsupported rules remain unavailable with an explanation.
4. **Investigate conflicting evidence:** Item or boss → reported/selected-source badge → source comparison → inspect build/date/snapshot → close the evidence panel and resume the same list position. Missing coverage must never read as a conflicting value.

## Prototype acceptance checks

- From Home, all four core tasks above have a visible named entry without icon guessing.
- Primary and secondary navigation remain usable at desktop width; compact mode has one clearly labeled navigation drawer and a single primary content surface.
- Every deep destination has a visible section/parent path; Back and parent navigation perform distinct actions.
- Opening and returning from a result preserves query, filters, page, selection and scroll.
- A player can distinguish native Known data, addon Reported loot, Hidden values and missing coverage without opening raw JSON.
- Keyboard users can discover navigation labels, move across a talent tree/list, open source evidence and return focus to its trigger.
- Validate prototypes with representative users before committing to a major shell rewrite. The current review predicts friction; it does not establish measured success rates.

## Suggested delivery order

1. Low-risk polish: labeled navigation, coherent page titles, local breadcrumbs, clear scope labels and state restoration.
2. Consolidate the dungeon index and redesign Home around player tasks; retain full technical change browsing on its own page.
3. Prototype dungeon tabs and the visual talent planner with screenshots/clickable mockups, then test real player tasks before production implementation.

