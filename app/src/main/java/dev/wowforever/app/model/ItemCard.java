package dev.wowforever.app.model;

import java.util.List;

/**
 * The client data of an item: quality (0 poor to 6 artifact), its tooltip lines in display order, icon,
 * inventory type, bonding and sell price in copper. Every field is null (and {@code lines} empty) for an item
 * without client data.
 */
public record ItemCard(
    Integer quality, List<TooltipLine> lines, Long iconId, Integer inventoryType, Integer bonding, Long sellPrice) {

  private static final ItemCard NONE = new ItemCard(null, List.of(), null, null, null, null);

  public ItemCard {
    lines = List.copyOf(lines);
  }

  /** The card of an item without client data. */
  public static ItemCard none() {
    return NONE;
  }
}
