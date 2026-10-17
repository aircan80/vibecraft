### Task 1: Fonts and theme tokens

**Files:**
- Create `{main}ui/theme/Fonts.java`.
- Create `{res}ui/fonts/Marcellus-Regular.ttf`, `{res}ui/fonts/MarcellusSC-Regular.ttf` and `{res}ui/fonts/OFL.txt`. Download them once from `github.com/google/fonts` (`ofl/marcellus`, `ofl/marcellussc`) and commit them.
- Modify `{main}ui/theme/Theme.java`, `{main}ui/theme/Tokens.java`, `{main}ui/theme/QualityColors.java` (Slate rare/epic text), `{res}ui/theme/WowForeverDarkLaf.properties` (`@background #12161D`, `Table.alternateRowColor #1E232B`), `{main}Main.java` (calls `Fonts.install()` before `ThemeManager.applySaved()`), `app/pom.xml` (+ `flatlaf-fonts-inter`), `NOTICE.md` and the app's NOTICE copy.
- Tests: create `{test}ui/theme/FontsTest.java`; modify `{test}ui/theme/TokensTest.java` and `{test}ui/theme/QualityColorsTest.java`.

**Interfaces:**
- Consumes: 2B `Theme` record, `Tokens.DARK`/`LIGHT`, `Contrast.ratio`.
- Produces:
  - `Fonts.install()`, which registers the two TTFs with the `GraphicsEnvironment` and installs Inter as FlatLaf's preferred family.
  - `Fonts.heading(float pt) -> Font` (Marcellus), `Fonts.headingSmallCaps(float pt) -> Font` (Marcellus SC) and `Fonts.headingFamily() -> String`.
  - `Fonts.FALLBACKS = List.of("Cinzel", "Georgia", Font.SERIF)`, plus a test seam `Fonts.resolve(Predicate<String> available, boolean resourcesLoaded) -> String`.
  - `Theme` gains the components `plate`, `hairline` (gold at 35 % alpha), `barStart`, `barEnd`, `altRow` and `selection`.

- [ ] **Step 1: failing tests.**
  - `FontsTest.marcellusLoadsFromResources`: after `Fonts.install()`, `headingFamily()` is `"Marcellus"`, and `headingSmallCaps(14f).getFamily()` is `"Marcellus SC"`.
  - `FontsTest.fallsBackToCinzelThenGeorgia`: `resolve(name -> name.equals("Georgia"), false)` returns `"Georgia"`, and `resolve(n -> false, false)` returns `Font.SERIF`.
  - `FontsTest.oflLicenceShips`: the resource `ui/fonts/OFL.txt` exists and contains "SIL OPEN FONT LICENSE".
  - `TokensTest.slateValues`: `DARK.bgBase() == #12161D`, `plate() == #0C1424`, `altRow() == #1E232B`, and `hairline()` has alpha 89 (35 % of 255).
  - `QualityColorsTest.slateRareEpic`: `text(3, true) == #4A9EF0` and `text(4, true) == #B864F5`.
  - `TokensTest.slateSmallTextContrast`: every quality text colour 0–7, text.primary, muted, link and gold is ≥ 4.5:1 on bg.base, bg.surface, `altRow` and `selection`.
- [ ] **Step 2:** run `-Dtest=FontsTest,TokensTest,QualityColorsTest` and expect FAIL.
- [ ] **Step 3:** implement the signatures above. Fonts load with `Font.createFont(TRUETYPE_FONT, stream)` and `GraphicsEnvironment.registerFont`. A load failure is logged and falls back; it never throws.
- [ ] **Step 4:** run again and expect PASS. Then run the full `./mvnw -B -q verify`.
- [ ] **Step 5:** commit `feat(ui): Marcellus headings, Inter body and Slate theme tokens`.

