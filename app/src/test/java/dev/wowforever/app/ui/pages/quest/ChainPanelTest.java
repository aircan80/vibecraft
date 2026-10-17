package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChainPanelTest {
  @Test
  void currentRowIsBoldWithGoldBarAndNavigationUsesTheSpine() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Step current = ChainRowsTest.CURRENT;
      Step before = ChainRowsTest.step(9, "Before", Connector.REQUIRES_ONE, 10L, -1);
      Step after = ChainRowsTest.step(11, "After", Connector.UNLOCKS, 10L, 1);
      QuestChain chain = new QuestChain(List.of(before, current, after), 1, List.of(before, current, after), false);
      List<ViewTarget> opened = new ArrayList<>();
      ChainPanel panel = new ChainPanel(chain, opened::add);
      JLabel label = descendants(panel).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
          .filter(l -> l.getText().contains("Current")).findFirst().orElseThrow();
      assertThat(label.getFont().isBold()).isTrue();
      Container line = label.getParent();
      line.setSize(400, 30);
      line.doLayout();
      BufferedImage image = new BufferedImage(400, 30, BufferedImage.TYPE_INT_ARGB);
      var g = image.createGraphics();
      line.paint(g);
      g.dispose();
      assertThat(image.getRGB(0, 15)).isEqualTo(Theme.current().accentGold().getRGB());
      assertThat(image.getRGB(2, 15)).isEqualTo(Theme.current().accentGold().getRGB());
      panel.previous();
      panel.next();
      assertThat(opened).containsExactly(new ViewTarget.Entity(before.ref()), new ViewTarget.Entity(after.ref()));
      button(panel, "Lv 10  Before — Westfall").doClick();
      assertThat(opened).hasSize(3);
    });
  }

  @Test
  void compactHidesStandaloneAndCollapsesWithAnAccessibleToggle() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JPanel compact = new JPanel();
      compact.putClientProperty(CompactColumns.KEY, true);
      ChainPanel standalone = new ChainPanel(ChainRowsTest.chain(List.of(ChainRowsTest.CURRENT)), t -> {});
      compact.add(standalone);
      standalone.doLayout();
      assertThat(standalone.isVisible()).isFalse();
      ChainPanel panel = new ChainPanel(ChainRowsTest.chain(List.of(ChainRowsTest.CURRENT,
          ChainRowsTest.step(11, "After", Connector.UNLOCKS, 10L, 1))), t -> {});
      compact.add(panel);
      panel.doLayout();
      assertThat(panel.isCollapsed()).isTrue();
      assertThat(panel.heading().getText()).isEqualTo("Quest chain (2)");
      assertThat(panel.heading().getAccessibleContext().getAccessibleName()).endsWith(", collapsed");
      panel.heading().doClick();
      panel.doLayout();
      assertThat(panel.isCollapsed()).isFalse();
      assertThat(panel.heading().getAccessibleContext().getAccessibleName()).endsWith(", expanded");
    });
  }

  @Test
  void fansAndWindowControlsExpandInPlaceAndUnknownQuestsAreNotLinks() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      List<Step> steps = new ArrayList<>(List.of(ChainRowsTest.CURRENT));
      for (int i = 0; i < 3; i++) {
        steps.add(ChainRowsTest.step(20 + i, "Fan", Connector.UNLOCKS, 10L, 1));
      }
      steps.add(ChainRowsTest.step(99, "Later", Connector.UNLOCKS, 22L, 3));
      steps.add(new Step(2955, null, null, null, Connector.UNLOCKS, 10L, 1, false));
      ChainPanel panel = new ChainPanel(ChainRowsTest.chain(steps), t -> {});
      button(panel, "Fan ×3 ›").doClick();
      assertThat(panel.rows()).filteredOn(r -> r.kind() == ChainRows.Kind.STEP).hasSize(4);
      button(panel, "Show 1 later steps").doClick();
      assertThat(panel.rows()).extracting(ChainRows.Row::text).contains("Lv 10  Later — Westfall");
      assertThat(descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast))
          .noneMatch(b -> b.getText().equals("Unknown quest #2955"));
    });
  }

  private static JButton button(Container panel, String text) {
    return descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
        .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
  }

  private static List<Component> descendants(Container parent) {
    List<Component> all = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      all.add(child);
      if (child instanceof Container container) {
        all.addAll(descendants(container));
      }
    }
    return all;
  }
}
