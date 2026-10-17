package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import java.util.List;
import java.util.Map;

/**
 * Where the instance page reads the loot of a boss in one difficulty, and the drop rate sources of its rates: the
 * repositories in the app ({@link #of}), a fake in tests.
 */
@FunctionalInterface
public interface BossLoot {
  /** The loot of boss {@code bossId} in {@code difficulty} (a {@code boss_loot.difficulty} value), in slot order. */
  List<LootRow> lookup(long bossId, String difficulty);

  /**
   * The drop rate sources of NPC {@code npcId}'s drops of {@code itemIds}, by pair, in one query
   * ({@link dev.wowforever.app.db.ItemRepository#sourcesOfNpc}); none unless overridden.
   */
  default Map<RateSource.Key, List<RateSource>> sources(long npcId, List<Long> itemIds) {
    return Map.of();
  }

  /** The boss loot and drop rate sources of {@code repos}. */
  static BossLoot of(Repositories repos) {
    return new BossLoot() {
      @Override
      public List<LootRow> lookup(long bossId, String difficulty) {
        return repos.instances().bossLoot(bossId, difficulty);
      }

      @Override
      public Map<RateSource.Key, List<RateSource>> sources(long npcId, List<Long> itemIds) {
        return repos.items().sourcesOfNpc(npcId, itemIds);
      }
    };
  }
}
