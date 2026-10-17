package dev.wowforever.app.model;

/**
 * An item in a loot, vendor or reward list. {@code dropPct}, {@code origin} and {@code slot} are null where
 * the list has no such notion; {@code itemName} is null for unknown or nameless items. {@code classId},
 * {@code className} ({@code item_class.name}: Weapon, Armor, Recipe, ...) and {@code subclassId} are null when
 * the item or its class is unknown. {@code sourceCount} is how many drop rate sources gave a rate and
 * {@code disagrees} whether they disagree on it (false and 0 for lists without drop rates).
 */
public record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
    Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount) {

  /** A row whose item class is unknown and without rate provenance. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot) {
    this(item, itemName, itemLevel, dropPct, origin, slot, null, null, null, false, 0);
  }

  /** A row with an item class but without rate provenance. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      Integer classId, String className, Integer subclassId) {
    this(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId, false, 0);
  }

  /** A row with rate provenance but whose item class is unknown. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      boolean disagrees, int sourceCount) {
    this(item, itemName, itemLevel, dropPct, origin, slot, null, null, null, disagrees, sourceCount);
  }

  public String displayName() {
    return item.displayName(itemName);
  }
}
