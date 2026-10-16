package dev.wowforever.app.model;

import java.util.List;

/** Everything the zone page shows; each list is capped by the repository. */
public record ZoneDetail(
    ZoneSummary summary,
    List<NpcSummary> npcs,
    List<ObjectSummary> objects,
    List<QuestSummary> quests,
    List<InstanceSummary> instances,
    List<ZoneSummary> subzones) {

  public ZoneDetail {
    npcs = List.copyOf(npcs);
    objects = List.copyOf(objects);
    quests = List.copyOf(quests);
    instances = List.copyOf(instances);
    subzones = List.copyOf(subzones);
  }
}
