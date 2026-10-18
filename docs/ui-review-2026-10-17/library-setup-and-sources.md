# Vibecraft library setup and source management

Design proposal for the main Java application. These interactive HTML mockups are review material, not a shipped implementation or a working importer. All filesystem scans, source checks, build stages and completion states are illustrative; no network requests or database writes occur.

## Experience

The proposed portable release includes the application and Java, with an empty data library. First launch goes straight to a useful, warm setup page rather than an empty Journal or a separate tool. The primary path is:

1. Welcome and choose the game: Forever beta is selected; future editions stay honestly unavailable until supported.
2. Choose sources: native game data and community reference records have separate scopes. AtlasLoot and All The Things are explicit optional selections. Nothing unselected is silently loaded from a cache or used as fallback.
3. Find game files: common locations are checked automatically before asking for a folder. Browse remains available. An example multiple-installation picker demonstrates how a different edition is visible but cannot be used as Forever data. Local folders are read-only.
4. Alternatively select official Blizzard content. Vibemining owns exact product/region/build discovery, availability and supported extraction. A discoverable preload is not a published game; missing, encrypted and unsupported content remain distinct.
5. Review sources, edition, exact-build readiness, required dependencies, destination, download size and temporary storage before starting. Unknown sizes stay unknown until the real source manifests are inspected. Portable storage beside the application is offered alongside a user-data location.
6. Build in the background through inspect, download, import, validate and activate. A candidate database protects the existing library. Safe stop and conditional resume are separate from completion. Stage advancement is manual in the mockup so reviewers can inspect each state without simulated rates or false downloads.
7. Ready links to the Journal and permanent Sources & updates. Optional portraits/icons/audio do not block the basic reference library.

The permanent area has Sources, Vibemining, History & issues, and Settings. Each provider shows selection, edition/build, last successful import, last checked and update state, with coverage, exclusions and conflicts available on demand. Update checking and downloading are separate. Small source icons expose readable details, omission impact, rights status and collapsed technical evidence. Routine content stays readable without large provenance blocks.

## Required implementation boundaries

The current backend is not this complete flow. Existing no-database handling uses an external database chooser; current importer dependencies include Questie and Atlas inputs, and optional Atlas support is a separate implementation task. Local folder/build metadata discovery is not a finished CASC/table extractor. The production entry point, background-job integration, source eligibility gates and candidate activation must be implemented and tested before calling this available.

QuestieDB is identified as a provider, not automatically certified as a Forever source. Each selected snapshot must be checked for edition, exact pin, license and import capability. A real build remains disabled until compatible input, source dependencies, writable storage and required game context are established. A community-only source combination must have an explicitly supported context; absence of native metadata cannot fabricate a Forever identity. Classic Era cannot silently supply missing Forever fields.

The browser prototype's persisted state is review-only session storage. Production resume needs durable job state with pinned inputs and source-specific support. It must not promise byte-range continuation for every download or replay unsupported additive imports. A failed or cancelled candidate never replaces the active database. Only a validated candidate can be activated; the last working library remains available during replacement.

## Review

- `mockups/setup.html`: fresh welcome; `?step=0` through `?step=6` jump to review slices.
- `mockups/sources.html`: persistent management; `?tab=vibemining`, `history` or `settings` opens each area.
- Add `theme=light` or `theme=dark`; the theme button persists within the preview session.
- Gallery links are in the Explorer mockup's upper design-study navigation.

The design uses the approved teal/charcoal and parchment palettes, rounded controls, 18px body text, large headings and compact source icons. At 420px the left navigation becomes a horizontal navigation strip and content stacks without horizontal overflow. Source dialogs use native modal keyboard handling; management tabs implement ArrowLeft/Right, Home/End and labelled tab panels. Controls have visible focus and readable labels.

Thirty Firefox WebDriver BiDi assertions passed: empty welcome, Forever default, disabled future editions, optional addons, source rights/omission details, empty-selection validation, automatic detection and no-compatible state, read-only browse example, official preload qualification, native-unselected path, unknown estimates, requirements preview, staged progress, safe stop/resume, ready state, permanent providers, honest manual checks, Vibemining/local checks, keyboard tabs and viewport fit on all seven setup stages plus management at 420px. Wide dark welcome/Vibemining, wide light review, 420px light sources/settings and 420px dark progress captures were inspected. Captures and the temporary browser harness are outside the repository. This is not a human playtest or native Java acceptance evidence.
