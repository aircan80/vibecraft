package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestInvolvementTest {
  @Test
  void objectiveWordingNeedsPageContext() {
    var row = new QuestInvolvement(new QuestSummary(176, "Wanted: Hogger", 11, 10, null),
        Set.of(QuestInvolvement.Role.OBJECTIVE), null);
    assertThat(row.rolesText()).isEqualTo("Objective");
    assertThat(row.rolesText(EntityKind.NPC)).isEqualTo("Kill objective");
    assertThat(row.rolesText(EntityKind.ITEM)).isEqualTo("Collect objective");
    assertThat(row.rolesText(EntityKind.OBJECT)).isEqualTo("Interact objective");
    assertThat(row.glyphs()).isEmpty();
  }

  @Test
  void mergedRolesHaveStableTextWithoutQuantities() {
    var row = new QuestInvolvement(new QuestSummary(7787, "Thunderaan", 60, 60, null),
        Set.of(QuestInvolvement.Role.STARTS, QuestInvolvement.Role.PROVIDED, QuestInvolvement.Role.REQUIRED), null);
    assertThat(row.glyphs()).isEqualTo("!");
    assertThat(row.rolesText()).isEqualTo("Starts · Given at start · Needed");
  }
}
