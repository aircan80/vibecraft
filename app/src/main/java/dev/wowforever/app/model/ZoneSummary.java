package dev.wowforever.app.model;

/**
 * A zone row; {@code kind} is zone, subzone, dungeon, raid, city, continent or unknown. {@code parentName} is
 * the parent zone's name, null when there is no parent or it has no name.
 */
public record ZoneSummary(long id, String name, String kind, Long parentId, String parentName) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.ZONE, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
