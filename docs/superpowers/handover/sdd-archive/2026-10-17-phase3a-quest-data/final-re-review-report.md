Verdict: Approved (Critical 0, Important 0, Minor 2)

# Phase 3A final re-review: 7cb870c..350a976

Scoped re-review of the fix wave (118a777..78389ca) and the focus fix (350a976) on feature/phase3a-quest-data. I read the
brief, the review package, the original report, the fix-wave brief and report, and the code at 350a976. I did not run
the build; the green verify on 350a976 (1,284 tests: lua-table 61, importer 420, app 803) is relied on. I made
read-only checks against the shipped database and the pinned `mangos.sqlite` (`?mode=ro&immutable=1`). I read the
JDK 21 AWT and Swing sources from the installed JDK's `src.zip` to judge the focus change. Nothing was run on a real
window.

## Original findings: closure

| Finding | Closed? | Evidence |
|---|---|---|
| I1 world-drop threshold | Yes | `ItemCategories.java:57-60` folds only when the rate is null or below `WORLD_DROP_MAX_PCT = 10.0`. Quest items, gear and bags are still excluded by class. `NpcPageTest` pins a 40 % item and an item at exactly 10 % staying in Trade goods. `ShippedDataTest` pins Hogger as exactly 4 rows, "Quest items (2)" to "World drops (23) ▸". The murloc assertion is honest: the shipped database has Murloc Eye on NPC 127 at 50.0035 % (questie-wowhead). README sentence updated. |
| M2 Vanilla label | Yes | `RateSource.java` LABELS has `"vmangos" -> "Vanilla rate (VMaNGOS)"`, with a test assertion. |
| M4 LootMgr validity | Yes | `ServerDbSource.invalid` mirrors `LootStoreItem::IsValid` in its order: mincountOrRef 0, then for items unknown item, chance in (0, 1e-6), maxcount below mincount. References skip the item checks, and the zero-chance rules stay at process time. Counters are read-time like `serverdb.loot.forbidden`, which is the right place because the rows never reach a template. The real `item_template` has `entry, patch`. I re-measured all five counters at 0 in both loot tables at patch 10. The group-share case is tested: a `mincountOrRef = 0` row no longer joins a group. |
| M5 shipped-test gaps | Yes | `assertThat(rs.next()).as(name).isTrue()` and a quest-only headline count of `isBetween(100L, 400L)` (208). |
| M6 route column, negative relation ids | Yes | `NpcRepository.RouteKey` replaces the column string, and both callers are updated. `QuestRelationWriter` skips a negative scalar id and counts `badValue.quest.<field>`, with a unit test. |
| M3 rebuild reproducibility | Yes | The README section covers the moving `db_latest`, keeping the cached archive, and changing the lock and the pom together. `ServerDbPinTest` reads the lock's `asset` short commit, which is the directory `fetch.py` `short_commit` writes to. It asserts that the pom default equals it, so it fails for the right reason. It depends on surefire's module working directory, which is the Maven default. |

Every commit carries the trailer the fix-wave brief named. No test method was removed, and the two modified tests
keep their old assertions.

## Focus fix: assessment

- **The cause gate is correct and is what closes the bug.** JDK 21 `DefaultCaret.adjustFocus`
  (`DefaultCaret.java:582-591`) requests focus with `FocusEvent.Cause.MOUSE_EVENT`, so a click into the box still
  opens recents. A traversal hand-off arrives as TRAVERSAL_FORWARD and window activation arrives as ACTIVATION or
  ROLLBACK, so neither opens the list. `focus()` sets the flag before the request. It drops the flag when the request
  fails, for example on an already-focused box, which is redundant (`KeyboardFocusManager.java:2432`). The resulting
  gain event consumes it.
- **Parking on the holder is safe.** `detailHolder` is a plain `JPanel` (`MainFrame.java:71`) with no bindings or
  focus listeners. JPanel and JLabel stay focusable by default.
- **Moving focus to the new table is safe.** A JTable focus gain only repaints the lead cell, so selection and scroll
  are unchanged. `AppController.setPage` restores page state after `setDetail`, and that restore is unaffected.
- **The guard keeps focus where it should stay.** Focus in the master list or the search box is outside the holder,
  so list preview and search-result opening keep their focus. Filter edits on list pages update rows in place, not
  through `setDetail`, so typing in a filter spinner is not interrupted.
- **Tab into the box no longer shows recents.** I consider that acceptable: typing and deleting, Ctrl+F and `/` from
  elsewhere, or a click still reach them. The second Minor covers the documentation side.

## Critical

None.

## Important

None.

## Minor

### M1. The parking comment describes a mechanism that does not happen

- **Where:** `app/src/main/java/dev/wowforever/app/ui/MainFrame.java:420-422`.
- **What is wrong:** The comment says parking the focus on the holder stops the removed page from handing focus to
  the search box. Lightweight focus requests are asynchronous on Windows (`syncLWRequests` is false by default). After
  `detailHolder.requestFocusInWindow()`, the old table is still the global focus owner when `removeAll()` runs.
  `Component.removeNotify` (`Component.java:7120`) therefore still calls `transferFocus(true)`, which queues a
  TRAVERSAL_FORWARD request to the next component, possibly the search box. Two things actually give the right
  result:
  - The final request at `:429` is the last queued lightweight request, and only the last one may trigger
    `restoreFocus` (`KeyboardFocusManager.java:2658`). That is what leaves focus on the new page.
  - The SearchBar cause gate keeps the transient traversal gain in the box from opening recents.
- **Why it matters:** The parking line is redundant, and the comment misleads. A maintainer trusting it could drop the
  cause gate as belt-and-braces and bring the bug back.
- **Fix:** Remove the parking call, or reword the comment. It should say that the removed focus owner still hands focus
  on by traversal, that the last request below wins, and that SearchBar ignores the transient traversal gain.

### M2. A click into the empty box of an inactive window shows no recents, and the README still says "the empty box shows your recent searches"

- **Where:** `app/src/main/java/dev/wowforever/app/ui/SearchBar.java:293-300` and `README.md:35`.
- **What is wrong:** The empty box may be the window's last focus owner when the user switches away, for example to
  the game. Clicking back into it then first restores focus with ACTIVATION or ROLLBACK
  (`DefaultKeyboardFocusManager.java:509-517`). The click's MOUSE_EVENT request is then redundant
  (`KeyboardFocusManager.java:2432`) or a duplicate of the pending one (`:2223`). Either way no MOUSE_EVENT gain
  arrives, and no recents open, although the user deliberately clicked into the box. Tab into the box also shows
  none. The README's unconditional sentence no longer matches either case. I derived this from the JDK 21 sources
  and did not observe it on a real window.
- **Fix:** Optionally, add a `mousePressed` listener on the text field that shows recents when the box is focused,
  empty and the dropdown is closed. Otherwise, make the README sentence say that clicking into the empty box, Ctrl+F
  or `/` shows recent searches.

## Notes, no action

- **maxcount = 0 references:** these still roll once. As the original review noted, the server loops `maxcount`
  times. The fix-wave brief ruled to keep one roll and count it, and the counter is 0 in the pinned database.
- **Spawn queries:** `NpcRepository.spawns` still concatenates a table and column name from literal callers. It was
  out of scope, as the fix-wave report says.
- **Shipped import report:** `import-report.txt` lacks the five new counters until the next `import-data` run, and no
  test reads them from it.
