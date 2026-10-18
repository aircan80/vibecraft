package dev.wowforever.app.model;

/** A boss of an instance; {@code extraList} marks AtlasLoot's extra (non-boss) loot lists. */
public record BossSummary(long id, String name, Long npcId, Integer level, String specialType, boolean extraList, Long objectId) {
  public BossSummary(long id, String name, Long npcId, Integer level, String specialType, boolean extraList) {
    this(id, name, npcId, level, specialType, extraList, null);
  }
  public EntityRef ref() {
    return new EntityRef(EntityKind.BOSS, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
