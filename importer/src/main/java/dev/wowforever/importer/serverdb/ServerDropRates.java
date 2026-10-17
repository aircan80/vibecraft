package dev.wowforever.importer.serverdb;

import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRateLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.report.ImportReport;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/**
 * Community drop rates from the VMaNGOS world database: for every creature, the chance in percent that one kill
 * drops each item, as the server's loot code ({@code src/game/LootMgr.cpp}) would roll it at patch 1.12
 * ({@value #PATCH}).
 *
 * <p>A loot template (a creature's {@code creature_loot_template} entry, or a {@code reference_loot_template} entry)
 * holds, as in {@code LootTemplate::AddEntry}, groups (rows with {@code groupid > 0} and {@code mincountOrRef > 0})
 * and ungrouped entries (every other row: items of group 0 and all references, whatever their groupid).
 * Processing a template ({@code LootTemplate::Process}):
 * <ul>
 *   <li>Each ungrouped item rolls on its own with {@code |ChanceOrQuestChance|} percent, capped at 100. A negative
 *       value marks a quest-only item ({@code serverdb.rates.questOnly}). A 0-chance item in group 0 is invalid and
 *       never drops ({@code serverdb.zeroChance.ungrouped}).</li>
 *   <li>Each ungrouped reference ({@code mincountOrRef < 0}) rolls its own chance, with a negative value read as a
 *       plain chance ({@code LootStoreItem::IsValid}); a 0-chance reference is skipped
 *       ({@code serverdb.references.zeroChance}). On a hit the referenced template is processed {@code maxcount}
 *       times (once for 0, {@code serverdb.references.maxcountZero}): with the reference's groupid {@code g > 0}
 *       only its group {@code g} rolls, with {@code g = 0} the whole template. An item one such pass drops with
 *       probability {@code q} drops with {@code chance * (1 - (1 - q)^maxcount)}. References nest (only through
 *       whole-template passes); the fifth level is the last one followed ({@value #MAX_REFERENCE_DEPTH}), deeper
 *       ones count as {@code serverdb.references.depthCapped}, unknown ones as
 *       {@code serverdb.references.missing}.</li>
 *   <li>Each group rolls once and yields at most one item ({@code LootGroup::Roll}): one roll of 0 to 100 is checked
 *       against the explicit-chance rows in row order (by item id), each taking {@code min(chance, what is left)},
 *       and a row of 100 % or more takes all that is left; when no explicit row hits, one 0-chance row is picked
 *       uniformly, so each gets {@code (100 - explicit sum) / n}, never below 0. Rows within a group exclude each
 *       other, so an item listed twice in one group adds its two shares.</li>
 *   <li>Different entries and groups roll independently: an item reached several ways drops with
 *       {@code 1 - prod(1 - p)}.</li>
 * </ul>
 * A rate is quest-only when every way to the item ends in a negative-chance item row; references are never
 * quest-only themselves. Quest-only rates go in at {@link #QUEST_ONLY_PRECEDENCE}, below every other source, marked
 * {@link DropRate#questOnly()}. Creatures reach their table through {@code creature_template.loot_id}; creature
 * entries are the Classic NPC ids Questie uses. Rates go into {@link DropRates} under source {@value #SOURCE} at
 * precedence {@link #PRECEDENCE}.
 */
public final class ServerDropRates {
  public static final String SOURCE = "vmangos";
  public static final int PRECEDENCE = DropRateLoader.VMANGOS;
  public static final int QUEST_ONLY_PRECEDENCE = DropRateLoader.VMANGOS_QUEST_ONLY;
  /** VMaNGOS patch index of 1.12, the last Classic patch; rows are read as they are at this patch. */
  public static final int PATCH = 10;
  public static final int MAX_REFERENCE_DEPTH = 5;

  /** An item's drop probability (0 to 1), and whether every way to it is quest-only. */
  private record Drop(double probability, boolean questOnly) {}

