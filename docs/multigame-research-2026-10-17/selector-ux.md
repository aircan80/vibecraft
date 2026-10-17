# Multi-game selector and navigation proposal

17 October 2026. Simulated desktop UX designer review, not a user test. This proposal changes no production code or existing prototype. Game names below are display labels; importer/product identifiers must come from the source verification report, not from this design document.

## Core decision

Keep a named **Game** selector permanently visible in the application header, immediately before global search. Suggested values are **WoW: Forever**, **Classic Era**, **Retail**, and **Anniversary TBC**, with availability and data version shown in the selector menu. One physical database does not make records interchangeable: every normal browsing, lookup, search, map, tooltip, and relationship query operates within the selected game.

**Forever uses Forever data only, including during beta.** A missing Forever stat, tooltip, name, map, relationship, or dataset stays missing. Do not fill it from Era, including through shared IDs or a fallback hidden behind a label. Era appears beside Forever only after the user explicitly chooses **Compare with Classic Era**; the comparison is separately labeled and cannot supply the primary Forever card.

## Placement and appearance

Desktop header:

```text
[Back] [Forward]  Game: [WoW: Forever · Beta ▾]  [Search WoW: Forever…             ]
```

The game name remains visible while the user scrolls and while evidence panels are open. In compact mode use two header rows rather than hiding game identity inside a hamburger menu: game selector above scoped search. Do not rely on a logo, color, tooltip, or truncated product code to distinguish games.

Selector menu concept:

```text
Game
✓ WoW: Forever     Beta · Installed · client build …
  Classic Era      Installed · client build …
  Retail           Not installed
  Anniversary TBC  Not installed

Data details…
```

Unavailable games remain visible and selectable for an availability page, but cannot open another game's content. Display only verified installed/available status. An unimplemented acquisition action should read **Dataset unavailable in this app version**, not present a nonfunctional Install button. If install/import is implemented, expose it there with size/version information before the action.

## Separate game selection from interface selection

The existing startup interface chooser answers **How should the app look?** (for example Journal or the classic interface). The game selector answers **Which game's data am I browsing?** These are independent choices.

- Name the startup control **Interface**, with screenshot/previews if useful.
- Keep the game selector present in both interfaces, at the same conceptual location.
- Persist the chosen interface separately from last active game.
- Choosing a different interface must not change game, data snapshot, query, or comparison scope.
- Choosing a different game must not change interface.
- On first launch, show the actual default game with its data availability; do not infer a choice from the first installed snapshot or a same-named character class.

## Switching behavior

Use immediate selection without a confirmation dialog for ordinary browsing. On selection:

1. Cancel or invalidate outstanding search, suggestions, detail loads, map tile loads, evidence loads, and filter computations for the previous game.
2. Switch the visible game label immediately and show a clearly scoped loading surface: **Opening Classic Era…**. Do not display old Forever results underneath the new label. Keeping the last view behind an explicitly blocked transition overlay is acceptable, but clearing it is simpler.
3. Restore that game's last valid section, query/filter state and selected page, or open its Home if it has no saved state.
4. Commit only results whose game and navigation request still match the active request. A slow response from an earlier game never replaces the new view.
5. If the dataset is absent, open that game's availability page. If load fails, retain the attempted game's identity and an honest error with **Retry** and **Choose another game**. Do not silently revert or substitute.

If a user switches twice quickly, only the latest selection wins; no confirmation queue. Cancellation may be logical rather than physically interrupting a SQLite operation, provided stale responses cannot render or affect state.

A hypothetical talent or camping build may need an explicit **Unsaved build** notice if switching would lose work. Prefer retaining separate in-memory drafts by game. Do not introduce a generic confirmation dialog when view state can be preserved. Existing transient/no-save promises should remain accurate until persistence is intentionally added.

## State scope

