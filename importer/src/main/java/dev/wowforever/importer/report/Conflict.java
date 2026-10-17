package dev.wowforever.importer.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One (npc, item) pair whose drop-rate sources disagree; {@code values} is source label to percent. */
public record Conflict(long npcId, long itemId, Map<String, Double> values) {
  public Conflict {
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /** The spread between the highest and lowest source value, in percentage points. */
  public double delta() {
    if (values.isEmpty()) {
      return 0;
    }
    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    for (double v : values.values()) {
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    return max - min;
  }
}
