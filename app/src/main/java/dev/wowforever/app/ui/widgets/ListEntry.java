package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityRef;
import java.util.Objects;

/**
 * One row of the {@link MasterList}: the entity it opens, the name on line 1 and the kind and key facts on line 2.
 * {@code quality} (item quality 0–7) and {@code iconId} are null when unknown.
 */
public record ListEntry(EntityRef ref, String title, String subtitle, Integer quality, Long iconId) {
  public ListEntry {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(title, "title");
    subtitle = subtitle == null ? "" : subtitle;
  }

  /** The title: what type-ahead in the list and screen readers go by. */
  @Override
  public String toString() {
    return title;
  }
}
