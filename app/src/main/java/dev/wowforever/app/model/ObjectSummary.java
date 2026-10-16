package dev.wowforever.app.model;

/** A game object row for lists; {@code name} is null for nameless objects. */
public record ObjectSummary(long id, String name, String zoneName, Long zoneId) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.OBJECT, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
