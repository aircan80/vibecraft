package dev.wowforever.app.db;

import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
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
      SELECT id, name, kind, parent_id FROM zone
      WHERE id IN (SELECT id FROM content)
      ORDER BY parent_id IS NOT NULL, name IS NULL, name COLLATE NOCASE, id""";

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
   * direct subzones; every list holds at most {@link #DETAIL_LIST_CAP} rows.
   */
  public Optional<ZoneDetail> detail(long id) {
    return db.read(c -> {
      Optional<ZoneSummary> zone = Sql.one(c, "SELECT id, name, kind, parent_id FROM zone WHERE id = ?",
          ZoneRepository::summary, id);
      if (zone.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new ZoneDetail(zone.get(),
          NpcRepository.byZone(c, id, DETAIL_LIST_CAP),
          ObjectRepository.byZone(c, id, DETAIL_LIST_CAP),
          QuestRepository.byZone(c, id, DETAIL_LIST_CAP),
          Sql.list(c, """
              SELECT id, name, content_type, level_min, level_max FROM instance
              WHERE zone_id = ? OR zone_id IN (SELECT id FROM zone WHERE parent_id = ?)
              ORDER BY level_min IS NULL, level_min, name COLLATE NOCASE, id LIMIT ?""",
              InstanceRepository::summary, id, id, DETAIL_LIST_CAP),
          Sql.list(c, """
              SELECT id, name, kind, parent_id FROM zone WHERE parent_id = ?
              ORDER BY name IS NULL, name COLLATE NOCASE, id LIMIT ?""",
              ZoneRepository::summary, id, DETAIL_LIST_CAP)));
    });
  }

  private static ZoneSummary summary(ResultSet rs) throws SQLException {
    return new ZoneSummary(rs.getLong("id"), rs.getString("name"), rs.getString("kind"),
        Sql.longValue(rs, "parent_id"));
  }
}
