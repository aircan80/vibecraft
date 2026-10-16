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
 * empty {@code Display_lang} or {@code Description_lang}; {@code RequiredSkillRank} is null without a skill.
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
              "DamageType"),
          IntStream.range(0, FLAG_COLUMNS).mapToObj(i -> "Flags_" + i),
          IntStream.range(0, STAT_SLOTS).mapToObj(i -> "StatModifier_bonusStat_" + i),
          IntStream.range(0, STAT_SLOTS).mapToObj(i -> "StatPercentEditor_" + i))
      .flatMap(s -> s)
      .toList();

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
    Map<Long, CsvTable.Row> era = eraTable.isPresent() ? index(eraTable.get(), ERA_SPARSE, SPARSE_COLUMNS) : Map.of();

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
          iconId, null, null, null, null, null, null, null, List.of(), null, List.of(), null, null, null, null);
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
        source);
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
