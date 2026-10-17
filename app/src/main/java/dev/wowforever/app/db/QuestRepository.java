package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestFacts;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestInvolvement.Role;
import dev.wowforever.app.model.QuestInvolvement;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestRelation;
import dev.wowforever.app.model.QuestSourceItem;
import dev.wowforever.app.model.QuestSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quests: detail pages, objectives, relations and chains, filtered lists, zone lists and the quests an entity starts
 * or ends.
 */
public final class QuestRepository {
  /** Quest columns; {@code zone_or_sort} names a zone only when positive (negative values are quest sorts). */
  private static final String COLUMNS = """
      q.id, q.name, q.quest_level, q.required_level, qz.name AS zone_name, q.objectives_text""";
  static final String ZONE_JOIN = "LEFT JOIN zone qz ON q.zone_or_sort > 0 AND qz.id = q.zone_or_sort";

  /** An objective point and the ordinal of the objective it belongs to. */
  private record OrdinalPoint(int ordinal, QuestObjective.Point point) {}

  private final Database db;

  public QuestRepository(Database db) {
    this.db = db;
  }

  public static final int INVOLVING_CAP = 500;

  /** One row per quest, with every role merged; sorted by level then name. */
  public List<QuestInvolvement> involving(EntityRef ref) {
    return involvingResult(ref).rows();
  }

  /** Loads one extra distinct quest to distinguish exactly 500 results from actual overflow. */
  public QuestInvolvement.Result involvingResult(EntityRef ref) {
    Objects.requireNonNull(ref, "ref");
    if (ref.kind() != EntityKind.NPC && ref.kind() != EntityKind.ITEM && ref.kind() != EntityKind.OBJECT) {
      return QuestInvolvement.Result.empty();
    }
    return db.read(c -> involving(c, ref));
  }

  private record InvolvementHit(QuestSummary quest, Role role, String via) {}

  private static QuestInvolvement.Result involving(Connection c, EntityRef ref) throws SQLException {
    String objectiveKind = ref.kind() == EntityKind.NPC ? "creature" : ref.kind().dbKind();
    List<Object> params = new ArrayList<>(List.of(ref.kind().dbKind(), ref.id(), objectiveKind, ref.id()));
    String hits = """
        SELECT quest_id, CASE role WHEN 'start' THEN 'STARTS' ELSE 'ENDS' END AS role, NULL AS via
        FROM quest_giver WHERE kind = ? AND entity_id = ? AND role IN ('start', 'end')
        UNION ALL
        SELECT quest_id, 'OBJECTIVE', NULL FROM quest_objective WHERE kind = ? AND entity_id = ?
        """;
    if (ref.kind() == EntityKind.NPC) {
      hits += """
          UNION ALL
          SELECT o.quest_id, 'DROPS_OBJECTIVE_ITEM', COALESCE(i.name, 'Unknown item #' || d.item_id)
          FROM item_npc_drop d JOIN quest_objective o ON o.kind = 'item' AND o.entity_id = d.item_id
          LEFT JOIN item i ON i.id = d.item_id WHERE d.npc_id = ?
          """;
      params.add(ref.id());
    } else if (ref.kind() == EntityKind.ITEM) {
      hits += """
          UNION ALL
          SELECT quest_id, CASE kind WHEN 'provided' THEN 'PROVIDED' ELSE 'REQUIRED' END, NULL
          FROM quest_source_item WHERE item_id = ? AND kind IN ('provided', 'required')
          UNION ALL
          SELECT quest_id, 'RELATED', NULL FROM item_related_quest WHERE item_id = ?
          """;
      params.add(ref.id());
      params.add(ref.id());
    }
    String sql = "WITH hits AS (" + hits + """
        ), chosen AS (
          SELECT h.quest_id FROM hits h LEFT JOIN quest q ON q.id = h.quest_id
          GROUP BY h.quest_id
          ORDER BY q.quest_level IS NULL, q.quest_level, q.name IS NULL, q.name COLLATE NOCASE, h.quest_id
          LIMIT 501
        )
        SELECT h.quest_id AS id, q.name, q.quest_level, q.required_level, qz.name AS zone_name, h.role, h.via
        FROM hits h JOIN chosen ch ON ch.quest_id = h.quest_id
        LEFT JOIN quest q ON q.id = h.quest_id
        """ + ZONE_JOIN + """
         ORDER BY q.quest_level IS NULL, q.quest_level, q.name IS NULL, q.name COLLATE NOCASE,
                  h.quest_id, h.role, h.via COLLATE NOCASE
        """;
    List<InvolvementHit> found = Sql.list(c, sql,
        rs -> new InvolvementHit(summary(rs), Role.valueOf(rs.getString("role")), rs.getString("via")), params.toArray());
    Map<Long, QuestSummary> quests = new LinkedHashMap<>();
    Map<Long, Set<Role>> roles = new HashMap<>();
    Map<Long, Set<String>> names = new HashMap<>();
    for (InvolvementHit hit : found) {
      long id = hit.quest().id();
      quests.putIfAbsent(id, hit.quest());
      roles.computeIfAbsent(id, k -> EnumSet.noneOf(Role.class)).add(hit.role());
      if (hit.via() != null) names.computeIfAbsent(id, k -> new TreeSet<>()).add(hit.via());
    }
    List<QuestInvolvement> rows = quests.values().stream().limit(INVOLVING_CAP)
        .map(q -> new QuestInvolvement(q, roles.get(q.id()), names.containsKey(q.id())
            ? String.join(", ", names.get(q.id())) : null)).toList();
    return new QuestInvolvement.Result(rows, quests.size() > INVOLVING_CAP);
  }

