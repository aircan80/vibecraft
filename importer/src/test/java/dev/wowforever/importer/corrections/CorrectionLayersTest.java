package dev.wowforever.importer.corrections;

import static dev.wowforever.importer.corrections.CorrectionTestSupport.arr;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.loadFixtureTables;
import static dev.wowforever.importer.entity.EntityKind.ITEM;
import static dev.wowforever.importer.entity.EntityKind.NPC;
import static dev.wowforever.importer.entity.EntityKind.OBJECT;
import static dev.wowforever.importer.entity.EntityKind.QUEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.report.Skip;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorrectionLayersTest {
  private final ImportReport report = new ImportReport();
  private final CorrectionScope.Bound scope = CorrectionTestSupport.bound();
  private final ProblemCollector problems = new ProblemCollector();

  @Test
  void appliesAllFixtureLayersInOrder() {
    Map<EntityKind, EntityTable> tables = loadFixtureTables();
    CorrectionApplier a = new CorrectionApplier(report);
    for (CorrectionLayer l : CorrectionLayers.forever()) a.applyLayer(l, Fixtures.questieDbRoot(), tables, scope, problems);
    assertThat(tables.get(ITEM).get(159, "npcDrops")).isEqualTo(LuaNil.NIL);          // foreverItemFixes deletes
    assertThat(tables.get(ITEM).contains(190179)).isTrue();                            // trace-created entity
    assertThat(LuaValues.asLong(tables.get(ITEM).get(1307, "startQuest"))).hasValue(123);
    assertThat(tables.get(QUEST).get(490, "name")).isEqualTo(new LuaString("Bounty: Gnarlpine Furbolg"));
    assertThat(report.skipsOnUiFields()).isZero();
  }

  @Test
  void fixtureRunComposesEveryLayerAndReportsOnlyTheUnresolvableQuestFields() {
    Map<EntityKind, EntityTable> tables = loadFixtureTables();
    CorrectionApplier a = new CorrectionApplier(report);
    for (CorrectionLayer l : CorrectionLayers.forever()) {
      a.applyLayer(l, Fixtures.questieDbRoot(), tables, scope, problems);
    }

    // generated base adds relatedQuests to 159, authored fixes later delete its drops: both survive
    assertThat(arr(tables.get(ITEM).get(159, "relatedQuests"))).containsExactly(99050L);
    assertThat(tables.get(ITEM).get(159, "objectDrops")).isEqualTo(LuaNil.NIL);
    // legacy replacement of a raw list
    assertThat(arr(tables.get(ITEM).get(730, "npcDrops"))).hasSize(36).startsWith(1259L, 1418L);
    // trace ops into fresh entities
    assertThat(arr(tables.get(ITEM).get(203755, "objectDrops"))).containsExactly(386759L);
    assertThat(arr(tables.get(OBJECT).get(410369, "questEnds"))).containsExactly(78261L, 78307L);
    LuaTable startedBy = (LuaTable) tables.get(QUEST).get(65593, "startedBy");
    assertThat(arr(startedBy.get(1))).containsExactly(5693L);
    // authored NPC fix: ops and an ordinary {} deletion of waypoints
    assertThat(arr(tables.get(NPC).get(2155, "questStarts"))).containsExactly(490L);
    assertThat(tables.get(NPC).get(2155, "waypoints")).isEqualTo(LuaNil.NIL);
    // reputation fix deletes quest 136's rewards and replaces quest 2's
    assertThat(tables.get(QUEST).get(136, "reputationReward")).isEqualTo(LuaNil.NIL);
    assertThat(LuaValues.deepEquals(tables.get(QUEST).get(2, "reputationReward"), CorrectionTestSupport.lua("{{81,100}}"))).isTrue();

    // classicQuestFixes 9422 references Questie.ICON_TYPE_* and l10n(...), which are not modelled
    assertThat(report.skips()).extracting(Skip::layer, Skip::entityId, Skip::field).containsExactlyInAnyOrder(
        org.assertj.core.groups.Tuple.tuple("legacy/classicQuestFixes", 9422L, "objectives"),
        org.assertj.core.groups.Tuple.tuple("legacy/classicQuestFixes", 9422L, "extraObjectives"));
    for (CorrectionLayer l : CorrectionLayers.forever()) {
      assertThat(report.get("rows." + l.name())).as(l.name()).isPositive();
    }
    assertThat(report.get("layer.missing")).isZero();
    // the evaluator's problems for those two values are surfaced per layer, and only where they occur
    assertThat(report.get("problems.legacy/classicQuestFixes")).isEqualTo(3);
    assertThat(report.render()).containsOnlyOnce("problems.");
  }

  @Test
  void foreverListsTheEighteenLayersInQuestieDbOrder() {
    List<CorrectionLayer> layers = CorrectionLayers.forever();
    assertThat(layers).extracting(CorrectionLayer::name).containsExactly(
        "legacy/classicItemFixes", "legacy/classicNPCFixes", "legacy/classicObjectFixes",
        "legacy/classicQuestFixes", "legacy/classicQuestReputationFixes", "legacy/itemStartFixes",
        "generated/foreverBaseItem", "generated/foreverBaseNpc", "generated/foreverBaseObject", "generated/foreverBaseQuest",
        "traces/foreverItemTraces", "traces/foreverNpcTraces", "traces/foreverObjectTraces", "traces/foreverQuestTraces",
        "forever/foreverItemFixes", "forever/foreverNPCFixes", "forever/foreverObjectFixes", "forever/foreverQuestFixes");
    assertThat(layers).extracting(CorrectionLayer::kind).containsExactly(
        ITEM, NPC, OBJECT, QUEST, QUEST, ITEM, ITEM, NPC, OBJECT, QUEST, ITEM, NPC, OBJECT, QUEST, ITEM, NPC, OBJECT, QUEST);
    assertThat(layers.get(5)).isEqualTo(new CorrectionLayer("legacy/itemStartFixes",
        "src/corrections/Forever/legacy/itemStartFixes.lua", "QuestieItemStartFixes:LoadAutomaticQuestStarts", ITEM));
    assertThat(layers.get(15).function()).isEqualTo("ForeverNpcFixes:Load");
    assertThat(layers.get(14).relativePath()).isEqualTo("src/corrections/Forever/foreverItemFixes.lua");
  }

  @Test
  void everyLayerFunctionExistsInItsFixtureFile() {
    for (CorrectionLayer l : CorrectionLayers.forever()) {
      Path file = Fixtures.questieDbRoot().resolve(l.relativePath());
      assertThat(file).as(l.name()).isRegularFile();
      assertThat(LuaChunk.parse(file).function(l.function())).as(l.name()).isPresent();
    }
  }

  @Test
  void missingFileOrFunctionIsReportedNotThrown(@TempDir Path root) throws Exception {
    Path file = root.resolve("src/corrections/Forever/x.lua");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "function Other:Load() return {} end\n");
    Map<EntityKind, EntityTable> tables = loadFixtureTables();
    CorrectionApplier a = new CorrectionApplier(report);

    a.applyLayer(new CorrectionLayer("t/absent", "src/corrections/Forever/none.lua", "X:Load", ITEM), root, tables, scope, problems);
    a.applyLayer(new CorrectionLayer("t/nofn", "src/corrections/Forever/x.lua", "X:Load", ITEM), root, tables, scope, problems);

    assertThat(report.get("layer.missing")).isEqualTo(2);
    assertThat(report.skips()).extracting(Skip::layer, Skip::field).containsExactly(
        org.assertj.core.groups.Tuple.tuple("t/absent", "<layer>"), org.assertj.core.groups.Tuple.tuple("t/nofn", "<layer>"));
    assertThat(report.skips()).allMatch(s -> s.reason().startsWith("file or function missing: "));
  }

  @Test
  void functionNotReturningATableIsReported(@TempDir Path root) throws Exception {
    Path file = root.resolve("x.lua");
    Files.writeString(file, "function X:Load() return 5 end\n");
    new CorrectionApplier(report).applyLayer(new CorrectionLayer("t/bad", "x.lua", "X:Load", ITEM), root, loadFixtureTables(), scope, problems);
    assertThat(report.skips()).extracting(Skip::reason).containsExactly("function did not return a table: 5");
    assertThat(report.get("layer.failed")).isEqualTo(1);
  }

  @Test
  void layerLocalsDoNotLeakIntoTheSharedScope(@TempDir Path root) throws Exception {
    Path file = root.resolve("x.lua");
    Files.writeString(file, "local leaked = 1\nfunction X:Load() return {} end\n");
    new CorrectionApplier(report).applyLayer(new CorrectionLayer("t/x", "x.lua", "X:Load", ITEM), root, loadFixtureTables(), scope, problems);
    assertThat(scope.scope().lookup("leaked")).isEmpty();
  }
}
