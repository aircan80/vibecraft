package dev.wowforever.app.model;

import java.util.Optional;

/** The kinds of entity the browser can show. */
public enum EntityKind {
  ITEM("item", "Item", "item"),
  NPC("npc", "NPC", "NPC"),
  OBJECT("object", "Object", "object"),
  QUEST("quest", "Quest", "quest"),
  ZONE("zone", "Zone", "zone"),
  INSTANCE("instance", "Instance", "instance"),
  BOSS(null, "Boss", "boss");

  private final String dbKind;
  private final String label;
  private final String noun;

  EntityKind(String dbKind, String label, String noun) {
    this.dbKind = dbKind;
    this.label = label;
    this.noun = noun;
  }

  /** The {@code kind} value of this entity in {@code search_index}, or {@code null} for {@link #BOSS}. */
  public String dbKind() {
    return dbKind;
  }

  /** Capitalised label for display, e.g. {@code "Item"}, {@code "NPC"}. */
  public String label() {
    return label;
  }

  /** Name shown for an entity whose name is unknown, e.g. {@code "Unknown NPC #185333"}. */
  public String unknownName(long id) {
    return "Unknown " + noun + " #" + id;
  }

  /** The kind whose {@link #dbKind()} is {@code dbKind}. */
  public static Optional<EntityKind> fromDbKind(String dbKind) {
    for (EntityKind k : values()) {
      if (k.dbKind != null && k.dbKind.equals(dbKind)) {
        return Optional.of(k);
      }
    }
    return Optional.empty();
  }
}
