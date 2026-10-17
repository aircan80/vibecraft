package dev.wowforever.importer.report;

import static dev.wowforever.importer.entity.EntityKind.ITEM;
import static dev.wowforever.importer.entity.EntityKind.NPC;
import static dev.wowforever.importer.entity.EntityKind.OBJECT;
import static dev.wowforever.importer.entity.EntityKind.QUEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportReportTest {

  @Test
  void countersAccumulateAndDefaultToZero() {
    ImportReport r = new ImportReport();
    r.count("ops", 2);
    r.count("ops", 3);
    assertThat(r.get("ops")).isEqualTo(5);
    assertThat(r.get("never")).isZero();
  }

  @Test
  void rendersSortedCountersGroupedSkipsAndRawSkips() {
    ImportReport r = new ImportReport();
    r.count("zeta", 1);
    r.count("alpha", 7);
    r.skip(new Skip("l1", ITEM, 1, "name", "a"));
    r.skip(new Skip("l2", QUEST, 2, "objectives", "b"));
    r.skip(new Skip("l2", QUEST, 3, "objectives", "c"));

    assertThat(r.render()).isEqualTo("""
        == Counters ==
        alpha = 7
        zeta = 1
        == Skips by layer/field ==
        l2  QUEST.objectives  2
        l1  ITEM.name  1
        == First 50 skips ==
        l1 ITEM 1 name: a
        l2 QUEST 2 objectives: b
        l2 QUEST 3 objectives: c
        """);
  }

  @Test
  void rawSkipListIsCappedAtFifty() {
    ImportReport r = new ImportReport();
    for (int i = 1; i <= 60; i++) {
      r.skip(new Skip("l", NPC, i, "spawns", "r"));
    }
    String rendered = r.render();
    assertThat(rendered).contains("l  NPC.spawns  60").contains("l NPC 50 spawns: r").doesNotContain("l NPC 51 spawns: r");
    assertThat(r.skips()).hasSize(60);
  }

  @Test
  void rendersNotesLastAndOnlyWhenPresent() {
    ImportReport r = new ImportReport();
    r.count("a", 1);
    assertThat(r.render()).doesNotContain("== Notes ==");
    r.note("problems.zones: dungeons.lua:3 something");
    r.note("second");
    assertThat(r.notes()).containsExactly("problems.zones: dungeons.lua:3 something", "second");
    assertThat(r.render()).endsWith("""
        == Notes ==
        problems.zones: dungeons.lua:3 something
        second
        """);
  }

  @Test
  void writesTheRenderedReportAsUtf8(@TempDir Path dir) throws Exception {
    ImportReport r = new ImportReport();
    r.skip(new Skip("l", ITEM, 1, "name", "Avelina's Heart — ü"));
    Path out = dir.resolve("sub/report.txt");
    r.writeTo(out);
    assertThat(Files.readString(out, StandardCharsets.UTF_8)).isEqualTo(r.render());
  }

  @Test
  void countsSkipsOnUiFieldsIncludingDroppedRowsAndLayers() {
    ImportReport r = new ImportReport();
    r.skip(new Skip("l", ITEM, 1, "npcDrops", "x"));
    r.skip(new Skip("l", ITEM, 1, "flags", "x"));
    r.skip(new Skip("l", QUEST, 0, "<id>", "x"));
    r.skip(new Skip("l", OBJECT, 0, "<layer>", "x"));
    r.skip(new Skip("l", NPC, 1, "waypoints", "x"));
    assertThat(r.skipsOnUiFields()).isEqualTo(3);
  }

  @Test
  void uiFieldsFollowTheAcceptanceBar() {
    assertThat(UiFields.isUiField(ITEM, "startQuest")).isTrue();
    assertThat(UiFields.isUiField(ITEM, "teachesSpell")).isFalse();
    assertThat(UiFields.isUiField(NPC, "npcFlags")).isTrue();
    assertThat(UiFields.isUiField(NPC, "waypoints")).isFalse();
    assertThat(UiFields.isUiField(OBJECT, "questEnds")).isTrue();
    assertThat(UiFields.isUiField(OBJECT, "factionID")).isFalse();
    assertThat(UiFields.isUiField(QUEST, "objectivesText")).isTrue();
    assertThat(UiFields.isUiField(QUEST, "reputationReward")).isFalse();
    assertThat(UiFields.isUiField(QUEST, "<id>")).isTrue();
  }

  @Test
  void questFieldsOfThePhase3QuestPageAreUiFields() {
    for (String field : List.of("objectives", "triggerEnd", "sourceItemId", "requiredSourceItems", "extraObjectives",
        "preQuestGroup", "preQuestSingle", "childQuests", "inGroupWith", "exclusiveTo", "nextQuestInChain",
        "parentQuest", "breadcrumbForQuestId", "breadcrumbs", "requiredSkill", "requiredMinRep")) {
      assertThat(UiFields.isUiField(QUEST, field)).as(field).isTrue();
    }
    assertThat(UiFields.isUiField(QUEST, "requiredMaxRep")).as("not stored").isFalse();
  }

  @Test
  void conflictsSectionLists() {
    ImportReport r = new ImportReport();
    for (int i = 1; i <= 60; i++) {
      r.conflict(new Conflict(i, 100, new java.util.LinkedHashMap<>(java.util.Map.of("a", 10.0, "b", 12.0 + i))));
    }
    r.compared("a", "b", true);
    r.compared("a", "b", false);
    String text = r.render();
    int start = text.indexOf("== Conflicts ==");
    assertThat(start).isPositive();
    String[] lines = text.substring(start).split("\n");
    assertThat(lines[1]).startsWith("60 100 ").endsWith("=62.00");
    assertThat(lines[2]).startsWith("59 100 ");
    assertThat(lines[50]).startsWith("11 100 ");
    assertThat(lines[51]).isEqualTo("a vs b: 1 disagreeing of 2 shared");
    assertThat(lines).hasSize(52);
  }
}
