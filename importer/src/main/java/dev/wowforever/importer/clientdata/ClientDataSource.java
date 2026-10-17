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
 *   build.lock             the client build the tables came from
 * </pre>
 *
 * Tables, the lock and the map index are read on first use and cached.
 */
public final class ClientDataSource {
  /** The name suffix of a fallback (Classic Era) table: {@code ItemSparse.era}, {@code ItemEffect.era}. */
  public static final String ERA_SUFFIX = ".era";
  private final Path dir;
  private final Map<String, CsvTable> tables = new ConcurrentHashMap<>();
  private final Map<String, Map<Long, CsvTable.Row>> idIndexes = new ConcurrentHashMap<>();
  private volatile BuildLock lock;
  private volatile List<MapInfo> maps;

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
      lock = l;
    }
    return l;
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
    Path csv = dir.resolve("csv");
    if (!Files.isDirectory(csv)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(csv)) {
      return files.map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(ERA_SUFFIX + ".csv"))
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
