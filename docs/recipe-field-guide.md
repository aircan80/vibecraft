# Native recipe field guide

This bounded v0.6 slice adds `NativeRecipeGuideRepository.forItem(outputItemId)` and `RecipeFieldGuidePanel(snapshot, navigator)`. It extends the current recipe catalog's output-item journey with create-spell yield, exact reagent slots, native profession/rank/skill-up thresholds, navigable item pages, and collapsible source paths/lines.

The legacy `Craft` model already represents much of this, but its combined projection is not sufficient evidence for exact current Forever values. This reader uses only the explicitly pinned `forever_diff_forever_snapshot_id` with product `wow_classic_beta`. Native names absent from that snapshot stay `Item #ID`; Era never supplies missing names, ingredients, quantities, or skill ranks. It does not infer trainers, recipe availability, ownership, positive reagent consumption from zero, or loot sources. Reagent item pages provide the existing separately qualified acquisition evidence.

Each native create-item effect is retained separately, including its difficulty and effect slot. Yield uses the existing `CraftLoader` base-points/variance rule. Missing formula inputs stay Unknown. Missing reagent records differ from retained rows containing no positive item IDs. Explicit zero reagent quantities and rank zero remain zero. Native spell presence is not proof that a player can learn or use it.

## Integration seam

On an explicitly selected **Crafting guide** tab, schedule `new NativeRecipeGuideRepository(repositories.database()).forItem(itemId)` on the existing worker. Render `new RecipeFieldGuidePanel(snapshot, navigator)` on the EDT only if that navigation request is still active. Keep initial item loading unchanged. The repository does not start its own executor, mutate the database, or read during rendering. No `LoadedItem` constructor changes are necessary for lazy loading.

The output predicate uses the existing `(snapshot_id,table_name,row_id)` archive primary key only for its snapshot/table prefix; JSON output/spell fields are not indexed. On the actual bundled database an isolated SQLite effect-output lookup measured 242 ms cold and warm SQL p95 279 ms (30 runs). One Java full-guide cold lookup measured 172 ms with filesystem cache warm; this is not a universal cold-performance guarantee. The reader decodes only matching rows plus indexed native ID lookups, without materializing complete wide tables. A weak-Database-scoped 32-item cache keyed by snapshot and output ID makes repeat navigation cheap (measured warm p95 0.0065 ms). Lazy loading keeps the occasional uncached scan off the initial item-navigation path. A future importer-owned native relationship index can remove these scans after coverage and migration review.

## Verification

Isolated compilation, three focused tests, and the real shipped-data control passed: exact native creation/skills/reagents, explicit zero and unknown distinctions, Era-only exclusion, snapshot reuse, actual reagent navigation, and collapsed provenance. No Maven shared target was modified.

`ShippedRecipeGuideTest` adds a real-data control for output **251525, Savory Whimsyfin Delight**, created by spell **1252566**, with reagents **251524 ×1** and **2678 ×1**, Cooking **185**, native required rank **1** and skill-up thresholds **125/165** in Forever **1.60.1.70450**. This test should join the root's final serialized shipped-data run. These are native values, not inferred live availability.
