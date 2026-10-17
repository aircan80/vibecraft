### Task 7: Icons and zone maps into the database

**Files:**
- Create: `clientdata/IconLoader.java`, `ZoneMapLoader.java`; modify writer for `icon`, `zone_map`, `zone.ui_map_id`; `meta` keys.
- Test: `IconLoaderTest`, `ZoneMapLoaderTest` (`missingTilesAreCountedNotFatal`, area → UiMap via Questie's `areaIdToUiMapId.lua` fixture + `UiMapAssignment` fallback + parent inheritance), writer tests reading the BLOB back and decoding it with `ImageIO` (56×56; JPEG 1002×668).

**Interfaces:**
- `IconLoader.load(ClientDataSource, Collection<ClientItem>) -> Map<Long, byte[]>`: PNG bytes for every distinct `iconId` with a file, plus id 0 fallback; counts `clientdata.icons`, `clientdata.icons.missing`.
- `ZoneMapLoader.load(ClientDataSource, Path questieDbRoot, Map<Long, Zone> zones) -> ZoneMaps(Map<Long /*zoneId*/, Long /*uiMapId*/> zoneToMap, Map<Long, MapImage(int w, int h, byte[] jpeg)> maps)`: reads `support/Forever/Zones/areaIdToUiMapId.lua` (long string `ZoneDB.private.areaIdToUiMapIdOverride`, via the existing lua-table API), then `UiMapAssignment.csv` for unmapped ids, then parent inheritance; only UiMaps with a file in `maps/` are included; counters `clientdata.maps`, `clientdata.maps.missingArt`, `clientdata.zones.mapped/unmapped`.
- `meta`: `client_product`, `client_build`, `client_fetched_at`, `client_item_count`, `icon_count`, `zone_map_count`.

- [ ] Steps 1–5; commit `feat(importer): embed icons and zone maps`

---

