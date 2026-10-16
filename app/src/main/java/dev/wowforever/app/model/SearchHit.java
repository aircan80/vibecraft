package dev.wowforever.app.model;

/** One full-text search result; {@code subtitle} is the kind label plus a short hint. */
public record SearchHit(EntityRef ref, String name, String subtitle) {
  public String displayName() {
    return ref.displayName(name);
  }
}
