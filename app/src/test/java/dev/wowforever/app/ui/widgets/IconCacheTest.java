package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.db.FixtureDb;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class IconCacheTest {
  private static final long RED = 1;
  private static final long BLUE = 2;
  private static final long MISSING = 99;

  /** A source of 56×56 PNGs: {@code ids} each a solid colour; counts every read. */
  private static final class FakeSource implements Function<Long, Optional<byte[]>> {
    final Map<Long, byte[]> pngs = new HashMap<>();
    final List<Long> reads = new ArrayList<>();

    FakeSource with(long id, Color colour) {
      pngs.put(id, png(colour));
      return this;
    }

    @Override
    public Optional<byte[]> apply(Long id) {
      reads.add(id);
      return Optional.ofNullable(pngs.get(id));
    }
  }

  private static byte[] png(Color colour) {
    BufferedImage image = new BufferedImage(56, 56, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 56; y++) {
      for (int x = 0; x < 56; x++) {
        image.setRGB(x, y, colour.getRGB());
      }
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static FakeSource withFallback() {
    return new FakeSource().with(RED, Color.RED).with(BLUE, Color.BLUE)
        .with(AssetRepository.FALLBACK_ICON_ID, Color.GRAY);
  }

  private static int centre(ImageIcon icon) {
    BufferedImage image = (BufferedImage) icon.getImage();
    return image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
  }

  @Test
  void anIconIsScaledToTheAskedSize() {
    var cache = new IconCache(withFallback(), 10, null);
    ImageIcon small = cache.icon(RED, 18);
    assertThat(small.getIconWidth()).isEqualTo(18);
    assertThat(small.getIconHeight()).isEqualTo(18);
    assertThat(centre(small)).isEqualTo(Color.RED.getRGB());
    assertThat(cache.icon(RED, 56).getIconWidth()).isEqualTo(56);
  }

  @Test
  void aSecondLookupOfTheSameSizeIsACacheHit() {
    FakeSource source = withFallback();
    var cache = new IconCache(source, 10, null);
    ImageIcon first = cache.icon(RED, 18);
    assertThat(cache.icon(RED, 18)).isSameAs(first);
    assertThat(cache.cached(RED, 18)).containsSame(first);
    assertThat(source.reads).containsExactly(RED);
    ImageIcon other = cache.icon(RED, 28); // another size is another entry, read again
    assertThat(other).isNotSameAs(first);
    assertThat(other.getIconWidth()).isEqualTo(28);
    assertThat(source.reads).containsExactly(RED, RED);
  }

  @Test
  void cachedNeverReadsTheSource() {
    FakeSource source = withFallback();
    var cache = new IconCache(source, 10, null);
    assertThat(cache.cached(RED, 18)).isEmpty();
    assertThat(source.reads).isEmpty();
  }

  @Test
  void aMissingOrNullIconFallsBackToTheQuestionMark() {
    var cache = new IconCache(withFallback(), 10, null);
    ImageIcon fallback = cache.icon(AssetRepository.FALLBACK_ICON_ID, 18);
    assertThat(cache.icon(MISSING, 18)).isSameAs(fallback);
    assertThat(cache.icon(null, 18)).isSameAs(fallback);
    assertThat(centre(fallback)).isEqualTo(Color.GRAY.getRGB());
  }

  @Test
  void withoutEvenTheQuestionMarkTheIconIsAStableEmptyOne() {
    FakeSource source = new FakeSource().with(RED, Color.RED);
    var cache = new IconCache(source, 10, null);
    ImageIcon empty = cache.icon(MISSING, 18);
    assertThat(empty).isSameAs(IconCache.empty(18));
    assertThat(cache.icon(null, 18)).isSameAs(empty);
    assertThat(cache.icon(MISSING + 1, 18)).isSameAs(empty);
    assertThat(empty.getIconWidth()).isEqualTo(18);
    assertThat(empty.getIconHeight()).isEqualTo(18);
    assertThat(((BufferedImage) empty.getImage()).getRGB(9, 9) >>> 24).as("transparent").isZero();
    int reads = source.reads.size();
    cache.icon(MISSING, 18);
    assertThat(source.reads).as("the miss is cached too").hasSize(reads);
  }

  @Test
  void theLeastRecentlyUsedIconIsEvicted() {
    FakeSource source = withFallback().with(3, Color.GREEN);
    var cache = new IconCache(source, 2, null);
    cache.icon(RED, 18);
    cache.icon(BLUE, 18);
    cache.icon(RED, 18); // RED is now the most recent
    cache.icon(3L, 18);
    assertThat(cache.cached(BLUE, 18)).as("evicted").isEmpty();
    assertThat(cache.cached(RED, 18)).isPresent();
    assertThat(cache.cached(3L, 18)).isPresent();
    assertThat(IconCache.CAPACITY).isEqualTo(2_000);
  }

  @Test
  void warmLoadsEveryIdOnce() {
    FakeSource source = withFallback();
    var cache = new IconCache(source, 10, null);
    cache.warm(java.util.Arrays.asList(RED, BLUE, RED, null), 18);
    assertThat(cache.cached(RED, 18)).isPresent();
    assertThat(cache.cached(BLUE, 18)).isPresent();
    assertThat(cache.cached(null, 18)).isPresent();
    assertThat(source.reads).containsExactlyInAnyOrder(RED, BLUE, AssetRepository.FALLBACK_ICON_ID);
  }

  @Test
  void aRendererMissShowsTheEmptyIconAndLoadsInTheBackground() throws Exception {
    FakeSource source = withFallback();
    List<Runnable> queued = new ArrayList<>();
    Executor loader = queued::add;
    var cache = new IconCache(source, 10, loader);
    AtomicInteger repaints = new AtomicInteger();
    JLabel target = new JLabel() {
      @Override
      public void repaint() {
        repaints.incrementAndGet();
      }
    };
    assertThat(cache.forRenderer(RED, 18, target)).isSameAs(IconCache.empty(18));
    assertThat(cache.forRenderer(RED, 18, target)).as("one load per missing icon").isSameAs(IconCache.empty(18));
    assertThat(queued).hasSize(1);
    assertThat(source.reads).as("nothing read on the calling thread").isEmpty();
    int before = repaints.get();
    queued.get(0).run();
    SwingUtilities.invokeAndWait(() -> {});
    assertThat(repaints.get() - before).as("repainted once the icon is in").isEqualTo(1);
    assertThat(cache.forRenderer(RED, 18, target)).isSameAs(cache.cached(RED, 18).orElseThrow());
  }

  @Test
  void aFailedBackgroundLoadIsNotRetriedOnEveryPaint() {
    List<Runnable> queued = new ArrayList<>();
    var cache = new IconCache(id -> {
      throw new IllegalStateException("database closed");
    }, 10, queued::add);
    JLabel target = new JLabel();
    cache.forRenderer(RED, 18, target);
    queued.remove(0).run();
    assertThat(cache.forRenderer(RED, 18, target)).isSameAs(IconCache.empty(18));
    assertThat(queued).as("no second load").isEmpty();
  }

  @Test
  void withoutALoaderARendererMissStaysEmpty() {
    FakeSource source = withFallback();
    var cache = new IconCache(source, 10, null);
    assertThat(cache.forRenderer(RED, 18, new JLabel())).isSameAs(IconCache.empty(18));
    assertThat(source.reads).isEmpty();
  }

  @Test
  void theDatabaseIconsLoadThroughTheAssetRepository() {
    var cache = new IconCache(FixtureDb.repositories().assets());
    assertThat(cache.icon(134124L, 18).getIconWidth()).isEqualTo(18);
    assertThat(cache.icon(134939L, 18)).as("no blob: the question mark")
        .isSameAs(cache.icon(AssetRepository.FALLBACK_ICON_ID, 18));
  }

  @Test
  void theSharedCacheIsReplaceable() {
    var mine = new IconCache(withFallback(), 10, null);
    IconCache before = IconCache.install(mine);
    try {
      assertThat(IconCache.shared()).isSameAs(mine);
    } finally {
      IconCache.install(before);
    }
    assertThat(IconCache.shared()).isSameAs(before);
  }
}
