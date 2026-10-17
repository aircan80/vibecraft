package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LevelBand;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestPins;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.NpcRoute;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneMap;
import dev.wowforever.app.model.ZoneSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Zones: the zone list and zone pages. */
public final class ZoneRepository {
  /** Maximum rows of each list on a zone page. */
  public static final int DETAIL_LIST_CAP = 2_000;

  /**
   * Zones with content: npcs, objects, quests or instances in the zone itself, or a subzone (at any depth)
   * that has content. Placeholder zones without content drop out.
   */
  private static final String ZONES_WITH_CONTENT = """
      WITH RECURSIVE direct(id) AS (
        SELECT zone_id FROM npc WHERE zone_id IS NOT NULL
        UNION SELECT zone_id FROM game_object WHERE zone_id IS NOT NULL
        UNION SELECT zone_or_sort FROM quest WHERE zone_or_sort > 0
        UNION SELECT zone_id FROM instance WHERE zone_id IS NOT NULL
      ),
      content(id) AS (
        SELECT id FROM direct
        UNION SELECT z.parent_id FROM zone z JOIN content c ON z.id = c.id WHERE z.parent_id IS NOT NULL
      )
      SELECT z.id, z.name, z.kind, z.parent_id, p.name AS parent_name
      FROM zone z LEFT JOIN zone p ON p.id = z.parent_id
      WHERE z.id IN (SELECT id FROM content)
      ORDER BY z.parent_id IS NOT NULL, z.name IS NULL, z.name COLLATE NOCASE, z.id""";
  /** The {@link #summary} columns of {@code zone z} with its parent's name; append WHERE and ORDER BY. */
  private static final String SUMMARY_SELECT = """
      SELECT z.id, z.name, z.kind, z.parent_id, p.name AS parent_name
      FROM zone z LEFT JOIN zone p ON p.id = z.parent_id""";

  /** The zone kinds of dungeons and raids, whose NPCs are all met inside. */
  private static final Set<String> INSTANCE_KINDS = Set.of("dungeon", "raid");

  /** Maximum spawn points of each spawn query. */
  public static final int PIN_CAP = 20_000;
  /**
   * Keeps spawn points ({@code s}) {@linkplain MapCoordinates#onMap on the map}: instance placeholders (-1, -1),
   * points past the edge and missing coordinates drop out.
   */
  private static final String ON_MAP = "\n" + MapCoordinates.sqlOnMap("s");

  private final Database db;

  public ZoneRepository(Database db) {
    this.db = db;
  }

  /** Zones that have content, top-level zones first, each group by name. */
  public List<ZoneSummary> all() {
    return db.read(c -> Sql.list(c, ZONES_WITH_CONTENT, ZoneRepository::summary));
  }

  /**
   * The zone page, or empty when there is no such zone. Instances are those in this zone or in one of its
   * direct subzones; every list holds at most {@link #DETAIL_LIST_CAP} rows. The map is the zone's {@link #map}, the
   * level band its {@link #levelBand}. A zone that is not itself a dungeon or raid leaves out the NPCs met only
   * inside its instances.
   */
  public Optional<ZoneDetail> detail(long id) {
    return db.read(c -> {
      Optional<ZoneSummary> zone = Sql.one(c, SUMMARY_SELECT + " WHERE z.id = ?",
          ZoneRepository::summary, id);
      if (zone.isEmpty()) {
        return Optional.empty();
      }
      boolean instanceZone = INSTANCE_KINDS.contains(zone.get().kind());
      return Optional.of(new ZoneDetail(zone.get(),
          NpcRepository.byZone(c, id, DETAIL_LIST_CAP, !instanceZone),
          ObjectRepository.byZone(c, id, DETAIL_LIST_CAP),
          QuestRepository.byZone(c, id, DETAIL_LIST_CAP),
          Sql.list(c, InstanceRepository.SUMMARY_SELECT + """

              WHERE i.zone_id = ? OR i.zone_id IN (SELECT id FROM zone WHERE parent_id = ?)
              ORDER BY i.level_min IS NULL, i.level_min, i.name COLLATE NOCASE, i.id LIMIT ?""",
              InstanceRepository::summary, id, id, DETAIL_LIST_CAP),
          Sql.list(c, SUMMARY_SELECT + """

              WHERE z.parent_id = ?
              ORDER BY z.name IS NULL, z.name COLLATE NOCASE, z.id LIMIT ?""",
              ZoneRepository::summary, id, DETAIL_LIST_CAP),
          map(c, id), levelBand(c, id)));
    });
  }

