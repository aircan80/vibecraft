package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkEvaluatorTest {
  @Test
  void runsFunctionBodyWithLocalAliases() {
    Scope s = new Scope();
    LuaTable keys = new LuaTable();
    keys.put(new LuaString("npcDrops"), LuaNumber.of(2));
    LuaTable db = new LuaTable();
    db.put(new LuaString("itemKeys"), keys);
    s.define("QuestieDB", db);
    LuaChunk c = LuaChunk.parse(LuaChunkTest.SRC, "f.lua");
    LuaValue r = new ChunkEvaluator("f.lua", CallHandler.NONE, new ProblemCollector())
        .runFunction(c.function("QuestieItemFixes:Load").get(), s);
    assertThat(((LuaTable) ((LuaTable) ((LuaTable) r).get(730)).get(2)).array())
        .containsExactly(LuaNumber.of(1259), LuaNumber.of(1418));
  }

  @Test
  void topLevelAssignmentsPopulateScopeTables() {
    Scope s = new Scope();
    s.define("constants", new LuaTable());
    new ChunkEvaluator("e.lua", CallHandler.NONE, new ProblemCollector())
        .run(LuaChunk.parse("constants.zoneIDs = { DUSKWOOD = 10 }\nlocal z = constants.zoneIDs", "e.lua").statements(), s);
    assertThat(((LuaTable) s.lookup("z").get()).get("DUSKWOOD")).isEqualTo(LuaNumber.of(10));
  }

  @Test
  void returnOfNestedChunkFromLongString() {
    LuaChunk outer = LuaChunk.parse(LuaChunkTest.SRC, "f.lua");
    LuaChunk inner = LuaChunk.parse(outer.longStringAssignedTo("QuestieDB.itemData").get(), "f.lua#itemData");
    LuaValue v = new ChunkEvaluator("f.lua#itemData", CallHandler.NONE, new ProblemCollector())
        .run(inner.statements(), new Scope()).get();
    assertThat(((LuaTable) ((LuaTable) v).get(25)).get(1)).isEqualTo(new LuaString("Worn Shortsword"));
  }

  @Test
  void reportsAssignmentIntoNonTableAndKeepsGoing() {
    ProblemCollector problems = new ProblemCollector();
    Scope s = new Scope();
    new ChunkEvaluator("p.lua", CallHandler.NONE, problems)
        .run(LuaChunk.parse("local n = nil\nn.x = 1\nlocal a, b = 5\nc = a", "p.lua").statements(), s);
    assertThat(problems.size()).isGreaterThan(0);
    assertThat(s.lookup("b")).hasValue(LuaNil.NIL);
    assertThat(s.lookup("c")).hasValue(LuaNumber.of(5));
  }

  @Test
  void bareReturnYieldsNil() {
    assertThat(new ChunkEvaluator("r.lua", CallHandler.NONE, new ProblemCollector())
            .run(LuaChunk.parse("return", "r.lua").statements(), new Scope()))
        .hasValue(LuaNil.NIL);
  }
}
