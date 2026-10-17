package dev.wowforever.app.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The level band a zone is for, as a player reads it ({@code "Level 10–20"}): the 10th to the 90th percentile of the
 * levels of the NPCs met there, the low end rounded down to a multiple of 5 (at least 1) and the high end to the
 * nearest multiple of 5 (at least the low end). Elwynn Forest reads 1–10, Westfall 10–20: the guards, the boss
 * outliers and the odd high-level visitor fall outside the percentiles.
 */
public record LevelBand(int min, int max) {
  /** The lower percentile. */
  public static final double LOW = 0.10;
  /** The upper percentile. */
  public static final double HIGH = 0.90;
  private static final int STEP = 5;

  /** One NPC's level (the middle of its range) and how often it is met there (its spawn points; 0: never). */
  public record Weighted(double level, int weight) {}

  /** The band of {@code levels}; empty when their weights add up to nothing. */
  public static Optional<LevelBand> of(List<Weighted> levels) {
    List<Weighted> sorted = new ArrayList<>(levels.stream().filter(w -> w.weight() > 0).toList());
    sorted.sort(Comparator.comparingDouble(Weighted::level));
    long total = sorted.stream().mapToLong(Weighted::weight).sum();
    if (total == 0) {
      return Optional.empty();
    }
    int low = Math.max(1, (int) Math.floor(percentile(sorted, total, LOW) / STEP) * STEP);
    int high = Math.max(low, (int) Math.round(percentile(sorted, total, HIGH) / STEP) * STEP);
    return Optional.of(new LevelBand(low, high));
  }

  /**
   * The {@code p} percentile of the levels, each repeated by its weight ({@code total} in all), interpolated between
   * the two nearest ranks.
   */
  private static double percentile(List<Weighted> sorted, long total, double p) {
    double rank = (total - 1) * p;
    long below = (long) Math.floor(rank);
    double a = levelAt(sorted, below);
    double b = levelAt(sorted, Math.min(below + 1, total - 1));
    return a + (b - a) * (rank - below);
  }

  /** The level at {@code index} of the levels repeated by their weights. */
  private static double levelAt(List<Weighted> sorted, long index) {
    long seen = 0;
    for (Weighted w : sorted) {
      seen += w.weight();
      if (index < seen) {
        return w.level();
      }
    }
    return sorted.get(sorted.size() - 1).level();
  }
}
