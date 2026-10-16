package dev.wowforever.app.model;

/** An NPC row for lists; {@code rank} is the raw rank number. */
public record NpcSummary(
    long id, String name, String subName, Integer minLevel, Integer maxLevel, Integer rank, String zoneName,
    Long zoneId) {

  public EntityRef ref() {
    return new EntityRef(EntityKind.NPC, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
