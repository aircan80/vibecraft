package dev.wowforever.importer.clientdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A converted client-data directory as written by {@code tools/clientdata/convert.py}:
 *
 * <pre>
 *   csv/{Table}.csv        DB2 tables as CSV (csv/{Table}.era.csv: the optional Era fallback tables, ruling 17:
 *                          ItemSparse.era and ItemEffect.era)
 *   icons/{fdid}.png       item icons, icons/0.png the fallback
 *   maps/{uiMapId}.jpg     composed zone maps
 *   maps.json              [{ui_map_id, name, width, height, tiles, overlays, missing_tiles}]
 *   ui_icons/{name}.png    client UI textures (the rail icons) at native size, ui_icons/{name}@24.png at 24 px
 *   build.lock             the client build the tables came from
 *   hotfix.json            with csv/{Table}.hotfix.csv: the hotfix overlay and the cache snapshot it came from
 *                          (Task 19; ItemSparse.hotfix, Item.hotfix, ItemEffect.hotfix)
 * </pre>
 *
 * Tables, the lock and the map index are read on first use and cached.
 */
public final class ClientDataSource {
  /** The name suffix of a fallback (Classic Era) table: {@code ItemSparse.era}, {@code ItemEffect.era}. */
  public static final String ERA_SUFFIX = ".era";
  /** The name suffix of a hotfix overlay table: {@code ItemSparse.hotfix}, {@code Item.hotfix}. */
  public static final String HOTFIX_SUFFIX = ".hotfix";
  private final Path dir;
  private final Map<String, CsvTable> tables = new ConcurrentHashMap<>();
  private final Map<String, Map<Long, CsvTable.Row>> idIndexes = new ConcurrentHashMap<>();
  private volatile BuildLock lock;
  private volatile List<MapInfo> maps;
  private volatile Optional<HotfixInfo> hotfix;

  /** One entry of {@code maps.json}. */
  public record MapInfo(long uiMapId, String name, int width, int height, int tiles, int overlays, int missingTiles) {}

  public ClientDataSource(Path outDir) {
    this.dir = Objects.requireNonNull(outDir, "outDir");
  }

  public Path dir() {
    return dir;
  }

  /**
   * The build lock, read once.
   *
   * @throws IllegalStateException when {@code build.lock} is missing or malformed, or when a
   *     {@code csv/{Table}.era.csv} exists but the lock does not record the fallback it came from
   *     ({@code fallback_product}, {@code fallback_build} and {@code tables["{Table}.era"]}): the file is then left
   *     over from an earlier fetch and its build is unknown
   */
  public BuildLock lock() {
    BuildLock l = lock;
    if (l == null) {
      Path p = dir.resolve("build.lock");
      if (!Files.isRegularFile(p)) {
        throw new IllegalStateException("client data directory has no build.lock: " + p);
      }
      l = BuildLock.read(p);
      checkFallback(l, p);
      hotfix = checkHotfix(l, p);
      lock = l;
    }
    return l;
  }

  /**
   * The hotfix overlay's {@code hotfix.json}, when the directory has one; read and checked with the lock
   * ({@link #lock}).
   */
  public Optional<HotfixInfo> hotfix() {
    if (!Files.isRegularFile(dir.resolve(HotfixInfo.FILE)) && tablesEndingWith(HOTFIX_SUFFIX).isEmpty()) {
      return Optional.empty(); // no overlay: no lock needed (loader tests run without one)
    }
    lock();
    return hotfix;
  }

  /**
   * The message of an import whose hotfix cache is of another build than the client tables: the client moved on and
   * the build watch says what to refresh.
   */
  public static String buildMismatch(long headerBuild, String lockBuild) {
    return "hotfix cache header build " + headerBuild + " differs from build.lock build " + lockBuild
        + ": the client moved on. Run tools/build_watch.py, fetch the client tables of the new build"
        + " (fetch.py --build ... --hotfix-cache) and convert again before importing";
  }

