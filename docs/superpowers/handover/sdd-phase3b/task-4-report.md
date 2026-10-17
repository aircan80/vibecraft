# Task 4 report: Parchment theme

Status: DONE_WITH_CONCERNS (one open choice, see the last section). Commit `e318076 feat(ui): Parchment light theme` on `feature/phase3b-quest-ui` (parent 8bbae34).

## What was built

- `Tokens.PARCHMENT` replaces `Tokens.LIGHT`, which is removed. Every caller is updated: `ThemeManager` and nine test classes (StatusBar, ThemeManager, Tokens, Chip, PinLegend, Rail, RateBarRenderer, SectionHeading, SourceSection).
- Every value in the plan's Parchment table is used verbatim: base #F3E9D2, surface #EADCBB, raised #FBF5E6, text #2B2416, muted #6B5E45, link #1F4E8C, gold #8A6D1E, gold text #7A5E14, warn #8A5A00, alternate row #F6EEDC. Plate and card stay #0C1424 and the card border stays #46587A. The hairline is gold #8A6D1E at 35 % alpha.
- The bar gradient is not pinned by the table. It runs from gold text #7A5E14 to gold #8A6D1E, the same pattern as the 2B light theme.
- `QualityColors` light array = Parchment qualities: #6B6B6B, #2B2416, #1A7A00, #0057B0, #7B22B8, #A85300, #7A5E14, #00687F.
- `WowForeverLightLaf.properties`:
  - Panel uses base.
  - Table, List, Tree, TextField, TextArea and ComboBox use raised.
  - Alternate row is #F6EEDC.
  - Table selection background is #FDEDB4, focused and unfocused.
  - Table selection foreground is the text colour, focused and unfocused.
  - `@accentColor` is #8A6D1E and `Component.linkColor` is #1F4E8C.
- `ThemeManager.Mode.label()` returns "Slate" and "Parchment". The saved values stay `dark` and `light`.
- `WindowMenus`: the "Dark theme" check item becomes two live radio items, "Slate theme" and "Parchment theme". The item for the active theme is selected. Clicking the active one does nothing, and clicking the other one toggles the theme.
- Consistency fixes outside the brief's file list:
  - The status bar toggle reads "Slate" and its tooltip says "off for Parchment".
  - The README sentence now names View › Slate theme or Parchment theme.
  - The MainFrame javadoc is updated to match.
- `Chip`: Rare and Rare Elite outlines use `PARCHMENT_SILVER` #767C85 on Parchment. Elite and Rare Elite text uses `PARCHMENT_GOLD_TEXT` #785C12 on Parchment. Slate is unchanged.
- Map pins, PinLegend dots, the tooltip card and the rail plates are untouched. PinLegendTest and RailTest now iterate Slate and Parchment, and both pass.

## Carried-over findings closed

1. **Selection foreground.** Parchment sets both `Table.selectionForeground` and `Table.selectionInactiveForeground` to #2B2416 on #FDEDB4, which measures 13.13:1. `ThemeManagerTest.parchmentTableSelectionTextReadsAtFourAndAHalfToOne` measures the UIManager pairs FlatLaf paints, mirroring the Slate test. I also added `listSelectionTextReadsAtFourAndAHalfToOneInBothThemes` for the List pairs. It failed on the old light theme and passes on Parchment.
2. **Quality colours on table backgrounds.** `ParchmentContrastTest.smallTextOnTableBackgrounds` checks every quality 0–7, text, muted, link, gold text and warn on raised, the alternate row and the selection. All pass, and the minima are in the table below.
3. **Silver outline.** It is #767C85 on Parchment only, recorded with ratios below. `ChipTest.chipTextContrast` iterates both palettes and asserts chip text is at least 4.5:1 on base and on surface.

## Chosen colours and measured ratios (WCAG 2)

**Selection #FDEDB4 (pale gold).** Poor #6B6B6B is the binding colour. It needs a background with luminance of at least about 0.837, which is barely darker than the alternate row (poor reads 4.61:1 there). A darker selection therefore cannot pass, so the selection is set apart by hue, a lower blue channel, instead.

