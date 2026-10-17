package dev.wowforever.app.db;

import dev.wowforever.importer.sqlite.SqliteWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * An empty database with the importer's {@code schema.sql}, for tests that build small graphs row by row. Insert
 * through {@link #sql}, then {@link #open()} it read-only as the app does. The {@link Database} only opens files,
 * so the database lives in a test's temporary directory rather than in memory.
 */
final class SchemaDb implements AutoCloseable {
  private final Path file;
  private final Connection writer;

  private SchemaDb(Path file, Connection writer) {
    this.file = file;
    this.writer = writer;
  }

  /** Creates {@code dir/schema.sqlite} with every table and index of the schema. */
  static SchemaDb create(Path dir) throws SQLException {
    Path file = dir.resolve("schema.sqlite");
    Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    try (Statement s = c.createStatement()) {
      for (String statement : withoutComments(SqliteWriter.schemaSql()).split(";")) {
        if (!statement.isBlank()) {
          s.execute(statement);
        }
      }
    }
    return new SchemaDb(file, c);
  }

  /** Runs one write statement with {@code params} bound in order. */
  SchemaDb sql(String sql, Object... params) throws SQLException {
    try (PreparedStatement ps = writer.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      ps.executeUpdate();
    }
    return this;
  }

  /** Inserts quest {@code id} named {@code name} at {@code level}, filed under no zone. */
  SchemaDb quest(long id, String name, Integer level) throws SQLException {
    return sql("INSERT INTO quest (id, name, quest_level) VALUES (?, ?, ?)", id, name, level);
  }

  /** Inserts the relation row {@code questId → relatedId} of {@code kind}. */
  SchemaDb relation(long questId, long relatedId, String kind) throws SQLException {
    return sql("INSERT INTO quest_relation (quest_id, related_id, kind) VALUES (?, ?, ?)", questId, relatedId, kind);
  }

  /** The database opened read-only, as the app opens it; the caller closes it. */
  Database open() throws SQLException {
    return Database.open(file);
  }

  @Override
  public void close() throws SQLException {
    writer.close();
  }

  /** The schema with its {@code --} comments removed (some of them contain semicolons). */
  private static String withoutComments(String sql) {
    StringBuilder out = new StringBuilder();
    for (String line : sql.split("\\R")) {
      int comment = line.indexOf("--");
      out.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
    }
    return out.toString();
  }
}
