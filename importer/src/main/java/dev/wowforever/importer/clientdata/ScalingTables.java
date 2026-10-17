package dev.wowforever.importer.clientdata;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The client's item budget tables, read once: {@code RandPropPoints} (stat budget), {@code ItemDamage*} (weapon
 * DPS), {@code ItemArmorTotal}, {@code ItemArmorQuality}, {@code ArmorLocation} and {@code ItemArmorShield}.
 *
 * <p>Stat budget slot groups ({@code RandPropPoints.<Quality>_<group>}), the classic {@code InventoryType} table
 * (wowdev wiki, {@code GetItemSlotBudgetIndex}); checked against the Era overlap, where moving ranged right (26) to
 * group 0 as retail does lowers the exact stat matches:
 *
 * <pre>
 *   group  inventory types
 *   0      1 head, 4 shirt, 5 chest, 7 legs, 17 two-hand, 20 robe
 *   1      3 shoulder, 6 waist, 8 feet, 10 hands, 12 trinket
 *   2      2 neck, 9 wrists, 11 finger, 14 shield, 16 back, 23 held in off-hand
 *   3      13 one-hand, 21 main hand, 22 off hand
 *   4      15 ranged (bow), 25 thrown, 26 ranged right (gun, crossbow, wand), 28 relic
 *   -      0 non-equip, 18 bag, 19 tabard, 24 ammo, 27 quiver: no budget
 * </pre>
 *
 * Quality picks the column: 4 and above {@code Epic}, 3 {@code Superior}, 0 to 2 {@code Good} (a common item with
 * stat shares, such as 12189, takes its budget from {@code Good}).
 */
public final class ScalingTables {
  /** The weapon DPS tables, one per kind. */
  public enum DamageKind {
    ONE_HAND("ItemDamageOneHand"),
    ONE_HAND_CASTER("ItemDamageOneHandCaster"),
    TWO_HAND("ItemDamageTwoHand"),
    TWO_HAND_CASTER("ItemDamageTwoHandCaster"),
    RANGED("ItemDamageRanged"),
    THROWN("ItemDamageThrown"),
    WAND("ItemDamageWand"),
    AMMO("ItemDamageAmmo");

    private final String table;

    DamageKind(String table) {
      this.table = table;
    }

    public String table() {
      return table;
    }
  }

  private static final int QUALITIES = 7;
  private static final int SLOT_GROUPS = 5;
  private static final List<String> MATERIALS = List.of("Cloth", "Leather", "Mail", "Plate");
  private static final List<String> LOCATION_COLUMNS =
      List.of("Clothmodifier", "Leathermodifier", "Chainmodifier", "Platemodifier");
  private static final int ROBE = 20;
  private static final int CHEST = 5;

  private final Map<Integer, int[][]> budget;
  private final Map<DamageKind, Map<Integer, double[]>> damage;
  private final Map<Integer, double[]> armorTotal;
  private final Map<Integer, double[]> armorQuality;
  private final Map<Integer, double[]> armorLocation;
  private final Map<Integer, int[]> shield;

  private ScalingTables(Map<Integer, int[][]> budget, Map<DamageKind, Map<Integer, double[]>> damage,
      Map<Integer, double[]> armorTotal, Map<Integer, double[]> armorQuality, Map<Integer, double[]> armorLocation,
      Map<Integer, int[]> shield) {
    this.budget = budget;
    this.damage = damage;
    this.armorTotal = armorTotal;
    this.armorQuality = armorQuality;
    this.armorLocation = armorLocation;
    this.shield = shield;
  }

  /** @throws IllegalStateException naming the table (and column) when a table or a column is missing */
  public static ScalingTables load(ClientDataSource src) {
    Map<Integer, int[][]> budget = new HashMap<>();
    CsvTable rpp = src.table("RandPropPoints");
    List<String> tiers = List.of("Epic", "Superior", "Good");
    require(rpp, "ID");
    for (String tier : tiers) {
      for (int g = 0; g < SLOT_GROUPS; g++) {
        require(rpp, tier + "_" + g);
      }
    }
    for (CsvTable.Row r : rpp.rows()) {
      int[][] v = new int[tiers.size()][SLOT_GROUPS];
      for (int t = 0; t < tiers.size(); t++) {
        for (int g = 0; g < SLOT_GROUPS; g++) {
          v[t][g] = r.intOr(tiers.get(t) + "_" + g, 0);
        }
      }
      budget.put(r.intOr("ID", -1), v);
    }

    Map<DamageKind, Map<Integer, double[]>> damage = new EnumMap<>(DamageKind.class);
    for (DamageKind kind : DamageKind.values()) {
      damage.put(kind, byLevel(src.table(kind.table()), "ItemLevel", "Quality_", QUALITIES));
    }
    Map<Integer, double[]> total = byLevel(src.table("ItemArmorTotal"), "ItemLevel", MATERIALS);
    Map<Integer, double[]> quality = byLevel(src.table("ItemArmorQuality"), "ID", "Qualitymod_", QUALITIES);
    Map<Integer, double[]> location = byLevel(src.table("ArmorLocation"), "ID", LOCATION_COLUMNS);
    Map<Integer, int[]> shield = new HashMap<>();
    byLevel(src.table("ItemArmorShield"), "ItemLevel", "Quality_", QUALITIES)
        .forEach((level, v) -> {
          int[] q = new int[v.length];
          for (int i = 0; i < v.length; i++) {
            q[i] = (int) Math.round(v[i]);
          }
          shield.put(level, q);
        });
    return new ScalingTables(budget, damage, total, quality, location, shield);
  }

