package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateSourceTest {
  @Test
  void questieSourcesComeBeforeAtlasLootAndUnknownOnesLast() {
    List<RateSource> l = new ArrayList<>(List.of(new RateSource("zeta", 1.0), new RateSource("atlasloot-override", 2.0),
        new RateSource("atlasloot", 3.0), new RateSource("questie-correction", 4.0),
        new RateSource("questie-wowhead", 5.0), new RateSource("questie", 6.0), new RateSource("alpha", 7.0)));
    l.sort(RateSource.ORDER);
    assertThat(l).extracting(RateSource::source).containsExactly("questie", "questie-wowhead",
        "questie-correction", "atlasloot", "atlasloot-override", "alpha", "zeta");
  }

  @Test
  void labelsNameTheAddon() {
    assertThat(new RateSource("questie", 1.0).label()).isEqualTo("Questie");
    assertThat(new RateSource("questie-wowhead", 1.0).label()).isEqualTo("Questie");
    assertThat(new RateSource("questie-correction", 1.0).label()).isEqualTo("Questie correction");
    assertThat(new RateSource("atlasloot", 1.0).label()).isEqualTo("AtlasLoot");
    assertThat(new RateSource("atlasloot-override", 1.0).label()).isEqualTo("AtlasLoot override");
    assertThat(new RateSource("atlasloot-forever", 1.0).label()).isEqualTo("AtlasLoot Forever");
    assertThat(new RateSource("atlasloot-forever-override", 1.0).label()).isEqualTo("AtlasLoot Forever override");
    assertThat(new RateSource("vmangos", 1.0).label()).isEqualTo("Vanilla rate (VMaNGOS)");
    assertThat(new RateSource("magic", 1.0).label()).isEqualTo("magic");
  }

  @Test
  void vmangosReadsVanillaRate() {
    assertThat(new RateSource("vmangos", 12.5).label()).isEqualTo("Vanilla rate (VMaNGOS)");
    List<RateSource> l = new ArrayList<>(List.of(new RateSource("zeta", 1.0), new RateSource("vmangos", 2.0),
        new RateSource("atlasloot-override", 3.0), new RateSource("questie-wowhead", 4.0),
        new RateSource("atlasloot-forever", 5.0)));
    l.sort(RateSource.ORDER);
    assertThat(l).extracting(RateSource::source).containsExactly("questie-wowhead", "atlasloot-override",
        "atlasloot-forever", "vmangos", "zeta");
    assertThat(RateSource.BOSS_NOTE).isEqualTo("Vanilla rate; Forever loot reworked");
  }

  @Test
  void aQuestOnlyRateIsTheChanceWhileOnTheQuest() {
    assertThat(new RateSource("vmangos", 100.0).questOnly()).isFalse();
    assertThat(new RateSource("vmangos", 100.0, true).questOnly()).isTrue();
  }
}
