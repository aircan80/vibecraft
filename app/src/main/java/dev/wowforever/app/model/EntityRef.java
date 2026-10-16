package dev.wowforever.app.model;

import java.util.Objects;

/** A reference to one entity: its kind and id. */
public record EntityRef(EntityKind kind, long id) {
  public EntityRef {
    Objects.requireNonNull(kind, "kind");
  }

  /** {@code name} when present, else {@code "Unknown <kind> #<id>"}. */
  public String displayName(String name) {
    return name != null ? name : kind.unknownName(id);
  }
}
