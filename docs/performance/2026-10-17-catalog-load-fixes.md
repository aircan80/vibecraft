# Catalog loading and Journal responsiveness

The slow first visit to XP Food decoded complete native tables while holding the
shared SQLite connection monitor. Other pages queued behind that work. SQLite
being local did not make the full-table JSON expansion inexpensive.

The native reader now streams one JSON payload per record rather than expanding
and sorting every cell in SQLite. Item catalogs request the IDs, columns and
predicates they need. Zone/instance change headers query their own context rather
than building the entire change hub. Systems reads share decoded records per
Database. Controller generation checks discard obsolete queued page requests
before they start, and suppress stale results; already-running SQL is not forcibly
interrupted.

Independent fresh Database measurements against the shipped database (OS page
cache may be warm) on the first polish artifact:

| Catalog | Load ms |
| --- | ---: |
| Set 2133 | 36.55 |
| Stats | 1.80 |
| Trinkets | 201.98 |
| Recipes | 248.66 |
| XP Food | 194.40 |
| Comprehension | 78.46 |

Separate before/after repository probes measured XP Food at 6414/195 ms and
Comprehension at 2797/80 ms; full snapshot hashes and row counts matched for all
eight item catalog probes. These are repository timings, not guarantees of total
visible page latency. Native ItemSparse decoding measured 3213/389 ms in fresh
JVMs. No persistent database or index migration was required.

Remaining opportunities and index experiments are recorded in
[the independent SQL review](2026-10-17-sql-review.md).
