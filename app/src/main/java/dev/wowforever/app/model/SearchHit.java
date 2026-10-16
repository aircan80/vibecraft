package dev.wowforever.app.model;

/**
 * One full-text search result; {@code subtitle} is the kind label plus a short hint. {@code quality} is the item
 * quality of an item hit with client data, null otherwise.
 */
public record SearchHit(EntityRef ref, String name, String subtitle, Integer quality) {
  /** A hit without an item quality. */
  public SearchHit(EntityRef ref, String name, String subtitle) {
    this(ref, name, subtitle, null);
  }

  public String displayName() {
    return ref.displayName(name);
  }
}
