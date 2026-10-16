package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC helpers: prepared statements bound with {@code setObject}, null-safe column readers. */
public final class Sql {
  private Sql() {}

  /** Maps the current row of a result set. */
  @FunctionalInterface
  public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }

  /** All rows of {@code sql} mapped by {@code m}. */
  public static <T> List<T> list(Connection c, String sql, RowMapper<T> m, Object... params) throws SQLException {
    try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
      List<T> out = new ArrayList<>();
      while (rs.next()) {
        out.add(m.map(rs));
      }
      return out;
    }
  }

  /** The first row of {@code sql} mapped by {@code m}, if any. */
  public static <T> Optional<T> one(Connection c, String sql, RowMapper<T> m, Object... params) throws SQLException {
    try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
      return rs.next() ? Optional.ofNullable(m.map(rs)) : Optional.empty();
    }
  }

  private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
    PreparedStatement ps = c.prepareStatement(sql);
    try {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      return ps;
    } catch (SQLException e) {
      ps.close();
      throw e;
    }
  }

  /** The column as an {@code Integer}, or null for SQL NULL. */
  public static Integer integer(ResultSet rs, String column) throws SQLException {
    Object v = rs.getObject(column);
    return v == null ? null : ((Number) v).intValue();
  }

  /** The column as a {@code Long}, or null for SQL NULL. */
  public static Long longValue(ResultSet rs, String column) throws SQLException {
    Object v = rs.getObject(column);
    return v == null ? null : ((Number) v).longValue();
  }

  /** The column as a {@code Double}, or null for SQL NULL. */
  public static Double doubleValue(ResultSet rs, String column) throws SQLException {
    Object v = rs.getObject(column);
    return v == null ? null : ((Number) v).doubleValue();
  }
}
