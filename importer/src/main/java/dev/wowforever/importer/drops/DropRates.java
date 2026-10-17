package dev.wowforever.importer.drops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drop rates keyed by (npc, item). Lower precedence numbers win. Item-level overrides apply to every
 * npc that drops the item and compete with the per-pair entries on the same precedence scale.
 */
public final class DropRates {
  private record Entry(DropRate rate, int precedence) {}

  private record Pair(long npcId, long itemId) {}

  private final Map<Pair, List<Entry>> pairs = new HashMap<>();
  private final Map<Long, Entry> items = new HashMap<>();

  public void put(long npcId, long itemId, DropRate rate, int precedence) {
    List<Entry> entries = pairs.computeIfAbsent(new Pair(npcId, itemId), k -> new ArrayList<>());
    Entry candidate = new Entry(rate, precedence);
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).precedence() == precedence) {
        entries.set(i, best(entries.get(i), candidate));
        return;
      }
    }
    entries.add(candidate);
  }

  /** Records a rate for an item that applies to every npc dropping it. */
  public void putItem(long itemId, DropRate rate, int precedence) {
    items.merge(itemId, new Entry(rate, precedence), DropRates::best);
  }

  public Optional<DropRate> get(long npcId, long itemId) {
    return all(npcId, itemId).stream().findFirst().map(Entry::rate);
  }

  /**
   * Every source's rate for the pair: the per-pair entries plus the item-level override when present,
   * ordered by precedence (the winner first).
   */
  public List<DropRate> allSources(long npcId, long itemId) {
    return all(npcId, itemId).stream().map(Entry::rate).toList();
  }

  private List<Entry> all(long npcId, long itemId) {
    List<Entry> result = new ArrayList<>(pairs.getOrDefault(new Pair(npcId, itemId), List.of()));
    Entry item = items.get(itemId);
    if (item != null) {
      result.add(item);
    }
    result.sort(Comparator.comparingInt(Entry::precedence));
    return result;
  }

  /** Number of explicit (npc, item) entries; item-level overrides are not counted. */
  public int size() {
    return pairs.size();
  }

  private static Entry best(Entry existing, Entry candidate) {
    return candidate.precedence() < existing.precedence() ? candidate : existing;
  }
}
