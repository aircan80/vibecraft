package dev.wowforever.app.model;

import java.util.List;

/** Immutable quest-page data assembled off the EDT. */
public record GiverSpot(QuestGiver giver, List<Spawn> spawns, List<ObjectiveSource> itemSources) {
  public GiverSpot {
    spawns = List.copyOf(spawns);
    itemSources = List.copyOf(itemSources);
  }
}
