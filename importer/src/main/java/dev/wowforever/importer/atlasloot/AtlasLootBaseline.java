package dev.wowforever.importer.atlasloot;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Compares an AtlasLoot pin with the pin it replaced, the AtlasLoot Classic Forever fork with Hoizame's
 * AtlasLootClassic (Task 19, pm-data-sources.md). Loot membership stays a union: a boss row (boss NPC, item) the
 * baseline had and the fork dropped is kept while Questie or VMaNGOS still list the pair, appended to the fork's boss
 * of that NPC (or, when the fork lost the boss, to the instance of the same key or InstanceID as the baseline's boss
 * with only the kept rows). Rows of bosses without an NPC id (trash, sets) are not compared.
 *
 * <p>Counters: {@value #REMOVED} (pairs the baseline had and the fork lacks), {@value #KEPT} and {@value #DROPPED}
 * (of those, listed elsewhere or not), {@value #UNPLACED} (kept pairs whose boss and instance the fork lacks; the pair
 * still reaches the drop lists through Questie or VMaNGOS) and {@value #ADDITIONS} (pairs the fork adds).
 */
public final class AtlasLootBaseline {
  public static final String REMOVED = "atlasloot.forever.removedVsHoizame";
  public static final String KEPT = REMOVED + ".kept";
  public static final String DROPPED = REMOVED + ".dropped";
  public static final String UNPLACED = REMOVED + ".unplaced";
  public static final String ADDITIONS = "atlasloot.forever.additions";

  private record Pair(long npcId, long itemId) {}

  private AtlasLootBaseline() {}

  /**
   * The fork's instances with the kept baseline rows added.
   *
   * @param listedElsewhere whether Questie or VMaNGOS list (npc, item)
   */
  public static List<AtlasInstance> merge(List<AtlasInstance> fork, List<AtlasInstance> baseline,
      BiPredicate<Long, Long> listedElsewhere, ImportReport report) {
    Set<Pair> forkPairs = pairs(fork);
    Set<Pair> basePairs = pairs(baseline);
    long additions = forkPairs.stream().filter(p -> !basePairs.contains(p)).count();

    // the kept rows of each baseline boss, in its order
    Map<AtlasBoss, List<AtlasLoot>> kept = new LinkedHashMap<>();
    Map<AtlasBoss, AtlasInstance> baseInstanceOf = new LinkedHashMap<>();
    Set<Pair> removed = new HashSet<>();
    Set<Pair> keptPairs = new HashSet<>();
    for (AtlasInstance i : baseline) {
      for (AtlasBoss b : i.bosses()) {
        if (b.npcId() == null) {
          continue;
        }
        for (AtlasLoot l : b.loot()) {
          Pair p = new Pair(b.npcId(), l.itemId());
          if (forkPairs.contains(p)) {
            continue;
          }
          removed.add(p);
          if (listedElsewhere.test(p.npcId(), p.itemId())) {
            keptPairs.add(p);
            kept.computeIfAbsent(b, k -> new ArrayList<>()).add(l);
            baseInstanceOf.put(b, i);
          }
        }
      }
    }

    List<List<AtlasBoss>> bosses = new ArrayList<>();
    for (AtlasInstance i : fork) {
      bosses.add(new ArrayList<>(i.bosses()));
    }
    Set<Pair> unplaced = new HashSet<>();
    for (Map.Entry<AtlasBoss, List<AtlasLoot>> e : kept.entrySet()) {
      AtlasBoss b = e.getKey();
      int[] at = bossWithNpc(bosses, b.npcId());
      if (at != null) {
        AtlasBoss f = bosses.get(at[0]).get(at[1]);
        List<AtlasLoot> loot = new ArrayList<>(f.loot());
        loot.addAll(e.getValue());
        bosses.get(at[0]).set(at[1], new AtlasBoss(f.name(), f.npcId(), f.objectId(), f.level(), f.specialType(),
            f.extraList(), f.sortOrder(), List.copyOf(loot)));
        continue;
      }
      int instance = instanceLike(fork, baseInstanceOf.get(b));
      if (instance < 0) {
        e.getValue().forEach(l -> unplaced.add(new Pair(b.npcId(), l.itemId())));
        continue;
      }
      List<AtlasBoss> list = bosses.get(instance);
      list.add(new AtlasBoss(b.name(), b.npcId(), b.objectId(), b.level(), b.specialType(), b.extraList(),
          list.size() + 1, List.copyOf(e.getValue())));
    }

    List<AtlasInstance> out = new ArrayList<>(fork.size());
    for (int i = 0; i < fork.size(); i++) {
      AtlasInstance f = fork.get(i);
      out.add(new AtlasInstance(f.key(), f.name(), f.mapId(), f.instanceId(), f.contentType(), f.levelMin(),
          f.levelMax(), List.copyOf(bosses.get(i))));
    }
    report.count(REMOVED, removed.size());
    report.count(KEPT, keptPairs.size());
    report.count(DROPPED, removed.size() - keptPairs.size());
    report.count(UNPLACED, unplaced.size());
    report.count(ADDITIONS, additions);
    return List.copyOf(out);
  }

  private static Set<Pair> pairs(List<AtlasInstance> instances) {
    Set<Pair> out = new HashSet<>();
    for (AtlasInstance i : instances) {
      for (AtlasBoss b : i.bosses()) {
        if (b.npcId() != null) {
          b.loot().forEach(l -> out.add(new Pair(b.npcId(), l.itemId())));
        }
      }
    }
    return out;
  }

  /** {instance index, boss index} of the first fork boss of {@code npcId}, or null. */
  private static int[] bossWithNpc(List<List<AtlasBoss>> bosses, long npcId) {
    for (int i = 0; i < bosses.size(); i++) {
      for (int j = 0; j < bosses.get(i).size(); j++) {
        if (Objects.equals(bosses.get(i).get(j).npcId(), npcId)) {
          return new int[] {i, j};
        }
      }
    }
    return null;
  }

  /** The index of the fork instance with the key, else the InstanceID, of {@code base}; -1 when none. */
  private static int instanceLike(List<AtlasInstance> fork, AtlasInstance base) {
    for (int i = 0; i < fork.size(); i++) {
      if (fork.get(i).key().equals(base.key())) {
        return i;
      }
    }
    for (int i = 0; i < fork.size(); i++) {
      if (base.instanceId() != null && base.instanceId().equals(fork.get(i).instanceId())) {
        return i;
      }
    }
    return -1;
  }
}
