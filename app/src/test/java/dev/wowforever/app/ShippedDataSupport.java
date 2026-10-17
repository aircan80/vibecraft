package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real committed database for the shipped-data tests, and the SQL helpers they share. Each test class extracts
 * it from the classpath resource with the production {@link DatabaseInstaller}, so every class also covers the
 * extraction path on the shipped file. The state is static: the classes must not run concurrently (the build
 * configures no parallel execution).
 */
abstract class ShippedDataSupport {
  static Database db;
  static Repositories repos;

  @BeforeAll
  static void open(@TempDir Path dir) throws Exception {
    Path file = new DatabaseInstaller(dir).install();
    db = Database.open(file);
    repos = Repositories.of(db);
  }

  @AfterAll
  static void close() {
    if (db != null) {
      db.close();
      db = null;
      repos = null;
    }
  }

  static long scalar(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        assertThat(rs.next()).as(sql).isTrue();
        return rs.getLong(1);
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  static List<Object> column(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        List<Object> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getObject(1));
        }
        return out;
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  static List<Long> longs(String sql) {
    return column(sql).stream().map(o -> ((Number) o).longValue()).toList();
  }

  static List<Object[]> rows(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        List<Object[]> out = new ArrayList<>();
        int n = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Object[] row = new Object[n];
          for (int i = 0; i < n; i++) {
            row[i] = rs.getObject(i + 1);
          }
          out.add(row);
        }
        return out;
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  static long count(String table) {
    return scalar("select count(*) from " + table);
  }

  /** The committed {@code import-report.txt} that ships beside the database. */
  static String committedReport() throws Exception {
    try (InputStream in = ShippedDataSupport.class.getResourceAsStream("/data/import-report.txt")) {
      assertThat(in).as("/data/import-report.txt").isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** The value of the report line {@code key = n}; fails when the line is missing. */
  static long reportCounter(String report, String key) {
    Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + " = (\\d+)\\s*$").matcher(report);
    assertThat(m.find()).as("%s in import-report.txt", key).isTrue();
    return Long.parseLong(m.group(1));
  }
}
