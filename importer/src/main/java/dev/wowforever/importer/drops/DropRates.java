package dev.wowforever.importer.drops;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Drop rates keyed by (npc, item). Lower precedence numbers win. Item-level overrides apply to every
 * npc that drops the item and compete with the per-pair entries on the same precedence scale.
 */
public final class DropRates {
  private record Entry(DropRate rate, int precedence) {}

  private record Pair(long npcId, long itemId) {}

  private final Map<Pair, Entry> pairs = new HashMap<>();
  private final Map<Long, Entry> items = new HashMap<>();

  public void put(long npcId, long itemId, DropRate rate, int precedence) {
    pairs.merge(new Pair(npcId, itemId), new Entry(rate, precedence), DropRates::best);
  }

  /** Records a rate for an item that applies to every npc dropping it. */
  public void putItem(long itemId, DropRate rate, int precedence) {
    items.merge(itemId, new Entry(rate, precedence), DropRates::best);
  }

  public Optional<DropRate> get(long npcId, long itemId) {
    Entry pair = pairs.get(new Pair(npcId, itemId));
    Entry item = items.get(itemId);
    if (pair == null && item == null) {
      return Optional.empty();
    }
    if (pair == null) {
      return Optional.of(item.rate());
    }
    if (item == null) {
      return Optional.of(pair.rate());
    }
    return Optional.of((item.precedence() < pair.precedence() ? item : pair).rate());
  }

  /** Number of explicit (npc, item) entries; item-level overrides are not counted. */
  public int size() {
    return pairs.size();
  }

  private static Entry best(Entry existing, Entry candidate) {
    return candidate.precedence() < existing.precedence() ? candidate : existing;
  }
}
