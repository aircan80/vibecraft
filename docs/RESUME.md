# Resume development — current checkpoint


## Latest continuation — collector projection and world-map drill-down

Codex, 2026-10-17. This section supersedes the historical checkpoint below.

- Primary checkout remains `feature/phase3b-quest-ui`. Parallel work was integrated and independently reviewed.
- Collector tooling now accepts repeatable explicit `--collector-file` inputs, archives each capture, and offers
  `project-collector --database FILE --ingestion ID` for separate offline projection. See README.
- Only direct known corpse sources become presence facts. Existing rates/origins remain intact. Noncorpse,
  hidden-source, unknown-entity and mixed-build evidence remains raw. Direct boss evidence additionally requires
  a matching known creature and client map; difficulty remains explicitly unknown. The app shows collector evidence
  on NPC/object/item/vendor/instance views without database work on the EDT.
- No synthetic collector facts are shipped. Real beta capture acceptance, AtlasLoot/hotfix data gates and releases remain open.
- The next roadmap slice now has Zones → Azeroth → continent → zone navigation, keyboard bindings, focus/history
  restoration and a remembered Map/List preference. See `WORLD_MAP_IMPORT.md` for acquisition/offline replay.
- The shipped hierarchy has 54 maps and 53 links from client **1.60.1.70374**, separately labelled from the main
  client **1.60.1.70450**. Zephras Isle and Darkspear Islands remain explicitly unplaced. Territory and
  New-in-Forever badges are omitted pending verified source comparisons. Later map layers remain future work.
- Shipped build ID: `241b1f205e1dd4843092c2ce61e5eca62a497edfb8107c179dcde26435cb1def`.
  It retains 248 archived artifacts and 114,477 structured records. All 47 existing application-data tables
  and all original archive/history rows were preserved; SQLite integrity passed.
- Additive map/collector updates share a stable content-based build identity and active composite input history.
  Ordinary core projection refuses applied overlays until explicit replay is supported, preventing silent loss.
- Full Maven verify passed: **1,710 tests, six skipped, zero failures/errors** (Lua 61, importer 535, app 1,114).
  Exact JAR SHA-256: `8921e766c611d04c998bf510b220b901da175f15ab5d1cb27318fcb9ccdee74e`.
- Final-JAR evidence is under `.local/collector-map-acceptance/`: 46 standard captures with zero geometry failures,
  warm main-page p95 maximum 100.891 ms; 12 map captures with compact bounds/Enter-route checks and map p95 31.06 ms;
  16 synthetic collector page captures with visible provenance and unknown-difficulty qualification.
  Timing is informational. Python suites were unchanged and not rerun. Native Robot input was unreliable
  under Wayland (intended and delivered click coordinates differed; frame capture blocked), so native interaction
  acceptance remains open. Headless Enter/bounds checks do not replace that gate.
- The previous Windows/addon branches and historical task worktrees remain available. Two older worktrees,
  `att-overlay` and `att-workflow`, contain uncommitted changes; preserve them. New temporary worktrees may be
  removed only after their commits and evidence are retained. No game-install files were touched.

The historical checkpoint below remains useful for environment and architectural constraints. Its old verification,
shipped build and active-work descriptions have been superseded by this section.

Saved by Codex, 2026-10-17. This is the current continuation checkpoint; older Windows instructions in
HANDOVER.md are historical. No further feature work was started after this checkpoint.

## Prompt to resume

> Resume the WoW Forever project at /home/aircan/Development/claud_java/claude_java. Read docs/RESUME.md, docs/HANDOVER.md and the linked progress ledger first. Continue from the saved checkpoint, inspect the current Git state, and choose the next unblocked roadmap task. Use parallel agents where useful without sacrificing quality. Keep the public dashboard current with progress, tests, code statistics and accurate Claude/Codex attribution. Keep the app available for review. Preserve source archives and existing app data, and do not release v0.3 until its remaining data gates are satisfied.

