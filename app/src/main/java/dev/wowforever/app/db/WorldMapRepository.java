package dev.wowforever.app.db;

import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import java.util.List;
import java.util.Optional;

/** Reads the archived client hierarchy projection from the same database as the zone pages. */
public final class WorldMapRepository {
  private final Database db;
  private WorldMapCatalog cached;
  public WorldMapRepository(Database db) { this.db = db; }

  public synchronized WorldMapCatalog catalog() {
    if (cached != null) return cached;
    cached = db.read(c -> {
      boolean available = Sql.one(c, "SELECT 1 FROM sqlite_master WHERE type='table' AND name='ui_map_child'",
          rs -> true).orElse(false);
      if (!available) return new WorldMapCatalog(List.of());
      String sourceBuild = Sql.one(c, "SELECT value FROM meta WHERE key='world_map_source_build'",
          rs -> rs.getString(1)).orElse("");
      return new WorldMapCatalog(Sql.list(c, """
          SELECT m.id, m.parent_id, m.name, r.x0,r.y0,r.x1,r.y1,
            (SELECT z.id FROM zone z WHERE z.ui_map_id=m.id ORDER BY z.parent_id IS NOT NULL,z.id LIMIT 1) AS zone_id,
            EXISTS(SELECT 1 FROM zone_map a WHERE a.ui_map_id=m.id) AS has_art
          FROM ui_map m LEFT JOIN ui_map_child r ON r.child_ui_map_id=m.id
          ORDER BY m.parent_id,m.name COLLATE NOCASE,m.id
          """, rs -> {
            Long zone = Sql.longValue(rs,"zone_id");
            int quests = zone == null ? 0 : Sql.one(c,
                "SELECT count(*) FROM quest WHERE zone_or_sort=?", r -> r.getInt(1), zone).orElse(0);
            int instances = zone == null ? 0 : Sql.one(c,
                "SELECT count(*) FROM instance WHERE zone_id=?", r -> r.getInt(1), zone).orElse(0);
            return new WorldMapRegion(rs.getLong("parent_id"),rs.getLong("id"),rs.getString("name"),
                rs.getDouble("x0"),rs.getDouble("y0"),rs.getDouble("x1"),rs.getDouble("y1"),zone,
                rs.getBoolean("has_art"), rs.getObject("x0") != null,
                zone == null ? Optional.empty() : ZoneRepository.levelBand(c,zone), quests, instances);
          }), sourceBuild);
    });
    return cached;
  }
}
