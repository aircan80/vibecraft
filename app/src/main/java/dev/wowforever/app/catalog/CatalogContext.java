package dev.wowforever.app.catalog;

import java.util.Objects;

/** Immutable catalog identity: native IDs are meaningful only inside this scope. */
public record CatalogContext(String editionId, String projectionId, String clientBuild, String locale) {
  public CatalogContext {
    editionId = required(editionId); projectionId = required(projectionId);
    clientBuild = required(clientBuild); locale = required(locale);
  }
  private static String required(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Catalog identity is required");
    return value;
  }
  /** Use for cached pages, asset mappings and saved navigation, never a numeric ID alone. */
  public record Key<T>(CatalogContext context, T value) {
    public Key { Objects.requireNonNull(context); Objects.requireNonNull(value); }
  }
  public <T> Key<T> key(T value) { return new Key<>(this, value); }
}
