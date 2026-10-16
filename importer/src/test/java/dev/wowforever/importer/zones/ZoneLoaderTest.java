package dev.wowforever.importer.zones;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.corrections.EnumLoader;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.ProblemCollector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZoneLoaderTest {
  private static LuaTable constants() {
    return EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector());
  }

  @Test
  void mergesZoneSources() {
    Map<Long, Zone> z = ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(2717L), new ProblemCollector());
    assertThat(z.get(10L)).isEqualTo(new Zone(10, "Duskwood", null, "zone"));
    assertThat(z.get(1581L).name()).isEqualTo("The Deadmines");
    assertThat(z.get(1581L).parentId()).isEqualTo(40L);
    assertThat(z.get(1581L).kind()).isEqualTo("dungeon");
    assertThat(z.get(2717L).kind()).isEqualTo("raid");
    assertThat(z.get(133L).kind()).isEqualTo("subzone");
  }

  @Test
  void loadsFixtureFilesWithoutProblems() {
    ProblemCollector problems = new ProblemCollector();
    ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(2717L), problems);
    assertThat(problems.problems()).isEmpty();
  }

  @Test
  void classifiesContinentsAndCities() {
    Map<Long, Zone> z = ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(), new ProblemCollector());
    assertThat(z.get(10074L).kind()).isEqualTo("continent");
    assertThat(z.get(1519L).kind()).isEqualTo("city");
    assertThat(z.get(1519L).name()).isEqualTo("Stormwind City");
  }

  @Test
  void subZoneParentsApplyToExistingAndNewIds() {
    Map<Long, Zone> z = ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(), new ProblemCollector());
    assertThat(z.get(133L).parentId()).isEqualTo(1L);
    assertThat(z.get(135L).kind()).isEqualTo("subzone");
  }

  @Test
  void unknownSubZoneIdsAreAddedWithPlaceholderName(@TempDir Path tmp) throws Exception {
    Path zonesDir = Files.createDirectories(tmp.resolve("support/Forever/Zones"));
    Files.copy(Fixtures.questieDbRoot().resolve("support/Forever/Zones/dungeons.lua"), zonesDir.resolve("dungeons.lua"));
    Files.writeString(zonesDir.resolve("subZoneToParentZone.lua"),
        "ZoneDB.private.subZoneToParentZoneOverride = [[return {\n  [99999] = 40,\n  [1581] = 12,\n}]]\n");
    Map<Long, Zone> z = ZoneLoader.load(tmp, constants(), Set.of(), new ProblemCollector());
    assertThat(z.get(99999L)).isEqualTo(new Zone(99999, "Zone #99999", 40L, "subzone"));
    // a dungeon listed as a sub-zone keeps its kind and takes the sub-zone parent
    assertThat(z.get(1581L)).isEqualTo(new Zone(1581, "The Deadmines", 12L, "dungeon"));
  }

  @Test
  void dungeonOutsideRaidSetIsDungeonWithParent() {
    Map<Long, Zone> z = ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(), new ProblemCollector());
    assertThat(z.get(2717L).kind()).isEqualTo("dungeon");
    assertThat(z.get(2717L).parentId()).isEqualTo(46L);
  }
}
