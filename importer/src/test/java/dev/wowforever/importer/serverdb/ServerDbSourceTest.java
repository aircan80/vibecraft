package dev.wowforever.importer.serverdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

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
  void rowsTheServersLootValidityCheckRejectsAreSkippedAndCountedOncePerRule() throws Exception {
    // LootStoreItem::IsValid: these rows never reach a loot template on the server
    Path dir = new ServerDbFixture().creature(1, 7)
        .loot(7, 100, 5, 0, 1, 1) // valid
        .loot(7, 101, 5, 0, 0, 1) // mincountOrRef = 0
        .loot(7, 102, 5, 0, 1, 1).unknownItem(102) // not in item_template
        .loot(7, 103, 5e-7, 0, 1, 1) // chance below 1e-6
        .loot(7, 104, -5e-7, 1, 1, 1) // a quest chance below 1e-6, grouped
        .loot(7, 105, 5, 0, 3, 2) // maxcount below mincount
        .loot(7, 106, 0, 1, 1, 1) // a grouped zero chance is an equal share: valid
        .loot(7, 107, 5, 0, 2, 2) // maxcount equal to mincount: valid
        .lootRef(7, 300, 5e-7, 0, 1) // a reference is not an item row: a tiny chance is kept
        .lootRef(7, 301, 10, 0, 0) // a reference rolled maxcount = 0 times is kept, rolled once, and counted
        .ref(300, 200, 5, 0, 1, 1).ref(300, 201, 5, 0, 0, 1).ref(301, 202, 5, 0, 1, 1).unknownItem(202)
        .write(tmp.resolve("db"));
    try (ServerDbSource db = ServerDbSource.open(dir).orElseThrow()) {
      Map<Long, List<LootRow>> loot = db.creatureLoot(10);
      Map<Long, List<LootRow>> references = db.referenceLoot(10);
      assertThat(loot.get(7L)).extracting(LootRow::item).containsExactlyInAnyOrder(100L, 106L, 107L, 300L, 301L);
      assertThat(references).containsOnlyKeys(300L);
      assertThat(references.get(300L)).extracting(LootRow::item).containsExactly(200L);
      assertThat(db.validityCounts()).containsExactly(
          entry(ServerDbSource.INVALID_MINCOUNT_ZERO, 2), entry(ServerDbSource.INVALID_UNKNOWN_ITEM, 2),
          entry(ServerDbSource.INVALID_LOW_CHANCE, 2), entry(ServerDbSource.INVALID_MAXCOUNT_BELOW_MIN, 1),
          entry(ServerDbSource.REFERENCES_MAXCOUNT_ZERO, 1));
    }
  }

  @Test
  void theWorldDatabaseIsOpenedImmutableSoAnotherWritersLockDoesNotBlockIt() throws Exception {
    Path dir = new ServerDbFixture().creature(1, 7).loot(7, 100, 5, 0, 1, 1).write(tmp.resolve("a dir with spaces"));
    try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("mangos.sqlite"))) {
      writer.createStatement().execute("BEGIN EXCLUSIVE");
      try (ServerDbSource db = ServerDbSource.open(dir).orElseThrow()) {
        assertThat(db.creatureLootIds(10)).containsEntry(1L, 7L);
      }
      writer.createStatement().execute("ROLLBACK");
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
