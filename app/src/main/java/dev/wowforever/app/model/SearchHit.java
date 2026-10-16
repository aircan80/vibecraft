package dev.wowforever.app.model;

import java.util.Objects;

/**
 * One full-text search result; {@code subtitle} is a short line of facts in words. {@code count} is how many
 * hits of the same kind and name this one stands for (1 unless several were collapsed into it).
 */
public record SearchHit(EntityRef ref, String name, String subtitle, int count) {
  public SearchHit {
    Objects.requireNonNull(ref, "ref");
    if (count < 1) {
      throw new IllegalArgumentException("count must be at least 1: " + count);
    }
  }

  /** A hit that stands for itself only. */
  public SearchHit(EntityRef ref, String name, String subtitle) {
    this(ref, name, subtitle, 1);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
