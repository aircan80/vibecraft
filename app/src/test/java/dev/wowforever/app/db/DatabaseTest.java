package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class DatabaseTest {

  @Test
  void openRefusesMissingFileWithoutCreatingIt(@TempDir Path dir) {
    Path missing = dir.resolve("missing.sqlite");
    assertThatThrownBy(() -> Database.open(missing).meta()).isInstanceOf(SQLException.class);
    assertThat(missing).doesNotExist();
  }

  @Test
  void connectionIsReadOnly() throws Exception {
    try (Database db = Database.open(FixtureDb.path())) {
      assertThat(db.meta()).containsKeys("build_id", "importer_version");
      assertThatThrownBy(() -> db.read(c -> {
        try (Statement s = c.createStatement()) {
          return s.executeUpdate("delete from meta");
        }
      })).isInstanceOf(Database.QueryException.class);
      assertThat(db.meta().get("build_id")).isEqualTo(FixtureDb.buildId());
    }
  }
}
