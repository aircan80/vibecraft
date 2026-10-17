package dev.wowforever.importer.serverdb;

import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRateLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.report.ImportReport;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/**
 * Community drop rates from the VMaNGOS world database: for every creature, the chance in percent that one kill
 * drops each item, as the server's loot rules would roll it at patch 1.12 ({@value #PATCH}).
 *
 * <p>Rules, per loot table (a creature's {@code creature_loot_template} entry, or a {@code reference_loot_template}
 * entry):
 * <ul>
 *   <li>A row's chance is {@code |ChanceOrQuestChance|} percent, capped at 100; a negative value marks a quest-only
 *       drop (counted as {@code serverdb.rates.questOnly}).</li>
 *   <li>Rows in group 0 roll independently. A group ({@code groupid > 0}) drops at most one of its rows: rows with
 *       an explicit chance keep it, and the rows with chance 0 share what the explicit chances leave of 100 %
 *       equally ({@code (100 - sum) / n}, never below 0). A 0-chance row in group 0 never drops
 *       ({@code serverdb.zeroChance.ungrouped}).</li>
 *   <li>A reference row ({@code mincountOrRef < 0}) rolls its chance once, then rolls the referenced table
 *       {@code maxcount} times: an item the referenced table drops with probability {@code q} drops with
 *       {@code chance * (1 - (1 - q)^maxcount)}. References nest; the fifth level is the last one followed
 *       ({@value #MAX_REFERENCE_DEPTH}), deeper ones count as {@code serverdb.references.depthCapped}, unknown ones
 *       as {@code serverdb.references.missing}.</li>
 *   <li>An item reached by several rows combines as independent rolls: {@code 1 - prod(1 - p)}.</li>
 * </ul>
 * Creatures reach their table through {@code creature_template.loot_id}; creature entries are the Classic NPC ids
 * Questie uses. Rates go into {@link DropRates} under source {@value #SOURCE} at precedence {@link #PRECEDENCE}.
 */
public final class ServerDropRates {
  public static final String SOURCE = "vmangos";
  public static final int PRECEDENCE = DropRateLoader.VMANGOS;
  /** VMaNGOS patch index of 1.12, the last Classic patch; rows are read as they are at this patch. */
  public static final int PATCH = 10;
  public static final int MAX_REFERENCE_DEPTH = 5;

  private final Map<Long, List<LootRow>> references;
  private final ImportReport report;
  private final Map<Long, Map<Long, Double>> referenceMemo = new HashMap<>();

  private ServerDropRates(Map<Long, List<LootRow>> references, ImportReport report) {
    this.references = references;
    this.report = report;
  }

  /**
   * Computes the rates of every creature {@code knownNpc} accepts and puts them into {@code rates}. Counters:
   * {@code serverdb.loot.rows} and {@code serverdb.reference.rows} (rows read at {@value #PATCH}),
   * {@code serverdb.creatures} (creatures with a loot table), {@code serverdb.unmatched.npcs} (of those, the ones
   * {@code knownNpc} rejects), {@code serverdb.rates} ((npc, item) pairs put) and the rule counters above.
   *
   * @return the number of (npc, item) pairs put
   */
  public static int load(ServerDbSource db, LongPredicate knownNpc, DropRates rates, ImportReport report)
      throws SQLException {
    Map<Long, List<LootRow>> loot = db.creatureLoot(PATCH);
    Map<Long, List<LootRow>> references = db.referenceLoot(PATCH);
    report.count("serverdb.loot.rows", loot.values().stream().mapToLong(List::size).sum());
    report.count("serverdb.reference.rows", references.values().stream().mapToLong(List::size).sum());
    for (String counter : List.of("serverdb.creatures", "serverdb.unmatched.npcs", "serverdb.rates",
        "serverdb.rates.questOnly", "serverdb.zeroChance.ungrouped", "serverdb.references.depthCapped",
        "serverdb.references.missing")) {
      report.count(counter, 0);
    }
    ServerDropRates computer = new ServerDropRates(references, report);
    Map<Long, Map<Long, Double>> tables = new HashMap<>();
    int pairs = 0;
    for (Map.Entry<Long, Long> creature : new TreeMap<>(db.creatureLootIds(PATCH)).entrySet()) {
      List<LootRow> rows = loot.get(creature.getValue());
      if (rows == null) {
        continue;
      }
      report.count("serverdb.creatures", 1);
      long npcId = creature.getKey();
      if (!knownNpc.test(npcId)) {
        report.count("serverdb.unmatched.npcs", 1);
        continue;
      }
      Map<Long, Double> table = tables.computeIfAbsent(creature.getValue(), k -> computer.roll(rows, 0));
      for (Map.Entry<Long, Double> item : table.entrySet()) {
        rates.put(npcId, item.getKey(), new DropRate(percent(item.getValue()), SOURCE), PRECEDENCE);
        pairs++;
      }
    }
    report.count("serverdb.rates", pairs);
    return pairs;
  }

