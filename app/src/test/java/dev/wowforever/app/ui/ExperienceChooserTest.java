package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ExperienceChooserTest {
  @Test
  void bothExperiencesAreExplicitAndCancelDoesNotChooseOne() throws Exception {
    List<UiExperience> selected = new ArrayList<>();
    AtomicInteger cancellations = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      JPanel panel = ExperienceChooser.content(selected::add, cancellations::incrementAndGet);
      assertThat(selected).isEmpty();
      button(panel, "choose-classic").doClick();
      button(panel, "choose-journal").doClick();
      button(panel, "cancel-startup").doClick();
      assertThat(selected).containsExactly(UiExperience.CLASSIC, UiExperience.JOURNAL);
      assertThat(cancellations.get()).isEqualTo(1);
      assertThat(button(panel, "choose-classic").getMnemonic()).isNotZero();
      assertThat(button(panel, "choose-journal").getMnemonic()).isNotZero();
    });
  }

  private static JButton button(Container parent, String name) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton button && name.equals(button.getName())) {
        return button;
      }
      if (child instanceof Container container) {
        JButton found = button(container, name);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
