Needs fixes (spec ✅, quality ❌): 0 Critical, 1 Important, 7 Minor.

# Task 1 review: fonts and theme tokens (18cdf05..b64b8a5)

The reviewer read the brief, the plan's Global Constraints (lines 30-147), the implementer report and the full diff. The built classes (`app/target/classes` at b64b8a5) were probed headless against FlatLaf 3.6. No repository files were changed.

## Spec compliance: ✅

Every brief requirement is met with the exact values.

| Requirement | Status |
|---|---|
| `Fonts.install / heading / headingSmallCaps / headingFamily / FALLBACKS / resolve` signatures | Met. `FALLBACKS = List.of("Cinzel", "Georgia", Font.SERIF)`. |
| Fonts load via `Font.createFont(TRUETYPE_FONT, stream)` and `registerFont` | Met (`Fonts.java:103-109`). |
| Load failure is logged and falls back, never throws | Met for realistic failures (see Minor 3 for `Error`s). A partial failure sends both headings to the fallback family. |
| Inter installed as FlatLaf preferred family; `flatlaf-fonts-inter` in `app/pom.xml` | Met (4.1, version property in the root pom). |
| `Main` calls `Fonts.install()` before `ThemeManager.applySaved()` | Met (`Main.java:78`). |
| TTFs are the real Google Fonts files | Verified. The reviewer downloaded both TTFs and both OFL.txt from `google/fonts` `main` and from the pinned commits 9712ecf and 1e838fd. SHA-256 of the working tree, the committed blobs and upstream are identical (1cf0cd10…, 03044ab6…, 715ba726…). The two upstream OFL.txt files are byte-identical, so shipping one copy is correct. |
| `OFL.txt` ships and contains "SIL OPEN FONT LICENSE" | Met. `.gitattributes` marks `*.ttf binary`, confirmed with `git check-attr`. |
| NOTICE credits in both copies | Met. The "Fonts" section credits Marcellus and Marcellus SC (OFL 1.1, commits pinned) and Inter (OFL 1.1). `cmp` shows the root and app copies byte-identical. |
| Theme gains `plate, hairline, barStart, barEnd, altRow, selection` | Met. Hairline is `#D6B25E` at alpha 89. |
| Slate: bg.base `#12161D`, plate `#0C1424`, altRow `#1E232B` | Met in `Tokens.DARK` and in `WowForeverDarkLaf.properties` (`@background`, `Table.alternateRowColor`). |
| Rare `#4A9EF0`, epic `#B864F5` | Met (`QualityColors.java`). |
| Tests: FontsTest (3 named + 1 extra), TokensTest.slateValues, slateSmallTextContrast, QualityColorsTest.slateRareEpic | Met. The contrast test covers qualities 0-7, text.primary, muted, link and gold on bg.base, bg.surface, altRow and selection, as listed. |
| `Tokens.LIGHT` untouched | Met. All 12 existing LIGHT values are unchanged. It only gains the six new record components as placeholders, which the record change forces. `WowForeverLightLaf.properties` is not in the diff. |

The two properties lines beyond the brief (`Table.selectionBackground` and `Table.selectionInactiveBackground` = `#29230F`) are justified. The brief introduces a `selection` token and requires small text at 4.5:1 on it, and the old `#3A3526` gave epic 3.60:1. `ThemeManagerTest.lafUsesTokens` ties the LAF to the token. Accepted as in scope, but see Important 1.

Measured Slate contrast for every listed colour is at least 4.5:1. The lowest are epic 4.61 on selection, 4.64 on altRow and 4.82 on surface, and rare 5.55 on selection.

## Code quality: ❌

### Critical

None.

### Important

**1. A focused table paints selected rows near-black on dark brown, at 1.26:1.**
- Where: `app/src/main/resources/dev/wowforever/app/ui/theme/WowForeverDarkLaf.properties:26`, and the coverage gap at `app/src/test/java/dev/wowforever/app/ui/theme/TokensTest.java:41-55`.
- What: no app renderer draws quality colours on a selected row. `LinkRenderer`, `LootTables.MutedRenderer`, `OriginChip`, `RateBarRenderer`, `DividerRows` and `MasterList.EntryRenderer` all leave the foreground to the component's selection foreground when `isSelected`. FlatLaf derives `Table.selectionForeground` from the accent selection, so under Slate it resolves to `#090B0F`. `FlatTableUI.toggleSelectionColors` (confirmed in the 3.6 bytecode) installs `Table.selectionForeground` on `Table.selectionBackground` whenever the table is the permanent focus owner. Measured pairs:

  | Pair (Slate) | Ratio |
  |---|---|
  | focused table: `#090B0F` on `#29230F` | 1.26 |
  | unfocused table: `#E4E1D8` on `#29230F` | 11.97 |
  | focused list: `#090B0F` on `#D6B25E` | 9.74 |
  | unfocused list: `#E4E1D8` on `#492C13` | 9.72 |

