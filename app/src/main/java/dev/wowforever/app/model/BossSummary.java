package dev.wowforever.app.model;

/** A boss of an instance; {@code extraList} marks AtlasLoot's extra (non-boss) loot lists. */
public record BossSummary(long id, String name, Long npcId, Integer level, String specialType, boolean extraList) {
  public EntityRef ref() {
    return new EntityRef(EntityKind.BOSS, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
