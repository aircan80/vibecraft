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
    state.setListVisible(true); // pinned open: the divider shows
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
    state.setListVisible(true); // pinned open last time
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
  void anUnpinnedListShowsForResultsAndCollapsesWhenOneOpens() {
    state.setDividerLocation(333);
    JPanel content = content();
    JSplitPane split = split(content);
    assertThat(list.isVisible()).as("not pinned: hidden until a search").isFalse();
    MainFrame.updateList(content, ListVisibility::resultsShown);
    assertThat(list.isVisible()).isTrue();
    assertThat(split.getDividerLocation()).isEqualTo(333);
    MainFrame.updateList(content, ListVisibility::previewed);
    assertThat(list.isVisible()).as("a preview keeps it").isTrue();
    MainFrame.updateList(content, v -> v.resultOpened(1280));
    assertThat(list.isVisible()).isFalse();
    assertThat(split.getDividerSize()).isZero();
    assertThat(new UiState(node).listVisible()).as("collapsing is no pin change").isFalse();
    MainFrame.updateList(content, ListVisibility::resultsShown);
    MainFrame.updateList(content, v -> v.resultOpened(ListVisibility.WIDE_PX));
    assertThat(list.isVisible()).as("a wide window keeps it").isTrue();
  }

  @Test
  void escBringsACollapsedListBackUnpinned() {
    JPanel content = content();
    MainFrame.updateList(content, ListVisibility::resultsShown);
    MainFrame.updateList(content, v -> v.resultOpened(1280));
    assertThat(list.isVisible()).isFalse();
    MainFrame.revealList(content); // Esc's last step, before it focuses the list
    assertThat(list.isVisible()).isTrue();
    assertThat(list.jlist().isVisible()).as("the escape target is on screen").isTrue();
    assertThat(MainFrame.listVisibility(content).pinned()).as("not a Ctrl+B pin").isFalse();
    assertThat(new UiState(node).listVisible()).isFalse();
    MainFrame.updateList(content, v -> v.resultOpened(1280));
    assertThat(list.isVisible()).as("the next opened result collapses it again").isFalse();
  }

  @Test
  void enterInTheListMovesTheFocusToThePageWhenTheListCollapses() {
    JPanel holder = new JPanel(new BorderLayout());
    JPanel page = new JPanel(new BorderLayout());
    javax.swing.JTable table = new javax.swing.JTable(1, 1);
    page.add(table);
    holder.add(page);
    JPanel content = MainFrame.buildContent(toolbar, rail, list, holder, status, state);
    MainFrame.updateList(content, ListVisibility::resultsShown);
    assertThat(MainFrame.focusAfterCollapse(list.jlist(), list, holder)).as("the list still shows").isNull();
    MainFrame.updateList(content, v -> v.resultOpened(1280));
    assertThat(MainFrame.focusAfterCollapse(list.jlist(), list, holder)).as("the page's first table")
        .isSameAs(table);
    assertThat(MainFrame.focusAfterCollapse(toolbar, list, holder)).as("focus elsewhere stays").isNull();
    assertThat(MainFrame.focusAfterCollapse(list.jlist(), list, new JPanel())).as("no page yet: the holder")
        .isNotNull();
  }

  @Test
  void ctrlBPinsTheListAgainstCollapsing() {
    JPanel content = content();
    toggle(content);
    assertThat(list.isVisible()).isTrue();
    assertThat(new UiState(node).listVisible()).as("the pin is saved").isTrue();
    MainFrame.updateList(content, v -> v.resultOpened(1280));
    assertThat(list.isVisible()).isTrue();
    toggle(content);
    assertThat(list.isVisible()).isFalse();
    assertThat(new UiState(node).listVisible()).isFalse();
    assertThat(MainFrame.listVisibility(content).pinned()).isFalse();
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
