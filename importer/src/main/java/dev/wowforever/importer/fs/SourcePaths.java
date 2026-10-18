package dev.wowforever.importer.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Rejects untrusted symlinks while recognizing the exact, verified macOS system-directory aliases. */
public final class SourcePaths {
  private SourcePaths() {}

  public static void rejectSymlinks(Path path) throws IOException {
    rejectSymlinks(path, platformAliases());
  }

  /** Normalizes trusted system aliases for comparisons; arbitrary input symlinks are still rejected. */
  public static Path normalize(Path path) throws IOException {
    return normalize(path, platformAliases());
  }

  static Path normalize(Path path, Map<Path, Path> aliases) throws IOException {
    rejectSymlinks(path, aliases);
    Path absolute = path.toAbsolutePath().normalize();
    for (var alias : aliases.entrySet()) {
      if (absolute.startsWith(alias.getKey()) && verifiedAlias(alias.getKey(), alias.getValue())) {
        return alias.getValue().resolve(alias.getKey().relativize(absolute)).normalize();
      }
    }
    return absolute;
  }

  static void rejectSymlinks(Path path, Map<Path, Path> aliases) throws IOException {
    for (Path part = path.toAbsolutePath(); part != null; part = part.getParent()) {
      if (Files.isSymbolicLink(part)) {
        Path expected = aliases.get(part);
        if (expected == null || !verifiedAlias(part, expected)) {
          throw new IOException("Symbolic link in source/database path refused: " + part);
        }
      }
    }
  }

  private static boolean verifiedAlias(Path alias, Path target) throws IOException {
    if (!Files.isSymbolicLink(alias) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) return false;
    Path actual = alias.getParent().resolve(Files.readSymbolicLink(alias)).normalize();
    // The expected directory and its ancestors must themselves be direct directories, not another alias chain.
    for (Path part = target; part != null; part = part.getParent()) {
      if (Files.isSymbolicLink(part)) return false;
    }
    return actual.equals(target);
  }

  private static Map<Path, Path> platformAliases() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if ((!os.contains("mac") && !os.contains("darwin")) || !Path.of("/").isAbsolute()) return Map.of();
    return Map.of(Path.of("/var"), Path.of("/private/var"),
        Path.of("/tmp"), Path.of("/private/tmp"), Path.of("/etc"), Path.of("/private/etc"));
  }
}
