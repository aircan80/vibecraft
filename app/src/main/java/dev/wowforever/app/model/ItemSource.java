package dev.wowforever.app.model;

/**
 * A non-NPC-drop link between an item and another entity. {@code kind} is one of {@link #OBJECT},
 * {@link #CONTAINER}, {@link #VENDOR}, {@link #REWARD} or {@link #RELATED}; {@code name} is null when unknown.
 * When {@code ref} is an item (a container, a quest's related item), {@code quality} and {@code iconId} are its
 * quality and icon (null without client data); otherwise both are null.
 */
public record ItemSource(String kind, EntityRef ref, String name, Integer quality, Long iconId) {
  /** The item is found in a game object. */
  public static final String OBJECT = "object";
  /** The item is found in a container item. */
  public static final String CONTAINER = "container";
  /** The item is sold by an NPC. */
  public static final String VENDOR = "vendor";
  /** The item is a quest reward. */
  public static final String REWARD = "reward";
  /** The item and the quest are related (quest item, starts the quest, ...). */
  public static final String RELATED = "related";

  /** A link without quality or icon (an object, an NPC, a quest). */
  public ItemSource(String kind, EntityRef ref, String name) {
    this(kind, ref, name, null, null);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
