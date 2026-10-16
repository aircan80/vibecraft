package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.clientdata.ClientDataSource;
import dev.wowforever.importer.clientdata.ClientItem;
import dev.wowforever.importer.clientdata.ItemClassNames;
import dev.wowforever.importer.clientdata.ItemEffectLoader;
import dev.wowforever.importer.clientdata.ItemSetLoader;
import dev.wowforever.importer.clientdata.ItemSparseLoader;
import dev.wowforever.importer.clientdata.ItemStat;
import dev.wowforever.importer.clientdata.ItemTooltips;
import dev.wowforever.importer.clientdata.ItemValues;
import dev.wowforever.importer.clientdata.ScalingTables;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaValue;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteWriterClientDataTest {

  @TempDir Path tmp;

  @Test
  void schemaHasEveryPhase2TableColumnAndIndex() throws Exception {
    Path db = tmp.resolve("t.sqlite");
    Items b = new Items();
    b.item(25, "Worn Shortsword");
    SqliteWriter.write(db, b.model());
    try (Connection c = open(db)) {
      assertThat(column(c, "select name from pragma_table_info('item')")).contains("quality", "inventory_type",
          "bonding", "sell_price", "buy_price", "max_stack", "icon_id", "item_set_id", "description",
          "allowable_class", "allowable_race", "required_skill", "required_skill_rank", "has_client_data",
          "client_source");
      assertThat(column(c, "select name from pragma_table_info('zone')")).contains("ui_map_id");
      for (String t : List.of("item_npc_drop", "boss_loot")) {
        assertThat(column(c, "select name || ':' || \"notnull\" || ':' || dflt_value from pragma_table_info('" + t
            + "') where name in ('disagrees', 'source_count') order by name"))
            .containsExactly("disagrees:1:0", "source_count:1:0");
      }
      assertThat(column(c, "select name from sqlite_master where type = 'table'")).contains("item_alt_value",
          "item_stat", "item_damage", "item_armor", "item_effect", "item_set", "item_set_item", "item_set_bonus",
          "item_tooltip_line", "icon", "zone_map", "drop_rate_source");
      assertThat(column(c, "select name from pragma_table_info('item_effect')"))
          .containsExactly("item_id", "ordinal", "trigger", "spell_id", "text");
      assertThat(column(c, "select name from pragma_table_info('item_damage') where pk = 1")).containsExactly("item_id");
      assertThat(column(c, "select name from pragma_table_info('drop_rate_source') where pk > 0 order by pk"))
          .containsExactly("npc_id", "item_id", "source");
      assertThat(column(c, "select name from sqlite_master where type = 'index' and name like 'idx_%'")).contains(
          "idx_item_stat_item", "idx_item_effect_item", "idx_item_set_item_set", "idx_item_set_item_item",
          "idx_item_tooltip_line_item", "idx_item_alt_value_item", "idx_drop_rate_source_item", "idx_item_quality",
          "idx_item_icon");
      assertThat(scalar(c, "select has_client_data from item where id = 25")).isEqualTo(0);
    }
  }

  @Test
  void clientValuesReplaceQuestiesAndDifferencesBecomeAltValues() throws Exception {
    Items b = new Items();
    b.item(17204, "Eye of Sulfuras").set("itemLevel", 60).set("class", 7).set("subClass", 11);
    b.item(2874, "An Unsent Letter").set("itemLevel", 15).set("requiredLevel", 16).set("class", 12)
        .set("subClass", 0).set("startQuest", 373);
    b.item(25, "Worn Short Sword").set("itemLevel", 2).set("requiredLevel", 1).set("class", 2).set("subClass", 7);
    b.item(16800, "Arcanist Boots").set("itemLevel", 66).set("requiredLevel", 60).set("class", 4).set("subClass", 1);
    b.item(999_999, "Questie Only").set("itemLevel", 10).set("class", 15).set("subClass", 0);
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> client = ItemSparseLoader.load(fixtureSource(), b.ids(), report);
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, client, null);

    try (Connection c = open(db)) {
      assertThat(row(c, "select quality, has_client_data, client_source, icon_id, item_set_id, sell_price,"
          + " buy_price, inventory_type, bonding, max_stack, allowable_class, allowable_race from item where id = 17204"))
          .containsExactly(5, 1, "forever", 134124, null, 200000, 800000, 0, 1, 1, -1, -1);
      assertThat(row(c, "select quality, has_client_data, client_source, item_set_id, icon_id, name from item"
          + " where id = 16800")).containsExactly(4, 1, "era", 201, 132541, "Arcanist Boots");
      assertThat(row(c, "select quality, has_client_data, client_source, icon_id, name, item_level from item"
          + " where id = 999999")).containsExactly(null, 0, null, null, "Questie Only", 10);

      assertThat(scalar(c, "select name from item where id = 25")).isEqualTo("Worn Shortsword");
      assertThat(scalar(c, "select item_level from item where id = 2874")).isEqualTo(16);
      assertThat(scalar(c, "select start_quest_id from item where id = 2874")).isEqualTo(373);
      assertThat(column(c, "select item_id || ':' || field || ':' || source || ':' || value from item_alt_value"
          + " order by item_id, field")).containsExactly("25:name:questie:Worn Short Sword",
          "2874:item_level:questie:15");

      assertThat(scalar(c, "select name from search_index where kind = 'item' and id = 25")).isEqualTo("Worn Shortsword");
      assertThat(column(c, "select id from search_index where search_index match '\"shortsword\"'"))
          .containsExactly(25L);
      assertThat(column(c, "select id from search_index where search_index match '\"short\"'")).isEmpty();
    }
    assertThat(report.get("clientdata.alt.name")).isEqualTo(1);
    assertThat(report.get("clientdata.alt.item_level")).isEqualTo(1);
    assertThat(report.get("clientdata.alt.required_level")).isZero();
    assertThat(report.get("clientdata.alt.class_id")).isZero();
  }

  @Test
  void computedStatsDamageAndArmourAreWritten() throws Exception {
    Items b = new Items();
    b.item(19019, "Thunderfury");
    b.item(12640, "Lionheart Helm");
    b.item(19321, "The Immovable Object");
    b.item(19316, "Ice Threaded Arrow");
    b.item(16800, "Arcanist Boots");
    b.item(4134, "Nimboya's Mystical Staff");
    b.item(999_999, "Questie Only");
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> client = ItemSparseLoader.load(fixtureSource(), b.ids(), report);
    Map<Long, ItemValues> values = ItemValues.compute(client, ScalingTables.load(fixtureSource()), report);
    values = new java.util.HashMap<>(values);
    values.put(424_242L, new ItemValues(List.of(new ItemStat(7, 1)), null, null)); // not an item row: not written
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, client, null, values);

    try (Connection c = open(db)) {
      assertThat(column(c, "select ordinal || ':' || stat_id || ':' || value from item_stat where item_id = 19019"
          + " order by ordinal")).containsExactly("0:3:5", "1:7:8", "2:51:8", "3:55:9");
      assertThat(column(c, "select stat_id || ':' || value from item_stat where item_id = 16800 order by ordinal"))
          .as("Era absolute values, resistances last").containsExactly("7:13", "5:14", "6:11", "54:10");
      assertThat(row(c, "select min, max, school, delay_ms, round(dps, 2) from item_damage where item_id = 19019"))
          .containsExactly(82, 153, 0, 1900, 61.84);
      assertThat(row(c, "select min, max, delay_ms, dps from item_damage where item_id = 19316"))
          .as("ammunition has no speed").containsExactly(16, 17, null, 16.5);
      assertThat(scalar(c, "select count(*) from item_damage where item_id = 4134")).as("unverified").isEqualTo(0);
      assertThat(column(c, "select item_id || ':' || armor || ':' || coalesce(block, 'null') from item_armor"
          + " order by item_id")).containsExactly("12640:565:null", "16800:70:null", "19321:2291:null");
      assertThat(scalar(c, "select count(*) from item_stat where item_id in (999999, 424242)")).isEqualTo(0);
    }
    assertThat(report.get("clientdata.stats.items")).as("all but the arrow").isEqualTo(5);
    assertThat(report.get("clientdata.damage.items")).isEqualTo(2);
    assertThat(report.get("clientdata.armor.items")).isEqualTo(3);
    assertThat(report.get("clientdata.unverified.damage")).isEqualTo(1);
    assertThat(report.get("clientdata.unverified.block")).isEqualTo(1);
    assertThat(report.counters()).containsKeys("clientdata.unverified.stats", "clientdata.unverified.armor");
  }

  @Test
  void effectsSetsAndTooltipLinesAreWrittenForItemRows() throws Exception {
    Items b = new Items();
    b.item(19019, "Thunderfury");
    b.item(19682, "Bloodvine Vest");
    b.item(5079, "Cold Basilisk Eye");
    b.item(13482, "Recipe: Transmute Air to Fire");
    b.item(999_999, "Questie Only");
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> client = new java.util.HashMap<>(ItemSparseLoader.load(fixtureSource(), b.ids(), report));
    Map<Long, ItemValues> values = ItemValues.compute(client, ScalingTables.load(fixtureSource()), report);
    ItemTooltips tooltips = ItemTooltips.build(fixtureSource(), client, values, Map.of(), report);
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, client, null, values, tooltips);

    try (Connection c = open(db)) {
      assertThat(row(c, "select ordinal, trigger, spell_id, text from item_effect where item_id = 5079"))
          .containsExactly(0, 0, 1139, "Increases time between target's attacks by 20% and reduces their movement "
              + "speed by 40% for 15 sec.");
      assertThat(column(c, "select item_id || ':' || trigger || ':' || spell_id from item_effect order by item_id"))
          .containsExactly("5079:0:1139", "13482:0:17559", "19019:2:21992");
      assertThat(column(c, "select id || ':' || name from item_set")).containsExactly("421:Bloodvine Garb");
      assertThat(column(c, "select item_id from item_set_item where set_id = 421 order by rowid"))
          .containsExactly(19682L, 19683L, 19684L);
      assertThat(row(c, "select threshold, spell_id, text from item_set_bonus where set_id = 421"))
          .containsExactly(3, 18382, "Improves your chance to get a critical strike with spells by 2.0%.");
      assertThat(column(c, "select kind from item_tooltip_line where item_id = 19019 order by ordinal"))
          .startsWith("name", "binding", "unique", "slot", "damage").endsWith("proc", "item_level", "sell_price");
      assertThat(row(c, "select text, colour from item_tooltip_line where item_id = 19019 and ordinal = 0"))
          .containsExactly("Thunderfury, Blessed Blade of the Windseeker", "#ff8000");
      assertThat(scalar(c, "select count(*) from item_tooltip_line where item_id = 999999")).isEqualTo(0);
      assertThat(scalar(c, "select count(*) from item_tooltip_line"))
          .isEqualTo((int) report.get(ItemTooltips.LINES));
    }
    assertThat(report.get(ItemTooltips.ITEMS)).isEqualTo(4);
    assertThat(report.get(ItemEffectLoader.EFFECTS)).isEqualTo(3);
    assertThat(report.get(ItemSetLoader.SETS)).isEqualTo(1);
  }

  @Test
  void withoutTooltipsTheTooltipTablesStayEmpty() throws Exception {
    Items b = new Items();
    b.item(19019, "Thunderfury");
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), new ImportReport(), ItemSparseLoader.load(fixtureSource(), b.ids(),
        new ImportReport()), null, Map.of());
    try (Connection c = open(db)) {
      assertThat(scalar(c, "select (select count(*) from item_effect) + (select count(*) from item_set)"
          + " + (select count(*) from item_set_item) + (select count(*) from item_set_bonus)"
          + " + (select count(*) from item_tooltip_line)")).isEqualTo(0);
    }
  }

  @Test
  void withoutValuesTheValueTablesStayEmpty() throws Exception {
    Items b = new Items();
    b.item(19019, "Thunderfury");
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), new ImportReport(), ItemSparseLoader.load(fixtureSource(), b.ids(),
        new ImportReport()), null);
    try (Connection c = open(db)) {
      assertThat(scalar(c, "select (select count(*) from item_stat) + (select count(*) from item_damage)"
          + " + (select count(*) from item_armor)")).isEqualTo(0);
    }
  }

  @Test
  void everyReplacedFieldIsRecordedUnderItsColumnName() throws Exception {
    Items b = new Items();
    // client: Eye of Sulfuras, item level 60, required level 0, class 7, subclass 11
    b.item(17204, "Eye of Sulfuras").set("itemLevel", 60).set("requiredLevel", 1).set("class", 15)
        .set("subClass", 4);
    ImportReport report = new ImportReport();
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, ItemSparseLoader.load(fixtureSource(), b.ids(), report), null);
    try (Connection c = open(db)) {
      assertThat(row(c, "select required_level, class_id, subclass_id from item where id = 17204"))
          .containsExactly(0, 7, 11);
      assertThat(column(c, "select field || ':' || source || ':' || value from item_alt_value where item_id = 17204"
          + " order by field")).containsExactly("class_id:questie:15", "required_level:questie:1",
          "subclass_id:questie:4");
    }
    assertThat(report.get("clientdata.alt.required_level")).isEqualTo(1);
    assertThat(report.get("clientdata.alt.class_id")).isEqualTo(1);
    assertThat(report.get("clientdata.alt.subclass_id")).isEqualTo(1);
    assertThat(report.get("clientdata.alt.name")).isZero();
    assertThat(report.get("clientdata.alt.item_level")).isZero();
  }

  @Test
  void namesDifferingOnlyInSurroundingWhitespaceAreNotAltValues() throws Exception {
    Items b = new Items();
    b.item(17204, "  Eye of Sulfuras ");
    ImportReport report = new ImportReport();
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, ItemSparseLoader.load(fixtureSource(), b.ids(), report), null);
    try (Connection c = open(db)) {
      assertThat(scalar(c, "select name from item where id = 17204")).isEqualTo("Eye of Sulfuras");
      assertThat(scalar(c, "select count(*) from item_alt_value")).isEqualTo(0);
    }
    assertThat(report.get("clientdata.alt.name")).isZero();
  }

  @Test
  void questieGapsTakeTheClientValueWithoutAnAltValue() throws Exception {
    Items b = new Items();
    b.item(17204, null); // a stub without name, level or class
    ImportReport report = new ImportReport();
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, ItemSparseLoader.load(fixtureSource(), b.ids(), report), null);
    try (Connection c = open(db)) {
      assertThat(row(c, "select name, item_level, class_id, subclass_id from item where id = 17204"))
          .containsExactly("Eye of Sulfuras", 60, 7, 11);
      assertThat(scalar(c, "select count(*) from item_alt_value")).isEqualTo(0);
      assertThat(column(c, "select id from search_index")).containsExactly(17204L);
    }
    assertThat(report.get("nameless.item")).isZero();
  }

  @Test
  void itemOnlyRowsSetTheIconAndFillOnlyAMissingClass() throws Exception {
    Items b = new Items();
    b.item(14145, "Cursed Felblade"); // Questie has no class
    b.item(5191, "Cruel Barb").set("class", 2).set("subClass", 7).set("itemLevel", 24);
    b.item(7736, "Fight Club").set("class", 4); // subclass missing, same class as the Item row
    Map<Long, ClientItem> client = Map.of(
        14145L, itemOnly(14145, 2, 7, 135648L),
        5191L, itemOnly(5191, 4, 1, 135325L),
        7736L, itemOnly(7736, 4, 3, 133486L));
    ImportReport report = new ImportReport();
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), report, client, null);
    try (Connection c = open(db)) {
      assertThat(row(c, "select class_id, subclass_id, icon_id, has_client_data, client_source, quality from item"
          + " where id = 14145")).containsExactly(2, 7, 135648, 0, null, null);
      assertThat(row(c, "select class_id, subclass_id, icon_id, has_client_data, item_level from item where id = 5191"))
          .as("Questie's class is kept, never replaced by the Item row's").containsExactly(2, 7, 135325, 0, 24);
      assertThat(row(c, "select class_id, subclass_id from item where id = 7736")).containsExactly(4, 3);
      assertThat(scalar(c, "select count(*) from item_alt_value")).isEqualTo(0);
    }
  }

  @Test
  void clientClassNamesReplaceTheStaticTable() throws Exception {
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, new Items().model(), new ImportReport(), Map.of(), ItemClassNames.load(fixtureSource()));
    try (Connection c = open(db)) {
      assertThat(scalar(c, "select count(*) from item_class")).isEqualTo(17);
      assertThat(scalar(c, "select name from item_class where id = 18")).isEqualTo("WoW Token");
      assertThat(scalar(c, "select name from item_subclass where class_id = 2 and id = 7"))
          .isEqualTo("One-Handed Swords");
      assertThat(scalar(c, "select count(*) from item_subclass")).isEqualTo(100);
    }
  }

  @Test
  void anEmptyClientMapKeepsTheQuestieOnlyBehaviour() throws Exception {
    Items b = new Items();
    b.item(17204, "Eye of Sulfuras").set("itemLevel", 59);
    Path db = tmp.resolve("t.sqlite");
    SqliteWriter.write(db, b.model(), new ImportReport(), Map.of(), null);
    try (Connection c = open(db)) {
      assertThat(row(c, "select name, item_level, quality, icon_id, has_client_data, client_source from item"))
          .containsExactly("Eye of Sulfuras", 59, null, null, 0, null);
      assertThat(scalar(c, "select name from item_class where id = 2")).isEqualTo("Weapon");
      assertThat(scalar(c, "select count(*) from item_alt_value")).isEqualTo(0);
    }
  }

  // ---------------------------------------------------------------------------------------------

  private static ClientDataSource fixtureSource() {
    return new ClientDataSource(Fixtures.clientDataRoot());
  }

  private static ClientItem itemOnly(long id, int classId, int subclassId, long iconId) {
    return new ClientItem(id, false, null, null, null, classId, subclassId, 13, null, null, null, null, iconId, null,
        null, null, null, null, null, null, List.of(), null, List.of(), null, null, null, null, null, null);
  }

  /** Questie items with the fixture's real item schema. */
  private static final class Items {
    final EntityTable table = new EntityTable(EntityKind.ITEM,
        MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/itemMeta.lua")));

    Row item(long id, String name) {
      table.create(id);
      Row r = new Row(table, id);
      return name == null ? r : r.set("name", new LuaString(name));
    }

    java.util.Set<Long> ids() {
      return table.rows().keySet();
    }

    ImportModel model() {
      Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
      tables.put(EntityKind.ITEM, table);
      return new ImportModel(tables, new DropRates(), new LinkedHashMap<>(), List.of(), new LinkedHashMap<>());
    }
  }

  private record Row(EntityTable table, long id) {
    Row set(String field, LuaValue v) {
      table.set(id, field, v);
      return this;
    }

    Row set(String field, long v) {
      return set(field, LuaNumber.of(v));
    }
  }

  private static Connection open(Path file) throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + file);
  }

  private static Object scalar(Connection c, String sql) throws SQLException {
    List<Object> r = row(c, sql);
    return r.get(0);
  }

  private static List<Object> row(Connection c, String sql) throws SQLException {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      assertThat(rs.next()).as("a row for: " + sql).isTrue();
      List<Object> out = new ArrayList<>();
      for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
        out.add(rs.getObject(i));
      }
      return out;
    }
  }

  private static List<Object> column(Connection c, String sql) throws SQLException {
    List<Object> out = new ArrayList<>();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      while (rs.next()) {
        Object v = rs.getObject(1);
        out.add(v instanceof Integer i ? Long.valueOf(i) : v);
      }
    }
    return out;
  }
}
