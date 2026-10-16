package dev.wowforever.importer.clientdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A converted client-data directory as written by {@code tools/clientdata/convert.py}:
 *
 * <pre>
 *   csv/{Table}.csv        DB2 tables as CSV (csv/ItemSparse.era.csv: the optional Era fallback, ruling 17)
 *   icons/{fdid}.png       item icons, icons/0.png the fallback
 *   maps/{uiMapId}.jpg     composed zone maps
 *   maps.json              [{ui_map_id, name, width, height, tiles, overlays, missing_tiles}]
 *   build.lock             the client build the tables came from
 * </pre>
 *
 * Tables, the lock and the map index are read on first use and cached.
 */
public final class ClientDataSource {
  private final Path dir;
  private final Map<String, CsvTable> tables = new ConcurrentHashMap<>();
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

  /** @throws IllegalStateException when {@code build.lock} is missing or malformed */
  public BuildLock lock() {
    BuildLock l = lock;
    if (l == null) {
      Path p = dir.resolve("build.lock");
      if (!Files.isRegularFile(p)) {
        throw new IllegalStateException("client data directory has no build.lock: " + p);
      }
      l = BuildLock.read(p);
      lock = l;
    }
    return l;
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
