package dev.wowforever.app.nav;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
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

  /** An item class/subclass listing. */
  record ItemClass(int classId, int subclassId) implements ViewTarget {}

  /** A full listing of one kind (all zones, all instances). */
  record List(EntityKind kind) implements ViewTarget {
    public List {
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** The landing page. */
  record Home() implements ViewTarget {}
}
