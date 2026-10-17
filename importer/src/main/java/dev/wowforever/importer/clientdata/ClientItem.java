package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * One item's client data: an {@code Item} row joined with an {@code ItemSparse} row, the hotfix overlay's when it
 * has one (Task 19), else Forever's, else the Era fallback's (ruling 17). Fields are null when the source cell is empty or holds the client's
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
 * @param clientSource {@link #HOTFIX}, {@link #FOREVER} or {@link #ERA} for the ItemSparse row's origin; null
 *                     without one
 * @param absolute     the Era row's absolute stat, damage, armour and resistance values; null for a Forever row
 *                     (whose values are computed from the budget tables) and without a sparse row
 * @param details      the remaining tooltip inputs; {@link Details#NONE} without a sparse row, never null
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
    String clientSource,
    Absolute absolute,
    Details details) {

  /** A row of the hotfix overlay (Task 19): Forever data revealed by a Blizzard hotfix, precedence over Forever's. */
  public static final String HOTFIX = "hotfix";
  public static final String FOREVER = "forever";
  public static final String ERA = "era";

  /** One stat slot: an ItemModType stat id and its budget share ({@code StatPercentEditor}). */
  public record StatShare(int statId, int share) {}

  /** An absolute stat amount ({@code StatModifier_bonusStat_n} with {@code StatModifier_bonusAmount_n}). */
  public record StatValue(int statId, int value) {}

  /**
   * The absolute values an Era {@code ItemSparse} row carries (Forever rows carry budget shares instead).
   *
   * @param stats        the stat slots with a non-zero amount, in slot order
   * @param minDamage    {@code MinDamage_0}; null when {@code MaxDamage_0} is 0 (no weapon damage)
   * @param maxDamage    {@code MaxDamage_0}; null likewise
   * @param damageSlots  how many of {@code MaxDamage_0..4} are non-zero (a second slot is bonus elemental damage)
   * @param armor        {@code Resistances_0}, the armour value (0 for none)
   * @param resistances  {@code Resistances_1..6}: holy, fire, nature, frost, shadow, arcane (0 for none)
   * @param extraMinDamage {@code MinDamage_1}, the second (bonus elemental) damage range; null when
   *                     {@code MaxDamage_1} is 0. Its school is not in the client data ({@code DamageType} is one
   *                     column, the first range's)
   * @param extraMaxDamage {@code MaxDamage_1}; null likewise
   */
  public record Absolute(List<StatValue> stats, Integer minDamage, Integer maxDamage, int damageSlots, int armor,
      List<Integer> resistances, Integer extraMinDamage, Integer extraMaxDamage) {
    public Absolute {
      stats = stats == null ? List.of() : List.copyOf(stats);
      resistances = resistances == null ? List.of() : List.copyOf(resistances);
    }
  }

  /**
   * The {@code ItemSparse} columns only the tooltip reads. Null means the client's "none" (0 or an empty cell),
   * except {@code allowableRaceHigh}, which is the raw cell.
   *
   * @param maxCount           {@code MaxCount}: 1 is "Unique", more is "Unique (n)"
   * @param maxDurability      {@code MaxDurability}; only the Era table has the column, so null for Forever rows
   * @param containerSlots     {@code ContainerSlots}, a bag's slot count
   * @param minFactionId       {@code MinFactionID}, the faction a reputation requirement names
   * @param minReputation      {@code MinReputation}, the standing (0 Hated .. 7 Exalted); null without a faction
   * @param durationInInventory {@code DurationInInventory}, seconds
   * @param allowableRaceHigh  {@code AllowableRace_1}: race bits 32 to 63 (Forever's Skyborne races use 32 and 33)
   */
  public record Details(Integer maxCount, Integer maxDurability, Integer containerSlots, Integer minFactionId,
      Integer minReputation, Integer durationInInventory, Long allowableRaceHigh) {
    public static final Details NONE = new Details(null, null, null, null, null, null, null);
  }

  public ClientItem {
    flags = flags == null ? List.of() : List.copyOf(flags);
    statShares = statShares == null ? List.of() : List.copyOf(statShares);
    details = details == null ? Details.NONE : details;
  }
}
