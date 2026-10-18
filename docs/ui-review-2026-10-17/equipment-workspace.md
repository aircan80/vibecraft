# Persistent equipment research workspace

Interactive responsive concept: [loot-research.html](mockups/loot-research.html). It reuses the agreed journal typography, colors and shell. No Java production files are changed. Search, state/slot filters, selection, source disclosure, theme and compact list/detail controls work against three explicitly marked examples. Review buttons demonstrate loading, empty and failed query states. Classic comparison is visibly separate and supplies no invented Classic facts.

## Interaction hierarchy

1. Global search remains for the whole library. Page-local equipment search owns this result set; its label and keyboard focus distinguish it from global search.
2. The primary filter row exposes equipment slot, class restriction compatibility, field knowledge and value scope. Existing quality/item-level/class/subclass/stat filters can be placed in an expandable **More filters** group rather than adding multiple stacked rows above every result. Each active constraint gets a removable chip and clear-all action in production.
3. Desktop retains a 280–300px results column and readable detail pane. Selecting a row updates the pane without losing query, page or list position. Opening a set/source/NPC returns to the same selected item through global history.
4. Detail leads with item identity and current field state, then readable facts, set/acquisition connections and source-qualified evidence. Technical archive paths/hash remain a second disclosure; no field is deleted to simplify the view.
5. Compact switches between **Results** and **Selected item**; switching mode preserves filters and each pane’s independent scroll. Selecting an item opens detail. Escape returns to results and restores focus to the selected result, rather than the beginning of the document. The prototype uses a simpler Results-button focus return; production should restore the actual row.

## Semantics agreed with loot implementation

The loot agent is implementing sanitized Forever class restriction mask and equipment slot filtering. **Class compatibility means permitted by a class restriction mask**, not armor/weapon proficiency, usefulness, a build recommendation or BiS. Label it accordingly and provide that explanation beside the filter.

Unknown Forever masks/slots are counted separately and excluded only when the active filter needs that field. Do not borrow Classic fields implicitly. An AtlasLoot `slot` is list ordering, never an equipment inventory slot. Molten Core item 16806 remains unknown in the relevant Forever fields even when genuine Classic data exists. Classic comparison is explicit and field-scoped.

The prototype deliberately has no asserted class masks; selecting class compatibility returns no example matches and explains missing coverage. That tests the honest UI rather than shipping guessed class compatibility. Production should distinguish “no compatible records” from “records lack restriction data” and offer an optional **Include unknown restrictions** mode only if query semantics support it explicitly.

## Missing and transitional states

| State | Required behavior |
|---|---|
| Query loading | Retain current query/selection; indicate pending result refresh. Do not clear to zero first. |
| No matches | Show applied constraints and reset/remove actions; absence of a result is not proof of absence in the game. |
| Query failed | Error plus Retry; preserve last successful results where safe and label them stale. Never show an error as “0 items.” |
| Selected item filtered out | Keep its identity in history; explain it is outside filters, offer Clear filters or select another result. Do not silently choose row 1. |
| Hidden name/stats | Show exact ID and available metadata. Missing names, hidden stats, numeric zero and unobserved fields remain distinct. |
| Partial acquisition evidence | Name source and scope. NPC drop and instance loot-list evidence may describe the same relation; do not sum them into a false unique drop count. |
| Classic requested | Load genuine Classic evidence or say unavailable; never relabel Forever facts as Classic. |

## Keyboard and scale acceptance

Tab order follows search → filters → result rows → detail actions. Enter/Space selects a result; arrow-key list navigation should use standard Swing list semantics once the renderer is implemented. Selection must be announced with item name and state. Filtering must not steal keyboard focus or reset the caret. Accessible labels describe scope, not only icon shapes. Dense results may use smaller rows while main actions retain comfortable heights.

Test the entire shell at 1280×900 and 420px, plus 150%/200% scaling. At desktop width, a selected item and several result names remain visible together. Compact must not stack four tall filters before the first result: collapse secondary filters behind a labelled **Filters (n)** control and preserve the local search/result count. The present prototype shows the expanded filter state so its semantics can be reviewed; its next production iteration should default secondary filters collapsed on compact.

## Latest independent implementation audit

Reviewed current `JournalCityPage`, `JournalDungeonPage`, `JournalItemFacts` and existing polish captures. No fresh city screenshots were present at review time; the older `headless-turntable-final/dungeons-*` captures still show the previous tile index and cannot validate the newer source’s persistent workspace.

- **City guide:** Source now includes serif hero, map preview, exact child districts, source-qualified coverage and original map/records guide. The overview’s second equal-width column is mostly explanatory copy; next small improvement is a genuinely useful travel/quick-facts card when records are available, with 2:1 directory/sidebar width instead of equal cards. Keep data scope honest. Music is a supplied component; verify actual state text/mute/keyboard behavior in settled native captures.
- **City compact:** Hero is permanently NORTH outside the scrollable overview. At 200% scale or short windows, large hero/music controls may leave little working viewport. Make the overall editorial header scroll with the selected page, or collapse it to a concise title after initial overview; verify before claiming compact acceptance.
- **Dungeon filters:** Source now preserves list/detail, labelled filters and compact mode controls. Four filters plus title/search/count in a non-scrolling NORTH header still risk consuming compact height. Group secondary filters behind Filters (n), leaving search + count + results/detail switch visible. Older compact capture already demonstrates the problem: first card starts around y=496 in a page-only 900px image.
- **Dungeon out-of-filter selection:** With an attached guide, `refresh()` retains that guide even if the filter set excludes it. Add a small explicit “Selected guide is outside these results” message with clear/reset action; otherwise users cannot reconcile a selected detail with no highlighted destination row.
- **Item facts:** Current source has replaced the stretched tooltip wrapper with themed facts/acquisition columns and optional in-game tooltip. Verify it with fresh native screenshots rather than reusing the older journey-item evidence. Acquisition record totals correctly qualify duplicates; retain that wording and add direct section anchors so the counts are useful actions rather than merely statistics.

These are targeted next patches, not a claim that current source already matches the proposed research workspace.
