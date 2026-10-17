package dev.wowforever.app.db;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.ImportOptions;
import dev.wowforever.importer.ImportPipeline;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension (and static helper) that builds the importer fixture database once per JVM into a
 * static temporary directory, using {@link ImportPipeline} on {@link Fixtures#roots()} and
 * {@link Fixtures#clientDataRoot()} from the importer test-jar. {@link #repositories()} is a shared read-only {@link Repositories} over it.
 */
public final class FixtureDb implements BeforeAllCallback {
  /** The client-data fixture covers exactly 75 % (12 of 16) of its named items, so the options ask for no more. */
  private static final int FIXTURE_COVERAGE_MIN_PCT = 75;

  @Override
  public void beforeAll(ExtensionContext context) {
    path();
  }

  /** The fixture SQLite file, built on first use. */
  public static Path path() {
    return Built.INSTANCE.db;
  }

  /** The build id the importer wrote for the fixture database (equals its {@code meta.build_id}). */
  public static String buildId() {
    return Built.INSTANCE.buildId;
  }

  /** Repositories over one shared read-only connection to the fixture database. */
  public static Repositories repositories() {
    return Shared.REPOS;
  }

  private static volatile Database openDb;

  private static final class Shared {
    static final Database DB = open();
    static final Repositories REPOS = Repositories.of(DB);

    private static Database open() {
      try {
        Database db = Database.open(path());
        openDb = db;
        return db;
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static final class Built {
    static final Built INSTANCE = build();
    final Path db;
    final String buildId;

    private Built(Path db, String buildId) {
      this.db = db;
      this.buildId = buildId;
    }

    private static Built build() {
      try {
        Path dir = Files.createTempDirectory("wowforever-app-fixture-db");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          Database db = openDb;
          if (db != null) {
            db.close();
          }
          deleteQuietly(dir);
        }));
        Path out = dir.resolve("wowforever.sqlite");
        Path buildIdOut = dir.resolve("build-id.txt");
        ImportOptions opts = new ImportOptions(out, dir.resolve("cache"),
            Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), false, buildIdOut,
            ClientDataOptions.of(Fixtures.clientDataRoot()), FIXTURE_COVERAGE_MIN_PCT);
        ImportPipeline.run(opts, new PrintStream(OutputStream.nullOutputStream()));
        seedSyntheticForeverUiItems(out);
        return new Built(out, Files.readString(buildIdOut, StandardCharsets.UTF_8).trim());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /**
   * Synthetic known-Forever cards for the shared UI/search/loot exercises. The importer fixture intentionally
   * supplies these five rows only through Era to test fallback ingestion; that is not the behavior these
   * app interaction tests exercise. Keep this explicit allowlist local to the test database. In particular,
   * Felheart Belt 16806 remains an Era-only control, and boundary tests set their own provenance explicitly.
   * No production archive or importer source evidence is changed by this test setup.
   */
  private static void seedSyntheticForeverUiItems(Path file) throws SQLException {
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        var statement = connection.createStatement()) {
      statement.executeUpdate("UPDATE item SET client_source='forever' WHERE id IN (14145,14148,14149,5191,16800)");
    }
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
