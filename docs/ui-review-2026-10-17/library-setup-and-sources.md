# Vibecraft library setup and source management

Design proposal for the main Java application. These interactive HTML mockups are review material, not a shipped implementation or a working importer. All filesystem scans, source checks, build stages and completion states are illustrative; no network requests or database writes occur.

## Experience

The proposed portable release includes the application and Java, with an empty data library. First launch goes straight to a useful, warm setup page rather than an empty Journal or a separate tool. The primary path is:

1. Welcome and choose one or more game versions: Forever beta is selected initially; Classic, Retail and Anniversary TBC can be selected for planning, with their import adapters clearly marked as planned. One shared database holds edition-scoped catalogues; switching editions never merges their facts.
2. Choose sources: native game data and community reference records have separate scopes. AtlasLoot and All The Things are explicit optional selections. Nothing unselected is silently loaded from a cache or used as fallback.
3. Find game files: common locations are checked automatically before asking for a folder. Browse remains available. An example multiple-installation picker demonstrates how a different edition is visible but cannot be used as Forever data. Local folders are read-only.
4. Alternatively select official Blizzard content. Vibecraft’s game-data setup checks exact product/region/build, availability and supported acquisition before importing. The separate vibeMiner project does not replace this in-app setup. A discoverable preload is not a published game; missing, encrypted and unsupported content remain distinct.
5. Review sources, edition, exact-build readiness, required dependencies, destination, download size and temporary storage before starting. Unknown sizes stay unknown until the real source manifests are inspected. Portable storage beside the application is offered alongside a user-data location.
6. Build in the background through inspect, download, import, validate and activate. A candidate database protects the existing library. Safe stop and conditional resume are separate from completion. Stage advancement is manual in the mockup so reviewers can inspect each state without simulated rates or false downloads.
7. Ready links to the Journal and permanent Sources & updates. Optional portraits/icons/audio do not block the basic reference library.

The permanent area has Sources, Game data, History & issues, and Settings. A small note identifies vibeMiner as a separate future CDN-only project; it is not a Vibecraft workspace or database integration. Each provider shows selection, edition/build, last successful import, last checked and update state, with coverage, exclusions and conflicts available on demand. Update checking and downloading are separate. Small source icons expose readable details, omission impact, rights status and collapsed technical evidence. Routine content stays readable without large provenance blocks.

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

The subsequent owner decision makes vibeMiner a separate CDN-only application for game/build discovery and on-demand caching. It does not require a local WoW installation and must not connect to Vibecraft’s database. Vibecraft retains its own in-app library source/import workflow. The mockup only notes the separate project; it does not implement or design that application. Internal `tab=vibemining` review routes remain for compatibility.

Multi-version review routes: `setup.html?step=1&games=forever,classic,retail,anniversary` selects all four in the preview; add `game=classic` to inspect another edition’s saved choices. The same parameters work on `sources.html`. Twenty-four dedicated Firefox BiDi checks passed for multi-selection, default Forever, per-edition source and checked-status preservation, navigation persistence, a combined version review, blocked planned-adapter imports, pending direct routes, open Vibe Miner placement, keyboard Tab, 18px text and 420px fit. This supplements the earlier single-version flow review rather than reasserting obsolete disabled-game behavior. Future explorer tracking: https://github.com/aircan80/vibecraft/issues/10.

## Provider and acquisition clarity — issue #1

The six named provider roles are QuestieDB, Blizzard native data, Wago.tools, AtlasLoot, All The Things and vMaNGOS. The first five are primary cards; vMaNGOS is under Additional sources, alongside the separate local collector input. Provider identity is separate from acquisition: native table exports may be delivered through Wago, while local extraction/direct CDN data acquisition are labelled planned; addon snapshots and actual capture files have their own methods. A delivery service is not silently treated as the original author of a native fact.

Collapsed Additional sources exposes two optional review choices: explicitly named vMaNGOS drop assertions, visibly limited to non-Forever comparison evidence, and actual local collector observations with recorded edition/build. Legacy assertions never populate Forever fields or rates. Collector inputs are real existing capture files, not an invented downloadable dataset. Both source choices and selected edition remain independently persistent in the interactive prototype, and the permanent source manager offers the same disclosure. All seven selections are mockup interactions, not a claim that the production setup has six wired import adapters.

## Final acquisition and capability refinement — issues #1 and #11

Every provider now shows Adds, If not selected, Needs, edition/evidence scope and acquisition method before selection. Questie is honestly described as currently required by the core importer; native data requires a supported verified game context. Atlas omissions affect its own loot lists/rates, ATT omissions affect its own additional links, and neither removes another provider’s facts. vMaNGOS is comparison-only. Collector input requires actual capture files. The manager explains intentionally unselected, failed import and no matching source records as distinct states.

Wago.tools is individually selected and initially off. It depends on selected native data and an explicit exact product/build; removing native data clears Wago consent with a visible explanation and never selects a fallback. It is a table-export delivery provider, not the author of underlying Blizzard facts. Its consent is separate from the native CDN checkbox.

The Game files step combines an optional read-only source folder with `Download missing data from Blizzard CDN into Vibecraft’s data folder.` These are complementary, not mutually exclusive origins. Detection starts before Browse. Only verified matching local product/build content is eligible. No folder plus CDN on supports a CDN-only plan; no folder plus CDN off blocks native acquisition. CDN off leaves missing content as gaps. A requested uninstalled edition/build can fetch missing available content only when supported; Retail records cannot become TBC facts. Identical verified cache blobs may be deduplicated without changing record provenance. Preferences persist per edition.

All writes—downloads, cache, temporary candidate files and the shared database—go into Vibecraft-owned storage. Review separately labels the source folder as read-only and shows destination child paths such as `Vibecraft/data/downloads/`, `cache/`, `temp/` and `library.sqlite`; these are explicit example paths, with resolved absolute paths required in production. A user-selected app data destination is separate. Production must canonicalize paths and reject any app write destination inside any selected Blizzard/WoW source tree. No game folder is ever repaired, patched or populated with downloaded files.

Validation: 28 final Firefox BiDi assertions passed for explicit providers/capability loss, Wago dependency/opt-in, vMaNGOS and collector boundaries, edition persistence, automatic detection, strict read-only copy, blocked no-folder/no-CDN acquisition, CDN-only/local-plus-CDN states, no edition fallback, separate source and destination, all app-owned paths, manager coverage distinctions, 420px fit on Sources/Game files/Review and 18px body text. These are mockup checks, not implemented production import adapters or filesystem-write enforcement.

Review: `setup.html?step=3&game=forever` shows optional read-only folder and complementary CDN checkbox. Add `review=acquisition` to jump to that checkbox. `setup.html?step=2&review=additional` opens vMaNGOS/collector choices. No downloads, scans or database changes occur.
