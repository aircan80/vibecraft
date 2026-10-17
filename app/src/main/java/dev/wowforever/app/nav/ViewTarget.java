package dev.wowforever.app.nav;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ListFilter;
import java.util.Objects;
import java.util.Optional;

/** Something the browser can show. Records give structural equality, used by {@link NavigationHistory}. */
public sealed interface ViewTarget {

  /** The detail page of one entity. */
  record Entity(EntityRef ref) implements ViewTarget {
    public Entity {
      Objects.requireNonNull(ref, "ref");
    }
  }

  /** A search, optionally restricted to one entity kind. */
  record Search(String text, Optional<EntityKind> kind) implements ViewTarget {
    public Search {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(kind, "kind");
    }
  }

  /**
   * A list page of one kind ({@link EntityKind#ITEM}, {@code NPC}, {@code QUEST}, {@code ZONE} or {@code INSTANCE})
   * narrowed by {@code filter}, which must {@linkplain ListFilter#fits fit} the kind.
   */
  record List(EntityKind kind, ListFilter filter) implements ViewTarget {
    public List {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(filter, "filter");
      if (!filter.fits(kind)) {
        throw new IllegalArgumentException("A " + kind.label() + " list cannot be filtered by " + filter);
      }
    }

    /** The whole list of {@code kind}, with its {@linkplain ListFilter#defaultFor default filter}. */
    public List(EntityKind kind) {
      this(kind, ListFilter.defaultFor(kind));
    }

    /** The item list of a v0.1 item class link ({@link ItemFilter#ofClass}), the class preselected. */
    public static List itemClass(int classId, int subclassId) {
      return new List(EntityKind.ITEM, ItemFilter.ofClass(classId, subclassId));
    }
  }

  /** A geographical map; the selected region is restored when returning from a zone. */
  record WorldMap(long uiMapId, Long focusedChildId) implements ViewTarget {
    public WorldMap(long uiMapId) { this(uiMapId, null); }
  }

  /** The landing page. */
  record Home() implements ViewTarget {}
}
