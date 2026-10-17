package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ObjectiveVerbTest {
  @Test
  void kindsAndExtraObjectiveTextChooseTheAction() {
    assertThat(ObjectiveVerb.of("creature", null)).isEqualTo(ObjectiveVerb.KILL);
    assertThat(ObjectiveVerb.of("item", null)).isEqualTo(ObjectiveVerb.COLLECT);
    assertThat(ObjectiveVerb.of("object", null)).isEqualTo(ObjectiveVerb.USE);
    assertThat(ObjectiveVerb.of("reputation", null)).isEqualTo(ObjectiveVerb.REPUTATION);
    assertThat(ObjectiveVerb.of("spell", null)).isEqualTo(ObjectiveVerb.CAST);
    assertThat(ObjectiveVerb.of("explore", "Use a Fresh Carcass at the Flame of Uzel"))
        .isEqualTo(ObjectiveVerb.USE);
    assertThat(ObjectiveVerb.of("explore", "Escort OOX-17/TN to Steamwheedle Port"))
        .isEqualTo(ObjectiveVerb.ESCORT);
    assertThat(ObjectiveVerb.of("explore", "Protect the caravan")).isEqualTo(ObjectiveVerb.ESCORT);
    assertThat(ObjectiveVerb.of("explore", "Explore the Cave")).isEqualTo(ObjectiveVerb.EXPLORE);
    assertThat(ObjectiveVerb.of("unknown", null)).isEqualTo(ObjectiveVerb.EXPLORE);
    assertThat(ObjectiveVerb.of(null, null)).isEqualTo(ObjectiveVerb.EXPLORE);
    assertThat(ObjectiveVerb.KILL.word()).isEqualTo("Kill");
  }
}
