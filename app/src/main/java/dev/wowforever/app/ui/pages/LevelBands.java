package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.InstanceSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Instances grouped by the band their lowest level falls in: 1–20, 21–40, 41–59, 60 and above, then those whose
 * lowest level is unknown (or below 1). Each band lists its instances by lowest level, then name; empty bands are
 * left out.
 */
final class LevelBands {
  private static final String[] TITLES = {"Levels 1–20", "Levels 21–40", "Levels 41–59", "Level 60", "Level unknown"};
  private static final int UNKNOWN = TITLES.length - 1;

  private LevelBands() {}

  /** One band: its heading and its instances. */
  record Band(String title, List<InstanceSummary> instances) {
    Band {
      instances = List.copyOf(instances);
    }
  }

  /** The non-empty bands of {@code instances}, lowest first, the unknown band last. */
  static List<Band> of(List<InstanceSummary> instances) {
    List<List<InstanceSummary>> bands = new ArrayList<>();
    for (int i = 0; i < TITLES.length; i++) {
      bands.add(new ArrayList<>());
    }
    instances.stream()
        .sorted(Comparator.comparing((InstanceSummary i) -> knownLevel(i.levelMin()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InstanceSummary::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingLong(InstanceSummary::id))
        .forEach(i -> bands.get(band(knownLevel(i.levelMin()))).add(i));
    List<Band> out = new ArrayList<>();
    for (int i = 0; i < TITLES.length; i++) {
      if (!bands.get(i).isEmpty()) {
        out.add(new Band(TITLES[i], bands.get(i)));
      }
    }
    return out;
  }

  private static Integer knownLevel(Integer level) {
    return level != null && level >= 1 ? level : null;
  }

  private static int band(Integer level) {
    if (level == null) {
      return UNKNOWN;
    }
    if (level <= 20) {
      return 0;
    }
    if (level <= 40) {
      return 1;
    }
    return level <= 59 ? 2 : 3;
  }
}
