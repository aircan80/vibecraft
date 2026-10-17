package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.SourceDifference;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class SourceDifferencesPanelTest {
  @Test void evidenceIsAccessibleWithoutHoverOrDatabaseAccess()throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      var panel=new SourceDifferencesPanel(List.of(new SourceDifference("Item level","Values differ",List.of(
          new SourceDifference.Assertion("Client","50","Build 70450",true),
          new SourceDifference.Assertion("Questie","45","Retained alternate",false)))));
      JButton toggle=(JButton)panel.getComponent(0);JPanel details=(JPanel)panel.getComponent(1);
      assertThat(toggle.isFocusable()).isTrue();assertThat(details.isVisible()).isFalse();
      toggle.doClick();assertThat(details.isVisible()).isTrue();
      assertThat(toggle.getAccessibleContext().getAccessibleName()).startsWith("Collapse");
      panel.setSize(360,1000);panel.doLayout();assertThat(panel.getPreferredSize().height).isPositive();
      toggle.doClick();assertThat(details.isVisible()).isFalse();
    });
  }
  @Test void largeEvidenceSetsBuildInBoundedBatchesOnlyWhenExpanded()throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      var entries=java.util.stream.IntStream.range(0,81).mapToObj(i->new SourceDifference("Field "+i,"Values differ",List.of())).toList();
      var panel=new SourceDifferencesPanel(entries);JPanel details=(JPanel)panel.getComponent(1);
      JPanel comparisons=(JPanel)details.getComponent(1);JButton more=(JButton)details.getComponent(2);
      assertThat(comparisons.getComponentCount()).isZero();
      ((JButton)panel.getComponent(0)).doClick();assertThat(comparisons.getComponentCount()).isEqualTo(80);
      assertThat(more.isVisible()).isTrue();more.doClick();assertThat(comparisons.getComponentCount()).isEqualTo(160);
      more.doClick();assertThat(more.isVisible()).isFalse();assertThat(comparisons.getComponentCount()).isEqualTo(162);
    });
  }
  @Test void structuredStatsRequireAnExplicitTechnicalEvidenceExpansion()throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      String raw="KNOWN · {\"ID\":16806,\"rows\":[1,2,3]}";
      var panel=new SourceDifferencesPanel(List.of(new SourceDifference("classic stats","Retained knowledge evidence",List.of(
          new SourceDifference.Assertion("Era",""+raw,"ItemSparse.csv:42 · snapshot archive",true)))));
      ((JButton)panel.getComponent(0)).doClick();
      JPanel details=(JPanel)panel.getComponent(1),comparisons=(JPanel)details.getComponent(1);
      JButton technical=null;WrappingText payload=null;
      for(var component:comparisons.getComponents()) {
        if(component instanceof JButton button)technical=button;
        if(component instanceof WrappingText text && text.getAccessibleContext().getAccessibleName().equals("Technical source evidence"))payload=text;
      }
      assertThat(technical).isNotNull();assertThat(payload).isNotNull();assertThat(payload.isVisible()).isFalse();
      technical.doClick();assertThat(payload.isVisible()).isTrue();assertThat(payload.getText()).isEqualTo(raw);
    });
  }
}
