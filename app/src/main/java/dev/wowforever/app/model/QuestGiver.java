package dev.wowforever.app.model;

/**
 * An NPC, object or item that starts or ends a quest. {@code role} is {@code "start"} or {@code "end"};
 * {@code name} is null when the entity is unknown.
 */
public record QuestGiver(EntityRef ref, String name, String role) {
  public String displayName() {
    return ref.displayName(name);
  }
}
