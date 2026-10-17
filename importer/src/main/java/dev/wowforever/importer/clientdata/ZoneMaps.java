package dev.wowforever.importer.clientdata;

import java.util.Map;

/**
 * The zone maps to embed.
 *
 * @param zoneToMap zone id to the UiMap id of its map; only zones with art are present
 * @param maps      one image per UiMap some zone refers to
 */
public record ZoneMaps(Map<Long, Long> zoneToMap, Map<Long, MapImage> maps) {
  public static final ZoneMaps EMPTY = new ZoneMaps(Map.of(), Map.of());

  /** A composed map: size in pixels and the JPEG bytes as stored. */
  public record MapImage(int w, int h, byte[] jpeg) {}

  public ZoneMaps {
    zoneToMap = Map.copyOf(zoneToMap);
    maps = Map.copyOf(maps);
  }
}
