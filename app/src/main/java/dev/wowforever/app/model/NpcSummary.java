package dev.wowforever.app.model;

/**
 * An NPC row for lists; {@code rank} is the rank shown (Questie's, or Boss for an AtlasLoot encounter) and
 * {@code friendlyTo} the factions the NPC is friendly to ({@code A}, {@code H}, {@code AH}; null for a hostile one).
 */
public record NpcSummary(
    long id, String name, String subName, Integer minLevel, Integer maxLevel, Integer rank, String zoneName,
    Long zoneId, String friendlyTo) {

  /** A hostile NPC (or one whose reaction is unknown). */
  public NpcSummary(long id, String name, String subName, Integer minLevel, Integer maxLevel, Integer rank,
      String zoneName, Long zoneId) {
    this(id, name, subName, minLevel, maxLevel, rank, zoneName, zoneId, null);
  }

  public EntityRef ref() {
    return new EntityRef(EntityKind.NPC, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }

  /** Whether the NPC is friendly to a faction (a guard, a vendor, a quest giver, a critter). */
  public boolean friendly() {
    return friendlyTo != null && !friendlyTo.isBlank();
  }
}
