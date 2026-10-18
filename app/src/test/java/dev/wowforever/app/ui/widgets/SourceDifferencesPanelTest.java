package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.SourceDifference;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class SourceDifferencesPanelTest {
  @Test void compactControlKeepsDifferencesVisibleAndBuildsEvidenceWithoutIo()throws Exception{
    SwingUtilities.invokeAndWait(()->{
      var panel=new SourceDifferencesPanel(List.of(new SourceDifference("Item level","Values differ",List.of(
          new SourceDifference.Assertion("Client","50","Build 70450",true),new SourceDifference.Assertion("Questie","45","Retained alternate",false)))));
      SourceInfoButton button=(SourceInfoButton)panel.getComponent(0);
      assertThat(button.isFocusable()).isTrue();assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Sources for source comparisons");
      assertThat(text(panel)).contains("Values differ").doesNotContain("Build 70450");
      var details=button.detailsContent();assertThat(text(details)).contains("Client","50","Questie","45","Build 70450");
      panel.setSize(360,1000);panel.doLayout();assertThat(panel.getPreferredSize().height).isPositive();
    });
  }
  @Test void largeEvidenceSetsBuildInBoundedBatchesOnlyWhenDetailsAreRequested()throws Exception{
    SwingUtilities.invokeAndWait(()->{
      var entries=java.util.stream.IntStream.range(0,81).mapToObj(i->new SourceDifference("Field "+i,"Values differ",List.of())).toList();
      var panel=new SourceDifferencesPanel(entries);assertThat(text(panel)).doesNotContain("Field 0");
      JPanel details=(JPanel)((SourceInfoButton)panel.getComponent(0)).detailsContent();
      JPanel comparisons=(JPanel)details.getComponent(2);JButton more=(JButton)details.getComponent(3);
      assertThat(comparisons.getComponentCount()).isEqualTo(80);assertThat(more.isVisible()).isTrue();
      more.doClick();assertThat(comparisons.getComponentCount()).isEqualTo(160);more.doClick();assertThat(more.isVisible()).isFalse();assertThat(comparisons.getComponentCount()).isEqualTo(162);
    });
  }
  @Test void structuredStatsAndTechnicalEvidenceStartCollapsed()throws Exception{
    SwingUtilities.invokeAndWait(()->{
      String raw="KNOWN · {\"ID\":16806,\"rows\":[1,2,3]}";
      var panel=new SourceDifferencesPanel(List.of(new SourceDifference("classic stats","Retained knowledge evidence",List.of(new SourceDifference.Assertion("Classic Era",raw,"ItemSparse.csv · snapshot abc",true)))));
      var details=((SourceInfoButton)panel.getComponent(0)).detailsContent();
      JPanel comparisons=(JPanel)details.getComponent(2);JButton technical=(JButton)comparisons.getComponent(3);WrappingText payload=(WrappingText)comparisons.getComponent(4);
      assertThat(payload.isVisible()).isFalse();assertThat(payload.getText()).contains(raw,"ItemSparse.csv");
      assertThat(((WrappingText)comparisons.getComponent(2)).getText()).contains("Structured source row retained").doesNotContain("16806");
      technical.doClick();assertThat(payload.isVisible()).isTrue();technical.doClick();assertThat(payload.isVisible()).isFalse();
      assertThat(text(panel)).contains("other-edition comparison");
    });
  }
  @Test void unknownsScopesAndComparisonWarningsAreNotHiddenBehindTheIcon(){
    var evidence=List.of(new SourceDifference("Loot","Incomplete rate coverage",List.of()),new SourceDifference("Rates","Different rate scopes",List.of()),new SourceDifference("Legacy","Values differ",List.of(new SourceDifference.Assertion("VMaNGOS","Unknown","Vanilla rate (VMaNGOS)",false))));
    assertThat(SourceDifferencesPanel.summary(evidence)).contains("Incomplete rate coverage","Different rate scopes","Values differ","other-edition comparison","unknown source values");
    assertThat(SourceDifferencesPanel.summary(List.of(new SourceDifference("Name","Updated by hotfix",List.of(new SourceDifference.Assertion("General source","name","retained",true)))))).doesNotContain("other-edition");
  }
  private static String text(Container root){var result=new StringBuilder();for(Component c:root.getComponents()){if(c instanceof JTextArea area)result.append(area.getText());if(c instanceof Container nested)result.append(text(nested));}return result.toString();}
}
