# Vibecraft roadmap and ideas backlog

**Every place. Every drop. Every adventure.**

A World of Warcraft companion for exploration, loot, crafting, and lore.

Owner-approved product name: **Vibecraft**. Branding is integrated for verification;
the existing running artifact predates the rename. Game names and historical evidence
remain unchanged: the installed gameplay catalog is still WoW Forever.

## Current high-level direction (2026-10-17)

The original release train remains in the [product backlog](superpowers/specs/2026-10-17-product-backlog.md).
Current scope and corrected acceptance are reconciled in the [product/project review](roadmap-product-reconciliation-2026-10-17.md).
Later owner requests bring selected UI, city and rendering work forward; they do not
mean every feature in a later release is complete.

| Stage | Focus | Current position |
| --- | --- | --- |
| Foundation through v0.4.1 | Search, quests, maps, provenance and Forever systems/content | Development verified; not released; genuine collector capture acceptance remains external |
| Explorer Journal | Mockup fidelity, persistent list/detail, compact navigation and fast loads | Journal-only startup running; Original interface and chooser retired; further polish remains |
| Cities | Native city guides, districts and source-backed soundtracks | Six-city baseline running; recorded vendor/giver directory running; geography/service coverage partial |
| NPC portraits | Static 2D images and Java PNG provider/cache | Portrait-only update in progress; current running artifact retains older preview behavior; 3D models/viewers/rotation out of scope |
| v0.5 | Loot research and preparation | Filters, source-reported shared lists and recorded quest preparation running; full release acceptance remains |
| v0.6 | Crafting and economy | Native crafting guide and native teaching-item navigation running; recorded object geography ready in isolation, not integrated; profession browser, graph, source resolver, trainers, economics, gathering and disenchanting remain |
| v0.7 | Lore and exploration | City baseline running; reward trails and quest-text search running; full gazetteer, storylines, pets, rares and fishing remain |
| v0.8 | Glance and compare | Pinned-reference slice running; item compare, wishlist/export, compact pinned tabs and two-build changes remain |
| Multiple games | Edition-scoped catalogs and selector | Foundation and picker running; only Forever installed; Classic, Retail and Anniversary/TBC data not imported |

The owner has superseded the earlier requirement to retain both interfaces: Explorer
Journal is the sole interface in the current launched artifact. The Original GUI and
startup chooser are retired; starting the app with no arguments opens Journal.

“Integrated, in verification” means source implementation exists, not that the new artifact
is running or that the whole release is accepted. The dashboard records the exact running
artifact and completed evidence independently. The owner uses it to monitor status: reconcile
it after every meaningful batch, launch, acceptance result or blocker change. Product management
owns scope and acceptance; project management verifies actual implementation and evidence.

The current launched research batch passed full verification (1,984 Java tests, six skipped,
zero failures/errors) and six native suites: 153 assertions and 64 captures. Exact artifact identity and timestamps
remain in the dashboard evidence; this does not close partial later releases.

The strict Forever boundary supersedes the original v0.5 Felheart Belt must-show example:
class/slot/resistance filters use recorded Forever facts. Missing fields remain unknown and
are counted separately. Archived Era values are available only through explicit comparison;
they cannot satisfy Forever filters. Molten Core Warlock/Waist currently yields 0 shown,
209 excluded and 15 unknown, which is a source-coverage result, not a reason to borrow Era data.

Owner-authorized native previews and city music may download verified assets on demand
and then reuse an offline cache. The research database remains local and self-contained.
This extends the older backlog's runtime-network principle for these optional asset
features; it does not permit cross-build or Era fallback. Missing appearance mappings,
unsupported materials and audio failures must remain visible, without blocking navigation.


## Native WoW interface sounds

**Running, optional.** Native Forever-build sound-kit feedback covers key interface
open/close, confirmation and tab actions, with asynchronous loading/playback and
separate persistent mute/volume controls. City music remains independent; typing
does not trigger sounds. All five packaged clips decoded correctly with nonzero
audio, and output-device playback was exercised at 22% volume. This is not human
listening acceptance.

