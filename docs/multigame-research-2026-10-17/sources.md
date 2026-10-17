# Multi-game sources and acquisition feasibility

Research checkpoint: 2026-10-17. This is a feasibility study, not a claim that additional editions are ingested or complete. No production database was changed and no game client was downloaded.

## Verified edition identities

Tiny public Blizzard patch-service responses were fetched directly over HTTPS and archived beside this document, with SHA-256 values and capture time in `metadata-capture.json`. These are observed US-channel versions at capture time, not permanent latest-version constants.

| User-facing edition | Verified patch product | Observed version | Scope |
| --- | --- | --- | --- |
| WoW Forever (Beta) | `wow_classic_beta` | 1.60.1.70450 | Current project's beta data; keep beta channel explicit |
| WoW Classic Era | `wow_classic_era` | 1.15.9.70003 | Vanilla-style ruleset; do not silently include seasonal rules |
| WoW Retail | `wow` | 12.1.5.70448 | Separate modern systems and item context |
| Burning Crusade Anniversary | `wow_anniversary` | 2.5.6.70006 | Anniversary TBC, not historical 2.4.3 emulation |
| Classic progression (outside initial requested four) | `wow_classic` | 5.5.4.70449 | Current progression branch; never alias this to Era |

Direct primary metadata: [Forever beta](https://us.version.battle.net/wow_classic_beta/versions), [Era](https://us.version.battle.net/wow_classic_era/versions), [Retail](https://us.version.battle.net/wow/versions), [Anniversary](https://us.version.battle.net/wow_anniversary/versions), [progression Classic](https://us.version.battle.net/wow_classic/versions). Each response includes region, build/config identifiers and version. The product-to-edition Forever association is corroborated by the project's existing pinned 1.60.1 acquisition; patch metadata itself does not provide a marketing label.

Blizzard confirms [Forever](https://worldofwarcraft.blizzard.com/en-us/forever/) launches November 4, 2026; today's dataset therefore remains beta. Blizzard's [Anniversary launch announcement](https://worldofwarcraft.blizzard.com/de-de/news/24242436) confirms Burning Crusade Anniversary is live. Blizzard's [Mists launch announcement](https://worldofwarcraft.blizzard.com/en-us/news/24213954/break-through-the-mists-of-pandaria-classic-on-july-21) establishes that progression Classic is a separate expansion track.

Recommendation: interpret the user's “Classic” as **Classic Era** in the initial four-edition selector, with an explicit description. Offer progression Classic later under its expansion name. Season of Discovery and Hardcore require ruleset/realm context; neither may be merged into Era merely because a client product contains shared records. Their inclusion is outside the requested initial four. Product presence is not proof that a row belongs to every realm mode.

## Acquisition inventory

| Content | Primary acquisition path | Additional evidence needed / limitations |
| --- | --- | --- |
| Items, spells, stats, effects, recipes | Native build-pinned DB2/CSV plus separately archived hotfix records | Retail bonus IDs, item level/scaling and difficulty/context can change one item ID's presentation; preserve item variants. Recipe links do not prove vendor availability. |
| Talents and class systems | Era/TBC classic Talent tables; modern Trait tables and associated spell/cost/condition links | Do not reuse Forever's points, Legacy or formula engines for another ruleset without a capability adapter. Missing tables or login-pushed changes remain explicit unknowns. |
| NPC identity and appearance | Official creature API where supported; client Creature/CreatureDisplayInfo/CreatureModelData/texture links; edition-specific collector observations | Client appearance rows are not a complete NPC-to-display/spawn index. A matching model does not establish a creature's identity or location. |
| Quests, objectives and coordinates | Build-flavor-specific Questie data for supported Classic branches, native quest-related tables, official API resources where supported, collector observations | Questie does not promise Retail parity. Flavor correction overlays must be applied independently. Client-only data does not guarantee full server quest conditions. |
| Loot and drop rates | Edition-specific provider/addon lists, genuine collector evidence, explicitly attributed community server databases | No universal official loot/drop-rate export has been verified. Native item existence or encounter appearance does not establish a drop. Distinguish reported membership, observed counts and numeric rates with denominators. |
| Vendors and trainers | Collector observations and edition-qualified community data, plus native cost/currency definitions | Native extended-cost definitions do not establish NPC inventory. Availability can be phase, reputation or realm dependent. |
| Maps, floors, travel | Native UiMap/assignment/map texture and taxi tables, source-qualified entrance coordinates and floor annotations | Client assets and available map tables vary by build. Preserve floor IDs and coordinate transforms; do not name-match entrances. |
| Models, textures and renders | Locally installed client/CASC assets addressed by verified build/file identifiers; offline renderer | Appearance-chain availability differs from an NPC mapping. Rendering formats and encrypted/unavailable assets need explicit coverage status. Keep game-specific appearance manifests even when bytes are deduplicated. |

[WoWDBDefs](https://github.com/wowdev/WoWDBDefs) supplies community-maintained client table definitions, not the data itself. Pin its commit and the exact build/layout; its README explicitly marks uncertain fields and notes incomplete historic manifest coverage. [Wago tools](https://wago.tools/) is a useful export provider, not Blizzard. The public `https://wago.tools/api/builds` returned **HTTP 403** during this research. We did not bypass that response and should not make this one service the only ingestion path.

[Questie](https://github.com/Questie/Questie) provides source-controlled Classic quest-helper data with flavor files. Inspect and pin the exact release, flavor overlays and licensing before each import. [AtlasLootClassic](https://github.com/Hoizame/AtlasLootClassic) is a candidate loot-membership source; it is not authoritative proof of rates or current Anniversary phase coverage. Existing Forever addon source work is reusable as an importer pattern, not as evidence that every edition shares the same content.

[CMaNGOS TBC DB](https://github.com/cmangos/tbc-db) explicitly targets historical client 2.4.3, so it can only be labelled historical community fallback for Anniversary. [TrinityCore](https://github.com/TrinityCore/TrinityCore) supports particular branches/builds; even a close build is an emulation source, not Blizzard live-server truth. Preserve original build and source labels, never promote its percentages to official current rates.

## Official API scope

The [current developer portal](https://develop.battle.net/documentation/world-of-warcraft/game-data-apis) is the authority for endpoint and namespace contracts. The historical [Blizzard Classic API announcement](https://us.forums.blizzard.com/en/blizzard/t/world-of-warcraft-classic-api-endpoints/346/) confirms items, creature identity/display media, races and classes existed, but its 2019 `static-classic` namespace example is **not evidence of today's Era or Anniversary routing**.

No authenticated edition-by-edition API probe was performed here, and current Anniversary/Forever namespace mappings remain unverified. An API adapter must register only namespace/endpoint combinations that return version-consistent sample identities with authorized credentials. Do not invent an Anniversary namespace or equate launcher product codes with API namespaces. Token handling belongs in ingestion configuration, never the shipped offline database. Official API resources can enrich identities/media and supported journal/recipe data, but “all quests, all NPC spawns, all vendors, all loot percentages” is not an established API capability.

## Recommended capture phases

1. **Inventory first:** archive the five tiny product metadata responses already captured; define four edition IDs plus channel/build/locale/region and optional ruleset. Keep the current Forever selector available while other editions show honest not-installed status.
2. **One small cross-edition proof:** acquire a pinned table subset for Era, Retail and Anniversary independently: items/spells, a representative quest, NPC/display, dungeon/map and recipe where available. Record missing tables and unavailable APIs rather than creating fake empty success. Demonstrate shared numeric IDs with different names/stats remain separate, including source conflicts and build selection.
3. **Native packs:** stage each build's raw tables and assets, exact hashes, original headers, parser/definition versions, fetch URL/time and row count. Project into edition-scoped views, then validate referential integrity and counts. Reuse identical raw blobs by content hash; do not deduplicate facts solely by numeric ID.
4. **Relationships and observed evidence:** add pinned Questie/Atlas/provider packages only for verified matching flavors, then genuine collector captures with product/build/realm/mode. Preserve claims that disagree. Community-era fallback stays explicitly fallback and never overwrites current known client fields.
5. **Coverage and UI acceptance:** show coverage by category for each edition (native, observed, reported, missing, conflict). Verify selector isolation, per-edition history/search/filter caches, no cross-game links, and async cancellation. Only advertise parity for tested supported capabilities, not every Forever-specific page.
6. **Refresh:** append immutable snapshots and diff within the same edition/channel before promotion. Changed values retain old source history; failed or partial acquisition cannot replace the selected known-good pack. Cross-edition comparison is an explicit user action rather than default conflict resolution.

## Shared-database implications (recommendation)

A shared SQLite database is feasible if identity is scoped by `(edition, entity_kind, native_id)` and snapshot identity includes product, channel, build and locale. Every relationship must carry the same edition scope or an explicit qualified target. Retail items additionally need a variant/context key. Shared numeric IDs are useful comparison candidates, not sufficient global identity.

Keep source facts immutable and project the selected build per edition. Use byte-level asset deduplication for identical content while retaining edition/build-to-asset associations. Edition-specific adapters describe supported talents, classes, map systems, professions and item calculations. Search indexes, worker request tokens, caches and navigation state must all include edition identity. This is a phased architectural proposal, not a database migration performed by this research.
