package dev.wowforever.app.model;

/**
 * {@code count} of item {@code itemId} that a craft consumes. {@code name}, {@code quality} and {@code iconId} come
 * from the item table and are null when the item is unknown there.
 */
public record CraftReagent(long itemId, int count, String name, Integer quality, Long iconId) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.ITEM, itemId);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
