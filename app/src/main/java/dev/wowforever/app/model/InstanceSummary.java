package dev.wowforever.app.model;

/**
 * A dungeon or raid; {@code contentType} is the AtlasLoot content type (DUNGEON, RAID20, RAID40). {@code zoneId}
 * is the zone the instance is in (null when unknown) and {@code zoneName} its name (null when unknown).
 * {@code key} is the AtlasLoot key, such as {@code ScarletMonasteryLibrary}, which tells instances that share a
 * name apart (null when unknown); a client dungeon's key is {@code client:<map id>}.
 *
 * <p>Where the instance comes from (schema v4): {@code source} is {@value #ATLASLOOT} or {@value #CLIENT} (a WoW:
 * Forever dungeon of the client's own tables, which AtlasLoot lacks). {@code tunedMinLevel} is the client's tuned
 * minimum level (null for AtlasLoot instances). {@code bossesState} is {@value #ATLASLOOT} (AtlasLoot's boss lists)
 * or {@value #BOSSES_CLIENT_NAMED} (the client names the bosses, without NPCs or levels), and {@code lootState}
 * {@value #ATLASLOOT} (AtlasLoot's loot tables) or {@value #LOOT_NONE} (no loot is known).
 */
public record InstanceSummary(
    long id, String name, String contentType, Integer levelMin, Integer levelMax, Long zoneId, String zoneName,
    String key, String source, Integer tunedMinLevel, String bossesState, String lootState) {
  public static final String ATLASLOOT = "atlasloot";
  public static final String CLIENT = "client";
  public static final String BOSSES_CLIENT_NAMED = "client_named";
  public static final String LOOT_NONE = "none";
  /** The {@code key} of a client dungeon is this prefix and its client map id: {@code client:3065}. */
  public static final String CLIENT_KEY_PREFIX = "client:";

  /** An AtlasLoot instance. */
  public InstanceSummary(
      long id, String name, String contentType, Integer levelMin, Integer levelMax, Long zoneId, String zoneName,
      String key) {
    this(id, name, contentType, levelMin, levelMax, zoneId, zoneName, key, ATLASLOOT, null, ATLASLOOT, ATLASLOOT);
  }

  /** An AtlasLoot instance without a known key. */
  public InstanceSummary(
      long id, String name, String contentType, Integer levelMin, Integer levelMax, Long zoneId, String zoneName) {
    this(id, name, contentType, levelMin, levelMax, zoneId, zoneName, null);
  }

  public EntityRef ref() {
    return new EntityRef(EntityKind.INSTANCE, id);
  }

  public String displayName() {
    return ref().displayName(name);
  }
}
