package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The primary oracle (controller ruling 2): items that exist in both Forever's {@code ItemSparse} and the Era
 * fallback. Each item's stats, damage and armour are computed from its Forever row (budget tables) and compared with
 * the Era row's absolute values.
 *
 * <p>Forever rebalanced some items, so a comparison is "unchanged" only when the inputs that decide the category
 * agree between the builds; the rate that gates the formula is the exact-match rate over unchanged items, and the raw
 * rate over every compared item is reported beside it.
 *
 * <ul>
 *   <li>stats: Forever's computed stats restricted to the ids Era rows can carry ({@link #ERA_STAT_IDS}; Era puts
 *       ratings and spell power into equip spells) against Era's amounts and {@code Resistances_1..6}. Unchanged:
 *       same item level, quality and inventory type, and the same stat ids.
 *   <li>damage: min and max. Unchanged: same item level, quality, inventory type, delay and variance, and both or
 *       neither having damage. Forever caster weapons (unverified, no damage written) are left out and counted.
 *   <li>armour: Forever's base armour plus its bonus armour stat (50) against Era's {@code Resistances_0}, which
 *       holds both. Unchanged: same item level, quality and inventory type, both or neither having armour, and not a
 *       Forever-added bonus (Era equal to the base alone while Forever has stat 50).
 * </ul>
 *
 * NPC-only display weapons ({@code "Monster - "} names, e.g. "Monster - Item, Tankard Wooden") are left out of every
 * category and counted in {@link Category#npcOnly}: their Era damage is hand-set (7 - 8 on an item level 1 tankard)
 * and no player ever sees their tooltip. With them, the real damage rate is 94.98 % (task 5 report).
 */
final class EraOverlapOracle {
  static final String NPC_ONLY_PREFIX = "Monster - ";
  static final Set<Integer> ERA_STAT_IDS = Set.of(0, 1, 3, 4, 5, 6, 7, 38, 51, 52, 53, 54, 55, 56);
  static final int MISMATCHES_KEPT = 20;

  /** One category's result. */
  static final class Category {
    final String name;
    int compared;
    int exact;
    int unchanged;
    int unchangedExact;
    int excluded;
    int npcOnly;
    final List<String> mismatches = new ArrayList<>();
    final List<String> changedSamples = new ArrayList<>();

    Category(String name) {
      this.name = name;
    }

    double rate() {
      return unchanged == 0 ? 1.0 : (double) unchangedExact / unchanged;
    }

    double rawRate() {
      return compared == 0 ? 1.0 : (double) exact / compared;
    }

    void add(boolean isExact, boolean isUnchanged, String detail) {
      compared++;
      exact += isExact ? 1 : 0;
      if (isUnchanged) {
        unchanged++;
        unchangedExact += isExact ? 1 : 0;
        if (!isExact && mismatches.size() < MISMATCHES_KEPT) {
          mismatches.add(detail);
        }
      } else if (changedSamples.size() < 5) {
        changedSamples.add(detail);
      }
    }

    @Override
    public String toString() {
      return String.format("%-6s unchanged %d/%d exact (%.2f%%), raw %d/%d (%.2f%%), changed %d, excluded %d,"
          + " npc-only %d", name, unchangedExact, unchanged, 100 * rate(), exact, compared, 100 * rawRate(),
          compared - unchanged, excluded, npcOnly);
    }
  }

  private EraOverlapOracle() {}

  static Map<String, Category> run(ClientDataSource src) {
    Set<Long> ids = new TreeSet<>(src.table(ItemSparseLoader.SPARSE).indexByLong("ID").keySet());
    ids.retainAll(src.table(ItemSparseLoader.ERA_SPARSE).indexByLong("ID").keySet());
    Map<Long, ClientItem> forever = ItemSparseLoader.load(src, ids, new ImportReport());
    Map<Long, ClientItem> era = ItemSparseLoader.loadEra(src, ids);
    ScalingTables tables = ScalingTables.load(src);
    ImportReport scratch = new ImportReport();

    Category stats = new Category("stats");
    Category damage = new Category("damage");
    Category armor = new Category("armor");
    for (Map.Entry<Long, ClientItem> e : era.entrySet()) {
      ClientItem f = forever.get(e.getKey());
      ClientItem a = e.getValue();
      if (f == null || !ClientItem.FOREVER.equals(f.clientSource())) {
        continue;
      }
      if (f.name() != null && f.name().startsWith(NPC_ONLY_PREFIX)) {
        stats.npcOnly++;
        damage.npcOnly++;
        armor.npcOnly++;
        continue;
      }
      boolean core = Objects.equals(f.itemLevel(), a.itemLevel()) && Objects.equals(f.quality(), a.quality())
          && Objects.equals(f.inventoryType(), a.inventoryType());
      String head = e.getKey() + " " + f.name() + " (il " + f.itemLevel() + ", q " + f.quality() + ", inv "
          + f.inventoryType() + ")";

      List<ItemStat> fStats = ItemStatsCalculator.stats(f, tables, scratch);
      Map<Integer, Integer> fs = new TreeMap<>();
      fStats.stream().filter(s -> ERA_STAT_IDS.contains(s.statId())).forEach(s -> fs.put(s.statId(), s.value()));
      Map<Integer, Integer> es = new TreeMap<>();
      ItemStatsCalculator.stats(a, tables, scratch).forEach(s -> es.merge(s.statId(), s.value(), Integer::sum));
      if (!fs.isEmpty() || !es.isEmpty()) {
        stats.add(fs.equals(es), core && fs.keySet().equals(es.keySet()),
            head + ": forever " + fs + " era " + es);
      }

      Optional<ItemDamage> ed = ItemDamageCalculator.damage(a, tables, scratch);
      if (ItemDamageCalculator.isForeverCaster(f)) {
        if (ed.isPresent()) {
          damage.excluded++;
        }
      } else {
        Optional<ItemDamage> fd = ItemDamageCalculator.damage(f, tables, scratch);
        if (fd.isPresent() || ed.isPresent()) {
          boolean exact = fd.isPresent() && ed.isPresent() && fd.get().min() == ed.get().min()
              && fd.get().max() == ed.get().max();
          boolean unchanged = core && Objects.equals(f.delayMs(), a.delayMs())
              && Math.abs(nz(f.dmgVariance()) - nz(a.dmgVariance())) < 1e-6 && fd.isPresent() == ed.isPresent();
          damage.add(exact, unchanged, head + ": forever " + range(fd) + " era " + range(ed) + " (delay "
              + f.delayMs() + "/" + a.delayMs() + ", variance " + f.dmgVariance() + "/" + a.dmgVariance() + ")");
        }
      }

      int base = ItemArmorCalculator.armor(f, tables, scratch).map(ItemArmor::armor).orElse(0);
      int bonus = fStats.stream().filter(s -> s.statId() == 50).mapToInt(ItemStat::value).sum();
      int eraArmor = a.absolute().armor();
      if (base + bonus > 0 || eraArmor > 0) {
        boolean unchanged = core && (base + bonus > 0) == (eraArmor > 0) && !(bonus > 0 && eraArmor == base);
        armor.add(base + bonus == eraArmor, unchanged,
            head + ": forever " + base + (bonus != 0 ? " + bonus " + bonus : "") + " era " + eraArmor);
      }
    }
    Map<String, Category> out = new LinkedHashMap<>();
    out.put(stats.name, stats);
    out.put(damage.name, damage);
    out.put(armor.name, armor);
    return out;
  }

  static String render(Map<String, Category> result) {
    StringBuilder sb = new StringBuilder();
    for (Category c : result.values()) {
      sb.append(c).append('\n');
      for (String m : c.mismatches) {
        sb.append("    mismatch ").append(m).append('\n');
      }
    }
    return sb.toString();
  }

  private static double nz(Double d) {
    return d == null ? 0 : d;
  }

  private static String range(Optional<ItemDamage> d) {
    return d.map(x -> x.min() + "-" + x.max()).orElse("none");
  }
}
