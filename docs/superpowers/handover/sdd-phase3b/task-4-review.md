**Approved** (spec ✅, quality ✅). 0 critical, 0 important, 4 minor.

# Task 4 review: Parchment theme (e318076)

Reviewed `git show e318076:<path>` for every changed file plus the diff package. I recomputed every WCAG ratio in the report independently and they match to two decimals. Maven was not run, per instructions. The verify was green on e318076 according to the controller.

## Spec compliance

| Requirement | Result |
|---|---|
| Every Parchment token verbatim (base, surface, raised, text, muted, link, gold, gold text, warn, alternate row) | ✅ `Tokens.java:44-51`, asserted one by one in `TokensTest.parchmentValues` |
| Quality colours 0-7 verbatim, including the new artifact #7A5E14 and heirloom #00687F | ✅ `QualityColors.java:50-53`, asserted in `QualityColorsTest.parchmentQualities` and again in `parchmentValues` |
| `Tokens.LIGHT` removed, every caller updated | ✅ `git grep Tokens.LIGHT` at e318076 only hits the plan document |
| `Mode.label()` returns "Slate" / "Parchment" | ✅ `ThemeManager.java:19-31` |
| Saved `dark` / `light` still restore | ✅ The stored values are unchanged. `labelsAndSavedValues` seeds "light", then checks that `applySaved` installs Parchment and that `apply` still writes "light". |
| Plate and cardBg navy #0C1424 in both | ✅ `platesStayNavy`, `slateValues`, `RailTest.plateStates` iterates both palettes |
| Table, List and TextField backgrounds = raised | ✅ Properties lines 19-24. `lafUsesTokens` asserts all three via UIManager. |
| Alternate row #F6EEDC | ✅ Properties line 26 and Tokens, asserted in both places |
| `@accentColor #8A6D1E`, `Component.linkColor #1F4E8C` | ✅ |
| Menu reads "Slate theme" / "Parchment theme" | ✅ Two radio items, accepted by the controller ruling |
| Tests the brief names: `TokensTest.parchmentValues`, `QualityColorsTest.parchmentQualities`, `ParchmentContrastTest.smallTextOnTableBackgrounds` / `headingsOnPage` / `platesStayNavy`, `ThemeManagerTest.labelsAndSavedValues` | ✅ All present |
| Map pins and the tooltip card keep fixed art | ✅ Untouched. `PinLegendTest` iterates both palettes. |

**Carried-over items are closed.**
- **Focused-table selection foreground.** It was 1.33:1. Parchment now sets all four `Table.selection*` keys, and the text pair measures #2B2416 on #FDEDB4 = 13.13:1. `parchmentTableSelectionTextReadsAtFourAndAHalfToOne` reads the resolved UIManager keys, which FlatTableUI swaps on focus. So it measures what FlatLaf paints.
- **List pairs.** The new `listSelectionTextReadsAtFourAndAHalfToOneInBothThemes` covers Parchment's FlatLaf-derived list selection. I recomputed white on #8A6D1E at 4.90:1. `MasterList` and `SearchDropdown` draw selected rows in the list's selection foreground, so that pair is the one that is painted.
- **Quality text on the alternate row and selection.** `smallTextOnTableBackgrounds` covers qualities 0-7 plus text, muted, link, gold text and warn on raised, the alternate row and the selection. The worst pair is poor on the selection at 4.555:1, which I recomputed. That includes legendary, artifact and heirloom. Tokens are tied to UIManager because `lafUsesTokens` asserts the same alternate row and selection values.

**Other checks passed.**
- **Real thresholds.** The contrast tests assert ≥ 4.5 for text and ≥ 3.0 for the silver outline, not "> 0". `TokensTest` also pins the known 4.49:1 gap with `isBetween(4.4, 4.5)`.
- **Both palettes.** `ChipTest.chipTextContrast` iterates Slate and Parchment.
- **User-facing text.** The README and status bar have no leftover "Dark theme" or "light theme" wording.

