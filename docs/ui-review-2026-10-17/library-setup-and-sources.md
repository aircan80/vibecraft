# Vibecraft library setup and source management

Design proposal for the main Java application. These interactive HTML mockups are review material, not a shipped implementation or a working importer. All filesystem scans, source checks, build stages and completion states are illustrative; no network requests or database writes occur.

## Experience

The proposed portable release includes the application and Java, with an empty data library. First launch goes straight to a useful, warm setup page rather than an empty Journal or a separate tool. The primary path is:

1. Welcome and choose one or more game versions: Forever beta is selected initially; Classic, Retail and Anniversary TBC can be selected for planning, with their import adapters clearly marked as planned. One shared database holds edition-scoped catalogues; switching editions never merges their facts.
2. Choose sources: native game data and community reference records have separate scopes. AtlasLoot and All The Things are explicit optional selections. Nothing unselected is silently loaded from a cache or used as fallback.
3. Find game files: common locations are checked automatically before asking for a folder. Browse remains available. An example multiple-installation picker demonstrates how a different edition is visible but cannot be used as Forever data. Local folders are read-only.
4. Alternatively select official Blizzard content. Vibe Miner owns exact product/region/build discovery, availability and supported extraction. A discoverable preload is not a published game; missing, encrypted and unsupported content remain distinct.
5. Review sources, edition, exact-build readiness, required dependencies, destination, download size and temporary storage before starting. Unknown sizes stay unknown until the real source manifests are inspected. Portable storage beside the application is offered alongside a user-data location.
6. Build in the background through inspect, download, import, validate and activate. A candidate database protects the existing library. Safe stop and conditional resume are separate from completion. Stage advancement is manual in the mockup so reviewers can inspect each state without simulated rates or false downloads.
7. Ready links to the Journal and permanent Sources & updates. Optional portraits/icons/audio do not block the basic reference library.

The permanent area has Sources, Game data, History & issues, and Settings. A small future Vibe Miner concept note describes the broader data explorer without fixing where it will live. Each provider shows selection, edition/build, last successful import, last checked and update state, with coverage, exclusions and conflicts available on demand. Update checking and downloading are separate. Small source icons expose readable details, omission impact, rights status and collapsed technical evidence. Routine content stays readable without large provenance blocks.

## Required implementation boundaries

The current backend is not this complete flow. Existing no-database handling uses an external database chooser. Optional Atlas import support is implemented as a backend foundation (2360d8a, 29 checks), and local discovery is implemented as a backend foundation (623f91e, 10 checks); neither means this complete setup UI is wired. Local folder/build metadata discovery is not a finished CASC/table extractor. The production entry point, background-job integration, source eligibility gates and candidate activation must be implemented and tested before calling this available.

QuestieDB is identified as a provider, not automatically certified as a Forever source. Each selected snapshot must be checked for edition, exact pin, license and import capability. A real build remains disabled until compatible input, source dependencies, writable storage and required game context are established. A community-only source combination must have an explicitly supported context; absence of native metadata cannot fabricate a Forever identity. Classic Era cannot silently supply missing Forever fields.

The browser prototype's persisted state is review-only session storage. Production resume needs durable job state with pinned inputs and source-specific support. It must not promise byte-range continuation for every download or replay unsupported additive imports. A failed or cancelled candidate never replaces the active database. Only a validated candidate can be activated; the last working library remains available during replacement.

## Review

- `mockups/setup.html`: fresh welcome; `?step=0` through `?step=6` jump to review slices.
- `mockups/sources.html`: persistent management; `?tab=vibemining`, `history` or `settings` opens each area.
- Add `theme=light` or `theme=dark`; the theme button persists within the preview session.
- Gallery links are in the Explorer mockup's upper design-study navigation.

The design uses the approved teal/charcoal and parchment palettes, rounded controls, 18px body text, large headings and compact source icons. At 420px the left navigation becomes a horizontal navigation strip and content stacks without horizontal overflow. Source dialogs use native modal keyboard handling; management tabs implement ArrowLeft/Right, Home/End and labelled tab panels. Controls have visible focus and readable labels.

Thirty Firefox WebDriver BiDi assertions passed: empty welcome, Forever default, disabled future editions, optional addons, source rights/omission details, empty-selection validation, automatic detection and no-compatible state, read-only browse example, official preload qualification, native-unselected path, unknown estimates, requirements preview, staged progress, safe stop/resume, ready state, permanent providers, honest manual checks, Vibe Miner/local checks, keyboard tabs and viewport fit on all seven setup stages plus management at 420px. Wide dark welcome/Vibe Miner, wide light review, 420px light sources/settings and 420px dark progress captures were inspected. Captures and the temporary browser harness are outside the repository. This is not a human playtest or native Java acceptance evidence.

## Multiple game versions — issue #9

The setup supports selecting Forever, Classic, Retail and Anniversary TBC together. The header switcher edits the active catalogue, preserving independent source selections, local/CDN preferences, progress-demo state and update-check status for each selected version. Review shows all selected versions and their source plans. Selection and preferences persist across navigation in the prototype session. These are edition catalogues inside one shared library/database, not per-edition database files.

Planned adapters stay visibly planned. Their preferences can be edited and saved, but their build action cannot enter an import preview; direct progress/ready routes show a pending-adapter state. Forever remains beta, and provider snapshots still require genuine edition compatibility rather than a Classic Era fallback. Game switching in these mockups is not evidence that production multi-edition ingestion or switching is implemented.

Tracking: https://github.com/aircan80/vibecraft/issues/9 (multi-version selection and switching), with setup context in issue #1.

Vibe Miner is the planned name for a broader data explorer: Blizzard data, other providers and the local database, table/record inspection, build comparison and new-item discovery. Placement remains an open product decision; it may become a standalone app sharing services. This mockup does not add it as a game-switcher workspace or settle that architecture. In-app library setup and ordinary game-data discovery remain part of Vibecraft. Internal `tab=vibemining` review routes are retained for compatibility.

Multi-version review routes: `setup.html?step=1&games=forever,classic,retail,anniversary` selects all four in the preview; add `game=classic` to inspect another edition’s saved choices. The same parameters work on `sources.html`. Twenty-four dedicated Firefox BiDi checks passed for multi-selection, default Forever, per-edition source and checked-status preservation, navigation persistence, a combined version review, blocked planned-adapter imports, pending direct routes, open Vibe Miner placement, keyboard Tab, 18px text and 420px fit. This supplements the earlier single-version flow review rather than reasserting obsolete disabled-game behavior. Future explorer tracking: https://github.com/aircan80/vibecraft/issues/10.
