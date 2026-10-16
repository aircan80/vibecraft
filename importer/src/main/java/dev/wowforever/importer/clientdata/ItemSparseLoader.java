package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Joins the client's {@code Item} and {@code ItemSparse} tables onto the Questie item ids.
 *
 * <p>Per item: Forever's {@code ItemSparse} row when present ({@code clientSource = "forever"}), else the Era
 * fallback's {@code ItemSparse.era} row when that table exists and has one ({@code "era"}, ruling 17); the
 * {@code Item} row (class, subclass, icon) always comes from Forever. The client's "none" values become null:
 * {@code ItemSet}, {@code StartQuestID}, {@code IconFileDataID}, {@code RequiredSkill} and {@code ItemDelay} of 0, an
 * empty {@code Display_lang} or {@code Description_lang}; {@code RequiredSkillRank} is null without a skill. The
 * tooltip-only columns ({@link ClientItem.Details}) likewise: {@code MaxCount}, {@code ContainerSlots},
 * {@code DurationInInventory} and {@code MinFactionID} of 0 are null, {@code MinReputation} is null without a
 * faction, and {@code MaxDurability} is read only from a table that has it (the Era one).
 *
 * <p>Every column read is checked up front; a missing one fails naming the table and the column.
 *
 * <p>Counters: {@code clientdata.items.matched} (an ItemSparse row, either source), {@code .matched.forever},
 * {@code .matched.era}, {@code clientdata.items.iconOnly} (an Item row only) and {@code clientdata.items.unmatched}
 * (neither); the note {@code clientdata.unmatched.ranges} lists the unmatched ids per 10,000-id bucket.
 */
public final class ItemSparseLoader {
  public static final String ITEM = "Item";
  public static final String SPARSE = "ItemSparse";
  public static final String ERA_SPARSE = "ItemSparse.era";
  private static final int FLAG_COLUMNS = 5;
  private static final int STAT_SLOTS = 10;
  private static final long BUCKET = 10_000;

  static final List<String> ITEM_COLUMNS = List.of("ID", "ClassID", "SubclassID", "InventoryType", "IconFileDataID");
  static final List<String> SPARSE_COLUMNS = Stream.of(
          Stream.of("ID", "Display_lang", "Description_lang", "ItemLevel", "OverallQualityID", "RequiredLevel",
              "InventoryType", "Bonding", "SellPrice", "BuyPrice", "Stackable", "ItemSet", "StartQuestID",
              "AllowableClass", "AllowableRace_0", "RequiredSkill", "RequiredSkillRank", "ItemDelay", "DmgVariance",
              "DamageType", "MaxCount", "MinFactionID", "MinReputation", "ContainerSlots", "DurationInInventory",
              "AllowableRace_1"),
          IntStream.range(0, FLAG_COLUMNS).mapToObj(i -> "Flags_" + i),
          IntStream.range(0, STAT_SLOTS).mapToObj(i -> "StatModifier_bonusStat_" + i),
          IntStream.range(0, STAT_SLOTS).mapToObj(i -> "StatPercentEditor_" + i))
      .flatMap(s -> s)
      .toList();
  private static final int DAMAGE_SLOTS = 5;
  private static final int RESISTANCES = 7;
  /** The Era row's absolute values (ruling 17); Forever's ItemSparse has none of these columns. */
  static final List<String> ERA_ABSOLUTE_COLUMNS = Stream.of(
          IntStream.range(0, STAT_SLOTS).mapToObj(i -> "StatModifier_bonusAmount_" + i),
          IntStream.range(0, DAMAGE_SLOTS).mapToObj(i -> "MinDamage_" + i),
          IntStream.range(0, DAMAGE_SLOTS).mapToObj(i -> "MaxDamage_" + i),
          IntStream.range(0, RESISTANCES).mapToObj(i -> "Resistances_" + i))
      .flatMap(s -> s)
      .toList();
  static final List<String> ERA_COLUMNS =
      Stream.concat(SPARSE_COLUMNS.stream(), ERA_ABSOLUTE_COLUMNS.stream()).toList();
  /** Read when the table has it: the Era table does, Forever's (build 1.60.1.70425) does not. */
  static final String MAX_DURABILITY = "MaxDurability";

  private ItemSparseLoader() {}

