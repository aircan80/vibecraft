# Task 3 report: rail icons from client art

Status: DONE. Commit `8bbae34` on `feature/phase3b-quest-ui` (parent `7291758`).

## What was built

- **`RailTarget`** gains `assetName()` ("rail.home" … "rail.instances") and `gossip()` (true for NPCS and QUESTS). It also gains `static Optional<RailTarget> of(EntityKind)`: ITEM, NPC, QUEST, ZONE and INSTANCE map to their sections, while OBJECT and BOSS map to none. The pages use it to choose their icons.
- **`RailIcons`** is the record from the brief.
  - `load(Function<String, Optional<byte[]>>)` decodes each texture once with `Images.decode`.
  - `plateArt(src, gossip)`: Interface/Icons art is cropped to a centred square of 86 % of its shorter side, copied rather than taken as a subimage so the bicubic scale cannot sample the cut-off bevel, then scaled with `IconCache.scale` to 24 px. A gossip glyph is scaled by 1.5×, bicubic and capped to fit, then centred on a transparent 24 px canvas.
  - `plate(t)` and `small(t, px)` return a private `ArtIcon` record that is UIScale-aware and painted bicubic. Two icons with the same image and size are equal. `small` falls back to the SVG `Icons.icon(t.iconName(), px)`.
  - `none()`, `shared()` and `install()` follow the pattern of `IconCache`.
- **`RailButtonUI`** (a `BasicToggleButtonUI`) paints each button.
  - Each button spans the full rail width and is 40 px tall, with a 28 px navy `Theme.plate()` plate at radius 4.
  - Idle: the icon at alpha 0.85, full colour.
  - Hover, armed or keyboard focus: a 1 px gold ring at alpha 128 on the plate edge, and the icon at full alpha.
  - Selected: a solid 1 px gold border, a static two-ring glow outside it (alpha 90 and 35), and a 3 px gold bar at x 0–2 as tall as the plate.
  - Gold is `Theme.current().accentGold()` on every paint, and nothing is animated.
- **`Rail`**
  - Its buttons are a private `RailButton` subclass. `updateUI()` always installs `RailButtonUI`, so a theme switch keeps the plates.
  - `createToolTip()` returns the navy card: `TooltipLinesPanel.BACKGROUND` with `DEFAULT_TEXT` (white) and a `BORDER` line.
  - Until the art arrives, the SVG glyphs are tinted a fixed light `#E4E1D8` (`RailButtonUI.GLYPH`), gold when selected. A theme-tinted glyph would be dark-on-navy in the light theme.
  - `setIcons(RailIcons)` replaces the icon of every target that has art and clears its selected icon. The UI fixes the button size, so nothing moves.
- **`MainFrame.attach`** calls `Async.run(controller.worker(), () -> RailIcons.load(repos.assets()::uiIcon), …)`. On the EDT it then calls `RailIcons.install(icons)` and `rail.setIcons(icons)`. A failure is logged with a warning and the SVG glyphs stay.
- **`SourcesPage`**: the sections it builds from rows go through `addRowSection`, which sets the 16 px icon from `railTarget(table)`. These come from `addSection(title, …)`, both `addFilteredSection` variants and `addLootSection`.
  - `railTarget` takes the kind of every row's first link column, skipping rows without a link such as group headings and dividers.
  - It returns that kind's rail target when all rows agree, and empty when kinds mix, when the rows are objects, or when there are no linked rows.
- **`ListPage`** sets its kind's 16 px icon on every section after building them. This covers the Results table and each instance level band, and it applies even when the list has no rows.

## Tests: red, then green

- **Red.** I wrote the tests first, and the focused run failed to compile: `cannot find symbol` for `RailIcons`, `RailButtonUI`, `assetName`, `RailTarget.of`, `setIcons` and `SourcesPage.railTarget`.
- **Green.** The focused run then passed: `RailIconsTest` 8, `RailTest` 10 (6 existing + 4 new), `SourcesPageIconTest` 5, `ListPageTest` 19 and `NpcPageTest` 21, all with 0 failures. `ShippedDataTest` passed 23, including the new `loadsAllSixFromTheShippedDatabase`.
- **New tests**
  - `RailIconsTest`: `assetNamesAndGossipGlyphs`, `eachListableKindHasItsRailTarget`, `cropsCentralEightySixPercent`, `gossipGlyphFillsTheIconBox` (opaque width ≥ 20 and centred), `missingAssetFallsBackToSvg`, `corruptArtFallsBackToSvg`, `loadsThePlateArtFromTheNativeTexturesAndTheSmallIconsFromTheBestVariant` and `sharedIsNoneUntilInstalled`.
  - `RailTest.plateStates` paints the whole rail in Slate and in the current light palette.
    - Idle: the plate is exactly `#0C1424` and the red icon pixel matches red at 0.85 over navy, within ±3 per channel. R stays above G, there is no ring and there is no bar.
    - Hover: the ring pixel matches gold at 0.5 over navy, the icon is at full alpha and the plate interior is navy.
    - Selected: both border columns are gold, x 0–2 at mid-plate are gold, x 3 is not, and the bar does not extend above the plate.
  - `RailTest.svgIconsSitOnTheSamePlatesUntilTheArtArrives`: the light theme's SVG glyphs are on navy and light.
  - `RailTest.iconsSwapInWithoutALayoutChange`: preferred sizes are unchanged, the art icon is installed, a target without art keeps its SVG, and accessible names are unchanged.
  - `RailTest.tooltipsOnNavyCard`: the tooltip text equals `label()`, the accessible name is unchanged, and the tooltip background is `#0C1424` with white text.
  - `SourcesPageIconTest`:
    - `questSectionsCarryTheQuestIcon` checks "Starts quests" and "Ends quests" on the NPC page, until Task 14 adds "Quests".
    - `itemSectionsCarryTheItemIconAndSpawnsNone` checks Drops and Sells.
    - `sectionsMixingKindsOrWithoutRowsHaveNoIcon` covers mixed NPC and object rows, NPC-only rows, object-only rows and no rows.
    - `listPagesCarryTheirKindsIconEvenWhenEmpty` covers ZONE and QUEST.
    - `withoutClientArtTheHeadingsShowTheSvgIcons` checks the fallback.
  - `ShippedDataTest.loadsAllSixFromTheShippedDatabase` sits after `everyPageKindRendersFromTheShippedDatabase`, as the controller asked. Every target has a 24×24 plate and a non-SVG 16 px icon.

