package dev.wowforever.app.model;

/** A dungeon or raid; {@code contentType} is the AtlasLoot content type (DUNGEON, RAID20, RAID40). */
public record InstanceSummary(long id, String name, String contentType, Integer levelMin, Integer levelMax) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.INSTANCE, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
