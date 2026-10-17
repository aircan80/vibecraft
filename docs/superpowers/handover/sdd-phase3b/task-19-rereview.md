Approved (0 Critical, 0 Important, 0 Minor, 1 Nit open)

# Task 19 fix round 1 re-review (code half), commit 807185b

Read at 807185b via the fix diff and git show. No build or test was run; the implementer's green verify is taken as given.

## Findings closed

- **I1 closed.** ItemEffectLoader now gives the Era fallback to `era` items only. Hotfix items without Forever effects are
  counted (items and rows). The test is inverted: 16805 has no effects, 16806 (Era) keeps them, counter 1, old counter gone.
  It would have failed before. The Era path is unchanged for `era` items.
- **I2 closed.** The index entry gains `last_seen_modified` and `last_checked_at`; the watch compares the served date with
  `last_seen_modified` (falling back to `last_modified` for old locks). The lock's top-level record is always the index's
  newest snapshot, with `last_seen_*` for the snapshot served now. A revert warns. Tests cover the same bytes with a newer
  date (watch reads fresh, first date kept) and the revert. A genuinely newer upload still reads STALE, since the served date
  then exceeds the last seen one. The index order is untouched, so convert.py still orders snapshots oldest first.
- **I3 closed.** `bare_user_agent()` is used for the Raidbots and WoWDBDefs clients in fetch.py. `agent_for` gives the contact
  to wago.tools only. The fetch test records User-Agents at a local server with the contact env set. A unit test covers
  `agent_for`.
- **M1 closed.** Each snapshot is decoded with its own build's layout and carried over by column name. Snapshots lacking a
  layout or a column are skipped, listed by SHA in `hotfix.json`, warned on stderr, and reported as
  `hotfix.snapshotsSkipped` with a note. Python and Java tests cover it, and the README states the rule.
- **M2 closed.** Status-2 removals are counted in `hotfix.json` and as `hotfix.itemsparse.removed`, with tests. The PM
  question is raised in the report.
- **M3 closed.** The six stat ids are recorded in backlog row F3 and in the report.
- **M5, M6, N1, N2 closed.** The schema comment, the baseline problem collector, imports and the fixture text are done.
- **M4 deferred** to the data commit as ruled. **N3** is left to the PM as ruled.

## Open

- **Nit.** `tools/build_watch.py` `client_getter` (the line building `Client(host, agent_for(url, contact))`) has no test
  through a server. `agent_for` is unit tested, so the risk is a wiring slip only.
