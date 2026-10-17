package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ChainStepperTest {
  @Test
  void defiasStepSevenOfSeven() {
    var opened = new ArrayList<ViewTarget>();
    var stepper = new ChainStepper(QuestTestData.load(166).data().chain(), opened::add);
    assertThat(stepper.text()).isEqualTo("‹ Previous · This quest · Next ›  Step 7 of 7");
    stepper.previous().doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST, 155)));
    assertThat(stepper.next().isEnabled()).isFalse();
    JPanel compact = new JPanel();
    compact.putClientProperty(CompactColumns.KEY, true);
    compact.add(stepper);
    assertThat(stepper.text()).isEqualTo("‹ Prev · 7/7 · Next ›");
  }

  @Test
  void hiddenForATrivialSpine() {
    assertThat(new ChainStepper(QuestTestData.load(176).data().chain(), target -> {}).isVisible()).isFalse();
    assertThat(new ChainStepper(QuestChain.EMPTY, target -> {}).isVisible()).isFalse();
  }
}
