package dev.wowforever.app.db;

import dev.wowforever.app.model.LootRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded exact numeric facts, loaded with the loot snapshot, never while changing filters. */
final class LootResistances {
  private LootResistances() {}
  static List<LootRow> attach(Connection c, List<LootRow> loot) throws SQLException {
    if (loot.isEmpty() || !Sql.hasTable(c,"item_stat")) return loot;
    var ids=loot.stream().map(r->r.item().id()).distinct().toList();
    Map<Long,Map<Integer,Integer>> values=new HashMap<>();
    for (int offset=0;offset<ids.size();offset+=450) {
      var chunk=ids.subList(offset,Math.min(offset+450,ids.size()));
      String placeholders=String.join(",",java.util.Collections.nCopies(chunk.size(),"?"));
      // item_stat(item_id) index plus item PK: no whole-table/native ItemSparse expansion.
      String sql="SELECT s.item_id,s.stat_id,SUM(s.value) AS value FROM item_stat s JOIN item i ON i.id=s.item_id"
          + " WHERE s.item_id IN ("+placeholders+") AND i.has_client_data=1"
          + " AND i.client_source IN ('forever','hotfix') AND (s.stat_id BETWEEN 51 AND 56 OR s.stat_id=124)"
          + " GROUP BY s.item_id,s.stat_id";
      try(var statement=c.prepareStatement(sql)) {
        for(int n=0;n<chunk.size();n++) statement.setLong(n+1,chunk.get(n));
        try(var rows=statement.executeQuery()) {
          while(rows.next()) values.computeIfAbsent(rows.getLong("item_id"),id->new HashMap<>())
              .put(rows.getInt("stat_id"),rows.getInt("value"));
        }
      }
    }
    var result=new ArrayList<LootRow>(loot.size());
    for(var row:loot) result.add(row.withResistanceValues(values.getOrDefault(row.item().id(),Map.of())));
    return List.copyOf(result);
  }
}
