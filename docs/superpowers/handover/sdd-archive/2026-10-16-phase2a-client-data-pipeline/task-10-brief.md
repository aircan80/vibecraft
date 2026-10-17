### Task 10: Minimal app consumers

**Files:**
- Modify: `app/.../model/ItemSummary` (+ `quality`, `iconId`), `ItemDetail` (+ `ItemCard card`: `record ItemCard(Integer quality, List<TooltipLine> lines, Long iconId, Integer inventoryType, Integer bonding, Long sellPrice)`), `DropSource`/`LootRow` (+ `boolean disagrees`, `int sourceCount`), `ZoneDetail` (+ `Optional<ZoneMap>`: `record ZoneMap(long uiMapId, int width, int height, byte[] jpeg)`); repositories (`ItemRepository.detail/list`, `SearchRepository` subtitles use quality text? no — only colour), new `AssetRepository.icon(long id) -> Optional<byte[]>`, `ZoneRepository.map(long zoneId)`; `Repositories`.
- Modify UI: `Columns.items()`/search hits: item names coloured by quality (text variants from the UI review table: 0 `#9D9D9D`, 1 `#E4E1D8`/`#1C1F24`, 2 `#3FE23A`/`#137A0E`, 3 `#4A9EFF`/`#0058B0`, 4 `#B861FF`/`#7E2BC4`, 5 `#FF9A33`/`#B35A00`, 6 `#E6CC80`/`#8A6D1E`) via a `QualityColors` class; `ItemDetailPanel`: 56 px icon left of the header (fallback icon when missing), quality-coloured name, a `TooltipLinesPanel` (plain `JLabel` stack with colours) under the header; drop/loot tables show "≠" after the rate when `disagrees`; `ZoneDetailPanel`: a `MapImagePanel` (scaled to width, aspect kept) at the top when a map exists. No pins yet.
- Test: repository tests on the fixture DB rebuilt with the client-data fixture (update `FixtureDb` to pass `--client-data-dir`), `QualityColorsTest`, `ItemDetailPanelTest.cardShowsIconQualityAndLines`, `ItemRepositoryTest.itemWithoutClientDataHasNullQuality`, `ZoneDetailPanelTest.mapShownWhenPresent`, `AppControllerTest` unchanged.

- [ ] Steps 1–5; commit `feat(app): quality colours, icons, tooltip lines, zone map images and disagreement markers`

---

