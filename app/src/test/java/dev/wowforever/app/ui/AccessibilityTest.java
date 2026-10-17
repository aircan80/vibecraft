package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.UIResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every button that shows no text in the window content has an accessible name, so a screen reader can say what
 * it does. Built under the app's own look and feel, whose search box adds an icon-only clear button.
 */
class AccessibilityTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);

  @BeforeEach
  void installTheme() {
    ThemeManager.applySaved();
  }

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private JPanel content() {
    SearchBar search = new SearchBar(t -> {}, state, new ManualDelay(), new SearchDropdown(new FakeDropdownHost()));
    search.textField().setText("hogger"); // the clear button shows only over text
    return MainFrame.buildContent(MainFrame.toolbar(MainFrame.historyButton("arrow-left", "Back", "Back"),
        MainFrame.historyButton("arrow-right", "Forward", "Forward"), search), new Rail(),
        new MasterList(new ManualDelay()), new JPanel(), new StatusBar(), state);
  }

  /** The buttons under {@code c} that show no text, skipping the look and feel's own (scroll bar arrows). */
  private static List<AbstractButton> iconOnlyButtons(Component c) {
    List<AbstractButton> found = new ArrayList<>();
    if (c instanceof AbstractButton b && !(b instanceof UIResource) && (b.getText() == null || b.getText().isBlank())) {
      found.add(b);
    }
    if (c instanceof Container parent) {
      for (Component child : parent.getComponents()) {
        found.addAll(iconOnlyButtons(child));
      }
    }
    return found;
  }

  private static String describe(AbstractButton b) {
    return b.getClass().getName() + " in " + b.getParent().getClass().getName()
        + (b.getName() != null ? " named " + b.getName() : "");
  }

  @Test
  void everyIconOnlyButtonHasAnAccessibleName() {
    List<AbstractButton> buttons = iconOnlyButtons(content());
    // six rail buttons, back and forward, and the search box's clear button
    assertThat(buttons).hasSizeGreaterThanOrEqualTo(9);
    for (AbstractButton b : buttons) {
      assertThat(b.getAccessibleContext().getAccessibleName()).as(describe(b)).isNotBlank();
    }
  }

  @Test
  void theSearchClearButtonIsNamed() {
    JPanel content = content();
    List<AbstractButton> clear = iconOnlyButtons(content).stream()
        .filter(b -> SwingUtilities.getAncestorOfClass(SearchBar.class, b) != null).toList();
    assertThat(clear).isNotEmpty();
    assertThat(clear).allSatisfy(b -> assertThat(b.getAccessibleContext().getAccessibleName())
        .isEqualTo(SearchBar.CLEAR_NAME));
  }
}
