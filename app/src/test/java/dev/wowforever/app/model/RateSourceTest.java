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
    assertThat(new RateSource("magic", 1.0).label()).isEqualTo("magic");
  }
}
