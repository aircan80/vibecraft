package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the curated fixture trees that later importer and app tests build on. */
class FixtureTreeTest {

  private static List<Path> luaFiles(Path root) throws IOException {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(p -> p.toString().endsWith(".lua")).sorted().toList();
    }
  }

  @Test
  void everyFixtureFileAndEmbeddedChunkParses() throws IOException {
    List<Path> files = Stream.concat(luaFiles(Fixtures.questieDbRoot()).stream(), luaFiles(Fixtures.atlasLootRoot()).stream()).toList();
    assertThat(files).hasSizeGreaterThan(40);
    int embedded = 0;
    for (Path file : files) {
      LuaChunk chunk = LuaChunk.parse(file);
      for (Stmt.Assign a : chunk.assignments()) {
        if (a.value() instanceof Expr.Literal l && l.value() instanceof LuaString s && s.value().startsWith("return")) {
          assertThat(LuaChunk.parse(s.value(), file.getFileName() + "#embedded").statements()).isNotEmpty();
          embedded++;
        }
      }
    }
    // four entity DBs, two drop tables, two sub-zone tables, one area-to-UiMap override
    assertThat(embedded).isEqualTo(9);
  }

  @Test
  void correctionFilesKeepTheirFunctions() {
    Path forever = Fixtures.questieDbRoot().resolve("src/corrections/Forever");
    assertThat(LuaChunk.parse(forever.resolve("legacy/classicItemFixes.lua")).function("QuestieItemFixes:LoadFactionFixes")).isPresent();
    assertThat(LuaChunk.parse(forever.resolve("legacy/itemStartFixes.lua")).function("QuestieItemStartFixes:LoadAutomaticQuestStarts")).isPresent();
    assertThat(LuaChunk.parse(forever.resolve("generated/foreverBaseItem.lua")).function("ForeverBaseItem:Load")).isPresent();
    assertThat(LuaChunk.parse(forever.resolve("traces/foreverItemTraces.lua")).function("ForeverItemTraces:Load")).isPresent();
    LuaChunk questFixes = LuaChunk.parse(forever.resolve("foreverQuestFixes.lua"));
    assertThat(questFixes.function("ForeverQuestFixes:Load")).isPresent();
    assertThat(questFixes.function("ForeverQuestFixes:LoadDynamic")).isPresent();
  }

  @Test
  void unknownNpcReferenceStaysUnknown() throws IOException {
    String npcs = Files.readString(Fixtures.questieDbRoot().resolve("data/Forever/foreverNpcDB.lua"));
    String traces = Files.readString(Fixtures.questieDbRoot().resolve("src/corrections/Forever/traces/foreverItemTraces.lua"));
    assertThat(npcs).doesNotContain("[185333]");
    assertThat(traces).contains("[itemKeys.npcDrops_add] = {185333}");
  }
}