  /** The stat budget slot group of an inventory type (see the class comment), empty when it has none. */
  public static OptionalInt slotGroup(int inventoryType) {
    return switch (inventoryType) {
      case 1, 4, 5, 7, 17, 20 -> OptionalInt.of(0);
      case 3, 6, 8, 10, 12 -> OptionalInt.of(1);
      case 2, 9, 11, 14, 16, 23 -> OptionalInt.of(2);
      case 13, 21, 22 -> OptionalInt.of(3);
      case 15, 25, 26, 28 -> OptionalInt.of(4);
      default -> OptionalInt.empty();
    };
  }

  /** {@code RandPropPoints[itemLevel].<Epic|Superior|Good>_<slot group>}; empty without a row or a slot group. */
  public OptionalInt randPropBudget(int itemLevel, int quality, int inventoryType) {
    int[][] row = budget.get(itemLevel);
    OptionalInt group = slotGroup(inventoryType);
    if (row == null || group.isEmpty() || quality < 0) {
      return OptionalInt.empty();
    }
    int tier = quality >= 4 ? 0 : quality == 3 ? 1 : 2;
    return OptionalInt.of(row[tier][group.getAsInt()]);
  }

  /** {@code ItemDamage<Kind>[itemLevel].Quality_<quality>}, the weapon's DPS; empty without a row or column. */
  public OptionalDouble dps(DamageKind kind, int itemLevel, int quality) {
    return cell(damage.get(kind), itemLevel, quality);
  }

  /**
   * Body armour: {@code round(ItemArmorTotal[level].<material> * ItemArmorQuality[level].Qualitymod_<quality> *
   * ArmorLocation[inventoryType].<material>modifier)}, half up. A robe (20) uses the chest row (5), whose
   * {@code ArmorLocation} row is the one with a modifier; slots the table gives 0 (shirt, tabard) yield 0.
   *
   * @param material the armour subclass: 1 cloth, 2 leather, 3 mail, 4 plate
   * @return empty without a table row, a location row or a known material
   */
  public OptionalInt armor(int itemLevel, int quality, int inventoryType, int material) {
    if (material < 1 || material > MATERIALS.size()) {
      return OptionalInt.empty();
    }
    OptionalDouble total = cell(armorTotal, itemLevel, material - 1);
    OptionalDouble qualityMod = cell(armorQuality, itemLevel, quality);
    OptionalDouble location = cell(armorLocation, inventoryType == ROBE ? CHEST : inventoryType, material - 1);
    if (total.isEmpty() || qualityMod.isEmpty() || location.isEmpty()) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(roundHalfUp(total.getAsDouble() * qualityMod.getAsDouble() * location.getAsDouble()));
  }

  /** {@code ItemArmorShield[itemLevel].Quality_<quality>}; empty without a row or column. */
  public OptionalInt shieldArmor(int itemLevel, int quality) {
    int[] row = shield.get(itemLevel);
    return row == null || quality < 0 || quality >= row.length ? OptionalInt.empty() : OptionalInt.of(row[quality]);
  }

  /** {@code floor(x + 0.5)}: the client's rounding of budget products. */
  static int roundHalfUp(double x) {
    return (int) Math.floor(x + 0.5);
  }

  private static OptionalDouble cell(Map<Integer, double[]> table, int key, int index) {
    double[] row = table.get(key);
    return row == null || index < 0 || index >= row.length ? OptionalDouble.empty() : OptionalDouble.of(row[index]);
  }

  private static Map<Integer, double[]> byLevel(CsvTable t, String key, String prefix, int n) {
    List<String> cols = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) {
      cols.add(prefix + i);
    }
    return byLevel(t, key, cols);
  }

  private static Map<Integer, double[]> byLevel(CsvTable t, String key, List<String> cols) {
    require(t, key);
    cols.forEach(c -> require(t, c));
    Map<Integer, double[]> out = new HashMap<>();
    for (CsvTable.Row r : t.rows()) {
      double[] v = new double[cols.size()];
      for (int i = 0; i < cols.size(); i++) {
        v[i] = r.doubleOr(cols.get(i), 0);
      }
      out.put(r.intOr(key, -1), v);
    }
    return out;
  }

  private static void require(CsvTable t, String col) {
    if (!t.hasColumn(col)) {
      throw new IllegalStateException("client table " + t.source() + " has no column " + col
          + "; the item stat, damage and armour computation needs it");
    }
  }
}
