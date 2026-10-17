package dev.wowforever.app.model;

/**
 * An NPC, object or item that starts or ends a quest. {@code role} is {@code "start"} or {@code "end"};
 * {@code name} is null when the entity is unknown. {@code quality} and {@code iconId} are an item giver's client
 * data, null for other givers and items without it.
 */
public record QuestGiver(EntityRef ref, String name, String role, Integer quality, Long iconId) {
  /** A giver without item client data. */
  public QuestGiver(EntityRef ref, String name, String role) {
    this(ref, name, role, null, null);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
