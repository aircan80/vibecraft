# Task 2 report: WoW chrome widgets

Commit: `9c793b3` on `feature/phase3b-quest-ui` (parent `b64b8a5`).

## What was built

- **SectionHeading** (`ui/widgets/SectionHeading.java`). Title and count in Marcellus SC at `UIScale.scale(15f)`, in `goldText()` per paint. A 1 px rule on the bottom row is drawn with a `GradientPaint` from `accentGold()` to fully transparent at the right edge. It offers `setCount`, `text()`, `title()`, `setIcon(Icon)`, `setTrailing(JComponent)` (null removes) and `label()`. The font is set again in `updateUI`, so it follows Ctrl+/- text size.
- **Chip** (`ui/widgets/Chip.java`). Outline only, never filled, and non-opaque. It has `Style { PLAIN, ELITE, RARE, RARE_ELITE, BOSS, REPEATABLE, FACTION }`, `Chip.rank(Integer) -> Optional<Chip>`, `outline()` and `textColor()`. The constants are `SILVER #C0C6CF` and `RED #C8463C`.
- **RaisedBorder** (`ui/widgets/RaisedBorder.java`). `create()` returns a 1 px border in `hairline()` with a 4 px radius and 1 px insets. It is applied to the item card holder in `ItemPage` and to `ZoneMapPanel`.
- **RankChip deleted.** `NpcPage` now uses `Chip.rank(rank).orElse(null)`, adds the badge only when present, and `rankChip()` returns null for normal or unknown ranks. `InstancePage` never used `RankChip`, so it is unchanged.
- **SourceSection.** Its heading is a `SectionHeading`, and `heading()` now returns that type.
- **HeaderPanel.** The title is `Fonts.heading(UIScale.scale(20f))`, reset in `updateUI`.
- **RateBarRenderer.** It has `public BAR_HEIGHT = 6` and `public Paint barPaint()`. The gradient runs from `barStart()` to `barEnd()` across the full track width, and `barPaint()` returns null for an unknown rate. The track is filled with `bg.raised`. Ticks are 1 px at every 10 %, drawn in `text.muted` at alpha 80. The number text and the disagreement marker are unchanged.
- **StatusBar.**
  - It is 22 px high (preferred and minimum), fills with `bg.surface` and draws the `hairline()` on row 0.
  - Provenance sits on the left, the message is centred, and the build label and two toggles sit on the right.
  - `provenanceText(meta)` returns `"Forever beta 1.60.1 · 20,492 items"`. It returns `""` if any of the three keys is missing or blank, or if the count is not a number.
  - `setProvenance(meta)` also puts the full client product and build in the tooltip.
- **ItemCardPanel.** The card keeps its navy background, and its border line is painted in `Theme.current().cardBorder()`.
- **Wiring.**
  - `AppController` calls `view.status().setProvenance(meta)` next to `setBuild`.
  - `MainFrame.wireStatusToggles()` gives each toggle a `WindowMenus.LiveToggle` model. That class went from private to package-private.
  - The theme toggle is selected while `ThemeManager.isDark()` is true, and its action calls `ThemeManager.toggle(this)`.
  - The layout toggle is selected while the window is in compact layout, and its action calls `toggleCompactMode()`, the same as F10.
  - The View menu items are untouched. No new key was added, so the README keymap table is unchanged.

## Tests, red then green

Red: the focused run failed to compile. The missing symbols were `Chip`, `SectionHeading`, `barPaint()`, `provenanceText`, `setProvenance`, `themeToggle`, `layoutToggle` and `heading().text()`.

Green, focused run of `SectionHeadingTest, ChipTest, RateBarRendererTest, StatusBarTest, SourceSectionTest, NpcPageTest`:

| Class | Tests |
|---|---|
| SectionHeadingTest | 3 |
| ChipTest | 4 |
| RateBarRendererTest | 11 |
| StatusBarTest | 7 |
| SourceSectionTest | 5 |
| NpcPageTest | 21 |

All of these passed with no failures.