- Why it matters: clicking a row in any results or loot table focuses the table, and the selected row's text becomes unreadable. This breaks the plan's contrast rule ("small text is ≥ 4.5:1 on every background it is drawn on") on exactly the background this task chose. The defect predates the task (2B's `#3A3526` gave about 1.61:1), but this task made it worse. The report says the contrast test "covers what is actually painted", which is not true. The test checks quality colours on `selection`, which are never painted there, and not the selection foreground, which is.
- Fix: add `Table.selectionForeground = @foreground` to `WowForeverDarkLaf.properties` (11.97:1). Assert it in `ThemeManagerTest.lafUsesTokens`. Add a test that the `UIManager` pairs `Table.selectionForeground` on `Table.selectionBackground` and `Table.selectionInactiveForeground` on `Table.selectionInactiveBackground` are at least 4.5:1, so the test measures the pair FlatLaf really paints. Keep the quality-on-selection checks as a guard for future renderers that keep quality colour on selection. The light theme has the same bug (`#FFFFFF` on `#EADFC0`, 1.33:1). Flag it to the Parchment task (Task 4) rather than fixing it here, since LIGHT is out of scope.

### Minor

**1. Lists do not use the `selection` token, and the report's open concern needs a recorded decision.**
- Where: `WowForeverDarkLaf.properties` has no `List.selection*` keys; `Theme.java:14-16` describes `selection` as a table's selected row.
- What: lists and trees paint FlatLaf's accent selection (`#D6B25E` focused, `#492C13` unfocused), not `#29230F`. If quality text were drawn there it would measure epic 1.68 and rare 1.40 on `#D6B25E`, and epic 3.74, muted 4.31 and rare 4.50 on `#492C13`. Today no list renderer draws quality or muted text on a selected row: `MasterList.java:394-397` switches to the selection foreground, and `SearchDropdown` headings are not selectable (line 307). So there is no violation now, and the `selection` token is the table's only.
- Why it matters: a later task that keeps quality colour on a selected list row would silently fail 4.5:1, and the token name suggests a broader reach than it has.
- Fix: either keep lists on the accent selection and say in the `Theme` javadoc that `selection` is the table selection and that list renderers must use the selection foreground, or set `List.selection*` and `Tree.selection*` to `#29230F` with `@foreground` and extend the test. The first is the smaller change and matches current renderers.

**2. The `Theme` javadoc claims a contrast guarantee that LIGHT does not meet.**
- Where: `app/src/main/java/dev/wowforever/app/ui/theme/Theme.java:15-16`.
- What: "Small text of every quality colour is at least 4.5:1 on altRow and selection" is stated for the whole record. LIGHT's placeholders give legendary 4.05, artifact 4.13 and heirloom 3.12 on altRow `#E9ECF0`, and heirloom 2.78 on selection `#EADFC0`.
- Fix: scope the sentence to the Slate palette until Parchment lands.

**3. "Never throws" is not airtight for `Error`s.**
- Where: `Fonts.java:110` and `Fonts.java:124`.
- What: `load` catches `IOException | FontFormatException | RuntimeException`, and `isInstalled` catches `RuntimeException`. `GraphicsEnvironment.getLocalGraphicsEnvironment()` and the font manager can throw `AWTError`, `InternalError` or `UnsatisfiedLinkError` on a broken or minimal JRE. That would escape `install()` and stop `startGui` before the window opens. The Inter step already catches `LinkageError`.
- Fix: catch `RuntimeException | LinkageError | AWTError` (and `InternalError` if wanted) in both places, matching the Inter step.

**4. Before `install()`, every heading call enumerates all system font families.**
- Where: `Fonts.java:72-75` and `Fonts.java:97`.
- What: while `family` is null, `headingFamily()` calls `resolve(Fonts::isInstalled, false)`. That calls `getAvailableFontFamilyNames()` and builds a `HashSet` per fallback, on every `heading()` or `headingSmallCaps()` call. Headless page tests never call `install()`, so once later tasks put headings in page constructors or renderers this runs per page or per paint.
- Fix: memoise the fallback family, for example a lazy holder, or set `family` on the first resolve.

**5. `FontsTest` changes global FlatLaf state for the rest of the test JVM.**
- Where: `app/src/test/java/dev/wowforever/app/ui/theme/FontsTest.java:13` and `:23-24`.
- What: `Fonts.install()` calls `FlatLaf.setPreferredFontFamily(Inter)`, which persists into every later `setup()` in the same surefire JVM. Tests that run after `FontsTest` get Inter and tests before it get the default font, so size- or layout-sensitive assertions become order-dependent. The full verify is green today.
- Fix: reset with `FlatLaf.setPreferredFontFamily(null)` in an `@AfterAll`, or document the effect.

**6. Two lines exceed the 120-column convention.**
- Where: `Tokens.java:8` (154 columns) and `Fonts.java:29` (124).
- Fix: rewrap.

**7. The commit trailer differs from the plan.**
- Where: commit b64b8a5.
- What: the plan's Global Constraints require `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. The commit has `Claude Opus 5.5`, following the implementer's session attribution instruction, and the implementer flagged it.
- Fix: the controller decides. Amend only if the plan trailer is required. There is no code impact.

## Strengths

- Font provenance is exemplary: pinned upstream commits, a SHA-256 table, re-hashed committed blobs, and NOTICE text naming the commits.
- Partial-load handling keeps heading and small-caps headings in one family.
- Tying the LAF properties to `Tokens.DARK` in `ThemeManagerTest` is the right pattern. It only needs to extend to the selection foreground.
- The `#29230F` choice is well reasoned and documented, and the inactive selection now matches the active one.