  /** A template split as {@code LootTemplate::AddEntry} splits it; groups keep row order. */
  private record Template(List<LootRow> entries, Map<Integer, List<LootRow>> groups) {
    static Template of(List<LootRow> rows) {
      List<LootRow> entries = new ArrayList<>();
      Map<Integer, List<LootRow>> groups = new TreeMap<>();
      for (LootRow r : rows) {
        if (r.group() > 0 && !r.isReference()) {
          groups.computeIfAbsent(r.group(), g -> new ArrayList<>()).add(r);
        } else {
          entries.add(r);
        }
      }
      return new Template(entries, groups);
    }
  }

  private record PassKey(long reference, int group, int depth) {}

  private final Map<Long, List<LootRow>> referenceRows;
  private final Map<Long, Template> references = new HashMap<>();
  private final ImportReport report;
  private final Map<PassKey, Map<Long, Drop>> passMemo = new HashMap<>();

  private ServerDropRates(Map<Long, List<LootRow>> referenceRows, ImportReport report) {
    this.referenceRows = referenceRows;
    this.report = report;
  }

  /**
   * Computes the rates of every creature {@code knownNpc} accepts and puts them into {@code rates}. Counters:
   * {@code serverdb.loot.rows} and {@code serverdb.reference.rows} (rows read at {@value #PATCH}),
   * {@code serverdb.loot.forbidden} (item rows {@code forbidden_items} removed), the validity counters of
   * {@link ServerDbSource} ({@code serverdb.invalid.*}, {@code serverdb.references.maxcountZero}),
   * {@code serverdb.creatures}
   * (creatures with a loot table), {@code serverdb.unmatched.npcs} (of those, the ones {@code knownNpc} rejects),
   * {@code serverdb.rates} ((npc, item) pairs put), {@code serverdb.rates.questOnlyPairs} (of those, the quest-only
   * ones) and the rule counters above, which count rows once per computed template pass.
   *
   * @return the number of (npc, item) pairs put
   */
  public static int load(ServerDbSource db, LongPredicate knownNpc, DropRates rates, ImportReport report)
      throws SQLException {
    Map<Long, List<LootRow>> loot = db.creatureLoot(PATCH);
    Map<Long, List<LootRow>> references = db.referenceLoot(PATCH);
    report.count("serverdb.loot.rows", loot.values().stream().mapToLong(List::size).sum());
    report.count("serverdb.reference.rows", references.values().stream().mapToLong(List::size).sum());
    report.count("serverdb.loot.forbidden", db.forbiddenRows());
    db.validityCounts().forEach(report::count);
    for (String counter : List.of("serverdb.creatures", "serverdb.unmatched.npcs", "serverdb.rates",
        "serverdb.rates.questOnly", "serverdb.rates.questOnlyPairs", "serverdb.zeroChance.ungrouped",
        "serverdb.references.zeroChance", "serverdb.references.depthCapped", "serverdb.references.missing")) {
      report.count(counter, 0);
    }
    ServerDropRates computer = new ServerDropRates(references, report);
    Map<Long, Map<Long, Drop>> tables = new HashMap<>();
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
      Map<Long, Drop> table =
          tables.computeIfAbsent(creature.getValue(), k -> computer.process(Template.of(rows), 0, 0));
      for (Map.Entry<Long, Drop> item : table.entrySet()) {
        Drop d = item.getValue();
        rates.put(npcId, item.getKey(), new DropRate(percent(d.probability()), SOURCE, d.questOnly()),
            d.questOnly() ? QUEST_ONLY_PRECEDENCE : PRECEDENCE);
        if (d.questOnly()) {
          report.count("serverdb.rates.questOnlyPairs", 1);
        }
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

  private static double chance(LootRow r) {
    return Math.min(100, Math.abs(r.chance())) / 100;
  }

  /**
   * One pass of {@code LootTemplate::Process} over {@code t}: with {@code group > 0} only that group rolls, with 0 the
   * ungrouped entries and every group. {@code depth} is the number of references followed to reach {@code t}.
   */
  private Map<Long, Drop> process(Template t, int group, int depth) {
    if (group > 0) {
      List<LootRow> rows = t.groups().get(group);
      return rows == null ? Map.of() : roll(rows);
    }
    Map<Long, Drop> miss = new HashMap<>(); // item -> (probability that nothing drops it, quest-only so far)
    for (LootRow r : t.entries()) {
      if (r.isReference()) {
        if (r.chance() == 0) {
          report.count("serverdb.references.zeroChance", 1);
          continue;
        }
        double p = chance(r);
        int times = Math.max(1, r.maxcount());
        for (Map.Entry<Long, Drop> e : reference(r.reference(), r.group(), depth + 1).entrySet()) {
          double q = p * (1 - Math.pow(1 - e.getValue().probability(), times));
          miss.merge(e.getKey(), new Drop(1 - q, e.getValue().questOnly()), ServerDropRates::both);
        }
      } else if (r.chance() == 0) {
        report.count("serverdb.zeroChance.ungrouped", 1);
      } else {
        if (r.chance() < 0) {
          report.count("serverdb.rates.questOnly", 1);
        }
        miss.merge(r.item(), new Drop(1 - chance(r), r.chance() < 0), ServerDropRates::both);
      }
    }
    for (List<LootRow> rows : t.groups().values()) {
      for (Map.Entry<Long, Drop> e : roll(rows).entrySet()) {
        miss.merge(e.getKey(), new Drop(1 - e.getValue().probability(), e.getValue().questOnly()),
            ServerDropRates::both);
      }
    }
    Map<Long, Drop> drops = new HashMap<>();
    miss.forEach((item, m) -> {
      if (m.probability() < 1) {
        drops.put(item, new Drop(1 - m.probability(), m.questOnly()));
      }
    });
    return drops;
  }

  /** One {@code LootGroup::Roll}: the item probabilities of a group, which exclude each other. */
  private Map<Long, Drop> roll(List<LootRow> rows) {
    Map<Long, Drop> drops = new HashMap<>();
    double left = 100;
    List<LootRow> equal = new ArrayList<>();
    for (LootRow r : rows) {
      if (r.chance() == 0) {
        equal.add(r);
        continue;
      }
      if (r.chance() < 0) {
        report.count("serverdb.rates.questOnly", 1);
      }
      double c = Math.abs(r.chance());
      double take = c >= 100 ? left : Math.min(c, left);
      left -= take;
      add(drops, r.item(), take / 100, r.chance() < 0);
    }
    for (LootRow r : equal) {
      add(drops, r.item(), left / equal.size() / 100, false);
    }
    drops.values().removeIf(d -> d.probability() <= 0);
    return drops;
  }

  /** Adds an exclusive share: an item listed twice in one group adds its shares. */
  private static void add(Map<Long, Drop> drops, long item, double p, boolean questOnly) {
    drops.merge(item, new Drop(p, questOnly),
        (a, b) -> new Drop(a.probability() + b.probability(), a.questOnly() && b.questOnly()));
  }

  /** Two independent misses of one item: both must miss; quest-only only when both ways are. */
  private static Drop both(Drop a, Drop b) {
    return new Drop(a.probability() * b.probability(), a.questOnly() && b.questOnly());
  }

  private Map<Long, Drop> reference(long id, int group, int depth) {
    if (depth > MAX_REFERENCE_DEPTH) {
      report.count("serverdb.references.depthCapped", 1);
      return Map.of();
    }
    PassKey key = new PassKey(id, group, depth);
    Map<Long, Drop> memo = passMemo.get(key);
    if (memo != null) {
      return memo;
    }
    List<LootRow> rows = referenceRows.get(id);
    if (rows == null) {
      report.count("serverdb.references.missing", 1);
      return Map.of();
    }
    Map<Long, Drop> result = process(references.computeIfAbsent(id, k -> Template.of(rows)), group, depth);
    passMemo.put(key, result);
    return result;
  }
}
