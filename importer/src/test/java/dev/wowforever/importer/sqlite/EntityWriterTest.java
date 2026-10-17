package dev.wowforever.importer.sqlite;

import static dev.wowforever.importer.sqlite.QuestFixture.column;
import static dev.wowforever.importer.sqlite.QuestFixture.corrected;
import static dev.wowforever.importer.sqlite.QuestFixture.table;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The schema v3 quest columns and the NPC waypoints from the fixture entities after every Forever correction layer. */
class EntityWriterTest {
  @TempDir Path tmp;
  private final ImportReport report = new ImportReport();

  @Test
  void questFlagsAndRequirements() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    EntityTable quests = tables.get(EntityKind.QUEST);
    // bit 1 decides; a malformed skill or reputation pair is counted and leaves the columns empty
    quests.set(176, "specialFlags", LuaNumber.of(3));
    quests.set(176, "requiredSkill", table(LuaNumber.of(202)));
    quests.set(176, "requiredMinRep", new LuaString("Exalted"));
    try (Connection c = QuestFixture.write(tmp, tables, report)) {
      // 3644 Membership Card Renewal: specialFlags 1, requiredSkill {202, 200} (Engineering), zoneOrSort -201
      assertThat(quest(c, 3644)).isEqualTo("1:202:200:null:null:-201:Engineering");
      // 960 Onu is meditating (created by its correction): specialFlags = specialFlags.REPEATABLE, a zone id
      assertThat(quest(c, 960)).isEqualTo("1:null:null:null:null:148:null");
      // 452 Pyrewood Ambush: specialFlags 2 is not repeatable
      assertThat(quest(c, 452)).isEqualTo("0:null:null:null:null:130:null");
      // 8121 Take Four Bases: the correction's requiredMinRep {510, 3000}; 8485: the raw {576, 42000}
      assertThat(quest(c, 8121)).isEqualTo("0:null:null:510:3000:3358:null");
      assertThat(quest(c, 8485)).isEqualTo("0:null:null:576:42000:1769:null");
      // 5691 (foreverBaseQuest): zoneOrSort = sortKeys.PRIEST
      assertThat(quest(c, 5691)).endsWith(":-262:Priest");
      assertThat(quest(c, 176)).isEqualTo("1:null:null:null:null:12:null");
      assertThat(column(c, "select count(*) from quest where sort_label is not null and zone_or_sort >= 0"))
          .containsExactly("0");
    }
    assertThat(report.get("badValue.quest.requiredSkill")).isEqualTo(1);
    assertThat(report.get("badValue.quest.requiredMinRep")).isEqualTo(1);
  }

  @Test
  void waypointsAreWrittenInOrder() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    EntityTable npcs = tables.get(EntityKind.NPC);
    // Hogger: two routes in zone 12 and one in zone 40
    npcs.set(448, "waypoints", zones(12, table(route(1, 2, 3, 4), route(5, 6)), 40, table(route(7, 8))));
    // Gobbler: one good point; a string point, a point without y, a non-table route and a non-table zone are bad
    LuaTable broken = table(table(xy(1, 1), new LuaString("x"), table(LuaNumber.of(2))), LuaNumber.of(7));
    npcs.set(1259, "waypoints", zones(11, broken, 13, LuaNumber.of(5)));
    try (Connection c = QuestFixture.write(tmp, tables, report)) {
      // 550 Defias Messenger: one Westfall route of 171 points, verbatim from Questie, in Questie's order
      assertThat(column(c, "select zone_id || ':' || route || ':' || count(*) || ':' || min(ordinal) || ':'"
          + " || max(ordinal) from npc_waypoint where npc_id = 550 group by zone_id, route"))
          .containsExactly("40:1:171:1:171");
      assertThat(column(c, "select x || ',' || y from npc_waypoint where npc_id = 550 and ordinal in (1, 2, 171)"
          + " order by ordinal")).containsExactly("45.1,69.33", "45.59,68.72", "44.48,69.61");
      assertThat(column(c, "select zone_id || ':' || route || ':' || ordinal || ':' || x || ',' || y"
          + " from npc_waypoint where npc_id = 448 order by zone_id, route, ordinal"))
          .containsExactly("12:1:1:1.0,2.0", "12:1:2:3.0,4.0", "12:2:1:5.0,6.0", "40:1:1:7.0,8.0");
      assertThat(column(c, "select zone_id || ':' || route || ':' || ordinal || ':' || x || ',' || y"
          + " from npc_waypoint where npc_id = 1259")).containsExactly("11:1:1:1.0,1.0");
      assertThat(column(c, "select distinct npc_id from npc_waypoint order by npc_id"))
          .containsExactly("448", "550", "1259");
    }
    assertThat(report.get("npc.waypoints.npcs")).isEqualTo(3);
    assertThat(report.get("npc.waypoints.points")).isEqualTo(171 + 4 + 1);
    assertThat(report.get("badValue.npc.waypoints")).isEqualTo(4);
  }

  /** {@code {[zoneA] = a, [zoneB] = b}}. */
  private static LuaTable zones(long zoneA, LuaValue a, long zoneB, LuaValue b) {
    LuaTable t = new LuaTable();
    t.put(LuaNumber.of(zoneA), a);
    t.put(LuaNumber.of(zoneB), b);
    return t;
  }

  /** A route of {@code {x, y}} points from the coordinate pairs. */
  private static LuaTable route(double... coords) {
    LuaValue[] points = new LuaValue[coords.length / 2];
    for (int i = 0; i < points.length; i++) {
      points[i] = xy(coords[2 * i], coords[2 * i + 1]);
    }
    return table(points);
  }

  private static LuaTable xy(double x, double y) {
    return table(new LuaNumber(x), new LuaNumber(y));
  }

  /** {@code repeatable:skill:skillValue:repFaction:repValue:zoneOrSort:sortLabel}. */
  private static String quest(Connection c, long id) throws SQLException {
    return column(c, "select repeatable || ':' || ifnull(required_skill_id, 'null') || ':'"
        + " || ifnull(required_skill_value, 'null') || ':' || ifnull(required_min_rep_faction, 'null') || ':'"
        + " || ifnull(required_min_rep_value, 'null') || ':' || ifnull(zone_or_sort, 'null') || ':'"
        + " || ifnull(sort_label, 'null') from quest where id = " + id).getFirst();
  }
}
