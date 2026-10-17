package dev.wowforever.app.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The {@link FixtureDb} with Westfall (zone 40) spawns added, since the importer fixture has no NPC spawning in a
 * zone with a map except Hogger (an elite): Foe Reaper 4000 (900001, rare elite) at 51.8, 49.6, Vultros (900002,
 * rare) at 61.2, 65.9, a boss (900003) at 30.0, 30.0 and Thor (900004, the Sentinel Hill flight master, normal) at
 * 56.6, 52.6, plus two points of his off the map (-1, -1 and 100.5, 50). Built once per JVM into a copy of the fixture file; {@link #repositories()} reads it.
 */
public final class SpawnFixtureDb {
  private SpawnFixtureDb() {}

  public static Repositories repositories() {
    return Shared.REPOS;
  }

  private static final class Shared {
    static final Repositories REPOS = Repositories.of(open());

    private static Database open() {
      try {
        Path dir = Files.createTempDirectory("wowforever-app-spawn-db");
        Path copy = dir.resolve("wowforever.sqlite");
        Files.copy(FixtureDb.path(), copy, StandardCopyOption.REPLACE_EXISTING);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy.toAbsolutePath());
            Statement s = c.createStatement()) {
          s.executeUpdate("""
              INSERT INTO npc(id, name, sub_name, min_level, max_level, rank, zone_id) VALUES
                (900001, 'Foe Reaper 4000', NULL, 20, 20, 2, 40),
                (900002, 'Vultros', NULL, 26, 26, 4, 40),
                (900003, 'Test Boss', NULL, 30, 30, 3, 40),
                (900004, 'Thor', 'Gryphon Master', 55, 55, 0, 40)""");
          s.executeUpdate("""
              INSERT INTO npc_spawn(npc_id, zone_id, x, y) VALUES
                (900001, 40, 51.8, 49.6), (900002, 40, 61.2, 65.9), (900003, 40, 30.0, 30.0),
                (900004, 40, 56.6, 52.6), (900004, 40, -1, -1), (900004, 40, 100.5, 50)""");
        }
        Database db = Database.open(copy);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          db.close();
          try {
            Files.deleteIfExists(copy);
            Files.deleteIfExists(dir);
          } catch (IOException ignored) {
            // best effort on JVM exit
          }
        }));
        return db;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
