# Azeroth through a word — first corpus

Implemented `LoreSearchRepository` and `JournalLoreSearchPage` as a bounded local text-research slice. Integration API: `new JournalLoreSearchPage(repos.database(), navigator, worker)`; intended route `new ViewTarget.Forever("lore")`. The shell owns the shared router/discovery link.

Coverage: normalized Questie quest titles and objectives, not full readable books, dialogue, client transcripts or all Warcraft lore. Display the actual normalized projection's Questie refresh revision (base revision fallback) and database build ID. A matching name never establishes canonical identity, NPC mapping or a new relationship. Each result navigates to its exact quest ID. The excerpt is actual source text, rendered in a plain text component with a deterministic highlighted match; it is not interpreted as HTML.

The shipped corpus has 5,232 quest rows and 4,495 non-null objectives. Literal parameterized `LIKE` scans this small normalized table only, ordered by quest ID; 101-row sentinel detects explicit 100-result truncation. Escaping covers percent, underscore and backslash. No FTS migration, installed database writes, whole JSON scans, corpus preload or new indexes. SQLite LIKE case folding follows its default ASCII semantics; arbitrary text is not parsed as FTS operators.

The UI waits 300 ms after edits and accepts 3–120 characters. SQL executes on the supplied worker. Generation checks discard obsolete queued work, check again after acquiring the database lock and between result rows, and suppress stale UI callbacks. Query timeout is two seconds. Already-running SQLite VM work is not forcibly interrupted; bounded corpus and timeout limit that work. Query state restores through PageState. Empty, truncated and failed searches have explicit messages.

Isolated Java 21 compilation against the running application JAR passed. Six focused tests passed: exact source/ID/highlight; wildcard/slash literals and cancellation; sentinel cap; deterministic offsets; worker/route/restore/empty UI; recoverable error UI. No main Maven targets were touched.

Actual shipped read-only DB measurements in a fresh JVM: Dalaran 24 results / 14.3 ms first query; Stormwind 100 results + truncation / 9.7 ms; `the` 100 + truncation / 5.4 ms; literal `100%` no results / 3.4 ms; `/path` no results / 1.8 ms. These are repository timings, excluding UI debounce and rendering; not a claim of full-corpus lore coverage.
