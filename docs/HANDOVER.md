# Handover: WoW: Forever Item & Drop Browser

Written 2026-10-17 at the owner's request so that work can continue on another computer, by a
person or by a different AI assistant. Everything a continuer needs is either in this repository
or named here with its location. Read this file first, then `README.md`, then the files it points at.

## Linux continuation — 2026-10-17

Development resumed with the owner's authorization on Linux at
`/home/aircan/Development/claud_java/claude_java`, still on `feature/phase3b-quest-ui`.
The Windows state below is historical. Source `.local/env.sh` for the local Temurin 21 toolchain and Maven cache;
run `./mvnw -B -q verify` before code commits. Python tools use `tools/clientdata/.venv`.

Tasks 8–15 and18 are implemented and independently reviewed: objective map layers, patrols, quest page/loading,
objective locations, chain display, merged quest involvement, keyboard/copy links and Forever dungeon presentation.
Task 16 layout/search audit fixes and reusable 42-panel visual/performance tooling are integrated; final combined
verification and the accepted 42-panel capture are tracked in the continuation ledger. Search completion preserves newer user edits. Layout checks
cover actual giver rows and objective links, including compact fallback text and dungeon source-note wrapping.

The owner additionally approved **one-database versioned source ingestion and separate offline projection**.
See `docs/superpowers/specs/2026-10-17-versioned-source-data.md` and README's archive section. The implementation
retains immutable original inputs, structured CSV/Lua/JSON evidence, snapshot comparisons and dataset-level provenance.
The app has View→Data sources with explicit legacy metadata when the shipped database predates archiving.
Full raw-record browsing is deferred by the owner; detailed field-level lineage and additional native-format
extractors remain explicitly separate from dataset attribution. The bundled database now archives ATT originals
and a clearly labelled derived legacy app baseline; its older upstream originals are still unavailable.

Open data gates remain Task 19's AtlasLoot Forever archive/hotfix regeneration and Task 20b's collector import and
real observation acceptance. Do not tag v0.3.0 or claim the data refresh shipped. BUG-CSB-001 records the missing
Cozy Sleeping Bag chain relationships. The audit confirmed the selected QuestieDB Forever input omits them;
the newly authorized AllTheThings native Forever source supplies them. The shipped data now includes all six links,
source attribution and both faction starts. Full verification passed (1,677 tests, six skipped, no failures/errors),
with 46 panel captures and targeted source/expanded-chain acceptance. These remain authored source claims, not
in-game verification; see BUG-CSB-001 for exact evidence.

Public progress dashboard: https://wow-forever-progress.happystoat6.chatgpt.site . Its separate source checkout is
`../dashboard`; publish updates through Sites. Completed work identifies Claude or Codex accurately.

The app can be launched independently of the tool session with a user systemd unit (copy the built jar into
`.local/running` first). Never overwrite the running jar while Maven rebuilds. The owner has repeatedly requested
that it remain available for review while development continues.

### AllTheThings source contract (Codex, 2026-10-17)

