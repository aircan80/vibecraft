Approved — spec ✅ quality ✅ (Critical 0, Important 0, Minor 8)

# Task 2 review: WoW chrome widgets (b64b8a5..9c793b3)

Reviewed read-only from `git show 9c793b3:<path>` and the review diff. Maven was not run, as instructed; the controller reports the full verify green on 9c793b3 (lua-table 61, importer 420, app 817).

## Spec compliance

Every file, interface and test in the brief is present with the brief's values.

| Brief item | Result |
|---|---|
| `SectionHeading(title, count)`, `setCount`, `text()`, `setIcon`, `setTrailing` | Present. `text()` gives "Dropped by (34)". |
| `Chip(text, Style)`, the seven styles, `rank`, `outline`, `textColor` | Present. Rank 1 ELITE, 2 RARE_ELITE, 3 BOSS, 4 RARE; 0, null and unknown are empty. |
| Chip colours | Elite gold outline and gold text. Rare `#C0C6CF` outline and text.primary. Rare Elite silver outline and gold text. Boss `#C8463C` outline and text.primary. Red is never text. |
| `RaisedBorder.create()` | 1 px, `hairline()`, 4 px radius (arc 8). Applied to the item card holder (`ItemPage.java:160`) and the zone map panel (`ZoneMapPanel.java:179`). |
| `RateBarRenderer.BAR_HEIGHT = 6`, `barPaint()` | Track height is `UIScale.scale(6)`. Gradient `barStart` to `barEnd`, track on bg.raised, ticks every 10 %. The number text is unchanged. |
| `StatusBar` | 22 px preferred and minimum, bg.surface, `hairline()` on row 0. Provenance left, message centre, toggles right. `provenanceText` and `setProvenance` as specified. |
| Toggles | `MainFrame.java:133-138` gives both toggles the existing `WindowMenus.LiveToggle` model reading `ThemeManager::isDark` and `layout::isCompact`. Their actions call `ThemeManager.toggle(this)` and `toggleCompactMode()`, the F10 path. No second copy of either state exists. |
| `AppController` | `AppController.java:116` passes the already-loaded `this.meta`. There is no new database read and no EDT work. The importer writes all three keys (`SqliteWriter.java:252-263`). |
| HeaderPanel title | Marcellus at `UIScale.scale(20f)`, re-applied in `updateUI`. This follows the text-size feature; it equals 20 pt at 100 %. Accepted as a justified reading of "Fonts.heading(20f)". |
| ItemCardPanel | Navy card kept; the line is painted in `Theme.current().cardBorder()` (`#46587A` in both palettes). |
| RankChip | Deleted with `RankChipTest`. `NpcPage` uses `Chip.rank(..).orElse(null)`. `InstancePage` never referenced `RankChip` at b64b8a5, so the brief's mention of it was a plan error and nothing was missed. |
| Tests | All seven named tests exist. `SourceSectionTest` and every other `heading().getText()` caller now read `heading().text()`. |
| Commit | Message and the `Claude Fable 5.1` trailer match the plan. |

**Test honesty.**
- `ruleFadesRight` paints into a 300×24 ARGB image and reads real alpha on row 23. The heading is non-opaque, so only the rule contributes. Near alpha is about 153 and far about 8.
- `chipTextContrast` iterates `Tokens.DARK` and `Tokens.LIGHT`, every style, on bg.base and bg.surface.
- `barIsSixPxGradientWithTicks` compares two fill pixels for the gradient. The alpha check alone would be vacuous on the opaque track, but the test also requires the tick pixel to differ from the plain `bg.raised` pixel 2 px to its right, which makes it real.
- `goldTopHairline` compares row 0 with `hairline()` composited over `bg.surface` in both themes. This is the honest form of the brief's "row 0 is `hairline()`", because the hairline is 35 % alpha over an opaque bar. It also proves no child paints over row 0.
- `chipsAreOutlinesOnly` checks the red outline pixel and a zero-alpha interior.

**The "Data build" label.** The spec lists three zones: provenance left, message centre, toggles right. The implementation keeps the existing "Data build 6c9ed1fa" label inside the right zone, before the toggles. This preserves 2B behaviour rather than adding new behaviour, the spec does not say to remove it, and the provenance and the build id carry different facts. It does not break the three-zone layout, so it is not a spec failure. It is listed as Minor 2 so the controller can record the decision.

## Code quality

### Strengths
- Colours are read from `Theme.current()` on every paint or query in all new widgets, so theme switches need no listeners.
- Fonts are re-applied in `updateUI` for `SectionHeading` and `HeaderPanel`, so headings follow Ctrl+/- text size.
- The toggle wiring reuses `LiveToggle` and the existing `ThemeManager.toggle` and F10 method, with no duplicated state.
- `provenanceText` is a pure static function with a careful empty result for missing, blank or non-numeric values. It never mislabels a product that is not the Forever beta.
- The test updates are mechanical and complete. No unrelated behaviour changed.

### Critical
None.

### Important
None.

### Minor