  /**
   * Checks the overlay against the lock: overlay CSVs need a {@code hotfix.json}; its header build (and the lock's
   * {@code hotfix.header_build}) must be the lock build's last number; it must name the lock's build and the
   * snapshot the lock records; every overlay CSV must have the SHA-256 {@code hotfix.json} gives it.
   */
  private Optional<HotfixInfo> checkHotfix(BuildLock l, Path lockFile) {
    List<String> overlays = tablesEndingWith(HOTFIX_SUFFIX);
    Path json = dir.resolve(HotfixInfo.FILE);
    if (!Files.isRegularFile(json)) {
      if (!overlays.isEmpty()) {
        throw new IllegalStateException(overlays.getFirst() + ".csv present but " + HotfixInfo.FILE
            + " is missing; re-run convert.py, or delete the stale " + tablePath(overlays.getFirst()));
      }
      return Optional.empty();
    }
    HotfixInfo info = HotfixInfo.read(json);
    String lockBuildNumber = l.build().substring(l.build().lastIndexOf('.') + 1);
    for (Long header : new Long[] {info.headerBuild(), l.hotfixHeaderBuild()}) {
      if (header != null && !String.valueOf(header).equals(lockBuildNumber)) {
        throw new IllegalStateException(buildMismatch(header, l.build()));
      }
    }
    if (!info.build().equals(l.build())) {
      throw new IllegalStateException(HotfixInfo.FILE + " was converted for build " + info.build() + ", build.lock is "
          + l.build() + "; re-run convert.py (lock: " + lockFile + ")");
    }
    if (l.hotfixSha256() == null || !l.hotfixSha256().equals(info.sha256())) {
      throw new IllegalStateException(HotfixInfo.FILE + " was converted from snapshot " + abbrev(info.sha256())
          + " but build.lock records " + (l.hotfixSha256() == null ? "none" : abbrev(l.hotfixSha256()))
          + "; re-run convert.py (fetch.py --hotfix-cache records the snapshot) (lock: " + lockFile + ")");
    }
    for (String overlay : overlays) {
      String table = overlay.substring(0, overlay.length() - HOTFIX_SUFFIX.length());
      HotfixInfo.Table t = info.tables().get(table);
      if (t == null || !t.sha256().equals(sha256(tablePath(overlay)))) {
        throw new IllegalStateException(overlay + ".csv does not match " + HotfixInfo.FILE
            + "; re-run convert.py");
      }
    }
    return Optional.of(info);
  }

  private static String abbrev(String sha) {
    return sha.length() > 12 ? sha.substring(0, 12) : sha;
  }

  private static String sha256(Path p) {
    try {
      return java.util.HexFormat.of().formatHex(
          java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + ": " + e.getMessage(), e);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private void checkFallback(BuildLock l, Path lockFile) {
    for (String era : eraTables()) {
      if (l.fallbackProduct() == null || l.fallbackBuild() == null || !l.tableSha().containsKey(era)) {
        throw new IllegalStateException(era + ".csv present but build.lock has no fallback entry"
            + " (fallback_product, fallback_build and tables[\"" + era + "\"]); re-run fetch.py --fallback-product"
            + " wow_classic_era and convert.py, or delete the stale " + tablePath(era) + " (lock: " + lockFile + ")");
      }
    }
  }

  /** The names of the fallback tables present, {@code csv/*.era.csv} without {@code .csv}, sorted. */
  private List<String> eraTables() {
    return tablesEndingWith(ERA_SUFFIX);
  }

  /** The tables present whose name ends with {@code suffix}, {@code csv/*{suffix}.csv} without {@code .csv}. */
  private List<String> tablesEndingWith(String suffix) {
    Path csv = dir.resolve("csv");
    if (!Files.isDirectory(csv)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(csv)) {
      return files.map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(suffix + ".csv"))
          .map(n -> n.substring(0, n.length() - ".csv".length()))
          .filter(this::hasTable)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + csv + ": " + e.getMessage(), e);
    }
  }

  public boolean hasTable(String name) {
    return Files.isRegularFile(tablePath(name));
  }

