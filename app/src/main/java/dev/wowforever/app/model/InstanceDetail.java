package dev.wowforever.app.model;

import java.util.List;

/**
 * An instance with its bosses in AtlasLoot order (main list first, then extra lists) and the difficulties its boss
 * loot comes in ({@code boss_loot.difficulty}: NORMAL first, then the others by name; empty without boss loot).
 */
public record InstanceDetail(InstanceSummary summary, List<BossSummary> bosses, List<String> difficulties) {
  public InstanceDetail {
    bosses = List.copyOf(bosses);
    difficulties = List.copyOf(difficulties);
  }

  /** An instance whose boss loot has no known difficulty. */
  public InstanceDetail(InstanceSummary summary, List<BossSummary> bosses) {
    this(summary, bosses, List.of());
  }
}