  /**
   * The level band of zone {@code zoneId} ({@link LevelBand#of}) over its hostile NPCs, each weighted by its spawn
   * points on the zone's map (an NPC without any spawn point counts once, one met only elsewhere, inside an
   * instance say, not at all) and bosses ({@link NpcRepository#RANK} 3) and unknown levels left out; over every
   * NPC when no hostile one counts. Empty when none does.
   */
  static Optional<LevelBand> levelBand(Connection c, long zoneId) throws SQLException {
    record Npc(LevelBand.Weighted level, boolean friendly) {}
    List<Npc> npcs = Sql.list(c, "SELECT n.min_level, n.max_level, n.friendly_to,"
            + " (SELECT count(*) FROM npc_spawn s WHERE s.npc_id = n.id AND s.zone_id = n.zone_id" + ON_MAP
            + ") AS on_map, EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_id = n.id) AS spawns"
            + " FROM npc n WHERE n.zone_id = ? AND " + NpcRepository.RANK + " IS NOT 3",
        rs -> {
          Integer min = positive(Sql.integer(rs, "min_level"));
          Integer max = positive(Sql.integer(rs, "max_level"));
          Integer low = min != null ? min : max;
          Integer high = max != null ? max : min;
          if (low == null || high >= InstanceRepository.SKULL_LEVEL) {
            return null;
          }
          int weight = rs.getInt("spawns") != 0 ? rs.getInt("on_map") : 1;
          String friendly = rs.getString("friendly_to");
          return new Npc(new LevelBand.Weighted((low + high) / 2.0, weight), friendly != null && !friendly.isBlank());
        }, zoneId).stream().filter(Objects::nonNull).toList();
    Optional<LevelBand> hostile = LevelBand.of(npcs.stream().filter(n -> !n.friendly()).map(Npc::level).toList());
    return hostile.isPresent() ? hostile : LevelBand.of(npcs.stream().map(Npc::level).toList());
  }

  private static Integer positive(Integer level) {
    return level != null && level > 0 ? level : null;
  }

  /** The map of zone {@code zoneId} (the zone_map of its UiMap), or empty when it has none. */
  public Optional<ZoneMap> map(long zoneId) {
    return db.read(c -> map(c, zoneId));
  }

  /**
   * Every NPC's patrol routes in zone {@code zoneId}, by NPC id, route and point; empty when there are none or the
   * database predates the npc_waypoint table.
   */
  public List<Route> routesInZone(long zoneId) {
    return db.read(c -> NpcRepository.routes(c, NpcRepository.RouteKey.ZONE, zoneId));
  }

  /** Named patrol routes in the zone, including unknown NPCs with their id fallback. */
  public List<NpcRoute> npcRoutesInZone(long zoneId) {
    return db.read(c -> {
      List<Route> routes = NpcRepository.routes(c, NpcRepository.RouteKey.ZONE, zoneId);
      if (routes.isEmpty()) {
        return List.of();
      }
      record Npc(String name, Integer rank) {}
      java.util.Map<Long, Npc> npcs = new java.util.HashMap<>();
      Sql.list(c, "SELECT n.id, n.name, " + NpcRepository.RANK + " AS rank FROM npc n"
          + " WHERE EXISTS (SELECT 1 FROM npc_waypoint w WHERE w.npc_id = n.id AND w.zone_id = ?)",
          rs -> { npcs.put(rs.getLong("id"), new Npc(rs.getString("name"), Sql.integer(rs, "rank"))); return 0; }, zoneId);
      return routes.stream().map(r -> {
        EntityRef ref = new EntityRef(EntityKind.NPC, r.npcId());
        Npc n = npcs.get(r.npcId());
        return new NpcRoute(ref, ref.displayName(n == null ? null : n.name()), n == null ? null : n.rank(), r);
      }).toList();
    });
  }

  /**
   * The NPC spawn points in zone {@code zoneId} that lie on its map ({@link MapCoordinates#onMap}), each with the
   * NPC's name and rank (the rank shown, {@link NpcRepository#RANK}), in import order and at most
   * {@link #PIN_CAP}; with {@code bossesAndRaresOnly} only those of bosses, rare elites and rares (ranks 3, 2 and 4).
   */
  public List<SpawnPin> npcSpawnsInZone(long zoneId, boolean bossesAndRaresOnly) {
    return db.read(c -> Sql.list(c, "SELECT s.npc_id AS id, n.name, " + NpcRepository.RANK + " AS rank, s.x, s.y"
        + " FROM npc_spawn s LEFT JOIN npc n ON n.id = s.npc_id WHERE s.zone_id = ?" + ON_MAP
        + " AND (? = 0 OR " + NpcRepository.RANK + " IN (2, 3, 4)) ORDER BY s.rowid LIMIT ?",
        rs -> pin(rs, EntityKind.NPC, Sql.integer(rs, "rank")), zoneId, bossesAndRaresOnly ? 1 : 0, PIN_CAP));
  }

  /**
   * The object spawn points in zone {@code zoneId} that lie on its map, each with the object's name, in import order
   * and at most {@link #PIN_CAP}.
   */
  public List<SpawnPin> objectSpawnsInZone(long zoneId) {
    return db.read(c -> Sql.list(c, """
        SELECT s.object_id AS id, o.name, s.x, s.y
        FROM object_spawn s LEFT JOIN game_object o ON o.id = s.object_id
        WHERE s.zone_id = ?""" + ON_MAP + """

        ORDER BY s.rowid LIMIT ?""",
        rs -> pin(rs, EntityKind.OBJECT, null), zoneId, PIN_CAP));
  }

