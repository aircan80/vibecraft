package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChainRowsTest {
  static Step step(long id, String name, Connector connector, Long parent, int depth) {
    return new Step(id, name, 10, "Westfall", connector, parent, depth, false);
  }
  static final Step CURRENT = step(10, "Current", null, null, 0);
  static QuestChain chain(List<Step> steps) {
    return new QuestChain(steps, steps.indexOf(CURRENT), List.of(CURRENT), false);
  }

  @Test
  void nestedGroupsKeepTheirParent() {
    QuestChain chain = chain(List.of(step(11, "Left", Connector.REQUIRES_ALL, 10L, -1),
        step(13, "A", Connector.REQUIRES_ONE, 11L, -2),
        step(14, "B", Connector.REQUIRES_ONE, 11L, -2),
        step(12, "Right", Connector.REQUIRES_ALL, 10L, -1), CURRENT));
    var rows = ChainRows.of(chain, Set.of(), false, false);
    assertThat(rows.stream().filter(r -> r.kind() == ChainRows.Kind.CONNECTOR))
        .extracting(r -> r.indent() + ":" + r.text())
        .containsExactly("1:Requires all of:", "2:Requires one of:");
    assertThat(rows.stream().filter(r -> r.step() != null)).extracting(r -> r.step().questId())
        .containsExactly(11L, 13L, 14L, 12L, 10L);
  }

  @Test
  void sameNamedUnlocksCollapseAndExpandWithinTheirOwnGroup() {
    List<Step> steps = new ArrayList<>(List.of(CURRENT));
    for (int i = 0; i < 87; i++) {
      steps.add(step(100 + i, "Camping 101", Connector.UNLOCKS, 10L, 1));
    }
    steps.add(step(900, "Camping 101", Connector.UNLOCKS, 777L, 2));
    QuestChain chain = chain(steps);
    var rows = ChainRows.of(chain, Set.of(), true, true);
    var fan = rows.stream().filter(r -> r.kind() == ChainRows.Kind.FAN).findFirst().orElseThrow();
    assertThat(fan.text()).isEqualTo("Camping 101 ×87 ›");
    assertThat(rows.stream().filter(r -> r.kind() == ChainRows.Kind.STEP)).hasSize(1);
    assertThat(ChainRows.of(chain, Set.of(fan.fanKey()), true, true).stream()
        .filter(r -> r.kind() == ChainRows.Kind.STEP)).hasSize(88);
  }

  @Test
  void windowsUnknownLoopAndTruncation() {
    Step unknown = new Step(2955, null, null, null, Connector.REQUIRES_ONE, 10L, -1, false);
    Step loop = new Step(10, "Current", 10, null, Connector.UNLOCKS, 10L, 1, true);
    var base = chain(List.of(step(1, "Early", Connector.REQUIRES_ALL, 10L, -4), unknown,
        CURRENT, loop, step(2, "Late", Connector.UNLOCKS, 10L, 4)));
    var chain = new QuestChain(base.steps(), base.currentIndex(), base.spine(), true);
    assertThat(ChainRows.of(chain, Set.of(), false, false)).extracting(ChainRows.Row::text)
        .contains("Show 1 earlier steps", "Show 1 later steps", "Unknown quest #2955",
            "Lv 10  Current (loop in data)", "The chain goes on beyond 50 steps.");
    assertThat(ChainRows.of(chain, Set.of(), true, true)).noneMatch(r ->
        r.kind() == ChainRows.Kind.SHOW_EARLIER || r.kind() == ChainRows.Kind.SHOW_LATER);
  }

  @Test
  void exclusiveMembersAreJoinedByOr() {
    var rows = ChainRows.of(chain(List.of(CURRENT, step(2, "A", Connector.EXCLUSIVE, 10L, 0),
        step(3, "B", Connector.EXCLUSIVE, 10L, 0))), Set.of(), false, false);
    assertThat(rows).extracting(ChainRows.Row::text)
        .contains("Choose one (exclusive):", "Lv 10  A — Westfall", "or Lv 10  B — Westfall");
  }

  @Test
  void standaloneAndEmpty() {
    assertThat(ChainRows.of(chain(List.of(CURRENT)), Set.of(), false, false))
        .singleElement().extracting(ChainRows.Row::text)
        .isEqualTo("No prerequisites or follow-ups recorded.");
    assertThat(ChainRows.of(QuestChain.EMPTY, Set.of(), false, false)).isEmpty();
  }
}