## NPC portraits: accepted scope

**Static 2D portraits only.** 3D models, interactive viewers and rotation are out of
scope for now. A Java static-PNG provider/cache is integrated in source and awaits final verification;
the current launched artifact still has the older preview behavior until the next
verified build. End users must not need Python, Node or Blender. Developer-only
offline generation can remain reference tooling and does not define the runtime
product. Missing exact Forever portrait links remain explicit; Auntie Bernice (246)
is unmapped. Neither an Era substitute nor a constrained humanoid lab prototype
establishes production portrait coverage.

Acceptance: load/cache supported static portraits asynchronously in the Java app,
handle missing or failed images without blocking navigation, and verify startup
and portrait use without developer renderer toolchains. Java-only native audio
asset fetching is integrated in source; final packaged acceptance remains pending.

## Source-only preparation and asset boundaries

A source-only export/bootstrap plan is in preparation locally. Code licensing and
Blizzard art attribution/rights are separate questions: a code GPL licence does not
confer redistribution rights to game art. Do not include bulk game assets in a GitHub
source export. No GitHub push or publication is authorized or claimed by this work.

## Portable Java-only delivery and release data

The normal runnable portable release **must include the compact gameplay database**
and its Java runtime, ready to browse without database setup. The GitHub source
export remains data-free; source and end-user delivery are separate artifacts.

Current source-only measurements are 20.4 MB for the application JAR and 76 MiB
archive / 184 MiB extracted for Linux code plus runtime. They exclude the gameplay
database and are **not complete end-user release sizes**. Total Windows/macOS sizes
are unknown; earlier 75–120 MB estimates excluded data and must not be presented as
release totals. Empty-PATH self-test and native GUI smoke validate the packaging
slice, not unfinished runtime or release acceptance.

Native Java audio fetching and static portraits are integrated in source, with final
packaged verification pending. External database
selection is an optional advanced/source-build flow, not required normal release setup.
Complete release sizing depends on database compaction and asset review. End users
must not need developer toolchains or separately installed Java for portable delivery.

The existing Git history includes game assets, so it cannot be published unchanged.
A clean source export remains under review; no GitHub push is authorized or claimed.
A separate derived release database removes raw development archives while
preserving selected facts, provenance and conflicts. Measured reduction and current
validation are recorded below; complete packaged release acceptance remains pending.

## Current portable preview candidate

Proposed version: **0.4.1-preview.1**, not an RC. Static Java portraits, Java-only
asset/audio support, complete packaging, preview notes and manifest tooling are
integrated in source. Fresh full verification is blocked by a test restoring UI
defaults incorrectly and causing cascading UI errors; its correction and a rebuilt
candidate are pending. The older verified artifact remains the running baseline.
Do not claim the latest source passed or was launched.

The derived database measures 1,050,083,328 bytes versus 2,283,749,376 bytes, a
54.02% reduction, with approximately 58.8 MB xz compression. All 81 retained table
digests, foreign-key/integrity checks, icons, maps and conflict data are preserved;
the shared development database is unchanged. These are database measurements,
not final full-release size. An obsolete preliminary 156 MiB gzip bundle passed a
relocated self-test; it is not the final candidate. Final rebuilt packaged acceptance
and complete release sizing remain pending.

## Accepted future exploration: map zoom and pan

**Planned, owner-approved; not implemented.** Add city and zone map zoom/pan as a
v0.7 exploration slice after current portrait and loot/preparation work. Support
mouse-wheel zoom, pinch where the platform supports it, drag pan, Reset/Fit and
keyboard equivalents. Keep markers aligned with the map while transforming,
preserve marker hit-testing and selection, and retain geographic breadcrumbs,
map hierarchy and source attribution. Compact layouts must keep controls reachable.
Zoom enlarges the existing source raster; it does not invent finer map detail.
Higher-resolution assets, new tiles and new district geography require separate
source acquisition and are outside this interaction slice.

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
