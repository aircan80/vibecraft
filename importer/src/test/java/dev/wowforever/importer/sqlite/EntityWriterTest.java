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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The schema v3 quest columns from the fixture quests after every Forever correction layer. */
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

  /** {@code repeatable:skill:skillValue:repFaction:repValue:zoneOrSort:sortLabel}. */
  private static String quest(Connection c, long id) throws SQLException {
    return column(c, "select repeatable || ':' || ifnull(required_skill_id, 'null') || ':'"
        + " || ifnull(required_skill_value, 'null') || ':' || ifnull(required_min_rep_faction, 'null') || ':'"
        + " || ifnull(required_min_rep_value, 'null') || ':' || ifnull(zone_or_sort, 'null') || ':'"
        + " || ifnull(sort_label, 'null') from quest where id = " + id).getFirst();
  }
}
