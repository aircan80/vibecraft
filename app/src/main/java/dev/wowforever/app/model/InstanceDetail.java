package dev.wowforever.app.model;

import java.util.List;

/** An instance with its bosses in AtlasLoot order (main list first, then extra lists). */
public record InstanceDetail(InstanceSummary summary, List<BossSummary> bosses) {
  public InstanceDetail {
    bosses = List.copyOf(bosses);
  }
}
