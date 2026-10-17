package dev.wowforever.importer.serverdb;

/**
 * One row of a VMaNGOS loot template ({@code creature_loot_template} or {@code reference_loot_template}).
 *
 * @param item          the item id; on a reference row an arbitrary id (the reference is {@code -mincountOrRef})
 * @param chance        {@code ChanceOrQuestChance}: a percentage; negative for a quest-only drop; 0 inside a group
 *                      for an equal share of what the group's explicit chances leave
 * @param group         {@code groupid}: 0 rolls independently, other values roll one row per group
 * @param mincountOrRef the minimum stack size, or the negated {@code reference_loot_template} entry
 * @param maxcount      the maximum stack size, or on a reference row how many times the reference is rolled
 */
public record LootRow(long item, double chance, int group, int mincountOrRef, int maxcount) {
  public boolean isReference() {
    return mincountOrRef < 0;
  }

  public long reference() {
    return -(long) mincountOrRef;
  }
}