1. **The small-caps family assertion depends on test order.**
   - `app/src/test/java/dev/wowforever/app/ui/widgets/SectionHeadingTest.java:19`
   - Without `Fonts.install()`, both `Fonts.heading` and `Fonts.headingSmallCaps` return the same fallback family, such as Georgia. A focused run of this class would then pass even if `SectionHeading` used `Fonts.heading`. The full verify happens to run `FontsTest` first (ui/theme before ui/widgets), so the check is real there.
   - Fix: call `Fonts.install()` in a `@BeforeAll` (it is idempotent), and also assert the family differs from `Fonts.heading(14f).getFamily()`.

2. **The status bar keeps a "Data build" label in the right zone.**
   - `app/src/main/java/dev/wowforever/app/ui/StatusBar.java:48-52`
   - The spec layout names only the toggles on the right. Keeping the label is defensible, as explained above, but it narrows the centred message area on small windows.
   - Fix: no code change needed. Record the decision in the plan's open questions. If the owner wants the strict three-zone layout, move the build id into the provenance tooltip.

3. **No test pins the controller-to-status-bar provenance wiring.**
   - `app/src/main/java/dev/wowforever/app/ui/AppController.java:116`
   - Removing this line would leave every test green, and the provenance would silently vanish from the app.
   - Fix: in `AppControllerTest`, build the controller with the three meta keys and assert `view.status().provenance()` is "Forever beta 1.60.1 · 20,492 items".

4. **No test pins the 20 pt Marcellus page title.**
   - `app/src/main/java/dev/wowforever/app/ui/detail/HeaderPanel.java:50-53`
   - The brief lists no test for it, but it is a spec value that a later refactor could drop unnoticed.
   - Fix: assert the title font's family equals `Fonts.heading(20f).getFamily()` and its size equals `UIScale.scale(20f)`.

5. **RaisedBorder has no test.**
   - `app/src/main/java/dev/wowforever/app/ui/widgets/RaisedBorder.java:31-48`
   - It is a new public widget that later tasks reuse, with spec values: 35 % gold and a 4 px radius.
   - Fix: paint it on a 40×20 ARGB image and assert the top-centre pixel equals `hairline()`, a corner pixel is lighter than it, and the insets are 1.

6. **Chip fonts do not follow the text size.**
   - `app/src/main/java/dev/wowforever/app/ui/widgets/Chip.java:43`
   - The bold 0.85× font is derived once in the constructor, as `RankChip` did. `updateUI` keeps that non-UIResource font, so after Ctrl+/- the chip beside the new 20 pt title stays at the old size until the page is rebuilt. `SectionHeading` and `HeaderPanel` already handle this.
   - Fix: move the `setFont` call into an `updateUI` override, deriving from `UIManager.getFont("Label.font")`.

7. **The quest map panel's RaisedBorder is not in any later brief.**
   - Plan, Task 11 file list (around line 493 of the plan)
   - The brief asked for the border on the quest map panel, which Task 11 creates. Task 11's text does not mention `RaisedBorder`, so the requirement can be lost.
   - Fix: the controller adds "frame `QuestMapPanel` with `RaisedBorder.create()`" to Task 11's brief.

8. **`QualityColors.rateBand` is now dead code.**
   - `app/src/main/java/dev/wowforever/app/ui/theme/QualityColors.java:44`
   - Only its own test uses it. The implementer flagged this.
   - Fix: delete it and its test in a later cleanup task.

### Recommendations
- **Parchment silver.** The fixed silver outline `#C0C6CF` is about 1.4:1 against Parchment base `#F3E9D2`, so Rare chips will nearly lose their outline in Task 4. The text still carries the meaning and passes 4.5:1, so this is a plan question for Task 4, not a defect here. A theme token for chip silver would solve it.
- **Testable toggle wiring.** The toggle wiring in `MainFrame` cannot be tested headlessly. A small helper such as `StatusBar.wire(BooleanSupplier dark, Runnable toggleTheme, BooleanSupplier compact, Runnable toggleLayout)` would let `StatusBarTest` click the toggles and check the selection.

## Declined to judge
- **Toggle height.** I could not confirm the 22 px bar has room for the FlatLaf toolbar toggles without running the app. By FlatLaf's toolbar-button sizing they should be about 18 px, which fits.
- **Gradient visibility.** The gradient's contrast against bg.raised and the tick visibility are Task 1 token choices, not Task 2 code.
- **Other gold headings.** Pages that draw their own gold headings outside `SourceSection` are not in this brief's file list. Later tasks restyle those pages.
- **Slow fallback font lookup.** `Fonts.headingFamily()` scans the installed families on every call when `install()` has not run. This only happens in tests and is Task 1 code.
- **Message truncation.** Long status messages are truncated without a tooltip in the narrower centre zone. This is cosmetic and the spec is silent on it.

## Assessment
**Ready to merge:** Yes. The minor items can be folded into a later fix wave.

**Reasoning:** Every brief requirement and test is present with the exact values, and the pixel tests are real. The wiring reuses existing state and adds no database or EDT work. The findings are test-coverage gaps and carry-forward notes, not defects.
