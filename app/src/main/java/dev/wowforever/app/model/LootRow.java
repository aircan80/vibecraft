package dev.wowforever.app.model;

/**
 * An item in a loot, vendor or reward list. {@code dropPct}, {@code origin} and {@code slot} are null where
 * the list has no such notion; {@code itemName} is null for unknown or nameless items. {@code sourceCount} is
 * how many drop rate sources gave a rate and {@code disagrees} whether they disagree on it (false and 0 for
 * lists without drop rates).
 */
public record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
    boolean disagrees, int sourceCount) {

  /** A row without rate provenance. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot) {
    this(item, itemName, itemLevel, dropPct, origin, slot, false, 0);
  }

  public String displayName() {
    return item.displayName(itemName);
  }
}