| Text | raised #FBF5E6 | altRow #F6EEDC | selection #FDEDB4 |
|---|---|---|---|
| poor #6B6B6B | 4.90 | 4.61 | 4.56 |
| legendary #A85300 | 4.95 | 4.66 | 4.60 |
| uncommon #1A7A00 | 5.04 | 4.75 | 4.69 |
| gold text / artifact #7A5E14 | 5.61 | 5.29 | 5.22 |
| warn #8A5A00 | 5.45 | 5.13 | 5.07 |
| heirloom #00687F | 5.87 | 5.53 | 5.46 |
| muted #6B5E45 | 5.83 | 5.49 | 5.42 |
| rare #0057B0 | 6.46 | 6.08 | 6.00 |
| epic #7B22B8 | 6.99 | 6.58 | 6.50 |
| link #1F4E8C | 7.64 | 7.20 | 7.10 |
| text / common #2B2416 | 14.13 | 13.30 | 13.13 |

The selection's luminance contrast is low against raised and the alternate row. If reviewers find the selected row too subtle, the only lever left is a more saturated pale gold such as #FFEEB0, where poor reads 4.60:1.

**Silver outline on Parchment, #767C85.** It is a non-text graphic, so the target is the 3:1 non-text contrast rule. It measures 3.48:1 on base, 3.10:1 on surface and 3.87:1 on raised. The old #C0C6CF measured 1.42, 1.26 and 1.58. Slate keeps #C0C6CF, which reads 10.55:1 on base.

## Open choice / concern

- **Gold text on surface is under 4.5:1.** The pinned gold text #7A5E14 reads 4.494:1 on the pinned surface #EADCBB, as the plan itself notes ("gold text 4.49"). Two rules collide here: "Chip text ≥ 4.5:1 on base and surface in both palettes" and the pinned token values.
  - I kept the tokens verbatim. On Parchment only, Elite and Rare Elite chips use #785C12, which reads 5.21:1 on base and 4.63:1 on surface, so the chip rule holds. The difference from #7A5E14 is invisible in practice.
  - Red evidence: with the plain gold text, `ChipTest.chipTextContrast` fails with "ELITE on bg.surface, light" at 4.4939.
  - The alternative is to exempt surface for Parchment chips, since chips sit only on the NPC page header, which is base. The team lead can pick that instead.
  - The `Theme.goldText` javadoc and `TokensTest` now state that gold text is guaranteed on base and raised in both themes and on surface in Slate only. Parchment's surface carries no small text.
- The View menu wording is my reading of "the menu item reads Parchment theme / Slate theme": two radio items built from `Mode.label()`.

## Red to green evidence

- **Compile red.** After renaming `Tokens.LIGHT` to `Tokens.PARCHMENT` in the tests, the focused build fails with "cannot find symbol" in StatusBarTest, ThemeManagerTest, ParchmentContrastTest, TokensTest and ChipTest.
- **Assertion red.** I stubbed `PARCHMENT = LIGHT`, `label()` as `name()` and `PARCHMENT_SILVER = SILVER`. The focused run then had 36 tests with 15 failures, including:
  - `labelsAndSavedValues`: expected Slate but was DARK.
  - `lafUsesTokens`: expected #F3E9D2 but was #F4F5F7.
  - `parchmentTableSelectionTextReadsAtFourAndAHalfToOne`: focused table selection.
  - `listSelection…`: LIGHT focused list selection.
  - `propertiesResourcesContainTokens`: missing #F3E9D2.
  - `smallTextOnTableBackgrounds`, `theSelectionIsAColourOfItsOwn`, `parchmentQualities` ([common]), `everyQualityHasColours`, `parchmentValues` ([base]), `parchmentDarkensTheGold…`, `goldTextReadsAt…`, `theSilverOutlineDarkensOnParchment` and the menu texts.
- **Green.** All focused classes pass: Tokens, QualityColors, ParchmentContrast, ThemeManager, Chip, WindowMenus, StatusBar, Rail, PinLegend, RateBarRenderer, SectionHeading and SourceSection.
- **Full verify.** `./mvnw -B -q verify` exits 0.

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| app | 850 | 0 | 0 | 0 |
| importer | 420 | 0 | 0 | 0 |
| lua-table | 61 | 0 | 0 | 0 |
