package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * The crafts of a client build ({@link CraftLoader}): {@code crafts} by spell id, {@code reagents} by spell id and
 * then in their {@code SpellReagents} slot order.
 */
public record Crafts(List<Craft> crafts, List<CraftReagent> reagents) {
  public static final Crafts EMPTY = new Crafts(List.of(), List.of());

  public Crafts {
    crafts = crafts == null ? List.of() : List.copyOf(crafts);
    reagents = reagents == null ? List.of() : List.copyOf(reagents);
  }

  /** The reagents of {@code spellId} in slot order; empty when it has none. */
  public List<CraftReagent> reagentsOf(long spellId) {
    return reagents.stream().filter(r -> r.spellId() == spellId).toList();
  }
}
