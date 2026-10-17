package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.drops.DropRates;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The schema v3 (phase 3A) tables, columns and indexes. */
class SchemaTest {

  @Test
  void v3TablesExist(@TempDir Path tmp) throws Exception {
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, new ImportModel(Map.of(), new DropRates(), new LinkedHashMap<>(), List.of(),
        new LinkedHashMap<>()));
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
      assertThat(columns(c, "quest_objective"))
          .containsExactly("quest_id", "ordinal", "kind", "entity_id", "text", "credit_group");
      assertThat(columns(c, "quest_objective_point")).containsExactly("quest_id", "ordinal", "zone_id", "x", "y");
      assertThat(columns(c, "quest_source_item")).containsExactly("quest_id", "item_id", "kind");
      assertThat(columns(c, "quest_relation")).containsExactly("quest_id", "related_id", "kind");
      assertThat(columns(c, "ui_asset")).containsExactly("name", "png");
      assertThat(column(c, "select name from pragma_table_info('ui_asset') where pk = 1")).containsExactly("name");
      assertThat(column(c, "select name from pragma_table_info('ui_asset') where \"notnull\" = 1"))
          .containsExactly("png");
      assertThat(columns(c, "quest")).as("additive: the v2 columns stay first").startsWith("id", "name",
          "required_level", "quest_level", "zone_or_sort", "required_races", "required_classes", "objectives_text",
          "quest_flags", "special_flags").endsWith("repeatable", "required_skill_id", "required_skill_value",
          "required_min_rep_faction", "required_min_rep_value", "sort_label");
      assertThat(column(c, "select type from pragma_table_info('quest') where name = 'sort_label'"))
          .containsExactly("TEXT");
      assertThat(column(c, "select name from pragma_table_info('quest_objective') where name = 'count'"))
          .as("Questie records no objective counts").isEmpty();

      assertThat(indexColumns(c, "idx_quest_relation_related")).containsExactly("related_id");
      assertThat(indexColumns(c, "idx_quest_objective_entity")).containsExactly("entity_id", "kind");
      assertThat(indexColumns(c, "idx_quest_giver_entity_role")).containsExactly("entity_id", "kind", "role");
      assertThat(indexColumns(c, "idx_quest_source_item_item")).containsExactly("item_id");
      assertThat(indexColumns(c, "idx_quest_objective_quest")).containsExactly("quest_id", "ordinal");
      assertThat(indexColumns(c, "idx_quest_objective_point_quest")).containsExactly("quest_id", "ordinal");

      assertThat(column(c, "select value from meta where key = 'schema_version'"))
          .containsExactly(String.valueOf(SqliteWriter.SCHEMA_VERSION));
      assertThat(SqliteWriter.SCHEMA_VERSION).isEqualTo(3);
    }
  }

  private static List<Object> columns(Connection c, String table) throws SQLException {
    return column(c, "select name from pragma_table_info('" + table + "') order by cid");
  }

  private static List<Object> indexColumns(Connection c, String index) throws SQLException {
    return column(c, "select name from pragma_index_info('" + index + "') order by seqno");
  }

  private static List<Object> column(Connection c, String sql) throws SQLException {
    List<Object> out = new ArrayList<>();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getObject(1));
      }
    }
    return out;
  }
}
