package dev.wowforever.app.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** All quest-page facts; no database work is needed to render this record. */
public record QuestPageData(QuestDetail detail, QuestFacts facts, List<ObjectiveRow> objectives,
    List<QuestSourceItem> sourceItems, List<GiverSpot> starts, List<GiverSpot> ends, QuestChain chain) {
  public QuestPageData {
    objectives = List.copyOf(objectives);
    sourceItems = List.copyOf(sourceItems);
    starts = List.copyOf(starts);
    ends = List.copyOf(ends);
  }

  /** Zones with on-map locations, in pickup, objective, turn-in order, without duplicates. */
  public List<Long> mapZones() {
    Set<Long> zones = new LinkedHashSet<>();
    starts.forEach(g -> add(zones, g));
    objectives.forEach(o -> {
      o.sources().forEach(s -> s.spawns().forEach(p -> add(zones, p)));
      o.points().stream().filter(p -> MapCoordinates.onMap(p.x(), p.y())).forEach(p -> zones.add(p.zoneId()));
    });
    ends.forEach(g -> add(zones, g));
    return List.copyOf(zones);
  }

  private static void add(Set<Long> zones, GiverSpot giver) {
    giver.spawns().forEach(p -> add(zones, p));
    giver.itemSources().forEach(s -> s.spawns().forEach(p -> add(zones, p)));
  }

  private static void add(Set<Long> zones, Spawn p) {
    if (MapCoordinates.onMap(p.x(), p.y())) {
      zones.add(p.zoneId());
    }
  }
}
