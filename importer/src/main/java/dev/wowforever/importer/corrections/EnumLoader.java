package dev.wowforever.importer.corrections;

import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Stmt;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Loads QuestieDB's correction enum files into one shared constants table. */
public final class EnumLoader {
  private static final List<String> FILES = List.of(
      "constants", "zones", "factions", "items", "quests", "professions", "icons", "phases", "waypoints", "expansions");
  /** Names the enum files bind from the addon vararg; the loader supplies them instead. */
  private static final Set<String> PREDEFINED = Set.of("_", "LibQuestieDB", "constants");

  private EnumLoader() {}

  public static LuaTable load(Path questieDbRoot, ProblemCollector problems) {
    LuaTable constants = new LuaTable();
    LuaTable lib = new LuaTable();
    lib.put(new dev.wowforever.lua.LuaString("Enum"), constants);
    for (String name : FILES) {
      Path file = questieDbRoot.resolve("src/corrections/enum/" + name + ".lua");
      LuaChunk chunk = LuaChunk.parse(file);
      // Drop the "local _, LibQuestieDB = ..." / "local constants = ..." prologue; both are pre-defined.
      List<Stmt> stmts = chunk.statements().stream()
          .filter(s -> !(s instanceof Stmt.Local l && l.names().stream().anyMatch(PREDEFINED::contains)))
          .toList();
      Scope scope = new Scope();
      scope.define("LibQuestieDB", lib);
      scope.define("constants", constants);
      new ChunkEvaluator(file.getFileName().toString(), (n, a, l) -> java.util.Optional.empty(), problems).run(stmts, scope);
    }
    return constants;
  }
}
