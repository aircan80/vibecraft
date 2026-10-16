package dev.wowforever.importer.drops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.lua.ProblemCollector;
import org.junit.jupiter.api.Test;

class DropRateLoaderTest {
  @Test
  void precedenceAcrossSources() {
    DropRates r = DropRateLoader.load(Fixtures.roots(), new ProblemCollector());
    // correction beats override 57.0 and wowhead 56.07
    assertThat(r.get(639, 2874)).contains(new DropRate(60.0, "questie-correction"));
    assertThat(r.get(11520, 14149)).hasValueSatisfying(d -> assertThat(d.source()).isEqualTo("atlasloot"));
  }

  @Test
  void markerValuesAreIgnored() {
    assertThat(DropRateLoader.load(Fixtures.roots(), new ProblemCollector()).get(98, 725)).isEmpty();
  }

  @Test
  void loadsFixtureFilesWithoutProblems() {
    ProblemCollector problems = new ProblemCollector();
    DropRateLoader.load(Fixtures.roots(), problems);
    assertThat(problems.problems()).isEmpty();
  }

  @Test
  void wowheadRatesAreLoadedWhenNothingBeatsThem() {
    assertThat(DropRateLoader.load(Fixtures.roots(), new ProblemCollector()).get(6, 159))
        .contains(new DropRate(10.9946, "questie-wowhead"));
  }

  @Test
  void itemLevelOverrideAppliesToEveryNpcAndLosesToBetterSources() {
    DropRates r = new DropRates();
    r.put(1, 100, new DropRate(5.0, "atlasloot"), 4);
    r.put(2, 100, new DropRate(6.0, "questie-wowhead"), 3);
    r.put(3, 100, new DropRate(7.0, "questie-correction"), 1);
    r.putItem(100, new DropRate(50.0, "atlasloot-override"), 2);
    assertThat(r.get(1, 100)).contains(new DropRate(50.0, "atlasloot-override"));
    assertThat(r.get(2, 100)).contains(new DropRate(50.0, "atlasloot-override"));
    assertThat(r.get(3, 100)).contains(new DropRate(7.0, "questie-correction"));
    assertThat(r.get(4, 101)).isEmpty();
  }

  @Test
  void falseOverrideIsIgnored() {
    DropRates r = DropRateLoader.load(Fixtures.roots(), new ProblemCollector());
    assertThat(r.get(11520, 14149)).contains(new DropRate(33.3, "atlasloot"));
    // 15410 is `false` in the override file: no item-level entry was created for it.
    assertThat(r.get(1, 15410)).isEmpty();
  }

  @Test
  void betterPairPrecedenceWinsRegardlessOfInsertionOrder() {
    DropRates r = new DropRates();
    r.put(1, 2, new DropRate(1.0, "atlasloot"), 4);
    r.put(1, 2, new DropRate(2.0, "questie-wowhead"), 3);
    r.put(1, 2, new DropRate(3.0, "atlasloot"), 4);
    assertThat(r.get(1, 2)).contains(new DropRate(2.0, "questie-wowhead"));
    assertThat(r.size()).isEqualTo(1);
  }
}
