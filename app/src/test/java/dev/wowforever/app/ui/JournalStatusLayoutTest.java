package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JournalStatusLayoutTest {
  @Test void narrowJournalStatusKeepsControlsAndMessageWithinTheWindow() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      StatusBar status=new StatusBar();
      status.setProvenance(Map.of("client_product","wow_classic_beta","client_build","1.60.1.70450","client_item_count","31085"));
      status.setBuild("0123456789abcdef");
      status.setMessage("Ready to explore");
      status.setSize(420,status.getPreferredSize().height);
      MainFrame.fitJournalStatus(status);
      layout(status);
      assertThat(status.provenanceLabel().isVisible()).isFalse();
      assertThat(status.infoLabel().isVisible()).isFalse();
      assertThat(status.messageLabel().getWidth()).isPositive();
      assertThat(status.messageLabel().getX()).isGreaterThanOrEqualTo(0);
      assertThat(status.messageLabel().getX()+status.messageLabel().getWidth()).isLessThanOrEqualTo(420);
      assertThat(status.themeToggle().isVisible()).isTrue();
      assertThat(status.layoutToggle().isVisible()).isTrue();
      assertThat(status.getToolTipText()).contains("Forever beta", "Data build");
      assertThat(status.messageLabel().getToolTipText()).isEqualTo("Ready to explore");
      status.setSize(1280,status.getHeight());
      MainFrame.fitJournalStatus(status);
      assertThat(status.provenanceLabel().isVisible()).isTrue();
      assertThat(status.infoLabel().isVisible()).isTrue();
    });
  }

  private static void layout(Container container) {
    container.doLayout();
    for(Component component:container.getComponents())if(component instanceof Container child)layout(child);
  }
}
