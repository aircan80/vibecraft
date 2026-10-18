package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalDatabaseTest {
  @Test void acceptsCompatibleLocalDatabaseWithoutChangingBytes(@TempDir Path dir) throws Exception {
    Path file = database(dir, "4", "wow_classic_beta", "enUS");
    byte[] before = Files.readAllBytes(file);
    assertThat(DatabaseInstaller.validateExternal(file)).isEqualTo(file.toAbsolutePath());
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }
  @Test void rejectsWrongSchemaEditionAndLocale(@TempDir Path dir) throws Exception {
    for (String[] values : new String[][]{{"5","wow_classic_beta","enUS"},{"4","wow","enUS"},{"4","wow_classic_beta","deDE"}}) {
      Path file = database(dir, values[0], values[1], values[2]);
      byte[] before = Files.readAllBytes(file);
      assertThatIOException().isThrownBy(() -> DatabaseInstaller.validateExternal(file));
      assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
  }
  @Test void missingFileIsNeverCreated(@TempDir Path dir) {
    Path missing = dir.resolve("missing.sqlite");
    assertThatIOException().isThrownBy(() -> DatabaseInstaller.validateExternal(missing));
    assertThat(missing).doesNotExist();
  }
  @Test void rejectsIncompleteSchema(@TempDir Path dir) throws Exception {
    Path file = database(dir, "4", "wow_classic_beta", "enUS");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var s = c.createStatement()) { s.execute("DROP TABLE npc"); }
    assertThatIOException().isThrownBy(() -> DatabaseInstaller.validateExternal(file));
  }
  private static Path database(Path dir, String schema, String product, String locale) throws Exception {
    Path file = Files.createTempFile(dir, "synthetic-", ".sqlite");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var s = c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      try (var p = c.prepareStatement("INSERT INTO meta VALUES (?,?)")) {
        for (String[] pair : new String[][]{{"schema_version",schema},{"client_product",product},{"client_locale",locale},{"client_build","1.60.1.70450"},{"build_id","synthetic"}}) {
          p.setString(1,pair[0]);p.setString(2,pair[1]);p.executeUpdate();
        }
      }
      for(String table: java.util.List.of("item","npc","quest","zone","instance","game_object")) s.execute("CREATE TABLE "+table+"(id INTEGER)");
    }
    return file;
  }
}
