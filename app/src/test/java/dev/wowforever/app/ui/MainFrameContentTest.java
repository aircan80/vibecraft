package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Test
  void ctrlBTogglesTheList() {
    state.setDividerLocation(333);
    JPanel content = content();
    JSplitPane split = split(content);
    Object key = content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ctrl B"));
    assertThat(key).isEqualTo(MainFrame.TOGGLE_LIST);
    Runnable press = () -> content.getActionMap().get(key)
        .actionPerformed(new ActionEvent(content, ActionEvent.ACTION_PERFORMED, ""));
    int dividerSize = split.getDividerSize();
    press.run();
    assertThat(list.isVisible()).isFalse();
    assertThat(split.getDividerSize()).isZero();
    assertThat(state.dividerLocation()).isEqualTo(333); // hiding does not overwrite the saved width
    press.run();
    assertThat(list.isVisible()).isTrue();
    assertThat(split.getDividerSize()).isEqualTo(dividerSize);
    assertThat(split.getDividerLocation()).isEqualTo(333);
  }
}
