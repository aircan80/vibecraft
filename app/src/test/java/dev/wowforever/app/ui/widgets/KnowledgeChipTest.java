package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.FieldKnowledgeView;
import org.junit.jupiter.api.Test;

class KnowledgeChipTest {
  @Test void allSixStatesHaveVisibleAccessibleNamesAndZeroRemainsAValue() {
    for(var state:FieldKnowledgeView.State.values()) {
      var field=new FieldKnowledgeView("stats",state,"0","source","build","path:7");
      var chip=new KnowledgeChip(field);
      assertThat(chip.getText()).isEqualTo(state.label());
      assertThat(chip.getIcon()).isNotNull();
      assertThat(chip.getAccessibleContext().getAccessibleName()).contains(state.label());
      assertThat(field.displayValue()).isEqualTo("0");
    }
    assertThat(new FieldKnowledgeView("stats",FieldKnowledgeView.State.NOT_YET_DISCOVERED,null,"","","").displayValue()).isEqualTo("Unknown");
    assertThat(new FieldKnowledgeView("stats",FieldKnowledgeView.State.NONE,null,"","","").displayValue()).isEqualTo("None");
  }
}
