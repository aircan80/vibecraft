package dev.wowforever.app.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the zone page shows; each list is capped by the repository. {@code map} is empty without one, and
 * {@code levelBand} when the repository could not tell one (no hostile NPC met on the map, say).
 */
public record ZoneDetail(
    ZoneSummary summary,
    List<NpcSummary> npcs,
    List<ObjectSummary> objects,
    List<QuestSummary> quests,
    List<InstanceSummary> instances,
    List<ZoneSummary> subzones,
    Optional<ZoneMap> map,
    Optional<LevelBand> levelBand) {

  public ZoneDetail {
    npcs = List.copyOf(npcs);
    objects = List.copyOf(objects);
    quests = List.copyOf(quests);
    instances = List.copyOf(instances);
    subzones = List.copyOf(subzones);
    Objects.requireNonNull(map, "map");
    Objects.requireNonNull(levelBand, "levelBand");
  }

  /** A zone page without a level band. */
  public ZoneDetail(ZoneSummary summary, List<NpcSummary> npcs, List<ObjectSummary> objects,
      List<QuestSummary> quests, List<InstanceSummary> instances, List<ZoneSummary> subzones, Optional<ZoneMap> map) {
    this(summary, npcs, objects, quests, instances, subzones, map, Optional.empty());
  }

  /** A zone page without a map. */
  public ZoneDetail(ZoneSummary summary, List<NpcSummary> npcs, List<ObjectSummary> objects,
      List<QuestSummary> quests, List<InstanceSummary> instances, List<ZoneSummary> subzones) {
    this(summary, npcs, objects, quests, instances, subzones, Optional.empty());
  }
}
