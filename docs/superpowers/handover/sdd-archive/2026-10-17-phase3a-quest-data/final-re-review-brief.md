# Phase 3A final re-review — scoped brief

Scope: the commits after 7cb870c on feature/phase3a-quest-data (the review package named in the dispatch holds the commit list, stat and full diff). They are the single post-review fix wave plus one owner-reported bug fix:

1. Fix wave (brief: `final-fix-wave-brief.md`; implementer report: `final-fix-wave-report.md`; original findings: `final-review-report.md`): I1 world-drop threshold `WORLD_DROP_MAX_PCT = 10.0`; M2 "Vanilla rate (VMaNGOS)" label; M4 LootMgr validity counters; M5 shipped-test assertions; M6 route column enum + negative scalar relation ids counted; M3 serverdb README rebuild section + `ServerDbPinTest` (pom default must equal `serverdb.lock`).
2. Focus fix (commit "fix(ui): clicking a link no longer pops the recent-searches list open"): `MainFrame.setDetail` parks focus on the holder before the swap and moves it to the new page's first table/list; `SearchBar.focusChanged(boolean, FocusEvent.Cause)` opens recents only on MOUSE_EVENT or after `focus()` (Ctrl+F, /). Tests in `SearchBarTest` and `MainFrameTest`.

Check:
- Each original finding is actually closed as the brief specified (threshold semantics: unknown or < 10 % folds; Hogger still Quest items 2 then World drops 23; a ≥ 10 % pool item stays in its class group; shipped murloc assertion present and honest).
- The fix wave's own choices: `ServerDbPinTest` (reads pom + lock — robust? fails for the right reason?), the M4 counter names and read-time placement, the M6 enum.
- The focus fix: is parking focus on `detailHolder` safe (JPanel focusable by default; any key bindings on the holder?), does moving focus to a `JTable` change its selection or scroll position, is `FocusEvent.Cause.MOUSE_EVENT` the right gate (Tab into the box now shows no recents until typing — acceptable?), does `focus()` still show recents on a real window (flag consumed by the resulting focus event)?
- Nothing else regressed: all existing tests kept, no behaviour change beyond the items, trailer on every commit.

Output: write `final-re-review-report.md` in this directory with a verdict (Approved / Needs fixes) and findings by severity with file:line. Reply with the verdict and counts only.
