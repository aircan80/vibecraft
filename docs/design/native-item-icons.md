# Native item icons in normal app views

Proposed bounded slice for [#17](https://github.com/aircan80/vibecraft/issues/17), under
[#14](https://github.com/aircan80/vibecraft/issues/14). No implementation changes are
included in this design. Existing SQLite PNG icons remain the first source.

## Actual current behavior

`item.icon_id` already contains `Item.IconFileDataID`; a new encoded icon key would
force unnecessary changes through item/list/reagent models and renderers.
`ItemSparseLoader` selects `Item.hotfix` before `Item` independently of the sparse
row. Consequently `item.client_source` describes ItemSparse origin and is not safe
icon provenance. A Forever Item icon can accompany an Era sparse row, or a hotfix
Item icon can differ from the native Item icon.

`AssetRepository.icon` reads PNG bytes from SQLite. `IconCache` prewarms 200 rows on
the page-loading worker, then its existing bounded renderer queue fills additional
rows. Adding HTTP to that synchronous source would make table loading slow again.
Item detail separately calls `assets.iconOrFallback`, bypassing IconCache, so a
renderer-only change would miss the main item portrait-sized icon.

The selected native Item dataset already records product/build/locale, snapshot,
manifest SHA, source path/line and exact IconFileDataID. No additional table download
or generic native-record store is needed.

## Reference projection and repository

Add optional `native_item_icon_reference`, keyed by selected item ID, containing
positive file-data ID, product/build/locale, source snapshot, source table (`Item`),
recorded table SHA-256, source path and source line. Index its file-data ID/profile.
Populate only selected canonical item IDs whose displayed `item.icon_id` equals the
actual selected native Item claim. Never infer this from generic database metadata
or `client_source`; a conflicting hotfix icon gets no native reference in this first
slice. A same-valued native claim is still valid evidence, not a claim about which
source last wrote the canonical row.

Projection runs transactionally during native dataset projection, after the existing
snapshot/manifest checks. It stores only this narrow reference, no BLP bytes and no
additional generic JSON rows. Updates must not leave an old reference selected when
an item's icon changes; repository lookup also requires canonical icon/reference
agreement. Runtime export retains the optional table and existing source identities.

Add a worker-only `AssetRepository.nativeIconReference(fdid)` lookup. Missing optional
tables, ambiguous/conflicting profile identities, unsupported build/locale, zero or
invalid IDs return empty. Keep `icon()` as SQLite-only and preserve existing model
`iconId` values. Old databases need no migration and continue displaying their PNGs
or current placeholders.

## On-demand worker service

A separate `NativeIconService` consumes an immutable qualified reference. First slice
supports only the reviewed exact Forever 70450/enUS asset profile; no Era/build/locale
substitution. Use NativeAssetClient verification and managed CDN/Wago consent, followed
by the existing Java BlpImageDecoder. No Python, Node, Blender, server conversion or
model rendering is involved.

Use a small bounded worker queue (for example two workers, at most 64 pending FDIDs),
deduplicate by profile/locale/FDID and request only visible rows/open detail. Drop
obsolete queued requests after rapid scrolling; do not download the 200 warmed rows
or every known icon. Active-catalog replacement cancels old service work and suppresses
stale UI callbacks. Network/cache/decode must stay off the EDT.

Fetch/decode bounds should be much smaller than map bounds: proposed 4 MiB input,
1024-pixel dimension /1 Mi-pixel top mip, bounded PNG output. Missing/encrypted or
unsupported BLP remains the current placeholder, with no guessed image or unselected
provider fallback. General cold archive discovery may pause within existing bounds;
the unwired coordinator is not silently enabled by icon loading.

Cache derived PNGs only in app-owned storage, outside selected read-only WoW roots.
After original BLP content is verified, key derived data by product/build/locale,
root/content identity and decoder version; recheck cached PNG framing/dimensions/hash
and regenerate corrupt derived files from verified source bytes. Never write PNGs
back into the library DB or game installation. A local derived-cache checksum detects
corruption; it is not independent authentication of source game content.

## Minimal UI integration

Keep existing DB PNG cache hits synchronous on their current workers. Separate native
work from `warm(200)` and trigger it from visible renderer misses or explicit item
card binding. On completion replace only affected in-memory icon entries, then repaint
or update the icon label on Swing. Loading an icon must not rebuild a table/page or
run SQL on the EDT. Preserve a placeholder immediately while native work runs.

A small ItemCardPanel binding is required because its JLabel otherwise retains a
static fallback image even after a repaint; this avoids broad ItemPage/DetailPanels
rewrites. Main's existing `IconCache.install(new IconCache(assets))` can keep its
signature; installation cancels the replaced cache's native scope without Main edits.

## Proposed ownership and checks

Importer: new `foundation/NativeItemIconProjection`, the native projection hook,
optional stale-reference cleanup in ClientItemUpdateWriter, and focused tests.
App: new immutable model/reference and NativeIconService, optional AssetRepository
lookup, IconCache two-tier loading, small ItemCardPanel binding and focused tests.
BlpImageDecoder is reused read-only. No Main, POM, release workflow, packaging or
standalone viewer changes.

Tests should prove selected native Item provenance independent of sparse/hotfix
origin, legacy no-table compatibility, DB-PNG priority, visible-only scheduling,
bounded/deduplicated queue, catalog-change cancellation, no EDT SQL/network/decode,
cache corruption recovery, provider refusal/game-root safety, unsupported-format
placeholder behavior and runtime-export retention. Separate actual acceptance needs
an Item.csv-confirmed FDID, exact native fetch, Java BLP-to-PNG conversion and a normal
item/list screenshot. Empty metadata cache cost must remain explicit.
