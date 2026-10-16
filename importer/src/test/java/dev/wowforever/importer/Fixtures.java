package dev.wowforever.importer;

import dev.wowforever.importer.source.SourceRoots;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Locates the curated upstream fixture trees on the test classpath. When they are plain directories
 * (tests of this module) they are used in place; when they live inside a jar (the importer test-jar on
 * another module's classpath) they are copied to a temporary directory once per JVM.
 */
public final class Fixtures {
  private Fixtures() {}

  public static Path questieDbRoot() {
    return Holder.ROOT.resolve("questiedb");
  }

  public static Path atlasLootRoot() {
    return Holder.ROOT.resolve("atlasloot");
  }

  /** A converted client-data output directory in miniature (csv/, icons/, maps/, maps.json, build.lock). */
  public static Path clientDataRoot() {
    return Holder.ROOT.resolve("clientdata");
  }

  public static SourceRoots roots() {
    return new SourceRoots(questieDbRoot(), atlasLootRoot());
  }

  private static final class Holder {
    static final Path ROOT = locate();
  }

  private static Path locate() {
    URL url = Fixtures.class.getResource("/fixtures/questiedb");
    if (url == null) {
      throw new IllegalStateException("fixtures/questiedb is not on the classpath");
    }
    try {
      URI uri = url.toURI();
      if ("file".equals(uri.getScheme())) {
        return Path.of(uri).getParent();
      }
      return copyOutOfJar(uri);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path copyOutOfJar(URI uri) throws IOException {
    FileSystem fs;
    try {
      fs = FileSystems.newFileSystem(uri, Map.of());
    } catch (FileSystemAlreadyExistsException e) {
      fs = FileSystems.getFileSystem(uri);
    }
    Path target = Files.createTempDirectory("wowforever-fixtures");
    for (String tree : List.of("questiedb", "atlasloot", "clientdata")) {
      Path source = fs.getPath("/fixtures", tree);
      try (Stream<Path> files = Files.walk(source)) {
        for (Path p : (Iterable<Path>) files::iterator) {
          Path dest = target.resolve(tree).resolve(source.relativize(p).toString());
          if (Files.isDirectory(p)) {
            Files.createDirectories(dest);
          } else {
            Files.copy(p, dest);
          }
        }
      }
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(target)));
    return target;
  }

  private static void deleteQuietly(Path dir) {
    try (Stream<Path> files = Files.walk(dir)) {
      for (Path p : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
        Files.deleteIfExists(p);
      }
    } catch (IOException ignored) {
      // best effort on JVM exit
    }
  }
}