  /**
   * The spawn points in zone {@code zoneId} on its map of the NPCs and objects that start or end quests: one pin per
   * giver and point (two spawn rows of a giver at the same point are one pin), each with the giver's name and the
   * quests it starts and ends, by quest level; the pins by giver name. Item givers have no spawn point and are not
   * pinned. Reads at most {@link #PIN_CAP} giver rows (a quest of a point each), one query: each spawn table by zone
   * ({@code idx_*_spawn_zone}) joined to {@code quest_giver} by giver ({@code idx_quest_giver_entity}). The result
   * says whether the zone had more rows than that.
   */
  public QuestPins questGiversInZone(long zoneId) {
    return questGiversInZone(zoneId, PIN_CAP);
  }

  /**
   * {@link #questGiversInZone(long)} reading at most {@code cap} giver rows: it asks for one more, so it knows
   * whether rows were left out.
   */
  QuestPins questGiversInZone(long zoneId, int cap) {
    record Key(EntityRef giver, double x, double y) {}
    record Row(Key key, String giverName, QuestRef quest) {}
    List<Row> rows = db.read(c -> Sql.list(c, """
        SELECT * FROM (
          SELECT 'npc' AS kind, s.npc_id AS id, n.name AS giver_name, s.x, s.y,
                 g.quest_id, q.name AS quest_name, q.quest_level, g.role
          FROM npc_spawn s
          JOIN quest_giver g ON g.kind = 'npc' AND g.entity_id = s.npc_id
          LEFT JOIN npc n ON n.id = s.npc_id
          LEFT JOIN quest q ON q.id = g.quest_id
          WHERE s.zone_id = ?""" + ON_MAP + """

          UNION ALL
          SELECT 'object', s.object_id, o.name, s.x, s.y, g.quest_id, q.name, q.quest_level, g.role
          FROM object_spawn s
          JOIN quest_giver g ON g.kind = 'object' AND g.entity_id = s.object_id
          LEFT JOIN game_object o ON o.id = s.object_id
          LEFT JOIN quest q ON q.id = g.quest_id
          WHERE s.zone_id = ?""" + ON_MAP + """

        )
        ORDER BY giver_name IS NULL, giver_name COLLATE NOCASE, kind, id, x, y,
                 quest_level IS NULL, quest_level, quest_name COLLATE NOCASE, quest_id, role <> 'start'
        LIMIT ?""",
        rs -> new Row(new Key(new EntityRef("npc".equals(rs.getString("kind")) ? EntityKind.NPC : EntityKind.OBJECT,
            rs.getLong("id")), rs.getDouble("x"), rs.getDouble("y")), rs.getString("giver_name"),
            new QuestRef(rs.getLong("quest_id"), rs.getString("quest_name"), Sql.integer(rs, "quest_level"),
                "start".equals(rs.getString("role")))),
        zoneId, zoneId, cap + 1));
    boolean capped = rows.size() > cap;
    if (capped) {
      rows = rows.subList(0, cap);
    }
    Map<Key, String> names = new LinkedHashMap<>();
    Map<Key, Set<QuestRef>> quests = new LinkedHashMap<>();
    for (Row r : rows) {
      names.putIfAbsent(r.key(), r.giverName());
      quests.computeIfAbsent(r.key(), k -> new LinkedHashSet<>()).add(r.quest());
    }
    List<QuestPin> pins = new ArrayList<>(names.size());
    names.forEach((k, name) -> pins.add(new QuestPin(k.giver(), name, k.x(), k.y(), List.copyOf(quests.get(k)))));
    return new QuestPins(pins, capped);
  }

  private static SpawnPin pin(ResultSet rs, EntityKind kind, Integer rank) throws SQLException {
    return new SpawnPin(new EntityRef(kind, rs.getLong("id")), rs.getString("name"), rank, rs.getDouble("x"),
        rs.getDouble("y"));
  }

  private static Optional<ZoneMap> map(Connection c, long zoneId) throws SQLException {
    return Sql.one(c, """
        SELECT m.ui_map_id, m.width, m.height, m.jpeg
        FROM zone z JOIN zone_map m ON m.ui_map_id = z.ui_map_id
        WHERE z.id = ?""",
        rs -> new ZoneMap(rs.getLong("ui_map_id"), rs.getInt("width"), rs.getInt("height"), rs.getBytes("jpeg")),
        zoneId);
  }

  private static ZoneSummary summary(ResultSet rs) throws SQLException {
    return new ZoneSummary(rs.getLong("id"), rs.getString("name"), rs.getString("kind"),
        Sql.longValue(rs, "parent_id"), rs.getString("parent_name"));
  }
}