  /** The table {@code csv/{name}.csv}, read once. @throws IllegalStateException naming it when missing */
  public CsvTable table(String name) {
    return tables.computeIfAbsent(name, n -> {
      Path p = tablePath(n);
      if (!Files.isRegularFile(p)) {
        throw new IllegalStateException("client table " + n + " missing: " + p);
      }
      return CsvTable.read(p);
    });
  }

  /**
   * {@code table(name).indexByLong("ID")}, unmodifiable, built once: every loader joining on a table's ids shares one
   * index (the item join runs twice, for the items and for the set pieces outside them).
   *
   * @throws IllegalStateException when the table is missing, has no ID column or repeats an id
   */
  public Map<Long, CsvTable.Row> indexById(String name) {
    return idIndexes.computeIfAbsent(name, n -> Collections.unmodifiableMap(table(n).indexByLong("ID")));
  }

  /** The table {@code csv/{name}.csv} when the file exists (read once, like {@link #table}), else empty. */
  public Optional<CsvTable> optionalTable(String name) {
    return hasTable(name) ? Optional.of(table(name)) : Optional.empty();
  }

  /** {@code icons/{fdid}.png} when present ({@code icon(0)} is the fallback icon). */
  public Optional<Path> icon(long fdid) {
    return file(dir.resolve("icons").resolve(fdid + ".png"));
  }

  /** {@code maps/{uiMapId}.jpg} when present. */
  public Optional<Path> map(long uiMapId) {
    return file(dir.resolve("maps").resolve(uiMapId + ".jpg"));
  }

  /**
   * Every {@code ui_icons/*.png} by file name without {@code .png}, sorted by name; empty when the directory is
   * absent.
   */
  public Map<String, Path> uiIcons() {
    Path d = dir.resolve("ui_icons");
    if (!Files.isDirectory(d)) {
      return Map.of();
    }
    try (Stream<Path> files = Files.list(d)) {
      Map<String, Path> out = new TreeMap<>();
      files.filter(Files::isRegularFile)
          .filter(f -> f.getFileName().toString().endsWith(".png"))
          .forEach(f -> {
            String n = f.getFileName().toString();
            out.put(n.substring(0, n.length() - ".png".length()), f);
          });
      return Collections.unmodifiableMap(out);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + d + ": " + e.getMessage(), e);
    }
  }

  /** The entries of {@code maps.json} in file order; empty when the file is absent. */
  public List<MapInfo> maps() {
    List<MapInfo> m = maps;
    if (m == null) {
      m = readMaps(dir.resolve("maps.json"));
      maps = m;
    }
    return m;
  }

  private Path tablePath(String name) {
    return dir.resolve("csv").resolve(name + ".csv");
  }

  private static Optional<Path> file(Path p) {
    return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
  }

  private static List<MapInfo> readMaps(Path p) {
    if (!Files.isRegularFile(p)) {
      return List.of();
    }
    try {
      if (!(MiniJson.parse(Files.readString(p, StandardCharsets.UTF_8)) instanceof List<?> entries)) {
        throw new IllegalArgumentException("not a JSON array");
      }
      List<MapInfo> out = new ArrayList<>();
      for (Object o : entries) {
        if (!(o instanceof Map<?, ?> e)) {
          throw new IllegalArgumentException("entry is not an object: " + o);
        }
        if (!(e.get("name") instanceof String name)) {
          throw new IllegalArgumentException("entry without a name: " + e);
        }
        out.add(new MapInfo(num(e, "ui_map_id"), name, (int) num(e, "width"), (int) num(e, "height"),
            (int) num(e, "tiles"), (int) num(e, "overlays"), (int) num(e, "missing_tiles")));
      }
      return List.copyOf(out);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + ": " + e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(p + ": " + e.getMessage(), e);
    }
  }

  private static long num(Map<?, ?> e, String field) {
    if (e.get(field) instanceof Long v) {
      return v;
    }
    throw new IllegalArgumentException("entry field " + field + " is not an integer: " + e);
  }
}
