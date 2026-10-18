package dev.wowforever.app.ui.widgets;
import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Component;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
class SourceEvidencePanelTest {
 @Test void absentMetadataIsExplicitAndTechnicalEvidenceStartsCollapsed()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var panel=new SourceEvidencePanel(List.of(new SourceEvidencePanel.Fact("Build",null),new SourceEvidencePanel.Fact("Source","A retained source")),"Only this image is qualified.","snapshot exact-hash");
   assertThat(((JTextArea)panel.getComponent(1)).getText()).isEqualTo("Not recorded");
   var payload=java.util.Arrays.stream(panel.getComponents()).filter(JTextArea.class::isInstance).map(JTextArea.class::cast).filter(text->text.getText().equals("snapshot exact-hash")).findFirst().orElseThrow();
   assertThat(payload.isVisible()).isFalse();
   var toggle=java.util.Arrays.stream(panel.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast).findFirst().orElseThrow();toggle.doClick();assertThat(payload.isVisible()).isTrue();
   for(Component component:panel.getComponents())if(component instanceof JTextArea text)assertThat(text.getFont().getSize()).isEqualTo(18);
   assertThat(panel.getScrollableTracksViewportWidth()).isTrue();
  });
 }
}
