package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.ui.widgets.IconCache;
import java.util.HashMap;
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

  /** A bounded snapshot for instance pages; call off the EDT, including icon and rate-source warming. */
  static BossLoot preloaded(InstanceDetail detail, Repositories repos) {
    record Key(long boss, String difficulty) {}
    Map<Key, List<LootRow>> rows = new HashMap<>();
    Map<RateSource.Key, List<RateSource>> sources = new HashMap<>();
    for (var boss : detail.bosses()) {
      for (String difficulty : detail.difficulties()) {
        List<LootRow> loot = List.copyOf(repos.instances().bossLoot(boss.id(), difficulty));
        rows.put(new Key(boss.id(), difficulty), loot);
        IconCache.shared().warm(loot.stream().map(LootRow::iconId).toList(), IconCache.ROW);
        List<Long> rated = RateSource.ratedItems(loot);
        if (boss.npcId() != null && !rated.isEmpty()) {
          repos.items().sourcesOfNpc(boss.npcId(), rated)
              .forEach((key, value) -> sources.put(key, List.copyOf(value)));
        }
      }
    }
    Map<Key, List<LootRow>> snapshot = Map.copyOf(rows);
    Map<RateSource.Key, List<RateSource>> rateSnapshot = Map.copyOf(sources);
    return new BossLoot() {
      @Override
      public List<LootRow> lookup(long boss, String difficulty) {
        return snapshot.getOrDefault(new Key(boss, difficulty), List.of());
      }

      @Override
      public Map<RateSource.Key, List<RateSource>> sources(long npcId, List<Long> itemIds) {
        Map<RateSource.Key, List<RateSource>> selected = new HashMap<>();
        for (long item : itemIds) {
          RateSource.Key key = new RateSource.Key(npcId, item);
          if (rateSnapshot.containsKey(key)) selected.put(key, rateSnapshot.get(key));
        }
        return Map.copyOf(selected);
      }
    };
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
