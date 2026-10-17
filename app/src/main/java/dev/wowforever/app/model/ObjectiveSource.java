package dev.wowforever.app.model;

import java.util.List;

/** Immutable quest-page data assembled off the EDT. */
public record ObjectiveSource(EntityRef ref, String name, Double dropPct, String pctSource, List<Spawn> spawns) {
  public ObjectiveSource {
    spawns = List.copyOf(spawns);
  }
}
