package dev.wowforever.app.model;

/** A quest row for lists; {@code zoneName} is null when the quest is filed under a sort, not a zone. */
public record QuestSummary(long id, String name, Integer questLevel, Integer requiredLevel, String zoneName) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.QUEST, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
