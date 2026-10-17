package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.pages.quest.ChainRows;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("shipped-data")
class ShippedChainRowsTest extends ShippedDataSupport {
  @Test
  void defiasShowsTwoBeforeAndFourEarlier() {
    var chain = repos.quests().chain(166);
    var rows = ChainRows.of(chain, Set.of(), false, false);
    assertThat(rows).noneMatch(r -> r.kind() == ChainRows.Kind.CONNECTOR);
    assertThat(rows).extracting(ChainRows.Row::text)
        .contains("Show 4 earlier steps", "Lv 22  The Defias Brotherhood — The Deadmines");
    assertThat(rows.stream().filter(r -> r.step() != null)).extracting(r -> r.step().questId())
        .containsExactly(142L, 155L, 166L);
    assertThat(ChainRows.of(chain, Set.of(), true, false).stream().filter(r -> r.step() != null))
        .extracting(r -> r.step().questId()).containsExactly(65L, 132L, 135L, 141L, 142L, 155L, 166L);
  }

  @Test
  void campingFanAndBreadcrumbBothWays() {
    // The plan said 87 identical names, but shipped Questie data has 12 profession-specific names.
    // Preserve those names: the controller ruled that equal names alone form each fan.
    var fans = ChainRows.of(repos.quests().chain(783), Set.of(), true, true).stream()
        .filter(r -> r.kind() == ChainRows.Kind.FAN).toList();
    assertThat(fans).hasSize(12);
    assertThat(fans.stream().mapToInt(ChainRows.Row::hidden).sum()).isEqualTo(87);
    assertThat(fans).extracting(ChainRows.Row::text)
        .contains("Camping 101: Alchemy ×7 ›", "Camping 101: Enchanting ×8 ›");
    assertThat(ChainRows.of(repos.quests().chain(11), Set.of(), true, true))
        .extracting(ChainRows.Row::text).contains("Optional lead-in:");
    assertThat(ChainRows.of(repos.quests().chain(239), Set.of(), true, true))
        .extracting(ChainRows.Row::text).contains("Leads to:");
  }

  @Test
  void requiresAllAndMissingQuest() {
    assertThat(ChainRows.of(repos.quests().chain(7795), Set.of(), true, true))
        .extracting(ChainRows.Row::text).contains("Requires all of:");
    assertThat(ChainRows.of(repos.quests().chain(7795), Set.of(), true, true).stream()
        .filter(r -> r.step() != null && r.step().connector()
            == dev.wowforever.app.model.QuestChain.Connector.REQUIRES_ALL))
        .extracting(r -> r.step().questId()).containsExactlyInAnyOrder(7791L, 7793L, 7794L);
    var wool = ChainRows.of(repos.quests().chain(7791), Set.of(), true, true);
    assertThat(wool.stream().filter(r -> r.step() != null && r.step().questId() == 7795))
        .singleElement().satisfies(r -> {
          assertThat(r.step().parentQuestId()).isEqualTo(7791L);
          assertThat(r.step().connector()).isEqualTo(dev.wowforever.app.model.QuestChain.Connector.UNLOCKS);
        });
    assertThat(wool.stream().filter(r -> r.step() != null && r.step().questId() == 7796))
        .singleElement().satisfies(r -> assertThat(r.step().parentQuestId()).isEqualTo(7795L));
    assertThat(ChainRows.of(repos.quests().chain(4604), Set.of(), true, true))
        .extracting(ChainRows.Row::text).contains("Unknown quest #2955", "Requires one of:",
            "Choose one (exclusive):");
  }
}
