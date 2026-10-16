package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Parses the cached upstream addon files; skipped when the importer cache is absent. */
@EnabledIf("cacheExists")
class RealFilesTest {
  private static final Path CACHE = Path.of(System.getProperty("user.home"), ".cache", "wowforever-importer");
  private static final Path QUESTIE = CACHE.resolve("questiedb/49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4");
  private static final Path ATLAS = CACHE.resolve("atlasloot/8e99341e4e779328460bf7684c0d5b22ce50ddf1");

  static boolean cacheExists() {
    return Files.isDirectory(QUESTIE) && Files.isDirectory(ATLAS);
  }

  private static List<Path> luaFiles(Path dir, boolean recursive) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> s = recursive ? Files.walk(dir) : Files.list(dir)) {
      return s.filter(p -> p.toString().endsWith(".lua")).sorted().toList();
    }
  }

  @Test
  void parsesEveryUpstreamFileAndTheirEmbeddedChunks() throws IOException {
    List<Path> files = new ArrayList<>();
    files.addAll(luaFiles(QUESTIE.resolve("src/corrections/Forever"), true));
    files.addAll(luaFiles(QUESTIE.resolve("data/Forever"), false));
    files.addAll(luaFiles(QUESTIE.resolve("src/corrections/enum"), false));
    files.addAll(luaFiles(QUESTIE.resolve("support/Forever/DropTables"), false));
    files.addAll(luaFiles(QUESTIE.resolve("support/Forever/Zones"), false));
    for (String f : List.of("data.lua", "droprate.lua", "droprate_override.lua")) {
      files.add(ATLAS.resolve("AtlasLootClassic_DungeonsAndRaids").resolve(f));
    }
    assertThat(files).hasSizeGreaterThan(10);

    int inner = 0;
    for (Path file : files) {
      LuaChunk chunk = LuaChunk.parse(file);
      String name = file.getFileName().toString();
      List<String> targets = new ArrayList<>();
      if (name.startsWith("forever") && name.endsWith("DB.lua")) {
        String kind = name.substring("forever".length(), name.length() - "DB.lua".length()).toLowerCase();
        targets.add("QuestieDB." + kind + "Data");
      } else if (name.equals("classicItemDrops.lua")) {
        targets.add("QuestieClassicItemDrops.wowheadData");
      } else if (file.getParent().endsWith("Zones")) {
        if (name.equals("areaIdToUiMapId.lua")) {
          targets.add("ZoneDB.private.areaIdToUiMapIdOverride");
        } else if (name.equals("subZoneToParentZone.lua")) {
          targets.add("ZoneDB.private.subZoneToParentZoneOverride");
        }
      }
      for (String target : targets) {
        var text = chunk.longStringAssignedTo(target);
        assertThat(text).as("%s should assign %s", name, target).isPresent();
        LuaChunk.parse(text.get(), name + "#" + target);
        inner++;
      }
    }
    System.out.println("RealFilesTest: parsed " + files.size() + " files, " + inner + " embedded chunks");
  }
}
