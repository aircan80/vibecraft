package dev.wowforever.app.model;

import java.util.Optional;

/**
 * One AtlasLoot loot table an item is in: the instance, the boss (or extra list, such as trash) and the slot.
 * {@code bossNpcId} is null for extra lists and bosses without an NPC. {@code sourceCount} is how many drop rate
 * sources gave a rate and {@code disagrees} whether they disagree on it.
 */
public record InstanceLoot(
    EntityRef instance, String instanceName, long bossId, String bossName, Long bossNpcId, Integer slot,
    Double dropPct, boolean disagrees, int sourceCount) {

  /** A loot table entry without rate provenance. */
  public InstanceLoot(EntityRef instance, String instanceName, long bossId, String bossName, Long bossNpcId,
      Integer slot, Double dropPct) {
    this(instance, instanceName, bossId, bossName, bossNpcId, slot, dropPct, false, 0);
  }

  public String instanceDisplayName() {
    return instance.displayName(instanceName);
  }

  public String bossDisplayName() {
    return new EntityRef(EntityKind.BOSS, bossId).displayName(bossName);
  }

  /** The boss's NPC, when it has one. */
  public Optional<EntityRef> bossNpc() {
    return Optional.ofNullable(bossNpcId).map(id -> new EntityRef(EntityKind.NPC, id));
  }
}
