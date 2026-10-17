package dev.wowforever.app.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Everything the zone page shows; each list is capped by the repository. {@code map} is empty without one. */
public record ZoneDetail(
    ZoneSummary summary,
    List<NpcSummary> npcs,
    List<ObjectSummary> objects,
    List<QuestSummary> quests,
    List<InstanceSummary> instances,
    List<ZoneSummary> subzones,
    Optional<ZoneMap> map) {

  public ZoneDetail {
    npcs = List.copyOf(npcs);
    objects = List.copyOf(objects);
    quests = List.copyOf(quests);
    instances = List.copyOf(instances);
    subzones = List.copyOf(subzones);
    Objects.requireNonNull(map, "map");
  }

  /** A zone page without a map. */
  public ZoneDetail(ZoneSummary summary, List<NpcSummary> npcs, List<ObjectSummary> objects,
      List<QuestSummary> quests, List<InstanceSummary> instances, List<ZoneSummary> subzones) {
    this(summary, npcs, objects, quests, instances, subzones, Optional.empty());
  }
}
