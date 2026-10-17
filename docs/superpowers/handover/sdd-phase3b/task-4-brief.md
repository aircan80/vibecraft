### Task 4: Parchment theme

**Files:**
- Modify `{main}ui/theme/Tokens.java` (`LIGHT` becomes the Parchment palette from the Global Constraints, renamed `PARCHMENT`; `LIGHT` is removed and every caller updated), `{main}ui/theme/QualityColors.java` (light array = Parchment quality colours), `{res}ui/theme/WowForeverLightLaf.properties`, `{main}ui/ThemeManager.java` and `{main}ui/WindowMenus.java` (the menu item reads "Parchment theme" / "Slate theme").
  - The properties: `@background #F3E9D2`, `@surface #EADCBB`, `@raised #FBF5E6`, `Table.background`, `List.background` and `TextField.background` = `@raised`, `Table.alternateRowColor #F6EEDC`, a selection colour chosen by the contrast test, `@accentColor #8A6D1E`, `Component.linkColor #1F4E8C`.
  - `ThemeManager`: `Mode.label()` returns "Slate" / "Parchment". The saved values stay `dark` / `light`, so existing preferences keep working.
- Tests: modify `{test}ui/theme/TokensTest.java`, `{test}ui/theme/QualityColorsTest.java` and `{test}ui/ThemeManagerTest.java`; create `{test}ui/theme/ParchmentContrastTest.java`.

**Interfaces:**
- Consumes: Tasks 1–2 tokens and widgets.
- Produces: `Tokens.PARCHMENT` and `ThemeManager.Mode.label()`. `Theme.current().plate()` and `cardBg()` are `#0C1424` in both themes.

- [ ] **Step 1: failing tests.**
  - `TokensTest.parchmentValues` asserts every value in the Global Constraints table.
  - `QualityColorsTest.parchmentQualities`: `text(0..5, false)` = `#6B6B6B, #2B2416, #1A7A00, #0057B0, #7B22B8, #A85300`.
  - `ParchmentContrastTest.smallTextOnTableBackgrounds`: every quality 0–7, text, muted, link, gold text and warn is ≥ 4.5:1 on raised `#FBF5E6`, the alternate row `#F6EEDC` and the selection colour.
  - `ParchmentContrastTest.headingsOnPage`: gold text `#7A5E14` and muted are ≥ 4.5:1 on base `#F3E9D2`.
  - `ParchmentContrastTest.platesStayNavy`: `PARCHMENT.plate() == PARCHMENT.cardBg() == #0C1424`.
  - `ThemeManagerTest.labelsAndSavedValues`: `Mode.LIGHT.label()` is "Parchment"; a saved `light` preference still restores Parchment.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Map pins and the tooltip card keep their fixed art.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): Parchment light theme`.

