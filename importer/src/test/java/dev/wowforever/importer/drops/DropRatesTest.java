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
  void theSourcesRankInTheRuledOrder() {
    DropRates r = new DropRates();
    r.put(1, 2, new DropRate(100, "vmangos", true), DropRateLoader.VMANGOS_QUEST_ONLY);
    r.put(1, 2, new DropRate(5, "atlasloot"), DropRateLoader.ATLASLOOT);
    r.put(1, 2, new DropRate(4, "vmangos"), DropRateLoader.VMANGOS);
    r.put(1, 2, new DropRate(3, "questie-wowhead"), DropRateLoader.QUESTIE_WOWHEAD);
    r.putItem(2, new DropRate(2, "atlasloot-override"), DropRateLoader.ATLASLOOT_OVERRIDE);
    r.put(1, 2, new DropRate(1, "questie-correction"), DropRateLoader.QUESTIE_CORRECTION);
    assertThat(r.allSources(1, 2)).extracting(DropRate::source).containsExactly("questie-correction",
        "atlasloot-override", "questie-wowhead", "vmangos", "atlasloot", "vmangos");
    assertThat(r.allSources(1, 2).get(5).questOnly()).isTrue();
    assertThat(new DropRate(1, "x")).isEqualTo(new DropRate(1, "x", false));
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