Short version if this project is already open:

> Resume from docs/RESUME.md. Continue development, use parallel agents where useful, and keep the dashboard and review app updated.

## Repository and environment

- Main checkout: `/home/aircan/Development/claud_java/claude_java`.
- Branch: `feature/phase3b-quest-ui`; latest implementation commit `bad8fdd00a8ab3f5afe8781ac39d49d7909ff207`.
  This document will be a later documentation-only commit. Working tree was clean before writing this checkpoint.
- No app Git remote is configured. `main` remains at `18cdf05`; do not merge/tag/release just because the latest work is complete.
- Java: project-local Temurin 21 (`.local/toolchains/jdk-21`); Maven wrapper 3.9.16 and local cache.
- Run `source .local/env.sh` before Maven/Java commands. In another worktree, source the primary checkout's env file.
- Python environment: `tools/clientdata/.venv`; local caches/toolchains/evidence are ignored and do not travel with Git.
- Git author for new Codex commits: `git -c user.name=Codex -c user.email=noreply@openai.com commit ...`.
- User authorizes parallel development; prior session had four concurrent slots including the controller. Check the actual
  new session limit rather than assuming it can be changed by project configuration. No agent work is outstanding at handoff.

```sh
cd /home/aircan/Development/claud_java/claude_java
git status --short
git log -5 --oneline
source .local/env.sh
java -version
./mvnw -B -q verify
```

Run full verify before code commits. Keep source, test and resource inputs frozen during each build. Use separate worktrees
for parallel coding, independently review changes, then test their combined integration. Do not blindly merge old worktrees:
`git worktree list` includes historical task branches and ATT branches, some with temporary uncommitted dependency copies.
Their intended changes have already been integrated. The separate collector branch described below is the exception.

## What is finished

- Phase 3B tasks 8–15 and 18: objective maps/patrols, quest page/loading/location display, chains, merged involvement,
  keyboard navigation/copy links, Forever dungeon presentation. Task 16 layout/search fixes and acceptance tooling are integrated.
- Versioned immutable source archives and regular app tables in **one SQLite database**, with separate acquisition and offline
  projection; content deduplication; generic CSV/Lua/JSON/lock-file records; extraction generations and field comparisons.
- View → Data sources: snapshots, files/hashes/source URLs, comparisons, exact projection execution history and active input set.
- AllTheThings native Forever source integrated; CLI imports include it unless `--skip-att` is explicit.
  Pin: `ATTWoWAddon/AllTheThings@a8c2fce5e8e52af3925352f5e61cbf7520ae3148`, MIT.
  Acquisition retains the complete 239-file Forever subtree, LICENSE and two runtime/semantics files (242 ATT files).
- Conservative ATT projection: accept entire representable prerequisite groups; preserve existing Questie groups on conflict.
  Unknown/dynamic fields, unsupported conditions, inherited gates and unsafe referenced requirements are withheld, with originals retained.
  Do not treat every `sourceQuests` list as OR: default is all eligible prerequisites; threshold/faction semantics matter.
- BUG-CSB-001 resolved using native authored Forever evidence:
  `(79007 OR 79008) → 79192 → 79980 → 79974 → 79975 → 79976`.
  Seven nodes, six links, a six-quest selected navigation spine. Search “This Must Be The Place” in the app.
  Accepted links have snapshot/path/line/evidence and pinned source URLs, labelled authored, **not in-game verified**.
  Local source overrides are hash-qualified and do not receive fabricated upstream links.

## Current shipped data and preservation constraints

The bundled database is `app/src/main/resources/data/wowforever.sqlite`, 271,523,840 bytes at this checkpoint.
Build ID: `86091b9bcf5d3891ea8a03e460bc74ce23361f0a6d99dea0a495792c158dc169`.
Original client build remains `1.60.1.70450`.

