# Task 1 report: fonts and theme tokens

Status: DONE_WITH_CONCERNS (concerns are small; see the end). Commit `b64b8a5` on `feature/phase3b-quest-ui`.

## What was built

- **Fonts** (`app/src/main/java/dev/wowforever/app/ui/theme/Fonts.java`).
  - `install()` loads `Marcellus-Regular.ttf` and `MarcellusSC-Regular.ttf` with `Font.createFont(TRUETYPE_FONT, stream)`, registers both with the `GraphicsEnvironment`, then calls `FlatInterFont.installLazy()` and `FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY)`. It is idempotent and synchronized.
  - A failed font load is logged at WARN and returns null. The Inter step catches `RuntimeException | LinkageError`. Nothing throws.
  - `heading(pt)`, `headingSmallCaps(pt)`, `headingFamily()`, `FALLBACKS = List.of("Cinzel", "Georgia", Font.SERIF)` and `resolve(Predicate<String>, boolean)` as specified. If either bundled font fails, both headings use the resolved fallback family, so heading and small-caps headings never mix families.
- **Resources** under `app/src/main/resources/dev/wowforever/app/ui/fonts/`: the two TTFs and `OFL.txt`. The two upstream OFL.txt files are byte-identical, so one copy ships.
- **Theme** gains `plate, hairline, barStart, barEnd, altRow, selection`, appended after `warn`. Only `Tokens` constructs `Theme`.
- **Tokens.DARK (Slate)**: bg.base `#12161D`, plate `#0C1424`, hairline `#D6B25E` at alpha 89, barStart `#8A6D1E`, barEnd `#D6B25E`, altRow `#1E232B`, selection `#29230F`. All other values unchanged.
- **Tokens.LIGHT** keeps its 2B values and gets placeholder chrome tokens until Parchment (Task 4): plate `#0C1424`, hairline `#9A7419` at alpha 89, barStart `#7A5E14`, barEnd `#9A7419`, altRow `#E9ECF0` (its current LAF alternate row), selection `#EADFC0` (its current LAF selection).
- **QualityColors** Slate rare `#4A9EF0`, epic `#B864F5`.
- **WowForeverDarkLaf.properties**: `@background #12161D`, `Table.alternateRowColor #1E232B`, `Table.selectionBackground #29230F`, `Table.selectionInactiveBackground #29230F`.
- **Main.startGui** calls `Fonts.install()` before `ThemeManager.applySaved()`.
- **Build**: root `pom.xml` property `flatlaf-fonts-inter.version` = 4.1; `app/pom.xml` depends on `com.formdev:flatlaf-fonts-inter`. The shaded jar contains the TTFs, OFL.txt and the Inter OTFs.
- **NOTICE.md** and `app/src/main/resources/NOTICE.md` (byte-identical) gain a "Fonts" section crediting Marcellus, Marcellus SC and Inter.
- **.gitattributes** gains `*.ttf binary` so the fonts are never EOL-normalised.

## Choices the brief left open

