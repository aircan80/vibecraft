package dev.wowforever.app.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/**
 * One read-only connection to the browser database. The connection is not thread-safe: repositories run
 * every query through {@link #read(Work)}, which synchronizes on this object, so worker threads take turns.
 */
public final class Database implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(Database.class);

  private final Connection connection;
  private Map<String, String> meta;

  private Database(Connection connection) {
    this.connection = connection;
  }

  /** Database work run under the database lock. */
  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  /** A query against the database failed. */
  public static final class QueryException extends RuntimeException {
    public QueryException(SQLException cause) {
      super(cause.getMessage(), cause);
    }
  }

  /** Opens {@code file} read-only; it is never created or written. */
  public static Database open(Path file) throws SQLException {
    SQLiteConfig cfg = new SQLiteConfig();
    cfg.setReadOnly(true);
    cfg.setOpenMode(SQLiteOpenMode.READONLY);
    cfg.resetOpenMode(SQLiteOpenMode.READWRITE);
    cfg.resetOpenMode(SQLiteOpenMode.CREATE);
    return new Database(cfg.createConnection("jdbc:sqlite:" + file.toAbsolutePath()));
  }

  /** The underlying connection; callers must hold the lock on this object while using it. */
  public Connection connection() {
    return connection;
  }

  /** Runs {@code work} on the connection while holding the lock on this object. */
  public synchronized <T> T read(Work<T> work) {
    try {
      return work.run(connection);
    } catch (SQLException e) {
      throw new QueryException(e);
    }
  }

  /** The {@code meta} table (build id, source commits, ...), read once. */
  public synchronized Map<String, String> meta() {
    if (meta == null) {
      Map<String, String> m = new LinkedHashMap<>();
      for (String[] kv : read(c -> Sql.list(c, "select key, value from meta order by key",
          rs -> new String[] {rs.getString("key"), rs.getString("value")}))) {
        m.put(kv[0], kv[1]);
      }
      meta = Collections.unmodifiableMap(m);
    }
    return meta;
  }

  /** Closes the connection. */
  @Override
  public synchronized void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      LOG.warn("Closing the database failed", e);
    }
  }
}
