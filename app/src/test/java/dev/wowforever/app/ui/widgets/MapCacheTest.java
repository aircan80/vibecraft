package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.ZoneMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class MapCacheTest {
  private final ZoneMap westfall = FixtureDb.repositories().zones().map(40).orElseThrow();

  /** The Westfall map under another UiMap id (the cache keys by id). */
  private ZoneMap as(long uiMapId) {
    return new ZoneMap(uiMapId, westfall.width(), westfall.height(), westfall.jpeg());
  }

  @Test
  void aMapIsDecodedOnceWhileItStaysCached() {
    var cache = new MapCache(2);
    var first = cache.decoded(westfall).orElseThrow();
    assertThat(first.getWidth()).isEqualTo(1002);
    assertThat(cache.decoded(westfall)).containsSame(first);
  }

  @Test
  void theMapUsedLeastRecentlyGoesFirst() {
    var cache = new MapCache(2);
    var one = cache.decoded(as(1)).orElseThrow();
    cache.decoded(as(2));
    cache.decoded(as(1)); // 1 is now the most recent
    cache.decoded(as(3)); // evicts 2
    assertThat(cache.contains(1)).isTrue();
    assertThat(cache.contains(2)).isFalse();
    assertThat(cache.contains(3)).isTrue();
    assertThat(cache.decoded(as(1))).containsSame(one);
  }

  @Test
  void aMapThatDoesNotDecodeIsNotCached() {
    var cache = new MapCache(2);
    assertThat(cache.decoded(new ZoneMap(9, 1, 1, new byte[] {1, 2, 3}))).isEmpty();
    assertThat(cache.contains(9)).isFalse();
  }

  @Test
  void theSharedCacheKeepsFour() {
    assertThat(MapCache.CAPACITY).isEqualTo(4);
    assertThat(MapCache.shared()).isSameAs(MapCache.shared());
  }
}
