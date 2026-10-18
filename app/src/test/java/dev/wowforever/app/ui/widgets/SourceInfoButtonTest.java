package dev.wowforever.app.ui.widgets;
import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
class SourceInfoButtonTest {
 @Test void iconHasAccessibleSummaryAndKeyboardActionWithoutEagerEvidenceCreation()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var created=new AtomicInteger();var button=new SourceInfoButton("Item #7","Client <beta> & addon evidence",()->{created.incrementAndGet();return new JPanel();});
   assertThat(created).hasValue(0);assertThat(button.getPreferredSize()).isEqualTo(new Dimension(28,28));assertThat(button.getFont().getSize()).isEqualTo(14);
   assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Sources for Item #7");assertThat(button.getAccessibleContext().getAccessibleDescription()).contains("Client <beta> & addon evidence");
   assertThat(button.getToolTipText()).contains("&lt;beta&gt;","&amp;");assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0))).isEqualTo("open-sources");
   button.detailsContent();assertThat(created).hasValue(1);
  });
 }
}
