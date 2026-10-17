package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SectionOrderTest {
  @Test
  void theOrdersPerPageType() {
    assertThat(SectionOrder.ITEM).containsExactly("Dropped by", "Sold by", "Quest reward", "Contained in", "Quests");
    assertThat(SectionOrder.NPC).containsExactly("Drops", "Sells", "Quests", "Location");
    assertThat(SectionOrder.ZONE).containsExactly("Map", "Dungeons & raids", "Quests", "NPCs", "Objects");
    assertThat(SectionOrder.INSTANCE).containsExactly("Encounters", "Loot", "Other loot lists", "Quests");
  }

  @Test
  void listedTitlesComeFirstInTheirOrderAndTheOthersKeepTheirsAfterThem() {
    List<String> built = List.of("Dropped by", "In instance loot", "Contained in", "Sold by", "Quest reward",
        "Related quests");
    assertThat(SectionOrder.sort(SectionOrder.ITEM, built, Function.identity())).containsExactly("Dropped by",
        "Sold by", "Quest reward", "Contained in", "In instance loot", "Related quests");
    assertThat(SectionOrder.sort(SectionOrder.NPC, List.of("Starts quests", "Location", "Ends quests", "Drops"),
        Function.identity())).containsExactly("Drops", "Location", "Starts quests", "Ends quests");
    assertThat(SectionOrder.sort(SectionOrder.ZONE, List.of(), Function.identity())).isEmpty();
  }

  @Test
  void aTitleAfterAnotherGoesRightAfterIt() {
    assertThat(SectionOrder.after(SectionOrder.ZONE, "Dungeons & raids", "Sub-zones")).containsExactly("Map",
        "Dungeons & raids", "Sub-zones", "Quests", "NPCs", "Objects");
  }
}
