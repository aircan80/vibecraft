package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.atlasloot.AtlasBoss;
import dev.wowforever.importer.atlasloot.AtlasInstance;
import dev.wowforever.importer.atlasloot.AtlasLoot;
import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.FieldType;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.importer.zones.Zone;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaUnresolved;
import dev.wowforever.lua.LuaValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteWriterTest {

  @Test
  void writesEntityWithNullFields(@TempDir Path tmp) throws Exception {
    ImportModel m = minimalModelWithItem(190179, "Avelina's Heart"); // only name + npcDrops set
    SqliteWriter.write(tmp.resolve("t.sqlite"), m);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      ResultSet rs = c.createStatement().executeQuery("select name, item_level, class_id from item where id = 190179");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("Avelina's Heart");
      assertThat(rs.getObject(2)).isNull();
      assertThat(rs.getObject(3)).isNull();
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 190179 and npc_id = 11520")).isEqualTo("questie");
    }
  }

  @Test
  void fieldsMissingFromTheSchemaAreCountedOncePerKindAndField(@TempDir Path tmp) throws Exception {
    EntitySchema slim = new EntitySchema("Item", List.of("name", "npcDrops"),
        List.of(FieldType.STRING, FieldType.TABLE), java.util.Arrays.asList(null, null));
    EntityTable items = new EntityTable(EntityKind.ITEM, slim);
    items.create(1);
    items.set(1, "name", new LuaString("One"));
    items.create(2);
    items.set(2, "name", new LuaString("Two"));
    Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    tables.put(EntityKind.ITEM, items);
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"),
        new ImportModel(tables, new DropRates(), new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()), report);
    assertThat(report.get("missingField.item.itemLevel")).isEqualTo(1);
    assertThat(report.get("missingField.item.teachesSpell")).isEqualTo(1);
    assertThat(report.get("missingField.item.name")).isZero();
    assertThat(report.get("missingField.item.npcDrops")).isZero();
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select count(*) from item where item_level is null")).isEqualTo(2);
    }
  }

  @Test
  void mergesAtlasLootIntoNpcDropsWithOrigin(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(14149, "Subterranean Cape").set("npcDrops", ids(11520));
    b.item(14148, "Crystalline Cuffs");
    b.rates.put(11520, 14149, new DropRate(17.5, "questie-wowhead"), 3);
    b.instances.add(instance("RagefireChasm", "Ragefire Chasm", 2437L,
        boss("Taragaman the Hungerer", 11520L, false, 1, loot(1, 14149), loot(2, 14148))));
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 14149 and npc_id = 11520")).isEqualTo("both");
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 14148 and npc_id = 11520")).isEqualTo("atlasloot");
      assertThat(scalar(c, "select count(*) from item_npc_drop")).isEqualTo(2);
      assertThat(scalar(c, "select count(*) from boss_loot")).isEqualTo(2);
      assertThat(scalar(c, "select drop_pct from item_npc_drop where item_id = 14149")).isEqualTo(17.5);
      assertThat(scalar(c, "select pct_source from item_npc_drop where item_id = 14149")).isEqualTo("questie-wowhead");
      assertThat(scalar(c, "select drop_pct from boss_loot where item_id = 14149")).isEqualTo(17.5);
      assertThat(scalar(c, "select drop_pct from item_npc_drop where item_id = 14148")).isNull();
      assertThat(scalar(c, "select slot from boss_loot where item_id = 14148")).isEqualTo(2);
    }
  }

  @Test
  void writesDropRateSourcesAndDisagreement(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(14149, "Subterranean Cape").set("npcDrops", ids(11520));
    b.item(14148, "Crystalline Cuffs");
    b.rates.put(11520, 14149, new DropRate(17.5, "questie-wowhead"), 3);
    b.rates.put(11520, 14149, new DropRate(19.0, "atlasloot"), 4);
    b.rates.put(11520, 14148, new DropRate(10.0, "questie-wowhead"), 3);
    b.rates.put(11520, 14148, new DropRate(14.0, "atlasloot"), 4);
    b.instances.add(instance("RagefireChasm", "Ragefire Chasm", 2437L,
        boss("Taragaman the Hungerer", 11520L, false, 1, loot(1, 14149), loot(2, 14148))));
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), report);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select count(*) from drop_rate_source")).isEqualTo(4);
      assertThat(scalar(c, "select source_count || '/' || disagrees from item_npc_drop where item_id = 14149")).isEqualTo("2/0");
      assertThat(scalar(c, "select source_count || '/' || disagrees from boss_loot where item_id = 14149")).isEqualTo("2/0");
      assertThat(scalar(c, "select source_count || '/' || disagrees from item_npc_drop where item_id = 14148")).isEqualTo("2/1");
      assertThat(scalar(c, "select source_count || '/' || disagrees from boss_loot where item_id = 14148")).isEqualTo("2/1");
    }
    assertThat(report.get("provenance.pairs")).isEqualTo(2);
    assertThat(report.get("provenance.disagreeing")).isEqualTo(1);
    assertThat(report.render()).contains("questie-wowhead vs atlasloot: 1 disagreeing of 2 shared");
  }

  @Test
  void ftsIndexFindsPrefix(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(14149, "Subterranean Cape");
    b.item(14148, "Crystalline Cuffs");
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(column(c, "select id from search_index where search_index match '\"subterr\"*'")).containsExactly(14149L);
      assertThat(scalar(c, "select kind from search_index where search_index match '\"subterr\"*'")).isEqualTo("item");
    }
  }

  @Test
  void namelessEntitiesAreStoredButNotIndexed(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(1307, null).set("itemLevel", LuaNumber.of(5));
    b.item(14149, "Subterranean Cape");
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), report);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select count(*) from item where id = 1307 and name is null and item_level = 5")).isEqualTo(1);
      assertThat(column(c, "select id from search_index")).containsExactly(14149L);
    }
    assertThat(report.get("nameless.item")).isEqualTo(1);
  }

  @Test
  void emptyAndUnresolvedFieldsYieldNoRowsOrNull(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(5191, "Cruel Barb")
        .set("npcDrops", new LuaTable())
        .set("vendors", table(LuaNumber.of(1), new LuaUnresolved("unknown symbol", 3)))
        .set("itemLevel", new LuaUnresolved("unknown symbol", 4))
        .set("requiredLevel", LuaNumber.of(19));
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), report);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select count(*) from item_npc_drop")).isEqualTo(0);
      assertThat(scalar(c, "select count(*) from item_vendor")).isEqualTo(0);
      assertThat(scalar(c, "select item_level from item where id = 5191")).isNull();
      assertThat(scalar(c, "select required_level from item where id = 5191")).isEqualTo(19);
    }
    assertThat(report.get("unresolved.item.vendors")).isEqualTo(1);
    assertThat(report.get("unresolved.item.itemLevel")).isEqualTo(1);
  }

  @Test
  void itemRelationFieldsBecomeRelationRows(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.item(2874, "An Unsent Letter")
        .set("objectDrops", ids(1560))
        .set("itemDrops", ids(6827))
        .set("vendors", ids(5, 6, 5))
        .set("questRewards", ids(373))
        .set("relatedQuests", ids(373, 374))
        .set("startQuest", LuaNumber.of(373))
        .set("class", LuaNumber.of(12))
        .set("subClass", LuaNumber.of(0))
        .set("flags", LuaNumber.of(2048));
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(column(c, "select object_id from item_object_drop where item_id = 2874")).containsExactly(1560L);
      assertThat(column(c, "select container_item_id from item_container_drop where item_id = 2874")).containsExactly(6827L);
      assertThat(column(c, "select npc_id from item_vendor where item_id = 2874 order by npc_id")).containsExactly(5L, 6L);
      assertThat(column(c, "select quest_id from quest_reward_item where item_id = 2874")).containsExactly(373L);
      assertThat(column(c, "select quest_id from item_related_quest where item_id = 2874 order by quest_id"))
          .containsExactly(373L, 374L);
      assertThat(scalar(c, "select start_quest_id from item where id = 2874")).isEqualTo(373);
      assertThat(scalar(c, "select class_id || '/' || subclass_id || '/' || flags from item where id = 2874"))
          .isEqualTo("12/0/2048");
    }
  }

  @Test
  void questGiversAndObjectivesTextAreMapped(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.row(EntityKind.QUEST, 2, "Sharptalon's Claw")
        .set("startedBy", table(LuaNil.NIL, LuaNil.NIL, ids(16305)))
        .set("finishedBy", table(ids(12696), ids(35)))
        .set("objectivesText", table(new LuaString("Bring the claw."), new LuaString("Then rest.")))
        .set("questLevel", LuaNumber.of(30))
        .set("requiredRaces", new LuaNumber(8589934770d))
        .set("zoneOrSort", LuaNumber.of(331));
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(column(c, "select kind || ':' || entity_id || ':' || role from quest_giver where quest_id = 2 order by role, kind"))
          .containsExactly("npc:12696:end", "object:35:end", "item:16305:start");
      assertThat(scalar(c, "select objectives_text from quest where id = 2")).isEqualTo("Bring the claw.\nThen rest.");
      assertThat(scalar(c, "select required_races from quest where id = 2")).isEqualTo(8589934770L);
      assertThat(scalar(c, "select zone_or_sort from quest where id = 2")).isEqualTo(331);
      assertThat(scalar(c, "select kind from search_index where id = 2")).isEqualTo("quest");
    }
  }

  @Test
  void spawnsBecomeRowsAndUnknownZonesGetPlaceholders(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.zones.put(1581L, new Zone(1581, "The Deadmines", 40L, "dungeon"));
    LuaTable spawns = new LuaTable();
    spawns.put(LuaNumber.of(1581), table(coords(10.5, 20.25), table(LuaNumber.of(3), LuaNumber.of(4), LuaNumber.of(1))));
    spawns.put(LuaNumber.of(77777), table(coords(5, 6)));
    b.row(EntityKind.NPC, 639, "Edwin VanCleef").set("spawns", spawns).set("zoneID", LuaNumber.of(1581))
        .set("subName", new LuaString("Defias Kingpin")).set("minLevel", LuaNumber.of(21));
    LuaTable objectSpawns = new LuaTable();
    objectSpawns.put(LuaNumber.of(1581), table(coords(1, 2)));
    b.row(EntityKind.OBJECT, 1560, "Storage Chest").set("spawns", objectSpawns).set("zoneID", LuaNumber.of(0));
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), report);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(column(c, "select zone_id || ':' || x || ':' || y from npc_spawn where npc_id = 639 order by zone_id, x"))
          .containsExactly("1581:3.0:4.0", "1581:10.5:20.25", "77777:5.0:6.0");
      assertThat(scalar(c, "select count(*) from object_spawn where object_id = 1560 and zone_id = 1581")).isEqualTo(1);
      assertThat(scalar(c, "select name || '|' || kind || '|' || ifnull(parent_id, 'none') from zone where id = 77777"))
          .isEqualTo("Zone #77777|unknown|none");
      assertThat(scalar(c, "select count(*) from npc_spawn s left join zone z on z.id = s.zone_id where z.id is null")).isEqualTo(0);
      assertThat(scalar(c, "select sub_name from npc where id = 639")).isEqualTo("Defias Kingpin");
      assertThat(scalar(c, "select zone_id from game_object where id = 1560")).isNull();
      assertThat(column(c, "select id from search_index where kind = 'zone'")).containsExactly(1581L);
    }
    assertThat(report.get("spawn.unknownZone")).isEqualTo(1);
  }

  @Test
  void zonePlaceholderNamesStayOutOfTheSearchIndex(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.zones.put(1581L, new Zone(1581, "The Deadmines", 40L, "dungeon"));
    b.zones.put(9999L, new Zone(9999, "Zone #9999", 1581L, "subzone"));
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), new ImportReport());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select name from zone where id = 9999")).isEqualTo("Zone #9999");
      assertThat(column(c, "select id from search_index where kind = 'zone'")).containsExactly(1581L);
      assertThat(scalar(c, "select count(*) from search_index where name like 'Zone #%'")).isEqualTo(0);
    }
  }

  @Test
  void instancesAndBossesGetSequentialIdsAndZoneLinks(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.zones.put(1581L, new Zone(1581, "The Deadmines", 40L, "dungeon"));
    b.item(5191, "Cruel Barb");
    b.rates.put(639, 5191, new DropRate(12.0, "atlasloot"), 4);
    b.instances.add(instance("TheDeadmines", "The Deadmines", 1581L,
        boss("Edwin VanCleef", 639L, false, 1, loot(4, 5191)),
        boss("Trash Mobs", null, true, 2, loot(1, 8492))));
    b.instances.add(instance("Nowhere", "Nowhere", 9999L, boss("Someone", 1L, false, 1)));
    ImportReport report = new ImportReport();
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model(), report);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(column(c, "select id || ':' || key || ':' || ifnull(zone_id, 'null') from instance order by id"))
          .containsExactly("1:TheDeadmines:1581", "2:Nowhere:null");
      assertThat(column(c, "select id || ':' || instance_id || ':' || name || ':' || is_extra_list from boss order by id"))
          .containsExactly("1:1:Edwin VanCleef:0", "2:1:Trash Mobs:1", "3:2:Someone:0");
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 5191 and npc_id = 639")).isEqualTo("atlasloot");
      assertThat(scalar(c, "select count(*) from item_npc_drop where item_id = 8492")).isEqualTo(0);
      assertThat(scalar(c, "select count(*) from boss_loot where boss_id = 2 and item_id = 8492")).isEqualTo(1);
      assertThat(scalar(c, "select drop_pct from boss_loot where boss_id = 2")).isNull();
      assertThat(scalar(c, "select drop_pct from boss_loot where boss_id = 1")).isEqualTo(12.0);
      assertThat(scalar(c, "select kind from search_index where name = 'The Deadmines' and kind = 'instance'")).isEqualTo("instance");
    }
    assertThat(report.get("atlas.unknownItem")).isEqualTo(1); // 8492 is not in the item table
  }

  @Test
  void writesStaticTablesMetaAndIndexes(@TempDir Path tmp) throws Exception {
    Builder b = new Builder();
    b.meta.put("build_id", "abc");
    b.meta.put("importer_version", "0.1.0");
    SqliteWriter.write(tmp.resolve("t.sqlite"), b.model());
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      assertThat(scalar(c, "select count(*) from item_class")).isEqualTo(16);
      assertThat(scalar(c, "select name from item_class where id = 2")).isEqualTo("Weapon");
      assertThat(scalar(c, "select name from item_subclass where class_id = 2 and id = 7")).isEqualTo("One-Handed Sword");
      assertThat(scalar(c, "select name from item_subclass where class_id = 4 and id = 6")).isEqualTo("Shield");
      assertThat(scalar(c, "select value from meta where key = 'build_id'")).isEqualTo("abc");
      assertThat(column(c, "select name from sqlite_master where type = 'index' and name like 'idx_%'"))
          .contains("idx_item_npc_drop_npc", "idx_npc_spawn_zone", "idx_quest_giver_entity", "idx_boss_loot_item",
              "idx_item_class", "idx_quest_zone", "idx_object_zone", "idx_item_related_quest_quest");
    }
  }

  @Test
  void replacesAnExistingFile(@TempDir Path tmp) throws Exception {
    Path out = tmp.resolve("t.sqlite");
    Files.writeString(out, "not a database");
    SqliteWriter.write(out, minimalModelWithItem(25, "Worn Shortsword"));
    try (Connection c = open(out)) {
      assertThat(scalar(c, "select name from item where id = 25")).isEqualTo("Worn Shortsword");
    }
  }

  // ---------------------------------------------------------------------------------------------

  private static ImportModel minimalModelWithItem(long id, String name) {
    Builder b = new Builder();
    b.item(id, name).set("npcDrops", ids(11520));
    return b.model();
  }

  /** Mutable model under construction; fixture schemas give the real field layout. */
  private static final class Builder {
    final Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    final DropRates rates = new DropRates();
    final Map<Long, Zone> zones = new LinkedHashMap<>();
    final List<AtlasInstance> instances = new ArrayList<>();
    final Map<String, String> meta = new LinkedHashMap<>();

    Row item(long id, String name) {
      return row(EntityKind.ITEM, id, name);
    }

    Row row(EntityKind kind, long id, String name) {
      EntityTable t = tables.computeIfAbsent(kind, k -> new EntityTable(k, MetaLoader.load(
          Fixtures.questieDbRoot().resolve("src/meta/" + k.metaEntity().toLowerCase(Locale.ROOT) + "Meta.lua"))));
      t.create(id);
      Row r = new Row(t, id);
      return name == null ? r : r.set("name", new LuaString(name));
    }

    ImportModel model() {
      return new ImportModel(tables, rates, zones, instances, meta);
    }
  }

  private record Row(EntityTable table, long id) {
    Row set(String field, LuaValue v) {
      table.set(id, field, v);
      return this;
    }
  }

  private static AtlasInstance instance(String key, String name, Long mapId, AtlasBoss... bosses) {
    return new AtlasInstance(key, name, mapId, 1L, "DUNGEON", 10, 20, List.of(bosses));
  }

  private static AtlasBoss boss(String name, Long npcId, boolean extraList, int sortOrder, AtlasLoot... loot) {
    return new AtlasBoss(name, npcId, null, 20, null, extraList, sortOrder, List.of(loot));
  }

  private static AtlasLoot loot(int slot, long itemId) {
    return new AtlasLoot("NORMAL", slot, itemId);
  }

  private static LuaTable ids(long... ids) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < ids.length; i++) {
      t.put(LuaNumber.of(i + 1), LuaNumber.of(ids[i]));
    }
    return t;
  }

  private static LuaTable table(LuaValue... values) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < values.length; i++) {
      t.put(LuaNumber.of(i + 1), values[i]);
    }
    return t;
  }

  private static LuaTable coords(double x, double y) {
    return table(new LuaNumber(x), new LuaNumber(y));
  }

  static Connection open(Path file) throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + file);
  }

  static Object scalar(Connection c, String sql) throws SQLException {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      assertThat(rs.next()).as("a row for: " + sql).isTrue();
      return rs.getObject(1);
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