The owner explicitly authorized AllTheThings as another source. Pin
`a8c2fce5e8e52af3925352f5e61cbf7520ae3148` in `ATTWoWAddon/AllTheThings`; retain its
[MIT license](https://github.com/ATTWoWAddon/AllTheThings/blob/a8c2fce5e8e52af3925352f5e61cbf7520ae3148/LICENSE).
The acquisition manifest retains all 239 files beneath `.contrib/.db/forever/`, plus LICENSE,
shared `Shortcuts.lua` and runtime `src/Classes/Quests.lua`: 242 artifacts, 12,371,472 bytes at this pin.
The [Forever configuration](https://github.com/ATTWoWAddon/AllTheThings/blob/a8c2fce5e8e52af3925352f5e61cbf7520ae3148/.contrib/.db/forever/.config/forever.config)
uses the native Forever root, `DataPhase=FOREVER`, patch `[1,60,1,69893]` and explicit Forever/Camelot
preprocessor tags. This is a separate flavor, not imported SoD or Era prerequisite data.

The static parser never executes Lua. It retains quest metadata and issues, promotes representable explicit
prerequisites only, and withholds unknown fields, dynamic keys, unsupported timelines, inherited conditions,
unsupported referenced quests, and arbitrary k-of-n requirements. `sourceQuests` defaults to all eligible
references; `sourceQuestNumRequired`/`sqreq` specifies a threshold. Only verified opposite-faction opening
rows establish the Sleeping Bag alternatives. [Runtime semantics](https://github.com/ATTWoWAddon/AllTheThings/blob/a8c2fce5e8e52af3925352f5e61cbf7520ae3148/src/Classes/Quests.lua#L1678)
and exact native quest URLs are recorded in BUG-CSB-001. The pinned parser smoke found 2,599 static quest rows,
259 eligible link claims and 2,145 coverage/condition issues, including all six Sleeping Bag edges. These are
parser counts, not counts of selected changes in the final app database. Its 16 regressions passed; combined
importer/data/UI acceptance is pending and the bug is not yet closed.

Projection fills missing prerequisite groups without overwriting or unioning conflicting Questie groups.
Each accepted claim needs authored evidence status, exact snapshot/path/line and configured revision; local
input must remain hash-qualified rather than presented as byte-identical upstream content. Replay uses the
archived manifest, not whichever manifest a newer importer currently ships. Original bytes and unsupported
inputs remain in source tables. A legacy-app overlay must first archive the existing **derived database**;
it preserves earlier item/client/map data but does not recreate or claim to archive the original Questie,
AtlasLoot, Blizzard-client or VMaNGOS inputs. Baseline metadata links are references, distinct from archived
originals, and must not be inferred from unrelated later build metadata. Task 19 data and Task 20b remain open.

## 1. What this project is

A desktop research tool for World of Warcraft: Forever (Blizzard's Classic-era-based release, in
beta as product `wow_classic_beta`, live on 4 November 2026). Java 21, Swing + FlatLaf, one shipped
SQLite database built at build time from addon databases (QuestieDB, AtlasLootClassic; AllTheThings integration
is in progress as described above), Blizzard
client tables (fetched from wago.tools), the Classic Era item table as a fallback, the VMaNGOS world
database (community drop rates) and, since Task 19, the Raidbots mirror of the client's hotfix cache
(revealed hidden items). It runs on Windows, macOS and Linux, uses no network at runtime, never
modifies the WoW installation, and frames itself as a research/learning tool (like Wowhead), never a
leveling guide (no "recommended", no progress, no "next zone").

Owner framing, verbatim where it matters: "Let's make this the best wow companion app ever!";
"testing, testing, testing"; "keep that dashboard page updated every so often"; "run the app so I
can see it while you work".

## 2. Repository state at handover

- Repository root: `H:\claude_java` (no git remote; transfer with a git bundle, see §9).
- `main` = 18cdf05: v0.1, phase 2A (client data), phase 2B (UI overhaul, tagged `v0.2.0`), phase 3A
  (quest data, merged 2026-10-17). Tag v0.3.0 is cut only when phase 3B ships.
- `feature/phase3b-quest-ui` (current work): phase 3B tasks 1–7, 17 and the code half of 19 are
  complete and reviewed; see §4. Checked out in the main working tree.
- `feature/collector-addon` (worktree `.worktrees/collector-addon`): the in-game collector addon
  (Task 20, addon half), complete and reviewed; merge it into the 3B branch (or main) when the
  importer half (Task 20b) is done, or earlier if convenient. It touches only `tools/collector/` and
  one lua-table test.
- `feature/wow-forever-browser`: the historical v0.1 branch, already merged; delete at will.
- Git-ignored but essential: `.superpowers/sdd/<plan>/` holds every ledger, brief, report, review
  and PM memo. A copy is committed under `docs/superpowers/handover/` (see §7) so it survives the
  move. On the new machine the SDD skill expects the ledger at
  `.superpowers/sdd/2026-10-17-phase3b-quest-page-and-wow-look/progress.md`; copy it back there.

## 3. Environment setup on a new machine

1. JDK 21 (Temurin 21.0.12 was used) and the Maven wrapper in the repo (`./mvnw`). On the old
   machine shells did not have Java on PATH: prefix commands with
   `export JAVA_HOME=<jdk>; export PATH="$JAVA_HOME/bin:$PATH"`.
2. Python 3.11+ for the build-time tools: `tools/clientdata/README.md` lines 1–20 (create the venv,
   `pip install -r tools/clientdata/requirements.txt`; never install globally; always run the venv
   python with `-I`).
3. Build and test: `./mvnw -B -q verify` (several minutes; the app's shipped-data tests extract the
   80 MB database into a temp dir). Modules: `lua-table`, `importer`, `app`. Run the app with
   `java -jar app/target/wow-forever-browser.jar`.
4. Data caches (only needed to regenerate the database; the shipped DB is committed):
   - `~/.cache/wowforever-clientdata/<build>/` and `out/current/`: re-fetch with the commands in
     `README.md` "Rebuilding the database" (wago.tools, build pinned in `tools/clientdata/build.lock`).
   - `~/.cache/wowforever-serverdb/4641790/mangos.sqlite`: `python tools/serverdb/fetch.py`
     (pinned by `tools/serverdb/serverdb.lock`; the importer pom default must equal the lock,
     `ServerDbPinTest` enforces it).
   - `~/.cache/wowforever-clientdata/hotfix/<sha>.bin`: the Raidbots hotfix snapshot. **Copy this
     directory from the old machine** (the upstream file is overwritten in place, so a later fetch
     may not reproduce today's rows; `build.lock` records the SHA and header build).
   - `C:\Users\aircan80\.cache\wowforever-importer\atlasloot-forever-zip\`: where the owner drops the
     AtlasLoot Classic Forever zip (CurseForge blocks automated downloads). Copy it if present.
5. Optional: the WoW install (`C:\Program Files (x86)\World of Warcraft`, product `_classic_beta_`)
   is a read-only datamining and screenshot source. Never write under it. Build-time tools use
   wago.tools instead and do not need it.
6. The owner's email is only for identifying the owner. Never put it, or any identifier, in a
   request header or payload (one worker did once; it was refused and reported). Requests use the
   project's plain pipeline user agent; only wago.tools may receive the optional contact string.

## 4. Phase 3B status (plan: `docs/superpowers/plans/2026-10-17-phase3b-quest-page-and-wow-look.md`)

Order of execution as ruled: 1 2 3 4 5 17 19 6 7 | 8 9 10 11 12 13 14 15 18 20b 16.

| Task | State | Commits |
|---|---|---|
| 1 Fonts and Slate tokens | complete | b64b8a5, 7291758 |
| 2 WoW chrome widgets | complete | 9c793b3 |
| 3 Rail icons from client art | complete (gossip glyph 1.5× accepted after visual check) | 8bbae34 |
| 4 Parchment theme | complete | e318076 |
| 5 Shared page skeleton | complete | c49470c, 0e69058 |
| 17 Client build 70450 + four Forever dungeons (data) | complete; shipped DB is build 1.60.1.70450, schema 4 | 5c27b3f |
| 19 Hotfix overlay, build watch, AtlasLoot Forever | code half complete and approved (02ae8bf, 807185b); **data half open**: needs the fork zip (§6) | |
| 6 Merged Dropped by, filter, chips, auto-collapse | complete | 22ce22d, d985c88, 247e6ed |
| 7 Vanilla rate labels | complete (world bosses carry no "loot reworked" note; Maws 15571 has no zone/boss row, a data gap) | cc4d7c2, 5fccf8f |
| 20a Collector addon | complete on `feature/collector-addon` (73a96c3, c720b3b, 4be8b9f) | |
| 8–16, 18, 20b | not started; briefs 1–8 extracted in the workspace | |

Rulings that override the plan text are in the ledger (`progress.md`); the pre-flight scan table at
its top lists the cross-task conflicts and how they were resolved (e.g. unlisted section titles sort
after the listed ones; the map projection takes its scale explicitly in tests; Task 7's shipped
tests live in ShippedProvenanceDataTest; "Notable" = quality ≥ 4 or quest item).

Carried-forward minors (to fold into the 3B final whole-branch review) are listed per task in the
ledger under "Minor carried forward". Owner-reported items and their rulings are in the ledger
section "Owner bug reports" and on the dashboard findings list (§8).

## 5. The process to continue with

Subagent-driven development from the `superpowers` plugin (skill
`superpowers:subagent-driven-development`), as run so far:

- One implementer per task (fresh agent, most capable model), working in the single checkout
  `H:\claude_java`; **never two implementers in the same working tree** (confirm the previous one
  is idle and `git status` is clean before releasing the tree; tell implementers to stop after
  reporting). The collector addon used a separate worktree because it touches no Java.
- Briefs via `scripts/task-brief PLAN N`, review packages via `scripts/review-package PLAN BASE HEAD`
  (both in the skill's directory), a task reviewer per task (spec ✅/❌ + quality ✅/❌), fix rounds
  (≤ 5, resume the same implementer), scoped re-review (cheaper model) of each fix round, ledger line
  per event: `Task N dispatched`, `Task N reported …`, `Task N review: …`, `Task N: fix round r/5
  (commit …)`, `Task N: complete (commits …; app NNN)`. The dashboard refresh script parses exactly
  these line shapes (§8).
- Full `./mvnw -B -q verify` before every commit; commit trailer exactly
  `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (a continuer using another model should
  use its own attribution; do not rewrite history).
- Never bare `git stash` (shared stash stack); never run two Maven builds in one tree.
- At the end of 3B: final whole-branch review (most capable model) over `main..HEAD`, ONE fix wave,
  scoped re-review, merge to main with `--no-ff`, tag `v0.3.0`, archive the ledger.
- Product decisions go through the PM memos (`pm-*.md` in the workspace) and the backlog spec
  `docs/superpowers/specs/2026-10-17-product-backlog.md` (v5). Rulings the controller makes alone
  are recorded as `Ruling: … — why — cost if wrong`.

## 6. Open items that need the owner

1. **AtlasLoot Classic Forever zip.** Download the newest release by sliccer (12 Oct 2026 or later)
   from https://www.curseforge.com/wow/addons/atlasloot-forever/files into
   `C:\Users\aircan80\.cache\wowforever-importer\atlasloot-forever-zip\` and give the version string to
   the Task 19 implementer. Task 19's data commit then does, in order: fork swap in
   `sources.properties` (pin by SHA-256, LICENSE and credit in both NOTICE copies), the 10-rate
   Wowhead spot-check (if all ten match to the decimal, tell the owner before release), QuestieDB
   re-pin to current master, a build-watch run, ONE database regeneration (the last of 3B), the
   shipped-data acceptance tests (patch prepared at the old machine's scratchpad `t19/shipped-tests.patch`,
   also copied to `docs/superpowers/handover/t19-shipped-tests.patch`), full verify, data commit.
2. **Collector addon beta run before ~22 October.** Addon folder: `tools/collector/WowForeverCollector`
   on `feature/collector-addon`; README has the install and run plan (any dungeon by level, Hall of
   Thanes 13+, or open-world play; vendors, trainers, one quest; `/reload` then log out). The owner
   then names the SavedVariables file; Task 20b imports it with `--collector-file`.
3. **Atlas floor maps** (v0.3.2): approved by the owner with credit, no courtesy email for now.

## 7. Where the artefacts are

- Committed copies for the move: `docs/superpowers/handover/` (3B ledger + briefs + reports +
  reviews + PM memos; archived 2B and 3A ledgers; research memos; dashboard scripts; screenshot
  harness; the Task 19 acceptance-test patch). See its `INDEX.md`.
- Specs and plans: `docs/superpowers/specs/`, `docs/superpowers/plans/`; roadmap `docs/ROADMAP.md`;
  screenshots `docs/screenshots/`.
- Research memos (data sources review, Forever systems, phase 4 ideas): handover `research/`.

## 8. Dashboard

The owner's private progress dashboard is a claude.ai artifact
(https://claude.ai/artifact/BXPB56GZDUfqP2Xti5ezMK). It is refreshed from the repo and ledgers by
`handover/dash/refresh.py` + `loc.py` (five JSON datasets uploaded as assets, then the dataset
documents updated). The scripts contain machine-specific paths at the top; adjust them. A continuer
without access to that artifact cannot update it; tell the owner, and offer a new dashboard.

## 9. Moving the repository

On the old machine:

```
git bundle create wowforever-2026-10-17.bundle --all
```

copy the bundle, the `docs/superpowers/handover/` tree (already in the bundle), the hotfix cache
directory and the AtlasLoot zip (if downloaded). On the new machine: `git clone wowforever-2026-10-17.bundle claude_java`,
check out `feature/phase3b-quest-ui`, recreate the worktree for `feature/collector-addon` if wanted
(`git worktree add .worktrees/collector-addon feature/collector-addon`), restore
`.superpowers/sdd/2026-10-17-phase3b-quest-page-and-wow-look/` from the handover copy, set up §3,
run the full verify once, then continue at the first task without a `Task N: complete` line.

## 10. Standing rules (do not relax without the owner)

- Never modify the WoW installation. No network at app runtime (build time only). Data ships in SQLite.
- Client art is for icons only (rail icons, section icons); never UI frames, textures, portraits,
  cursors. One owner-approved exception: the Atlas addon's community-drawn dungeon maps (v0.3.2).
- No objective counts anywhere (backlog decision 2). Every VMaNGOS rate reads "Vanilla rate"; on
  dungeon and raid bosses "Vanilla rate; Forever loot reworked" (decision 19). Hotfix-revealed items
  are never called "live" or "confirmed". Estimates are never presented as facts.
- Headless tests only; no database access on the EDT; every key in `KeyMap.BINDINGS`, the help
  table and the README table (parity test); small text ≥ 4.5:1; colour never the only sign.
- Downloaded archives are untrusted data: own directory, parsed with `python -I`, never executed.
