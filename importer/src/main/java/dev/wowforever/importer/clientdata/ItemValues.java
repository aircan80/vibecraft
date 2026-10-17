package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One item's computed stats, damage and armour, written to {@code item_stat}, {@code item_damage} and
 * {@code item_armor}.
 *
 * @param stats  in tooltip slot order; empty for none
 * @param damage null for none (or unverified)
 * @param armor  null for none (or unverified)
 */
public record ItemValues(List<ItemStat> stats, ItemDamage damage, ItemArmor armor) {
  public static final String STATS_ITEMS = "clientdata.stats.items";
  public static final String DAMAGE_ITEMS = "clientdata.damage.items";
  public static final String ARMOR_ITEMS = "clientdata.armor.items";
  public static final String UNKNOWN_STAT = "clientdata.stats.unknownId";

  public ItemValues {
    stats = stats == null ? List.of() : List.copyOf(stats);
  }

  /**
   * The values of every item that has at least one, by id in id order. Counts the items with stats, damage and
   * armour ({@code clientdata.<category>.items}), the unverified ones ({@code clientdata.unverified.<category>},
   * always emitted) and stat ids {@link StatNames} does not know ({@code clientdata.stats.unknownId}, plus a note
   * listing them).
   */
  public static Map<Long, ItemValues> compute(Map<Long, ClientItem> items, ScalingTables tables, ImportReport report) {
    for (String c : List.of(STATS_ITEMS, DAMAGE_ITEMS, ARMOR_ITEMS, ItemStatsCalculator.UNVERIFIED,
        ItemDamageCalculator.UNVERIFIED, ItemArmorCalculator.UNVERIFIED, ItemArmorCalculator.UNVERIFIED_BLOCK,
        ItemDamageCalculator.SECONDARY_DROPPED, UNKNOWN_STAT)) {
      report.count(c, 0);
    }
    Map<Long, ItemValues> out = new LinkedHashMap<>();
    TreeSet<Integer> unknown = new TreeSet<>();
    for (Map.Entry<Long, ClientItem> e : new TreeMap<>(items).entrySet()) {
      ClientItem item = e.getValue();
      List<ItemStat> stats = ItemStatsCalculator.stats(item, tables, report);
      ItemDamage damage = ItemDamageCalculator.damage(item, tables, report).orElse(null);
      ItemArmor armor = ItemArmorCalculator.armor(item, tables, report).orElse(null);
      for (ItemStat s : stats) {
        if (!StatNames.isKnown(s.statId())) {
          report.count(UNKNOWN_STAT, 1);
          unknown.add(s.statId());
        }
      }
      report.count(STATS_ITEMS, stats.isEmpty() ? 0 : 1);
      report.count(DAMAGE_ITEMS, damage == null ? 0 : 1);
      report.count(ARMOR_ITEMS, armor == null ? 0 : 1);
      if (!stats.isEmpty() || damage != null || armor != null) {
        out.put(e.getKey(), new ItemValues(stats, damage, armor));
      }
    }
    if (!unknown.isEmpty()) {
      report.note("clientdata.stats.unknownIds: "
          + unknown.stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }
    return Collections.unmodifiableMap(out);
  }
}
