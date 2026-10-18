# Prototype fidelity gaps — first native journal pass

The running journal is a functional first pass, not a visual match to the agreed mockups. This comparison uses `native-final-3` captures against `mockups/previews` and the prototype CSS. Production code was not changed by this review. Native captures are 1280×900 and previews 1428×1000: compare relative geometry and hierarchy, not raw screenshot pixels. The prototype banner is a review label and should not be reproduced in production.

## P0 — stabilize what is being compared

`shell-home-dark-1280.png` begins with a largely empty hero whose left text is absent, then “Where are you headed?” and six cards. Its scrollbar thumb starts well below the top. Before calling this purely a paint bug, capture Home from scroll position zero after layout settles, and verify all heading/hero children have bounds. Navigation to a fresh home should show the intended title; restoring an existing home scroll position should be deliberate. Neither a scrolled capture nor a clipped text child is acceptable evidence of fidelity.

## P1 — home composition, not just different labels

The prototype has a 36px normal-weight serif page title, short introduction, **three** numbered task cards across, followed by one two-column Dalaran feature. Native home instead uses a 22px bold sans title, six 2-column cards with tiny buttons, and an art preview above them. This changes the whole first impression.

Patch the desktop page to: eyebrow → editorial title → short introduction → numbered Dungeon/World/Gear cards → Dalaran feature with title, factual summary, action and cropped art → quiet secondary links for planner/professions/changes. At 1280px width, a 205px sidebar and 32px content insets leave about 1,000px: three cards at roughly 320px each are feasible. Use a 16px gap and 20–22px card padding. Cards should have subtle neutral borders and 8–10px corners rather than gold rectangular outlines. Dalaran art should fill the right half of its feature; the current 300px thumbnail with empty left space is not the design.

**Pass:** At 1280×900, fresh Home shows its title, introduction, all three complete primary task cards and at least the top of the Dalaran feature without scrolling. No six-card dashboard wall, empty hero half, or unlabeled art-preview placeholder. Secondary destinations remain reachable. At compact widths, cards stack and artwork moves below feature copy.

## P1 — typography and controls

The prototype uses Georgia (not Marcellus) for brand/h1/h2, with regular weight. Native screenshots retain default bold sans headings and a one-line sans brand. Use the app’s existing bundled Marcellus if available as the consistent serif approximation, or Georgia/Serif fallback; do not introduce an unlicensed font download. Brand can wrap to two lines with a small spaced subtitle. Target title 32–36px, section 23–26px, body 14–15px, secondary 12px. Preserve font scaling.

Native buttons and toolbar are still dense FlatLaf defaults, around 22–26px tall; prototype actions are around 40px. Give primary actions 36–40px height, modest corner radius and 12px horizontal padding. Use generous controls selectively rather than enlarging every dense loot row. Put history controls and search within a toolbar aligned with main content, not spanning above the sidebar. Keep a visible focus outline, but avoid making focus the only strongly styled component on the screen.

**Pass:** A side-by-side view clearly shares serif title hierarchy, normal-weight headings and comfortable primary buttons. No title falls back silently to bold UI sans. Search, breadcrumb and content have consistent horizontal alignment. Keyboard navigation still has visible focus.

## P1 — palette is visibly different

Native dark uses a blue-gray sidebar and large #242A33 raised cards; native light is warm yellow with strong gold selection. Prototype dark is green-charcoal with restrained teal selected states; light is neutral ivory with sage selection. These differences matter more than icon polish.

Prototype tokens are explicit in `mockups/index.html`: dark base `#111719`, panel `#192124`, raised `#202A2D`, line `#354043`, text `#E8E8DE`, muted `#A6B4B6`, gold `#DAB776`, accent `#A8D7CC`, selected tint `#263D38`. Light base `#F2EEE5`, panel `#FAF7EF`, raised `#E8E4D9`, line `#C8C6BA`, text `#263235`, muted `#566766`, gold `#826025`, accent `#236C60`, tint `#DCE8DE`. Port these intentionally through theme tokens, then validate quality-color and selected-row contrast; do not blindly replace the existing audited quality palette.

