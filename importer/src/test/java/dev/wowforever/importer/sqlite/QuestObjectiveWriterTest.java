package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.corrections.CorrectionApplier;
import dev.wowforever.importer.corrections.CorrectionLayer;
import dev.wowforever.importer.corrections.CorrectionLayers;
import dev.wowforever.importer.corrections.CorrectionScope;
import dev.wowforever.importer.corrections.EnumLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaUnresolved;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.ProblemCollector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * quest_objective, quest_objective_point and quest_source_item from the fixture quests after every Forever
 * correction layer, as the import runs them. The fixture rows (176, 452, 532, 1136, 5561, 8121, 8485, 9422) and
 * their classicQuestFixes entries are copied from QuestieDB 49e7dbca.
 */
class QuestObjectiveWriterTest {
  @TempDir Path tmp;
  private final ImportReport report = new ImportReport();

  @Test
  void creatureAndItemObjectivesInOrder() throws Exception {
    try (Connection c = write(correctedFixture())) {
      // 532 Battle of Hillsbrad: objectives {{{2335},{2387}},{{1761,"Hillsbrad Proclamation destroyed"}},{{3657}}}
      assertThat(objectives(c, 532)).containsExactly(
          "0:creature:2335:null:null", "1:creature:2387:null:null",
          "2:object:1761:Hillsbrad Proclamation destroyed:null", "3:item:3657:null:null");
      // 176 Wanted: "Hogger": objectives {nil,nil,{{1931}}}
      assertThat(objectives(c, 176)).containsExactly("0:item:1931:null:null");
      // 9422: the classicQuestFixes creature objective {{{18199, nil, ICON}}} replaced the raw text
      assertThat(objectives(c, 9422)).startsWith("0:creature:18199:null:null");
    }
    assertThat(report.get("quest.objective.kinds.object")).isEqualTo(1);
  }

  @Test
  void killCreditGroupSharesOneGroup() throws Exception {
    try (Connection c = write(correctedFixture())) {
      // 452 Pyrewood Ambush: {nil, nil, nil, nil, {{{2060, ..., 2068}, 2060}}}
      List<String> pyrewood = objectives(c, 452);
      assertThat(pyrewood).hasSize(9).allMatch(r -> r.startsWith("0:creature:") && r.endsWith(":null:0"));
      assertThat(pyrewood.getFirst()).isEqualTo("0:creature:2060:null:0");
      assertThat(pyrewood).extracting(r -> r.split(":")[2]).doesNotHaveDuplicates()
          .containsExactly("2060", "2061", "2062", "2063", "2064", "2065", "2066", "2067", "2068");
      // 5561 Kodo Roundup: the correction replaces the raw creature objective with a killCredit group, then
      // its extraObjectives spawnlist is a plain zone map, so the explore row keeps the Desolace point
      assertThat(objectives(c, 5561)).containsExactly("0:creature:4700:null:0", "0:creature:4701:null:0",
          "0:creature:4702:null:0", "1:explore:null:Lure the Kodos to Smeed Scrabblescrew.:null");
      assertThat(points(c, 5561)).containsExactly("1:405:60.58:62.0");
    }
  }

  @Test
  void killCreditBaseOutsideItsListComesFirstWithTheGroupText() throws Exception {
    Map<EntityKind, EntityTable> tables = correctedFixture();
    LuaTable group = table(table(LuaNumber.of(11), LuaNumber.of(12)), LuaNumber.of(10), new LuaString("Kill credit"));
    tables.get(EntityKind.QUEST).set(176, "objectives",
        table(LuaNil.NIL, LuaNil.NIL, table(table(LuaNumber.of(1931))), LuaNil.NIL, table(group)));
    try (Connection c = write(tables)) {
      assertThat(objectives(c, 176)).containsExactly("0:item:1931:null:null",
          "1:creature:10:Kill credit:1", "1:creature:11:null:1", "1:creature:12:null:1");
    }
  }

  @Test
  void explorePointsKeepTheirZone() throws Exception {
    try (Connection c = write(correctedFixture())) {
      // 8121 Take Four Bases: triggerEnd {"Hold Four Bases.", {[1638]=…, [45]=…, [1637]=…, [130]=…}}
      assertThat(objectives(c, 8121)).containsExactly("0:explore:null:Hold Four Bases.:null");
      assertThat(points(c, 8121)).containsExactly(
          "0:45:73.72:29.52", "0:130:39.68:17.75", "0:1637:50.1:69.03", "0:1638:40.4:51.57");
      // 1136 Frostmaw: the extra objective has no spawnlist, so it is text only
      assertThat(objectives(c, 1136)).containsExactly("0:item:5811:null:null",
          "1:explore:null:Use a Fresh Carcass at the Flame of Uzel:null");
      assertThat(points(c, 1136)).isEmpty();
      assertThat(objectives(c, 9422)).containsExactly("0:creature:18199:null:null", "1:explore:null:Silithyst:null");
    }
    // 76 (foreverQuestFixes triggerEnd), 8121, 1136, 5561, 9422
    assertThat(report.get("quest.objective.kinds.explore")).isEqualTo(5);
  }

