package dev.wowforever.app.model;

import java.util.Objects;

/**
 * What a list page of one kind is narrowed to. Filters are values (records), so two list targets with equal
 * filters are the same history entry.
 */
public sealed interface ListFilter permits ItemFilter, NpcFilter, QuestFilter, NoFilter {

  /** The filter a list of {@code kind} opens with: everything. Only items, NPCs, quests, zones and instances list. */
  static ListFilter defaultFor(EntityKind kind) {
    Objects.requireNonNull(kind, "kind");
    return switch (kind) {
      case ITEM -> ItemFilter.ALL;
      case NPC -> NpcFilter.ALL;
      case QUEST -> QuestFilter.ALL;
      case ZONE, INSTANCE -> NoFilter.INSTANCE;
      default -> throw new IllegalArgumentException("There is no list of " + kind.label() + " entities");
    };
  }

  /** Whether this filter narrows a list of {@code kind} (an item filter fits items only, and so on). */
  default boolean fits(EntityKind kind) {
    return defaultFor(kind).getClass() == getClass();
  }
}
