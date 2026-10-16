package dev.wowforever.app.model;

/**
 * An item in a loot, vendor or reward list. {@code dropPct}, {@code origin} and {@code slot} are null where
 * the list has no such notion; {@code itemName} is null for unknown or nameless items. {@code classId},
 * {@code className} ({@code item_class.name}: Weapon, Armor, Recipe, ...) and {@code subclassId} are null when
 * the item or its class is unknown.
 */
public record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
    Integer classId, String className, Integer subclassId) {

  /** A row whose item class is unknown. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot) {
    this(item, itemName, itemLevel, dropPct, origin, slot, null, null, null);
  }

  public String displayName() {
    return item.displayName(itemName);
  }
}
