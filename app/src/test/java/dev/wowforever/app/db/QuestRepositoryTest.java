package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.QuestSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
}
