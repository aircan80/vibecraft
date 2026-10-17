package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.Route.Point;
import dev.wowforever.app.model.Spawn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** NPCs: detail pages and zone lists. */
public final class NpcRepository {
  private static final String SUMMARY = """
      SELECT n.id, n.name, n.sub_name, n.min_level, n.max_level, n.rank, n.zone_id, nz.name AS zone_name,
             n.npc_flags, n.friendly_to
      FROM npc n LEFT JOIN zone nz ON nz.id = n.zone_id""";

  private final Database db;

  public NpcRepository(Database db) {
    this.db = db;
  }

  /** The NPC page, or empty when there is no such NPC. */
  public Optional<NpcDetail> detail(long id) {
    return db.read(c -> {
      Optional<NpcDetail> head = Sql.one(c, SUMMARY + " WHERE n.id = ?",
          rs -> new NpcDetail(summary(rs), rs.getInt("npc_flags"), rs.getString("friendly_to"),
              List.of(), List.of(), List.of(), List.of(), List.of()),
          id);
      if (head.isEmpty()) {
        return head;
      }
      NpcDetail h = head.get();
      List<LootRow> drops = Sql.list(c, """
          SELECT d.item_id, i.name, i.item_level, d.drop_pct, d.origin, d.disagrees, d.source_count
          FROM item_npc_drop d LEFT JOIN item i ON i.id = d.item_id
          WHERE d.npc_id = ?
          ORDER BY d.drop_pct IS NULL, d.drop_pct DESC, i.name IS NULL, i.name COLLATE NOCASE, d.item_id""",
          rs -> new LootRow(new EntityRef(EntityKind.ITEM, rs.getLong("item_id")), rs.getString("name"),
              Sql.integer(rs, "item_level"), Sql.doubleValue(rs, "drop_pct"), rs.getString("origin"), null,
              rs.getInt("disagrees") != 0, rs.getInt("source_count")),
          id);
      List<LootRow> sells = Sql.list(c, """
          SELECT v.item_id, i.name, i.item_level
          FROM item_vendor v LEFT JOIN item i ON i.id = v.item_id
          WHERE v.npc_id = ?
          ORDER BY i.name IS NULL, i.name COLLATE NOCASE, v.item_id""",
          NpcRepository::plainLoot, id);
      return Optional.of(new NpcDetail(h.summary(), h.npcFlags(), h.friendlyTo(), drops, sells,
          QuestRepository.givenBy(c, "npc", id, "start"), QuestRepository.givenBy(c, "npc", id, "end"),
          spawns(c, "npc_spawn", "npc_id", id)));
    });
  }

  /** NPCs whose zone is {@code zoneId}, by name. */
  public List<NpcSummary> byZone(long zoneId) {
    return db.read(c -> byZone(c, zoneId, -1));
  }

  /** NPCs whose zone is {@code zoneId}, by name, at most {@code limit} (negative: all). */
  static List<NpcSummary> byZone(Connection c, long zoneId, int limit) throws SQLException {
    return Sql.list(c, SUMMARY + " WHERE n.zone_id = ? ORDER BY n.name IS NULL, n.name COLLATE NOCASE, n.id LIMIT ?",
        NpcRepository::summary, zoneId, limit);
  }

  /**
   * The patrol routes of NPC {@code npcId}, by zone and route, each in walking order; empty when it has none or the
   * database predates the npc_waypoint table.
   */
  public List<Route> routes(long npcId) {
    return db.read(c -> routes(c, "npc_id", npcId));
  }

  /**
   * The routes whose {@code column} (npc_id or zone_id) is {@code id}, ordered by NPC, zone, route and point; empty
   * on a database without the npc_waypoint table.
   */
  static List<Route> routes(Connection c, String column, long id) throws SQLException {
    if (!Sql.hasTable(c, "npc_waypoint")) {
      return List.of();
    }
    record Row(long npc, long zone, int route, Point point) {}
    List<Row> rows = Sql.list(c, "SELECT npc_id, zone_id, route, x, y FROM npc_waypoint WHERE " + column + " = ?"
            + " AND x IS NOT NULL AND y IS NOT NULL ORDER BY npc_id, zone_id, route, ordinal",
        rs -> new Row(rs.getLong("npc_id"), rs.getLong("zone_id"), rs.getInt("route"),
            new Point(rs.getDouble("x"), rs.getDouble("y"))),
        id);
    List<Route> routes = new ArrayList<>();
    int start = 0;
    for (int i = 1; i <= rows.size(); i++) {
      Row first = rows.get(start);
      if (i == rows.size() || rows.get(i).npc() != first.npc() || rows.get(i).zone() != first.zone()
          || rows.get(i).route() != first.route()) {
        List<Point> points = rows.subList(start, i).stream().map(Row::point).toList();
        routes.add(new Route(first.npc(), first.zone(), first.route(), points, Route.isLoop(points)));
        start = i;
      }
    }
    return routes;
  }

  /** An item row without drop chance, origin or slot ({@code item_id}, {@code name}, {@code item_level}). */
  static LootRow plainLoot(ResultSet rs) throws SQLException {
    return new LootRow(new EntityRef(EntityKind.ITEM, rs.getLong("item_id")), rs.getString("name"),
        Sql.integer(rs, "item_level"), null, null, null);
  }

  /** The spawn points of an NPC or object from {@code table} (npc_spawn or object_spawn), in import order. */
  static List<Spawn> spawns(Connection c, String table, String idColumn, long id) throws SQLException {
    return Sql.list(c, "SELECT s.zone_id, z.name AS zone_name, s.x, s.y FROM " + table + " s"
            + " LEFT JOIN zone z ON z.id = s.zone_id"
            + " WHERE s." + idColumn + " = ? AND s.x IS NOT NULL AND s.y IS NOT NULL ORDER BY s.rowid",
        rs -> new Spawn(rs.getLong("zone_id"), rs.getString("zone_name"), rs.getDouble("x"), rs.getDouble("y")),
        id);
  }

  private static NpcSummary summary(ResultSet rs) throws SQLException {
    return new NpcSummary(rs.getLong("id"), rs.getString("name"), rs.getString("sub_name"),
        Sql.integer(rs, "min_level"), Sql.integer(rs, "max_level"), Sql.integer(rs, "rank"),
        rs.getString("zone_name"), Sql.longValue(rs, "zone_id"));
  }
}
