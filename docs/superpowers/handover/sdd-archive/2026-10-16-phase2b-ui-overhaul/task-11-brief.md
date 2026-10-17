### Task 11: Tooltip card, icons and quality colours everywhere

**Files:** `ui/widgets/ItemCard.java` (custom-painted navy card, 1 px border, 4 px radius, 56 px icon beside the name, `JLabel` stack of `item_tooltip_line` rows coloured by the `colour` column; `slot` lines split on `\t` into left/right), `ui/widgets/IconCache.java` (`icon(id, size)` → `ImageIcon` from the `icon` table BLOB with bicubic downscale, LRU 2,000; fallback id 0), item names in every table/list/dropdown rendered with `QualityColors.text`, 18 px icons in rows and 28 px in the master list; `Columns`/`MasterList` renderers; `ItemPage` card at the top; `db/AssetRepository` (from 2A Task 10) reused; a muted "Stats from Classic Era data" line is already a tooltip line (`source_note`) — just render it.
**Tests:** `ItemCardTest` (lines in order with colours; slot split), `IconCacheTest` (fallback, size cache), renderer tests, `ItemPageTest.cardShown`.
- [ ] Commit `feat(ui): tooltip card, icons and quality colours`.

