package dev.wowforever.app;

import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.SearchHit;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sanity checks against the real committed database. It is extracted from the classpath resource by the
 * production {@link DatabaseInstaller}, so these tests also cover the extraction path on the shipped file.
 * Skip them with {@code -DexcludedGroups=shipped-data}.
 */
@Tag("shipped-data")
class ShippedDataTest {
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
    }
  }

  private static long scalar(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        rs.next();
        return rs.getLong(1);
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  private static long count(String table) {
    return scalar("select count(*) from " + table);
  }

  @Test
  void minimumCounts() {
    assertThat(count("item")).isGreaterThan(15_000);
    assertThat(count("npc")).isGreaterThan(8_000);
    assertThat(count("instance")).isEqualTo(33);
    assertThat(count("item_npc_drop")).isGreaterThan(50_000);
    assertThat(count("search_index")).isGreaterThan(30_000);
  }

  @Test
  void noOrphansInPrimaryLinks() {
    // AtlasLoot items missing from Questie: tolerate < 5 %
    assertThat(scalar("select count(*) from boss_loot bl left join item i on i.id = bl.item_id where i.id is null"))
        .isLessThan(count("boss_loot") / 20);
    assertThat(scalar("select count(*) from boss b left join instance i on i.id = b.instance_id where i.id is null"))
        .isZero();
    assertThat(scalar("select count(*) from npc_spawn s left join zone z on z.id = s.zone_id where z.id is null"))
        .isZero();
    // documented upstream gap: 325 at the pinned commits
    assertThat(scalar("select count(*) from item_npc_drop d left join npc n on n.id = d.npc_id where n.id is null"))
        .isLessThanOrEqualTo(400);
  }

  @Test
  void knownFacts() {
    assertThat(repos.items().detail(14149).get().droppedBy()).extracting(s -> s.ref().id()).contains(11520L);
    // Eye of Sulfuras from Ragnaros (Thunderfury is crafted, not dropped)
    var sulfuras = repos.items().detail(17204).get().droppedBy().stream()
        .filter(s -> s.ref().id() == 11502L).findFirst();
    assertThat(sulfuras).isPresent();
    assertThat(sulfuras.get().dropPct()).isNotNull().isBetween(7.5, 8.6);
    assertThat(repos.npcs().detail(639).get().drops())
        .anyMatch(l -> l.item().id() == 2874 && l.dropPct() != null && l.dropPct() > 50);
    assertThat(repos.search().search("hogger", Optional.empty(), 5)).extracting(SearchHit::ref)
        .contains(new EntityRef(NPC, 448));
    assertThat(repos.zones().detail(1581).get().summary().name()).isEqualTo("The Deadmines");
    assertThat(repos.instances().all()).hasSize(33).extracting(InstanceSummary::name).contains("Molten Core");
  }
}
