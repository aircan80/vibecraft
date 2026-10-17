package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * An item's stats, resistances included.
 *
 * <ul>
 *   <li>Forever rows: {@code value = round(budget * share / 10000)}, half up, where {@code budget} is
 *       {@link ScalingTables#randPropBudget} and {@code share} is {@code StatPercentEditor_n}. Zero shares and
 *       values that round to 0 are left out; slot order is kept.
 *   <li>Era rows (ruling 17): the absolute {@code StatModifier_bonusAmount_n}, then {@code Resistances_1..6} in the
 *       client's order (holy 53, fire 51, nature 55, frost 52, shadow 54, arcane 56).
 * </ul>
 *
 * A Forever item with non-zero shares but no budget (no {@code RandPropPoints} row, or an inventory type without a
 * slot group) gets no stats and counts {@code clientdata.unverified.stats}: a value is never guessed.
 */
public final class ItemStatsCalculator {
  public static final String UNVERIFIED = "clientdata.unverified.stats";
  /** {@code Resistances_1..6} as stat ids. */
  static final int[] RESISTANCE_STATS = {53, 51, 55, 52, 54, 56};

  private ItemStatsCalculator() {}

  public static List<ItemStat> stats(ClientItem item, ScalingTables tables, ImportReport report) {
    if (!item.hasSparse()) {
      return List.of();
    }
    if (item.absolute() != null) {
      return absolute(item.absolute());
    }
    List<ClientItem.StatShare> shares = item.statShares().stream().filter(s -> s.share() != 0).toList();
    if (shares.isEmpty()) {
      return List.of();
    }
    OptionalInt budget = item.itemLevel() == null || item.quality() == null || item.inventoryType() == null
        ? OptionalInt.empty()
        : tables.randPropBudget(item.itemLevel(), item.quality(), item.inventoryType());
    if (budget.isEmpty()) {
      report.count(UNVERIFIED, 1);
      return List.of();
    }
    List<ItemStat> out = new ArrayList<>();
    for (ClientItem.StatShare s : shares) {
      int value = ScalingTables.roundHalfUp(budget.getAsInt() * s.share() / 10000.0);
      if (value != 0) {
        out.add(new ItemStat(s.statId(), value));
      }
    }
    return List.copyOf(out);
  }

  private static List<ItemStat> absolute(ClientItem.Absolute a) {
    List<ItemStat> out = new ArrayList<>();
    for (ClientItem.StatValue v : a.stats()) {
      out.add(new ItemStat(v.statId(), v.value()));
    }
    for (int i = 0; i < a.resistances().size() && i < RESISTANCE_STATS.length; i++) {
      int value = a.resistances().get(i);
      if (value != 0) {
        out.add(new ItemStat(RESISTANCE_STATS[i], value));
      }
    }
    return List.copyOf(out);
  }
}
