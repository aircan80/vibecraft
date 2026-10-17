package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Optional addon-reported presence provenance, independent of any rate source. */
final class ForeverLootFacts {
  private ForeverLootFacts() {}
  static String origin(Connection c) throws SQLException {
    if(!Sql.hasTable(c,"atlasloot_forever_fact"))return "'atlasloot'";
    return "COALESCE((SELECT group_concat(label,' + ') || ' (reported)' FROM (SELECT DISTINCT "
        + "CASE f.provider WHEN 'atlasloot-forever' THEN 'AtlasLoot Forever' "
        + "WHEN 'atlasloot-continued-forever' THEN 'AtlasLoot Continued' ELSE f.provider END AS label "
        + "FROM atlasloot_forever_fact f WHERE f.boss_id=bl.boss_id AND f.item_id=bl.item_id "
        + "AND f.difficulty=bl.difficulty ORDER BY label)), 'atlasloot')";
  }
}