**Pass:** Sidebar selected state is teal/sage, surfaces neutral rather than navy/yellow, and gold serves editorial emphasis/status rather than outlining all containers. Both themes retain readable quality colors and table selections.

## P1 — dungeon explorer needs persistent context

`journey-boss-loot.png` supplies a useful encounter/detail split, but the prototype adds a persistent **destination** list beside a unified selected dungeon panel. Native has two breadcrumb rows, a small entity header, default tabs and an extremely narrow encounter list against a mostly empty page. Repeated “Loot status unverified” on all nine bosses crowds the list while the actual loot table is three tiny rows.

Use a 220–250px destination/result list retained during detail navigation, with selected dungeon clearly highlighted. Inside the detail panel, display larger serif dungeon title, concise level/encounter summary, qualified client/addon chips and unified tabs. Encounter rows should have a clear selected state and comfortable click area; keep general source qualification once above the list, per-boss state only where it differs. Display loot as readable icon/name rows with secondary source and a compact Reported badge, or improve the existing JTable renderer/row heights if card rows are too invasive. Preserve exact boss order, grouping and source distinctions.

**Pass:** At desktop width, another dungeon can be selected without going back, the active dungeon and boss are obvious, and the three known loot entries have enough spacing to scan. Switching tabs preserves selected dungeon/boss, filters and list position. Compact replaces side-by-side panes with a clear list/detail toggle rather than squeezing columns.

## P1 — item detail remains a tooltip in a stretched empty frame

`journey-item.png` shows a 400px dark tooltip inside a roughly 1,030px gold outline; the right two-thirds are blank. Metadata, duplicate Known states, empty appearance section and drop rows then stack below. Prototype uses a persistent item-results list and a detail pane with readable stats embedded in the theme, a clear item name, one source-qualified state, set link and “Where does this come from?” disclosure.

Retain current trustworthy tooltip content as an optional “In-game tooltip” view, but use the same data to render a themed default facts card. A smaller safe first patch is to size the tooltip container to content and move acquisition summary beside it; this removes dead space but still does **not** satisfy the full prototype’s item explorer. The complete slice needs a reusable list/detail container shared with dungeon browsing. Suppress empty appearance sections and avoid repeating known state three times. Keep quality color on item identity; use audited theme accent for effects rather than assuming neon green works on all light surfaces.

**Pass:** Desktop item view keeps results/selection context, has no broad empty tooltip wrapper, places known stats and acquisition/set information coherently, and retains all existing exact source evidence behind a named disclosure. Hidden item #287012 must still show its real slot/set facts and honest field states; no invented name or stats. Duplicate acquisition rows from different evidence models must be visibly distinguished or explicitly grouped, not silently deleted.

## P2 — sidebar density and compact behavior

Native sidebar is ~224px and has nine nearly equal destinations, all under Explore; prototype is ~205px, with Home separated, Explore/Character grouping and fewer top-level choices. Reduce to the agreed grouping and move secondary catalogs under parent destinations. Do not remove working features: expose them inside their parent page. Keep custom icons if desired, but lower their visual noise and align icon boxes/text consistently. At compact sizes use a deliberate collapsed rail or horizontal destination switcher; the prototype does not reserve desktop sidebar width on a narrow viewport.

**Pass:** At 1280px width, sidebar is approximately 200–210px and content starts consistently. At 420px, active destination remains identifiable, main text uses the viewport width, all destinations remain keyboard accessible, and overlays/back navigation preserve place.

## Verification before “matches the mockup”

Capture settled Home, dungeon explorer with Dalaran/Arcane Anomaly, known item and hidden item in **both themes at the same 1280×900 resolution** as a normalized prototype render. Also capture the entire shell at 420px, not page bodies alone. Review the top-of-page viewport and one scrolled state separately. Fidelity acceptance requires correct composition, type hierarchy, surfaces, spacing and persistent navigation together; passing route, focus and history tests only establishes function. Preserve those tests while adding visual comparison evidence.
