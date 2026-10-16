package dev.wowforever.importer.corrections;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import org.junit.jupiter.api.Test;

class EnumLoaderTest {
  @Test
  void loadsZoneAndFactionIds() {
    ProblemCollector problems = new ProblemCollector();
    LuaTable c = EnumLoader.load(Fixtures.questieDbRoot(), problems);
    assertThat(((LuaTable) c.get("zoneIDs")).get("DUSKWOOD")).isEqualTo(LuaNumber.of(10));
    assertThat(((LuaTable) c.get("factionIDs")).get("DARNASSUS")).isEqualTo(LuaNumber.of(69));
    assertThat(problems.problems()).isEmpty();
  }

  @Test
  void computesForeverRaceMasks() {
    LuaTable c = EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector());
    LuaTable forever = (LuaTable) ((LuaTable) c.get("byExpansion")).get("Forever");
    assertThat(((LuaTable) forever.get("raceKeys")).get("HUMAN")).isEqualTo(LuaNumber.of(1));
    assertThat(LuaValues.asLong(((LuaTable) forever.get("raceKeys")).get("ALL_ALLIANCE"))).isPresent();
  }
}
