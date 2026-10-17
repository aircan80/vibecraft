package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class QuestRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void questDetailHasGivers() {
    assertThat(repos.quests().detail(2).get().givers()).isNotEmpty();
  }

  @Test
  void startGiversComeFirstAndKnowTheirQuest() {
    var givers = repos.quests().detail(2).get().givers();
    assertThat(givers.get(0).role()).isEqualTo("start");
    assertThat(givers).allSatisfy(g -> assertThat(g.displayName()).isNotBlank());
  }

  @Test
  void missingQuestIsEmpty() {
    assertThat(repos.quests().detail(-1)).isEmpty();
  }

  @Test
  void questListByZoneAndLevel() {
    assertThat(repos.quests().list(new QuestFilter(40L, null, null))).extracting(QuestSummary::name)
        .containsExactly("Captain Sander's Hidden Treasure");
    assertThat(repos.quests().list(new QuestFilter(null, 15, 16))).extracting(QuestSummary::id)
        .containsExactly(5691L, 5692L, 136L);
    assertThat(repos.quests().list(new QuestFilter(40L, 20, null))).isEmpty();
    assertThat(repos.quests().list(QuestFilter.ALL)).hasSize(10);
    assertThat(repos.quests().list(new QuestFilter(40L, null, null))).isEqualTo(repos.quests().byZone(40));
  }

  @Test
  void itemGiversCarryTheItemQualityAndIcon(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest_giver(quest_id, kind, entity_id, role) values (2, 'item', 17204, 'end')");
    }
    try (Database db = Database.open(copy)) {
      List<QuestGiver> givers = Repositories.of(db).quests().detail(2).orElseThrow().givers();
      assertThat(givers).filteredOn(g -> g.ref().id() == 17204).singleElement().satisfies(g -> {
        assertThat(g.name()).isEqualTo("Eye of Sulfuras");
        assertThat(g.quality()).isEqualTo(5);
        assertThat(g.iconId()).isEqualTo(134124L);
      });
      assertThat(givers).filteredOn(g -> g.ref().kind() == EntityKind.NPC).isNotEmpty()
          .allSatisfy(g -> {
            assertThat(g.quality()).isNull();
            assertThat(g.iconId()).isNull();
          });
    }
  }

  @Test
  void questLevelsOfZeroAndBelowAreUnknown(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest(id, name, quest_level, zone_or_sort) values "
          + "(97001, 'Scaling Quest', -1, 40), (97002, 'Level Zero Quest', 0, 40)");
    }
    try (Database db = Database.open(copy)) {
      QuestRepository quests = Repositories.of(db).quests();
      assertThat(quests.list(new QuestFilter(40L, null, null))).extracting(QuestSummary::id)
          .contains(97001L, 97002L);
      assertThat(quests.list(new QuestFilter(40L, null, 20))).extracting(QuestSummary::id).containsExactly(136L);
      assertThat(quests.list(new QuestFilter(40L, 1, null))).extracting(QuestSummary::id).containsExactly(136L);
    }
  }
}
