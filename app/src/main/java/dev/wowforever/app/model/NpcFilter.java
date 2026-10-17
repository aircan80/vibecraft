package dev.wowforever.app.model;

/**
 * An NPC list: NPCs of zone {@code zoneId} and raw rank {@code rank} whose level range overlaps
 * {@code minLevel}..{@code maxLevel}. Null leaves that part open; an NPC without a level is left out once either
 * level bound is set.
 */
public record NpcFilter(Long zoneId, Integer rank, Integer minLevel, Integer maxLevel) implements ListFilter {
  /** Every NPC. */
  public static final NpcFilter ALL = new NpcFilter(null, null, null, null);

  /** This filter with another zone. */
  public NpcFilter withZone(Long zoneId) {
    return new NpcFilter(zoneId, rank, minLevel, maxLevel);
  }

  /** This filter with another rank. */
  public NpcFilter withRank(Integer rank) {
    return new NpcFilter(zoneId, rank, minLevel, maxLevel);
  }

  /** This filter with other level bounds. */
  public NpcFilter withLevels(Integer minLevel, Integer maxLevel) {
    return new NpcFilter(zoneId, rank, minLevel, maxLevel);
  }
}
