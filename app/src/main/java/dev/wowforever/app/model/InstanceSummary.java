package dev.wowforever.app.model;

/**
 * A dungeon or raid; {@code contentType} is the AtlasLoot content type (DUNGEON, RAID20, RAID40). {@code zoneId}
 * is the zone the instance is in (null when unknown) and {@code zoneName} its name (null when unknown).
 */
public record InstanceSummary(
    long id, String name, String contentType, Integer levelMin, Integer levelMax, Long zoneId, String zoneName) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.INSTANCE, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
