package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.report.Conflict;
import dev.wowforever.importer.report.ImportReport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects every source's drop rate for the (npc, item) pairs written to {@code item_npc_drop} and
 * {@code boss_loot}, then writes each pair's sources once into {@code drop_rate_source} and feeds the
 * conflict report. Two values disagree when they differ by more than {@value #THRESHOLD} percentage points.
 */
final class DropProvenance {
  static final double THRESHOLD = 2.0;

  /** Source count and disagreement flag of one pair, as stored on the drop rows. */
  record Stat(int sourceCount, int disagrees) {}

  private record Pair(long npcId, long itemId) implements Comparable<Pair> {
    @Override
    public int compareTo(Pair o) {
      int byNpc = Long.compare(npcId, o.npcId);
      return byNpc != 0 ? byNpc : Long.compare(itemId, o.itemId);
    }
  }

  private final DropRates rates;
  private final Map<Pair, List<DropRate>> pairs = new TreeMap<>();

  DropProvenance(DropRates rates) {
    this.rates = rates;
  }

  /** Registers the pair (once) and returns its stat; pairs without any value are not written. */
  Stat stat(long npcId, long itemId) {
    List<DropRate> sources = rates.allSources(npcId, itemId);
    if (!sources.isEmpty()) {
      pairs.putIfAbsent(new Pair(npcId, itemId), sources);
    }
    return new Stat(sources.size(), disagree(sources) ? 1 : 0);
  }

  private static boolean differ(DropRate a, DropRate b) {
    return Math.abs(a.percent() - b.percent()) > THRESHOLD;
  }

  static boolean disagree(List<DropRate> sources) {
    for (int i = 0; i < sources.size(); i++) {
      for (int j = i + 1; j < sources.size(); j++) {
        if (differ(sources.get(i), sources.get(j))) {
          return true;
        }
      }
    }
    return false;
  }

  void write(Connection c, ImportReport report) throws SQLException {
    report.count("provenance.pairs", 0);
    report.count("provenance.disagreeing", 0);
    try (Batch rows = new Batch(c, "INSERT INTO drop_rate_source(npc_id, item_id, source, pct) VALUES (?, ?, ?, ?)")) {
      for (Map.Entry<Pair, List<DropRate>> e : pairs.entrySet()) {
        Pair p = e.getKey();
        List<DropRate> sources = e.getValue();
        for (DropRate r : sources) {
          rows.add(p.npcId(), p.itemId(), r.source(), r.percent());
          report.count("provenance.sources." + r.source(), 1);
        }
        report.count("provenance.pairs", 1);
        for (int i = 0; i < sources.size(); i++) {
          for (int j = i + 1; j < sources.size(); j++) {
            report.compared(sources.get(i).source(), sources.get(j).source(), differ(sources.get(i), sources.get(j)));
          }
        }
        if (disagree(sources)) {
          report.count("provenance.disagreeing", 1);
          Map<String, Double> values = new LinkedHashMap<>();
          sources.forEach(r -> values.put(r.source(), r.percent()));
          report.conflict(new Conflict(p.npcId(), p.itemId(), values));
        }
      }
    }
  }
}