| State | Scope and expected behavior |
| --- | --- |
| Global search | Active game only. Placeholder names the game. Suggestions, counts, paste resolution, result titles, and empty messages all use that game. |
| Search type/scope | Preserve per game. Do not retain a now-invalid filter invisibly; explain any reset. |
| Local catalog filters | Per game and catalog; preserve query, sort, page, selected row and scroll when returning. Show active filters visibly. |
| Recent searches | Per game. Optional unified history must show a game badge on every row and selecting it intentionally switches game. |
| Recently viewed entities | Game + entity kind + ID + snapshot/context as needed. Same numeric ID in a second game is a different destination. |
| Pins/favorites | Game-scoped. Cross-game pin lists require explicit game badges. A pin to an unavailable game opens that game's availability state; never substitutes a namesake. |
| Back/Forward | Separate history stack per game. A deliberate selector switch restores the target game's stack; Back does not unexpectedly change game. |
| Breadcrumbs | Current game’s hierarchy only. Prefer **World > Azeroth > Eastern Kingdoms > Westfall** with the persistent game selector providing game identity. Do not use breadcrumbs as game switches. |
| Build planners | Game-specific draft, rules, point budgets, classes, and supported systems. Do not translate an existing draft into another game automatically. |
| Source inspection | Evidence for the selected game’s record. Preserve provider, build and observation scope; shared physical archives do not establish authority across games. |
| Interface/theme/text size | Global preference, independent of game. |

A game's history entry should be restored under that game only. Within a game, filter changes and row previews should keep the existing app's intentional Back behavior. An unavailable/deleted old destination gets a clear **This page is unavailable in this dataset** state with a section link, rather than remapping its ID.

## Links, pasted IDs and comparisons

A bare ID or unqualified item string resolves only against the active game. A recognized link carrying a different game/product may offer **Open in Classic Era** with the detected game named; do not silently reinterpret it as Forever. Unknown link scope should be explicit rather than guessed. Cross-game internal links also name their destination game.

**Compare with Classic Era** is a deliberate view action, separate from changing the game selector. On a Forever item page:

- The header and selector stay **WoW: Forever · Beta**.
- Two panels are explicitly named **Forever** and **Classic Era comparison**, each with its own build/evidence.
- Forever missing values remain missing. An Era value never becomes a Forever default or fills a blank primary tooltip.
- Matching requires justified identity/correspondence; a numerical ID match alone is not a claim that two different entities are equivalent.
- If Era is unavailable, show **Classic Era comparison dataset unavailable** inside the comparison area; keep the Forever page usable.
- Closing comparison restores the original page state and focus. Export/copy actions identify which game's value is being copied.

## Availability and uncertainty copy

| Situation | Proposed visible wording |
| --- | --- |
| Entire game absent | **Retail data is not installed.** This game cannot be browsed with the current local dataset. |
| Game not supported by this app build | **Anniversary TBC browsing is not available in this app version.** |
| Particular dataset absent | **Forever quest data is not available in this snapshot.** Other Forever sections remain available. |
| Real search with zero matches | **No Forever items match these filters.** Clear filters / Edit search. |
| Item exists but a field is absent | **Not recorded in this Forever snapshot.** Use the precise existing knowledge state where known. |
| Blizzard hides a field | **Hidden by Blizzard.** Do not replace with Era text. |
| Addon-only loot association | **Reported by addon · Not verified in game.** Keep the provider reachable. |
| Source conflict | **Sources differ.** Inspect sources. Preserve selected value and competing evidence. |
| Beta dataset | **Beta · build …** alongside game identity; specific unconfirmed gameplay claims remain next to the affected content. |
| Incomplete comparison coverage | **Comparison incomplete: the required Era/Forever table is unavailable.** Do not count missing coverage as unchanged. |

A beta badge qualifies release status, not permission to mix games. Source builds and timestamps belong in a concise data details disclosure, with important stale/missing warnings next to the affected content.

## Prototype acceptance tasks

1. Start in either interface; identify the active game without hovering. Change interface and verify the game does not change.
2. Search for a known item, switch games before suggestions finish, and verify no old-game suggestion/result appears under the new game label.
3. Open a Forever item with a missing field. Verify no Era fallback appears. Explicitly open Compare and verify any Era value remains confined to its named panel.
4. Apply filters, open a dungeon and boss, switch games and return. Verify the original filters, boss selection and scroll are restored.
5. Choose an unavailable game. Verify it shows an honest availability page and never the last game's results.
6. Open same-number IDs in two games through separate pins. Verify histories and details remain separate.
7. Paste a link explicitly identifying another game. Verify switching is named and deliberate.
8. At compact width and increased text size, verify the complete game identity, navigation affordance and scoped search remain visible.

This is a proposed interaction contract for later implementation and prototype evaluation. Source product codes, acquisition feasibility and installed dataset coverage must be supplied by the technical research team.
