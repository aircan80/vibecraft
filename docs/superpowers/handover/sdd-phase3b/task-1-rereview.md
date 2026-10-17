Approved: 0 Critical, 0 Important, 0 Minor.

# Task 1 re-review (fix commit 7291758)

Checked against `git show 7291758:<path>`; no Maven run, no repository changes.

| Finding | Status | Evidence |
|---|---|---|
| Important 1, selected-row text | Closed | `WowForeverDarkLaf.properties:26-27` sets `Table.selectionForeground` and `Table.selectionInactiveForeground` to `@foreground` (#E4E1D8, 11.97:1 on #29230F). `ThemeManagerTest.lafUsesTokens` asserts both equal `Tokens.DARK.textPrimary()`. |
| New pair contrast test | Closed | `slateTableSelectionTextReadsAtFourAndAHalfToOne` reads the four `UIManager` keys FlatTableUI swaps between on focus, so it measures the pair that is painted. The quality-on-selection checks in `TokensTest` stay. |
| Minor 1, 2 javadoc | Closed | `Theme.java` says `selection` is the table selection, lists and trees keep the accent selection, renderers use the selection foreground, and the 4.5:1 claim is scoped to Slate. Light theme untouched (carried to Task 4). |
| Minor 3 Errors | Closed | `Fonts.load`, `Fonts.isInstalled` and the Inter step all catch `RuntimeException \| LinkageError \| AWTError`. |
| Minor 4 memoisation | Closed | `headingFamily()` caches the fallback in a volatile field; `install()` still overrides via `family`. The unsynchronised write is benign (idempotent result). |
| Minor 5 test isolation | Closed | `FontsTest` `@AfterAll` calls `FlatLaf.setPreferredFontFamily(null)`. |
| Minor 6 line length | Closed | No line over 120 columns in any file touched by the commit. |
| Minor 7 trailer | Ruled: stays | b64b8a5 unchanged; the fix commit uses the plan trailer. |

Regressions: none found. The diff changes only the files listed, adds no new behaviour beyond the two properties keys, and the reported verify (61 / 420 / 818) is consistent with the added test.
