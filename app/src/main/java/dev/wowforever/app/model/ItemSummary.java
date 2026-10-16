package dev.wowforever.app.model;

/**
 * An item row for lists. {@code name} is null for nameless stub items; {@code sourceCount} counts npc drops,
 * object drops, container drops, vendors and quest rewards.
 */
public record ItemSummary(
    long id, String name, Integer itemLevel, Integer requiredLevel, String className, String subclassName,
    int sourceCount) {

  public EntityRef ref() {
    return new EntityRef(EntityKind.ITEM, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
