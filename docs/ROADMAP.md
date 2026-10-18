# Vibecraft roadmap and ideas backlog

**Every place. Every drop. Every adventure.**

A World of Warcraft companion for exploration, loot, crafting, and lore.

## Current status

The current desktop development launch is the accepted search-cleanup artifact
(`96b554ac…`, service `vibecraft-search-ui`), using reviewed existing data. It passed
79 focused tests and 55 native assertions across four captures. The rounded-header
artifact (`4cc6…`, 111 assertions/four captures) is an earlier accepted milestone,
not the currently active artifact. Original portable preview 2 bundles remain
unchanged: approximately 156 MiB Linux and 147 MiB Windows. Windows application
launch testing has not run; macOS portable delivery is not built.

Source checkpoint `3fdb808` is pushed to
[GitHub](https://github.com/aircan80/vibecraft). Its [CI run](https://github.com/aircan80/vibecraft/actions/runs/39093624739)
passed source verification and source-only packaging/audit on **Linux, macOS and
Windows**. The preceding combined clean-source run reported 1,959 tests, 55 explicit
skips and zero failures. Later local setup work is not claimed pushed, accepted or
running. Source CI success does not establish native Windows application acceptance.
No gameplay database, portable release assets or tags were uploaded with the source.

## Active setup and import work

[#1 Sources & Updates setup](https://github.com/aircan80/vibecraft/issues/1) is the
current priority. Approved setup/provider mockups passed 28 checks. The new
database-independent first-run Java UI, persistence/metadata workers, immutable
plans/storage safeguards ([#6](https://github.com/aircan80/vibecraft/issues/6)) and
exact-build reader/transport work ([#7](https://github.com/aircan80/vibecraft/issues/7))
are in isolated implementation/verification. They are not the currently running app.

Bounded read-only local WoW detection is integrated (10 focused tests), as is
optional AtlasLoot support (29 focused tests). These completed slices do not create
a full importer. **Zero-database bootstrap is not ready**: production importer
integration, staged validation/activation and native CASC/DB2 extraction remain open.
Existing-data launches do not demonstrate empty-installation database creation.

Setup must support **multiple game versions** in shared edition-scoped catalogs,
with an active-game switcher ([#9](https://github.com/aircan80/vibecraft/issues/9)).
Only Forever is currently installed. Each optional provider explains **Adds,
Needs, and If omitted** coverage ([#11](https://github.com/aircan80/vibecraft/issues/11)).
Wago exports are explicit; VMaNGOS is comparison evidence. Local WoW folders are
optional and read-only. Any selected missing-asset CDN retrieval writes only to
app data, never into the WoW folder.

Source-icon and breadcrumb-dropdown mockups are complete; Java implementation
remains open under [#2](https://github.com/aircan80/vibecraft/issues/2) and
[#3](https://github.com/aircan80/vibecraft/issues/3). The Zephras Isle visibility
correction is already included in preview 2; nested scrolling is removed and More
Destinations appears above map art.

## Product scope and remaining releases

The original release train remains in the [product backlog](superpowers/specs/2026-10-17-product-backlog.md).
The [product/project review](roadmap-product-reconciliation-2026-10-17.md) reconciles
scope. Pulled-forward slices never imply completion of an entire later release.

| Stage | Current position | Remaining scope |
| --- | --- | --- |
| Foundation through v0.4.1 | Development verified; not a final release | Genuine collector acceptance and documented coverage limits |
| Explorer Journal | Sole interface; Original GUI/chooser retired; palette/header/search/map inspector and legend accepted | Setup implementation and further usability work |
| Cities | Six guides/music, recorded vendor/giver directory running | Geography and service coverage remain partial |
| NPC portraits | Java static PNG provider/cache running | Exact Forever portrait coverage; missing links remain explicit |
| v0.5 loot/preparation | Filters, source-reported shared lists and recorded quest preparation running | Complete release-wide acceptance and broader evidence coverage |
| v0.6 crafting/economy | Crafting guides and native teaching-item navigation running | Profession browser, recursive graph, source resolver, trainers, economics, gathering and disenchanting; object geography remains isolated |
| v0.7 lore/exploration | Cities, reward trails and attributed quest-text search running | Gazetteer, storylines, contextual mentions, pets, rares and fishing |
| v0.8 glance/compare | Scoped pinned references running | Item comparison, wishlist/export, compact pinned tabs and verified two-build changes |
| Multiple games | Context foundation/picker; only Forever installed | Approved multi-version setup/import and other catalogs |

Forever gameplay facts never silently borrow Era values. Explicit Era comparisons
remain available. Missing class/slot/resistance fields are counted as unknown;
the original Felheart Belt must-show criterion is superseded by source-correct
filter results. Molten Core Warlock/Waist currently partitions 0 shown, 209 excluded
and 15 unknown. Unrecorded keys/attunements remain unknown; a recorded area quest
is not proof of an access requirement.

## Portraits, audio and delivery boundaries

Portraits are **static 2D only**. 3D models, interactive viewers and rotation are out
of scope. End users need no Python, Node or Blender. Developer-only generation is
reference tooling. Auntie Bernice (246) remains unmapped; a humanoid lab prototype
or an Era substitute does not establish production Forever coverage.

Optional native interface audio is asynchronous, with separate mute/volume controls
from city music. Typing does not trigger sounds. Five packaged clips decoded with
nonzero audio; output-device playback was exercised. Human listening acceptance is
not claimed. Optional assets use exact-build verified retrieval/cache without
blocking navigation.

Normal portable delivery includes its Java runtime and compact gameplay database,
ready to browse. GitHub source is data-free; source and runnable release artifacts
are distinct. A code GPL licence does not confer redistribution rights to game art.
Bulk game assets must not enter the source export. External database selection is
an advanced/source-build option, not required normal-release setup.

## Historical artifact evidence

Evidence below belongs to the named earlier milestone, not current source HEAD.

- Research baseline: 1,984 tests, six skipped; 153 native assertions/64 captures.
- Preview 1: 2,011 tests, six skipped; 149 native assertions/66 captures. Linux
  archive 163,412,349 bytes (155.84 MiB), extracted 1,242,483,170 bytes, SHA-256
  `54e5bd64bf75df5d207a035dfdb36539b7d6541aec72cdec828dd20338c4f219`.
  Fresh-location/fresh-data/empty-PATH checks passed with bundled Java 21.0.12.1.
- Preview 2 GUI batch: 2,025 reported tests, six skipped; original packaged GUI
  acceptance 95 assertions/22 captures. Later rounded/search development artifacts
  have separate acceptance recorded in Current status above.
- Compact derived database: 1,050,083,328 bytes versus 2,283,749,376 bytes (54.02%
  smaller), approximately 58.8 MB xz. All 81 retained table digests, integrity/FK
  checks, icons, maps and conflict data preserved; development database unchanged.
- Historical code-only sizes (20.4 MB JAR; 76 MiB Linux runtime archive) exclude data
  and must never be presented as complete release sizes.

## Repository and continuity

Main checkout: `/home/aircan/Development/vibecraft`; task/development folders are
external. History cleanup preserved all 370 original commits and 51 references,
with external Git/data backups. Independent inspection covered 371 commits,
3,448 blobs and 376 administrative files with zero requested identity/secret
findings. Dataset binaries were removed from reachable history; Git was about
24 MiB with largest blob 1.37 MB at that inspection.

The owner monitors the published dashboard. Reconcile it after meaningful work,
acceptance, launch or blocker changes. Distinguish running artifacts, isolated
implementation, completed evidence and external gates. Product management owns
scope; project management reconciles implementation/evidence. Features,
enhancements and bugs must have issues updated while work proceeds.

- [#1 Zero-data setup](https://github.com/aircan80/vibecraft/issues/1): active.
- [#2 Source-icon Java](https://github.com/aircan80/vibecraft/issues/2): pending.
- [#3 Breadcrumb Java](https://github.com/aircan80/vibecraft/issues/3): pending.
- [#4 Search-pane removal](https://github.com/aircan80/vibecraft/issues/4): closed; accepted and running.
- [#5 Windows launch](https://github.com/aircan80/vibecraft/issues/5): not run.
- [#6 Staged import jobs](https://github.com/aircan80/vibecraft/issues/6): active.
- [#7 Native data reading](https://github.com/aircan80/vibecraft/issues/7): active.
- [#8 Cross-platform source CI](https://github.com/aircan80/vibecraft/issues/8): closed; all three platforms green.
- [#9 Multi-version setup](https://github.com/aircan80/vibecraft/issues/9): accepted design, implementation incomplete.
- [#10 Separate vibeMiner](https://github.com/aircan80/vibecraft/issues/10): planning only.
- [#11 Provider coverage](https://github.com/aircan80/vibecraft/issues/11): accepted design, implementation active.

## Separate vibeMiner plan

vibeMiner is a separate project in `~/Development/vibeminer`; README and plan exist
locally. Scope is CDN-only game/build discovery and on-demand caching, without a
local WoW folder or connection to Vibecraft's database. Implementation has not
started. Vibecraft no-data setup/import stays in Vibecraft.

## Accepted future exploration: map zoom and pan

**Planned, not implemented.** City/zone wheel zoom, pinch where supported, drag pan,
Reset/Fit and keyboard equivalents must preserve marker positioning/hit-testing,
selection, breadcrumbs, geographic hierarchy and attribution, including compact
controls. Zoom enlarges source raster detail; higher-resolution asset acquisition
or invented district geography is outside this interaction slice.

## Historical ideas backlog

The following original planning notes are retained as historical input. Their old
future tense does not override the current scope/status above.

Collected 2026-10-16 after v0.1 (the first working browser). Not committed to any order yet;
the suggested sequence is at the bottom. Items marked *Done (v0.2.0)* shipped with phase 2A
(client data) and phase 2B (UI overhaul); their text is kept as it was planned.

## A. Richer item data (client datamining)

1. **Item stats, quality and a WoW-style tooltip.** *Done (v0.2.0).* Slot filters wait for D16.
   Source: client DB2 tables for the Forever build (`ItemSparse`, `Item`, `ItemEffect`,
   `Spell`/`SpellName`, `ItemSet`), via wago.tools CSV exports or a local dump. New importer stage
   and tables (`item_stats`, `item_effect`, `item_set`); tooltip panel with quality colours;
   quality colouring and slot/stat filters in item lists.
2. **Icons.** *Done (v0.2.0).* `ItemSparse`/`ItemAppearance` → icon FileDataID → BLP from CASC →
   PNG. ~6k unique icons for 21k items; ship as a resource folder (a few MB).
3. **Zone maps with spawn pins.** *Done (v0.2.0).* Client map tiles per zone; Questie spawn
   coordinates are map percentages, so pins map directly. Click a pin to open the NPC/object.
4. **3D item models.** Tier 1: pre-rendered static or 36-frame turntable images (wow.export →
   glTF → batch render). Tier 2: live viewer with LWJGL/JOGL. Armour on a body needs the
   dressing-room machinery and is out of scope until Tier 1 exists.
5. **Better drop percentages from emulator databases.** VMaNGOS loot templates (GPL-2.0) give real
   per-NPC rates for vanilla content, replacing "rate unknown" on ~153k of 164k drop rows.
6. **Smaller additions.** Crafting recipes and "crafted by" (AtlasLoot crafting module), faction
   and PvP vendors, quest chains, copyable `item:ID` links, "usable by my class/level" filter,
   two-item compare view, the 50 most-changed item names (client vs Questie, `item_alt_value`) in
   the import report's Conflicts section (planned in phase 2A, deferred by its final review).

**Build-time extraction vs. reading the user's client at runtime (decided 2026-10-16).** Do the
extraction in the build pipeline and ship the results. Reasons: modern clients store assets in
CASC (indexed, chunked archives), so reading them needs CASC + BLTE + BLP + M2 + DB2 readers,
none of which exist as mature Java libraries (the good tools are C#: TACTSharp, CASCExplorer; and
JS: wow.export); Blizzard's CDN serves the same CASC data publicly, so the pipeline needs no local
install and can use those tools or wago.tools' per-build CSV/file exports; FileDataIDs are stable
across builds but DB2 schemas change per build, so a once-per-build pipeline step is the right
place to absorb breakage; users then need no WoW install to browse. Runtime extraction buys only
legal cleanliness at the cost of the whole reader stack in Java and a first-run extraction step.
Proportion: ship stats (data) and icons (a few MB, widely redistributed); keep model renders as a
separate asset pack, which is also where an opt-in "render from my local install" would go.
Finding an install, if ever needed: Windows registry `HKLM\SOFTWARE\WOW6432Node\Blizzard
Entertainment\World of Warcraft` or Battle.net's `product.db`; macOS `/Applications/World of
Warcraft`; Linux via a folder picker (Wine).

**Caveat, stated once:** client extraction is a grey area under Blizzard's EULA and shipping icons
or models means redistributing Blizzard art. Fan sites do this and Blizzard has tolerated it; the
call is the project owner's. The cleaner alternative is an opt-in "import from my WoW install"
that reads assets from the user's own client at first run (needs a CASC reader in Java), so
nothing copyrighted ships in the repo. Do that second.

## B. Multiple sources and conflicting data (provenance)

Measured on the v0.1 data: "who drops it" never conflicts (drop lists are unioned; 162,225 rows
Questie-only, 969 AtlasLoot-only, 1,253 both). "How often" does: 242 pairs have a percentage from
both Questie's Wowhead table and AtlasLoot, 137 disagree by >1 point, 85 by >5 (mostly quest items,
where Wowhead's sample is players on the quest). AtlasLoot also omits 1,958 Questie drop rows on its
bosses by design (quest items, grey junk, reagents).

7. **Keep every competing value, not just the winner.** *Done (v0.2.0).* Replace the single
   `drop_pct`/`pct_source` with a `drop_rate_source(npc_id, item_id, source, pct)` table (keep a
   denormalised "best" column for list views). The UI shows the chosen value with its source and a
   disagreement marker when sources differ by more than a few points; hover/expand shows the
   others.
8. **Provenance on every fact.** *Done (v0.2.0).* Already on drop rows (`origin`). Extend to names
   and levels when a second source for them (client data) arrives; prefer the client's value and
   report mismatches.
9. **Conflict section in the import report.** *Done (v0.2.0).* List the top disagreements between
   sources and the rows one source has that another omits, so a data refresh shows what moved.
10. **Source filter in the UI.** *Done (v0.2.0).* Settings toggle to hide AtlasLoot-only or
    Questie-only rows for users who trust one source more.

## C. Verification

11. **Byte-level equivalence check against QuestieDB's own generated artifact.** QuestieDB ships a
    Lua 5.1 binary and `generate.lua Forever`; a one-off script could diff names and drop lists
    entity by entity against our SQLite to prove the importer matches their Source-mode view.

## D. UI follow-ups (left out of phase 2B)

Collected 2026-10-17 at the end of the phase 2B UI overhaul.

12. **HiDPI icons.** Item icons are 56 px PNGs downscaled per size, so at 150 % and above they are
    soft. Multi-resolution icons need a 2× source in the client-data pipeline first.
13. **Top 50 changed names in the import report.** Still deferred (see A6): the Conflicts section
    should list the 50 item names the client data changed most against Questie (`item_alt_value`).
14. **Kind chips at high scaling.** Beyond 150 % UI scaling the search box's kind chips overflow
    the toolbar; they should fold into the "Kind ▾" menu as the compact window does.
15. **Type-to-search zone combo.** The zone combo of the NPC and quest list pages lists hundreds of
    zones; an editable combo that filters as you type is more than a small change, so it waits.
16. **Equipment slot column and filters.** Integrated for v0.5 verification: true native slots and class/resistance filters. AtlasLoot ordering is not an equipment slot; missing Forever fields remain explicit.
17. **Pinned pages.** Scoped pinned-reference view and Ctrl+D are integrated for verification. Wishlist/export and compact pinned tabs remain separate v0.8 work.
18. **Remember column widths, sort order, last search kind and the monitor (P1.7 remainder).** Phase
    2B stored these settings but nothing read them back, so the final review removed them; add them
    again together with the code that restores them.
19. **Home launch pad: Recent, Pinned, Dungeons & Raids by level band (spec §5.1).** Home still
    shows the welcome text; the launch pad needs the pinned list of item 17.
20. **Not-found and unknown-entity states with a Search/Back action and muted styling (review
    §P1.8).** A missing entity still shows a plain "Not found" label.

21. **Visible source comparisons and conflicts on entity pages.** *Implemented in v0.4.1 development, 2026-10-17.*
    Entity pages retain and display per-source values, loot membership, selected provenance, build/version,
    archived paths and lines. Comparisons distinguish contradictory assertions from missing coverage,
    list order, quest scope and temporal changes. Large loot lists disclose evidence in batches; technical
    JSON remains available on request. Known-value selection uses comparable build and hotfix authority
    without discarding original assertions. Genuine collector capture acceptance remains separate.

## Suggested order

1. A1 + A2 + B7–B10 together (stats, quality, tooltip, icons, provenance), since they all touch
   the item page and the importer schema. *Done (v0.2.0).*
2. A3 (maps). *Done (v0.2.0).*
3. A4 Tier 1 (pre-rendered models).
4. A5 (emulator drop rates), then A6 as small follow-ups.
5. C11 whenever a data refresh makes it worth proving.