New or changed tests:
- `SectionHeadingTest`: `headingUsesSmallCapsGoldAndCount`, `ruleFadesRight` and `takesAnIconAndATrailingControl`.
- `ChipTest`: `rankStyles`, `readsTheRankInWords`, `chipTextContrast` (all 7 styles, DARK and LIGHT, on bg.base and bg.surface) and `chipsAreOutlinesOnly`. It absorbs `RankChipTest`, which is deleted.
- `RateBarRendererTest`: `barIsSixPxGradientWithTicks` and `numberTextIsUnchanged`. The two band-colour tests were removed, and the unknown-rate test now checks `barPaint()` for null.
- `StatusBarTest`: `provenanceFromMeta`, `goldTopHairline` (both themes), `theThemeAndLayoutTogglesSitOnTheRight` and `theProvenanceIsHiddenInTheCompactWindowButTheTogglesStay`.
- Every `heading().getText()` call became `heading().text()`. That touched SourceSectionTest, ItemPageTest, NpcPageTest, ListPageTest, InstancePageTest, DetailPanelsTest, AppControllerTest, WindowMenusTest and ShippedDataTest.
- In `NpcPageTest`, the rank style is now `Chip.Style.BOSS`, and a normal NPC's `rankChip()` is null.

Full `./mvnw -B -q verify` exited 0:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 420 | 0 | 0 | 0 |
| app | 817 | 0 | 0 | 0 |

## Choices the brief left open

- **Hairline test.** `hairline()` is translucent and the status bar is opaque. So `goldTopHairline` checks that row 0 equals `hairline()` composited over `bg.surface`, computed by painting the same two fills into a 1×1 ARGB image. It checks this in both themes.
- **Tick test.** The test paints a 5 % bar with the renderer non-opaque into an ARGB image. The pixel at 10 % of the track has alpha > 0, and its colour differs from the plain `bg.raised` track 2 px to its right.
- **Gold choice.**
  - Chip outlines use `accentGold`, the gold for borders, and chip text uses `goldText`. In Slate both are `#D6B25E`.
  - The section rule also uses `accentGold`.
  - REPEATABLE has a `link` outline. PLAIN and FACTION have a `text.muted` outline. All three use `text.primary` text.
- **Heading sizes.** I measured that FlatLaf's `UIScale` user scale factor follows the text-size `defaultFont`, going from 1.0 to 1.5 at 150 %. So titles use `UIScale.scale(20f)` and section headings use `UIScale.scale(15f)`, both reset in `updateUI`, and they keep following the text-size feature. The brief gave no size for section headings, so I chose 15 pt.
- **Build label kept.** The status bar keeps the existing "Data build 6c9ed1fa" label, now at the right before the toggles. Existing tests and the home page depend on it.
- **Compact window.** The build and provenance are hidden there, but both toggles stay, because the layout toggle is a way back to the full window.
- **Toggle details.** The toggles read "Dark" and "Compact" and look like toolbar buttons. Their tooltips are "Dark theme (also in the View menu)" and "Compact window (F10)", and their accessible names are "Dark theme" and "Compact window". They are not focusable, since the keyboard already has F10 and the View menu.
- **Product names.** Only `wow_classic_beta` reads "Forever beta". Any other product shows its raw id, so the provenance is never mislabelled.
- **Raised panels.**
  - The ItemPage card holder wraps its old 10/12/2/12 margin around a `RaisedBorder` with 5 px padding, so the gold frame and the card's own navy line do not touch.
  - `ZoneMapPanel` wraps its old margin around a `RaisedBorder` with 6/6/4/6 padding around the controls, map and legend.
  - The quest map panel does not exist yet. `QuestMapPanel` is created by a later task, so that task should apply `RaisedBorder.create()`. The current `QuestPage` maps were left alone because that page will be rewritten.

## Concerns

- `QualityColors.rateBand` and its test are now unused in main code. I left them because removing them was outside the brief's file list; a later task can delete them.
- On the light theme, the silver chip outline `#C0C6CF` is faint. The text carries the meaning and meets 4.5:1, but Task 4's Parchment theme may want a darker silver.
- The new look has not been checked visually in the running app, because the tests are headless only.