The original Windows client input directories are not available here. To avoid losing existing stats/maps/icons, the ATT
update archived the original derived application database as `legacy-app-database/base.sqlite`, then projected ATT onto
that retained baseline. This is explicitly **derived data**, not recovered Questie/AtlasLoot/Blizzard/VMaNGOS original files.
The catalog distinguishes its legacy provider reference links from archived original inputs. Never claim full field lineage
for older merged data. The baseline is reused on subsequent ATT captures to avoid recursive database-within-database growth.

Actual retained ingestion: `e24fb2a8ef6b293b4b54306cc5044de7fd25d1f86e057c4f6665a43ca376845c`.
It contains 242 ATT files, three configuration files and one derived baseline (246 artifacts); 114,354 structured records.
Projection attributed 257 links: 242 support existing rows and 15 fill gaps. One conflicting group was withheld;
2,146 conditions/coverage issues include one missing canonical endpoint beyond the parser's 2,145 issues.
All 257 attributed definition lines/URLs were independently checked. All prior relationships, 47 other original data tables,
and original metadata except the build ID were preserved. Integrity and table-digest comparisons passed.
The packaged import report retains the previous validation counters labelled historical, followed by the ATT projection report.

**Do not replace this database with a `--skip-client-data` normal rebuild for convenience.** A full replacement must supply
and validate the original-equivalent client/map/icon/stat inputs, preserve archive history, and pass shipped-data checks.
Do experiments on an isolated copy, then review the validated candidate before updating committed resources.

CLI stages (see README for all options):

```sh
# Capture normal pinned sources first; provide actual client/server inputs for a full app rebuild.
java -jar importer/target/importer.jar ingest --out research.sqlite --skip-client-data
java -jar importer/target/importer.jar project --database research.sqlite --ingestion INGESTION_ID
# Add/refresh ATT while retaining an existing legacy app baseline (capture only, then explicit projection).
java -jar importer/target/importer.jar ingest-att --out existing-app-copy.sqlite --skip-client-data
java -jar importer/target/importer.jar project --database existing-app-copy.sqlite --ingestion INGESTION_ID
java -jar importer/target/importer.jar sources --database existing-app-copy.sqlite
java -jar importer/target/importer.jar diff --database existing-app-copy.sqlite --before SNAPSHOT_ID --after SNAPSHOT_ID
```

`ingest-att` rejects explicit client/server/Questie/AtlasLoot directory options rather than silently ignoring them.
Projection uses archived configuration/manifests and no network/cache fallback, builds a candidate and publishes atomically.
Captured sources survive failed projection. Source tables/history must never be dropped during ordinary app-data refreshes.
Raw source-record browsing in the app is a later feature; detailed field-level lineage for all app values is also unfinished.

## Verification and local review artifacts

Latest completed full `./mvnw -B -q verify`: **1,677 tests, six skipped, zero failures/errors**.
Lua 61 (skip 1), importer 518 (skip 5), app 1,098 (skip 0).
Completed 2026-10-17T20:41:23.488149806Z against the exact implementation at `bad8fdd`.
The build ran in `../att-pipeline`; primary-checkout target test reports may be older. Authoritative saved log:
`.local/att-acceptance/verify.log`. Both runnable jars were copied into the primary checkout's target directories.
Python suites were previously 122 client-data +16 server-database tests; they were **not rerun** in the final ATT snapshot.

Verified app jar SHA-256: `c78b67f7f39ca4a923fedb2d51db4723920099978d270214a0424f932f73905e`.
The visual review used that exact jar, copied to `.local/att-acceptance/app-review.jar`.
46 panels: zero geometry failures; ten warm load cases, maximum p95 96.42 ms (informational target 300 ms).
Manifest: `.local/att-acceptance/panels/manifest.json`, created 2026-10-17T20:39:58.444133025Z.
Additional 16 targeted captures passed: catalog/history desktop+compact in both themes (12), expanded Sleeping Bag chain (4).
All seven nodes, six enabled source links and qualifications were visible. Database loaded off EDT and closed before rendering.

