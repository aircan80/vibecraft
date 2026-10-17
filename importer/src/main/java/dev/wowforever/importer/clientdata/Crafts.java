package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * The crafts of a client build ({@link CraftLoader}): {@code crafts} by spell id, {@code reagents} by spell id and
 * then in their {@code SpellReagents} slot order, {@code recipeItems} (every item teaching each craft) by spell id and
 * then item id.
 */
public record Crafts(List<Craft> crafts, List<CraftReagent> reagents, List<CraftRecipeItem> recipeItems) {
  public static final Crafts EMPTY = new Crafts(List.of(), List.of(), List.of());

  public Crafts {
    crafts = crafts == null ? List.of() : List.copyOf(crafts);
    reagents = reagents == null ? List.of() : List.copyOf(reagents);
    recipeItems = recipeItems == null ? List.of() : List.copyOf(recipeItems);
  }

  /** Crafts without recipe items. */
  public Crafts(List<Craft> crafts, List<CraftReagent> reagents) {
    this(crafts, reagents, List.of());
  }

  /** The reagents of {@code spellId} in slot order; empty when it has none. */
  public List<CraftReagent> reagentsOf(long spellId) {
    return reagents.stream().filter(r -> r.spellId() == spellId).toList();
  }
}
