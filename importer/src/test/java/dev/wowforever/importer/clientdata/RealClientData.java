package dev.wowforever.importer.clientdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The real client data on this machine, for the cache-gated oracle runs: the directory named by the system property
 * {@code wowforever.clientdata.dir}, else the newest {@code ~/.cache/wowforever-clientdata/out/<build>}, else the
 * newest fetch cache {@code ~/.cache/wowforever-clientdata/<build>} (its {@code csv/} holds the same files
 * {@code convert.py} copies). Only a directory with {@code csv/ItemSparse.csv}, {@code csv/ItemSparse.era.csv} and
 * {@code csv/RandPropPoints.csv} counts.
 */
final class RealClientData {
  private RealClientData() {}

  static Optional<Path> dir() {
    String prop = System.getProperty("wowforever.clientdata.dir");
    if (prop != null && !prop.isBlank()) {
      return Optional.of(Path.of(prop)).filter(RealClientData::usable);
    }
    Path cache = Path.of(System.getProperty("user.home"), ".cache", "wowforever-clientdata");
    return newest(cache.resolve("out")).or(() -> newest(cache));
  }

  private static Optional<Path> newest(Path parent) {
    if (!Files.isDirectory(parent)) {
      return Optional.empty();
    }
    try (Stream<Path> dirs = Files.list(parent)) {
      return dirs.filter(RealClientData::usable).max(Comparator.comparing(p -> p.getFileName().toString()));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static boolean usable(Path dir) {
    Path csv = dir.resolve("csv");
    return Files.isRegularFile(csv.resolve("ItemSparse.csv")) && Files.isRegularFile(csv.resolve("ItemSparse.era.csv"))
        && Files.isRegularFile(csv.resolve("RandPropPoints.csv"));
  }
}
