package dev.wowforever.app.model;

/**
 * An item the quest hands over at the start ({@code kind} {@code provided}) or that its objectives need besides
 * the collected items ({@code required}). {@code name}, {@code quality} and {@code iconId} are null when the item is
 * unknown.
 */
public record QuestSourceItem(long itemId, String kind, String name, Integer quality, Long iconId) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.ITEM, itemId);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
