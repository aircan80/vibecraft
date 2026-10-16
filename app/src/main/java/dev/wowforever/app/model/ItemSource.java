package dev.wowforever.app.model;

/**
 * A non-NPC-drop link between an item and another entity. {@code kind} is one of {@link #OBJECT},
 * {@link #CONTAINER}, {@link #VENDOR}, {@link #REWARD} or {@link #RELATED}; {@code name} is null when unknown.
 */
public record ItemSource(String kind, EntityRef ref, String name) {
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

  public String displayName() {
    return ref.displayName(name);
  }
}
