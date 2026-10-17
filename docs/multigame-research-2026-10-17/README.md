# Multiple WoW editions: initial design assessment

17 October 2026. This is a three-agent architecture, data-source and interaction assessment. It proposes an extension; it does not claim the other game catalogs have been imported.

**One SQLite database is feasible, with a visible game selector and edition-scoped identities.** Selection must affect every search, relation, map, stat, loot list and cache. Numeric IDs alone cannot identify an entity across games. A selected game is independent of the Original/Explorer Journal interface choice.

## Firm data rule

Forever uses Forever-native and Forever-qualified facts. A beta gap remains unknown. Classic Era cannot silently fill it. Era comparison is an explicit, separately labelled action and cannot overwrite the Forever side. The current app's source fallback audit exposed a concrete violation; the shared browser read projection now masks Era-only card/scalar facts while retaining original bytes for explicit comparison and provenance.

## Verified current product metadata

| Display name | Blizzard product | Observed build |
| --- | --- | --- |
| WoW Forever beta | `wow_classic_beta` | 1.60.1.70450 |
| WoW Classic Era | `wow_classic_era` | 1.15.9.70003 |
| WoW Retail | `wow` | 12.1.5.70448 |
| WoW Anniversary: TBC | `wow_anniversary` | 2.5.6.70006 |

These were observed in small official Blizzard patch-service responses, archived with timestamps and hashes beside [sources.md](sources.md). Progression Classic is a separate product (`wow_classic`, observed 5.5.4.70449), not an alias for Era. Product/build metadata is time-dependent and must be refreshed at capture time.

## Recommended structure

- A game/edition registry, immutable source snapshots and selected projection per game/build/locale.
- Composite edition/projection-qualified entity and relationship keys; no cross-game joins by bare ID.
- Shared content-addressed asset bytes where genuinely identical, with independent per-game provenance and mappings.
- Game-scoped repositories, full-text search, filters, history, recents, favorites, hypothetical drafts and caches.
- An always-visible game control before global search. Switching starts a new immutable session, cancels/discards stale loads and clearly shows missing/unavailable catalogs.
- Explicit cross-game comparisons with both editions/builds visible; no implicit fallback or guessed identity equivalence.

## Delivery plan

1. Finish the strict Forever boundary and audit field/source lineage.
2. Add namespace/migration foundations and scoped repository/session tests in a disposable candidate. Preserve the current database and original interface as rollback inputs.
3. Prove one small second-game catalog, including deliberate ID collisions and isolated search/navigation.
4. Add the game selector with honest installed/available states; never display an empty catalog as an imported game.
5. Expand each game through independent acquisition manifests and coverage reports. Native files, official APIs and community sources have different strengths. Native client data does not establish complete server loot rates, spawns or vendor stock.
6. Add explicit comparisons and deduplicated assets after primary game isolation is established.

“Scrape all the data” should become reproducible, source-specific capture workflows rather than bulk mirroring one website. API credentials, unavailable fields and missing coverage must be represented honestly. No additional game was bulk downloaded or published by this assessment.

## Reports

- [Schema, migration and async session design](architecture.md)
- [Current products and acquisition/source coverage](sources.md)
- [Game selector and scoped navigation design](selector-ux.md)
- [Forever/Era boundary audit](forever-boundary-audit.md)
