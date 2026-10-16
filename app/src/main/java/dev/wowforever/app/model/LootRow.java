package dev.wowforever.app.model;

/**
 * An item in a loot, vendor or reward list. {@code dropPct}, {@code origin} and {@code slot} are null where
 * the list has no such notion; {@code itemName} is null for unknown or nameless items.
 */
public record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot) {
  public String displayName() {
    return item.displayName(itemName);
  }
}
