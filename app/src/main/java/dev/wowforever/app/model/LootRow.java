package dev.wowforever.app.model;

/**
 * An item in a loot, vendor or reward list. {@code dropPct}, {@code origin} and {@code slot} are null where
 * the list has no such notion; {@code itemName} is null for unknown or nameless items. {@code classId},
 * {@code className} ({@code item_class.name}: Weapon, Armor, Recipe, ...) and {@code subclassId} are null when
 * the item or its class is unknown. {@code sourceCount} is how many drop rate sources gave a rate and
 * {@code disagrees} whether they disagree on it (false and 0 for lists without drop rates). {@code quality} and
 * {@code iconId} come from the client data and are null for items without it, as are {@code skillLine} (the
 * tooltip's required-skill line, {@code "Requires Leatherworking (65)"}, null when the item has none) and
 * {@code allowableClass} (the client's class mask; null, -1 and every class mean unrestricted). {@code dropperCount}
 * is how many NPCs drop the item, filled on NPC drop lists only (0 elsewhere). {@code pctSource} is the source of the
 * headline rate ({@code pct_source}: {@code questie-wowhead}, {@code vmangos}, ...), null without one or for lists
 * without drop rates.
 */
public record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
    Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount, Integer quality,
    Long iconId, String skillLine, Integer allowableClass, int dropperCount, String pctSource, boolean observedInGame) {

  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount, Integer quality,
      Long iconId, String skillLine, Integer allowableClass, int dropperCount, String pctSource) {
    this(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId, disagrees, sourceCount,
        quality, iconId, skillLine, allowableClass, dropperCount, pctSource, false);
  }

  public String displayOrigin() { return observedInGame ? "collector" : origin; }

  public LootRow withObservation(boolean observed) {
    return new LootRow(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId,
        disagrees, sourceCount, quality, iconId, skillLine, allowableClass, dropperCount, pctSource, observed);
  }

  /** A row without a headline rate source. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount, Integer quality,
      Long iconId, String skillLine, Integer allowableClass, int dropperCount) {
    this(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId, disagrees, sourceCount,
        quality, iconId, skillLine, allowableClass, dropperCount, null);
  }

  /** A row without a required skill, class restriction or dropper count. */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount, Integer quality,
      Long iconId) {
    this(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId, disagrees, sourceCount,
        quality, iconId, null, null, 0);
  }

  /** A row without client data (quality and icon). */
  public LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot,
      Integer classId, String className, Integer subclassId, boolean disagrees, int sourceCount) {
    this(item, itemName, itemLevel, dropPct, origin, slot, classId, className, subclassId, disagrees, sourceCount,
        null, null);
  }

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