  /**
   * The client data of every id in {@code questieItemIds} that has an Item or ItemSparse row, by id in id order.
   *
   * @throws IllegalStateException when a table is missing or lacks a column this loader reads
   */
  public static Map<Long, ClientItem> load(ClientDataSource src, Set<Long> questieItemIds, ImportReport report) {
    Map<Long, CsvTable.Row> items = index(src.table(ITEM), ITEM, ITEM_COLUMNS);
    Map<Long, CsvTable.Row> forever = index(src.table(SPARSE), SPARSE, SPARSE_COLUMNS);
    Optional<CsvTable> eraTable = src.optionalTable(ERA_SPARSE);
    Map<Long, CsvTable.Row> era = eraTable.isPresent() ? index(eraTable.get(), ERA_SPARSE, ERA_COLUMNS) : Map.of();

    Map<Long, ClientItem> out = new LinkedHashMap<>();
    long matchedForever = 0;
    long matchedEra = 0;
    long iconOnly = 0;
    List<Long> unmatched = new ArrayList<>();
    for (long id : new TreeSet<>(questieItemIds)) {
      CsvTable.Row item = items.get(id);
      CsvTable.Row sparse = forever.get(id);
      String source = ClientItem.FOREVER;
      if (sparse == null) {
        sparse = era.get(id);
        source = ClientItem.ERA;
      }
      if (sparse != null) {
        if (source.equals(ClientItem.FOREVER)) {
          matchedForever++;
        } else {
          matchedEra++;
        }
      } else if (item != null) {
        iconOnly++;
      } else {
        unmatched.add(id);
        continue;
      }
      out.put(id, item(id, item, sparse, sparse == null ? null : source));
    }
    report.count("clientdata.items.matched", matchedForever + matchedEra);
    report.count("clientdata.items.matched.forever", matchedForever);
    report.count("clientdata.items.matched.era", matchedEra);
    report.count("clientdata.items.iconOnly", iconOnly);
    report.count("clientdata.items.unmatched", unmatched.size());
    report.note("clientdata.unmatched.ranges: " + ranges(unmatched));
    return Collections.unmodifiableMap(out);
  }

  /**
   * The Era rows alone ({@code clientSource = "era"}, with their absolute values) joined with Forever's Item rows,
   * for every id in {@code ids} that has both; for the Era overlap oracle, which compares an item's Era values with
   * what its Forever row computes to. Empty without an {@code ItemSparse.era} table.
   */
  static Map<Long, ClientItem> loadEra(ClientDataSource src, Set<Long> ids) {
    Optional<CsvTable> eraTable = src.optionalTable(ERA_SPARSE);
    if (eraTable.isEmpty()) {
      return Map.of();
    }
    Map<Long, CsvTable.Row> items = index(src.table(ITEM), ITEM, ITEM_COLUMNS);
    Map<Long, CsvTable.Row> era = index(eraTable.get(), ERA_SPARSE, ERA_COLUMNS);
    Map<Long, ClientItem> out = new LinkedHashMap<>();
    for (long id : new TreeSet<>(ids)) {
      if (items.containsKey(id) && era.containsKey(id)) {
        out.put(id, item(id, items.get(id), era.get(id), ClientItem.ERA));
      }
    }
    return Collections.unmodifiableMap(out);
  }

  private static Map<Long, CsvTable.Row> index(CsvTable table, String name, List<String> columns) {
    for (String col : columns) {
      if (!table.hasColumn(col)) {
        throw new IllegalStateException("client table " + name + " (" + table.source() + ") has no column " + col
            + "; the item join needs " + columns);
      }
    }
    return table.indexByLong("ID");
  }

  private static ClientItem item(long id, CsvTable.Row item, CsvTable.Row s, String source) {
    Integer classId = item == null ? null : integer(item, "ClassID");
    Integer subclassId = item == null ? null : integer(item, "SubclassID");
    Long iconId = item == null ? null : positive(item.getLong("IconFileDataID"));
    if (s == null) {
      Integer inventoryType = item == null ? null : integer(item, "InventoryType");
      return new ClientItem(id, false, null, null, null, classId, subclassId, inventoryType, null, null, null, null,
          iconId, null, null, null, null, null, null, null, List.of(), null, List.of(), null, null, null, null, null,
          ClientItem.Details.NONE);
    }
    Integer requiredSkill = positiveInt(s.getInt("RequiredSkill"));
    List<Integer> flags = new ArrayList<>(FLAG_COLUMNS);
    for (int i = 0; i < FLAG_COLUMNS; i++) {
      flags.add((int) s.longOr("Flags_" + i, 0));
    }
    List<ClientItem.StatShare> shares = new ArrayList<>();
    for (int i = 0; i < STAT_SLOTS; i++) {
      int stat = s.intOr("StatModifier_bonusStat_" + i, -1);
      if (stat >= 0) {
        shares.add(new ClientItem.StatShare(stat, s.intOr("StatPercentEditor_" + i, 0)));
      }
    }
    OptionalLong race = s.getLong("AllowableRace_0");
    return new ClientItem(id, true,
        integer(s, "OverallQualityID"),
        integer(s, "ItemLevel"),
        integer(s, "RequiredLevel"),
        classId,
        subclassId,
        integer(s, "InventoryType"),
        integer(s, "Bonding"),
        boxed(s.getLong("SellPrice")),
        boxed(s.getLong("BuyPrice")),
        integer(s, "Stackable"),
        iconId,
        positive(s.getLong("ItemSet")),
        text(s.get("Display_lang")),
        text(s.get("Description_lang")),
        integer(s, "AllowableClass"),
        race.isPresent() ? race.getAsLong() : null,
        requiredSkill,
        requiredSkill == null ? null : integer(s, "RequiredSkillRank"),
        flags,
        positive(s.getLong("StartQuestID")),
        shares,
        positiveInt(s.getInt("ItemDelay")),
        decimal(s.getDouble("DmgVariance")),
        integer(s, "DamageType"),
        source,
        ClientItem.ERA.equals(source) ? absolute(s) : null,
        details(s));
  }

