package dev.wowforever.app.db;

import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Dungeons and raids: instance list, boss lists and boss loot tables. Most come from AtlasLoot; the WoW: Forever
 * dungeons come from the client's own tables, with named bosses and no loot ({@link InstanceSummary#source()}).
 */
public final class InstanceRepository {
  /** The {@link LootRow#origin()} of boss loot, which always comes from AtlasLoot. */
  public static final String BOSS_LOOT_ORIGIN = "atlasloot";
  /** AtlasLoot's level for a boss shown with a skull: no real level. */
  public static final int SKULL_LEVEL = 999;
  /** The {@link #summary} columns of {@code instance i} with its zone's name; append WHERE and ORDER BY. */
  static final String SUMMARY_SELECT = """
      SELECT i.id, i.name, i.content_type, i.level_min, i.level_max, i.zone_id, z.name AS zone_name, i.key,
             i.source, i.tuned_min_level, i.bosses_state, i.loot_state
      FROM instance i LEFT JOIN zone z ON z.id = i.zone_id""";

  /** The boss loot columns with the item's; append WHERE and ORDER BY. */
  private static final String BOSS_LOOT = "SELECT bl.item_id, " + NpcRepository.ITEM_COLUMNS
      + ", bl.drop_pct, bl.pct_source, bl.slot, bl.disagrees, bl.source_count FROM boss_loot bl " + NpcRepository.itemJoin("bl");

  private final Database db;

  public InstanceRepository(Database db) {
    this.db = db;
  }

  /** Build of the shipped client snapshot; load before rendering a page on the EDT. */
  public String clientBuild() {
    return db.meta().getOrDefault("client_build", "unknown");
  }

  /** All instances in AtlasLoot order, then the client dungeons by map id. */
  public List<InstanceSummary> all() {
    return db.read(c -> Sql.list(c,
        SUMMARY_SELECT + " ORDER BY i.id",
        InstanceRepository::summary));
  }

  /**
   * The instance with its bosses (main list in AtlasLoot order, then extra lists) and its boss loot's
   * {@linkplain #difficulties difficulties}, or empty. A boss AtlasLoot gives no level, or its skull level
   * ({@value #SKULL_LEVEL}), takes the level of its NPC row (the highest when the NPC has a range) when that is
   * known; otherwise AtlasLoot's value stays.
   */
  public Optional<InstanceDetail> detail(long id) {
    return db.read(c -> {
      Optional<InstanceSummary> instance = Sql.one(c,
          SUMMARY_SELECT + " WHERE i.id = ?",
          InstanceRepository::summary, id);
      if (instance.isEmpty()) {
        return Optional.empty();
      }
      List<BossSummary> bosses = Sql.list(c, """
          SELECT b.id, b.name, b.npc_id,
                 CASE WHEN (b.level IS NULL OR b.level >= %1$d)
                       AND COALESCE(n.max_level, n.min_level) BETWEEN 1 AND %1$d - 1
                      THEN COALESCE(n.max_level, n.min_level) ELSE b.level END AS level,
                 b.special_type, b.is_extra_list
          FROM boss b LEFT JOIN npc n ON n.id = b.npc_id
          WHERE b.instance_id = ? ORDER BY b.is_extra_list, b.sort_order, b.id""".formatted(SKULL_LEVEL),
          rs -> new BossSummary(rs.getLong("id"), rs.getString("name"), Sql.longValue(rs, "npc_id"),
              Sql.integer(rs, "level"), rs.getString("special_type"), rs.getInt("is_extra_list") != 0),
          id);
      return Optional.of(new InstanceDetail(instance.get(), bosses, difficulties(c, id)));
    });
  }

  /** The loot table of a boss in slot order, every difficulty. */
  public List<LootRow> bossLoot(long bossId) {
    return db.read(c -> Sql.list(c, BOSS_LOOT + """

        WHERE bl.boss_id = ?
        ORDER BY bl.slot IS NULL, bl.slot, bl.difficulty, bl.item_id""",
        InstanceRepository::bossLootRow, bossId));
  }

  /** The loot table of a boss in one {@code difficulty} (a {@link #difficulties} value), in slot order. */
  public List<LootRow> bossLoot(long bossId, String difficulty) {
    return db.read(c -> Sql.list(c, BOSS_LOOT + """

        WHERE bl.boss_id = ? AND bl.difficulty = ?
        ORDER BY bl.slot IS NULL, bl.slot, bl.item_id""",
        InstanceRepository::bossLootRow, bossId, difficulty));
  }

  /**
   * The distinct {@code boss_loot.difficulty} values (such as NORMAL) over the instance's bosses: NORMAL first,
   * then the others by name; empty when the instance has no boss loot.
   */
  public List<String> difficulties(long instanceId) {
    return db.read(c -> difficulties(c, instanceId));
  }

  private static List<String> difficulties(Connection c, long instanceId) throws SQLException {
    return Sql.list(c, """
        SELECT DISTINCT bl.difficulty FROM boss_loot bl JOIN boss b ON b.id = bl.boss_id
        WHERE b.instance_id = ?
        ORDER BY bl.difficulty <> 'NORMAL', bl.difficulty""",
        rs -> rs.getString("difficulty"), instanceId);
  }

  private static LootRow bossLootRow(ResultSet rs) throws SQLException {
    return NpcRepository.ratedLoot(rs, BOSS_LOOT_ORIGIN, Sql.integer(rs, "slot"));
  }

  static InstanceSummary summary(ResultSet rs) throws SQLException {
    return new InstanceSummary(rs.getLong("id"), rs.getString("name"), rs.getString("content_type"),
        Sql.integer(rs, "level_min"), Sql.integer(rs, "level_max"), Sql.longValue(rs, "zone_id"),
        rs.getString("zone_name"), rs.getString("key"), rs.getString("source"), Sql.integer(rs, "tuned_min_level"),
        rs.getString("bosses_state"), rs.getString("loot_state"));
  }
}
