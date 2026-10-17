# Part 1 gate fix wave (findings from the controller's visual checks)

Worktree `H:\claude_java\.worktrees\phase2b-ui`, branch `feature/phase2b-ui`. Report file:
`H:\claude_java\.worktrees\phase2b-ui\.superpowers\sdd\2026-10-16-phase2b-ui-overhaul\part1-gate-report.md`.
Screenshots behind each finding: `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\tour2b\` and `...\tour2b-gate\`.

## Findings (all must be fixed, each with a headless test)

- **(a) Search → master list.** Opening a dropdown hit with Enter leaves the master list at "Results (0)". Ruling (UI review P1.2: "the list stays visible under a 'Search: westfall' heading"): when a hit is opened from the dropdown, the controller also runs the search for the typed text into the master list (same kind filter) without pushing a second history entry, and selects the opened row in the list if present. Shift+Enter / "Show all" behaviour unchanged.
- **(b) Item page gap.** `ItemPage` shows a large empty gap between the header and the summary line/sections (sections pushed to the bottom; see `tour2b/04-item-scarlet-belt.png`). Content must start at the top: fix the layout glue (BoxLayout/vertical filler or `PageContent` alignment), and add a test asserting the first section's y is within one header height of the header's bottom after `doLayout` at 1000×800.
- **(c) Boss levels.** Instance encounter lists show "Lv ??" for every boss (AtlasLoot has no level; 999 = skull). Ruling: fill the level from the boss's NPC row (`npc.level_min/level_max` via `BossSummary.npcId`) in `InstanceRepository.detail` when AtlasLoot's is null/999; when still unknown, omit the level token entirely ("1. Lucifron  Boss") instead of "Lv ??"; same in the boss header on the right ("Level ??" → omitted).
- **(d) Empty master list heading.** On Home with no search, the list shows "Results (0)". Ruling: hide the heading and show a muted placeholder "Search results appear here" in the list area; the heading returns with the first search.
- **(e) Build hash.** The status bar and the home page print the full 64-character database build hash; it overflows the 420 px compact window. Ruling: show the first 8 characters everywhere ("Data build 6c9ed1fa"), with the full hash in the status label's tooltip and in About; hide the build label entirely in compact mode.
- **(f) Compact instance page.** In compact (420 px) the instance page keeps its two-column layout, so the encounter list is clipped ("Lv ?? Bos"). Ruling: below a page width of 600 px the encounter list stacks above the loot section (single column), responsive on resize (use the enclosing-shell density flag Task 10 introduced, or the page width in `doLayout`); test both layouts headlessly by setting the size.

## Also in this wave
- Re-run every v0.1 test and the full `./mvnw -B -q verify` from the worktree root; commit as `fix(ui): Part 1 gate — search list, item page gap, boss levels, empty list, short build id, compact instance layout` (one commit is fine, or one per finding).
- Do not launch the real window; the controller re-runs the screenshot tour after your commit.
