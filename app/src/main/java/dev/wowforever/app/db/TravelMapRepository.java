package dev.wowforever.app.db;

import dev.wowforever.app.model.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Additive travel geography, read from the archived-source projection in the application database. */
public final class TravelMapRepository {
  private final Database db;
  public TravelMapRepository(Database db) { this.db = db; }

  public TravelMapData inZone(long zoneId) {
    return db.read(c -> {
      if (!available(c)) return TravelMapData.EMPTY;
      var entrances = Sql.list(c, "SELECT * FROM map_instance_access WHERE zone_id=? ORDER BY is_exit,name,trigger_id",
          TravelMapRepository::entrance, zoneId);
      var flights = Sql.list(c, "SELECT * FROM map_flight_node WHERE zone_id=? ORDER BY name,node_id", rs -> {
        long id = rs.getLong("node_id");
        var destinations = Sql.list(c, "SELECT DISTINCT to_name FROM map_flight_connection WHERE from_node_id=? ORDER BY to_name",
            r -> r.getString(1), id);
        Long npc = Sql.longValue(rs,"npc_id");
        return new TravelMapData.FlightNode(id,rs.getString("name"),rs.getDouble("x"),rs.getDouble("y"),
            TravelMapData.Faction.valueOf(rs.getString("faction")),npc == null ? null : new EntityRef(EntityKind.NPC,npc),destinations);
      },zoneId);
      record RouteKey(long pathId,int segment) {}
      record RouteRow(RouteKey key,String destination,double x,double y) {}
      var rows = Sql.list(c,"SELECT path_id,segment_index,destination,x,y FROM map_flight_route WHERE zone_id=? ORDER BY path_id,segment_index,point_index",
          rs -> new RouteRow(new RouteKey(rs.getLong(1),rs.getInt(2)),rs.getString(3),rs.getDouble(4),rs.getDouble(5)),zoneId);
      Map<RouteKey,List<Route.Point>> points = new LinkedHashMap<>();
      Map<RouteKey,String> destinations = new LinkedHashMap<>();
      for (var row : rows) {
        points.computeIfAbsent(row.key(),k -> new ArrayList<>()).add(new Route.Point(row.x(),row.y()));
        destinations.putIfAbsent(row.key(),row.destination());
      }
      var routes = points.entrySet().stream().filter(e -> e.getValue().size()>=2)
          .map(e -> new TravelMapData.FlightRoute((e.getKey().pathId()<<32)|(e.getKey().segment()&0xffffffffL),destinations.get(e.getKey()),e.getValue())).toList();
      String sourceLabel = Sql.one(c,"SELECT value FROM meta WHERE key='map_layers_source_label'",rs -> rs.getString(1)).orElse("");
      return new TravelMapData(entrances,flights,routes,sourceLabel);
    });
  }

  public InstanceAccess forInstance(long instanceId) {
    return db.read(c -> {
      if (!available(c)) return InstanceAccess.EMPTY;
      var locations = Sql.list(c,"""
          SELECT a.*, z.name AS zone_name FROM map_instance_access a
          JOIN zone z ON z.id=a.zone_id WHERE a.instance_id=? AND a.is_exit=0 ORDER BY a.name,a.zone_id,a.trigger_id
          """,rs -> {
            long zone = rs.getLong("zone_id");
            return new InstanceAccess.Location(zone,rs.getString("zone_name"),entrance(rs),Sql.one(c,"""
                SELECT m.ui_map_id,m.width,m.height,m.jpeg FROM zone z JOIN zone_map m ON m.ui_map_id=z.ui_map_id WHERE z.id=?
                """, r -> new ZoneMap(r.getLong(1),r.getInt(2),r.getInt(3),r.getBytes(4)),zone));
          },instanceId);
      var parents = Sql.list(c,"""
          SELECT DISTINCT i.id,i.name FROM map_instance_entry_context a JOIN instance i ON i.id=a.parent_instance_id
          WHERE a.instance_id=? ORDER BY i.name,i.id
          """,rs -> new InstanceAccess.Parent(rs.getLong(1),rs.getString(2)),instanceId);
      return new InstanceAccess(locations,parents);
    });
  }

  private static TravelMapData.Entrance entrance(java.sql.ResultSet rs) throws SQLException {
    return new TravelMapData.Entrance(new EntityRef(EntityKind.INSTANCE,rs.getLong("instance_id")),rs.getString("name"),
        rs.getDouble("x"),rs.getDouble("y"),rs.getBoolean("raid"),rs.getBoolean("is_exit"),
        rs.getBoolean("exit_here"),rs.getString("tooltip"));
  }
  private static boolean available(Connection c) throws SQLException {
    return Sql.one(c,"SELECT 1 FROM sqlite_master WHERE type='table' AND name='map_instance_access'",rs -> true).orElse(false);
  }
}