- **Selection `#29230F`.** The old Slate selection `#3A3526` gives epic 3.60:1 and rare 4.33:1, so it fails the 4.5:1 rule. `#29230F` is a dark gold-brown that keeps every small-text colour at least 4.5:1; the lowest is epic at 4.61:1. Visibly distinct in hue from the grey alternate row.
- **Inactive table selection** was `@raised`, where epic reads 4.25:1. It now uses the same `#29230F`, so the selected row in an unfocused table looks the same as in a focused one.
- **barStart `#8A6D1E`** for Slate ("dark gold"; the plan's Parchment gold). barEnd is accent gold.
- **The LAF and the tokens are tied by a test**: `ThemeManagerTest.lafUsesTokens` now asserts the dark LAF's alternate row, selection and inactive selection equal `Tokens.DARK.altRow()` and `selection()`, so the contrast test covers what is actually painted.
- **Inter version 4.1.** Maven Central lists 3.19, 4.0 and 4.1 for `flatlaf-fonts-inter` (metadata last updated 2025-03-09). The artifact is versioned after the Inter release, not after FlatLaf, and 4.1 is the current release published for the FlatLaf 3.x line. FlatLaf here is 3.6.

## Font sources

| File | URL | SHA-256 |
|---|---|---|
| Marcellus-Regular.ttf | https://raw.githubusercontent.com/google/fonts/9712ecf0710335ef60aa998f5ee3b3c7e40f5e7c/ofl/marcellus/Marcellus-Regular.ttf | 1cf0cd10b17d35e852729962cc1ffaffed94514895972458345e2df34abb2f81 |
| MarcellusSC-Regular.ttf | https://raw.githubusercontent.com/google/fonts/1e838fdd5dbb8daec48f4de4f76a1cfff4b3d1ed/ofl/marcellussc/MarcellusSC-Regular.ttf | 03044ab65228b0554c9a5c1e63df29d4e58a71857e212801aaf768dfd106bdf0 |
| OFL.txt | https://raw.githubusercontent.com/google/fonts/9712ecf0710335ef60aa998f5ee3b3c7e40f5e7c/ofl/marcellus/OFL.txt (identical to the marcellussc copy) | 715ba72697e14daa9b29a333545f69bde7c497b9f77d0a2a4c4a5a752d961801 |

The commits are the latest google/fonts commits touching `ofl/marcellus` (9712ecf…) and `ofl/marcellussc` (1e838fd…), both dated 2026-03-25, per the GitHub commits API. The committed blobs were re-hashed from `git show HEAD:…` and match.

## Tests

New: `FontsTest` (marcellusLoadsFromResources, installTwiceIsHarmless, fallsBackToCinzelThenGeorgia, oflLicenceShips). Added: `TokensTest.slateValues`, `TokensTest.slateSmallTextContrast` (qualities 0–7, text.primary, muted, link, gold on bg.base, bg.surface, altRow, selection), `QualityColorsTest.slateRareEpic`.

Existing assertions that pinned the old values were updated to the spec values: `QualityColorsTest` (rare and epic dark text in two tests) and `ThemeManagerTest` (`#14171C` to `#12161D` in three places, plus the three new LAF-to-token assertions).

Red: `-Dtest=FontsTest,TokensTest,QualityColorsTest,ThemeManagerTest` failed at test compilation (`Fonts`, `plate()`, `altRow()`, `hairline()`, `selection()` missing).

Green: the same set plus `MainFrameTest` (NOTICE equality): 25 tests, 0 failures.

Full `./mvnw -B verify`, BUILD SUCCESS:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| Lua table parser | 61 | 0 | 0 | 0 |
| Data importer | 420 | 0 | 0 | 0 |
| Browser application | 811 | 0 | 0 | 0 |

## Concerns

1. **Commit trailer.** The plan says `Co-Authored-By: Claude Fable 5.1`, but my harness's attribution instruction for this session says `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, and that is the model that wrote this commit. I used the Opus 5.5 trailer. Amend if the controller wants the plan's trailer.
2. **List selection is not covered.** `List.selectionBackground` still comes from FlatLaf's accent-derived default (gold-based). I did not change it because the brief scopes this task to tables; a later task that renders quality text in lists should check it against the 4.5:1 rule.
3. **Shade warning** now lists `flatlaf-fonts-inter-4.1.jar` among jars with one overlapping resource (with flatlaf, flatlaf-extras and jsvg, which already overlapped). The overlapping resource is `META-INF/LICENSE`, and the build succeeds. The Inter licence itself also ships separately inside the jar at `com/formdev/flatlaf/fonts/inter/LICENSE.txt`, so it is not lost.

## Fix round 1

Committed on top of 9c793b3 (Task 2). Every review finding is fixed per the controller's rulings.

- **Important 1, selected-row text.** `WowForeverDarkLaf.properties` sets `Table.selectionForeground` and `Table.selectionInactiveForeground` to `@foreground`. FlatLaf's default had been `#090B0F`, 1.26:1 on `#29230F`. Now it is `#E4E1D8`, 11.97:1. `ThemeManagerTest.lafUsesTokens` asserts both keys equal `Tokens.DARK.textPrimary()`. The new test `ThemeManagerTest.slateTableSelectionTextReadsAtFourAndAHalfToOne` measures the two pairs FlatLaf paints, read from `UIManager`. The quality-on-selection checks in `TokensTest` stay. The light theme is untouched. Its 1.33:1 selected-row text stays with Task 4.
- **Minor 1 and 2, Theme javadoc.** It now says `selection` is a table's selected row, painted with the table's selection foreground. Lists and trees keep FlatLaf's accent selection, and list renderers use the selection foreground. The 4.5:1 claim is scoped to Slate. No `List.*` keys were added.
- **Minor 3, errors.** `Fonts.load` and `Fonts.isInstalled` now catch `RuntimeException | LinkageError | AWTError`. The Inter step catches the same set.
- **Minor 4, memoised fallback.** Before `install()`, `headingFamily()` resolves the fallback once and caches it in a volatile field. The system fonts are no longer enumerated on every heading call.
- **Minor 5, test isolation.** `FontsTest` resets `FlatLaf.setPreferredFontFamily(null)` in an `@AfterAll`.
- **Minor 6, line length.** The long lines in `Tokens.java` and `Fonts.java` are rewrapped. No main or test theme file exceeds 120 columns.
- **Minor 7, trailer.** b64b8a5 keeps its trailer. The new commit uses `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

Red: `-Dtest=ThemeManagerTest,FontsTest` failed two tests. `lafUsesTokens` expected `#E4E1D8` but got `#090B0F`. The selection-pair test failed on "focused table selection".

Green: the focused set (`ThemeManagerTest, FontsTest, TokensTest, QualityColorsTest, MainFrameTest`) ran 26 tests with 0 failures.

Full `./mvnw -B verify`, BUILD SUCCESS:

| Module | Tests | Failures |
|---|---|---|
| Lua table parser | 61 | 0 |
| Data importer | 420 | 0 |
| Browser application | 818 | 0 |
