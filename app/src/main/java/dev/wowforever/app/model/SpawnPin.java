package dev.wowforever.app.model;

import java.util.Objects;

/**
 * One spawn point of an NPC or object to pin on a zone map: the entity, its name (null when unknown), the NPC's raw
 * rank (null for objects and unknown ranks) and the point as map percentages (0 to 100).
 */
public record SpawnPin(EntityRef ref, String name, Integer rank, double x, double y) {
  public SpawnPin {
    Objects.requireNonNull(ref, "ref");
  }

  /** The name, else {@code "Unknown <kind> #<id>"}. */
  public String displayName() {
    return ref.displayName(name);
  }
}
