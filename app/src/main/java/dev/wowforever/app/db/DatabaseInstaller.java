package dev.wowforever.app.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the database embedded in the application jar to {@code dataDir/wowforever.sqlite} when it is missing
 * or when its {@code meta.build_id} differs from the embedded build id.
 */
public final class DatabaseInstaller {
  public static final String FILE_NAME = "wowforever.sqlite";
  static final String EMBEDDED_DB = "/data/wowforever.sqlite";
  static final String EMBEDDED_BUILD_ID = "/data/build-id.txt";
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseInstaller.class);

  private final Path dataDir;

  public DatabaseInstaller(Path dataDir) {
    this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
  }

  /** Installs the database embedded on the classpath; returns the installed file. */
  public Path install() throws IOException {
    String buildId;
    try (InputStream in = resource(EMBEDDED_BUILD_ID)) {
      buildId = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    try (InputStream db = resource(EMBEDDED_DB)) {
      return install(db, buildId);
    }
  }

  /**
   * Installs {@code embedded} (which the caller closes) unless the installed file already carries
   * {@code embeddedBuildId}. A file whose build id cannot be read counts as different and is replaced.
   */
  public Path install(InputStream embedded, String embeddedBuildId) throws IOException {
    Path target = dataDir.resolve(FILE_NAME);
    String installed = installedBuildId(target);
    if (embeddedBuildId.equals(installed)) {
      return target;
    }
    LOG.info("Extracting database build {} to {} (installed build: {})", embeddedBuildId, target, installed);
    Files.createDirectories(dataDir);
    Path tmp = Files.createTempFile(dataDir, FILE_NAME + ".", ".tmp");
    try {
      Files.copy(embedded, tmp, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
    return target;
  }

  /** The build id of the installed database, or null when it is missing or unreadable. */
  private static String installedBuildId(Path target) {
    if (!Files.isRegularFile(target)) {
      return null;
    }
    try (Database db = Database.open(target)) {
      return db.meta().get("build_id");
    } catch (SQLException | RuntimeException e) {
      LOG.info("Installed database {} is unreadable, replacing it: {}", target, e.getMessage());
      return null;
    }
  }

  private static InputStream resource(String name) {
    InputStream in = DatabaseInstaller.class.getResourceAsStream(name);
    if (in == null) {
      throw new UncheckedIOException(new IOException(name + " is not on the classpath"));
    }
    return in;
  }
}