  @Test
  void providedAndRequiredItems() throws Exception {
    try (Connection c = write(correctedFixture())) {
      // 1136 Frostmaw: sourceItemId 5838, requiredSourceItems {5810}
      assertThat(sourceItems(c, 1136)).containsExactly("provided:5838", "required:5810");
      assertThat(sourceItems(c, 2)).containsExactly("provided:16305");
      assertThat(sourceItems(c, 5561)).containsExactly("provided:13892");
      assertThat(sourceItems(c, 176)).isEmpty();
      assertThat(scalar(c, "select count(*) from quest_source_item")).isEqualTo(report.get("quest.sourceItems"));
    }
    assertThat(report.get("quest.sourceItems")).isEqualTo(5); // 2, 1136 (two), 5561, 8485
  }

  @Test
  void reputationObjectiveKeepsFactionAndValue() throws Exception {
    try (Connection c = write(correctedFixture())) {
      // 8485 The Brokering of Peace: objectives {nil,nil,nil,{576,42000}}
      assertThat(objectives(c, 8485)).containsExactly("0:reputation:576:42000:null");
      assertThat(sourceItems(c, 8485)).containsExactly("provided:21155");
    }
    assertThat(report.get("quest.objective.kinds.reputation")).isEqualTo(1);
  }

  @Test
  void spellObjectiveKeepsTheSpellButNotItsItem() throws Exception {
    Map<EntityKind, EntityTable> tables = correctedFixture();
    // foreverQuestFixes 97979 The Goddess Provides: {nil,nil,nil,nil,nil,{{1259799},{20580}}}, plus an item
    tables.get(EntityKind.QUEST).set(176, "objectives", table(LuaNil.NIL, LuaNil.NIL, LuaNil.NIL, LuaNil.NIL,
        LuaNil.NIL, table(table(LuaNumber.of(1259799)), table(LuaNumber.of(20580), new LuaString("Cast"), LuaNumber.of(77)))));
    try (Connection c = write(tables)) {
      assertThat(objectives(c, 176)).containsExactly("0:spell:1259799:null:null", "1:spell:20580:Cast:null");
      assertThat(sourceItems(c, 176)).isEmpty();
      assertThat(scalar(c, "select count(*) from quest_source_item where item_id = 77")).isEqualTo(0L);
    }
    assertThat(report.get("quest.objective.kinds.spell")).isEqualTo(2);
  }

  @Test
  void kindCountersCountRows() throws Exception {
    try (Connection c = write(correctedFixture())) {
      for (String kind : List.of("creature", "object", "item", "reputation", "spell", "explore")) {
        assertThat(report.get("quest.objective.kinds." + kind)).as(kind)
            .isEqualTo(scalar(c, "select count(*) from quest_objective where kind = '" + kind + "'"));
      }
    }
    // 452 (9) + 532 (2) + 5561 (3) + 9422 (1) creature rows
    assertThat(report.get("quest.objective.kinds.creature")).isEqualTo(15);
    assertThat(report.counters()).containsEntry("quest.objective.unknownKind", 0L);
  }

  @Test
  void unknownKindsAndMalformedEntriesAreCountedNotWritten() throws Exception {
    Map<EntityKind, EntityTable> tables = correctedFixture();
    LuaTable objectives = table(table(table(new LuaString("not an id")), table(LuaNumber.of(5))));
    objectives.put(LuaNumber.of(7), table(table(LuaNumber.of(9))));
    objectives.put(new LuaString("x"), table());
    tables.get(EntityKind.QUEST).set(176, "objectives", objectives);
    tables.get(EntityKind.QUEST).set(2, "objectives", table(table(new LuaUnresolved("unknown enum", 3))));
    LuaTable zones = new LuaTable();
    zones.put(LuaNumber.of(12), table(table(new LuaNumber(1.5), new LuaNumber(2.5)), new LuaString("bad")));
    tables.get(EntityKind.QUEST).set(136, "triggerEnd", table(new LuaString("Scout"), zones));
    // an extra objective with neither spawnlist nor text, and one that is not a table
    tables.get(EntityKind.QUEST).set(136, "extraObjectives", table(table(LuaNil.NIL, LuaNumber.of(3)), LuaNumber.of(5)));
    try (Connection c = write(tables)) {
      assertThat(objectives(c, 176)).containsExactly("0:creature:5:null:null");
      assertThat(objectives(c, 2)).isEmpty();
      assertThat(objectives(c, 136)).containsExactly("0:explore:null:Scout:null");
      assertThat(points(c, 136)).containsExactly("0:12:1.5:2.5");
    }
    assertThat(report.get("quest.objective.unknownKind")).isEqualTo(2);
    assertThat(report.get("badValue.quest.objectives")).isEqualTo(1);
    assertThat(report.get("badValue.quest.triggerEnd")).isEqualTo(1);
    assertThat(report.get("badValue.quest.extraObjectives")).isEqualTo(2);
    assertThat(report.get("unresolved.quest.objectives")).as("counted once per field").isEqualTo(1);
  }

