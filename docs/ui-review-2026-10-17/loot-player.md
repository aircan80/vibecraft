# Dungeon, loot, and build planning review

This is a simulated experienced WoW player walkthrough by an agent, not testing with recruited gamers. Evidence: actual v0.4.1 acceptance screenshots in `.local/v041-acceptance/{standard-final,catalog-final,source-evidence-final}` and current `InstancePage`, `ItemPage`, `ItemContentPage`, `TalentPage`, `LegacyPage`, and `CampingPage` source. Screenshot observations are distinguished from live interaction findings; this review did not drive the running application.

## Overall player reaction

The app has valuable unusual information: new dungeon loot, exact source provenance, hidden-item context, and real planning rules. Presentation currently makes the player read a database before making a gameplay decision. Lead with “Where am I going, what drops, and what can I plan?” Keep source evidence one deliberate action away. Do not remove uncertainty: reported drops and hidden stats are essential facts.

The Dalaran dungeon view already has a useful encounter list and selected boss loot. The clickable geography hierarchy is valuable and should be preserved. The repeated build, New, provenance, and explanatory lines take roughly the first 270 pixels of its screenshot before the encounter workspace. The loot table then shows item level and origin but no quick slot decision. On a hidden item, the page puts a lock beside “Forever stats” and question marks beside fields; a casual reader can mistake unsupported values for fields they can discover through another click. The set page makes each piece a full-width button and a separate ID row, consuming space that could show slots and bonuses together.

## Prioritized improvements

| Priority | Observed friction | Proposed experience | Acceptance criterion / scope |
|---|---|---|---|
| P0 | Talents are a long table of names, zeroes, and node IDs; selected description includes raw `$m1` and source paths. The tree context and rank affordance are hard to see. | Separate Class and specialization selection. Show a source-backed spatial tree when native positions and links are available, with ranked nodes and selected-talent detail. Retain a compact accessible list alternative. Show current/max ranks together and name prerequisites; keep the current allocation rules. | Player can choose a class/spec, see spent/remaining points, select a talent and add/remove ranks without reading a node ID. Locked nodes explain the real requirement. Keyboard operation and current import validation still work. Unknown links are not invented. This is UI over existing rules, not a balance/rank-calculation expansion. |
| P0 | Camping opens four reference sections before the party composer. Five “members” in source are actually five independent profession/rank entries. | Two tabs: **Plan a camp** and **Browse camps**. First tab starts with **Profession slots** (accurate current model), rank inputs, covered features, and unmet requirements. Keep a clear “Draft only · recipe ownership not checked” note. | Initial viewport contains first input and live coverage. Adding/removing a profession updates coverage without scrolling past blueprints. Do not call five profession entries five fully modeled characters. Two professions per named character is a separate model change. |
| P0 | Legacy opens a raw source-ID column and a large raw-field inspector. “Ruleset set 60/61” has no gameplay meaning established. | Lead with Challenge, Requirement, and reward where actually linked. Move IDs and raw fields to **Source details** drawer. Label unresolved sets **Challenge group 60 (meaning unverified)** and **61**, retaining exact IDs in evidence. | Initial screen is readable without any DB field name; player can filter known challenge text and inspect an original record in one action. No invented Standard/Hardcore labels or character progress. |
| P1 | Dungeon title/context block competes with the actual boss/loot workspace. Empty entrance text occupies space; quest rows crowd the selected boss loot. | Compact dungeon header: breadcrumb, name, level, dungeon type, loot evidence badge. **Overview / Bosses & loot / Maps / Quests** tabs, with Bosses & loot default after selecting a boss. Entrance status belongs to Maps/Overview. | At 1216×720 the first boss and several loot rows appear in the initial viewport. At compact width, boss selector stays obvious above selected boss. Changing bosses preserves a readable selected name and loot status. All geography links remain usable. |
| P1 | Boss loot lists item name/item level/origin; item choice needs opening each row. | Add **Slot** where actual item metadata exists; armor/weapon type as secondary text. Search names and filter recorded slots. Show **All / Reported / Observed** only for actual evidence states present; preserve available difficulty switch. Optional tooltip preview on focus/hover and explicit Open link. | Search/filter state survives item→Back and dungeon context is restored. Unknown slot is “Unknown,” not excluded by an arbitrary guessed class. Missing numeric rate stays blank/“Not recorded,” never 0%. Stat/BiS ranking or usable-by-class filtering requires additional design/data work and is not assumed to exist. |
| P1 | The evidence control says “Source differences and evidence (9),” mixing historical updates, true disagreement, and scope differences. Expanded hotfix evidence contains repeated unknowns, raw -1 masks and full SHA strings. | Summary: **Updated by hotfix** or **Sources differ on 2 fields**, with separate **Changes / Conflicts / Sources** sections as applicable. Default rows show field, displayed value, alternative/previous value, source/build. Technical IDs, masks, paths and hashes in expandable details with Copy. | Player understands whether a drop is disputed versus simply addon-reported. A hotfix update alone never receives a conflict warning. Decode masks only with verified semantics; keep raw value in details. Exact source original remains accessible. |
| P1 | Hidden item screenshot has four far-separated status chips and an appearance box saying “Shares appearance with 0 other items.” | Compact honest placeholder card: **Item #287012 · Plate hands**, **Stats hidden**, known set link; one **What is known?** disclosure for field-level state. Suppress empty appearance module or show it in reference details. | Name/slot/set facts remain independent of hidden stats. Do not suggest this particular item's stats can be revealed by clicking. No invented artwork, stats, acquisition path, or identity from same-looking items. |
| P1 | Set pieces consume two rows each; Known chips repeat down the right edge. Classic comparison unavailable is mixed into the subtitle. | Member table: piece, slot, evidence; bonuses beside/below in ascending piece count. Distinct **Forever / Compare with Classic** modes; comparison pane can state absence of a matching Era set plainly. | Five pieces and first bonuses fit together at normal width. Classic text always comes from Era evidence, not a Forever tooltip on an Era-labeled item. Unknown formula remains qualified. No ownership checkbox unless clearly hypothetical. |
| P2 | Dense rows, muted secondary text and enormous technical numeric columns dominate multiple catalog tables. Screenshot talent/camping/Legacy column headers are not visible although source defines headers. | Make names primary; IDs hidden by default; 28–32px rows, visible headings, consistent numeric alignment and select/open behavior. Keep adjustable density for reference browsing. | Live UI check confirms headers visible and sticky in scrolled tables; verify screenshot harness versus actual-window behavior before diagnosing a production header bug. Tooltips supplement accessible names rather than replacing them. |
| P2 | Build export requires **Show build**, select field, copy; current page explicitly does not save. Navigation can discard hypothetical work. | **Copy build**, **Import build** actions, clearly marked session draft, and a draft-preservation policy across app navigation. | Copy has success feedback; failed import leaves current build untouched. Session draft retention is a small state-lifecycle feature, not character integration. Permanent saved builds/folders are separate new functionality. |

