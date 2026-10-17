package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import java.util.Collection;
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

  /** The drop rate sources of each of {@code pairs} that has any; none unless overridden. */
  default Map<RateSource.Key, List<RateSource>> sources(Collection<RateSource.Key> pairs) {
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
      public Map<RateSource.Key, List<RateSource>> sources(Collection<RateSource.Key> pairs) {
        return repos.items().sources(pairs);
      }
    };
  }
}
