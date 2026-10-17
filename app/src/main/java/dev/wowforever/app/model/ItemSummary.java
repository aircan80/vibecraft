package dev.wowforever.app.model;

/**
 * An item row for lists. {@code name} is null for nameless stub items; {@code sourceCount} counts npc drops,
 * object drops, container drops, vendors and quest rewards. {@code quality} and {@code iconId} come from the
 * client data and are null for items without it.
 */
public record ItemSummary(
    long id, String name, Integer itemLevel, Integer requiredLevel, String className, String subclassName,
    int sourceCount, Integer quality, Long iconId) {

  /** An item row without client data. */
  public ItemSummary(long id, String name, Integer itemLevel, Integer requiredLevel, String className,
      String subclassName, int sourceCount) {
    this(id, name, itemLevel, requiredLevel, className, subclassName, sourceCount, null, null);
  }

  public EntityRef ref() {
    return new EntityRef(EntityKind.ITEM, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
