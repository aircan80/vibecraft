package dev.wowforever.importer.sqlite;

import static dev.wowforever.importer.sqlite.QuestFixture.column;
import static dev.wowforever.importer.sqlite.QuestFixture.corrected;
import static dev.wowforever.importer.sqlite.QuestFixture.scalar;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * quest_relation from the fixture quests after every Forever correction layer. The rows 247, 595, 597, 599, 4964,
 * 5142, 5149 and 5601 and the classicQuestFixes entries of 4964, 5142, 5149 and 5601 are copied from QuestieDB
 * 49e7dbca.
 */
class QuestRelationWriterTest {
  @TempDir Path tmp;
  private final ImportReport report = new ImportReport();

  @Test
  void bothPrerequisiteKindsAreKept() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    // the same prerequisite as a group member and as a single prerequisite stays two rows
    tables.get(EntityKind.QUEST).set(176, "preQuestGroup", table(LuaNumber.of(2), LuaNumber.of(136)));
    tables.get(EntityKind.QUEST).set(176, "preQuestSingle", table(LuaNumber.of(2)));
    try (Connection c = write(tables)) {
      // 247 The Hunt Completed: preQuestGroup {2, 23, 24}
      assertThat(relations(c, 247)).containsExactly("pre_group:2", "pre_group:23", "pre_group:24");
      // 597 The Bloodsail Buccaneers: preQuestSingle {595}, nextQuestInChain 599
      assertThat(relations(c, 597)).containsExactly("next:599", "pre_single:595");
      assertThat(relations(c, 176)).containsExactly("pre_group:136", "pre_group:2", "pre_single:2");
      // 3644: the correction's preQuestSingle {3639, 3641, 3643} replaced the raw {3639}
      assertThat(relations(c, 3644)).containsExactly("pre_single:3639", "pre_single:3641", "pre_single:3643");
    }
  }

  @Test
  void missingRelationTargetsAreKeptAndCounted() throws Exception {
    try (Connection c = write(corrected())) {
      // the Bloodsail chain 595 -> 597 -> 599 -> 604; 604 is not in the fixture
      assertThat(relations(c, 595)).containsExactly("next:597");
      assertThat(relations(c, 599)).containsExactly("next:604", "pre_single:597");
      assertThat(scalar(c, "select count(*) from quest where id = 604")).isZero();
      // 960 (created by its correction): nextQuestInChain = 0 is "none", preQuestSingle {944} is missing
      assertThat(relations(c, 960)).containsExactly("pre_single:944");
      assertThat(scalar(c, "select count(*) from quest_relation where related_id not in (select id from quest)"))
          .isEqualTo(report.get("quest.relation.missing"));
    }
    // 2: 6383, 23, 24; 136: 138; 247: 23, 24; 532: 529, 539; 599: 604; 960: 944; 1136: 1131; 3644: 3639, 3641, 3643;
    // 4964: 4976, 4962, 4963
    assertThat(report.get("quest.relation.missing")).isEqualTo(17);
  }

  @Test
  void duplicatesCollapse() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    tables.get(EntityKind.QUEST).set(176, "preQuestSingle",
        table(LuaNumber.of(2), LuaNumber.of(999), LuaNumber.of(2), LuaNumber.of(999)));
    tables.get(EntityKind.QUEST).set(176, "exclusiveTo", table(LuaNumber.of(136), LuaNumber.of(136)));
    try (Connection c = write(tables)) {
      assertThat(relations(c, 176)).containsExactly("exclusive:136", "pre_single:2", "pre_single:999");
    }
    // the fixture's 17, plus 999 once
    assertThat(report.get("quest.relation.missing")).isEqualTo(18);
  }

  @Test
  void breadcrumbsBothWays() throws Exception {
    try (Connection c = write(corrected())) {
      // 5142 Little Pamela and 5601 Sister Pamela: exclusive to each other, both lead to 5149 Pamela's Doll
      assertThat(relations(c, 5142)).containsExactly("breadcrumb_for:5149", "exclusive:5601", "next:5149");
      assertThat(relations(c, 5601)).containsExactly("breadcrumb_for:5149", "exclusive:5142", "next:5149");
      // 5149: the correction sets breadcrumbs {5142, 5601} and empties the raw preQuestSingle {5142, 5601}
      assertThat(relations(c, 5149)).containsExactly("breadcrumb:5142", "breadcrumb:5601");
    }
  }

  @Test
  void negativePrerequisiteIdsAreStrictPrerequisitesOfTheAbsoluteId() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    // a negative id and its absolute id in one list are one prerequisite
    tables.get(EntityKind.QUEST).set(176, "preQuestSingle", table(LuaNumber.of(-136), LuaNumber.of(136)));
    try (Connection c = write(tables)) {
      // 4964 The Completed Orb of Dar'Orahil: the correction's preQuestGroup {4976, -4962}, exclusiveTo {4963}
      assertThat(relations(c, 4964)).containsExactly("exclusive:4963", "pre_group:4962", "pre_group:4976");
      assertThat(relations(c, 176)).containsExactly("pre_single:136");
      assertThat(scalar(c, "select count(*) from quest_relation where related_id < 0")).isZero();
      // 4962 is absent from the fixture: one missing row, counted once
      assertThat(scalar(c, "select count(*) from quest_relation where related_id = 4962")).isEqualTo(1);
    }
    // the fixture's 17 (4962 among them); 136 is present
    assertThat(report.get("quest.relation.missing")).isEqualTo(17);
  }

  @Test
  void malformedRelationsAreCountedNotWritten() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    tables.get(EntityKind.QUEST).set(176, "nextQuestInChain", new LuaString("soon"));
    tables.get(EntityKind.QUEST).set(176, "parentQuest", LuaNumber.of(136));
    tables.get(EntityKind.QUEST).set(176, "childQuests", table(new LuaString("x"), LuaNumber.of(2)));
    try (Connection c = write(tables)) {
      assertThat(relations(c, 176)).containsExactly("child:2", "parent:136");
    }
    assertThat(report.get("badValue.quest.nextQuestInChain")).isEqualTo(1);
    assertThat(report.get("badValue.quest.childQuests")).isEqualTo(1);
  }

  @Test
  void aNegativeScalarRelationIdIsCountedNotWritten() throws Exception {
    Map<EntityKind, EntityTable> tables = corrected();
    tables.get(EntityKind.QUEST).set(176, "nextQuestInChain", LuaNumber.of(-136));
    tables.get(EntityKind.QUEST).set(176, "parentQuest", LuaNumber.of(-2));
    tables.get(EntityKind.QUEST).set(176, "breadcrumbForQuestId", LuaNumber.of(136));
    try (Connection c = write(tables)) {
      assertThat(relations(c, 176)).containsExactly("breadcrumb_for:136");
      assertThat(scalar(c, "select count(*) from quest_relation where related_id <= 0")).isZero();
    }
    assertThat(report.get("badValue.quest.nextQuestInChain")).isEqualTo(1);
    assertThat(report.get("badValue.quest.parentQuest")).isEqualTo(1);
    assertThat(report.get("badValue.quest.breadcrumbForQuestId")).isZero();
  }

  // ---------------------------------------------------------------------------------------------

  private Connection write(Map<EntityKind, EntityTable> tables) throws Exception {
    return QuestFixture.write(tmp, tables, report);
  }

  /** {@code kind:related} sorted by kind, then related id as text. */
  private static List<String> relations(Connection c, long quest) throws SQLException {
    return column(c, "select kind || ':' || related_id from quest_relation where quest_id = " + quest
        + " order by kind, cast(related_id as text)");
  }
}
