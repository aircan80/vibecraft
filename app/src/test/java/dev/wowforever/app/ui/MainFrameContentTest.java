package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import java.awt.*;
import java.util.prefs.*;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Journal has one full-width page; compatibility result models never enter its component tree. */
class MainFrameContentTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final JPanel toolbar = new JPanel();
  private final Rail rail = new Rail();
  private final MasterList list = new MasterList(new ManualDelay());
  private final JPanel detail = new JPanel();
  private final StatusBar status = new StatusBar();
  @AfterEach void removeNode() throws BackingStoreException { node.removeNode(); }
  private JPanel content() { return MainFrame.buildContent(toolbar, rail, list, detail, status, state); }
  private static JPanel workspace(JPanel content) { return (JPanel)((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER); }
  @Test void pageIsDirectlyBelowHeaderWithoutResultsPaneOrDivider() {
    JPanel content=content();BorderLayout layout=(BorderLayout)content.getLayout();JPanel workspace=workspace(content);
    assertThat(layout.getLayoutComponent(BorderLayout.NORTH)).isNull();
    assertThat(layout.getLayoutComponent(BorderLayout.WEST)).isSameAs(rail);
    assertThat(layout.getLayoutComponent(BorderLayout.SOUTH)).isSameAs(status);
    assertThat(((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.NORTH)).isSameAs(toolbar);
    assertThat(((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.CENTER)).isSameAs(detail);
    assertThat(list.getParent()).isNull();assertThat(list.isVisible()).isFalse();
    assertThat(descendants(content)).noneMatch(JSplitPane.class::isInstance).doesNotContain(list);
  }
  @Test void oldPinnedPreferenceCannotRecreateThePane() {
    state.setListVisible(true);state.setDividerLocation(345);JPanel content=content();
    assertThat(new UiState(node).listVisible()).isFalse();
    assertThat(list.getParent()).isNull();assertThat(list.isVisible()).isFalse();
    assertThat(content.getActionMap().get("toggleList")).isNull();
    assertThat(descendants(content)).noneMatch(JSplitPane.class::isInstance);
  }
  @Test void sidebarAndPageUseAllAvailableSpaceAtWideAndNarrowWidths() {
    JPanel content=content();toolbar.setPreferredSize(new Dimension(300,74));
    for(int width:new int[]{1280,420}){
      content.setSize(width,900);for(int i=0;i<4;i++)layoutTree(content);
      Point header=SwingUtilities.convertPoint(toolbar,0,0,content),side=SwingUtilities.convertPoint(rail,0,0,content);
      assertThat(side.y).isZero();assertThat(header.y).isEqualTo(side.y);
      assertThat(header.x).isEqualTo(side.x+rail.getWidth());assertThat(rail.getHeight()).isGreaterThan(toolbar.getHeight());
      assertThat(detail.getWidth()).isEqualTo(workspace(content).getWidth());
      assertThat(detail.getX()).isZero();assertThat(detail.getY()).isEqualTo(toolbar.getHeight());
      assertThat(list.getParent()).isNull();
    }
  }
  private static java.util.List<Component> descendants(Container root){var result=new java.util.ArrayList<Component>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(descendants(child));}return result;}
  private static void layoutTree(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layoutTree(child);}
  @Test
  void historyButtonsHaveIconsNamesAndTooltips() {
    javax.swing.JButton back = HistoryButtons.back();
    javax.swing.JButton forward = HistoryButtons.forward();
    assertThat(back.getIcon()).isNotNull();
    assertThat(back.getIcon().getIconWidth()).isPositive();
    assertThat(forward.getIcon()).isNotNull();
    assertThat(back.getAccessibleContext().getAccessibleName()).isEqualTo("Back");
    assertThat(forward.getAccessibleContext().getAccessibleName()).isEqualTo("Forward");
    assertThat(back.getToolTipText()).contains("mouse button 4");
    assertThat(forward.getToolTipText()).contains("mouse button 5");
    assertThat(back.getText()).isNullOrEmpty();
  }
}
