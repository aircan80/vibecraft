package dev.wowforever.app.model;

/**
 * An NPC that drops an item. {@code name} (and the level and zone columns) are null when the drop row
 * points at an NPC the database does not know. {@code sourceCount} is how many drop rate sources gave a rate
 * and {@code disagrees} whether they disagree on it.
 */
public record DropSource(
    EntityRef ref, String name, Integer levelMin, Integer levelMax, String zoneName, Long zoneId,
    Double dropPct, String pctSource, String origin, boolean disagrees, int sourceCount, boolean observedInGame) {

  public DropSource(EntityRef ref, String name, Integer levelMin, Integer levelMax, String zoneName, Long zoneId,
      Double dropPct, String pctSource, String origin, boolean disagrees, int sourceCount) {
    this(ref, name, levelMin, levelMax, zoneName, zoneId, dropPct, pctSource, origin, disagrees, sourceCount, false);
  }

  public String displayOrigin() { return observedInGame ? "collector" : origin; }

  /** A drop without rate provenance. */
  public DropSource(EntityRef ref, String name, Integer levelMin, Integer levelMax, String zoneName, Long zoneId,
      Double dropPct, String pctSource, String origin) {
    this(ref, name, levelMin, levelMax, zoneName, zoneId, dropPct, pctSource, origin, false, 0);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
