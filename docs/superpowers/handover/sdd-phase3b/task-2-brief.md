### Task 2: WoW chrome widgets

**Files:**
- Create `{main}ui/widgets/SectionHeading.java`, `{main}ui/widgets/Chip.java` and `{main}ui/widgets/RaisedBorder.java`.
- Modify `{main}ui/widgets/SourceSection.java` (its heading becomes a `SectionHeading`), `{main}ui/detail/HeaderPanel.java` (title `Fonts.heading(20f)`), `{main}ui/widgets/RateBarRenderer.java` (6 px gradient bar, 10 % ticks), `{main}ui/StatusBar.java`, `{main}ui/AppController.java` (passes meta to `StatusBar.setProvenance`) and `{main}ui/widgets/ItemCardPanel.java` (keeps its navy card; border uses `card.border`).
- Delete `{main}ui/widgets/RankChip.java`; its callers (`NpcPage`, `InstancePage`) use `Chip.rank`.
- Tests: create `{test}ui/widgets/SectionHeadingTest.java` and `{test}ui/widgets/ChipTest.java` (absorbs `RankChipTest`); modify `RateBarRendererTest`, `StatusBarTest` and `SourceSectionTest`.

**Interfaces:**
- Consumes: Task 1 `Fonts`, `Theme.hairline/barStart/barEnd/plate`.
- Produces:
  - `SectionHeading(String title, int count)`, with `setCount(int)`, `text() -> String` ("Dropped by (34)"), `setIcon(Icon)` (16 px, Task 3) and `setTrailing(JComponent)` (Task 6's filter control).
  - `Chip(String text, Chip.Style style)` with `enum Style { PLAIN, ELITE, RARE, RARE_ELITE, BOSS, REPEATABLE, FACTION }`, plus `static Optional<Chip> rank(Integer rank)`, `outline() -> Color` and `textColor() -> Color`.
  - `RaisedBorder.create() -> Border`.
  - `StatusBar.setProvenance(Map<String,String> meta)` and `static String provenanceText(Map<String,String> meta)`.
  - `StatusBar.themeToggle() -> AbstractButton` and `StatusBar.layoutToggle() -> AbstractButton`, wired by `MainFrame` to `ThemeManager.toggle` and the F10 switch.
  - `RateBarRenderer.BAR_HEIGHT = 6` and `RateBarRenderer.barPaint() -> Paint`.

- [ ] **Step 1: failing tests.**
  - `SectionHeadingTest.headingUsesSmallCapsGoldAndCount`: the font family is `Fonts.headingSmallCaps(..)`'s, the foreground is `Theme.current().goldText()`, and `text()` is `"Dropped by (34)"`.
  - `SectionHeadingTest.ruleFadesRight`: painting into a 300×24 image, the pixel under the heading at x = 290 has lower alpha than at x = 120.
  - `ChipTest.rankStyles`: rank 1 → ELITE, gold outline and gold text; rank 4 → RARE, outline `#C0C6CF`; rank 2 → RARE_ELITE, silver outline and gold text; rank 3 → BOSS, outline `#C8463C` and text text.primary; rank 0 → empty.
  - `ChipTest.chipTextContrast`: every style's text is ≥ 4.5:1 on bg.base and bg.surface in both themes. Parchment is added in Task 4, so this test then iterates both palettes.
  - `RateBarRendererTest.barIsSixPxGradientWithTicks`: `barTrack().height == UIScale.scale(6)`; at 50 % the fill colour at the left differs from the right end (gradient); a tick pixel at 10 % of the track has alpha > 0; the number text is unchanged ("60.00 %").
  - `StatusBarTest.provenanceFromMeta`: meta `client_product=wow_classic_beta, client_build=1.60.1.70425, client_item_count=20492` gives `"Forever beta 1.60.1 · 20,492 items"`; missing keys give `""`; the bar's preferred height is `UIScale.scale(22)`.
  - `StatusBarTest.goldTopHairline`: row 0 of a painted 200×22 image is `hairline()`.
  - `SourceSectionTest` (existing tests) updated to read `heading().text()`.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. `RaisedBorder` is a `FlatLineBorder`-style 1 px rounded border in `hairline()`. Apply it to `ItemCardPanel`'s holder, the zone map panel and the quest map panel.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): WoW chrome — section headings, outline chips, gold bars and status bar`.

