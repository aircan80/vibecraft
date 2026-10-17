package dev.wowforever.app.model;

import java.util.Objects;

/**
 * One full-text search result; {@code subtitle} is a short line of facts in words. {@code count} is how many
 * hits of the same kind and name this one stands for (1 unless several were collapsed into it). {@code quality}
 * is the item quality of an item hit with client data and {@code iconId} its icon, null otherwise.
 */
public record SearchHit(EntityRef ref, String name, String subtitle, int count, Integer quality, Long iconId) {
  public SearchHit {
    Objects.requireNonNull(ref, "ref");
    if (count < 1) {
      throw new IllegalArgumentException("count must be at least 1: " + count);
    }
  }

  /** A hit standing for {@code count} hits, with an item quality (null when unknown) but no icon. */
  public SearchHit(EntityRef ref, String name, String subtitle, int count, Integer quality) {
    this(ref, name, subtitle, count, quality, null);
  }

  /** A hit that stands for itself only, without an item quality. */
  public SearchHit(EntityRef ref, String name, String subtitle) {
    this(ref, name, subtitle, 1, null);
  }

  /** A hit standing for {@code count} hits, without an item quality. */
  public SearchHit(EntityRef ref, String name, String subtitle, int count) {
    this(ref, name, subtitle, count, null);
  }

  /** A hit that stands for itself only, with an item quality (null when unknown). */
  public SearchHit(EntityRef ref, String name, String subtitle, Integer quality) {
    this(ref, name, subtitle, 1, quality);
  }

  /** This hit standing for {@code count} hits. */
  public SearchHit withCount(int count) {
    return new SearchHit(ref, name, subtitle, count, quality, iconId);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