## Target player flows

### Choose tonight's dungeon and inspect loot

`Dungeons → filter recorded level / name → City of Dalaran → Bosses & loot → Arcane Anomaly → Plate/Hands if recorded → Ephemeral Grips → Back`

The dungeon header should answer level and evidence quality immediately. Boss rows should show loot count plus **Reported** rather than repeating “Loot status unverified” for every boss. Count is source coverage, not proof of a complete loot table. After opening an item, Back restores the exact boss, filters and scroll position. “Atlas maps (2)” should become **Maps · 2 floors**, with a floor label and legend action after opening. Dungeon-wide loot aggregation is a separate query/UX feature; it must not blend trash, boss, and addon extra lists silently.

### Understand an item's reliability

`Item → tooltip/known placeholder → How to obtain → Evidence summary → optional Source details`

Use independent badges: **Stats: hotfix** / **Drop: addon reported** / **Name: hidden**, rather than a single overall trustworthy/untrustworthy badge. A known name does not verify a drop; a Reported drop does not invalidate client stats. The initial item view should give these distinctions in one compact line, with exact build/hash in details. Known, Hidden, Not yet discovered, and Missing from this source must remain different.

### Compare set changes versus Classic

`Sets → search set → pieces + bonuses → Compare with Classic → matching field evidence`

The comparison should offer “Not present in the imported Era set table” or “Comparison unavailable” according to actual coverage, rather than claiming the game never had an equivalent. Avoid matching differently numbered sets by name alone. A proposed gear-to-gear stat comparison/pin tray would be a new feature, not a restyling of current set comparison.

### Make a talent draft

`Talents → class → specialization → select node → add rank → review named prerequisite feedback → Copy build`

Use a persistent total such as **12 spent / 51 cap** and show current node **2 / 5** beside its name. Available rank gains, blocked gains and selected node need distinct visual states in both themes and without relying on color alone. Where a description formula cannot be resolved, say **Effect magnitude not available in this snapshot**, with original template under source details. Do not calculate a fabricated value. The existing beta/above-30 limitation remains visible but can be one compact note rather than multiple introductory lines.

### Check party camp coverage

`Camping → Plan a camp → add Cooking rank 140 → see Journeyman Campfire eligibility → add another profession → inspect unmet requirements`

Coverage means recorded profession/rank requirements are met. It does not mean recipe learned, object owned, buffs stack, or every member can receive a benefit. Describe these limits near results, not as a long source-data preface. Blueprint rows should link to actual existing item pages when supported, never imply an unrecorded drop location.

## Suggested first mockup

Start with the dungeon workspace because it connects maps, bosses, loot, items and evidence. A second mockup should show the talent planner, using real native node positions when reliable, plus the accessible list view. A third should put the camping planner above the reference catalog. These should use the same shell, selected-tab treatment, concise evidence badges, and detail-drawer pattern. Real content to anchor them: City of Dalaran / Arcane Anomaly; item #287012; Rotmender's Raiment; Shaman Enhancement; Cooking rank 140.

## Validation tasks after implementing a direction

Use a guided formative session with actual players before calling this “gamer tested.” Ask a player to find one reported Dalaran drop, explain its reliability, return to the same boss, locate a set bonus, allocate a legal talent and copy it, and determine the recorded camp features for Cooking 140. Record wrong turns, time, vocabulary confusion and loss of context. Verify both themes and compact width; keyboard navigation must reach source disclosures, boss selection, filters and rank actions. Success is a correct explanation plus a completed task, not just an attractive screenshot.
