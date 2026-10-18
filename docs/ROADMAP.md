# Vibecraft roadmap and ideas backlog

**Every place. Every drop. Every adventure.**

A World of Warcraft companion for exploration, loot, crafting, and lore.

## Current status

The current first-run preview is the accepted source-only setup artifact
(`b7e01431…`), with 128 native checks and 28 captures. The newer combined source
and map-navigation artifact (`3bddf1c0…`) passed 60 native checks/24 captures and
100 separate headless source-evidence captures. Those checks cover the named
artifacts, not a rebuilt portable release. Original preview 2 archives remain
approximately 156 MiB Linux and 147 MiB Windows. Windows GUI launch is untested;
macOS portable delivery is not built.

Source checkpoint `6c4ca78` passed [Linux, macOS and Windows CI](https://github.com/aircan80/vibecraft/actions/runs/39096565844),
including verification, source-only packaging and archive audit. Combined local
verification reported 2,015 tests, 55 explicit skips and zero failures/errors.
Subsequent source changes include acceptance tools and cache-recovery fixes, with
their own checks. Source CI does not establish native Windows GUI acceptance.
No gameplay database, portable assets or release tags were uploaded.

## Active setup and import work

[#1 Sources & Updates setup](https://github.com/aircan80/vibecraft/issues/1) is the
priority. The five-step database-independent Java wizard, source preferences,
read-only folder detection, explicit CDN consent, compatibility workers and
Settings management are implemented and accepted. **The Build action is disabled**
until the acquisition, validation and activation flow is connected.

Protected immutable plans/storage, selected-build readers and explicitly selected
Wago CSV acquisition are integrated. Staged capture/projection and read-only
candidate validation are under integration ([#6](https://github.com/aircan80/vibecraft/issues/6)).
Source snapshots and cache recovery are being hardened. Fresh real-data acceptance,
community-source acquisition, runtime compaction and safe activation remain open.
General local CASC extraction, archive/range CDN acquisition and DB2 decoding are
not complete ([#7](https://github.com/aircan80/vibecraft/issues/7)). Existing-data
launches do not establish empty-installation database creation.

Setup supports multiple selected game drafts; executable import currently targets
Forever/enUS. Shared edition-scoped catalogs and active-game switching remain
[#9](https://github.com/aircan80/vibecraft/issues/9). Providers explain **Adds,
Needs, and If omitted** ([#11](https://github.com/aircan80/vibecraft/issues/11)).
Wago consent is separate from Blizzard CDN consent. Local WoW folders are optional
and strictly read-only; all downloads and generated data go to app-owned storage.

Compact source icons/dialogs are accepted for existing item, instance and encounter
comparison panels; broader map, portrait and quest-source presentation is ongoing
([#2](https://github.com/aircan80/vibecraft/issues/2)). Recorded hierarchy breadcrumb
links and sibling dropdowns are implemented and accepted; [#3](https://github.com/aircan80/vibecraft/issues/3)
is closed. They include unplaced catalogue destinations and recorded zone/district
siblings, without inventing missing geography.

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

The planned normal release includes its Java runtime and starts without a gameplay
database; the main-app setup will acquire selected sources and build the library.
Existing preview 2 archives include a compact database and are historical delivery
artifacts. GitHub source remains data-free; source and runnable releases are distinct. A code GPL licence does not confer redistribution rights to game art.
Bulk game assets must not enter the source export. Opening an existing database remains an optional escape hatch; it does not replace
the planned first-run library-building flow.

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
