package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * One item's client data: Forever's {@code Item} row joined with an {@code ItemSparse} row, Forever's when it has
 * one, else the Era fallback's (ruling 17). Fields are null when the source cell is empty or holds the client's
 * "none" value (see {@link ItemSparseLoader}).
 *
 * @param hasSparse    an ItemSparse row was found (Forever or Era); false for an Item-only ("icon only") item,
 *                     whose sparse fields are all null and whose lists are empty
 * @param classId      {@code Item.ClassID}; null without an Item row (likewise subclass and icon)
 * @param inventoryType {@code ItemSparse.InventoryType}, or {@code Item.InventoryType} without a sparse row
 * @param maxStack     the stack size, {@code ItemSparse.Stackable} ({@code MaxCount} is the "Unique" count)
 * @param allowableRace {@code ItemSparse.AllowableRace_0}; the client splits the race mask into {@code _0} and
 *                     {@code _1}, and {@code _1} (races beyond the first 32 bits) is not used by Classic races
 * @param flags        {@code ItemSparse.Flags_0..4}, as 32-bit patterns
 * @param statShares   the slots of {@code StatModifier_bonusStat_n} that name a stat, with
 *                     {@code StatPercentEditor_n}; Era rows carry absolute amounts instead, so their shares are 0
 * @param clientSource {@link #FOREVER} or {@link #ERA} for the ItemSparse row's origin; null without one
 */
public record ClientItem(
    long id,
    boolean hasSparse,
    Integer quality,
    Integer itemLevel,
    Integer requiredLevel,
    Integer classId,
    Integer subclassId,
    Integer inventoryType,
    Integer bonding,
    Long sellPrice,
    Long buyPrice,
    Integer maxStack,
    Long iconId,
    Long itemSetId,
    String name,
    String description,
    Integer allowableClass,
    Long allowableRace,
    Integer requiredSkill,
    Integer requiredSkillRank,
    List<Integer> flags,
    Long startQuestId,
    List<StatShare> statShares,
    Integer delayMs,
    Double dmgVariance,
    Integer damageType,
    String clientSource) {

  public static final String FOREVER = "forever";
  public static final String ERA = "era";

  /** One stat slot: an ItemModType stat id and its budget share ({@code StatPercentEditor}). */
  public record StatShare(int statId, int share) {}

  public ClientItem {
    flags = flags == null ? List.of() : List.copyOf(flags);
    statShares = statShares == null ? List.of() : List.copyOf(statShares);
  }
}