  /** A probability as a percentage, rounded to six decimals so that float noise ({@code 25.500000000000007}) goes. */
  static double percent(double probability) {
    return Math.round(probability * 100 * 1e6) / 1e6;
  }

  /**
   * Item id to drop probability (0 to 1) of one table's rows; {@code depth} is the number of references followed to
   * reach them. Each creature table and each top-level reference is computed once, so the rule counters count rows
   * per computed table.
   */
  private Map<Long, Double> roll(List<LootRow> rows, int depth) {
    Map<Integer, Double> explicit = new HashMap<>();
    Map<Integer, Integer> equal = new HashMap<>();
    for (LootRow r : rows) {
      if (r.group() > 0) {
        if (r.chance() == 0) {
          equal.merge(r.group(), 1, Integer::sum);
        } else {
          explicit.merge(r.group(), Math.min(100, Math.abs(r.chance())), Double::sum);
        }
      }
    }
    Map<Long, Double> miss = new HashMap<>(); // item -> probability that no row drops it
    for (LootRow r : rows) {
      double p;
      if (r.chance() != 0) {
        p = Math.min(100, Math.abs(r.chance())) / 100;
      } else if (r.group() > 0) {
        p = Math.max(0, 100 - explicit.getOrDefault(r.group(), 0.0)) / equal.get(r.group()) / 100;
      } else {
        report.count("serverdb.zeroChance.ungrouped", 1);
        continue;
      }
      if (p <= 0) {
        continue;
      }
      if (r.isReference()) {
        Map<Long, Double> inner = reference(r.reference(), depth + 1);
        int times = Math.max(1, r.maxcount());
        for (Map.Entry<Long, Double> e : inner.entrySet()) {
          double q = p * (1 - Math.pow(1 - e.getValue(), times));
          miss.merge(e.getKey(), 1 - q, (a, b) -> a * b);
        }
      } else {
        if (r.chance() < 0) {
          report.count("serverdb.rates.questOnly", 1);
        }
        miss.merge(r.item(), 1 - p, (a, b) -> a * b);
      }
    }
    Map<Long, Double> drops = new HashMap<>();
    miss.forEach((item, m) -> {
      if (m < 1) {
        drops.put(item, 1 - m);
      }
    });
    return drops;
  }

  private Map<Long, Double> reference(long id, int depth) {
    if (depth > MAX_REFERENCE_DEPTH) {
      report.count("serverdb.references.depthCapped", 1);
      return Map.of();
    }
    List<LootRow> rows = references.get(id);
    if (rows == null) {
      report.count("serverdb.references.missing", 1);
      return Map.of();
    }
    // Only top-level references (depth 1) are memoised: deeper results depend on how much depth is left.
    if (depth == 1) {
      Map<Long, Double> memo = referenceMemo.get(id);
      if (memo != null) {
        return memo;
      }
      Map<Long, Double> result = roll(rows, depth);
      referenceMemo.put(id, result);
      return result;
    }
    return roll(rows, depth);
  }
}
