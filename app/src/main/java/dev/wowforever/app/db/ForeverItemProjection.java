package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

/** Connection-local Forever view. Archived Era records remain unchanged in {@code main.item}. */
final class ForeverItemProjection {
  private ForeverItemProjection() {}
  private static final Set<String> IDENTITY = Set.of("id", "client_source", "flags", "food_type", "ammo_type", "start_quest_id", "teaches_spell_id");

  static void install(Connection c) throws SQLException {
    if (!Sql.hasTable(c, "item")) return;
    var columns = new ArrayList<String>();
    try (var statement = c.createStatement(); var rows = statement.executeQuery("PRAGMA main.table_info(item)")) {
      while (rows.next()) columns.add(rows.getString("name"));
    }
    // Older fixtures without source provenance have no Era fallback to mask.
    if (!columns.contains("client_source")) return;
    var projection = new ArrayList<String>();
    for (String column : columns) {
      String quoted = "\"" + column.replace("\"", "\"\"") + "\"";
      if (IDENTITY.contains(column)) projection.add(quoted);
      else projection.add("CASE WHEN client_source='era' THEN "
          + (column.equals("has_client_data") ? "0" : "NULL") + " ELSE " + quoted + " END AS " + quoted);
    }
    try (var statement = c.createStatement()) {
      statement.execute("CREATE TEMP VIEW item AS SELECT " + String.join(",", projection) + " FROM main.item");
    }
  }
}
