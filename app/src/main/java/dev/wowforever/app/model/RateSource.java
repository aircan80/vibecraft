package dev.wowforever.app.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One drop rate source's value for an NPC and item ({@code drop_rate_source}): {@code source} is the importer's
 * name for it ({@code questie-wowhead}, {@code questie-correction}, {@code atlasloot}, {@code atlasloot-override})
 * and {@code pct} the rate in percent, null when the source gave none.
 */
public record RateSource(String source, Double pct) {
  /** The order sources are listed in: Questie's, then AtlasLoot's, then any other by name. */
  private static final List<String> KNOWN = List.of("questie", "questie-wowhead", "questie-correction", "atlasloot",
      "atlasloot-override");
  private static final Map<String, String> LABELS = Map.of("questie", "Questie", "questie-wowhead", "Questie",
      "questie-correction", "Questie correction", "atlasloot", "AtlasLoot", "atlasloot-override",
      "AtlasLoot override");

  /** Questie's sources first, then AtlasLoot's, then unknown sources by name. */
  public static final Comparator<RateSource> ORDER = Comparator
      .comparingInt((RateSource s) -> rank(s.source()))
      .thenComparing(RateSource::source);

  /** The NPC and item a drop rate is for. */
  public record Key(long npcId, long itemId) {}

  /** The NPC and item of each of NPC {@code npcId}'s {@code loot} rows with rate sources ({@code sourceCount > 0}). */
  public static List<Key> keys(long npcId, List<LootRow> loot) {
    return loot.stream().filter(l -> l.sourceCount() > 0).map(l -> new Key(npcId, l.item().id())).toList();
  }

  /** The source as the tooltip names it: {@code "Questie"}, {@code "AtlasLoot override"}; unknown ones as stored. */
  public String label() {
    return LABELS.getOrDefault(source, source);
  }

  private static int rank(String source) {
    int i = KNOWN.indexOf(source);
    return i < 0 ? KNOWN.size() : i;
  }
}
