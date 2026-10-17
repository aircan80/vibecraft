package dev.wowforever.app.db;
import dev.wowforever.app.model.InstanceFloorMap;
import java.util.List;
/** Optional map overlay, loaded entirely before rendering on the EDT. */
public final class InstanceMapRepository {
 private final Database db;
 public InstanceMapRepository(Database db) { this.db=db; }
 public List<InstanceFloorMap> load(long instanceId) {
  return db.read(c -> {
   if (!Sql.hasTable(c,"instance_map")) return List.of();
   var maps=Sql.list(c,"SELECT floor,label,jpeg,forever FROM instance_map WHERE instance_id=? ORDER BY ordinal",rs -> new InstanceFloorMap(rs.getString(1),rs.getString(2),rs.getBytes(3),rs.getInt(4)!=0,List.of()),instanceId);
   var result=new java.util.ArrayList<InstanceFloorMap>();
   for (var map:maps) {
    var legend=Sql.list(c,"SELECT number,name,npc_id FROM instance_map_legend WHERE instance_id=? AND floor=? ORDER BY ordinal",rs -> new InstanceFloorMap.Legend(rs.getString(1),rs.getString(2),Sql.longValue(rs,"npc_id")),instanceId,map.floor());
    result.add(new InstanceFloorMap(map.floor(),map.label(),map.jpeg(),map.forever(),legend));
   }
   return List.copyOf(result);
  });
 }
}
