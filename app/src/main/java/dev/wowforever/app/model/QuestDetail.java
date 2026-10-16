package dev.wowforever.app.model;

import java.util.List;

/** Everything the quest page shows. */
public record QuestDetail(
    QuestSummary summary,
    String objectivesText,
    List<QuestGiver> givers,
    List<LootRow> rewards,
    List<ItemSource> relatedItems) {

  public QuestDetail {
    givers = List.copyOf(givers);
    rewards = List.copyOf(rewards);
    relatedItems = List.copyOf(relatedItems);
  }
}
