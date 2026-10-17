package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link MainFrame#buildContent}: built headless, the frame itself is never created in tests. */
class MainFrameContentTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final JPanel toolbar = new JPanel();
  private final Rail rail = new Rail();
  private final MasterList list = new MasterList(new ManualDelay());
  private final JPanel detail = new JPanel();
  private final StatusBar status = new StatusBar();

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private JPanel content() {
    return MainFrame.buildContent(toolbar, rail, list, detail, status, state);
  }

  private static JPanel center(JPanel content) {
    return (JPanel) ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
  }

  private static JSplitPane split(JPanel content) {
    return (JSplitPane) ((BorderLayout) center(content).getLayout()).getLayoutComponent(BorderLayout.CENTER);
  }

  @Test
  void contentIsToolbarRailListDetailAndStatus() {
    JPanel content = content();
    BorderLayout layout = (BorderLayout) content.getLayout();
    assertThat(layout.getLayoutComponent(BorderLayout.NORTH)).isSameAs(toolbar);
    assertThat(layout.getLayoutComponent(BorderLayout.SOUTH)).isSameAs(status);
    assertThat(((BorderLayout) center(content).getLayout()).getLayoutComponent(BorderLayout.WEST)).isSameAs(rail);
    JSplitPane split = split(content);
    assertThat(split.getOrientation()).isEqualTo(JSplitPane.HORIZONTAL_SPLIT);
    assertThat(split.getLeftComponent()).isSameAs(list);
    assertThat(split.getRightComponent()).isSameAs(detail);
  }

  @Test
  void dividerStartsAtTheSavedLocation() {
    state.setDividerLocation(345);
    assertThat(split(content()).getDividerLocation()).isEqualTo(345);
  }

  @Test
  void movingTheDividerSavesIt() {
    split(content()).setDividerLocation(410);
    assertThat(new UiState(node).dividerLocation()).isEqualTo(410);
  }

  private static void toggle(JPanel content) {
    content.getActionMap().get(MainFrame.TOGGLE_LIST)
        .actionPerformed(new ActionEvent(content, ActionEvent.ACTION_PERFORMED, ""));
  }

  @Test
  void toggleListHidesAndShowsTheListAndRemembersIt() {
    state.setDividerLocation(333);
    JPanel content = content();
    JSplitPane split = split(content);
    // Ctrl+B itself is bound on the root pane by KeyMap; the content holds the action
    assertThat(content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ctrl B"))).isNull();
    int dividerSize = split.getDividerSize();
    toggle(content);
    assertThat(list.isVisible()).isFalse();
    assertThat(split.getDividerSize()).isZero();
    assertThat(state.dividerLocation()).isEqualTo(333); // hiding does not overwrite the saved width
    assertThat(new UiState(node).listVisible()).isFalse();
    toggle(content);
    assertThat(list.isVisible()).isTrue();
    assertThat(split.getDividerSize()).isEqualTo(dividerSize);
    assertThat(split.getDividerLocation()).isEqualTo(333);
    assertThat(new UiState(node).listVisible()).isTrue();
  }

  @Test
  void aListHiddenLastTimeStartsHidden() {
    state.setDividerLocation(333);
    state.setListVisible(false);
    JPanel content = content();
    JSplitPane split = split(content);
    assertThat(list.isVisible()).isFalse();
    assertThat(split.getDividerSize()).isZero();
    toggle(content);
    assertThat(list.isVisible()).isTrue();
    assertThat(split.getDividerSize()).isPositive();
    assertThat(split.getDividerLocation()).isEqualTo(333);
  }

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
