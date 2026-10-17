package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Optional capture evidence, queried on the repository connection rather than the UI thread. */
final class CollectorFacts {
  private CollectorFacts() {}

  static String observed(Connection c, String category, String entityType, String entityId) throws SQLException {
    return observed(c, category, entityType, entityId, "d.item_id");
  }

  static String observed(Connection c, String category, String entityType, String entityId, String itemId) throws SQLException {
    if (!Sql.hasTable(c, "collector_fact")) {
      return "0";
    }
    return "EXISTS (SELECT 1 FROM collector_fact cf WHERE cf.category = '" + category
        + "' AND cf.entity_type = '" + entityType + "' AND cf.item_id = " + itemId + " AND cf.entity_id = "
        + entityId + ")";
  }
}
