package dev.wowforever.importer.drops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DropRatesTest {
  @Test
  void allSourcesKeepsEveryValueInPrecedenceOrder() {
    DropRates r = new DropRates();
    r.put(1, 2, new DropRate(33.3, "atlasloot"), 4);
    r.put(1, 2, new DropRate(56.07, "questie-wowhead"), 3);
    r.put(1, 2, new DropRate(60.0, "questie-correction"), 1);
    assertThat(r.allSources(1, 2)).containsExactly(new DropRate(60.0, "questie-correction"),
        new DropRate(56.07, "questie-wowhead"), new DropRate(33.3, "atlasloot"));
    assertThat(r.get(1, 2)).contains(new DropRate(60.0, "questie-correction"));
    assertThat(r.allSources(9, 9)).isEmpty();
  }

  @Test
  void itemLevelOverrideAppearsInAllSources() {
    DropRates r = new DropRates();
    r.put(1, 2, new DropRate(60.0, "questie-correction"), 1);
    r.put(1, 2, new DropRate(56.07, "questie-wowhead"), 3);
    r.putItem(2, new DropRate(57.0, "atlasloot-override"), 2);
    assertThat(r.allSources(1, 2)).extracting(DropRate::source)
        .containsExactly("questie-correction", "atlasloot-override", "questie-wowhead");
    assertThat(r.allSources(7, 2)).containsExactly(new DropRate(57.0, "atlasloot-override"));
    assertThat(r.get(1, 2)).contains(new DropRate(60.0, "questie-correction"));
  }
}
