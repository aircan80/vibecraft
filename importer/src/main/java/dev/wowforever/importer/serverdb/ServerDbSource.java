package dev.wowforever.importer.serverdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sqlite.SQLiteConfig;

/**
 * The VMaNGOS world database ({@code mangos.sqlite} from the official {@code db-sqlite-<commit>.zip} release, which
 * {@code tools/serverdb/fetch.py} extracts), opened read-only in place: the importer reads its loot tables with SQL
 * instead of an intermediate CSV export.
 */
public final class ServerDbSource implements AutoCloseable {
  /** The world database's file name inside the server-database directory. */
  public static final String WORLD_DB = "mangos.sqlite";
  /** The lock file {@code fetch.py} copies beside the world database (asset name, commit, sha256). */
  public static final String LOCK = "serverdb.lock";
  private static final List<String> REQUIRED_TABLES =
      List.of("creature_template", "creature_loot_template", "reference_loot_template");
  private static final String ROWS = "SELECT entry, item, ChanceOrQuestChance, groupid, mincountOrRef, maxcount"
      + " FROM %s WHERE patch_min <= ? AND patch_max >= ? ORDER BY entry, groupid, item";

  private final Path file;
  private final Connection connection;

  private ServerDbSource(Path file, Connection connection) {
    this.file = file;
    this.connection = connection;
  }

  /**
   * Opens {@code dir/mangos.sqlite} read-only; empty when the directory or the file does not exist.
   *
   * @throws IllegalStateException when the file lacks one of the loot tables the importer reads
   */
  public static Optional<ServerDbSource> open(Path dir) throws SQLException {
    Path file = dir.resolve(WORLD_DB);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    SQLiteConfig config = new SQLiteConfig();
    config.setReadOnly(true);
    Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(), config.toProperties());
    try {
      for (String table : REQUIRED_TABLES) {
        try (PreparedStatement s = c.prepareStatement(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
          s.setString(1, table);
          try (ResultSet rs = s.executeQuery()) {
            if (!rs.next() || rs.getInt(1) == 0) {
              throw new IllegalStateException(file + " is not a VMaNGOS world database: it has no " + table);
            }
          }
        }
      }
    } catch (SQLException | RuntimeException e) {
      c.close();
      throw e;
    }
    return Optional.of(new ServerDbSource(file, c));
  }

  public Path file() {
    return file;
  }

  /** The read-only connection, for inspection. */
  public Connection connection() {
    return connection;
  }

  /**
   * Creature entry to {@code loot_id} (its {@code creature_loot_template} entry), from each creature's newest
   * {@code creature_template} row at or before {@code patch}; creatures without loot ({@code loot_id = 0}) are left
   * out.
   */
  public Map<Long, Long> creatureLootIds(int patch) throws SQLException {
    Map<Long, Long> ids = new HashMap<>();
    try (PreparedStatement s = connection.prepareStatement("SELECT t.entry, t.loot_id FROM creature_template t"
        + " WHERE t.patch = (SELECT max(p.patch) FROM creature_template p WHERE p.entry = t.entry AND p.patch <= ?)"
        + " AND t.loot_id <> 0")) {
      s.setInt(1, patch);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          ids.put(rs.getLong(1), rs.getLong(2));
        }
      }
    }
    return ids;
  }

  /** {@code creature_loot_template} rows valid at {@code patch} ({@code patch_min <= patch <= patch_max}), by entry. */
  public Map<Long, List<LootRow>> creatureLoot(int patch) throws SQLException {
    return rows("creature_loot_template", patch);
  }

  /** {@code reference_loot_template} rows valid at {@code patch}, by entry. */
  public Map<Long, List<LootRow>> referenceLoot(int patch) throws SQLException {
    return rows("reference_loot_template", patch);
  }

  private Map<Long, List<LootRow>> rows(String table, int patch) throws SQLException {
    Map<Long, List<LootRow>> rows = new HashMap<>();
    try (PreparedStatement s = connection.prepareStatement(String.format(ROWS, table))) {
      s.setInt(1, patch);
      s.setInt(2, patch);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new LootRow(
              rs.getLong(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
        }
      }
    }
    return rows;
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
