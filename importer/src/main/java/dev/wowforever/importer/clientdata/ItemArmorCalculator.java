package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * An item's base armour.
 *
 * <ul>
 *   <li>Forever cloth, leather, mail and plate (class 4, subclass 1 to 4): {@link ScalingTables#armor}, the material
 *       being the subclass.
 *   <li>Forever shields (class 4, subclass 6): {@link ScalingTables#shieldArmor}. A shield's own block value is in no
 *       table this pipeline reads (Wowhead shows "41 Block" for The Immovable Object), so {@code block} is null and
 *       {@code clientdata.unverified.block} is counted per shield.
 *   <li>Era rows (ruling 17): the absolute {@code Resistances_0} when it is not 0.
 * </ul>
 *
 * Bonus armour (stat 50) stays a stat: Wowhead shows it as its own "+80 Armor" line under the base value. A base
 * value of 0 (a shirt) means no armour. A missing table row counts {@code clientdata.unverified.armor}.
 */
public final class ItemArmorCalculator {
  public static final String UNVERIFIED = "clientdata.unverified.armor";
  public static final String UNVERIFIED_BLOCK = "clientdata.unverified.block";
  private static final int ARMOR = 4;
  private static final int SHIELD = 6;

  private ItemArmorCalculator() {}

  public static Optional<ItemArmor> armor(ClientItem item, ScalingTables tables, ImportReport report) {
    if (!item.hasSparse()) {
      return Optional.empty();
    }
    if (item.absolute() != null) {
      int a = item.absolute().armor();
      return a > 0 ? Optional.of(new ItemArmor(a, null)) : Optional.empty();
    }
    Integer cls = item.classId();
    Integer sub = item.subclassId();
    if (cls == null || cls != ARMOR || sub == null || (sub != SHIELD && (sub < 1 || sub > 4))) {
      return Optional.empty();
    }
    if (item.itemLevel() == null || item.quality() == null || item.inventoryType() == null) {
      report.count(UNVERIFIED, 1);
      return Optional.empty();
    }
    OptionalInt base = sub == SHIELD
        ? tables.shieldArmor(item.itemLevel(), item.quality())
        : tables.armor(item.itemLevel(), item.quality(), item.inventoryType(), sub);
    if (base.isEmpty()) {
      report.count(UNVERIFIED, 1);
      return Optional.empty();
    }
    if (base.getAsInt() <= 0) {
      return Optional.empty();
    }
    if (sub == SHIELD) {
      report.count(UNVERIFIED_BLOCK, 1);
    }
    return Optional.of(new ItemArmor(base.getAsInt(), null));
  }
}
