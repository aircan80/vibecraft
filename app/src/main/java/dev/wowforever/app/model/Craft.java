package dev.wowforever.app.model;

import java.util.List;

/**
 * A crafting spell from the client data: it creates {@code quantityMin} to {@code quantityMax} of item {@code itemId}
 * from {@code reagents} (in the client's slot order). The skill fields are null when the client lists no skill for
 * the spell. {@code recipeItemIds} are every item that teaches it, in id order (empty when none does), and
 * {@code recipeItemId} is the first of them, null when there is none. {@code itemName}, {@code itemQuality},
 * {@code itemIconId} and {@code recipeItemName} (the name of {@code recipeItemId}) come from the item table and are
 * null when the item is unknown there.
 *
 * @param minSkill    the skill rank needed to learn it, as the client lists it
 * @param trivialLow  the rank from which it stops being certain to raise the skill
 * @param trivialHigh the rank from which it no longer raises the skill
 */
public record Craft(long spellId, String spellName, long itemId, String itemName, Integer itemQuality,
    Long itemIconId, int quantityMin, int quantityMax, Long skillLineId, String skillName, Integer minSkill,
    Integer trivialLow, Integer trivialHigh, Long recipeItemId, String recipeItemName, List<Long> recipeItemIds,
    List<CraftReagent> reagents) {

  public Craft {
    recipeItemIds = List.copyOf(recipeItemIds);
    reagents = List.copyOf(reagents);
  }

  /** The created item. */
  public EntityRef item() {
    return new EntityRef(EntityKind.ITEM, itemId);
  }

  /** The recipe item, or null when no item teaches the craft. */
  public EntityRef recipe() {
    return recipeItemId == null ? null : new EntityRef(EntityKind.ITEM, recipeItemId);
  }

  /** {@code "2"}, or {@code "1-3"} when the yield varies. */
  public String quantityText() {
    return quantityMin == quantityMax ? String.valueOf(quantityMin) : quantityMin + "-" + quantityMax;
  }
}
