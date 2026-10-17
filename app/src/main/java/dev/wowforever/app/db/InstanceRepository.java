package dev.wowforever.app.db;

import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Dungeons and raids from AtlasLoot: instance list, boss lists and boss loot tables. */
public final class InstanceRepository {
  /** The {@link LootRow#origin()} of boss loot, which always comes from AtlasLoot. */
  public static final String BOSS_LOOT_ORIGIN = "atlasloot";
  /** The {@link #summary} columns of {@code instance i} with its zone's name; append WHERE and ORDER BY. */
  static final String SUMMARY_SELECT = """
      SELECT i.id, i.name, i.content_type, i.level_min, i.level_max, i.zone_id, z.name AS zone_name
      FROM instance i LEFT JOIN zone z ON z.id = i.zone_id""";

  private final Database db;

  public InstanceRepository(Database db) {
    this.db = db;
  }

  /** All instances in AtlasLoot order. */
  public List<InstanceSummary> all() {
    return db.read(c -> Sql.list(c,
        SUMMARY_SELECT + " ORDER BY i.id",
        InstanceRepository::summary));
  }

  /** The instance with its bosses (main list in AtlasLoot order, then extra lists), or empty. */
  public Optional<InstanceDetail> detail(long id) {
    return db.read(c -> {
      Optional<InstanceSummary> instance = Sql.one(c,
          SUMMARY_SELECT + " WHERE i.id = ?",
          InstanceRepository::summary, id);
      if (instance.isEmpty()) {
        return Optional.empty();
      }
      List<BossSummary> bosses = Sql.list(c, """
          SELECT id, name, npc_id, level, special_type, is_extra_list FROM boss
          WHERE instance_id = ? ORDER BY is_extra_list, sort_order, id""",
          rs -> new BossSummary(rs.getLong("id"), rs.getString("name"), Sql.longValue(rs, "npc_id"),
              Sql.integer(rs, "level"), rs.getString("special_type"), rs.getInt("is_extra_list") != 0),
          id);
      return Optional.of(new InstanceDetail(instance.get(), bosses));
    });
  }

  /** The loot table of a boss in slot order. */
  public List<LootRow> bossLoot(long bossId) {
    return db.read(c -> Sql.list(c, """
        SELECT bl.item_id, i.name, i.item_level, bl.drop_pct, bl.slot, bl.disagrees, bl.source_count
        FROM boss_loot bl LEFT JOIN item i ON i.id = bl.item_id
        WHERE bl.boss_id = ?
        ORDER BY bl.slot IS NULL, bl.slot, bl.difficulty, bl.item_id""",
        rs -> new LootRow(new EntityRef(EntityKind.ITEM, rs.getLong("item_id")), rs.getString("name"),
            Sql.integer(rs, "item_level"), Sql.doubleValue(rs, "drop_pct"), BOSS_LOOT_ORIGIN, Sql.integer(rs, "slot"),
            rs.getInt("disagrees") != 0, rs.getInt("source_count")),
        bossId));
  }

  static InstanceSummary summary(ResultSet rs) throws SQLException {
    return new InstanceSummary(rs.getLong("id"), rs.getString("name"), rs.getString("content_type"),
        Sql.integer(rs, "level_min"), Sql.integer(rs, "level_max"), Sql.longValue(rs, "zone_id"),
        rs.getString("zone_name"));
  }
}
