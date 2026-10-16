package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.lua.ast.Stmt;
import org.junit.jupiter.api.Test;

class LuaChunkTest {
  static final String SRC = """
      local QuestieDB = QuestieLoader:ImportModule("QuestieDB");
      QuestieDB.itemData = [[return {
      [25] = {'Worn Shortsword',nil,nil},
      }]]
      function QuestieItemFixes:Load()
          local itemKeys = QuestieDB.itemKeys
          return { [730] = { [itemKeys.npcDrops] = {1259, 1418} } }
      end
      if UnitFactionGroup("player") == "Horde" then
          HORDE_DIFF = data:AddDifficulty(FACTION_HORDE, "horde", nil, 1)
      else
          ALLIANCE_DIFF = data:AddDifficulty(FACTION_ALLIANCE, "alliance", nil, 1)
      end
      data["Ragefire"] = { MapID = 2437 }
      """;

  @Test
  void findsLongStringAssignment() {
    assertThat(LuaChunk.parse(SRC, "f.lua").longStringAssignedTo("QuestieDB.itemData"))
        .hasValueSatisfying(s -> assertThat(s).startsWith("return {"));
  }

  @Test
  void findsFunction() {
    assertThat(LuaChunk.parse(SRC, "f.lua").function("QuestieItemFixes:Load"))
        .hasValueSatisfying(f -> assertThat(f.body()).hasSize(2));
  }

  @Test
  void skipsIfBlocks() {
    assertThat(LuaChunk.parse(SRC, "f.lua").statements()).filteredOn(s -> s instanceof Stmt.Skipped).hasSize(1);
  }

  @Test
  void listsIndexedAssignments() {
    assertThat(LuaChunk.parse(SRC, "f.lua").assignedValue("data[\"Ragefire\"]")).isPresent();
  }

  @Test
  void skipsNestedBlocksAndFunctionExpressions() {
    LuaChunk c = LuaChunk.parse("""
        for i = 1, 3 do
          if i then local f = function() return 1 end end
          repeat i = i + 1 until i > 5
        end
        while x do y() end
        repeat z() until function() end
        t.cb = function(a) if a then return 1 end end
        local after = 7
        """, "n.lua");
    assertThat(c.statements()).hasSize(5);
    assertThat(c.statements()).filteredOn(s -> s instanceof Stmt.Skipped).hasSize(3);
    assertThat(c.localValue("after")).isPresent();
    assertThat(c.assignedValue("t.cb")).isPresent();
  }

  @Test
  void splitsMultipleAssignmentAndRendersQualifiedNames() {
    LuaChunk c = LuaChunk.parse("a, b = 1\nfunction A.B.C(x, ...) end\nlocal function f() end\nreturn", "m.lua");
    assertThat(c.assignments()).hasSize(2);
    assertThat(c.function("A.B.C")).hasValueSatisfying(f -> assertThat(f.params()).containsExactly("x", "..."));
    assertThat(c.function("f")).isPresent();
    assertThat(c.statements().get(c.statements().size() - 1)).isInstanceOf(Stmt.Return.class);
  }
}
