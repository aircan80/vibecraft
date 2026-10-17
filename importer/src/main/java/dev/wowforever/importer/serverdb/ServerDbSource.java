package dev.wowforever.importer.serverdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/**
 * The VMaNGOS world database ({@code mangos.sqlite} from the official {@code db-sqlite-<commit>.zip} release, which
 * {@code tools/serverdb/fetch.py} extracts), opened read-only and immutable in place: the importer reads its loot
 * tables with SQL instead of an intermediate CSV export. Like the server's loader ({@code LootStore::LoadLootTable}),
 * item rows whose item {@code forbidden_items} bans at the patch are left out, and so are the rows its validity check
 * ({@code LootStoreItem::IsValid}) rejects, each counted under one rule ({@link #validityCounts()}), first rule
 * first:
 * <ul>
 *   <li>{@value #INVALID_MINCOUNT_ZERO}: {@code mincountOrRef = 0}, neither an item nor a reference;</li>
 *   <li>{@value #INVALID_UNKNOWN_ITEM}: an item row whose item has no {@code item_template} row at the patch;</li>
 *   <li>{@value #INVALID_LOW_CHANCE}: an item row whose {@code |ChanceOrQuestChance|} is above 0 and below
 *       {@value #MIN_CHANCE} (a reference is not checked);</li>
 *   <li>{@value #INVALID_MAXCOUNT_BELOW_MIN}: an item row with {@code maxcount < mincountOrRef}.</li>
 * </ul>
 * A reference rolled {@code maxcount = 0} times is kept (it rolls once) and counted as
 * {@value #REFERENCES_MAXCOUNT_ZERO}.
 */
public final class ServerDbSource implements AutoCloseable {
  /** The world database's file name inside the server-database directory. */
  public static final String WORLD_DB = "mangos.sqlite";
  /** The lock file {@code fetch.py} copies beside the world database (asset name, commit, sha256). */
  public static final String LOCK = "serverdb.lock";
  public static final String INVALID_MINCOUNT_ZERO = "serverdb.invalid.mincountZero";
  public static final String INVALID_UNKNOWN_ITEM = "serverdb.invalid.unknownItem";
  public static final String INVALID_LOW_CHANCE = "serverdb.invalid.lowChance";
  public static final String INVALID_MAXCOUNT_BELOW_MIN = "serverdb.invalid.maxcountBelowMin";
  public static final String REFERENCES_MAXCOUNT_ZERO = "serverdb.references.maxcountZero";
  /** The lowest non-zero chance, in percent, the server keeps on an item row ({@code 0.000001f}). */
  public static final double MIN_CHANCE = 0.000001;
  private static final List<String> REQUIRED_TABLES =
      List.of("creature_template", "creature_loot_template", "reference_loot_template", "item_template");
  private static final String ROWS = "SELECT entry, item, ChanceOrQuestChance, groupid, mincountOrRef, maxcount"
      + " FROM %s WHERE patch_min <= ? AND patch_max >= ? ORDER BY entry, groupid, item";

  private final Path file;
  private final Connection connection;
  private int forbiddenRows;
  private final Map<String, Integer> validityCounts = new LinkedHashMap<>();

  private ServerDbSource(Path file, Connection connection) {
    this.file = file;
    this.connection = connection;
    for (String counter : List.of(INVALID_MINCOUNT_ZERO, INVALID_UNKNOWN_ITEM, INVALID_LOW_CHANCE,
        INVALID_MAXCOUNT_BELOW_MIN, REFERENCES_MAXCOUNT_ZERO)) {
      validityCounts.put(counter, 0);
    }
  }

  /**
   * Opens {@code dir/mangos.sqlite} read-only and immutable ({@code ?immutable=1}: no locks, no journal, so a file
   * shared with another reader or a stray writer is read as it is); empty when the directory or the file does not
   * exist.
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
    config.setOpenMode(SQLiteOpenMode.OPEN_URI);
    Connection c = DriverManager.getConnection(
        "jdbc:sqlite:" + file.toAbsolutePath().toUri() + "?immutable=1", config.toProperties());
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

  /** The read-only connection, for tests. */
  Connection connection() {
    return connection;
  }

  /** Item rows left out so far because {@code forbidden_items} bans their item at the patch read. */
  public int forbiddenRows() {
    return forbiddenRows;
  }

  /**
   * The rows skipped so far by each validity rule, and the references kept with {@code maxcount = 0}, by report
   * counter, in rule order; 0 for a rule no row broke.
   */
  public Map<String, Integer> validityCounts() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(validityCounts));
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
    Set<Long> forbidden = forbiddenItems(patch);
    Set<Long> known = knownItems(patch);
    Map<Long, List<LootRow>> rows = new HashMap<>();
    try (PreparedStatement s = connection.prepareStatement(String.format(ROWS, table))) {
      s.setInt(1, patch);
      s.setInt(2, patch);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          LootRow row = new LootRow(rs.getLong(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5), rs.getInt(6));
          if (!row.isReference() && forbidden.contains(row.item())) {
            forbiddenRows++;
            continue;
          }
          String invalid = invalid(row, known);
          if (invalid != null) {
            validityCounts.merge(invalid, 1, Integer::sum);
            continue;
          }
          if (row.isReference() && row.maxcount() == 0) {
            validityCounts.merge(REFERENCES_MAXCOUNT_ZERO, 1, Integer::sum);
          }
          rows.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(row);
        }
      }
    }
    return rows;
  }

  /** The counter of the first {@code LootStoreItem::IsValid} rule {@code row} breaks, or null when it is valid. */
  private static String invalid(LootRow row, Set<Long> known) {
    if (row.mincountOrRef() == 0) {
      return INVALID_MINCOUNT_ZERO;
    }
    if (row.isReference()) {
      return null; // a zero-chance reference is skipped where the template is processed
    }
    if (!known.contains(row.item())) {
      return INVALID_UNKNOWN_ITEM;
    }
    double chance = Math.abs(row.chance());
    if (chance != 0 && chance < MIN_CHANCE) {
      return INVALID_LOW_CHANCE;
    }
    if (row.maxcount() < row.mincountOrRef()) {
      return INVALID_MAXCOUNT_BELOW_MIN;
    }
    return null;
  }

  /** The items {@code item_template} defines at {@code patch} (a row at that patch or an earlier one). */
  private Set<Long> knownItems(int patch) throws SQLException {
    Set<Long> items = new HashSet<>();
    try (PreparedStatement s =
        connection.prepareStatement("SELECT DISTINCT entry FROM item_template WHERE patch <= ?")) {
      s.setInt(1, patch);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          items.add(rs.getLong(1));
        }
      }
    }
    return items;
  }

  /**
   * Items {@code forbidden_items} bans at {@code patch}: {@code after_or_before = 0} from its patch on,
   * {@code 1} up to its patch, as in the server's loot loader. Empty when the table does not exist.
   */
  private Set<Long> forbiddenItems(int patch) throws SQLException {
    Set<Long> items = new HashSet<>();
    try (PreparedStatement exists = connection.prepareStatement(
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'forbidden_items'");
        ResultSet rs = exists.executeQuery()) {
      if (!rs.next() || rs.getInt(1) == 0) {
        return items;
      }
    }
    try (PreparedStatement s = connection.prepareStatement("SELECT entry FROM forbidden_items"
        + " WHERE (after_or_before = 0 AND patch <= ?) OR (after_or_before = 1 AND patch >= ?)")) {
      s.setInt(1, patch);
      s.setInt(2, patch);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          items.add(rs.getLong(1));
        }
      }
    }
    return items;
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
