package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectivesPanelTest {
  @Test
  void shippedRequiredCountsDoNotLeakIntoDescriptionsOrAccessibleNames() {
    for (long id : new long[] {2969, 6622, 6624}) {
      var loaded = QuestTestData.load(id);
      var panel = new ObjectivesPanel(loaded.data().objectives(), loaded.data().sourceItems(), t -> {}, n -> {});
      for (var row : panel.rows()) {
        assertThat(row.text().getText()).doesNotContain("at least 6", "15 Patients");
        assertThat(row.text().getAccessibleContext().getAccessibleName()).doesNotContain("at least 6", "15 Patients");
      }
      var map = new QuestMapPanel(loaded.data(), Map.of(), t -> {}, text -> {});
      assertThat(map.coordinates().getText()).doesNotContain("at least 6", "15 Patients");
    }
  }

  @Test
  void hoggerRowFocusAndTargetLinks() {
    var data = QuestTestData.load(176).data();
    List<ViewTarget> opened = new ArrayList<>();
    List<Integer> focus = new ArrayList<>();
    var panel = new ObjectivesPanel(data.objectives(), data.sourceItems(), opened::add, focus::add);
    var row = panel.rows().getFirst();
    assertThat(row.focusButton().getText()).isEqualTo("1");
    assertThat(row.row().verb()).isEqualTo(ObjectiveVerb.COLLECT);
    assertThat(row.targetLinks().getFirst().getText()).isEqualTo("Huge Gnoll Claw");
    assertThat(row.whereLine().getText()).isEqualTo("Drops from Hogger 84 %");
    row.targetLinks().getFirst().doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM, 1931)));
    row.focusButton().doClick();
    assertThat(focus).containsExactly(1);
    assertThat(row.text().isVisible()).isFalse();
  }

  @Test
  void instanceLinkWoolObjectsAndKillCreditNames() {
    var data = QuestTestData.load(166).data();
    List<ViewTarget> opened = new ArrayList<>();
    var panel = new ObjectivesPanel(data.objectives(), data.sourceItems(), opened::add, n -> {});
    assertThat(panel.rows().getFirst().whereLink().getText()).isEqualTo("Inside The Deadmines — open dungeon page ›");
    panel.rows().getFirst().whereLink().doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.INSTANCE, 3)));
    var wool = QuestTestData.load(7791).data();
    assertThat(new ObjectivesPanel(wool.objectives(), wool.sourceItems(), t -> {}, n -> {}).rows().getFirst()
        .whereLine().getText()).startsWith("Found in ");
    var credit = QuestTestData.load(452).data();
    var kill = new ObjectivesPanel(credit.objectives(), credit.sourceItems(), t -> {}, n -> {}).rows().getFirst();
    assertThat(kill.targetLinks()).hasSize(9);
    assertThat(kill.whereLine().getText()).contains("Silverpine Forest", "of 9 NPCs");
  }

  @Test
  void carriedItemsAndEscortAndNoReputationQuantity() {
    var data = QuestTestData.load(7787).data();
    var panel = new ObjectivesPanel(data.objectives(), data.sourceItems(), t -> {}, n -> {});
    assertThat(panel.carryLine().getText()).isEqualTo("You carry: Dormant Wind Kissed Blade (given at start)");
    assertThat(panel.needLine().isVisible()).isFalse();
    var escort = QuestTestData.load(648).data();
    var row = new ObjectivesPanel(escort.objectives(), escort.sourceItems(), t -> {}, n -> {}).rows().getFirst();
    assertThat(row.row().verb()).isEqualTo(ObjectiveVerb.ESCORT);
    assertThat(row.whereLine().getText()).isEqualTo("Escort — location marked on the map.");
    var reputation = new ObjectiveRow(1, ObjectiveVerb.REPUTATION, List.of(), null, "42000", List.of(), List.of(), null);
    assertThat(new ObjectivesPanel(List.of(reputation), List.of(), t -> {}, n -> {}).rows().getFirst().text().getText())
        .isEmpty();
  }
}
