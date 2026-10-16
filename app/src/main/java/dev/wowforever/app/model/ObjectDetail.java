package dev.wowforever.app.model;

import java.util.List;

/** Everything the object page shows. */
public record ObjectDetail(
    ObjectSummary summary,
    List<LootRow> contains,
    List<QuestSummary> starts,
    List<QuestSummary> ends,
    List<Spawn> spawns) {

  public ObjectDetail {
    contains = List.copyOf(contains);
    starts = List.copyOf(starts);
    ends = List.copyOf(ends);
    spawns = List.copyOf(spawns);
  }
}
