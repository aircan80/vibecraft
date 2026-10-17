# Task 1 report: theme tokens, dark default, quality colours

Commit cc27279 `feat(ui): theme tokens, dark default and quality colours`.

## Implemented
- `ui/theme/Theme` (record of 9 tokens, `current()`/`setCurrent`), `Tokens` (DARK and LIGHT palettes, exact values), `QualityColors` (`text`, `canonical`, `rateBand`).
- `WowForeverDarkLaf` / `WowForeverLightLaf` subclasses of FlatDarkLaf / FlatLightLaf, each with `setup()`.
- `ThemeManager`: `Mode` enum, `current()`, `apply(Mode)`, default dark, `applySaved()`, `isDark()`, `toggle(root)` unchanged in behaviour. Same prefs node and key, so existing choices persist.

## LaF wiring
FlatLaf loads `<LafClassName>.properties` from the class's package. Files are `WowForeverDarkLaf.properties` and `WowForeverLightLaf.properties` in `app/src/main/resources/dev/wowforever/app/ui/theme/` (named after the classes, not the brief's `WowForeverDark.properties`, because FlatLaf requires that). They set @background/@foreground/@accentColor, link colour, arcs, focusWidth 2, panel/list/table/tree/text backgrounds, alternate row, selection colours. `ThemeManager.install` sets the LaF and `Theme.setCurrent`.

## TDD
Tests written first; compile failed (missing symbols). After implementation: QualityColorsTest 2 and ThemeManagerTest 5 green, including UIManager assertions for Panel.background, Table.background, Component.linkColor. `./mvnw -B -q verify` from the worktree root passes.

## Files
app/src/main/java/dev/wowforever/app/ui/ThemeManager.java; ui/theme/{Theme,Tokens,QualityColors,WowForeverDarkLaf,WowForeverLightLaf}.java; two properties files; tests ThemeManagerTest and theme/QualityColorsTest.

## Concerns
- Quality 7 light variant `#0090B8` is my choice. Selection backgrounds (`#3A3526` dark, `#EADFC0` light) are gold-tinted picks not in the spec.
- ThemeManagerTest writes the real user prefs node and restores it afterwards.
