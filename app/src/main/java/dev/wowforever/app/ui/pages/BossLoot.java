package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import java.util.List;

/**
 * Where the instance page reads the loot of a boss in one difficulty: the repository in the app
 * ({@code repos.instances()::bossLoot}), a fake in tests.
 */
@FunctionalInterface
public interface BossLoot {
  /** The loot of boss {@code bossId} in {@code difficulty} (a {@code boss_loot.difficulty} value), in slot order. */
  List<LootRow> lookup(long bossId, String difficulty);
}