  /** The tooltip-only columns; {@code MaxDurability} only when the row's table has the column. */
  private static ClientItem.Details details(CsvTable.Row s) {
    Integer faction = positiveInt(s.getInt("MinFactionID"));
    OptionalLong raceHigh = s.getLong("AllowableRace_1");
    return new ClientItem.Details(
        positiveInt(s.getInt("MaxCount")),
        s.table().hasColumn(MAX_DURABILITY) ? positiveInt(s.getInt(MAX_DURABILITY)) : null,
        positiveInt(s.getInt("ContainerSlots")),
        faction,
        faction == null ? null : integer(s, "MinReputation"),
        positiveInt(s.getInt("DurationInInventory")),
        raceHigh.isPresent() ? raceHigh.getAsLong() : null);
  }

  /** The Era row's absolute values: non-zero stat amounts, the first damage range, armour and resistances. */
  private static ClientItem.Absolute absolute(CsvTable.Row s) {
    List<ClientItem.StatValue> stats = new ArrayList<>();
    for (int i = 0; i < STAT_SLOTS; i++) {
      int stat = s.intOr("StatModifier_bonusStat_" + i, -1);
      int amount = s.intOr("StatModifier_bonusAmount_" + i, 0);
      if (stat >= 0 && amount != 0) {
        stats.add(new ClientItem.StatValue(stat, amount));
      }
    }
    int slots = 0;
    for (int i = 0; i < DAMAGE_SLOTS; i++) {
      if (s.intOr("MaxDamage_" + i, 0) > 0) {
        slots++;
      }
    }
    int max = s.intOr("MaxDamage_0", 0);
    int extraMax = s.intOr("MaxDamage_1", 0);
    List<Integer> resistances = new ArrayList<>();
    for (int i = 1; i < RESISTANCES; i++) {
      resistances.add(s.intOr("Resistances_" + i, 0));
    }
    return new ClientItem.Absolute(stats, max > 0 ? s.intOr("MinDamage_0", 0) : null, max > 0 ? max : null, slots,
        s.intOr("Resistances_0", 0), resistances, extraMax > 0 ? s.intOr("MinDamage_1", 0) : null,
        extraMax > 0 ? extraMax : null);
  }

  private static Integer integer(CsvTable.Row r, String col) {
    OptionalInt v = r.getInt(col);
    return v.isPresent() ? v.getAsInt() : null;
  }

  private static Double decimal(OptionalDouble v) {
    return v.isPresent() ? v.getAsDouble() : null;
  }

  private static Long boxed(OptionalLong v) {
    return v.isPresent() ? v.getAsLong() : null;
  }

  private static Long positive(OptionalLong v) {
    return v.isPresent() && v.getAsLong() > 0 ? v.getAsLong() : null;
  }

  private static Integer positiveInt(OptionalInt v) {
    return v.isPresent() && v.getAsInt() > 0 ? v.getAsInt() : null;
  }

  private static String text(String cell) {
    return cell.isEmpty() ? null : cell;
  }

  /** {@code 0-9999 (3), 190000-199999 (1)}, or {@code none}. */
  static String ranges(List<Long> ids) {
    if (ids.isEmpty()) {
      return "none";
    }
    Map<Long, Long> buckets = ids.stream()
        .collect(Collectors.groupingBy(id -> Math.floorDiv(id, BUCKET), TreeMap::new, Collectors.counting()));
    return buckets.entrySet().stream()
        .map(e -> (e.getKey() * BUCKET) + "-" + (e.getKey() * BUCKET + BUCKET - 1) + " (" + e.getValue() + ")")
        .collect(Collectors.joining(", "));
  }
}
