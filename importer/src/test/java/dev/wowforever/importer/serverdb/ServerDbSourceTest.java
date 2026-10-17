package dev.wowforever.importer.serverdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerDbSourceTest {
  @TempDir Path tmp;

  @Test
  void aDirectoryWithoutTheWorldDatabaseOpensNothing() throws Exception {
    assertThat(ServerDbSource.open(tmp.resolve("absent"))).isEmpty();
    assertThat(ServerDbSource.open(tmp)).isEmpty();
  }

  @Test
  void readsCreatureLootIdsAndLootRowsAtAPatch() throws Exception {
    Path dir = new ServerDbFixture()
        .creature(1, 0, 0).creature(1, 4, 7).creature(2, 9)
        .loot(7, 100, -50, 0, 1, 1).lootRef(7, 300, 10, 2, 3).lootPatched(7, 101, 5, 0, 6)
        .ref(300, 200, 0, 1, 1, 2)
        .write(tmp.resolve("db"));
    try (ServerDbSource db = ServerDbSource.open(dir).orElseThrow()) {
      assertThat(db.file()).isEqualTo(dir.resolve("mangos.sqlite"));
      assertThat(db.creatureLootIds(10)).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 7L, 2L, 9L));
      assertThat(db.creatureLootIds(3)).containsExactlyInAnyOrderEntriesOf(Map.of(2L, 9L));
      assertThat(db.creatureLoot(10)).containsOnlyKeys(7L);
      assertThat(db.creatureLoot(10).get(7L)).containsExactlyInAnyOrder(
          new LootRow(100, -50, 0, 1, 1), new LootRow(300, 10, 2, -300, 3));
      assertThat(db.creatureLoot(5).get(7L)).hasSize(3);
      assertThat(db.referenceLoot(10)).isEqualTo(Map.of(300L, List.of(new LootRow(200, 0, 1, 1, 2))));
    }
  }

  @Test
  void theWorldDatabaseIsOpenedReadOnly() throws Exception {
    Path dir = new ServerDbFixture().creature(1, 1).write(tmp.resolve("db"));
    try (ServerDbSource db = ServerDbSource.open(dir).orElseThrow()) {
      assertThatThrownBy(() -> db.connection().createStatement().execute("DELETE FROM creature_template"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void aWorldDatabaseWithoutTheLootTablesIsRefused() throws Exception {
    Files.createDirectories(tmp.resolve("db"));
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp.resolve("db/mangos.sqlite"))) {
      c.createStatement().execute("CREATE TABLE creature_template (entry integer, patch integer, loot_id integer)");
    }
    assertThatThrownBy(() -> ServerDbSource.open(tmp.resolve("db")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("creature_loot_template");
  }
}
