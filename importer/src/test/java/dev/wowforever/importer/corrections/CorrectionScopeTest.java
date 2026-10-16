package dev.wowforever.importer.corrections;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.corrections.CorrectionKey.Op;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CorrectionScopeTest {

  private static LuaTable constants() {
    return EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector());
  }

  private static Map<EntityKind, EntitySchema> schemas() {
    Map<EntityKind, EntitySchema> m = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      m.put(k, MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/" + k.metaEntity().toLowerCase() + "Meta.lua")));
    }
    return m;
  }

  private static LuaValue eval(String expr, Scope scope) {
    LuaChunk chunk = LuaChunk.parse("return " + expr, "test.lua");
    return new ChunkEvaluator("test.lua", (n, a, l) -> java.util.Optional.empty(), new ProblemCollector())
        .run(chunk.statements(), scope).orElseThrow();
  }

  @Test
  void bindsKeysAndOperations() {
    Scope s = CorrectionScope.build(constants(), schemas()).scope();
    assertThat(eval("QuestieDB.itemKeys.npcDrops", s)).isEqualTo(LuaNumber.of(2));
    assertThat(CorrectionKey.parse(eval("QuestieDB.itemKeys.npcDrops_add", s))).contains(new CorrectionKey(Op.ADD, 2));
    assertThat(CorrectionKey.parse(eval("QuestieDB.questKeys.finishedBy_remove", s))).contains(new CorrectionKey(Op.REMOVE, 3));
  }

  @Test
  void bindsAliasesUsedByCorrectionFiles() {
    Scope s = CorrectionScope.build(constants(), schemas()).scope();
    assertThat(eval("ZoneDB.zoneIDs.ELWYNN_FOREST", s)).isEqualTo(LuaNumber.of(12));
    assertThat(eval("QuestieDB.raceKeys.ALL_ALLIANCE", s)).isInstanceOf(LuaNumber.class);
    assertThat(eval("QuestieProfessions.professionKeys.COOKING", s)).isEqualTo(LuaNumber.of(185));
    assertThat(eval("QuestieDB.itemClasses.QUEST", s)).isEqualTo(LuaNumber.of(12));
  }

  @Test
  void loaderCallsReturnScopeTables() {
    CorrectionScope.Bound b = CorrectionScope.build(constants(), schemas());
    LuaValue v = b.calls().call("QuestieLoader:ImportModule", List.of(new LuaString("ZoneDB")), 1).get();
    assertThat(((LuaTable) v).get("zoneIDs")).isInstanceOf(LuaTable.class);
  }

  @Test
  void parsesCorrectionKeys() {
    assertThat(CorrectionKey.parse(LuaNumber.of(5))).contains(new CorrectionKey(Op.REPLACE, 5));
    assertThat(CorrectionKey.parse(new LuaString("op:add:7"))).contains(new CorrectionKey(Op.ADD, 7));
    assertThat(CorrectionKey.parse(new LuaString("name"))).isEmpty();
  }

  @Test
  void foreverCorrectionFileHeadersEvaluateWithoutProblems() throws IOException {
    CorrectionScope.Bound b = CorrectionScope.build(constants(), schemas());
    Path dir = Fixtures.questieDbRoot().resolve("src/corrections/Forever");
    List<Path> files;
    try (Stream<Path> walk = Files.walk(dir)) {
      files = walk.filter(p -> p.toString().endsWith(".lua")).toList();
    }
    assertThat(files).hasSize(18);
    for (Path f : files) {
      ProblemCollector problems = new ProblemCollector();
      new ChunkEvaluator(f.getFileName().toString(), b.calls(), problems).run(LuaChunk.parse(f).statements(), b.scope().child());
      assertThat(problems.problems()).as(f.toString()).isEmpty();
    }
  }
}
