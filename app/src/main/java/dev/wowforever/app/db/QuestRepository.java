package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Quests: detail pages, filtered lists, zone lists and the quests an entity starts or ends. */
public final class QuestRepository {
  /** Quest columns; {@code zone_or_sort} names a zone only when positive (negative values are quest sorts). */
  private static final String COLUMNS = """
      q.id, q.name, q.quest_level, q.required_level, qz.name AS zone_name, q.objectives_text""";
  private static final String ZONE_JOIN = "LEFT JOIN zone qz ON q.zone_or_sort > 0 AND qz.id = q.zone_or_sort";

  private final Database db;

  public QuestRepository(Database db) {
    this.db = db;
  }

  /** The quest page, or empty when there is no such quest. */
  public Optional<QuestDetail> detail(long id) {
    return db.read(c -> {
      Optional<QuestDetail> head = Sql.one(c, "SELECT " + COLUMNS + " FROM quest q " + ZONE_JOIN + " WHERE q.id = ?",
          rs -> new QuestDetail(summary(rs), rs.getString("objectives_text"), List.of(), List.of(), List.of()), id);
      if (head.isEmpty()) {
        return head;
      }
      List<QuestGiver> givers = Sql.list(c, """
          SELECT g.kind, g.entity_id, g.role,
                 CASE g.kind WHEN 'npc' THEN n.name WHEN 'object' THEN o.name WHEN 'item' THEN i.name END AS giver_name
          FROM quest_giver g
          LEFT JOIN npc n ON g.kind = 'npc' AND n.id = g.entity_id
          LEFT JOIN game_object o ON g.kind = 'object' AND o.id = g.entity_id
          LEFT JOIN item i ON g.kind = 'item' AND i.id = g.entity_id
          WHERE g.quest_id = ?
          ORDER BY g.role <> 'start', g.kind, giver_name IS NULL, giver_name COLLATE NOCASE, g.entity_id""",
          QuestRepository::giver, id).stream().filter(Objects::nonNull).toList();
      return Optional.of(new QuestDetail(head.get().summary(), head.get().objectivesText(), givers,
          Sql.list(c, "SELECT r.item_id, " + NpcRepository.ITEM_COLUMNS
              + " FROM quest_reward_item r " + NpcRepository.itemJoin("r") + """

              WHERE r.quest_id = ?
              ORDER BY i.name IS NULL, i.name COLLATE NOCASE, r.item_id""",
              NpcRepository::plainLoot, id),
          Sql.list(c, """
              SELECT r.item_id, i.name
              FROM item_related_quest r LEFT JOIN item i ON i.id = r.item_id
              WHERE r.quest_id = ?
              ORDER BY i.name IS NULL, i.name COLLATE NOCASE, r.item_id""",
              rs -> new ItemSource(ItemSource.RELATED, new EntityRef(EntityKind.ITEM, rs.getLong("item_id")),
                  rs.getString("name")),
              id)));
    });
  }

  /** Quests filed under zone {@code zoneId}, by name. */
  public List<QuestSummary> byZone(long zoneId) {
    return db.read(c -> byZone(c, zoneId, -1));
  }

  /** Quests filed under zone {@code zoneId}, by name, at most {@code limit} (negative: all). */
  static List<QuestSummary> byZone(Connection c, long zoneId, int limit) throws SQLException {
    return Sql.list(c, "SELECT " + COLUMNS + " FROM quest q " + ZONE_JOIN
            + " WHERE q.zone_or_sort = ? ORDER BY q.name IS NULL, q.name COLLATE NOCASE, q.id LIMIT ?",
        QuestRepository::summary, zoneId, limit);
  }

  /**
   * Every quest {@code filter} lets through, by name. Quests without a known level (null, or 0 and below as for
   * quests that scale with the player) drop out once a level bound is set; a zone filter matches the zone a quest
   * is filed under.
   */
  public List<QuestSummary> list(QuestFilter filter) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> params = new ArrayList<>();
    if (filter.zoneId() != null) {
      where.append(" AND q.zone_or_sort = ?");
      params.add(filter.zoneId());
    }
    if (filter.minLevel() != null || filter.maxLevel() != null) {
      where.append(" AND q.quest_level > 0");
    }
    if (filter.minLevel() != null) {
      where.append(" AND q.quest_level >= ?");
      params.add(filter.minLevel());
    }
    if (filter.maxLevel() != null) {
      where.append(" AND q.quest_level <= ?");
      params.add(filter.maxLevel());
    }
    String sql = "SELECT " + COLUMNS + " FROM quest q " + ZONE_JOIN + where
        + " ORDER BY q.name IS NULL, q.name COLLATE NOCASE, q.id";
    return db.read(c -> Sql.list(c, sql, QuestRepository::summary, params.toArray()));
  }

  /** Quests that the {@code kind} ({@code npc}, {@code object}) entity {@code id} gives in {@code role}. */
  static List<QuestSummary> givenBy(Connection c, String kind, long id, String role) throws SQLException {
    return Sql.list(c, "SELECT g.quest_id AS id, q.name, q.quest_level, q.required_level, qz.name AS zone_name"
            + " FROM quest_giver g LEFT JOIN quest q ON q.id = g.quest_id " + ZONE_JOIN
            + " WHERE g.kind = ? AND g.entity_id = ? AND g.role = ?"
            + " ORDER BY q.quest_level IS NULL, q.quest_level, q.name COLLATE NOCASE, g.quest_id",
        QuestRepository::summary, kind, id, role);
  }

  /** The giver on the current row, or null for an entity kind the app does not know. */
  private static QuestGiver giver(ResultSet rs) throws SQLException {
    Optional<EntityKind> kind = EntityKind.fromDbKind(rs.getString("kind"));
    if (kind.isEmpty()) {
      return null;
    }
    return new QuestGiver(new EntityRef(kind.get(), rs.getLong("entity_id")), rs.getString("giver_name"),
        rs.getString("role"));
  }

  private static QuestSummary summary(ResultSet rs) throws SQLException {
    return new QuestSummary(rs.getLong("id"), rs.getString("name"), Sql.integer(rs, "quest_level"),
        Sql.integer(rs, "required_level"), rs.getString("zone_name"));
  }
}
