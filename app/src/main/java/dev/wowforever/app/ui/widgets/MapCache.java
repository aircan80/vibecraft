package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.ZoneMap;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decoded zone maps by UiMap id, the {@value #CAPACITY} used last, so going back and forth between a zone and its
 * NPCs (or previewing a list) decodes each map once. Pages only read the images. Call {@link #decoded} off the EDT.
 */
public final class MapCache {
  /** How many decoded maps are kept (about 2.7 MB each). */
  public static final int CAPACITY = 4;
  private static final MapCache SHARED = new MapCache(CAPACITY);

  private final Map<Long, BufferedImage> maps;

  public MapCache(int capacity) {
    maps = new LinkedHashMap<>(capacity * 2, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
        return size() > capacity;
      }
    };
  }

  /** The cache {@code DetailPanels.load} uses. */
  public static MapCache shared() {
    return SHARED;
  }

  /** The decoded image of {@code map}, decoded once while it stays cached; empty when it does not decode. */
  public Optional<BufferedImage> decoded(ZoneMap map) {
    synchronized (maps) {
      BufferedImage cached = maps.get(map.uiMapId());
      if (cached != null) {
        return Optional.of(cached);
      }
    }
    // outside the lock: decoding takes a while; a map decoded meanwhile by another load wins, so pages share it
    return Images.decode(map.jpeg()).map(image -> {
      synchronized (maps) {
        BufferedImage first = maps.putIfAbsent(map.uiMapId(), image);
        return first != null ? first : image;
      }
    });
  }

  /** Whether the map of UiMap {@code uiMapId} is cached. */
  public boolean contains(long uiMapId) {
    synchronized (maps) {
      return maps.containsKey(uiMapId);
    }
  }
}
