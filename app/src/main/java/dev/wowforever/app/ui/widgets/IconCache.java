package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.db.AssetRepository;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item icons from the database's {@code icon} table as {@link ImageIcon}s of a given size (bicubic downscale of
 * the 56×56 PNG), kept in a least-recently-used cache of {@value #CAPACITY} entries keyed by id and size. An id
 * without an icon (or null) shows the {@linkplain AssetRepository#FALLBACK_ICON_ID question mark}; without even
 * that, a transparent {@linkplain #empty empty icon} of the size, the same instance every time.
 *
 * <p>{@link #icon} reads the database on the calling thread, so it runs on a worker: pages and lists pre-warm the
 * icons they show ({@link #warm}) while they load. Renderers on the EDT only look in the cache: {@link #cached}, or
 * {@link #forRenderer}, which shows the empty icon for a miss and fetches it on the cache's own loader thread,
 * repainting the component once it is in (a list page of thousands of items warms only its first rows). Misses load
 * newest first, so after a fast scroll the rows on screen come before those scrolled past, and at most
 * {@value #QUEUE_LIMIT} wait: older ones are dropped and asked for again when they are painted again.
 *
 * <p>The app {@linkplain #install installs} one cache over its database; until then {@link #shared} is a cache
 * without icons.
 */
public final class IconCache {
  private static final Logger LOG = LoggerFactory.getLogger(IconCache.class);
  /** How many icons (id and size pairs) the cache keeps. */
  public static final int CAPACITY = 2_000;
  /** The icon size of table rows. */
  public static final int ROW = 18;
  /** The icon size of the master list and the search dropdown. */
  public static final int LIST = 28;
  /** The icon size of the tooltip card. */
  public static final int CARD = 56;
  /** How many rows of a long list are warmed while it loads; the rest load when they are first painted. */
  public static final int WARM_ROWS = 200;
  /** Most renderer misses waiting for the loader; the oldest beyond are dropped. */
  public static final int QUEUE_LIMIT = 256;

  private static final Map<Integer, ImageIcon> EMPTY = new ConcurrentHashMap<>();
  private static volatile IconCache shared = new IconCache(id -> Optional.empty(), CAPACITY, null);

  private record Key(long id, int size) {}

  private final Function<Long, Optional<byte[]>> source;
  private final Executor loader;
  private final Map<Key, ImageIcon> icons;
  /** The keys the loader is fetching or will fetch, each with the components to repaint once it is in. */
  private final Map<Key, Set<Component>> loading = new HashMap<>();
  /** The keys waiting for the loader, newest first (guarded by {@link #loading}). */
  private final Deque<Key> waiting = new ArrayDeque<>();

  /** The icons of {@code assets}, misses of renderers fetched on a daemon thread of their own. */
  public IconCache(AssetRepository assets) {
    this(assets::icon, CAPACITY, Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "icon-loader");
      t.setDaemon(true);
      t.setPriority(Thread.NORM_PRIORITY - 1);
      return t;
    }));
  }

  /**
   * Icons read from {@code source} (the PNG of an id, empty when there is none), at most {@code capacity} kept;
   * {@code loader} fetches the misses of {@link #forRenderer} (null: they stay empty).
   */
  public IconCache(Function<Long, Optional<byte[]>> source, int capacity, Executor loader) {
    this.source = Objects.requireNonNull(source, "source");
    this.loader = loader;
    this.icons = new LinkedHashMap<>(64, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Key, ImageIcon> eldest) {
        return size() > capacity;
      }
    };
  }

  /** The cache the app's renderers and pages use. */
  public static IconCache shared() {
    return shared;
  }

  /** Makes {@code cache} the {@linkplain #shared shared} one; returns the one it replaces. */
  public static IconCache install(IconCache cache) {
    IconCache before = shared;
    shared = Objects.requireNonNull(cache, "cache");
    return before;
  }

  /** The transparent icon of {@code size}: always the same instance. */
  public static ImageIcon empty(int size) {
    return EMPTY.computeIfAbsent(size, s -> new ImageIcon(new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)));
  }

  /**
   * Icon {@code id} at {@code size}, from the cache or read and scaled now (on the calling thread: not the EDT);
   * the question mark for a null or missing id, the {@linkplain #empty empty icon} without that either.
   */
  public ImageIcon icon(Long id, int size) {
    Key key = key(id, size);
    Optional<ImageIcon> hit = cached(key);
    if (hit.isPresent()) {
      return hit.get();
    }
    ImageIcon icon = read(key.id(), size)
        .or(() -> key.id() == AssetRepository.FALLBACK_ICON_ID ? Optional.empty()
            : Optional.of(icon(AssetRepository.FALLBACK_ICON_ID, size)))
        .orElse(empty(size));
    synchronized (icons) {
      icons.put(key, icon);
    }
    return icon;
  }

  /** Icon {@code id} at {@code size} if it is in the cache; never reads the database. */
  public Optional<ImageIcon> cached(Long id, int size) {
    return cached(key(id, size));
  }

  /** Loads every icon of {@code ids} at {@code size} into the cache (on the calling thread: a worker). */
  public void warm(Collection<Long> ids, int size) {
    for (Long id : ids) {
      icon(id, size);
    }
  }

  /**
   * For a renderer on the EDT: icon {@code id} at {@code size} if it is cached, else the {@linkplain #empty empty
   * icon} while the loader fetches it (once, however many components ask) and then repaints {@code repaint} and
   * every other component that asked for it meanwhile.
   */
  public ImageIcon forRenderer(Long id, int size, Component repaint) {
    Key key = key(id, size);
    Optional<ImageIcon> hit = cached(key);
    if (hit.isPresent()) {
      return hit.get();
    }
    if (loader != null) {
      boolean first;
      synchronized (loading) {
        Set<Component> asking = loading.get(key);
        first = asking == null;
        if (first) {
          asking = Collections.newSetFromMap(new IdentityHashMap<>());
          loading.put(key, asking);
          waiting.addFirst(key);
          while (waiting.size() > QUEUE_LIMIT) {
            loading.remove(waiting.removeLast()); // scrolled past: asked for again if painted again
          }
        }
        asking.add(repaint);
      }
      if (first) {
        loader.execute(this::fetchNewest);
      }
    }
    return empty(size);
  }

  /** Fetches the newest waiting key, if any is still waiting (one run per key queued; dropped keys leave none). */
  private void fetchNewest() {
    Key key;
    synchronized (loading) {
      key = waiting.pollFirst();
    }
    if (key != null) {
      fetch(key);
    }
  }

  /**
   * Loads {@code key} and repaints whoever asked for it. A failure leaves the empty icon in its place (not retried
   * on every paint); whatever happens, even an {@link Error}, the key stops loading, so it is never stuck.
   */
  private void fetch(Key key) {
    try {
      icon(key.id(), key.size());
    } catch (RuntimeException e) { // a closed or broken database: the row keeps its empty icon, not retried
      LOG.warn("Loading icon {} failed", key.id(), e);
      synchronized (icons) {
        icons.put(key, empty(key.size()));
      }
    } finally {
      Set<Component> asked;
      synchronized (loading) {
        asked = loading.remove(key);
      }
      if (asked != null) {
        SwingUtilities.invokeLater(() -> asked.forEach(Component::repaint));
      }
    }
  }

  private Optional<ImageIcon> cached(Key key) {
    synchronized (icons) {
      return Optional.ofNullable(icons.get(key));
    }
  }

  private Optional<ImageIcon> read(long id, int size) {
    return source.apply(id).flatMap(Images::decode).map(image -> new ImageIcon(scale(image, size)));
  }

  private static Key key(Long id, int size) {
    return new Key(id == null ? AssetRepository.FALLBACK_ICON_ID : id, size);
  }

  /**
   * {@code image} scaled to {@code size}×{@code size} with bicubic interpolation, halving in steps first so a
   * large reduction keeps its detail instead of skipping pixels.
   */
  public static BufferedImage scale(BufferedImage image, int size) {
    BufferedImage current = image;
    while (current.getWidth() / 2 >= size && current.getHeight() / 2 >= size) {
      current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
    }
    if (current.getWidth() == size && current.getHeight() == size
        && current.getType() == BufferedImage.TYPE_INT_ARGB) {
      return current;
    }
    return draw(current, size, size);
  }

  private static BufferedImage draw(BufferedImage image, int width, int height) {
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(image, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return out;
  }
}
