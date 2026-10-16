package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.clientdata.ScalingTables.DamageKind;
import dev.wowforever.importer.report.ImportReport;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * An item's weapon damage.
 *
 * <p>Forever rows: {@code dps = ItemDamage<Kind>[ItemLevel].Quality_<q>}; {@code avg = dps * delay / 1000}
 * ({@code avg = dps} for ammunition); {@code min = max(1, floor(avg * (1 - variance / 2)))},
 * {@code max = round(avg * (1 + variance / 2))} half up. The half-up maximum (not the floor the spec first assumed)
 * is what the Era overlap shows: Worn Shortsword 1 - 3, Thunderfury 82 - 153.
 *
 * <p>Kind, from class, subclass and inventory type:
 *
 * <pre>
 *   class 6 (projectile)                          AMMO
 *   class 2, subclass 19 (wand)                   WAND
 *   class 2, subclass 16 or inventory type 25     THROWN
 *   class 2, subclass 2, 3, 18 or inv. 15, 26     RANGED (bow, gun, crossbow)
 *   class 2, inventory type 17                    TWO_HAND, TWO_HAND_CASTER with Flags_1 0x200
 *   class 2, inventory type 13, 21, 22            ONE_HAND, ONE_HAND_CASTER with Flags_1 0x200
 * </pre>
 *
 * (Forever's caster tables equal the plain ones in build 1.60.1.70425.)
 *
 * <p>Era rows (ruling 17): the absolute {@code MinDamage_0}/{@code MaxDamage_0}; a second damage range (bonus
 * elemental damage) has no place in {@code item_damage} and counts {@code clientdata.damage.secondaryDropped}.
 *
 * <p>Unverified, counted as {@code clientdata.unverified.damage} and left empty: a Forever weapon of no known kind,
 * without a delay or without a table row, and a Forever caster weapon ({@code Flags_4} bit 0x100), whose damage
 * Wowhead shows about a quarter (two-hand) to a third (one-hand) below the tables with an added spell power line that
 * no client table explains.
 */
public final class ItemDamageCalculator {
  public static final String UNVERIFIED = "clientdata.unverified.damage";
  public static final String SECONDARY_DROPPED = "clientdata.damage.secondaryDropped";
  static final int CASTER_FLAG_1 = 0x200;
  static final int FOREVER_CASTER_FLAG_4 = 0x100;
  private static final int WEAPON = 2;
  private static final int PROJECTILE = 6;

  private ItemDamageCalculator() {}

  public static Optional<ItemDamage> damage(ClientItem item, ScalingTables tables, ImportReport report) {
    if (!item.hasSparse()) {
      return Optional.empty();
    }
    int delay = item.delayMs() == null ? 0 : item.delayMs();
    int school = item.damageType() == null ? 0 : item.damageType();
    if (item.absolute() != null) {
      ClientItem.Absolute a = item.absolute();
      if (a.maxDamage() == null) {
        return Optional.empty();
      }
      if (a.damageSlots() > 1) {
        report.count(SECONDARY_DROPPED, 1);
      }
      boolean ammo = item.classId() != null && item.classId() == PROJECTILE;
      return Optional.of(of(a.minDamage(), a.maxDamage(), school, ammo ? 0 : delay));
    }
    Integer cls = item.classId();
    if (cls == null || (cls != WEAPON && cls != PROJECTILE)) {
      return Optional.empty();
    }
    Optional<DamageKind> kind = kind(item);
    boolean ammo = kind.isPresent() && kind.get() == DamageKind.AMMO;
    if (kind.isEmpty() || (!ammo && delay <= 0) || isForeverCaster(item) || item.itemLevel() == null
        || item.quality() == null) {
      report.count(UNVERIFIED, 1);
      return Optional.empty();
    }
    OptionalDouble dps = tables.dps(kind.get(), item.itemLevel(), item.quality());
    if (dps.isEmpty()) {
      report.count(UNVERIFIED, 1);
      return Optional.empty();
    }
    double avg = ammo ? dps.getAsDouble() : dps.getAsDouble() * delay / 1000.0;
    double variance = item.dmgVariance() == null ? 0 : item.dmgVariance();
    int min = Math.max(1, (int) Math.floor(avg * (1 - variance / 2)));
    int max = Math.max(min, ScalingTables.roundHalfUp(avg * (1 + variance / 2)));
    return Optional.of(of(min, max, school, ammo ? 0 : delay));
  }

  /** The DPS table of a weapon or projectile (see the class comment), empty for anything else. */
  public static Optional<DamageKind> kind(ClientItem item) {
    Integer cls = item.classId();
    int sub = item.subclassId() == null ? -1 : item.subclassId();
    int inv = item.inventoryType() == null ? 0 : item.inventoryType();
    if (cls == null) {
      return Optional.empty();
    }
    if (cls == PROJECTILE) {
      return Optional.of(DamageKind.AMMO);
    }
    if (cls != WEAPON) {
      return Optional.empty();
    }
    boolean caster = (flag(item, 1) & CASTER_FLAG_1) != 0;
    if (sub == 19) {
      return Optional.of(DamageKind.WAND);
    }
    if (sub == 16 || inv == 25) {
      return Optional.of(DamageKind.THROWN);
    }
    if (sub == 2 || sub == 3 || sub == 18 || inv == 15 || inv == 26) {
      return Optional.of(DamageKind.RANGED);
    }
    if (inv == 17) {
      return Optional.of(caster ? DamageKind.TWO_HAND_CASTER : DamageKind.TWO_HAND);
    }
    if (inv == 13 || inv == 21 || inv == 22) {
      return Optional.of(caster ? DamageKind.ONE_HAND_CASTER : DamageKind.ONE_HAND);
    }
    return Optional.empty();
  }

  /** Forever's caster weapon mark, {@code Flags_4} bit 0x100 (Era rows never carry it). */
  static boolean isForeverCaster(ClientItem item) {
    return (flag(item, 4) & FOREVER_CASTER_FLAG_4) != 0;
  }

  private static int flag(ClientItem item, int index) {
    return index < item.flags().size() ? item.flags().get(index) : 0;
  }

  private static ItemDamage of(int min, int max, int school, int delayMs) {
    double avg = (min + max) / 2.0;
    return new ItemDamage(min, max, school, delayMs, delayMs > 0 ? avg * 1000.0 / delayMs : avg);
  }
}