## Findings

### Minor

1. **Some properties literals are duplicated with no test tying them to Tokens.**
   - **Where:** `WowForeverLightLaf.properties:5,7,32` and `ThemeManagerTest.java:158`.
   - **What is wrong:** `@muted` (#6B5E45) feeds `Label.disabledForeground`, but no test compares that key to `Tokens.PARCHMENT.textMuted()`. `@surface` (#EADCBB) and `@warnColor` are defined but no key in the file references them. `propertiesResourcesContainTokens` still checks for the string "#EADCBB", so it passes whether or not the value has any runtime effect.
   - **Why it matters:** A later palette edit can change Tokens and leave the LAF stale, or the reverse, with every test still green. That is the drift the "no untied duplicate literals" rule exists to stop.
   - **Fix:** In `lafUsesTokens`, add `assertEquals(p.textMuted(), rgb("Label.disabledForeground"))`. Assert `Panel.background`, `Table.background`, `Table.alternateRowColor` and `Component.linkColor` against `p.bgBase()`, `p.bgRaised()`, `p.altRow()` and `p.link()` instead of literals. Then either drop the unused `@surface` and `@warnColor` from both properties files and from the string check, or note that they are documentation only.

2. **Chip carries Parchment palette values and branches on the dark flag.**
   - **Where:** `Chip.java:30-32,83,94`, and the `Theme.java:8` javadoc.
   - **What is wrong:** `PARCHMENT_SILVER` and `PARCHMENT_GOLD_TEXT` live in a widget and are chosen with `t.dark() ? … : …`, so palette knowledge sits outside `Tokens`. The `Theme` javadoc still says `goldText` is what the Elite and Boss chips use. On Parchment the Elite text is now #785C12. The Boss chip's text was already text.primary, so that part of the sentence was inaccurate before this task.
   - **Why it matters:** A third palette, or a tweak to Parchment's gold, has to find this branch by hand. A reader trusting the `Theme` javadoc will look in the wrong place. The values themselves are accepted by ruling.
   - **Fix:** Optionally move both colours into `Theme` as chip tokens, for example `chipSilver` and `chipGoldText`, which equal `goldText` on Slate. At minimum, correct `Theme.java:8` to say that chips use a Parchment-specific gold, and drop "and Boss".

3. **Two new lines break the 120-column convention.**
   - **Where:** `StatusBar.java:42` is 133 columns and `QualityColors.java:8` is 127 columns. `WindowMenus.java:20-21` wraps `{@link #addHelpItems}` onto its own line, followed by an orphaned "(Keyboard shortcuts, About)".
   - **Why it matters:** The codebase stays at or under 120 columns. Only three older lines exceed it. These are cosmetic.
   - **Fix:** Wrap the `toggle(...)` call and the javadoc lines, and reflow the `WindowMenus` class javadoc.

4. **"Light theme" wording remains in internal javadoc.**
   - **Where:** `QualityColors.java:33`, which reads "on the dark … or light theme", and `TooltipLinesPanel.java:36`, which reads "the light theme too".
   - **Why it matters:** Neither is user-facing, so this is optional consistency with the rename.
   - **Fix:** Say "Slate or Parchment" when next touching these files.

## Noted, not findings

- **The selection is barely visible against the rows.** #FDEDB4 is only 1.08:1 against raised and 1.01:1 against the alternate row, so it is told apart mostly by hue. The controller accepted it. The table renderers in `LinkRenderer`, `LootTables`, `DividerRows` and `OriginChip` draw quality colours only on unselected rows, and selected rows use the selection foreground at 13.13:1. So if the selection ever needs to be more visible, the brief's rule that quality colours must read on the selection could be relaxed, and the selection could then be darker.
- **The pinned 4.49:1 test is deliberate.** `TokensTest` asserts that gold text sits between 4.4 and 4.5 on Parchment's surface. It will fail if someone fixes the surface, which is the intended tripwire.
