package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRates;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The (item, npc) drop pairs from Questie {@code npcDrops} and AtlasLoot boss loot, with their origin:
 * {@code questie}, {@code atlasloot}, or {@code both} once the two sources agree on a pair.
 */
final class NpcDrops {
  static final String QUESTIE = "questie";
  static final String ATLASLOOT = "atlasloot";
  static final String BOTH = "both";

  private record Pair(long itemId, long npcId) {}

  private final Map<Pair, String> origins = new LinkedHashMap<>();

  void add(long itemId, long npcId, String origin) {
    origins.merge(new Pair(itemId, npcId), origin, (a, b) -> a.equals(b) ? a : BOTH);
  }

  /** Inserts every pair with its drop rate, if {@code rates} knows one. */
  void write(Connection c, DropRates rates) throws SQLException {
    try (Batch rows = new Batch(c,
        "INSERT INTO item_npc_drop(item_id, npc_id, drop_pct, pct_source, origin) VALUES (?, ?, ?, ?, ?)")) {
      for (Map.Entry<Pair, String> e : origins.entrySet()) {
        Pair p = e.getKey();
        Optional<DropRate> rate = rates.get(p.npcId(), p.itemId());
        rows.add(p.itemId(), p.npcId(), rate.map(DropRate::percent).orElse(null),
            rate.map(DropRate::source).orElse(null), e.getValue());
      }
    }
  }
}