  @Test
  void aCorrectionAddingAnItemObjectiveLandsInQuestObjective() throws Exception {
    Map<EntityKind, EntityTable> tables = correctedFixture();
    Path root = tmp.resolve("layer");
    Files.createDirectories(root);
    Files.writeString(root.resolve("fix.lua"), """
        local TestQuestFixes = QuestieLoader:CreateModule("TestQuestFixes")
        local QuestieDB = QuestieLoader:ImportModule("QuestieDB")
        function TestQuestFixes:Load()
            local questKeys = QuestieDB.questKeys
            return {
                [176] = { -- Wanted: "Hogger"
                    [questKeys.objectives] = {nil, nil, {{1931}, {3618, "Gobbler's Head"}}},
                },
            }
        end
        """);
    new CorrectionApplier(report).applyLayer(new CorrectionLayer("test/fix", "fix.lua", "TestQuestFixes:Load",
        EntityKind.QUEST), root, tables, bound(), new ProblemCollector());
    assertThat(report.skips()).isEmpty();
    try (Connection c = write(tables)) {
      assertThat(objectives(c, 176)).containsExactly("0:item:1931:null:null", "1:item:3618:Gobbler's Head:null");
    }
  }

  // ---------------------------------------------------------------------------------------------

  private static Map<EntityKind, EntitySchema> schemas() {
    Map<EntityKind, EntitySchema> m = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      m.put(k, MetaLoader.load(
          Fixtures.questieDbRoot().resolve("src/meta/" + k.metaEntity().toLowerCase(Locale.ROOT) + "Meta.lua")));
    }
    return m;
  }

  private static CorrectionScope.Bound bound() {
    return CorrectionScope.build(EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector()), schemas());
  }

  /** The fixture entity tables after every Forever correction layer, as {@code ImportPipeline} builds them. */
  private Map<EntityKind, EntityTable> correctedFixture() {
    Map<EntityKind, EntitySchema> schemas = schemas();
    Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      tables.put(k, RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/forever" + k.metaEntity()
          + "DB.lua"), k, schemas.get(k), new ProblemCollector()));
    }
    CorrectionScope.Bound bound = CorrectionScope.build(
        EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector()), schemas);
    CorrectionApplier applier = new CorrectionApplier(new ImportReport());
    for (CorrectionLayer layer : CorrectionLayers.forever()) {
      applier.applyLayer(layer, Fixtures.questieDbRoot(), tables, bound, new ProblemCollector());
    }
    return tables;
  }

  private Connection write(Map<EntityKind, EntityTable> tables) throws Exception {
    Path out = tmp.resolve("t.sqlite");
    SqliteWriter.write(out, new ImportModel(tables, new DropRates(), Map.of(), List.of(), Map.of()), report);
    return DriverManager.getConnection("jdbc:sqlite:" + out);
  }

  /** {@code ordinal:kind:entity:text:group} in insertion order. */
  private static List<String> objectives(Connection c, long quest) throws SQLException {
    return column(c, "select ordinal || ':' || kind || ':' || ifnull(entity_id, 'null') || ':' || ifnull(text, 'null')"
        + " || ':' || ifnull(credit_group, 'null') from quest_objective where quest_id = " + quest + " order by rowid");
  }

  private static List<String> points(Connection c, long quest) throws SQLException {
    return column(c, "select ordinal || ':' || zone_id || ':' || x || ':' || y from quest_objective_point"
        + " where quest_id = " + quest + " order by ordinal, zone_id, x");
  }

  private static List<String> sourceItems(Connection c, long quest) throws SQLException {
    return column(c, "select kind || ':' || item_id from quest_source_item where quest_id = " + quest
        + " order by kind, item_id");
  }

  private static List<String> column(Connection c, String sql) throws SQLException {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }

  private static long scalar(Connection c, String sql) throws SQLException {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static LuaTable table(LuaValue... values) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < values.length; i++) {
      if (!(values[i] instanceof LuaNil)) {
        t.put(LuaNumber.of(i + 1), values[i]);
      }
    }
    return t;
  }
}
