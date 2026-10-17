package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.Route.Point;
import dev.wowforever.app.model.Spawn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** NPCs: detail pages, filtered lists and zone lists. */
public final class NpcRepository {
  /**
   * The rank an NPC {@code n} is shown and filtered with: Boss (3) for an AtlasLoot encounter of an instance's main
   * list that AtlasLoot gives no special type (Questie calls some of them elite or rare elite, Edwin VanCleef
   * among them), else Questie's rank. An encounter AtlasLoot itself calls rare or elite keeps Questie's rank, as the
   * instance page reads it. The {@code IN} list is built once per query.
   */
  static final String RANK = """
      CASE WHEN n.id IN (SELECT b.npc_id FROM boss b WHERE b.npc_id IS NOT NULL AND b.is_extra_list = 0
                         AND (b.special_type IS NULL OR b.special_type = '')) THEN 3 ELSE n.rank END""";
  private static final String SUMMARY = "SELECT n.id, n.name, n.sub_name, n.min_level, n.max_level, " + RANK
      + " AS rank, n.zone_id, nz.name AS zone_name, n.npc_flags, n.friendly_to"
      + " FROM npc n LEFT JOIN zone nz ON nz.id = n.zone_id";

  /**
   * The item columns a {@link LootRow} reads ({@link #itemJoin} must be joined): name, level, class, quality, icon,
   * class mask and the tooltip's required-skill line.
   */
  static final String ITEM_COLUMNS = """
      i.name, i.item_level, i.class_id, ic.name AS class_name, i.subclass_id, i.quality, i.icon_id, \
      i.allowable_class, (SELECT t.text FROM item_tooltip_line t WHERE t.item_id = i.id AND t.kind = 'req_skill' \
      ORDER BY t.ordinal LIMIT 1) AS skill_line""";

