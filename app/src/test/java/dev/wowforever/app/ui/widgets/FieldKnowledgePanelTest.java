package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.FieldKnowledgeView;
import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class FieldKnowledgePanelTest {
  @Test void aggregateStatsRemainInEvidenceWithoutFloodingDefaultFieldFacts()throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      String json="{\"ID\":\"16806\",\"payload\":\""+"x".repeat(4000)+"\"}";
      var raw=new FieldKnowledgeView("classic_stats",FieldKnowledgeView.State.KNOWN,json,"era","1.15.9.70003","ItemSparse.csv:42 · snapshot archive-id");
      var panel=new FieldKnowledgePanel(List.of(raw,
          new FieldKnowledgeView("stats",FieldKnowledgeView.State.KNOWN,"0","hotfix","70450","ItemSparse.csv:7"),
          new FieldKnowledgeView("stats",FieldKnowledgeView.State.KNOWN,"","hotfix","70450","ItemSparse.csv:8"),
          new FieldKnowledgeView("name",FieldKnowledgeView.State.KNOWN,"A scalar fact","hotfix","70450","ItemSparse.csv:9"),
          new FieldKnowledgeView("stats",FieldKnowledgeView.State.KNOWN,"Public ItemSparse hotfix row","hotfix","70450","ItemSparse.csv:10")));
      var text=texts(panel);
      assertThat(text).doesNotContain(json,"Public ItemSparse hotfix row").contains("0","","A scalar fact");
      assertThat(text).anySatisfy(value->assertThat(value).contains("Classic Era value","1.15.9.70003"));
      assertThat(raw.value()).isEqualTo(json);assertThat(raw.evidence()).contains("ItemSparse.csv:42","archive-id");
      assertThat(chips(panel)).hasSize(5).allSatisfy(chip->assertThat(chip.getText()).isEqualTo("Known"));
    });
  }
  private static List<String> texts(Container root) {
    var values=new ArrayList<String>();
    for(Component child:root.getComponents()){if(child instanceof JTextArea text)values.add(text.getText());if(child instanceof Container nested)values.addAll(texts(nested));}
    return values;
  }
  private static List<KnowledgeChip> chips(Container root) {
    var values=new ArrayList<KnowledgeChip>();
    for(Component child:root.getComponents()){if(child instanceof KnowledgeChip chip)values.add(chip);if(child instanceof Container nested)values.addAll(chips(nested));}
    return values;
  }
}
