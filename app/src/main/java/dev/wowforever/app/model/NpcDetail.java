package dev.wowforever.app.model;

import java.util.List;

/** Everything the NPC page shows. */
public record NpcDetail(
    NpcSummary summary,
    int npcFlags,
    String friendlyTo,
    List<LootRow> drops,
    List<LootRow> sells,
    List<QuestSummary> starts,
    List<QuestSummary> ends,
    List<Spawn> spawns) {

  public NpcDetail {
    drops = List.copyOf(drops);
    sells = List.copyOf(sells);
    starts = List.copyOf(starts);
    ends = List.copyOf(ends);
    spawns = List.copyOf(spawns);
  }
}