  /** How many NPC ids one {@link #bosses} query asks about, each bound once: under SQLite's classic 999 limit. */
  static final int BOSS_CHUNK = 900;

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
      // dropper_count: how many NPCs drop the item (a primary-key prefix count), which tells the world drops
      List<LootRow> drops = Sql.list(c, "SELECT d.item_id, " + ITEM_COLUMNS
          + ", d.drop_pct, d.pct_source, d.origin, d.disagrees, d.source_count,"
          + " (SELECT count(*) FROM item_npc_drop x WHERE x.item_id = d.item_id) AS dropper_count"
          + " FROM item_npc_drop d " + itemJoin("d") + """

          WHERE d.npc_id = ?
          ORDER BY d.drop_pct IS NULL, d.drop_pct DESC, i.name IS NULL, i.name COLLATE NOCASE, d.item_id""",
          rs -> withDropperCount(ratedLoot(rs, rs.getString("origin"), null), rs.getInt("dropper_count")),
          id);
      List<LootRow> sells = Sql.list(c, "SELECT v.item_id, " + ITEM_COLUMNS
          + " FROM item_vendor v " + itemJoin("v") + """

          WHERE v.npc_id = ?
          ORDER BY i.name IS NULL, i.name COLLATE NOCASE, v.item_id""",
          NpcRepository::plainLoot, id);
      return Optional.of(new NpcDetail(h.summary(), h.npcFlags(), h.friendlyTo(), drops, sells,
          QuestRepository.givenBy(c, "npc", id, "start"), QuestRepository.givenBy(c, "npc", id, "end"),
          spawns(c, "npc_spawn", "npc_id", id)));
    });
  }

  /** The spawn points of NPC {@code npcId} with coordinates, in import order. */
  public List<Spawn> spawnsOf(long npcId) {
    return db.read(c -> spawns(c, "npc_spawn", "npc_id", npcId));
  }

  /**
   * The NPCs among {@code npcIds} whose Vanilla rates read "Forever loot reworked" (decision 19): an instance's
   * encounter of a dungeon or raid ({@code boss.npc_id} of an instance with a zone: AtlasLoot's "World Bosses" list
   * has none, and open-world bosses keep their rates unnoted), or an NPC whose zone is a dungeon or raid. Each id is
   * bound once, at most {@value #BOSS_CHUNK} per query (SQLite's classic limit is 999 parameters; an item's droppers
   * can run to well over a thousand); no ids, no query.
   */
  public Set<Long> bosses(Collection<Long> npcIds) {
    List<Long> ids = List.copyOf(new LinkedHashSet<>(npcIds));
    if (ids.isEmpty()) {
      return Set.of();
    }
    return db.read(c -> {
      Set<Long> bosses = new HashSet<>();
      for (int from = 0; from < ids.size(); from += BOSS_CHUNK) {
        List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + BOSS_CHUNK));
        String values = String.join(", ", Collections.nCopies(chunk.size(), "(?)"));
        bosses.addAll(Sql.list(c, "WITH ids(id) AS (VALUES " + values + ")"
            + " SELECT b.npc_id AS id FROM boss b JOIN ids ON ids.id = b.npc_id"
            + " JOIN instance i ON i.id = b.instance_id WHERE i.zone_id IS NOT NULL"
            + " UNION SELECT n.id FROM npc n JOIN ids ON ids.id = n.id JOIN zone z ON z.id = n.zone_id"
            + " WHERE z.kind IN ('dungeon', 'raid')",
            rs -> rs.getLong("id"), chunk.toArray()));
      }
      return Set.copyOf(bosses);
    });
  }

  /** NPCs whose zone is {@code zoneId}, by name. */
  public List<NpcSummary> byZone(long zoneId) {
    return db.read(c -> byZone(c, zoneId, -1));
  }

  /** NPCs whose zone is {@code zoneId}, by name, at most {@code limit} (negative: all). */
  static List<NpcSummary> byZone(Connection c, long zoneId, int limit) throws SQLException {
    return byZone(c, zoneId, limit, false);
  }

  /**
   * {@link #byZone(Connection, long, int)}; with {@code outdoorsOnly}, NPCs whose every spawn point is an instance
   * placeholder (inside a dungeon of the zone, not on its map) are left out.
   */
  static List<NpcSummary> byZone(Connection c, long zoneId, int limit, boolean outdoorsOnly) throws SQLException {
    String outdoors = !outdoorsOnly ? "" : " AND (NOT EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_id = n.id)"
        + " OR EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_id = n.id AND s.x >= 0 AND s.y >= 0))";
    return Sql.list(c, SUMMARY + " WHERE n.zone_id = ?" + outdoors
            + " ORDER BY n.name IS NULL, n.name COLLATE NOCASE, n.id LIMIT ?",
        NpcRepository::summary, zoneId, limit);
  }

  /**
   * The patrol routes of NPC {@code npcId}, by zone and route, each in walking order; empty when it has none or the
   * database predates the npc_waypoint table.
   */
  public List<Route> routes(long npcId) {
    return db.read(c -> routes(c, RouteKey.NPC, npcId));
  }

  /** The npc_waypoint column {@link #routes(Connection, RouteKey, long)} selects routes by. */
  enum RouteKey {
    NPC("npc_id"), ZONE("zone_id");

    private final String column;

    RouteKey(String column) {
      this.column = column;
    }
  }

  /**
   * The routes whose {@code key} column (npc_id or zone_id) is {@code id}, ordered by NPC, zone, route and point;
   * empty on a database without the npc_waypoint table.
   */
  static List<Route> routes(Connection c, RouteKey key, long id) throws SQLException {
    if (!Sql.hasTable(c, "npc_waypoint")) {
      return List.of();
    }
    record Row(long npc, long zone, int route, Point point) {}
    List<Row> rows = Sql.list(c, "SELECT npc_id, zone_id, route, x, y FROM npc_waypoint WHERE " + key.column + " = ?"
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

  /**
   * Every NPC {@code filter} lets through, by name; the rank filter matches the rank shown ({@link #RANK}). The
   * level bounds keep an NPC whose level range overlaps them (an
   * NPC with one known level counts it as both ends); NPCs without a level drop out once a bound is set.
   */
  public List<NpcSummary> list(NpcFilter filter) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> params = new ArrayList<>();
    if (filter.zoneId() != null) {
      where.append(" AND n.zone_id = ?");
      params.add(filter.zoneId());
    }
    if (filter.rank() != null) {
      where.append(" AND ").append(RANK).append(" = ?");
      params.add(filter.rank());
    }
    if (filter.minLevel() != null) {
      where.append(" AND coalesce(n.max_level, n.min_level) >= ?");
      params.add(filter.minLevel());
    }
    if (filter.maxLevel() != null) {
      where.append(" AND coalesce(n.min_level, n.max_level) <= ?");
      params.add(filter.maxLevel());
    }
    String sql = SUMMARY + where + " ORDER BY n.name IS NULL, n.name COLLATE NOCASE, n.id";
    return db.read(c -> Sql.list(c, sql, NpcRepository::summary, params.toArray()));
  }

  /** Joins {@code item i} and {@code item_class ic} on the {@code item_id} of the table aliased {@code alias}. */
  static String itemJoin(String alias) {
    return "LEFT JOIN item i ON i.id = " + alias + ".item_id LEFT JOIN item_class ic ON ic.id = i.class_id";
  }

  /** An item row without drop chance, origin or slot ({@code item_id} and the {@link #ITEM_COLUMNS}). */
  static LootRow plainLoot(ResultSet rs) throws SQLException {
    return loot(rs, null, null, null);
  }

  /** An item row of {@code item_id} and the {@link #ITEM_COLUMNS}, with the given drop chance, origin and slot. */
  static LootRow loot(ResultSet rs, Double dropPct, String origin, Integer slot) throws SQLException {
    return new LootRow(new EntityRef(EntityKind.ITEM, rs.getLong("item_id")), rs.getString("name"),
        Sql.integer(rs, "item_level"), dropPct, origin, slot, Sql.integer(rs, "class_id"),
        rs.getString("class_name"), Sql.integer(rs, "subclass_id"), false, 0, Sql.integer(rs, "quality"),
        Sql.longValue(rs, "icon_id"), rs.getString("skill_line"), Sql.integer(rs, "allowable_class"), 0);
  }

  private static LootRow withDropperCount(LootRow r, int dropperCount) {
    return new LootRow(r.item(), r.itemName(), r.itemLevel(), r.dropPct(), r.origin(), r.slot(), r.classId(),
        r.className(), r.subclassId(), r.disagrees(), r.sourceCount(), r.quality(), r.iconId(), r.skillLine(),
        r.allowableClass(), dropperCount, r.pctSource());
  }

  /**
   * A drop row of {@code item_id}, the {@link #ITEM_COLUMNS}, {@code drop_pct} and the rate provenance columns
   * {@code pct_source}, {@code disagrees} and {@code source_count}, with the given origin and slot.
   */
  static LootRow ratedLoot(ResultSet rs, String origin, Integer slot) throws SQLException {
    LootRow row = loot(rs, Sql.doubleValue(rs, "drop_pct"), origin, slot);
    return new LootRow(row.item(), row.itemName(), row.itemLevel(), row.dropPct(), row.origin(), row.slot(),
        row.classId(), row.className(), row.subclassId(), rs.getInt("disagrees") != 0, rs.getInt("source_count"),
        row.quality(), row.iconId(), row.skillLine(), row.allowableClass(), 0, rs.getString("pct_source"));
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
        rs.getString("zone_name"), Sql.longValue(rs, "zone_id"), rs.getString("friendly_to"));
  }
}
