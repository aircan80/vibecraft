package dev.wowforever.app.model;

/**
 * A quest a giver starts ({@code starts}) or ends: its id, its name (null when unknown) and its level (null when
 * unknown).
 */
public record QuestRef(long questId, String name, Integer level, boolean starts) {
  /** The quest to open. */
  public EntityRef ref() {
    return new EntityRef(EntityKind.QUEST, questId);
  }

  /** The name, else {@code "Unknown quest #<id>"}. */
  public String displayName() {
    return ref().displayName(name);
  }

  /** {@code "Wanted: Hogger (11)"}: the name with the level, without it when unknown. */
  public String label() {
    return level == null ? displayName() : displayName() + " (" + level + ")";
  }
}
