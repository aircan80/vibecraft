package dev.wowforever.app.db;

import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneMap;
import dev.wowforever.app.model.ZoneSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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
   * direct subzones; every list holds at most {@link #DETAIL_LIST_CAP} rows. The map is the zone's {@link #map}.
   */
  public Optional<ZoneDetail> detail(long id) {
    return db.read(c -> {
      Optional<ZoneSummary> zone = Sql.one(c, SUMMARY_SELECT + " WHERE z.id = ?",
          ZoneRepository::summary, id);
      if (zone.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new ZoneDetail(zone.get(),
          NpcRepository.byZone(c, id, DETAIL_LIST_CAP),
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
          map(c, id)));
    });
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
    return db.read(c -> NpcRepository.routes(c, "zone_id", zoneId));
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
