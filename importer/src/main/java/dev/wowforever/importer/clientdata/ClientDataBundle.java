package dev.wowforever.importer.clientdata;

import java.util.Map;

/**
 * Everything the client-data stages produce and the writer embeds, so the pipeline passes one object.
 *
 * @param clientItems item rows from {@link ItemSparseLoader}, by item id
 * @param classNames  client item class names, or null to keep the static names
 * @param values      computed stats, damage and armour, by item id
 * @param tooltips    effects, sets and tooltip lines
 * @param icons       PNG bytes by icon id (id 0 the fallback)
 * @param zoneMaps    zone to UiMap assignments and the map images
 * @param lock        the client build the data came from; null skips the {@code client_*} meta keys
 * @param uiAssets    client UI textures (PNG) by {@code ui_asset} name, see {@link UiAssetLoader}
 * @param crafts      crafting spells and their reagents, see {@link CraftLoader}
 */
public record ClientDataBundle(
    Map<Long, ClientItem> clientItems,
    ItemClassNames classNames,
    Map<Long, ItemValues> values,
    ItemTooltips tooltips,
    Map<Long, byte[]> icons,
    ZoneMaps zoneMaps,
    BuildLock lock,
    Map<String, byte[]> uiAssets,
    Crafts crafts) {

  /** No client data at all: writes what a Questie-only import writes. */
  public static final ClientDataBundle EMPTY = new ClientDataBundle(Map.of(), null, Map.of(), ItemTooltips.empty(),
      Map.of(), ZoneMaps.EMPTY, null, Map.of(), Crafts.EMPTY);

  /** A bundle without crafts. */
  public ClientDataBundle(Map<Long, ClientItem> clientItems, ItemClassNames classNames, Map<Long, ItemValues> values,
      ItemTooltips tooltips, Map<Long, byte[]> icons, ZoneMaps zoneMaps, BuildLock lock, Map<String, byte[]> uiAssets) {
    this(clientItems, classNames, values, tooltips, icons, zoneMaps, lock, uiAssets, Crafts.EMPTY);
  }

  public ClientDataBundle {
    clientItems = clientItems == null ? Map.of() : clientItems;
    values = values == null ? Map.of() : values;
    tooltips = tooltips == null ? ItemTooltips.empty() : tooltips;
    icons = icons == null ? Map.of() : icons;
    zoneMaps = zoneMaps == null ? ZoneMaps.EMPTY : zoneMaps;
    uiAssets = uiAssets == null ? Map.of() : uiAssets;
    crafts = crafts == null ? Crafts.EMPTY : crafts;
  }
}