Local evidence directory `.local/att-acceptance/` contains:

- `baseline-preservation.json`, ingestion/projection logs, isolated source database and importer jar used for acquisition/projection.
- `panels/`, `catalog-with-headers/`, `chain-expanded-final/` (use these final directories, not earlier capture drafts).
- `harness/AttCatalogCapture.java` and `harness/AttChainCapture.java`, copied from temporary scratch files for later reuse.

Reusable committed capture runner:

```sh
source .local/env.sh
bash tools/acceptance/render-snapshots.sh app/target/wow-forever-browser.jar .local/NEW_EMPTY_CAPTURE_DIRECTORY 30
```

Only run appropriate checks after changes; do not repeatedly rebuild unchanged inputs just to regenerate statistics.

## Launch the review app

At handoff the latest verified app was active under `wowforever-browser.service`. It may have been closed since.
Check `systemctl --user is-active wowforever-browser.service`. Launch only the stable jar copy, not a jar Maven is overwriting:

```sh
source .local/env.sh
systemctl --user stop wowforever-browser.service 2>/dev/null || true
mkdir -p .local/running
cp app/target/wow-forever-browser.jar .local/running/wow-forever-browser.jar
systemd-run --user --unit=wowforever-browser --collect \
  --setenv="DISPLAY=$DISPLAY" --setenv="WAYLAND_DISPLAY=$WAYLAND_DISPLAY" \
  --setenv="XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR" \
  --setenv="DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS" \
  --working-directory="$PWD" "$JAVA_HOME/bin/java" -jar "$PWD/.local/running/wow-forever-browser.jar"
```

Use the desktop-session environment. A background process launched with plain nohup has previously died with its tool session.
Never touch the user's WoW installation. Runtime network access occurs only after a deliberate source-link click.

## Public dashboard

- URL: https://wow-forever-progress.happystoat6.chatgpt.site
- Separate source repo: `/home/aircan/Development/claud_java/dashboard`.
- Current source: `c750428ea3171610fb794fd75f1571f8a042c95a` (clean at handoff).
- Sites project ID: `appgprj_6ad3c67d11e08191af4d5a8a27a07b37`; audience is **public**, as explicitly requested.
- Latest successful deployment: `appgdep_6ad3de3c25a08191ba6046314ef43927`, 2026-10-17T20:44:50.623523+00:00.
- `status.json` controls task/acceptance notes; `verification.json` contains last COMPLETED Java and visual evidence;
  `refresh.py` generates `dist/data.json` and measures committed first-party physical nonblank code/test lines.
- At this snapshot: 37,192 code lines +29,500 test lines across 572 source/test files. Generated/vendor/assets/fixtures/docs
  are excluded. Metrics describe committed app code at `bad8fdd`, not this later documentation checkpoint.
- Never scrape in-flight Surefire results or replace named prior results with guessed counts. Keep Claude/Codex attribution accurate.

After substantive work, update metadata then run `APP_REPO=/home/aircan/Development/claud_java/claude_java python3 refresh.py`
in the dashboard checkout and appropriate JS/JSON checks. Read the installed Sites skill before publishing. Use its native
source helper, exact project ID, archive/save/deploy workflow and preserve the public audience. Mint a fresh short-lived
write credential; pass it to the helper via stdin and never save or print tokens. Credentials from this chat will not survive.
Helper: `/home/aircan/.codex/plugins/cache/openai-curated-remote/sites/1.0.2-remix-b.1/scripts/site-workflow.mjs`.
Node: `dashboard/.local/node/bin/node` (prepend that directory to PATH). Package `dist/` using the helper with absolute
archive path, then save/deploy the exact returned pushed commit/archive; poll only nonterminal deployments.
Do not use the historical dashboard scripts to replace the current Sites workflow.

