package dev.wowforever.app.model;

/**
 * A quest list: quests filed under zone {@code zoneId} whose quest level is at least {@code minLevel} and at most
 * {@code maxLevel}. Null leaves that part open; a quest without a known level (null, or 0 and below) is left out
 * once either bound is set.
 */
public record QuestFilter(Long zoneId, Integer minLevel, Integer maxLevel) implements ListFilter {
  /** Every quest. */
  public static final QuestFilter ALL = new QuestFilter(null, null, null);

  /** This filter with another zone. */
  public QuestFilter withZone(Long zoneId) {
    return new QuestFilter(zoneId, minLevel, maxLevel);
  }

  /** This filter with other level bounds. */
  public QuestFilter withLevels(Integer minLevel, Integer maxLevel) {
    return new QuestFilter(zoneId, minLevel, maxLevel);
  }
}
