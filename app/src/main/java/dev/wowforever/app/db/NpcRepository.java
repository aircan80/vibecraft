package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.Spawn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** NPCs: detail pages, filtered lists and zone lists. */
public final class NpcRepository {
  private static final String SUMMARY = """
      SELECT n.id, n.name, n.sub_name, n.min_level, n.max_level, n.rank, n.zone_id, nz.name AS zone_name,
             n.npc_flags, n.friendly_to
      FROM npc n LEFT JOIN zone nz ON nz.id = n.zone_id""";

  /** The item columns a {@link LootRow} reads ({@link #itemJoin} must be joined): name, item level and class. */
  static final String ITEM_COLUMNS = "i.name, i.item_level, i.class_id, ic.name AS class_name, i.subclass_id";

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
      List<LootRow> drops = Sql.list(c, "SELECT d.item_id, " + ITEM_COLUMNS + ", d.drop_pct, d.origin"
          + " FROM item_npc_drop d " + itemJoin("d") + """

          WHERE d.npc_id = ?
          ORDER BY d.drop_pct IS NULL, d.drop_pct DESC, i.name IS NULL, i.name COLLATE NOCASE, d.item_id""",
          rs -> loot(rs, Sql.doubleValue(rs, "drop_pct"), rs.getString("origin"), null),
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
   * Every NPC {@code filter} lets through, by name. The level bounds keep an NPC whose level range overlaps them (an
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
      where.append(" AND n.rank = ?");
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
        rs.getString("class_name"), Sql.integer(rs, "subclass_id"));
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