## Remaining work and first decisions on resume

1. Read the roadmap and phase-3B ledger, inspect actual available input files, then pick an unblocked task. Do not assume
   the following external inputs have appeared since this checkpoint.
2. **Task 19 data gate:** AtlasLoot Classic Forever release archive by sliccer; historical download source is
   https://www.curseforge.com/wow/addons/atlasloot-forever/files . Recheck current version/details when resuming.
   The Windows handoff required a manually downloaded zip because automated acquisition was refused. Check local caches
   first; use the new platform's actual cache paths, not the old C:\Users path. Pin version/SHA/license/credits and test
   its rates/Forever applicability. Hotfix/hidden-item regeneration and a deliberate Questie refresh remain data work.
   The historical Task 19 acceptance patch is `docs/superpowers/handover/t19-shipped-tests.patch`.
3. **Task 20b:** collector importer and real-observation acceptance. Collector addon code is on the separate
   `feature/collector-addon` branch at `4be8b9f`, worktree `.worktrees/collector-addon`; it has not been merged into
   the current app branch. Inspect its README and ledger before integrating. Actual SavedVariables observations need
   an owner beta session; never fabricate them. Import observations as their own versioned source, with provenance,
   followed by separate projection consistent with the new source architecture.
4. If external data gates remain unavailable, progress on independent importer/collector tooling or source diagnostics
   without claiming real-data acceptance. Later advanced source-record browsing, wider fact lineage and native Blizzard
   adapters are planned; user approved browsing later, not a claim that a full explorer already exists.
5. Release gates 19/20b remain open. No v0.3 tag, release, merge-to-main or claim of a complete data refresh at this checkpoint.

Additional research already completed:

- QuestieDB remains pinned at `49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4` for reproducible imports.
  A bounded check of `e9a67d1a2753a0e64c4c96bf7864788fac7b765f` still omitted Sleeping Bag Forever links;
  do not refresh merely hoping that omission disappeared. Recheck upstream when a deliberate refresh is undertaken.
- Client `QuestLineXQuest` at 1.60.1.70450 had 22 rows, none for these Sleeping Bag IDs; `QuestLine` had three rows,
  none matching. `QuestV2` contained the seven IDs but no prerequisite links. Direct client mining does not fill this gap.
- Wowhead was discussed as a possible source, but no Wowhead quest scraper was added; direct quest pages hit access/JS
  restrictions. Do not imply Wowhead scraping is implemented or bypass restrictions.
- Native ATT Forever data is the selected additional authored source. SoD corrections, guides and shared quest-giver
  objects were corroboration, not silently promoted prerequisites.

## Product/quality preferences that persist

- Continue authorized development autonomously; no repeated permission checks for routine reversible work.
- Parallelize bounded independent work, preserve independent review and integration quality; all new work here was Codex.
- Keep the dashboard current and reopen the app for progress review when useful/requested.
- One database for now; preserve raw original inputs, unknown fields, versions/diffs, source URLs and separate projection.
- Main-app provenance should become richer over time; keep legacy/derived/unverified evidence honest.
- No database work on the Swing EDT; no frame/dialog construction in headless tests.
- Do not show objective quantities in the main UI; raw source records may retain them.
- VMaNGOS rates are “Vanilla rate”; Forever dungeon boss loot is labelled reworked. Do not disguise Era data as Forever.

## Further reading

- `docs/HANDOVER.md` — historical Windows context, source contract, collector/AtlasLoot requirements.
- `docs/superpowers/handover/sdd-phase3b/progress.md` — integration ledger and accepted evidence.
- `docs/ROADMAP.md` and `docs/superpowers/specs/2026-10-17-product-backlog.md` — roadmap and resolved/open bugs.
- `docs/superpowers/specs/2026-10-17-versioned-source-data.md` — source architecture and scope limits.
- `README.md`, `NOTICE.md`, importer CLI usage — operational and licensing details.
