package dev.wowforever.app.db;

import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Game objects: detail pages and zone lists. */
public final class ObjectRepository {
  private static final String SUMMARY = """
      SELECT o.id, o.name, o.zone_id, oz.name AS zone_name
      FROM game_object o LEFT JOIN zone oz ON oz.id = o.zone_id""";

  private final Database db;

  public ObjectRepository(Database db) {
    this.db = db;
  }

  /** The object page, or empty when there is no such object. */
  public Optional<ObjectDetail> detail(long id) {
    return db.read(c -> {
      Optional<ObjectSummary> summary = Sql.one(c, SUMMARY + " WHERE o.id = ?", ObjectRepository::summary, id);
      if (summary.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new ObjectDetail(summary.get(),
          Sql.list(c, "SELECT d.item_id, " + NpcRepository.ITEM_COLUMNS
              + " FROM item_object_drop d " + NpcRepository.itemJoin("d") + """

              WHERE d.object_id = ?
              ORDER BY i.name IS NULL, i.name COLLATE NOCASE, d.item_id""",
              NpcRepository::plainLoot, id),
          QuestRepository.givenBy(c, "object", id, "start"), QuestRepository.givenBy(c, "object", id, "end"),
          NpcRepository.spawns(c, "object_spawn", "object_id", id)));
    });
  }

  /** Objects whose zone is {@code zoneId}, by name. */
  public List<ObjectSummary> byZone(long zoneId) {
    return db.read(c -> byZone(c, zoneId, -1));
  }

  /** Objects whose zone is {@code zoneId}, by name, at most {@code limit} (negative: all). */
  static List<ObjectSummary> byZone(Connection c, long zoneId, int limit) throws SQLException {
    return Sql.list(c, SUMMARY + " WHERE o.zone_id = ? ORDER BY o.name IS NULL, o.name COLLATE NOCASE, o.id LIMIT ?",
        ObjectRepository::summary, zoneId, limit);
  }

  private static ObjectSummary summary(ResultSet rs) throws SQLException {
    return new ObjectSummary(rs.getLong("id"), rs.getString("name"), rs.getString("zone_name"),
        Sql.longValue(rs, "zone_id"));
  }
}
