### Task 10: Compact mode

**Files:** `ui/CompactShell.java` (420×720 single column: search with dropdown, then the page; tables show name, rate and one chip; other columns in the row tooltip), `MainFrame` swaps content panes on F10 with the same controller; `setAlwaysOnTop` toggle (Ctrl+Shift+T) persisted; separate bounds in `UiState`.
**Tests:** `CompactShellTest` (builds headless; compact column set; toggling preserves current target), `UiStateTest.compactBoundsSeparate`.
- [ ] Commit `feat(ui): compact second-screen mode`.

### Part 1 gate
- [ ] Full `./mvnw -B -q verify` green in the worktree; controller visual check with the screenshot script (home, search, item, NPC, instance, zone, compact); ledger "Part 1 complete". Then wait for 2A to merge, rebase `feature/phase2b-ui` onto `main`, resolve conflicts in `Columns`/`ItemPage`/`ZonePage` in favour of 2B's structure while keeping 2A's data accessors, and continue.

## Part 2 — after phase 2A merges

