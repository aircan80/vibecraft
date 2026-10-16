package dev.wowforever.importer.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** A prepared insert that sends its rows to SQLite in batches of {@value #SIZE}. */
final class Batch implements AutoCloseable {
  static final int SIZE = 5_000;

  private final PreparedStatement statement;
  private int pending;

  Batch(Connection connection, String sql) throws SQLException {
    this.statement = connection.prepareStatement(sql);
  }

  /** Adds one row; {@code null} values become SQL NULL. */
  void add(Object... values) throws SQLException {
    for (int i = 0; i < values.length; i++) {
      statement.setObject(i + 1, values[i]);
    }
    statement.addBatch();
    if (++pending == SIZE) {
      flush();
    }
  }

  private void flush() throws SQLException {
    if (pending > 0) {
      statement.executeBatch();
      pending = 0;
    }
  }

  @Override
  public void close() throws SQLException {
    try {
      flush();
    } finally {
      statement.close();
    }
  }
}