  /** The quest page, or empty when there is no such quest. */
  public Optional<QuestDetail> detail(long id) {
    return db.read(c -> detail(c, id));
  }

  static Optional<QuestDetail> detail(Connection c, long id) throws SQLException {
    Optional<QuestDetail> head = Sql.one(c, "SELECT " + COLUMNS + " FROM quest q " + ZONE_JOIN + " WHERE q.id = ?",
        rs -> new QuestDetail(summary(rs), rs.getString("objectives_text"), List.of(), List.of(), List.of()), id);
    if (head.isEmpty()) {
      return head;
    }
    List<QuestGiver> givers = Sql.list(c, """
        SELECT g.kind, g.entity_id, g.role,
               CASE g.kind WHEN 'npc' THEN n.name WHEN 'object' THEN o.name WHEN 'item' THEN i.name END AS giver_name,
               i.quality, i.icon_id
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
            SELECT r.item_id, i.name, i.quality, i.icon_id
            FROM item_related_quest r LEFT JOIN item i ON i.id = r.item_id
            WHERE r.quest_id = ?
            ORDER BY i.name IS NULL, i.name COLLATE NOCASE, r.item_id""",
            rs -> new ItemSource(ItemSource.RELATED, new EntityRef(EntityKind.ITEM, rs.getLong("item_id")),
                rs.getString("name"), Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id")),
            id)));

  }

  /** The quest's objectives in Questie's order, each with its area points; empty for an unknown quest. */
  public List<QuestObjective> objectives(long questId) {
    return db.read(c -> objectives(c, questId));
  }

  static List<QuestObjective> objectives(Connection c, long questId) throws SQLException {
    Map<Integer, List<QuestObjective.Point>> points = new HashMap<>();
    for (OrdinalPoint p : Sql.list(c, """
        SELECT ordinal, zone_id, x, y FROM quest_objective_point
        WHERE quest_id = ? AND x IS NOT NULL AND y IS NOT NULL
        ORDER BY ordinal, rowid""",
        rs -> new OrdinalPoint(rs.getInt("ordinal"),
            new QuestObjective.Point(rs.getLong("zone_id"), rs.getDouble("x"), rs.getDouble("y"))),
        questId)) {
      points.computeIfAbsent(p.ordinal(), k -> new ArrayList<>()).add(p.point());
    }
    return Sql.list(c, """
        SELECT quest_id, ordinal, kind, entity_id, text, credit_group FROM quest_objective
        WHERE quest_id = ?
        ORDER BY ordinal, rowid""",
        rs -> new QuestObjective(rs.getLong("quest_id"), rs.getInt("ordinal"), rs.getString("kind"),
            Sql.longValue(rs, "entity_id"), rs.getString("text"), Sql.integer(rs, "credit_group"),
            points.getOrDefault(rs.getInt("ordinal"), List.of())),
        questId);

  }

  /** The quest's own {@code quest_relation} rows (not the rows naming it), by kind and related id. */
  public List<QuestRelation> relations(long questId) {
    return db.read(c -> Sql.list(c, """
        SELECT quest_id, related_id, kind FROM quest_relation WHERE quest_id = ? ORDER BY kind, related_id""",
        QuestRepository::relation, questId));
  }

  /** The items the quest provides, then the items it requires; each group by name, unknown items last. */
  public List<QuestSourceItem> sourceItems(long questId) {
    return db.read(c -> sourceItems(c, questId));
  }

  static List<QuestSourceItem> sourceItems(Connection c, long questId) throws SQLException {
    return Sql.list(c, """
        SELECT s.item_id, s.kind, i.name, i.quality, i.icon_id
        FROM quest_source_item s LEFT JOIN item i ON i.id = s.item_id
        WHERE s.quest_id = ?
        ORDER BY s.kind <> 'provided', s.kind, i.name IS NULL, i.name COLLATE NOCASE, s.item_id""",
        rs -> new QuestSourceItem(rs.getLong("item_id"), rs.getString("kind"), rs.getString("name"),
            Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id")),
        questId);
  }

  /**
   * The quest chain around {@code questId} (prerequisites, alternatives, unlocks and the linear spine); a quest
   * without relations is a standalone chain of one step, an unknown quest {@link QuestChain#EMPTY}.
   */
  public QuestChain chain(long questId) {
    return db.read(c -> QuestChainBuilder.build(c, questId));
  }

  /** Zone, restrictions and repeatability of the quest, without presentation defaults. */
  public Optional<QuestFacts> facts(long questId) {
    return db.read(c -> facts(c, questId));
  }

  static Optional<QuestFacts> facts(Connection c, long questId) throws SQLException {
    return Sql.one(c, """
        SELECT CASE WHEN q.zone_or_sort > 0 THEN q.zone_or_sort END AS zone_id,
               qz.name AS zone_name, q.sort_label, q.required_races, q.required_classes, q.repeatable
        FROM quest q
        """ + ZONE_JOIN + " WHERE q.id = ?",
        rs -> new QuestFacts(Sql.longValue(rs, "zone_id"), rs.getString("zone_name"),
            rs.getString("sort_label"), Sql.integer(rs, "required_races"), Sql.integer(rs, "required_classes"),
            rs.getBoolean("repeatable")), questId);
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
        rs.getString("role"), Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id"));
  }

  static QuestRelation relation(ResultSet rs) throws SQLException {
    return new QuestRelation(rs.getLong("quest_id"), rs.getLong("related_id"), rs.getString("kind"));
  }

  private static QuestSummary summary(ResultSet rs) throws SQLException {
    return new QuestSummary(rs.getLong("id"), rs.getString("name"), Sql.integer(rs, "quest_level"),
        Sql.integer(rs, "required_level"), rs.getString("zone_name"));
  }
}