## Full verify

`./mvnw -B -q verify` exited 0 before the commit.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 420 | 0 | 0 | 0 |
| app | 841 | 0 | 0 | 0 |

## Rail preview (for open question 4)

I rendered these with a throwaway test class that extended `ShippedDataSupport`. It loaded the shipped art, ran headless under the real LaFs (`WowForeverDarkLaf` and `WowForeverLightLaf`) and was deleted before the verify and the commit. In every image Quests is selected and Items is hovered.

- Side by side at 1×, from left to right: Slate with art, Slate with SVG, light with art, light with SVG.
  `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-both-themes-1x.png`
- The same sheet enlarged 4× with nearest-neighbour scaling, for inspection:
  `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-both-themes-4x-zoom.png`
- The individual 1× rails:
  - `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-slate-1x.png`
  - `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-light-1x.png`
  - `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-slate-svg-1x.png`
  - `C:\Users\aircan80\AppData\Local\Temp\claude\H--claude-java\a1319091-e49b-497b-a1d5-19b251c5d1a8\scratchpad\rail-preview\rail-light-svg-1x.png`

At 1.5× bicubic, the speech bubble and the "!" look soft but legible, and they are about as large as the Interface/Icons art. Native sizes in the shipped database: the four Interface/Icons textures are 64 px, the two gossip glyphs 16 px, and every `@24` variant 24 px.

## Choices the brief left open

1. **The source of each `small` image.** Interface/Icons art uses the `@24` variant, which 3A's converter already cropped. Gossip art uses the native 16 px glyph, so a 16 px heading icon is pixel-exact. The `@24` gossip variant is the glyph centred at native size in 24 px, so scaled to 16 it would be only about 11 px. `load` never requests the gossip `@24` names. If the `@24` variant is missing, the icon falls back to the plate art, and without that to the SVG.
2. **Plate geometry.** The button is 48 × 40 px, the plate 28 px at radius 4 and the art 24 px, all logical pixels scaled by UIScale. The rail now takes 6 × 40 px plus 12 px of border.
3. **Hover also covers armed and keyboard focus.** FlatLaf keeps toolbar buttons unfocusable by default, so the focus case rarely occurs.
4. **The selected state's "soft glow"** (spec §5) is two static rings outside the border, at alpha 90 and 35. The brief's tests do not mention it. Nothing pulses.
5. **The selected icon is drawn at full alpha**, like hover. The spec only defines idle at 85 %.
6. **SVG glyph colour on the plates.** The glyphs use a fixed `#E4E1D8`, with the theme gold when selected, because the plate is navy in both themes. Before this change they used the theme's text colour, which is dark in the light palette.
7. **Tooltip card colours** reuse `TooltipLinesPanel.BACKGROUND`, `BORDER` and `DEFAULT_TEXT` (white), so they match the item card.
8. **Sections built outside `SourcesPage` get no inferred icon.** These are added through `addSection(SourceSection)`: the spawn points, `ListPage`'s own Results section and later custom sections. Spawn rows link to zones, but the Zone column is hidden when every spawn is in the page's zone. An inferred icon would therefore come and go, so Spawns has none. `ListPage` sets its icons explicitly.
9. **The icon is decided when a section is built.** A drop table that mixes NPC and object sources has no icon, and the same table with only NPCs gets the NPCs icon, as the brief says ("rows are one entity kind"). An Item page's "Dropped by" can therefore differ between items.
10. **The art loads on `controller.worker()`** through `Async.run`, the same worker as the other database reads, not on `Async.SWING_WORKER`.
11. **The ShippedDataTest test keeps the name `loadsAllSixFromTheShippedDatabase`**, as the brief and the controller gave it, with a Javadoc line that says it covers the rail art.

## Concerns and limits

- A page built before the art finishes loading keeps its SVG section icons until the user opens it again. This only matters in the moment after start-up. `RailIcons.shared()` is read when a page is built, not when it paints.
- `InstancePage` is not a `SourcesPage`, and the brief does not list it, so its boss "Loot" section has no item icon. Task 5's section work may want it.
- The plan's "same icons at 16 px head the matching sections" now holds for NPC, Object, Item, Quest and Zone pages wherever all rows link to one listable kind. Object rows have no rail section, so they get no icon.
